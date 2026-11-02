package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class WikipediaExtractResearchProviderTest {
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun response(url: String, xml: String, domain: String = "de.wikipedia.org"): ResearchResult {
        val document = ResearchDocument(
            url, null, 1_700_000_000_000L, hash(xml), "application/xml",
            SourceIdentity(domain, domain), xml
        )
        return ResearchResult(listOf(document), listOf(document.citation()))
    }

    private val solarXml = """
        <api><query><pages><page pageid="123" ns="0" title="Solarzelle">
        <extract xml:space="preserve">Eine Solarzelle wandelt Licht in elektrische Energie um &amp; nutzt Halbleiter.</extract>
        </page></pages></query></api>
    """.trimIndent()

    @Test fun boundedExtractRetainsOriginalArticleCitationAndSourceHash() {
        var requested: ResearchRequest? = null
        val downstream = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult =
                error("Full Wikipedia article HTML must not be downloaded")
        }
        val provider = WikipediaExtractResearchProvider(downstream) { request ->
            requested = request
            response(request.url, solarXml)
        }
        val articleUrl = "https://de.wikipedia.org/wiki/Solarzelle"
        val result = provider.retrieve(ResearchRequest("Wie funktionieren Solarzellen?", articleUrl, 128_000, 8_000))
        val doc = result.documents.single()
        assertEquals(articleUrl, doc.url)
        assertEquals(articleUrl, result.citations.single().url)
        assertEquals(doc.contentHash, result.citations.single().contentHash)
        assertEquals(hash(doc.content), doc.contentHash)
        assertEquals("text/plain", doc.mimeType)
        assertTrue(doc.content.contains("elektrische Energie"))
        assertTrue(doc.content.contains(" & nutzt"))
        assertEquals("de.wikipedia.org", doc.source.domain)
        assertEquals(24_000, requested!!.maxBytes)
        assertEquals(8_000, requested!!.timeoutMillis)
        assertEquals(0, requested!!.maxRedirects)
        assertTrue(requested!!.url.startsWith("https://de.wikipedia.org/w/api.php?"))
        assertTrue(requested!!.url.contains("exchars=1200"))
        assertTrue(requested!!.url.contains("titles=Solarzelle"))
    }

    @Test fun unrelatedOrModifiedUrlsStayInExistingSecureProvider() {
        var calls = 0
        val downstream = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult {
                calls++
                return response(request.url, solarXml, "example.org")
            }
        }
        val provider = WikipediaExtractResearchProvider(downstream) { error("Must not fetch extract") }
        for (url in listOf(
            "https://example.org/wiki/Solarzelle",
            "http://de.wikipedia.org/wiki/Solarzelle",
            "https://de.wikipedia.org/wiki/Solarzelle?oldid=1",
            "https://de.wikipedia.org:8443/wiki/Solarzelle",
            "https://de.wikipedia.org/wiki/A%2FB"
        )) {
            assertEquals(url, provider.retrieve(ResearchRequest("topic", url)).documents.single().url)
        }
        assertEquals(5, calls)
    }

    @Test fun doctypeExternalEntityAndWrongTitleAreRejected() {
        val entityXml = """<!DOCTYPE api [<!ENTITY injected SYSTEM "file:///etc/passwd">]>
            <api><query><pages><page title="Solarzelle"><extract>&injected;</extract></page></pages></query></api>"""
        assertThrows(IllegalArgumentException::class.java) {
            WikipediaExtractResearchProvider.parseExtract(entityXml, "Solarzelle")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WikipediaExtractResearchProvider.parseExtract(
                """<api><query><pages><page title="Andere Seite"><extract>Fake evidence</extract></page></pages></query></api>""",
                "Solarzelle"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WikipediaExtractResearchProvider.parseExtract(
                """<api><query><pages><page title="Solarzelle" missing=""><extract>Fake</extract></page></pages></query></api>""",
                "Solarzelle"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WikipediaExtractResearchProvider.parseExtract("x".repeat(24_001), "Solarzelle")
        }
    }

    @Test fun forgedTransportResponseDoesNotBecomeArticleEvidence() {
        val downstream = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult = error("No fallback to full HTML")
        }
        val provider = WikipediaExtractResearchProvider(downstream) { request ->
            response(request.url, solarXml, "example.org")
        }
        assertThrows(IllegalArgumentException::class.java) {
            provider.retrieve(ResearchRequest("topic", "https://de.wikipedia.org/wiki/Solarzelle"))
        }
        val redirected = WikipediaExtractResearchProvider(downstream) { request ->
            response("https://example.org/redirect", solarXml)
        }
        assertThrows(IllegalArgumentException::class.java) {
            redirected.retrieve(ResearchRequest("topic", "https://de.wikipedia.org/wiki/Solarzelle"))
        }
    }

    @Test fun missingExcerptDoesNotTurnIntoClaimOrCitation() {
        val downstream = object : ResearchProvider {
            override fun retrieve(request: ResearchRequest): ResearchResult = error("No fallback to full HTML")
        }
        val missing = WikipediaExtractResearchProvider(downstream) { request ->
            response(request.url, """<api><query><pages><page title="Solarzelle" missing=""/></pages></query></api>""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            missing.retrieve(ResearchRequest("topic", "https://de.wikipedia.org/wiki/Solarzelle"))
        }
    }
}
