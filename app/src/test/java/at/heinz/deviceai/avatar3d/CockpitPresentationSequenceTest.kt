package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.cognitive.*
import org.junit.Assert.*
import org.junit.Test

class CockpitPresentationSequenceTest {
    private val first = LiveFrameBinding("task-one", 1L, "com.example.target", "at.heinz.deviceai",
        LiveFrameOrigin.PROJECTION_WINDOW_CROP)
    private fun event(binding: LiveFrameBinding, sequence: Long, time: Long = sequence) =
        CockpitWorkEvent(binding.taskId, binding.captureSession, sequence, time, 0L, CockpitWorkEvent.Stage.FOCUS)

    @Test fun reusedFeedCanStartANewCaptureWithoutWaitingForOldSequence() {
        val presentation = CockpitPresentationSequence()
        assertTrue(presentation.accept(event(first, 50L), first, 100L))
        val second = first.copy(captureSession = 2L)
        assertTrue(presentation.accept(event(second, 1L, 101L), second, 200L))
        assertFalse(presentation.accept(event(first, 51L, 102L), second, 200L))
        assertFalse(presentation.accept(event(second, 1L, 103L), second, 200L))
        assertTrue(presentation.accept(event(second, 2L, 104L), second, 200L))
    }

    @Test fun lateAndFutureEventsDoNotResetOrAdvanceActiveSequence() {
        val presentation = CockpitPresentationSequence()
        assertTrue(presentation.accept(event(first, 5L, 20L), first, 30L))
        assertFalse(presentation.accept(event(first, 6L, 19L), first, 30L))
        assertFalse(presentation.accept(event(first, 7L, 40L), first, 30L))
        assertTrue(presentation.accept(event(first, 6L, 25L), first, 30L))
    }
}
