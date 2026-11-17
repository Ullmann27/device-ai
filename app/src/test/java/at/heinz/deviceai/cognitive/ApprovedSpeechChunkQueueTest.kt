package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedSpeechChunkQueueTest {
    @Test fun rejectedUnapprovedDraftCannotReachSpeech() {
        val q = ApprovedSpeechChunkQueue()
        assertTrue(q.beginTurn("turn-1"))
        assertFalse(q.appendApproved("turn-1", "chunk-1", "draft", ""))
        assertNull(q.takeNextReady("turn-1"))
        assertTrue(q.stop("turn-1"))
    }

    @Test fun onlyTheLastBoundChunkCanCompleteTheTurn() {
        val q = ApprovedSpeechChunkQueue()
        assertTrue(q.beginTurn("turn-1"))
        assertTrue(q.appendApproved("turn-1", "c1", "Ein Satz.", "quality-1"))
        assertTrue(q.appendApproved("turn-1", "c2", "Noch ein Satz.", "quality-2"))
        assertTrue(q.markLastChunkSubmitted("turn-1"))
        assertEquals("c1", q.takeNextReady("turn-1")?.chunkId)
        assertNull(q.takeNextReady("turn-1"))
        assertEquals(ApprovedSpeechChunkQueue.Completion.IGNORED, q.onChunkFinished("turn-1", "c2"))
        assertEquals(ApprovedSpeechChunkQueue.Completion.MORE_CHUNKS, q.onChunkFinished("turn-1", "c1"))
        assertEquals("c2", q.takeNextReady("turn-1")?.chunkId)
        assertEquals(ApprovedSpeechChunkQueue.Completion.LAST_CHUNK_FINISHED, q.onChunkFinished("turn-1", "c2"))
        assertFalse(q.hasActiveTurn())
    }

    @Test fun lateCallbacksAndStopCannotResurrectAudio() {
        val q = ApprovedSpeechChunkQueue()
        assertTrue(q.beginTurn("first"))
        assertTrue(q.appendApproved("first", "c1", "Hallo.", "quality-1"))
        assertEquals("c1", q.takeNextReady("first")?.chunkId)
        assertTrue(q.stop("first"))
        assertTrue(q.beginTurn("second"))
        assertEquals(ApprovedSpeechChunkQueue.Completion.IGNORED, q.onChunkFinished("first", "c1"))
        assertFalse(q.appendApproved("first", "c2", "Alt.", "quality-2"))
        assertNull(q.takeNextReady("second"))
        assertTrue(q.hasActiveTurn())
    }

    @Test fun atMostConfiguredChunksAndDuplicateIdsAreRejected() {
        val q = ApprovedSpeechChunkQueue(maxChunks = 1)
        assertTrue(q.beginTurn("one"))
        assertTrue(q.appendApproved("one", "a", "Text.", "approval"))
        assertFalse(q.appendApproved("one", "b", "Mehr.", "approval2"))
        assertFalse(q.appendApproved("one", "a", "Doppelt.", "approval3"))
    }
}
