package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class JarvisThermalVisualContractTest {
    private data class Rim(val width: Float, val alpha: Float)

    private fun dockContact(budget: JarvisRenderQuality.Budget, time: Long): List<Rim> {
        val rims = mutableListOf<Rim>()
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            when (method.name) {
                "strokeRoundRect" -> {
                    if (args!![6] is Fill.Radial) rims += Rim(args[5] as Float, args[7] as Float)
                    null
                }
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
        JarvisShellRenderer.draw(painter, JarvisShellLayout.create(884f, 760f),
            timeMs = 0L, materialTimeMs = time, depthBudget = budget,
            controlPress = JarvisShellRenderer.ControlPress("bottom:device", "device"))
        return rims
    }

    private fun shaped(thermal: Int, phase: JarvisComputePhasePolicy.Phase,
                       powerSave: Boolean = false, reducedMotion: Boolean = false): JarvisRenderQuality.Budget {
        val inference = phase in setOf(JarvisComputePhasePolicy.Phase.THINKING,
            JarvisComputePhasePolicy.Phase.RESEARCH, JarvisComputePhasePolicy.Phase.LEARNING)
        val base = JarvisRenderQuality.adaptiveAnimation(true, powerSave, thermal,
            reducedMotion, inference, cinemaOptIn = true, experimentEnabled = true)
        return JarvisComputePhasePolicy.apply(base, phase)
    }

    @Test fun actualShellContactRemainsStaticAcrossHardFloorsAndPhases() {
        for (phase in JarvisComputePhasePolicy.Phase.entries) {
            val floors = listOf(shaped(3, phase), shaped(6, phase),
                shaped(0, phase, powerSave = true), shaped(0, phase, reducedMotion = true))
            for (budget in floors) {
                assertFalse(budget.animate)
                val first = dockContact(budget, 300L)
                val later = dockContact(budget, 900L)
                assertEquals("Contact remains visible", 2, first.size)
                assertEquals("No time-driven pulse at a hard floor", first, later)
                assertTrue(first.all { it.width <= 6f && it.alpha in 0f..1f })
            }
        }
    }

    @Test fun warmWorkRetainsConstructionBeforeHardStopSettlesEveryWindow() {
        for (phase in listOf(JarvisComputePhasePolicy.Phase.THINKING,
            JarvisComputePhasePolicy.Phase.RESEARCH, JarvisComputePhasePolicy.Phase.LEARNING)) {
            val warm = shaped(1, phase)
            assertTrue(warm.animate)
            assertTrue(warm.frameDelayMillis >= 50L)
            assertTrue(warm.particleCount <= 12)
            assertTrue(warm.reducedBloom)
            assertNotEquals(dockContact(warm, 300L), dockContact(warm, 900L))
            val slots = listOf(JarvisShellLayout.Slot.WORK_DOCUMENT,
                JarvisShellLayout.Slot.WORK_PROCESS, JarvisShellLayout.Slot.WORK_DRAFT)
            slots.forEachIndexed { rank, slot ->
                val time = JarvisCinemaEntrance.DEPART_MS + rank * JarvisCinemaEntrance.STAGGER_MS + 300L
                val moving = JarvisCinemaEntrance.workOrderPose(slot, rank, time, 1f, warm)
                assertFalse("Warm work still moves", moving.settled)
                assertTrue(moving.travel > 0f && moving.travel < 1f)
                val hot = shaped(3, phase)
                val settled = JarvisCinemaEntrance.workOrderPose(slot, rank, time, 1f, hot)
                assertTrue("Thermal change cannot strand reconstruction", settled.settled)
                assertFalse(settled.hidden)
                assertEquals(1f, settled.contentAlpha, 0f)
                val returned = JarvisCinemaEntrance.returnPose(slot, rank, 0L, time, 1f, hot)
                assertTrue("Return remains reachable", returned.settled && !returned.hidden)
                assertEquals(1f, returned.contentAlpha, 0f)
            }
        }
    }

    @Test fun essentialFaceEligibilityDoesNotRestartDecorativeMotion() {
        val hot = shaped(3, JarvisComputePhasePolicy.Phase.SPEAKING)
        val eligible = AvatarEssentialMotionPolicy.frameLoopAllowed(true, true, true, true, hot)
        assertTrue("Existing visible face stays eligible", eligible)
        assertEquals(50L, AvatarEssentialMotionPolicy.frameDelayMillis(hot, speaking = true))
        assertFalse(AvatarEssentialMotionPolicy.decorativeAllowed(eligible, hot))
        assertEquals(dockContact(hot, 300L), dockContact(hot, 900L))
        for (flags in listOf(Triple(false, true, true), Triple(true, false, true), Triple(true, true, false))) {
            assertFalse("Visibility and accessibility remain authoritative",
                AvatarEssentialMotionPolicy.frameLoopAllowed(flags.first, flags.second, flags.third, true, hot))
        }
        assertFalse("An orb alone cannot request essential face frames",
            AvatarEssentialMotionPolicy.frameLoopAllowed(true, true, true, false, hot))
    }
}
