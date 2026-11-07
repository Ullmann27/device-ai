package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** M3 (Genesis): norm discovery over the official RIS open-data API, fixture-based (captured 2026-11-01). */
class LegalRisDiscoveryProviderTest {
    private fun fixture(name: String) = javaClass.getResourceAsStream("/research/$name")!!
        .bufferedReader(Charsets.UTF_8).readText()

    private class Recording : ResearchDiscoveryProvider {
        val queries = mutableListOf<String>()
        override val isGeneralSearchAvailable = false
        override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
            queries += query.text; return listOf(ResearchSourceCandidate("https://example.org/general", "general"))
        }
    }

    @Test fun parsesNormReferencesIncludingLetterSuffixes() {
        assertEquals(LegalNormReference("364a", "ABGB"), LegalNormReference.parse("Was regelt § 364a ABGB?"))
        assertEquals(LegalNormReference("1151", "ABGB"), LegalNormReference.parse("Bitte den Text von §1151 Abs 2 ABGB"))
        assertEquals(LegalNormReference("1157a", "ABGB"), LegalNormReference.parse("Gibt es einen § 1157 a ABGB?"))
        assertEquals(LegalNormReference("27", "MRG"), LegalNormReference.parse("Paragraph 27 des MRG"))
        assertEquals(LegalNormReference("7", "B-VG"), LegalNormReference.parse("Art? Nein: § 7 B-VG"))
        assertNull("no abbreviation", LegalNormReference.parse("§ 1151"))
        assertNull("plain word is not a statute", LegalNormReference.parse("§ 5 Minuten"))
        assertNull(LegalNormReference.parse("Wer leitet die EZB?"))
        assertEquals("https://data.bka.gv.at/ris/api/v2.6/Bundesrecht?Applikation=BrKons&DokumenteProSeite=Ten&Suchworte=%C2%A7+364a+ABGB",
            LegalRisDiscoveryProvider.apiUrl(LegalNormReference("364a", "ABGB")))
    }

    @Test fun letterParagraphIsFoundAsOfficialOgdDocumentWithValidity() {
        val general = Recording()
        val p = LegalRisDiscoveryProvider(general, fetchJson = { url ->
            assertTrue(url, url.startsWith("https://data.bka.gv.at/ris/api/v2.6/Bundesrecht?Applikation=BrKons"))
            fixture("ris-api-v26-abgb-364a.json")
        })
        val out = p.discover(ResearchQuery("Was regelt § 364a ABGB?", "q1"))
        assertEquals(1, out.size)
        assertEquals("https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018091/NOR12018091.html", out[0].url)
        assertEquals("§ 364a ABGB · RIS-Fassung in Kraft seit 1917-01-01 · Allgemeines bürgerliches Gesetzbuch", out[0].title)
        assertEquals("ris-ogd-api", out[0].discoveredBy)
        assertTrue("general web search is not consulted for a resolved norm", general.queries.isEmpty())
        assertNull(p.lastIssue)
    }

    @Test fun repealedAndCurrentVersionsAreBothOfferedCurrentFirstAndOtherParagraphsDropped() {
        val p = LegalRisDiscoveryProvider(Recording(), fetchJson = { fixture("ris-api-v26-abgb-678.json") })
        val out = p.discover(ResearchQuery("§ 678 ABGB", "q1"))
        // The API answer contains 35 hits (§ 0, § 1503 versions …); only the two § 678 versions survive.
        assertEquals(listOf(
            "https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR40172909/NOR40172909.html",
            "https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018403/NOR12018403.html"), out.map { it.url })
        assertTrue(out[0].title!!, out[0].title!!.startsWith("§ 678 ABGB · RIS-Fassung in Kraft seit 2017-01-01"))
        assertTrue(out[1].title!!, out[1].title!!.startsWith("§ 678 ABGB · AUSSER KRAFT seit 2016-12-31, historische Fassung ab 1812-01-01"))
    }

    @Test fun nonNormQuestionsApiFailuresAndZeroHitsFallBackVisibly() {
        val general = Recording()
        val p = LegalRisDiscoveryProvider(general, fetchJson = { throw IllegalStateException("HTTP 503") })
        assertEquals("https://example.org/general",
            p.discover(ResearchQuery("Wer leitet die EZB?", "q1")).single().url)
        assertNull(p.lastIssue)
        assertEquals("https://example.org/general",
            p.discover(ResearchQuery("§ 1151 ABGB", "q2")).single().url)
        assertTrue(p.lastIssue!!, p.lastIssue!!.startsWith("RIS-API nicht verfügbar: HTTP 503"))
        val empty = LegalRisDiscoveryProvider(general, fetchJson = { """{"OgdSearchResult":{"OgdDocumentResults":{"Hits":{"#text":"0"}}}}""" })
        empty.discover(ResearchQuery("§ 1157a ABGB", "q3"))
        assertTrue(empty.lastIssue!!, empty.lastIssue!!.contains("keine Fassung zu § 1157a ABGB"))
        // Hostile JSON never yields a non-official candidate.
        val hostile = LegalRisDiscoveryProvider(general, fetchJson = { fixture("ris-api-v26-abgb-364a.json")
            .replace("https://ogd.ris.bka.gv.at/Dokumente/Bundesnormen/NOR12018091/NOR12018091.html", "https://evil.invalid/NOR12018091.html") })
        assertEquals("https://example.org/general",
            hostile.discover(ResearchQuery("§ 364a ABGB", "q4")).single().url)
    }

    /** Real OGD document for § 364a ABGB: extractor now binds letter paragraphs. */
    @Test fun extractorBindsLetterParagraphFromRealDocument() {
        val text = HttpReadOnlyResearchProvider.responseText(fixture("ris-ogd-NOR12018091-abgb-364a.html"), "text/html", false, 40_000)
        assertEquals("364a", LegalRisParagraphExtractor.declaredParagraph(text))
        val out = LegalRisParagraphExtractor.extract("364a", text)!!
        assertTrue(out, out.startsWith("[RIS-Fassung in Kraft seit 01.01.1917"))
        assertTrue(out.contains("§ 364a. Wird jedoch die Beeinträchtigung durch eine Bergwerksanlage"))
        assertNull("§ 364 is another norm", LegalRisParagraphExtractor.extract("364", text))
        assertEquals("364a", LegalRisParagraphExtractor.paragraphNumber("https://www.ris.bka.gv.at/eli/jgs/1811/946/P364a/NOR12018091"))
    }
}
