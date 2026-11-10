package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test

class JarvisFluidLightTest {
    private val high = JarvisRenderQuality.resolve(true, false, 0, false, false)

    @Test fun lightBridgesDepartureAndReconstruction() {
        assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(0))
        assertTrue(JarvisFluidLight.sample(280).energy > .3f)
        assertTrue(JarvisFluidLight.sample(420).energy > .6f)
        assertTrue(JarvisFluidLight.sample(650).rebuilding)
        assertTrue(JarvisFluidLight.sample(900).energy > .6f)
        assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(1600))
    }

    @Test fun suspendedAndThermalBudgetsCannotAnimate() {
        for (budget in listOf(
            JarvisRenderQuality.resolve(false, false, 0, false, false),
            JarvisRenderQuality.resolve(true, false, 3, false, false),
            JarvisRenderQuality.resolve(true, true, 0, false, false))) {
            assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(650, budget = budget))
            assertEquals(0, JarvisFluidLight.strandCount(budget))
        }
        assertEquals(4, JarvisFluidLight.strandCount(high))
        assertEquals(2, JarvisFluidLight.strandCount(JarvisRenderQuality.resolve(true, false, 1, false, true)))
    }

    @Test fun replayIsBoundedAndUsesTheExistingStagger() {
        for (time in -200L..2000L step 13) {
            val a = JarvisFluidLight.sample(time, 2, high)
            assertEquals(a, JarvisFluidLight.sample(time, 2, high))
            assertTrue(a.energy in 0f..1f && a.phase in 0f..1f)
            assertEquals(a, JarvisFluidLight.sample(time - 140, 0, high))
        }
        assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(Long.MAX_VALUE))
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(650, animatorScale = scale))
        }
    }

    @Test fun cancellingAnyConstructionFramePreservesTheVisiblePose() {
        val slots = listOf(JarvisShellLayout.Slot.WORK_DOCUMENT, JarvisShellLayout.Slot.WORK_PROCESS,
            JarvisShellLayout.Slot.WORK_DRAFT, JarvisShellLayout.Slot.BOTTOM_NAV, JarvisShellLayout.Slot.TILES)
        for (slot in slots) for (rank in 0..4) for (time in 0L..1800L step 37) {
            val working = JarvisCinemaEntrance.workOrderPose(slot, rank, time, 1f, high)
            val cancelled = JarvisCinemaEntrance.returnPose(slot, rank, 0, time, 1f, high)
            assertEquals("$slot $rank $time", working, cancelled)
            val settled = JarvisCinemaEntrance.returnPose(slot, rank, 3000, time, 1f, high)
            assertTrue(settled.settled && !settled.hidden)
        }
    }
}
