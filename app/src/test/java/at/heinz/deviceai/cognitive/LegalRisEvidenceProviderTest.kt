package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LegalRisEvidenceProviderTest {
    private val url = "https://www.ris.bka.gv.at/eli/jgs/1811/946/P1295/NOR12019037"

    @Test fun acceptsOfficialRISAndRejectsLookalikesAndSearchPortals() {
        assertEquals("1295", LegalRisParagraphExtractor.paragraphNumber(url))
        assertEquals("1295", LegalRisParagraphExtractor.paragraphNumber(
            "https://ris.bka.gv.at/NormDokument.wxe?Abfrage=Bundesnormen&Paragraf=1295"))
        assertNull(LegalRisParagraphExtractor.paragraphNumber("https://www.ris.bka.gv.at.evil.invalid/eli/jgs/1811/946/P1295/NOR12019037"))
        assertNull(LegalRisParagraphExtractor.paragraphNumber("https://www.google.de/search?q=RIS+1295"))
        assertNull(LegalRisParagraphExtractor.paragraphNumber("http://www.ris.bka.gv.at/eli/jgs/1811/946/P1295/NOR12019037"))
    }

    @Test fun extractsOnlyMatchingNormativeParagraphNotSiteNavigation() {
        val raw = "RIS - Allgemeines bürgerliches Gesetzbuch § 1295 Bundesrecht Suche Login " +
            "Kurztitel ABGB Kundmachungsorgan Type BG Text: " +
            "§ 1295. (1) Eine erfundene synthetische Prüfbestimmung verpflichtet den Testfall " +
            "bei bestätigten Schäden zu einer sehr sorgfältigen nachvollziehbaren Bewertung. " +
            "(2) Auch die zweite Prüfung bleibt hier ausdrücklich Teil des Testdokuments. " +
            "Schlagworte: Impressum und Navigation ohne Gesetzesinhalt."
        val result = LegalRisParagraphExtractor.extract("1295",raw)
        assertNotNull(result)
        assertTrue(result!!.contains("synthetische Prüfbestimmung"))
        assertFalse(result.contains("Impressum"))
        assertNull(LegalRisParagraphExtractor.extract("1296",raw))
        assertNull(LegalRisParagraphExtractor.extract("1295","Login Google Seite ohne Paragraf".repeat(10)))
    }
}
