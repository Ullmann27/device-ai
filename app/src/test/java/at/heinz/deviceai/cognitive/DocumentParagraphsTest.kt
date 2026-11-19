package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Code64: deterministic paragraph segmentation of really read page text. HOST, pure JVM. */
class DocumentParagraphsTest {
    @Test fun blankLinesSeparateParagraphsAndAHeadingIsNotCounted() {
        val page = "Zeitliche Korrekturen\n\nEine spätere Aussage ersetzt eine frühere.\nSie muss markiert sein.\n\n" +
            "Widersprüche werden zurückgefragt."
        val result = DocumentParagraphs.of(page)
        assertTrue(result.exact)
        assertEquals(listOf("Eine spätere Aussage ersetzt eine frühere. Sie muss markiert sein.",
            "Widersprüche werden zurückgefragt."), result.paragraphs)
    }

    @Test fun windowsLineEndingsAndSpacesInBlankLinesAreHandled() {
        val result = DocumentParagraphs.of("Erster Absatz endet hier.\r\n \t\r\nZweiter Absatz endet hier.\r\n")
        assertEquals(listOf("Erster Absatz endet hier.", "Zweiter Absatz endet hier."), result.paragraphs)
        assertTrue(result.exact)
    }

    @Test fun aPageOfShortLinesWithoutSentencesKeepsEveryBlock() {
        val result = DocumentParagraphs.of("Brot\n\nMilch\n\nKaffee")
        assertEquals(listOf("Brot", "Milch", "Kaffee"), result.paragraphs)
    }

    @Test fun aLastShortBlockIsKeptEvenWhenItLooksLikeAHeading() {
        val result = DocumentParagraphs.of("Der Vertrag beginnt am ersten Januar.\n\nOrt, Datum")
        assertEquals(listOf("Der Vertrag beginnt am ersten Januar.", "Ort, Datum"), result.paragraphs)
    }

    @Test fun withoutBlankLinesTheLineStructureIsUsedAndReportedAsEstimate() {
        val page = listOf(
            "Die Miete beträgt achthundertfünfzig Euro im Monat und ist jeweils",
            "am dritten Werktag fällig.",
            "Die Kündigungsfrist beträgt drei Monate zum Monatsende und beginnt",
            "mit dem Zugang des Schreibens.").joinToString("\n")
        val result = DocumentParagraphs.of(page)
        assertFalse(result.exact)
        assertEquals(listOf(
            "Die Miete beträgt achthundertfünfzig Euro im Monat und ist jeweils am dritten Werktag fällig.",
            "Die Kündigungsfrist beträgt drei Monate zum Monatsende und beginnt mit dem Zugang des Schreibens."),
            result.paragraphs)
    }

    @Test fun wrappedLinesWithoutAnyParagraphEvidenceStayOneParagraph() {
        val page = "Dieser Text läuft ohne erkennbare Grenze über mehrere gleich lange\n" +
            "Zeilen weiter und endet erst ganz am Schluss der Seite mit Punkt."
        val result = DocumentParagraphs.of(page)
        assertFalse(result.exact)
        assertEquals(1, result.paragraphs.size)
        assertEquals(page.replace("\n", " "), result.paragraphs.single())
    }

    @Test fun aSingleLineIsOneParagraphAndAnEmptyPageHasNone() {
        assertEquals(listOf("Einkauf: Brot, Milch, Kaffee."), DocumentParagraphs.of("Einkauf: Brot, Milch, Kaffee.").paragraphs)
        assertEquals(emptyList<String>(), DocumentParagraphs.of("  \n ").paragraphs)
    }

    @Test fun everyParagraphConsistsOnlyOfPageText() {
        val page = "Überschrift\n\nAbsatz eins mit Umlauten: äöü ß.\n\nAbsatz zwei 😀 mit Emoji."
        val squeezedPage = page.filterNot { it.isWhitespace() }
        DocumentParagraphs.of(page).paragraphs.forEach {
            assertTrue(it, squeezedPage.contains(it.filterNot { c -> c.isWhitespace() }))
        }
    }
}
