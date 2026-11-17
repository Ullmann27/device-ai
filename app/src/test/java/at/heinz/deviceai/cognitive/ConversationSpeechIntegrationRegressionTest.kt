package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** Independent pure hand-off checks; no claim of Android ASR or audible playback. */
class ConversationSpeechIntegrationRegressionTest {
    @Test fun twentyRepliesResumeOnlyAfterTheLastApprovedChunk() {
        val mode = ContinuousConversationMode()
        val queue = ApprovedSpeechChunkQueue()
        assertTrue(mode.start("owner"))
        repeat(20) { index ->
            val token = checkNotNull(mode.listeningTurn("owner"))
            assertEquals(ContinuousConversationMode.Decision.SEND, mode.recognized(token, "owner", "Frage $index"))
            val reply = "reply-$index"
            assertTrue(queue.beginTurn(reply))
            assertTrue(queue.appendApproved(reply, "$reply-a", "Erster Satz.", "quality-$index"))
            assertTrue(queue.appendApproved(reply, "$reply-b", "Zweiter Satz.", "quality-$index"))
            assertTrue(queue.markLastChunkSubmitted(reply))
            mode.assistantSpeechQueued(reply)
            assertNull(mode.listeningTurn("owner"))
            assertEquals("$reply-a", queue.takeNextReady(reply)!!.chunkId)
            assertEquals(ApprovedSpeechChunkQueue.Completion.MORE_CHUNKS, queue.onChunkFinished(reply, "$reply-a"))
            assertNull(mode.listeningTurn("owner"))
            assertEquals("$reply-b", queue.takeNextReady(reply)!!.chunkId)
            assertEquals(ApprovedSpeechChunkQueue.Completion.LAST_CHUNK_FINISHED, queue.onChunkFinished(reply, "$reply-b"))
            assertEquals(ContinuousConversationMode.Decision.LISTEN, mode.assistantSpeechFinished(reply, false))
            assertEquals(ContinuousConversationMode.Decision.LISTEN, mode.resume("owner", true, false))
            assertEquals(ContinuousConversationMode.Decision.IGNORE, mode.recognized(token, "owner", "Verspäteter Text"))
        }
        assertFalse(queue.hasActiveTurn())
        assertEquals(ContinuousConversationMode.State.LISTENING, mode.state)
    }

    @Test fun stopAndBargeInInvalidateOldTtsAndRecognizerCallbacks() {
        val mode = ContinuousConversationMode()
        val queue = ApprovedSpeechChunkQueue()
        assertTrue(mode.start("owner"))
        val oldToken = checkNotNull(mode.listeningTurn("owner"))
        mode.recognized(oldToken, "owner", "Frage")
        queue.beginTurn("old")
        queue.appendApproved("old", "old-a", "Antwort.", "quality")
        queue.takeNextReady("old")
        mode.assistantSpeechQueued("old")
        assertEquals(ContinuousConversationMode.Decision.LISTEN, mode.ownerTap("owner"))
        assertTrue(queue.stop("old"))
        assertEquals(ApprovedSpeechChunkQueue.Completion.IGNORED, queue.onChunkFinished("old", "old-a"))
        assertEquals(ContinuousConversationMode.Decision.IGNORE, mode.assistantSpeechFinished("old", false))
        assertEquals(ContinuousConversationMode.Decision.IGNORE, mode.recognized(oldToken, "owner", "Alt"))
        val current = checkNotNull(mode.listeningTurn("owner"))
        assertEquals(ContinuousConversationMode.Decision.STOP, mode.recognized(current, "owner", "Stopp"))
        assertEquals(ContinuousConversationMode.Decision.IGNORE, mode.recognized(current, "owner", "Alt"))
        assertFalse(mode.active)
    }

    @Test fun focusApprovalAndSessionChangesNeverRestartAnUnattendedMicrophone() {
        val mode = ContinuousConversationMode()
        assertTrue(mode.start("owner"))
        mode.recognized(checkNotNull(mode.listeningTurn("owner")), "owner", "Frage")
        mode.replyIdle()
        assertEquals(ContinuousConversationMode.Decision.PAUSE, mode.resume("owner", false, false))
        assertNull(mode.listeningTurn("owner"))
        assertEquals(ContinuousConversationMode.Decision.LISTEN, mode.ownerTap("owner"))
        mode.recognized(checkNotNull(mode.listeningTurn("owner")), "owner", "Weitere Frage")
        mode.replyIdle()
        assertEquals(ContinuousConversationMode.Decision.STOP, mode.resume("different-session", true, false))
        assertFalse(mode.active)
    }
}
