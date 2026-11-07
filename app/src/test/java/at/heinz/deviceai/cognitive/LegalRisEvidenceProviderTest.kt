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

    private fun fixture(name: String) = javaClass.getResourceAsStream("/research/$name")!!
        .bufferedReader(Charsets.UTF_8).readText()
    private fun provider(url: String, raw: String, mime: String = "text/html") = LegalRisEvidenceProvider(object : ResearchProvider {
        override fun retrieve(request: ResearchRequest): ResearchResult {
            val content = HttpReadOnlyResearchProvider.responseText(raw, mime, false, 32_000)
            val doc = ResearchDocument(url, "RIS Dokument", 1L, "0".repeat(64), mime,
                SourceIdentity("ogd.ris.bka.gv.at", "ogd.ris.bka.gv.at"), content)
            return ResearchResult(listOf(doc), listOf(doc.citation()))
        }
    })

    /** Real document from the official RIS open-data host (captured 2026-11-01): § 1151 ABGB, in force since 1917. */
    @Test fun realOfficialRisDocumentYieldsBoundParagraphWithValidity() {
        val url = "https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018880/NOR12018880.html"
        val raw = fixture("ris-ogd-NOR12018880-abgb-1151.html")
        assertTrue(LegalRisParagraphExtractor.isOfficialDocumentUrl(url))
        val text = HttpReadOnlyResearchProvider.responseText(raw, "text/html", false, 32_000)
        assertEquals("1151", LegalRisParagraphExtractor.declaredParagraph(text))
        // Screen-reader duplicates must not leak into evidence.
        assertFalse(text.contains("Paragraph 1151,"))
        assertFalse(text.contains("Absatz eins"))
        val result = provider(url, raw).retrieve(ResearchRequest("§ 1151 ABGB Dienstvertrag", url))
        val doc = result.documents.single()
        assertTrue(doc.content, doc.content.startsWith("[RIS-Fassung in Kraft seit 01.01.1917"))
        assertTrue(doc.content.contains("§ 1151. (1) Wenn jemand sich auf eine gewisse Zeit zur Dienstleistung"))
        assertTrue(doc.content.contains("(2) Insoweit damit eine Geschäftsbesorgung (§ 1002) verbunden ist"))
        assertFalse(doc.content.contains("Kurztitel"))
        assertFalse(doc.content.contains("Paragraph 1002"))
        assertEquals("text/plain", doc.mimeType)
        assertEquals(result.citations.single().url, url)
        // The same document can never serve as evidence for another paragraph.
        assertNull(LegalRisParagraphExtractor.extract("1152", text))
    }

    /** Real document: § 678 ABGB, repealed 31.12.2016 – must be marked as a historical version. */
    @Test fun repealedRisVersionIsMarkedNotSilentlyQuotedAsCurrentLaw() {
        val url = "https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018403/NOR12018403.html"
        val raw = fixture("ris-ogd-NOR12018403-abgb-678.html")
        val result = provider(url, raw).retrieve(ResearchRequest("§ 678 ABGB", url))
        val doc = result.documents.single()
        assertTrue(doc.content, doc.content.startsWith("[RIS-Fassung AUSSER KRAFT seit 31.12.2016 (historische Fassung, kein geltendes Recht), in Kraft gewesen ab 01.01.1812]"))
        assertTrue(doc.content.contains("§ 678. Unter Juwelen werden in der Regel nur Edelsteine"))
    }

    @Test fun officialHostLookalikesAndUnboundDocumentsAreRejected() {
        for (bad in listOf("http://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018880/NOR12018880.html",
            "https://ogd.ris.bka.gv.at.evil.invalid/Dokumente/Bundesnormen/NOR12018880/NOR12018880.html",
            "https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018880/NOR12018881.html",
            "https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018880/NOR12018880.html?x=1",
            "https://user@ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018880/NOR12018880.html",
            "https://ogd.ris.bka.gv.at/Dokumente/Justiz/JJT_1/JJT_1.html"))
            assertFalse(bad, LegalRisParagraphExtractor.isOfficialDocumentUrl(bad))
        // An OGD request whose upstream answers with a document from a different host yields nothing.
        val url = "https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018880/NOR12018880.html"
        val wrongHost = provider("https://www.ris.bka.gv.at/eli/jgs/1811/946/P1151/NOR12018880",
            fixture("ris-ogd-NOR12018880-abgb-1151.html")).retrieve(ResearchRequest("§ 1151", url))
        assertTrue(wrongHost.documents.isEmpty())
        // Bot wall on the OGD path: no declared paragraph, no evidence.
        val wall = provider(url, fixture("ris-myra-botwall-503.html")).retrieve(ResearchRequest("§ 1151", url))
        assertTrue(wall.documents.isEmpty())
        // Legacy no-break-space heading on the portal/ELI path is handled as well.
        val eli = "https://www.ris.bka.gv.at/eli/jgs/1811/946/P1295/NOR12019037"
        val nbsp = "Kurztitel ABGB Text: §\u00A01295. (1) Eine erfundene synthetische Prüfbestimmung verpflichtet den Testfall " +
            "bei bestätigten Schäden zu einer sehr sorgfältigen nachvollziehbaren Bewertung. Schlagworte: Navigation"
        assertNotNull(LegalRisParagraphExtractor.extract("1295", nbsp))
        assertEquals("1295", LegalRisParagraphExtractor.paragraphNumber(eli))
    }
}
