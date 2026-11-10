package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class JarvisResultVisibilityTest {
    private val high = JarvisRenderQuality.adaptiveAnimation(
        foreground = true, powerSave = false, thermalStatus = 0,
        reducedMotion = false, inferenceActive = false,
        cinemaOptIn = true, experimentEnabled = true)

    @Test fun resultKeepsConstructedTextFullyVisible() {
        for (time in listOf(0L, 100L, 760L, 900L, 10_000L)) {
            val result = JarvisCinemaEntrance.resultAdvance(time, high)
            assertEquals("Result data at $time", 1f, result.contentAlpha, .0001f)
            assertEquals("Result glass at $time", 1f, result.glassAlpha, .0001f)
            assertTrue("Construction finished at $time", result.frame.settled)
        }
    }

    @Test fun returnStartsFromFullyConstructedWorkColumns() {
        val slots = listOf(JarvisShellLayout.Slot.WORK_DOCUMENT,
            JarvisShellLayout.Slot.WORK_PROCESS, JarvisShellLayout.Slot.WORK_DRAFT)
        slots.forEachIndexed { rank, slot ->
            val running = JarvisCinemaEntrance.workOrderPose(slot, rank, 3_000L, budget = high)
            val returning = JarvisCinemaEntrance.returnPose(slot, rank, 0L,
                interruptedAfterMs = 3_000L, budget = high, returnResultElapsedMs = 760L)
            assertEquals("No result dimming for $slot", running.contentAlpha, returning.contentAlpha, .0001f)
            assertEquals("No glass dimming for $slot", running.glassAlpha, returning.glassAlpha, .0001f)
        }
    }

    @Test fun earlyResultReturnPreservesItsExactSnapshot() {
        for (time in listOf(0L, 1L, 70L, 200L, 759L)) {
            val before = JarvisCinemaEntrance.resultAdvance(time, high)
            val after = JarvisCinemaEntrance.returnPose(JarvisShellLayout.Slot.WORK_DRAFT,
                2, 0L, interruptedAfterMs = 3_000L, budget = high,
                returnResultElapsedMs = time)
            assertEquals("Continuous return at $time", before, after)
        }
    }

    @Test fun settledSampleConsumesEveryRankDelay() {
        for (rank in listOf(-1, 0, 1, 2, 20)) {
            val result = JarvisCinemaEntrance.settledPose(JarvisCinemaEntrance.Kind.WORK_WINDOW,
                rank, side = 1f, budget = high)
            assertEquals("Settled data rank $rank", 1f, result.contentAlpha, .0001f)
            assertTrue("Settled rank $rank", result.settled)
            assertEquals("No entrance tilt rank $rank", 0f, result.tiltY, .0001f)
        }
    }
}
