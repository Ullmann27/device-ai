package at.heinz.deviceai.devicetools

import at.heinz.deviceai.cognitive.ChainExtract
import at.heinz.deviceai.cognitive.OwnerChainStepCatalog
import at.heinz.deviceai.cognitive.OwnerDocumentLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream

/** „Sich holen“ and „PDF erstellen“ on the host: real files, the real Word writer, a stand-in for Android's PDF classes. */
class DeviceFileTextAndDocumentTest {
    private val storage = TestStorage()

    @After fun clean() = storage.delete()

    private fun read(relative: String, pages: String? = null, offset: Int = 0, budget: Int = 6_000, pdf: PdfPageReader = TestPdf(emptyList())) =
        DeviceFileText.read(File(storage.dir, relative), DevicePaths.shown(relative), pages, offset, budget, pdf)

    @Test fun plainTextIsReadInItsEncodingAndCutWithAMarkerThatNamesTheWayOn() {
        storage.text("Download/notiz.txt", "Erste Zeile\r\nZweite Zeile mit Ümlaut\n")
        assertEquals("Datei: /sdcard/Download/notiz.txt\nTyp: Text · 38 Byte\nInhalt (Zeichen 1–35 von 35):\nErste Zeile\nZweite Zeile mit Ümlaut",
            read("Download/notiz.txt"))
        storage.file("Download/latin.txt", "Grüße aus Österreich".toByteArray(Charsets.ISO_8859_1))
        assertTrue(read("Download/latin.txt").endsWith("Grüße aus Österreich"))
        storage.file("Download/bom.csv", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "a;b\n1;2".toByteArray())
        assertTrue(read("Download/bom.csv").endsWith(":\na;b\n1;2"))

        val long = (1..400).joinToString(" ") { "Wort$it" }
        storage.text("Download/lang.md", long)
        val first = read("Download/lang.md", budget = 1_400)
        assertTrue(first.length <= 1_400)
        val head = Regex("Inhalt \\(Zeichen 1–(\\d+) von ${long.length}, gekürzt – weiter mit offset=(\\d+)\\):").find(first)
        assertNotNull(first, head)
        assertEquals(head!!.groupValues[1], head.groupValues[2])
        assertTrue(first.endsWith("\n[gekürzt]"))
        // Reading on from the named offset continues exactly there; together nothing is lost or repeated.
        val offset = head.groupValues[2].toInt()
        val second = read("Download/lang.md", offset = offset, budget = 6_000)
        assertTrue(second, second.contains("Inhalt (Zeichen ${offset + 1}–${long.length} von ${long.length}):"))
        fun body(text: String) = text.substringAfter("):\n").removeSuffix("\n[gekürzt]")
        assertEquals(long, body(first) + " " + body(second).trimStart())
        assertTrue(read("Download/lang.md", offset = 999_999).contains("gibt es keinen weiteren Text"))
        // The chain hands the text on.
        val contract = OwnerChainStepCatalog.contract("read_file")!!
        assertNull(contract.success.problem(read("Download/notiz.txt")) { _, _ -> null })
        assertEquals("Erste Zeile\nZweite Zeile mit Ümlaut",
            (contract.gives[1].extract(read("Download/notiz.txt")) { _, _ -> null } as ChainExtract.Result.Values).values["text"])
    }

    @Test fun binariesMissingFilesAndFoldersAreRefusedInWords() {
        storage.file("Download/bild.jpg", ByteArray(64) { it.toByte() })
        storage.file("Download/daten.dat", byteArrayOf(1, 2, 0, 3, 4))
        storage.folder("Download/ordner")
        val contract = OwnerChainStepCatalog.contract("read_file")!!
        listOf(read("Download/bild.jpg") to "Kein Text:", read("Download/daten.dat") to "Kein Text:",
            read("Download/fehlt.txt") to "Nicht gefunden:", read("Download/ordner") to "Kein Dateitext:").forEach { (text, start) ->
            assertTrue(text, text.startsWith(start))
            assertNotNull("a refusal must fail the chain's success check: $text", contract.success.problem(text) { _, _ -> null })
        }
    }

    @Test fun aPdfIsReadByPageFromItsTextLayerAndAScanSaysSo() {
        storage.file("Download/Experimente.pdf", ByteArray(2_000))
        val pdf = TestPdf(listOf("Versuch A: Argon bei 4 bar.", "Versuch B.", "", "Ergebnis: stabil."))
        val all = read("Download/Experimente.pdf", pdf = pdf)
        assertTrue(all, all.startsWith("Datei: /sdcard/Download/Experimente.pdf\nTyp: PDF · 4 Seiten · 2 KB\nInhalt (Seiten 1–4 von 4):\n--- Seite 1 ---\nVersuch A: Argon bei 4 bar."))
        assertTrue(all, all.contains("--- Seite 3 ---\n(kein Text auf dieser Seite)\n--- Seite 4 ---\nErgebnis: stabil."))
        val two = read("Download/Experimente.pdf", pages = "2", pdf = pdf)
        assertTrue(two, two.contains("Inhalt (Seite 2 von 4):\n--- Seite 2 ---\nVersuch B."))
        assertFalse(two.contains("Argon"))
        assertTrue(read("Download/Experimente.pdf", pages = "3", pdf = pdf).contains("Kein Text: Auf Seite 3 gibt es keine Textebene"))
        assertTrue(read("Download/Experimente.pdf", pages = "9", pdf = pdf).startsWith("Seitenangabe nicht verstanden: „9“. Das PDF /sdcard/Download/Experimente.pdf hat 4 Seite(n)"))
        assertTrue(read("Download/Experimente.pdf", pages = "wert", pdf = pdf).startsWith("Seitenangabe nicht verstanden"))
        // More pages than one call carries: the first ten, and the range to continue with.
        val many = read("Download/Experimente.pdf", pdf = TestPdf(List(25) { "Seite ${it + 1}" }))
        assertTrue(many, many.contains("Inhalt (Seiten 1–10 von 25; weitere Seiten mit pages=\"11-20\"):"))
        assertFalse(many.contains("--- Seite 11 ---"))
        assertTrue(read("Download/Experimente.pdf", pdf = TestPdf(listOf("x"), android15 = false)).startsWith("Kein Text: Den Text eines PDFs kann ich erst ab Android 15 lesen."))
        assertTrue(read("Download/Experimente.pdf", pdf = PdfPageReader { _, _ -> throw java.io.IOException("kaputt") }).startsWith("Nicht lesbar:"))
    }

    @Test fun aWordFileWrittenByTheAppIsReadBackAsItsText() {
        val port = TestDocumentPort(storage)
        val writer = DeviceDocumentWriter(port) { LocalDate.of(2026, 11, 19) }
        val outcome = writer.create("Einkauf & Plan", "# Markt\n\n- Milch <frisch>\n- Brot\n\nBis morgen, „Heinz“.\n1. Zuerst\n2. Danach", "docx", "Einkaufsliste")
        assertEquals("Gespeichert: /sdcard/Download/Einkaufsliste.docx\nName: Einkaufsliste.docx\nOrt: Interner Speicher › Download\n" +
            "Format: Word-Datei · ${File(storage.dir, "Download/Einkaufsliste.docx").length().let { at.heinz.deviceai.cognitive.OwnerLibraryMatcher.sizeText(it) }}\n" +
            "Geprüft: zurückgelesen, Inhalt stimmt überein\nEine neue Datei; nichts wurde überschrieben.", outcome.observation)
        assertEquals("/sdcard/Download/Einkaufsliste.docx", outcome.created!!.path)
        assertTrue(outcome.created!!.verified)
        // A real Word package: the four parts, and the text comes back through the read tool.
        val file = File(storage.dir, "Download/Einkaufsliste.docx")
        val entries = ZipInputStream(file.inputStream()).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertEquals(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels"), entries)
        val text = read("Download/Einkaufsliste.docx")
        assertTrue(text, text.startsWith("Datei: /sdcard/Download/Einkaufsliste.docx\nTyp: Word · "))
        assertTrue(text, text.endsWith("Einkauf & Plan\nDonnerstag, 19. November 2026\nMarkt\n• Milch <frisch>\n• Brot\nBis morgen, „Heinz“.\n1. Zuerst\n2. Danach"))
        // The chain reads name and path from the observation.
        val contract = OwnerChainStepCatalog.contract("create_document")!!
        assertNull(contract.success.problem(outcome.observation) { _, _ -> null })
        assertEquals("/sdcard/Download/Einkaufsliste.docx", (contract.gives[0].extract(outcome.observation) { _, _ -> null } as ChainExtract.Result.Values).values["path"])
        assertEquals("Einkaufsliste.docx", (contract.gives[1].extract(outcome.observation) { _, _ -> null } as ChainExtract.Result.Values).values["name"])
        // No file is ever written over: the same name again gets a number.
        assertTrue(writer.create("Einkauf", "Text", "word", "Einkaufsliste").observation.startsWith("Gespeichert: /sdcard/Download/Einkaufsliste (2).docx"))
        assertTrue(read("Download/kaputt.docx".also { storage.text(it, "kein zip") }).startsWith("Kein Text: /sdcard/Download/kaputt.docx"))
    }

    @Test fun aPdfIsBuiltFromTheSameBlocksAndUnknownFormatsOrEmptyContentWriteNothing() {
        val port = TestDocumentPort(storage)
        val writer = DeviceDocumentWriter(port) { LocalDate.of(2026, 11, 19) }
        val pdf = writer.create("Bericht", "# Bericht\nErster Absatz\nweiter.\n\n## Punkte\n- eins", null, null)
        assertTrue(pdf.observation, pdf.observation.startsWith("Gespeichert: /sdcard/Download/Bericht 19.11.2026.pdf\nName: Bericht 19.11.2026.pdf\n"))
        assertTrue(pdf.observation.contains("Format: PDF · 1 Seite · "))
        assertEquals(1, port.pdfRenders)
        // The title is printed once: a first heading that repeats it is dropped; list items get their bullet.
        assertEquals("%PDF-TEST\nTITLE:Bericht\nDATE:Donnerstag, 19. November 2026\nBODY:Erster Absatz weiter.\nHEADING:Punkte\nBODY:• eins",
            File(storage.dir, "Download/Bericht 19.11.2026.pdf").readText())
        val before = File(storage.dir, "Download").list()!!.size
        listOf(writer.create("Bericht", "Text", "wert", null) to "Nicht angelegt: Das Format „wert“ kenne ich nicht.",
            writer.create("Bericht", "   \n", "pdf", null) to "Nicht angelegt: Titel oder Inhalt",
            writer.create("  ", "Text", "pdf", null) to "Nicht angelegt: Titel oder Inhalt").forEach { (outcome, start) ->
            assertTrue(outcome.observation, outcome.observation.startsWith(start))
            assertNull(outcome.created)
            assertNotNull(OwnerChainStepCatalog.contract("create_document")!!.success.problem(outcome.observation) { _, _ -> null })
        }
        assertEquals(before, File(storage.dir, "Download").list()!!.size)
        // A file that cannot be read back is reported as saved but NOT as checked.
        val unchecked = DeviceDocumentWriter(TestDocumentPort(storage, readBackBroken = true)) { LocalDate.of(2026, 11, 19) }.create("Zweiter", "Text", "pdf", null)
        assertTrue(unchecked.observation, unchecked.observation.contains("Geprüft: NEIN"))
        assertFalse(unchecked.created!!.verified)
        val blocks = DeviceDocumentMarkup.layout("Titel", LocalDate.of(2026, 11, 19), "Absatz")
        assertEquals(listOf(OwnerDocumentLayout.Style.TITLE, OwnerDocumentLayout.Style.DATE, OwnerDocumentLayout.Style.BODY), blocks.map { it.style })
    }
}
