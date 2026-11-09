package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.ContinuousConversationMode.Decision
import at.heinz.deviceai.cognitive.ContinuousConversationMode.State
import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 Fold7 finding (#641, #642): after a reply the hands-free microphone reported
 * „Der lokale Spracherkenner ist gerade belegt“ and stayed paused for good. A recoverable
 * handover failure must be retried a bounded number of times (with a cold recognizer
 * recommended), must never loop forever and must recover for the following turns.
 */
class ContinuousConversationRecoveryTest {
    private val session = "s-busy"

    private fun listening(): ContinuousConversationMode = ContinuousConversationMode().also { assertTrue(it.start(session)) }

    private fun failBusy(mode: ContinuousConversationMode): Decision =
        mode.listenFailed(mode.listeningTurn(session)!!, session, silence = false,
            recoverable = ContinuousConversationMode.isRecoverable(Failure.BUSY))

    private fun replyCycle(mode: ContinuousConversationMode, text: String, utterance: String) {
        assertEquals(Decision.SEND, mode.recognized(mode.listeningTurn(session)!!, session, text))
        mode.assistantSpeechQueued(utterance)
        assertEquals(Decision.LISTEN, mode.assistantSpeechFinished(utterance, interrupted = false))
        assertEquals(Decision.LISTEN, mode.resume(session, canListen = true, busy = false))
    }

    @Test fun busyIsRetriedBoundedThenPausesWithColdRecognizerRecommended() {
        val mode = listening()
        val tokens = mutableListOf<Long>()
        repeat(ContinuousConversationMode.MAX_RECOVERY_RETRIES) { attempt ->
            tokens += mode.listeningTurn(session)!!
            assertEquals("attempt ${attempt + 1}", Decision.RETRY_LISTEN, failBusy(mode))
            assertEquals(State.LISTENING, mode.state)
            assertEquals(attempt + 1, mode.recoveryAttemptsUsed)
            assertEquals(attempt + 1 >= 2, mode.coldRecognizerRecommended)
        }
        // Delays grow; none is zero (the service needs time to free its session).
        assertTrue(mode.recoveryRetryDelayMs() >= 800L)
        assertEquals(Decision.PAUSE, failBusy(mode))
        assertEquals(State.PAUSED, mode.state)
        assertTrue(mode.coldRecognizerRecommended)
        // Every retry opened a new turn so stale callbacks of the failed starts are ignored.
        assertEquals(tokens.size, tokens.distinct().size)
        assertEquals(Decision.IGNORE, mode.listenFailed(tokens.first(), session, silence = false, recoverable = true))
    }

    @Test fun ownerTapAfterBusyPauseKeepsColdRecommendationUntilConsumed() {
        val mode = listening()
        repeat(ContinuousConversationMode.MAX_RECOVERY_RETRIES + 1) { failBusy(mode) }
        assertEquals(State.PAUSED, mode.state)
        assertEquals(Decision.LISTEN, mode.ownerTap(session))
        assertTrue("tap after busy pause must start a fresh native recognizer", mode.coldRecognizerRecommended)
        assertEquals(0, mode.recoveryAttemptsUsed)
        mode.coldRecognizerUsed()
        assertFalse(mode.coldRecognizerRecommended)
    }

    @Test fun successfulRecognitionResetsRecoveryBudgetForTheNextTurn() {
        val mode = listening()
        assertEquals(Decision.RETRY_LISTEN, failBusy(mode))
        assertEquals(Decision.RETRY_LISTEN, failBusy(mode))
        replyCycle(mode, "Wie wird das Wetter?", "u1")
        assertEquals(0, mode.recoveryAttemptsUsed)
        assertFalse(mode.coldRecognizerRecommended)
        // Full budget available again.
        repeat(ContinuousConversationMode.MAX_RECOVERY_RETRIES) { assertEquals(Decision.RETRY_LISTEN, failBusy(mode)) }
        assertEquals(Decision.PAUSE, failBusy(mode))
    }

    @Test fun twentyHandsFreeTurnsWithInjectedBusyAfterEveryReplySurvive() {
        val mode = listening()
        var retries = 0
        repeat(20) { i ->
            // The recognizer is busy right after TTS released audio (the Fold7 pattern).
            if (failBusy(mode) == Decision.RETRY_LISTEN) retries++
            assertEquals(State.LISTENING, mode.state)
            replyCycle(mode, "Frage Nummer ${i + 1}", "u$i")
            assertEquals(State.LISTENING, mode.state)
        }
        assertEquals(20, retries)
        assertTrue(mode.active)
    }

    @Test fun nonRecoverableErrorsStillPauseImmediately() {
        val mode = listening()
        for (failure in listOf(Failure.PERMISSION, Failure.LANGUAGE_UNAVAILABLE, Failure.SERVICE_UNAVAILABLE, Failure.NETWORK)) {
            assertFalse(failure.name, ContinuousConversationMode.isRecoverable(failure))
        }
        assertEquals(Decision.PAUSE, mode.listenFailed(mode.listeningTurn(session)!!, session, silence = false,
            recoverable = ContinuousConversationMode.isRecoverable(Failure.PERMISSION)))
        assertEquals(State.PAUSED, mode.state)
        assertFalse(mode.coldRecognizerRecommended)
    }

    @Test fun silenceClassificationUsesStructuredFailuresNotMessageText() {
        assertTrue(ContinuousConversationMode.isSilence(Failure.NO_MATCH))
        assertTrue(ContinuousConversationMode.isSilence(Failure.NO_INPUT))
        assertTrue(ContinuousConversationMode.isSilence(Failure.TIMEOUT))
        assertFalse(ContinuousConversationMode.isSilence(Failure.BUSY))
        assertFalse(ContinuousConversationMode.isRecoverable(Failure.NO_MATCH))
    }

    @Test fun spokenStopDuringRecoveryEndsTheMode() {
        val mode = listening()
        assertEquals(Decision.RETRY_LISTEN, failBusy(mode))
        assertEquals(Decision.STOP, mode.recognized(mode.listeningTurn(session)!!, session, "Stopp"))
        assertFalse(mode.active)
        assertFalse(mode.coldRecognizerRecommended)
        assertEquals(Decision.IGNORE, mode.listenFailed(mode.turn, session, silence = false, recoverable = true))
    }

    @Test fun bargeInTapDuringReplyThenBusyRecoversWithoutLoop() {
        val mode = listening()
        assertEquals(Decision.SEND, mode.recognized(mode.listeningTurn(session)!!, session, "Erzähl mir etwas"))
        mode.assistantSpeechQueued("u1")
        assertEquals(Decision.LISTEN, mode.ownerTap(session))
        // TTS stop and microphone start race: the first start is busy, the retry succeeds.
        assertEquals(Decision.RETRY_LISTEN, failBusy(mode))
        assertEquals(Decision.SEND, mode.recognized(mode.listeningTurn(session)!!, session, "Danke, das reicht"))
        assertEquals(State.THINKING, mode.state)
        // The interrupted old utterance cannot reopen the microphone.
        assertEquals(Decision.IGNORE, mode.assistantSpeechFinished("u1", interrupted = true))
    }
}
