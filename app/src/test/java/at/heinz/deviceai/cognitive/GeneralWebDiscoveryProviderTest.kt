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

    @Test fun solarQuestionFiltersDictionaryNoiseAndSearchesTheRealTopic() {
        var requested = ""
        val provider = GeneralWebDiscoveryProvider(fetchHtml = { url ->
            requested = url
            """<rss><channel>
                <item><title>wie – Bedeutung und Grammatik</title><link>https://www.dwds.de/wb/wie</link></item>
                <item><title>Facebook – Anmeldung</title><link>https://secure.facebook.com/</link></item>
                <item><title>Solarzelle – photovoltaischer Effekt</title><link>https://www.energy.gov/eere/solar/solar-photovoltaic-technology-basics</link></item>
            </channel></rss>"""
        })
        val result = provider.discover(ResearchQuery("Wie funktionieren Solarzellen?", "q0"))
        assertEquals(listOf("https://www.energy.gov/eere/solar/solar-photovoltaic-technology-basics"), result.map { it.url })
        assertTrue(requested.contains("q=solarzellen"))
        assertTrue(provider.isGeneralSearchAvailable)
    }

    @Test fun irrelevantSearchHitsCauseTopicFocusedWikipediaFallback() {
        var topic = ""
        val fallback = object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = false
            override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
                topic = query.text
                return listOf(
                    ResearchSourceCandidate("https://www.energy.gov/eere/solar/solar-photovoltaic-technology-basics", "Solarzelle")
                )
            }
        }
        val provider = GeneralWebDiscoveryProvider(fallback, fetchHtml = {
            """<rss><channel><item><title>Wie – Rechtschreibung</title>
            <link>https://www.duden.de/rechtschreibung/wie</link></item></channel></rss>"""
        })
        val result = provider.discover(ResearchQuery("Wie funktionieren Solarzellen?", "q0"))
        assertEquals("solarzellen", topic)
        assertEquals(1, result.size)
        assertEquals("Solarzelle", result.single().title)
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

    @Test fun publicGeneralSearchDoesNotDefaultToWikipedia() {
        val provider = GeneralWebDiscoveryProvider(fetchHtml = {
            """<rss><channel><item><title>Hacking Grundlagen</title>
                 <link>https://de.wikipedia.org/wiki/Hacking</link></item></channel></rss>"""
        })
        assertTrue(provider.discover(ResearchQuery("hacking", "q0")).isEmpty())
        assertFalse(provider.isGeneralSearchAvailable)
    }

    @Test fun combinesIndependentSearchEnginesForBroaderCoverage() {
        val provider = GeneralWebDiscoveryProvider(fetchHtml = { url ->
            if(url.contains("bing.com"))
                """<rss><channel><item><title>Hacking Einführung</title><link>https://owasp.org/</link></item></channel></rss>"""
            else """<a class="result__a" href="https://portswigger.net/web-security">Hacking und Websicherheit</a>"""
        })
        val results = provider.discover(ResearchQuery("hacking", "q0"))
        assertEquals(setOf("owasp.org", "portswigger.net"),
            results.map { java.net.URI(it.url).host }.toSet())
    }

    @Test fun secondaryPublicSearchWorksWhenBingIsUnavailable() {
        val visited = mutableListOf<String>()
        val wikipedia = Fallback()
        val provider = GeneralWebDiscoveryProvider(wikipedia, fetchHtml = { url ->
            visited += url
            if (url.contains("bing.com")) error("Bing temporarily unavailable")
            """<a class="result__a" href="https://www.nasa.gov/science/">NASA space research</a>"""
        })
        val sources = provider.discover(ResearchQuery("space", "q0"))
        assertEquals(2, visited.size)
        assertTrue(visited[0].startsWith("https://www.bing.com/"))
        assertTrue(visited[1].startsWith("https://html.duckduckgo.com/"))
        assertEquals(listOf("https://www.nasa.gov/science/"), sources.map { it.url })
        assertEquals(0, wikipedia.calls)
        assertTrue(provider.isGeneralSearchAvailable)
    }

    @Test fun revocationPreventsSecondSearchProvider() {
        var active = true
        val visited = mutableListOf<String>()
        val provider = GeneralWebDiscoveryProvider(
            checkAccess = { check(active) { "approval revoked" } },
            fetchHtml = { url -> visited += url; active = false; "<rss><channel/></rss>" }
        )
        assertThrows(IllegalStateException::class.java) {
            provider.discover(ResearchQuery("space", "q0"))
        }
        assertEquals(1, visited.size)
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

    @Test fun revocationWhileFallbackIsRunningDiscardsItsSources() {
        var authorized = true
        val provider = GeneralWebDiscoveryProvider(object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = false
            override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
                authorized = false
                return listOf(ResearchSourceCandidate("https://de.wikipedia.org/wiki/Test", "Test"))
            }
        }, checkAccess = { check(authorized) { "revoked" } }, fetchHtml = { "<rss><channel/></rss>" })
        assertThrows(IllegalStateException::class.java) { provider.discover(ResearchQuery("topic", "q")) }
    }

    @Test fun interruptedPrimarySearchDoesNotStartFallbackAndPreservesInterruption() {
        val fallback = Fallback()
        val provider = GeneralWebDiscoveryProvider(fallback, fetchHtml = { throw InterruptedException("cancelled") })
        try {
            assertThrows(InterruptedException::class.java) { provider.discover(ResearchQuery("topic", "q")) }
            assertEquals(0, fallback.calls)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun fallbackCancellationPropagatesToItsCaller() {
        val provider = GeneralWebDiscoveryProvider(object : ResearchDiscoveryProvider {
            override val isGeneralSearchAvailable = false
            override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> =
                throw java.util.concurrent.CancellationException("cancelled")
        }, fetchHtml = { "<rss><channel/></rss>" })
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            provider.discover(ResearchQuery("topic", "q"))
        }
    }


    @Test fun bothSearchTransportsFailingIsDistinctFromGenuinelyEmptySearchResults() {
        var offline = true
        val provider = GeneralWebDiscoveryProvider(fetchHtml = {
            if (offline) throw java.io.IOException("search network offline")
            """<rss><channel><item><title>NASA space research</title>
                <link>https://www.nasa.gov/science/</link></item></channel></rss>"""
        })
        assertTrue(provider.discover(ResearchQuery("space", "q0")).isEmpty())
        assertTrue(provider.allSearchEndpointsFailed)
        assertFalse(provider.isGeneralSearchAvailable)

        offline = false
        assertEquals(1, provider.discover(ResearchQuery("space", "q0")).size)
        assertFalse(provider.allSearchEndpointsFailed)
        assertTrue(provider.isGeneralSearchAvailable)

        val empty = GeneralWebDiscoveryProvider(fetchHtml = { "<rss><channel/></rss>" })
        assertTrue(empty.discover(ResearchQuery("space", "q0")).isEmpty())
        assertFalse(empty.allSearchEndpointsFailed)
    }

    @Test fun incompleteSearchIsNotMistakenForConfirmedZeroResults() {
        val provider = GeneralWebDiscoveryProvider(fetchHtml = { url ->
            if (url.contains("bing.com")) "<rss><channel/></rss>"
            else throw java.io.IOException("secondary temporarily unavailable")
        })
        assertTrue(provider.discover(ResearchQuery("Photovoltaik", "q0")).isEmpty())
        assertFalse(provider.allSearchEndpointsFailed)
        assertTrue(provider.searchIncomplete)
        val recovered = GeneralWebDiscoveryProvider(fetchHtml = { "<rss><channel/></rss>" })
        assertTrue(recovered.discover(ResearchQuery("Photovoltaik", "q0")).isEmpty())
        assertFalse(recovered.searchIncomplete)
    }
    @Test fun regionalSearchPortalHomepagesNeverCountAsDomainEvidence() {
        val rss = """<rss><channel>
            <item><title>RIS § 1295 ABGB – Google Suche</title><link>https://www.google.de/index.html</link></item>
            <item><title>RIS § 1295 ABGB – Yahoo Suche</title><link>https://de.search.yahoo.com/</link></item>
            <item><title>RIS § 1295 ABGB – Google Bilder</title><link>https://images.google.de/?hl=de</link></item>
            <item><title>RIS § 1295 ABGB – Gesetzestext</title><link>https://www.ris.bka.gv.at/eli/jgs/1811/946/P1295/NOR12019037</link></item>
        </channel></rss>"""
        val candidates = GeneralWebDiscoveryProvider.parseSearchResults(rss)
        assertEquals(listOf("www.ris.bka.gv.at"), candidates.map { java.net.URI(it.url).host })
    }

    @Test fun searchDomainLookalikesAreNotRejectedBySubstringMatches() {
        val rss = """<rss><channel>
            <item><title>Technikbericht</title><link>https://google.com.evidence.example.org/article</link></item>
            <item><title>Yahoo News</title><link>https://news.yahoo.co.jp/search</link></item>
        </channel></rss>"""
        val candidates = GeneralWebDiscoveryProvider.parseSearchResults(rss)
        assertEquals(listOf("google.com.evidence.example.org"), candidates.map { java.net.URI(it.url).host })
    }

}
