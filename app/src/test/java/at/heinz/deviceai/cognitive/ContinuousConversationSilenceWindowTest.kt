package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.ContinuousConversationMode.Decision
import at.heinz.deviceai.cognitive.ContinuousConversationMode.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code63: a speech pause is not a goodbye. Field complaint: after a few silent recognizer rounds the
 * hands-free session paused („Ich habe nichts gehört.“) and needed another tap. With an owner-selected
 * listening window silence only re-arms the microphone until the window closes.
 */
class ContinuousConversationSilenceWindowTest {
    private val session = "s-1"

    private fun mode(windowMs: Long) = ContinuousConversationMode().apply {
        silenceWindowMs = windowMs
        assertTrue(start(session))
    }

    private fun silent(mode: ContinuousConversationMode, nowMs: Long): Decision =
        mode.listenFailed(mode.listeningTurn(session)!!, session, silence = true, nowMs = nowMs)

    @Test fun silenceInsideTheWindowKeepsListeningWellBeyondThreeAttempts() {
        val mode = mode(120_000L)
        var now = 1_000L
        repeat(20) {
            assertEquals("round $it", Decision.RETRY_LISTEN, silent(mode, now))
            assertEquals(State.LISTENING, mode.state)
            now += 5_000L
        }
        // 20 rounds × 5 s = 100 s after the first silent result: still inside 120 s.
        assertEquals(Decision.RETRY_LISTEN, silent(mode, 1_000L + 119_999L))
        assertEquals(Decision.PAUSE, silent(mode, 1_000L + 120_000L))
        assertEquals(State.PAUSED, mode.state)
        assertTrue(mode.coldRecognizerRecommended)
    }

    @Test fun realSpeechReopensAFullWindowForTheNextPause() {
        val mode = mode(60_000L)
        assertEquals(Decision.RETRY_LISTEN, silent(mode, 0L))
        assertEquals(Decision.RETRY_LISTEN, silent(mode, 50_000L))
        assertEquals(Decision.SEND, mode.recognized(mode.listeningTurn(session)!!, session, "Wie spät ist es?", 55_000L))
        mode.assistantSpeechQueued("u-1")
        assertEquals(Decision.LISTEN, mode.assistantSpeechFinished("u-1", interrupted = false))
        assertEquals(Decision.LISTEN, mode.resume(session, canListen = true, busy = false))
        // 70 s after the first silence of the previous pause, but a new pause: the window starts again.
        assertEquals(Decision.RETRY_LISTEN, silent(mode, 70_000L))
        assertEquals(Decision.RETRY_LISTEN, silent(mode, 129_999L))
        assertEquals(Decision.PAUSE, silent(mode, 130_000L))
    }

    @Test fun anEmptyRecognitionCountsAsSilenceInsideTheSameWindow() {
        val mode = mode(60_000L)
        assertEquals(Decision.RETRY_LISTEN, mode.recognized(mode.listeningTurn(session)!!, session, "  ", 0L))
        assertEquals(Decision.RETRY_LISTEN, silent(mode, 30_000L))
        assertEquals(Decision.PAUSE, mode.recognized(mode.listeningTurn(session)!!, session, "", 60_000L))
    }

    @Test fun stopAndPauseWordsStillEndOrPauseImmediately() {
        val stop = mode(600_000L)
        assertEquals(Decision.STOP, stop.recognized(stop.listeningTurn(session)!!, session, "Stopp", 1L))
        assertFalse(stop.active)
        val pause = mode(600_000L)
        assertEquals(Decision.PAUSE, pause.recognized(pause.listeningTurn(session)!!, session, "Warte kurz", 1L))
        assertEquals(State.PAUSED, pause.state)
    }

    @Test fun theWindowIsBoundedAndABackwardClockNeverListensForever() {
        val mode = mode(Long.MAX_VALUE)
        assertEquals(ContinuousConversationMode.MAX_SILENCE_WINDOW_MS, mode.silenceWindowMs)
        assertEquals(Decision.RETRY_LISTEN, silent(mode, 500_000L))
        assertEquals(Decision.PAUSE, silent(mode, 499_999L))
    }

    @Test fun nonSilenceFailuresKeepTheirBoundedRecovery() {
        val mode = mode(600_000L)
        repeat(ContinuousConversationMode.MAX_RECOVERY_RETRIES) {
            assertEquals(Decision.RETRY_LISTEN, mode.listenFailed(mode.listeningTurn(session)!!, session,
                silence = false, recoverable = true, nowMs = 1_000L))
        }
        assertEquals(Decision.PAUSE, mode.listenFailed(mode.listeningTurn(session)!!, session,
            silence = false, recoverable = true, nowMs = 1_000L))
    }

    @Test fun withoutAWindowOrAClockTheClassicThreeAttemptsApply() {
        val classic = mode(0L)
        repeat(ContinuousConversationMode.MAX_SILENT_RETRIES) {
            assertEquals(Decision.RETRY_LISTEN, silent(classic, it * 1_000L))
        }
        assertEquals(Decision.PAUSE, silent(classic, 10_000L))

        val noClock = mode(120_000L)
        repeat(ContinuousConversationMode.MAX_SILENT_RETRIES) {
            assertEquals(Decision.RETRY_LISTEN,
                noClock.listenFailed(noClock.listeningTurn(session)!!, session, silence = true))
        }
        assertEquals(Decision.PAUSE, noClock.listenFailed(noClock.listeningTurn(session)!!, session, silence = true))
    }

    @Test fun storedPreferenceMapsOnlyToSupportedWindows() {
        assertEquals(120, ContinuousConversationMode.silenceWindowSeconds(120))
        assertEquals(0, ContinuousConversationMode.silenceWindowSeconds(0))
        assertEquals(ContinuousConversationMode.DEFAULT_SILENCE_WINDOW_S,
            ContinuousConversationMode.silenceWindowSeconds(86_400))
        assertTrue(ContinuousConversationMode.SILENCE_WINDOW_CHOICES_S.all {
            it * 1_000L <= ContinuousConversationMode.MAX_SILENCE_WINDOW_MS
        })
    }
}
