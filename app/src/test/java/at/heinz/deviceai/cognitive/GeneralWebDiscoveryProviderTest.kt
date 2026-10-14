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
            """<a href="https://www.nasa.gov/science/">NASA</a>"""
        }
        val results = provider.discover(ResearchQuery("space", "q0"))
        assertEquals("https://www.nasa.gov/science/", results.single().url)
        assertEquals(0, fallback.calls)
    }

    @Test fun fallsBackOnSearchFailure() {
        val fallback = Fallback()
        val provider = GeneralWebDiscoveryProvider(fallback) { error("Search provider unavailable") }
        assertEquals(1, provider.discover(ResearchQuery("space", "q0")).size)
        assertEquals(1, fallback.calls)
    }

    @Test fun rejectsLocalHostsAndNonHttpsResults() {
        val html = """<a href="https://localhost/admin">Local</a><a href="http://public.org/">HTTP</a><a href="https://router.local/">Local</a><a href="https://example.org:8443/">Port</a>"""
        assertTrue(GeneralWebDiscoveryProvider.parseSearchResults(html).isEmpty())
    }
}
