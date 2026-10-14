package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ApprovedWebToolGateTest {
    private class FakeDiscovery : ResearchDiscoveryProvider {
        var calls = 0
        override val isGeneralSearchAvailable = true
        override fun discover(query: ResearchQuery): List<ResearchSourceCandidate> {
            calls++
            return listOf(ResearchSourceCandidate("https://example.org/public", "Example"))
        }
    }

    @Test fun searchRequiresExactOneShotAuthorization() {
        val consent = InternetConsentPolicy()
        val discovery = FakeDiscovery()
        val gate = ApprovedWebToolGate(consent, discovery) { error("read must not run") }
        val search = WebToolRequest.Search("solar energy")
        assertNull(gate.execute(search))
        assertTrue(gate.request(search))
        assertNull(gate.execute(WebToolRequest.Search("different query")))
        assertEquals(0, discovery.calls)
        assertTrue(gate.request(search))
        assertEquals(1, gate.execute(search)!!.sources.size)
        assertEquals(1, discovery.calls)
        assertNull(gate.execute(search))
    }

    @Test fun disabledModeCancelsPendingCall() {
        val consent = InternetConsentPolicy()
        val discovery = FakeDiscovery()
        val gate = ApprovedWebToolGate(consent, discovery) {}
        val search = WebToolRequest.Search("weather")
        assertTrue(gate.request(search))
        consent.setMode(InternetAccessMode.OFF)
        assertNull(gate.execute(search))
        assertEquals(0, discovery.calls)
        assertFalse(gate.request(search))
    }

    @Test fun readRequiresHttpsAndNeverExecutesWithoutPermission() {
        var reads = 0
        val gate = ApprovedWebToolGate(InternetConsentPolicy(), FakeDiscovery()) { url ->
            reads++
            ResearchDocument(url, "Example", 1L, "a".repeat(64), "text/plain",
                SourceIdentity("example.org", "https://example.org"), "Verified test content")
        }
        assertFalse(gate.request(WebToolRequest.Read("http://example.org")))
        assertFalse(gate.request(WebToolRequest.Read("https://user@example.org")))
        val page = WebToolRequest.Read("https://example.org/page")
        assertTrue(gate.request(page))
        assertEquals("web_read", gate.execute(page)!!.operation)
        assertEquals(1, reads)
        assertNull(gate.execute(page))
        assertEquals(1, reads)
    }

    @Test fun cancellationNeverCallsProvider() {
        val discovery = FakeDiscovery()
        val gate = ApprovedWebToolGate(InternetConsentPolicy(), discovery) {}
        val search = WebToolRequest.Search("topic")
        assertTrue(gate.request(search))
        gate.cancel()
        assertNull(gate.execute(search))
        assertEquals(0, discovery.calls)
    }
}
