package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnScopedInternetModeTest {
    @Test fun offlineVetoSurvivesInternetReconnection() {
        for (mode in InternetAccessMode.values()) {
            assertEquals(InternetAccessMode.OFF, TurnScopedInternetMode.effective(InternetAccessMode.OFF, mode))
            assertEquals(InternetAccessMode.OFF, TurnScopedInternetMode.effective(mode, InternetAccessMode.OFF))
        }
    }

    @Test fun temporaryModeRevocationCannotBeUndoneByAnotherToggle() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val epoch = consent.revision()
        consent.setMode(InternetAccessMode.OFF)
        consent.setMode(InternetAccessMode.ALWAYS_ON)
        assertEquals(InternetAccessMode.OFF, TurnScopedInternetMode.effective(
            InternetAccessMode.ALWAYS_ON, consent.mode, consent.revision() == epoch))
    }

    @Test fun askIsNotUpgradedWhileTurnIsActive() {
        assertEquals(InternetAccessMode.ASK,
            TurnScopedInternetMode.effective(InternetAccessMode.ASK, InternetAccessMode.ALWAYS_ON))
        assertEquals(InternetAccessMode.ASK,
            TurnScopedInternetMode.effective(InternetAccessMode.ALWAYS_ON, InternetAccessMode.ASK))
        assertEquals(InternetAccessMode.ALWAYS_ON,
            TurnScopedInternetMode.effective(InternetAccessMode.ALWAYS_ON, InternetAccessMode.ALWAYS_ON))
    }

    @Test fun staleNativeContinuationIsRejectedBeforeDispatch() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val epoch = consent.revision()
        var calls = 0
        assertEquals("fresh", TurnScopedInternetMode.withCurrentConsentRevision(epoch, consent::revision) {
            calls++; "fresh"
        })
        consent.cancel()
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            TurnScopedInternetMode.withCurrentConsentRevision(epoch, consent::revision) { calls++; "stale" }
        }
        assertEquals(1, calls)
    }

    @Test fun revocationDuringNativeGenerationDiscardsTheResult() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val epoch = consent.revision()
        var published = false
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            val answer = TurnScopedInternetMode.withCurrentConsentRevision(epoch, consent::revision) {
                consent.cancel(); "answer from an earlier native prompt"
            }
            published = answer.isNotBlank()
        }
        org.junit.Assert.assertFalse(published)
    }

    @Test fun modeReconnectionDoesNotAllowAnOldNativeConversationToResume() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val epoch = consent.revision()
        consent.setMode(InternetAccessMode.OFF)
        consent.setMode(InternetAccessMode.ALWAYS_ON)
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            TurnScopedInternetMode.withCurrentConsentRevision(epoch, consent::revision) {
                throw AssertionError("native continuation must not run")
            }
        }
    }

    @Test fun newlyMintedSessionDuringCaptureCannotReachTheOldTurnDispatcher() {
        val consent = InternetConsentPolicy(InternetAccessMode.ALWAYS_ON)
        val epoch = consent.revision()
        val query = WebToolRequest.Search("Wetter Wien")
        var executed = 0
        org.junit.Assert.assertThrows(IllegalStateException::class.java) {
            val session = TurnScopedInternetMode.withCurrentConsentRevision(epoch, consent::revision) {
                consent.setMode(InternetAccessMode.OFF)
                consent.setMode(InternetAccessMode.ALWAYS_ON)
                ApprovedWebToolGate(consent).authorizeAlwaysOn(query, ResearchMode.QUICK)!!
            }
            session.execute(query) { executed++; "must not run" }
        }
        assertEquals(0, executed)
    }
}
