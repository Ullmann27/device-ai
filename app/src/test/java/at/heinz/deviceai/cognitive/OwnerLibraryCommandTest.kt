package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document desk's parsers beyond the sentence lists of `regression-library*.jsonl`: bounds, the assistant's name,
 * what exactly is taken out of a sentence, and that the present owner lanes keep their sentences.
 */
class OwnerLibraryCommandTest {
    @Test fun theOpenOrderCarriesTheNameAsSaidAndNothingElse() {
        assertEquals(OwnerLibraryCommand.Open("Argon4", openWhenClear = true), OwnerLibraryCommand.parseOpen("Öffne das PDF Argon4"))
        assertEquals(OwnerLibraryCommand.Open("Argon 4", true), OwnerLibraryCommand.parseOpen("Maya, öffne bitte die PDF-Datei Argon 4.", "Maya"))
        assertEquals(OwnerLibraryCommand.Open("Mietvertrag_2024 (Kopie)", true), OwnerLibraryCommand.parseOpen("Öffne Mietvertrag_2024 (Kopie).pdf"))
        assertEquals(OwnerLibraryCommand.Open("Argon4", false), OwnerLibraryCommand.parseOpen("Such das PDF Argon4"))
        assertEquals(OwnerLibraryCommand.Open("Argon4", true, OwnerLibraryMatcher.FOLDER_DOWNLOAD),
            OwnerLibraryCommand.parseOpen("Such das PDF Argon4 in meinen Downloads und zeig es mir"))
        assertEquals(OwnerLibraryCommand.Open("Argon4", true, OwnerLibraryMatcher.FOLDER_DOCUMENTS),
            OwnerLibraryCommand.parseOpen("Öffne das PDF Argon4 aus dem Dokumente-Ordner"))
        // Too short, too long, control characters, another assistant's name in front.
        assertNull(OwnerLibraryCommand.parseOpen("PDF X"))
        assertNull(OwnerLibraryCommand.parseOpen("Öffne das PDF " + "A".repeat(200)))
        assertNull(OwnerLibraryCommand.parseOpen("Öffne das PDF Argon4\u0000"))
        assertNull(OwnerLibraryCommand.parseOpen("Öffne das PDF " + "Sehr Langer Name ".repeat(5)))
        assertNull(OwnerLibraryCommand.parseOpen("Maya, öffne das PDF Argon4"))
    }

    @Test fun whatTodayOpensThePickerOrAnotherLaneIsNotTaken() {
        // The existing document lane still recognises these – and the desk leaves them alone.
        listOf("Öffne eine PDF", "Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.", "Suche mir eine PDF",
            "Kannst du mir bitte eine PDF öffnen?", "Öffne die ausgewählte PDF").forEach {
            assertNull(it, OwnerLibraryCommand.parseOpen(it))
            assertTrue(it, OwnerDocumentIntent.parse(it) != null)
        }
        // Reference registry, chain recipes, news, export and research keep theirs.
        listOf("Öffne das erste Dokument", "Schau auf Seite drei", "Such die neueste PDF in Downloads und sag mir, wie groß sie ist",
            "Fass mir die Nachrichten von heute zusammen", "Speichere das als PDF", "Recherchiere zu Argon im Internet",
            "Öffne die Einstellungen", "Öffne WhatsApp und schreib Anna").forEach {
            assertNull(it, OwnerLibraryCommand.parseOpen(it))
            assertNull(it, OwnerPageJobCommand.parse(it))
        }
        // The page order never swallows the plain summary or a plain export.
        assertNull(OwnerPageJobCommand.parse("Fass Seite 2 zusammen"))
        assertNull(OwnerPageJobCommand.parse("Fasse die zweite Seite kurz zusammen"))
        assertTrue(OwnerExportCommand.parse("Speichere das als Word") != null && OwnerPageJobCommand.parse("Speichere das als Word") == null)
    }

    @Test fun thePageOrderKeepsPageFormatAndTheWishedName() {
        fun order(text: String) = OwnerPageJobCommand.parse(text) as OwnerPageJobCommand.Result.Order
        order("Fass Seite 2 zusammen und speichere es als Word").let {
            assertEquals(2, it.page); assertEquals(OwnerExportCommand.Format.DOCX, it.format); assertNull(it.name)
        }
        order("Fass Seite 12 zusammen und speichere es als PDF unter dem Namen Kapitel Seite 3").let {
            // The name may itself say „Seite 3“; the page of the order is the one in front of it.
            assertEquals(12, it.page); assertEquals(OwnerExportCommand.Format.PDF, it.format); assertEquals("Kapitel Seite 3", it.name)
        }
        assertEquals(OwnerPageReference.LAST, order("Fasse die letzte Seite zusammen und speichere sie als Word-Datei").page)
        assertNull(order("Fass Seite 2 zusammen und speichere sie").format)
        assertEquals(OwnerPageJobCommand.Result.SeveralPages, OwnerPageJobCommand.parse("Fass die Seiten 1 und 2 zusammen und speichere sie als PDF"))
        assertNull(OwnerPageJobCommand.parse("Fass Seite 0 zusammen und speichere es als Word"))
        assertNull(OwnerPageJobCommand.parse("Fass Seite 2 zusammen und speichere es als Word\u0007"))
    }

    @Test fun correctionsNeedACorrectionWordAndNameWhatIsMeant() {
        assertEquals(OwnerDeskCorrection.Correction.Page(3), OwnerDeskCorrection.parse("Jarvis, ich meinte Seite 3."))
        assertEquals(OwnerDeskCorrection.Correction.Page(3), OwnerDeskCorrection.parse("Seite 3, nicht Seite 2"))
        assertEquals(OwnerDeskCorrection.Correction.Page(7), OwnerDeskCorrection.parse("Sorry, ich meinte die siebte Seite"))
        assertEquals(OwnerDeskCorrection.Correction.Format(OwnerExportCommand.Format.DOCX), OwnerDeskCorrection.parse("Als Word, nicht als PDF"))
        assertEquals(OwnerDeskCorrection.Correction.Format(OwnerExportCommand.Format.PDF), OwnerDeskCorrection.parse("Doch lieber als PDF-Datei speichern"))
        assertEquals(OwnerDeskCorrection.Correction.OtherFile, OwnerDeskCorrection.parse("Öffne die andere Datei"))
        assertEquals(OwnerDeskCorrection.Correction.Pick(1), OwnerDeskCorrection.parse("Nein, die erste Datei"))
        assertEquals(OwnerDeskCorrection.Correction.Name("Datenblatt", false), OwnerDeskCorrection.parse("Ich meinte das Datenblatt"))
        assertEquals(OwnerDeskCorrection.Correction.Name("Argon4 Datenblatt", true), OwnerDeskCorrection.parse("Nein, das PDF Argon4 Datenblatt.pdf"))
        listOf("Seite 3", "Auf Seite 3 steht etwas anderes", "Nein, was steht auf Seite 3?", "als PDF", "Die andere Datei ist größer",
            "Nein, das ist falsch", "Nein, such im Internet", "Ich meine, das stimmt so", "ok").forEach {
            assertNull(it, OwnerDeskCorrection.parse(it))
        }
    }

    @Test fun shortAnswersAreWholeUtterances() {
        listOf("Ja", "ja bitte", "Ja, öffne sie", "Okay", "Genau", "Mach das").forEach { assertTrue(it, OwnerDeskReply.isYes(it)) }
        listOf("Nein", "Nein danke", "Lieber nicht", "Später").forEach { assertTrue(it, OwnerDeskReply.isNo(it)) }
        listOf("Ja, aber erst morgen", "Nein, die zweite", "Jarvis ist toll", "").forEach {
            assertFalse(it, OwnerDeskReply.isYes(it)); assertFalse(it, OwnerDeskReply.isNo(it))
        }
        assertEquals(OwnerExportCommand.Format.DOCX, OwnerDeskReply.format("Als Word-Datei bitte"))
        assertEquals(OwnerExportCommand.Format.PDF, OwnerDeskReply.format("PDF"))
        assertNull(OwnerDeskReply.format("Als Bild"))
        assertTrue(OwnerDeskReply.isStop("Jarvis, stopp!", "Jarvis") && OwnerDeskReply.isStop("Brich ab"))
        assertFalse(OwnerDeskReply.isStop("Stopp die Suche nach Argon und such Xenon"))
    }

    @Test fun pageNumbersAreReadFromDigitsWordsAndOrdinals() {
        fun pages(text: String) = OwnerPageReference.one.findAll(text).map(OwnerPageReference::page).toList()
        assertEquals(listOf(2), pages("Seite 2"))
        assertEquals(listOf(12), pages("auf Seite zwölf"))
        assertEquals(listOf(3, 4), pages("die dritte Seite und Seite Nummer 4"))
        assertEquals(listOf(OwnerPageReference.LAST), pages("die letzte Seite"))
        assertEquals(emptyList<Int?>(), pages("Seiten 2 bis 4"))
        assertEquals(emptyList<Int?>(), pages("Webseite 2"))
    }
}
