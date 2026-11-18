package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** The page task for the model, what is accepted from it, and the document that is saved ([OwnerPageSummary]). */
class OwnerPageSummaryTest {
    private val page = "Seite zwei erklärt den Abruf: Erinnerungen werden nach Zeit und Bedeutung geordnet."
    private val date = LocalDate.of(2026, 11, 18)

    @Test fun theTaskCarriesOnlyThatPageAndFitsTheBudget() {
        val prompt = OwnerPageSummary.prompt("Argon4.pdf", 2, "  $page\n", 6_000)!!
        assertTrue(prompt.text.startsWith("Fasse den Text von Seite 2 des Dokuments „Argon4“ auf Deutsch zusammen. Schreibe höchstens fünf vollständige Sätze"))
        assertTrue(prompt.text.endsWith("<<<SEITE\n$page\nSEITE>>>"))
        assertTrue(prompt.complete)
        assertEquals(page.length, prompt.totalChars)
        // A long page is cut on a word boundary, within characters and bytes, and says how much it covers.
        val long = (1..900).joinToString(" ") { "Größe$it" }
        val cut = OwnerPageSummary.prompt("Argon4.pdf", 2, long, 6_000)!!
        assertFalse(cut.complete)
        assertEquals(long.length, cut.totalChars)
        assertTrue(cut.coveredChars in 3_000..OwnerPageSummary.MAX_EXCERPT_CHARS)
        assertTrue(cut.text.toByteArray(Charsets.UTF_8).size <= 6_000)
        assertTrue(Regex("Größe\\d+\nSEITE>>>$").containsMatchIn(cut.text))
        // A small model window shrinks the excerpt further; no room at all means no task.
        val small = OwnerPageSummary.prompt("Argon4.pdf", 2, long, 1_200)!!
        assertTrue(small.text.toByteArray(Charsets.UTF_8).size <= 1_200 && small.coveredChars < cut.coveredChars)
        assertNull(OwnerPageSummary.prompt("Argon4.pdf", 2, long, 500))
        assertNull(OwnerPageSummary.prompt("Argon4.pdf", 2, "   \n ", 6_000))
        assertNull(OwnerPageSummary.prompt("Argon4.pdf", 0, page, 6_000))
        // The budget is never exceeded even when the caller offers more.
        assertTrue(OwnerPageSummary.prompt("Argon4.pdf", 2, long, 64_000)!!.text.toByteArray(Charsets.UTF_8).size <= OwnerPageSummary.MAX_PROMPT_BYTES)
    }

    @Test fun onlyAUsableAnswerIsAccepted() {
        val summary = "Die Seite beschreibt den Abruf. Erinnerungen werden nach Zeit und Bedeutung geordnet."
        assertEquals(listOf(summary), OwnerPageSummary.accept("  $summary \n"))
        assertEquals(listOf(summary), OwnerPageSummary.accept("Zusammenfassung: $summary"))
        assertEquals(listOf(summary), OwnerPageSummary.accept("Hier ist die Zusammenfassung von Seite 2:\n$summary"))
        assertEquals(listOf("Erster Absatz mit genug Text für den Test.", "Zweiter Absatz."),
            OwnerPageSummary.accept("**Erster Absatz** mit genug\nText für den Test.\n\n\n\n- Zweiter Absatz."))
        assertNull(OwnerPageSummary.accept(""))
        assertNull(OwnerPageSummary.accept("Zu kurz."))
        assertNull(OwnerPageSummary.accept("x".repeat(2_500)))
        // Far more than asked: the model's own text up to its last complete sentence within the bound.
        val talkative = (1..80).joinToString(" ") { "Das ist der ausführliche Satz Nummer $it." }
        val kept = OwnerPageSummary.accept(talkative)!!.single()
        assertTrue(kept.length in 2_000..OwnerPageSummary.MAX_SUMMARY_CHARS && kept.endsWith(".") && talkative.startsWith(kept))
        assertNull("the task's own markers are not a summary", OwnerPageSummary.accept("<<<SEITE\n$page\nSEITE>>>"))
    }

    @Test fun theDocumentNamesItsSourceAndOpensAsWord() {
        val summary = OwnerPageSummary.Summary(listOf("Erster Absatz & <Zeichen>.", "Zweiter Absatz."), 80, 80, cloud = false)
        val result = OwnerPageSummary.deliverable("Argon4.pdf", 2, 4, summary, date, 1L)
        assertEquals(OwnerDeliverable.Kind.PAGE_SUMMARY, result.kind)
        assertEquals("Zusammenfassung Argon4 Seite 2", result.title)
        assertEquals("Quelle: „Argon4.pdf“, Seite 2 von 4 · zusammengefasst vom Sprachmodell auf dem Gerät am 18.11.2026 · vor der " +
            "Verwendung prüfen.", result.note)
        assertEquals("Zusammenfassung Argon4 Seite 2 18.11.2026.docx",
            OwnerExportCommand.fileName(null, result.title, date, OwnerExportCommand.Format.DOCX) { false })
        // The Word file: a zip whose document part is well-formed XML with title, date, source line and the text.
        val docx = OwnerDocumentDocx.write(OwnerDocumentLayout.blocks(result))
        assertTrue(OwnerPageSummary.readableDocx(docx))
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(docx)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; parts[entry.name] = zip.readBytes() } }
        assertEquals(setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels"), parts.keys)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        parts.values.forEach { assertNotNull(factory.newDocumentBuilder().parse(ByteArrayInputStream(it))) }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(parts.getValue("word/document.xml")))
        val paragraphs = document.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p")
        assertEquals(listOf("Zusammenfassung Argon4 Seite 2", "Mittwoch, 18. November 2026", result.note, "Erster Absatz & <Zeichen>.", "Zweiter Absatz."),
            (0 until paragraphs.length).map { paragraphs.item(it).textContent })
        // Not a Word file: something else, or a zip without the document part.
        assertFalse(OwnerPageSummary.readableDocx("kein zip".toByteArray()))
        assertFalse(OwnerPageSummary.readableDocx(ByteArray(0)))
    }

    @Test fun aCutPageAndTheCloudBrainAreNamedInTheSourceLine() {
        val cut = OwnerPageSummary.deliverable("Bericht.PDF", 7, 0, OwnerPageSummary.Summary(listOf("Ein Absatz."), 4_000, 12_345, cloud = true), date, 1L)
        assertEquals("Zusammenfassung Bericht Seite 7", cut.title)
        assertEquals("Quelle: „Bericht.PDF“, Seite 7 · zusammengefasst von Claude (Cloud) am 18.11.2026 · vor der Verwendung prüfen. " +
            "Grundlage sind die ersten 4.000 von 12.345 Zeichen der Seite; der Rest ist nicht enthalten.", cut.note)
        assertEquals("Dokument", OwnerPageSummary.baseName(" .pdf "))
        assertEquals("1.840", OwnerPageSummary.count(1_840))
    }
}
