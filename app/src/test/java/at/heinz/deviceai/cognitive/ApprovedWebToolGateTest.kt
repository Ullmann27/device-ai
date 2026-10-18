package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ApprovedWebToolGateTest {
    @Test fun alwaysOnAllowsOnlyOneBoundedWebSearchAtATime() {
        val consent=InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val gate=ApprovedWebToolGate(consent)
        val search=WebToolRequest.Search("verified Kotlin documentation")
        assertNull(gate.prepare(search))
        val session=gate.authorizeAlwaysOn(search,ResearchMode.QUICK)!!
        var actions=0
        session.execute(search) { actions++ }
        assertEquals(1,actions)
        assertThrows(IllegalStateException::class.java) {
            session.execute(search) { actions++ }
        }
        assertEquals(1,actions)
        assertNull(gate.authorizeAlwaysOn(WebToolRequest.Read("http://localhost/"),ResearchMode.QUICK))
        assertNull(gate.authorizeAlwaysOn(WebToolRequest.Search(" "),ResearchMode.QUICK))
    }

    @Test fun alwaysOnRevocationInvalidatesExistingWebSession() {
        val consent=InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val gate=ApprovedWebToolGate(consent)
        val search=WebToolRequest.Search("public sources")
        val session=gate.authorizeAlwaysOn(search,ResearchMode.QUICK)!!
        consent.setMode(InternetAccessMode.OFF)
        var executed=false
        assertThrows(IllegalStateException::class.java) { session.execute(search) { executed=true } }
        assertFalse(executed)
        assertNull(gate.authorizeAlwaysOn(search,ResearchMode.QUICK))
        consent.setMode(InternetAccessMode.ASK)
        assertNull(gate.authorizeAlwaysOn(search,ResearchMode.QUICK))
        assertNotNull(gate.prepare(search))
    }

    @Test fun alwaysOnStillEnforcesExactReadUrl() {
        val gate=ApprovedWebToolGate(InternetConsentPolicy(InternetAccessMode.ALWAYS_ON))
        val call=WebToolRequest.Read("https://www.wikipedia.org/wiki/Science")
        val session=gate.authorizeAlwaysOn(call,ResearchMode.QUICK)!!
        var reads=0
        val provider=session.provider(object:ResearchProvider {
            override fun retrieve(request:ResearchRequest):ResearchResult {
                reads++
                return ResearchResult(emptyList(),emptyList())
            }
        })
        session.execute(call) {
            assertThrows(IllegalArgumentException::class.java) {
                provider.retrieve(ResearchRequest("q","https://www.nasa.gov"))
            }
            provider.retrieve(ResearchRequest("q",call.url))
        }
        assertEquals(1,reads)
    }

    @Test fun searchRequiresExactOneShotAuthorization() {
        val gate=ApprovedWebToolGate(InternetConsentPolicy())
        val search=WebToolRequest.Search("solar energy")
        val session=gate.approve(gate.prepare(search)!!,ResearchMode.QUICK)!!
        var calls=0
        assertThrows(IllegalStateException::class.java) { session.execute(WebToolRequest.Search("different")) { calls++ } }
        assertEquals(0,calls)
        session.execute(search) { calls++ }
        assertEquals(1,calls)
        assertThrows(IllegalStateException::class.java) { session.execute(search) { calls++ } }
        assertEquals(1,calls)
    }

    @Test fun disabledModeCancelsPendingCall() {
        val consent=InternetConsentPolicy();val gate=ApprovedWebToolGate(consent)
        val search=WebToolRequest.Search("weather")
        val ticket=gate.prepare(search)!!
        consent.setMode(InternetAccessMode.OFF)
        assertNull(gate.approve(ticket,ResearchMode.QUICK))
        assertNull(gate.prepare(search))
    }

    @Test fun readRequiresHttpsAndCannotExpandToAnotherSource() {
        val gate=ApprovedWebToolGate(InternetConsentPolicy())
        assertNull(gate.prepare(WebToolRequest.Read("http://example.org")))
        assertNull(gate.prepare(WebToolRequest.Read("https://user@example.org")))
        val call=WebToolRequest.Read("https://www.wikipedia.org/page")
        val session=gate.approve(gate.prepare(call)!!,ResearchMode.QUICK)!!
        var reads=0
        val provider=session.provider(object:ResearchProvider {
            override fun retrieve(request:ResearchRequest):ResearchResult { reads++;return ResearchResult(emptyList(),emptyList()) }
        })
        assertThrows(IllegalStateException::class.java) { provider.retrieve(ResearchRequest("q",call.url)) }
        session.execute(call) {
            assertThrows(IllegalArgumentException::class.java) { provider.retrieve(ResearchRequest("q","https://www.nasa.gov/")) }
            provider.retrieve(ResearchRequest("q",call.url))
        }
        assertEquals(1,reads)
    }

    @Test fun cancellationNeverCallsProvider() {
        val gate=ApprovedWebToolGate(InternetConsentPolicy())
        val ticket=gate.prepare(WebToolRequest.Search("topic"))!!
        gate.cancel()
        assertNull(gate.approve(ticket,ResearchMode.QUICK))
    }

    @Test fun blocksLocalAndReservedHostsBeforeGrant() {
        val gate=ApprovedWebToolGate(InternetConsentPolicy())
        for(url in listOf("https://localhost/path","https://router.local/path","https://host.internal/path",
            "https://example.invalid/path","https://www.example.org:8443/path","https://127.0.0.1/"))
            assertNull(url,gate.prepare(WebToolRequest.Read(url)))
    }

    @Test fun invalidReplacementRevokesOldConsent() {
        val consent=InternetConsentPolicy();val gate=ApprovedWebToolGate(consent)
        val ticket=gate.prepare(WebToolRequest.Search("climate"))!!
        assertNull(gate.prepare(WebToolRequest.Search("")))
        assertNull(gate.approve(ticket,ResearchMode.QUICK))
    }

    @Test fun mismatchedApprovalRevokesPendingConsent() {
        val gate=ApprovedWebToolGate(InternetConsentPolicy())
        val old=gate.prepare(WebToolRequest.Search("climate"))!!
        val current=gate.prepare(WebToolRequest.Search("other"))!!
        assertNull(gate.approve(old,ResearchMode.QUICK))
        assertNull(gate.approve(current,ResearchMode.QUICK))
    }
}
