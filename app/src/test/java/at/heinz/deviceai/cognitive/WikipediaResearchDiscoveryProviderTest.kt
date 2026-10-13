package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class WikipediaResearchDiscoveryProviderTest {
    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
        <api><query><searchinfo totalhits="3"/><search>
        <p ns="0" title="Künstliche Intelligenz" pageid="1"/>
        <p ns="1" title="Talk:Ignored" pageid="2"/>
        <p ns="0" title="Solarzelle" pageid="3"/>
        <p ns="0" title="Solarzelle" pageid="3"/>
        </search></query></api>"""

    @Test fun fixedHttpsApiRequestAndEncodedQuery() {
        val url = WikipediaResearchDiscoveryProvider.searchUrl("Solarzelle & KI")
        assertTrue(url.startsWith("https://de.wikipedia.org/w/api.php?"))
        assertTrue(url.contains("srsearch=Solarzelle+%26+KI"))
        assertThrows(IllegalArgumentException::class.java) {
            WikipediaResearchDiscoveryProvider.searchUrl("")
        }
    }
    @Test fun parsesMainNamespaceAndDeduplicatesResults() {
        val items = WikipediaResearchDiscoveryProvider.parseSearchResponse(xml)
        assertEquals(2, items.size)
        assertEquals("Künstliche Intelligenz", items[0].title)
        assertEquals("https://de.wikipedia.org/wiki/Künstliche_Intelligenz", items[0].url)
        assertEquals("https://de.wikipedia.org/wiki/Solarzelle", items[1].url)
    }
    @Test fun rejectsDoctypeAndDoesNotExpandExternalEntities() {
        assertThrows(Exception::class.java) {
            WikipediaResearchDiscoveryProvider.parseSearchResponse(
                """<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><api><search><p ns="0" title="&xxe;"/></search></api>"""
            )
        }
    }
    @Test fun fakeApiYieldsNetworkFreeDiscoveryTests() {
        val provider = WikipediaResearchDiscoveryProvider { _ -> xml }
        assertTrue(provider.isGeneralSearchAvailable)
        assertEquals(2, provider.discover(ResearchQuery("Solarzelle", "q0")).size)
    }
}
