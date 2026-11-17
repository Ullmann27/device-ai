package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class ApprovedWebToolGateTest {
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

    @Test fun revokedDialogNeverDeliversApprovalOrDeclineFallback() {
        for (accepted in listOf(false, true)) {
            val gate = ApprovedWebToolGate(InternetConsentPolicy())
            val pending = gate.prepare(WebToolRequest.Search("weather"))!!
            gate.cancel()
            var callbacks = 0
            assertFalse(gate.completeApproval(pending, ResearchMode.QUICK, accepted) { callbacks++ })
            assertEquals(0, callbacks)
        }
    }

    @Test fun staleDialogCannotCancelTheReplacementRequest() {
        for (accepted in listOf(false, true)) {
            val gate = ApprovedWebToolGate(InternetConsentPolicy())
            val old = gate.prepare(WebToolRequest.Search("old topic"))!!
            val replacement = gate.prepare(WebToolRequest.Search("new topic"))!!
            assertFalse(gate.completeApproval(old, ResearchMode.QUICK, accepted) { fail("Stale callback") })
            var calls = 0
            assertTrue(gate.completeApproval(replacement, ResearchMode.QUICK, true) { session ->
                assertNotNull(session)
                session!!.execute(replacement.call) { calls++ }
            })
            assertEquals(1, calls)
        }
    }

    @Test fun modeChangeAndReenableDoNotRevivePendingDialogs() {
        for (accepted in listOf(false, true)) {
            val consent = InternetConsentPolicy()
            val gate = ApprovedWebToolGate(consent)
            val pending = gate.prepare(WebToolRequest.Search("weather"))!!
            consent.setMode(InternetAccessMode.OFF)
            consent.setMode(InternetAccessMode.ASK)
            assertFalse(gate.completeApproval(pending, ResearchMode.QUICK, accepted) { fail("Revoked callback") })
        }
    }

    @Test fun currentDialogCompletesExactlyOnceIncludingGenuineDecline() {
        for (accepted in listOf(false, true)) {
            val gate = ApprovedWebToolGate(InternetConsentPolicy())
            val pending = gate.prepare(WebToolRequest.Search("weather"))!!
            var callbacks = 0
            assertTrue(gate.completeApproval(pending, ResearchMode.QUICK, accepted) { session ->
                callbacks++
                assertEquals(accepted, session != null)
            })
            assertFalse(gate.completeApproval(pending, ResearchMode.QUICK, !accepted) { callbacks++ })
            assertEquals(1, callbacks)
        }
    }

    @Test fun revokedInFlightResultCannotBePublishedOrReused() {
        for (mode in listOf(InternetAccessMode.ASK, InternetAccessMode.ALWAYS_ON)) {
            val gate = ApprovedWebToolGate(InternetConsentPolicy(mode))
            val call = WebToolRequest.Search("weather")
            val session = if (mode == InternetAccessMode.ASK)
                gate.approve(gate.prepare(call)!!, ResearchMode.QUICK)!!
            else gate.authorizeAlwaysOn(call, ResearchMode.QUICK)!!
            var published = false
            assertThrows(IllegalStateException::class.java) {
                session.execute(call) {
                    gate.cancel()
                    "late result"
                }
                published = true
            }
            assertFalse(published)
            assertFalse(session.isAuthorized())
            assertThrows(IllegalStateException::class.java) { session.execute(call) { fail("Reused") } }
        }
    }
}
