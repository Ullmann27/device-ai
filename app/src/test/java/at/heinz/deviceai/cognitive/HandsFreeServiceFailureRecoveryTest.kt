package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.ContinuousConversationMode.Decision
import at.heinz.deviceai.cognitive.ContinuousConversationMode.State
import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field P0 (Fold7, installed Code54 0.3.53, 12.11.2026): „Gesprächsmodus pausiert: Der lokale
 * Spracherkennungsdienst meldet einen Fehler …“ after the mode switched itself on and off.
 * Replays the activity's call (`listenFailed` with the per-failure budget) on the pure state machine.
 */
class HandsFreeServiceFailureRecoveryTest {
    private val session = "s-field"

    private fun listening() = ContinuousConversationMode().also { assertTrue(it.start(session)) }

    /** Exactly what AgentActivity.onConversationListenFailed passes for [failure]. */
    private fun fail(mode: ContinuousConversationMode, failure: Failure): Decision =
        mode.listenFailed(mode.listeningTurn(session)!!, session,
            silence = ContinuousConversationMode.isSilence(failure),
            recoverable = ContinuousConversationMode.isRecoverable(failure),
            retryBudget = ContinuousConversationMode.recoveryBudget(failure))

    @Test fun serviceFailureGetsOneDelayedFreshReArmThenPausesForOneTapResume() {
        val mode = listening()
        assertEquals(Decision.RETRY_LISTEN, fail(mode, Failure.SERVICE_FAILURE))
        assertTrue("the single re-arm must not reuse the dead pooled recognizer", mode.coldRecognizerRecommended)
        assertTrue(mode.retryDelayMs(Failure.SERVICE_FAILURE) >= 1_200L)
        // Second service failure: no further automatic toggling, a visible pause instead.
        assertEquals(Decision.PAUSE, fail(mode, Failure.SERVICE_FAILURE))
        assertEquals(State.PAUSED, mode.state)
        assertTrue("paused, not killed", mode.active)
        assertTrue(mode.coldRecognizerRecommended)
        // One owner tap resumes listening with a fresh recognizer.
        assertEquals(Decision.LISTEN, mode.ownerTap(session))
        assertEquals(State.LISTENING, mode.state)
        assertTrue(mode.coldRecognizerRecommended)
    }

    @Test fun repeatedServiceFailuresNeverLoopWithoutTheOwner() {
        val mode = listening()
        var automaticReArms = 0
        repeat(50) {
            val token = mode.listeningTurn(session) ?: return@repeat
            if (mode.listenFailed(token, session, false,
                    ContinuousConversationMode.isRecoverable(Failure.SERVICE_FAILURE),
                    ContinuousConversationMode.recoveryBudget(Failure.SERVICE_FAILURE)) == Decision.RETRY_LISTEN)
                automaticReArms++
        }
        assertEquals(ContinuousConversationMode.SERVICE_FAILURE_RETRY_BUDGET, automaticReArms)
        assertEquals(1, automaticReArms)
        assertEquals(State.PAUSED, mode.state)
    }

    @Test fun benignSilenceUsesThreeRetriesThenRequiresOwnerResume() {
        for (failure in listOf(Failure.NO_MATCH, Failure.NO_INPUT, Failure.TIMEOUT)) {
            val mode = listening()
            assertEquals(0, ContinuousConversationMode.recoveryBudget(failure))
            assertEquals(3, ContinuousConversationMode.MAX_SILENT_RETRIES)
            repeat(3) { attempt ->
                assertEquals(Decision.RETRY_LISTEN, fail(mode, failure))
                assertEquals(attempt >= 1, mode.coldRecognizerRecommended)
                assertEquals(State.LISTENING, mode.state)
            }
            assertEquals(Decision.PAUSE, fail(mode, failure))
            assertTrue(failure.name, mode.active)
            assertEquals(State.PAUSED, mode.state)
            assertEquals(Decision.IGNORE, mode.restoreForeground(session, 1_000, true, false))
            assertEquals(State.PAUSED, mode.state)
            assertEquals(Decision.LISTEN, mode.ownerTap(session))
        }
    }

    @Test fun busyKeepsItsDocumentedBoundedScheduleAndHardErrorsPauseAtOnce() {
        assertEquals(ContinuousConversationMode.MAX_RECOVERY_RETRIES,
            ContinuousConversationMode.recoveryBudget(Failure.BUSY))
        val mode = listening()
        assertEquals(Decision.RETRY_LISTEN, fail(mode, Failure.BUSY))
        assertFalse(mode.coldRecognizerRecommended) // unchanged #641 schedule: cold from the second retry
        assertEquals(800L, mode.retryDelayMs(Failure.BUSY))
        for (hard in listOf(Failure.PERMISSION, Failure.LANGUAGE_UNAVAILABLE, Failure.LANGUAGE_UNSUPPORTED,
                Failure.NETWORK, Failure.SERVICE_UNAVAILABLE)) {
            val m = listening()
            assertEquals(hard.name, 0, ContinuousConversationMode.recoveryBudget(hard))
            assertEquals(hard.name, Decision.PAUSE, fail(m, hard))
            assertTrue(m.active)
            assertEquals(LocalSpeechInputPolicy.message(hard), ContinuousConversationMode.pauseReason(hard))
        }
    }

    @Test fun serviceFailurePauseReasonIsHonestAboutTheSingleAttempt() {
        val reason = ContinuousConversationMode.pauseReason(Failure.SERVICE_FAILURE)
        assertTrue(reason.startsWith(LocalSpeechInputPolicy.message(Failure.SERVICE_FAILURE)))
        assertTrue(reason.contains("Ein automatischer Versuch"))
        assertFalse(reason.contains("Mehrere"))
        assertTrue(reason.contains("beim nächsten Start"))
    }
}
