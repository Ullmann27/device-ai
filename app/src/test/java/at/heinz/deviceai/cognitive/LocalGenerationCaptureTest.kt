package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalGenerationCaptureTest {
    @Test fun completeResponsesKeepTheirEndingAcrossAgentBoundary() {
        for (size in listOf(8_000, 8_001)) {
            val ending = " NICHT AUSFÜHREN. Ö."
            val answer = "x".repeat(size - ending.length) + ending
            val capture = LocalGenerationCapture()
            capture.append(answer, 10)
            assertEquals(answer, capture.complete().response)
            assertTrue(capture.snapshot().responseComplete)
        }
    }

    @Test fun exactCapCompletesBeforeWatchdogPoll() {
        val answer = "x".repeat(64_000)
        val capture = LocalGenerationCapture()
        capture.append(answer, 10)
        capture.complete()
        val result = capture.poll(250)
        assertEquals(LocalGenerationCompletion.COMPLETE, result.completion)
        assertEquals(answer, result.response)
    }

    @Test fun exactCapCompletesAfterWatchdogPoll() {
        val answer = "x".repeat(64_000)
        val capture = LocalGenerationCapture()
        capture.append(answer, 10)
        assertFalse(capture.poll(250).terminal)
        val result = capture.complete()
        assertEquals(LocalGenerationCompletion.COMPLETE, result.completion)
        assertEquals(answer, result.response)
    }

    @Test fun oversizedChunkCannotBecomeComplete() {
        val capture = LocalGenerationCapture()
        val overflow = capture.append("x".repeat(64_001), 10)
        assertEquals(LocalGenerationCompletion.OUTPUT_LIMIT, overflow.completion)
        assertEquals("x".repeat(64_000), overflow.response)
        assertFalse(overflow.responseComplete)
        assertEquals(overflow, capture.complete())
    }

    @Test fun overflowAfterExactCapPreservesTheBoundedDraft() {
        val capture = LocalGenerationCapture()
        capture.append("x".repeat(64_000), 10)
        val result = capture.append("y", 11)
        assertEquals(LocalGenerationCompletion.OUTPUT_LIMIT, result.completion)
        assertEquals("x".repeat(64_000), result.response)
        assertEquals(result, capture.poll(250))
    }

    @Test fun splitOverflowDoesNotSplitASupplementaryCharacter() {
        val prefix = "x".repeat(63_999)
        val capture = LocalGenerationCapture()
        capture.append(prefix, 10)
        val result = capture.append("🙂z", 11)
        assertEquals(LocalGenerationCompletion.OUTPUT_LIMIT, result.completion)
        assertEquals(prefix, result.response)
        assertEquals(prefix, result.response.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }

    @Test fun partialTextSurvivesARealStreamStall() {
        val capture = LocalGenerationCapture()
        capture.append("Bereits erzeugter Anfang. Noch unvollständig", 10)
        assertFalse(capture.poll(120_009).terminal)
        val result = capture.poll(120_010)
        assertEquals(LocalGenerationCompletion.STALLED, result.completion)
        assertEquals("Bereits erzeugter Anfang. Noch unvollständig", result.response)
        assertFalse(result.responseComplete)
        assertEquals(result, capture.complete())
    }

    @Test fun startupStallHasNoInventedText() {
        val capture = LocalGenerationCapture()
        assertFalse(capture.poll(179_999).terminal)
        val result = capture.poll(180_000)
        assertEquals(LocalGenerationCompletion.STALLED, result.completion)
        assertEquals("", result.response)
        assertFalse(result.responseComplete)
    }

    @Test fun ongoingProgressHasNoTotalTimeDeadline() {
        val capture = LocalGenerationCapture()
        capture.append("Erster Abschnitt. ", 170_000)
        assertFalse(capture.poll(289_999).terminal)
        capture.append("Zweiter Abschnitt.", 290_000)
        assertFalse(capture.poll(409_999).terminal)
        assertTrue(capture.complete().responseComplete)
    }

    @Test fun failureKeepsTextAndCannotTurnIntoSuccess() {
        val capture = LocalGenerationCapture()
        capture.append("Ein erhaltener Teil.", 10)
        val failed = capture.fail("native failure")
        assertEquals(LocalGenerationCompletion.FAILED, failed.completion)
        assertEquals("Ein erhaltener Teil.", failed.response)
        assertEquals("native failure", failed.failure)
        assertEquals(failed, capture.complete())
        assertEquals(failed, capture.append(" Nachträglich", 11))
    }

    @Test fun stopRejectsLateTextAndCompletion() {
        val capture = LocalGenerationCapture()
        capture.append("Vor STOP", 10)
        val cancelled = capture.cancel("Owner STOP")
        assertEquals(LocalGenerationCompletion.CANCELLED, cancelled.completion)
        assertEquals("Owner STOP", cancelled.failure)
        assertFalse(cancelled.responseComplete)
        assertEquals(cancelled, capture.append(" darf nicht erscheinen", 11))
        assertEquals(cancelled, capture.complete())
        assertEquals(cancelled, capture.fail("late failure"))
    }

    @Test fun completedSnapshotIsImmutableAfterLateCallbacks() {
        val capture = LocalGenerationCapture()
        capture.append("Fertig.", 10)
        val completed = capture.complete()
        assertEquals(completed, capture.append(" Fremder Text", 11))
        assertEquals(completed, capture.fail("late failure"))
        assertEquals(completed, capture.cancel())
        assertEquals("Fertig.", completed.response)
    }

    @Test fun blankDeltasDoNotRefreshTheProgressClock() {
        val capture = LocalGenerationCapture()
        capture.append("Anfang", 10)
        capture.append("", 120_000)
        assertEquals(LocalGenerationCompletion.STALLED, capture.poll(120_010).completion)
    }
}
