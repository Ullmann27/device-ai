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

    /** Field P0 Code54: the page a text came from is kept – a heading on page 1 is read from page 1 only. */
    @Test fun pageAttributionKeepsBlankPagesAndTheSameText() {
        val raw = sequenceOf("  Projekt\tÜbersicht \r\n\n  Ziel: Fold7 ", "", "Seite  zwei")
        val bounded = PdfDocumentIntake.boundedPages(raw, 1_000)
        assertEquals(PdfDocumentIntake.boundedText(raw, 1_000), bounded.text)
        assertEquals(listOf("Projekt Übersicht\nZiel: Fold7", "", "Seite zwei"), bounded.pages)
    }

    @Test fun pageAttributionFollowsTheSameBudgetCut() {
        var read = 0
        val pages = sequence { repeat(50) { read++; yield("Wort ".repeat(40)) } }
        val bounded = PdfDocumentIntake.boundedPages(pages, 300)
        assertTrue("stops after the budget: $read", read <= 2)
        assertEquals("one entry per page read", read, bounded.pages.size)
        assertEquals("the pages together are exactly the bounded text", bounded.text,
            bounded.pages.filter { it.isNotEmpty() }.joinToString("\n\n"))
        assertTrue(bounded.pages.last().endsWith(" …"))
        assertTrue("page 1 stays whole", bounded.pages.first().split(' ').size == 40)
    }

    @Test fun noTextGivesNoText() {
        assertEquals("", PdfDocumentIntake.boundedText(sequenceOf("", "  ", "\n"), 100))
    }
}
