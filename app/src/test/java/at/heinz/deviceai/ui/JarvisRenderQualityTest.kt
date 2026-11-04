package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Battery/accessibility/foreground priorities must hold regardless of inference work. */
class JarvisRenderQualityTest {
    @Test fun hiddenScenesSuspendForEveryDeviceAndInferenceState() {
        for (powerSave in listOf(false, true)) for (reducedMotion in listOf(false, true)) {
            for (thermal in listOf(null, 0, 1, 2, 3, 4, 5, 6)) for (inference in listOf(false, true)) {
                val budget = JarvisRenderQuality.resolve(false, powerSave, thermal, reducedMotion, inference)
                assertEquals(JarvisRenderQuality.Tier.SUSPENDED, budget.tier)
                assertFalse(budget.animate)
                assertEquals("No hidden particle workload", 0, budget.particleCount)
            }
        }
    }

    @Test fun reducedMotionAndPowerSavingSuppressAnimationEvenDuringInference() {
        for (inference in listOf(false, true)) {
            val accessibility = JarvisRenderQuality.resolve(true, false, 0, true, inference)
            val battery = JarvisRenderQuality.resolve(true, true, 0, false, inference)
            for (budget in listOf(accessibility, battery)) {
                assertEquals(JarvisRenderQuality.Tier.LOW, budget.tier)
                assertFalse(budget.animate)
                assertTrue(budget.reducedBloom)
            }
        }
    }

    @Test fun thermalPressureNeverIncreasesRenderWork() {
        val budgets = (0..6).map { thermal -> JarvisRenderQuality.resolve(true, false, thermal, false, false) }
        budgets.zipWithNext().forEach { (cooler, hotter) ->
            assertTrue(hotter.particleCount <= cooler.particleCount)
            assertTrue(hotter.orbitSegments <= cooler.orbitSegments)
            assertTrue(hotter.frameDelayMillis >= cooler.frameDelayMillis)
        }
        budgets.drop(3).forEach {
            assertFalse("Severe or higher thermal state must suppress presentation animation", it.animate)
            assertEquals(JarvisRenderQuality.Tier.LOW, it.tier)
        }
    }

    @Test fun activeInferenceReservesCapacityWhileNormalForegroundKeepsFullQuality() {
        val normal = JarvisRenderQuality.resolve(true, false, null, false, false)
        val inference = JarvisRenderQuality.resolve(true, false, null, false, true)
        assertEquals(JarvisRenderQuality.Tier.HIGH, normal.tier)
        assertEquals(JarvisRenderQuality.Tier.MEDIUM, inference.tier)
        assertTrue(inference.particleCount < normal.particleCount)
        assertTrue(inference.orbitSegments < normal.orbitSegments)
        assertTrue(inference.frameDelayMillis > normal.frameDelayMillis)
        assertTrue(inference.reducedBloom)
        assertTrue("Inference need not disable every decorative frame", inference.animate)
    }
}
