package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.ContinuousConversationMode.Decision
import at.heinz.deviceai.cognitive.ContinuousConversationMode.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner requirement (#544 Q1-CONTINUOUS-01): an explicitly started conversation mode with
 * several natural turns, follow-up question, pause, stop and resumption, without a new
 * gesture for every sentence. Ordinary dictation and tool approvals stay unchanged.
 */
class ContinuousConversationModeTest {
    private val session = "s-1"

    private fun listeningMode(): Pair<ContinuousConversationMode, Long> {
        val mode = ContinuousConversationMode()
        assertTrue(mode.start(session))
        return mode to mode.listeningTurn(session)!!
    }

    /** One complete spoken turn: recognized → sent → reply spoken → listening again. */
    private fun completeTurn(mode: ContinuousConversationMode, text: String, utterance: String): Long {
        val token = mode.listeningTurn(session)!!
        assertEquals(Decision.SEND, mode.recognized(token, session, text))
        assertEquals(State.THINKING, mode.state)
        mode.assistantSpeechQueued(utterance)
        assertEquals(State.SPEAKING, mode.state)
        assertEquals(Decision.LISTEN, mode.assistantSpeechFinished(utterance, interrupted = false))
        assertEquals(Decision.LISTEN, mode.resume(session, canListen = true, busy = false))
        assertEquals(State.LISTENING, mode.state)
        return token
    }

    @Test fun dictationIsNotAConversationTurnUntilExplicitlyStarted() {
        val mode = ContinuousConversationMode()
        assertNull(mode.listeningTurn(session))
        assertFalse(mode.active)
        assertEquals(Decision.IGNORE, mode.recognized(0L, session, "Hallo"))
        assertEquals(Decision.IGNORE, mode.ownerTap(session))
    }

    @Test fun severalGermanTurnsIncludingFollowUpRunWithoutNewGesture() {
        val (mode, _) = listeningMode()
        val first = completeTurn(mode, "Mein Testkürzel ist Nebel 731.", "u1")
        val second = completeTurn(mode, "Wie lautet mein Testkürzel?", "u2")
        val third = completeTurn(mode, "Nicht vier, sondern fünf.", "u3")
        val fourth = completeTurn(mode, "Und warum?", "u4")
        assertTrue(first < second && second < third && third < fourth)
        assertEquals(State.LISTENING, mode.state)
    }

    @Test fun lateRecognizerResultOfAnOldTurnIsIgnored() {
        val (mode, oldToken) = listeningMode()
        completeTurn(mode, "Wie spät ist es?", "u1")
        assertEquals(Decision.IGNORE, mode.recognized(oldToken, session, "veraltete Erkennung"))
        assertEquals(State.LISTENING, mode.state)
    }

    @Test fun oldReplyCallbackCannotReopenTheMicrophone() {
        val (mode, token) = listeningMode()
        assertEquals(Decision.SEND, mode.recognized(token, session, "Erzähl mir etwas"))
        mode.assistantSpeechQueued("old")
        mode.assistantSpeechQueued("new") // a newer reply replaced the old utterance
        assertEquals(Decision.IGNORE, mode.assistantSpeechFinished("old", interrupted = true))
        assertEquals(State.SPEAKING, mode.state)
        assertEquals(Decision.LISTEN, mode.assistantSpeechFinished("new", interrupted = false))
    }

    @Test fun spokenStopEndsTheModeAndIsNotSentAsChat() {
        val (mode, token) = listeningMode()
        assertEquals(Decision.STOP, mode.recognized(token, session, "Stopp!"))
        assertFalse(mode.active)
        assertNull(mode.listeningTurn(session))
    }

    @Test fun stopInsideASentenceIsAnOrdinaryTurn() {
        val (mode, token) = listeningMode()
        assertEquals(Decision.SEND, mode.recognized(token, session, "Stopp die Musik bitte"))
        assertEquals(ContinuousConversationMode.Command.STOP, ContinuousConversationMode.command(" Gespräch beenden. "))
        assertNull(ContinuousConversationMode.command("Ohne Internetrecherche."))
    }

    @Test fun spokenPauseKeepsTheModeAndTapResumes() {
        val (mode, token) = listeningMode()
        assertEquals(Decision.PAUSE, mode.recognized(token, session, "Warte kurz"))
        assertEquals(State.PAUSED, mode.state)
        assertTrue(mode.active)
        assertNull(mode.listeningTurn(session))
        assertEquals(Decision.LISTEN, mode.ownerTap(session))
        completeTurn(mode, "Weiter mit der Frage von vorhin.", "u5")
    }

    @Test fun silenceIsRetriedOnceThenPauses() {
        val (mode, token) = listeningMode()
        assertEquals(Decision.RETRY_LISTEN, mode.listenFailed(token, session, silence = true))
        val retry = mode.listeningTurn(session)!!
        assertTrue(retry > token)
        assertEquals(Decision.PAUSE, mode.listenFailed(retry, session, silence = true))
        assertEquals(State.PAUSED, mode.state)
    }

    @Test fun nonSilenceRecognizerErrorPausesImmediately() {
        val (mode, token) = listeningMode()
        assertEquals(Decision.PAUSE, mode.listenFailed(token, session, silence = false))
        assertEquals(State.PAUSED, mode.state)
    }

    @Test fun ownerInterruptingTheReplyPausesInsteadOfListening() {
        val (mode, token) = listeningMode()
        mode.recognized(token, session, "Lies mir das vor")
        mode.assistantSpeechQueued("u1")
        assertEquals(Decision.PAUSE, mode.assistantSpeechFinished("u1", interrupted = true))
        assertEquals(State.PAUSED, mode.state)
    }

    @Test fun bargeInTapDuringReplyListensImmediately() {
        val (mode, token) = listeningMode()
        mode.recognized(token, session, "Erkläre das ausführlich")
        mode.assistantSpeechQueued("u1")
        assertEquals(Decision.LISTEN, mode.ownerTap(session))
        // The engine's later onStop(interrupted) for the replaced reply is ignored.
        assertEquals(Decision.IGNORE, mode.assistantSpeechFinished("u1", interrupted = true))
        assertEquals(State.LISTENING, mode.state)
    }

    @Test fun openDialogPausesSoNoVoiceTurnCanAnswerAnApprovalPrompt() {
        val (mode, token) = listeningMode()
        mode.recognized(token, session, "Öffne die Kamera-App")
        assertEquals(Decision.LISTEN, mode.replyIdle())
        assertEquals(Decision.PAUSE, mode.resume(session, canListen = false, busy = false))
        assertEquals(State.PAUSED, mode.state)
        assertNull(mode.listeningTurn(session))
    }

    @Test fun busyModelKeepsWaitingForTheReply() {
        val (mode, token) = listeningMode()
        mode.recognized(token, session, "Denk gründlich nach")
        assertEquals(Decision.LISTEN, mode.replyIdle())
        assertEquals(Decision.IGNORE, mode.resume(session, canListen = true, busy = true))
        assertEquals(State.THINKING, mode.state)
        mode.assistantSpeechQueued("final")
        assertEquals(Decision.LISTEN, mode.assistantSpeechFinished("final", interrupted = false))
    }

    @Test fun rejectedSendPausesAndKeepsTheDraftForTheOwner() {
        val (mode, token) = listeningMode()
        mode.recognized(token, session, "Noch eine Frage")
        assertEquals(Decision.PAUSE, mode.sendRejected())
        assertEquals(State.PAUSED, mode.state)
    }

    @Test fun newChatSessionEndsTheMode() {
        val (mode, token) = listeningMode()
        assertEquals(Decision.IGNORE, mode.recognized(token, "s-2", "fremde Sitzung"))
        mode.recognized(token, session, "Hallo")
        mode.assistantSpeechQueued("u1")
        mode.assistantSpeechFinished("u1", interrupted = false)
        assertEquals(Decision.STOP, mode.resume("s-2", canListen = true, busy = false))
        assertFalse(mode.active)
    }

    @Test fun startIsIdempotentAndStopInvalidatesTokens() {
        val (mode, token) = listeningMode()
        assertFalse(mode.start(session))
        mode.stop()
        assertEquals(Decision.IGNORE, mode.recognized(token, session, "zu spät"))
        assertTrue(mode.start(session))
    }
}
