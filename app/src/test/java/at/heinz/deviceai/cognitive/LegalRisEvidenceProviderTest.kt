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

    /** Real Myra bot-wall body captured from ris.bka.gv.at (HTTP 503, 2026-11-01). The fetcher
     *  already rejects non-2xx; this guards the extractor if a challenge page ever arrives as 200. */
    @Test fun myraBotWallPageYieldsNoLegalEvidence() {
        val botWall = javaClass.getResourceAsStream("/research/ris-myra-botwall-503.html")!!
            .bufferedReader(Charsets.UTF_8).readText()
        assertTrue(botWall.contains("Security Check"))
        assertNull(LegalRisParagraphExtractor.extract("1151", botWall))
        assertNull(LegalRisParagraphExtractor.extract("30", botWall))
        val risUrl = "https://www.ris.bka.gv.at/eli/jgs/1811/946/P1151/NOR12018403"
        val upstream = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult {
                val doc = ResearchDocument(risUrl, "Security Check", 1L, "0".repeat(64), "text/html",
                    SourceIdentity("www.ris.bka.gv.at", "www.ris.bka.gv.at"), botWall)
                return ResearchResult(listOf(doc), listOf(doc.citation()))
            }
        }
        val result = LegalRisEvidenceProvider(upstream).retrieve(ResearchRequest("§ 1151 ABGB", risUrl))
        assertTrue(result.documents.isEmpty())
        assertTrue(result.citations.isEmpty())
    }
}
