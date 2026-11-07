package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDocumentIntakeTextTest {
    @Test fun pagesStayInOrderAndBlankRunsCollapse() {
        val text = PdfDocumentIntake.boundedText(sequenceOf("  Projekt\tÜbersicht \r\n\n  Ziel: Fold7 ", "", "Seite  zwei"), 1_000)
        assertEquals("Projekt Übersicht\nZiel: Fold7\n\nSeite zwei", text)
    }

    @Test fun theBudgetCutsOnAWordAndStopsReadingFurtherPages() {
        var read = 0
        val pages = sequence { repeat(50) { read++; yield("Wort ".repeat(40)) } }
        val text = PdfDocumentIntake.boundedText(pages, 300)
        assertTrue("bounded: ${text.length}", text.length <= 302)
        assertTrue("marked as cut", text.endsWith(" …"))
        assertTrue("no half word", text.removeSuffix(" …").split(Regex("\\s+")).all { it == "Wort" })
        assertTrue("stops after the budget instead of reading all 50 pages: $read", read <= 2)
    }

    @Test fun noTextGivesNoText() {
        assertEquals("", PdfDocumentIntake.boundedText(sequenceOf("", "  ", "\n"), 100))
    }
}
