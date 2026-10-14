package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class GeneralWebDiscoveryProviderTest {
    private class Fallback : ResearchDiscoveryProvider {
        var calls = 0
        override val isGeneralSearchAvailable = false
        override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
            calls++
            return listOf(ResearchSourceCandidate("https://de.wikipedia.org/wiki/Test", "Test"))
        }
    }

    @Test fun parsesPublicSearchResultAndDoesNotUseFallback() {
        val fallback = Fallback()
        val provider = GeneralWebDiscoveryProvider(fallback) {
            """<rss><channel><item><title>NASA</title><link>https://www.nasa.gov/science/</link></item></channel></rss>"""
        }
        val results = provider.discover(ResearchQuery("space", "q0"))
        assertEquals("https://www.nasa.gov/science/", results.first().url)
        assertEquals(1, results.size)
        assertEquals(0, fallback.calls)
        assertTrue(provider.isGeneralSearchAvailable)
    }

    @Test fun fallsBackOnSearchFailure() {
        val fallback = Fallback()
        val provider = GeneralWebDiscoveryProvider(fallback) { error("Search provider unavailable") }
        assertEquals(1, provider.discover(ResearchQuery("space", "q0")).size)
        assertEquals(1, fallback.calls)
    }

    @Test fun rejectsLocalHostsAndNonHttpsResults() {
        val html = """<a class="result__a" href="https://localhost/admin">Local</a><a class="result__a" href="http://public.org/">HTTP</a><a class="result__a" href="https://router.local/">Local</a><a class="result__a" href="https://example.org:8443/">Port</a>"""
        assertTrue(GeneralWebDiscoveryProvider.parseSearchResults(html).isEmpty())
    }
    @Test fun doesNotExtractUrlsFromPlainTextOrScripts() {
        val html = """<script>const tracking = "https://www.example.net/tracker"</script><p>https://www.example.com</p>"""
        assertTrue(GeneralWebDiscoveryProvider.parseSearchResults(html).isEmpty())
    }

    @Test fun rssRejectsExternalEntitiesAndProviderNavigationLinks() {
        assertThrows(IllegalArgumentException::class.java) {
            GeneralWebDiscoveryProvider.parseSearchResults("""<!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel/></rss>""")
        }
        val xml="""<rss><channel><link>https://www.bing.com/search</link><item><title>Nav</title><link>https://www.bing.com/search?q=secret</link></item><item><title>Local</title><link>https://127.0.0.1/</link></item></channel></rss>"""
        assertTrue(GeneralWebDiscoveryProvider.parseSearchResults(xml).isEmpty())
    }

    @Test fun fallbackFailureIsVisibleAndRevocationStopsBothProviders() {
        val fallback=Fallback()
        val provider=GeneralWebDiscoveryProvider(fallback,checkAccess={error("revoked")},fetchHtml={error("must not fetch")})
        assertThrows(IllegalStateException::class.java) { provider.discover(ResearchQuery("topic","q")) }
        assertEquals(0,fallback.calls)
        val broken=GeneralWebDiscoveryProvider(object:ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable=false
            override fun discover(query:ResearchQuery):List<ResearchSourceCandidate> = error("down")
        },fetchHtml={error("down")})
        assertTrue(broken.discover(ResearchQuery("topic","q")).isEmpty())
        assertFalse(broken.isGeneralSearchAvailable)
        assertTrue(broken.lastIssue!!.contains("Fallback"))
    }

    @Test fun capsDistinctSearchSources() {
        val html = (1..30).joinToString("") { i ->
            """<a class="result__a" href="https://public${i}.org/article">Source</a>"""
        }
        assertEquals(8, GeneralWebDiscoveryProvider.parseSearchResults(html).size)
    }

}

