package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Battery/accessibility/foreground priorities must hold regardless of inference work. */
class JarvisRenderQualityTest {
    @Test fun cinemaAdaptationRetainsCoolInferenceLighting() {
        val budget = JarvisRenderQuality.adaptiveAnimation(true, false, 0, false, true, true, true)
        assertFalse("EXP15 must retain cool lighting", budget.reducedBloom)
        assertTrue(budget.frameDelayMillis >= 50L)
        assertTrue(budget.particleCount <= 12)
        assertTrue(budget.orbitSegments <= 40)
    }

    @Test fun cinemaLightingCannotOverrideSafetySignals() {
        for (thermal in listOf(null, 0, 1, 2, 3, 4, 5, 6)) {
            for (powerSave in listOf(false, true)) for (reducedMotion in listOf(false, true)) {
                for (foreground in listOf(false, true)) {
                    val base = JarvisRenderQuality.resolve(foreground, powerSave, thermal, reducedMotion, true)
                    val adapted = JarvisRenderQuality.adaptiveAnimation(
                        foreground, powerSave, thermal, reducedMotion, true, true, true)
                    assertEquals(base.reducedBloom, adapted.reducedBloom)
                    assertEquals(base.animate, adapted.animate)
                    assertTrue(adapted.frameDelayMillis >= base.frameDelayMillis)
                    assertTrue(adapted.particleCount <= base.particleCount)
                }
            }
        }
    }
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

    @Test fun moderateHeatReducesFrameWorkBeforeSevereWithoutDroppingAnimation() {
        for (inference in listOf(false, true)) {
            val light = JarvisRenderQuality.resolve(true, false, 1, false, inference)
            val moderate = JarvisRenderQuality.resolve(true, false, 2, false, inference)
            val severe = JarvisRenderQuality.resolve(true, false, 3, false, inference)
            assertEquals(JarvisRenderQuality.Tier.MEDIUM, moderate.tier)
            assertTrue(moderate.animate)
            assertTrue(moderate.frameDelayMillis > light.frameDelayMillis)
            assertTrue(moderate.particleCount < light.particleCount)
            assertTrue(moderate.orbitSegments < light.orbitSegments)
            assertEquals(JarvisRenderQuality.Tier.LOW, severe.tier)
            assertFalse(severe.animate)
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

    @Test fun explicitlyCoolInferencePreservesLightingWithoutDefeatingWarmThrottling() {
        val cool = JarvisRenderQuality.resolve(true, false, 0, false, true)
        val unknown = JarvisRenderQuality.resolve(true, false, null, false, true)
        val warm = JarvisRenderQuality.resolve(true, false, 1, false, true)
        val moderate = JarvisRenderQuality.resolve(true, false, 2, false, true)
        assertEquals(JarvisRenderQuality.Tier.MEDIUM, cool.tier)
        assertEquals(33L, cool.frameDelayMillis)
        assertFalse(cool.reducedBloom)
        assertEquals(32, cool.particleCount)
        assertTrue(unknown.reducedBloom)
        assertTrue(warm.reducedBloom)
        assertTrue(moderate.reducedBloom)
        assertEquals(80L, moderate.frameDelayMillis)
        assertTrue(JarvisRenderQuality.resolve(true, true, 0, false, true).reducedBloom)
        assertTrue(JarvisRenderQuality.resolve(true, false, 3, false, true).reducedBloom)
    }

    @Test fun inferenceOnAWarmDeviceGetsTheSmallestAnimatedBudget() {
        val idleWarm = JarvisRenderQuality.resolve(true, false, 1, false, false)
        val inferringWarm = JarvisRenderQuality.resolve(true, false, 1, false, true)
        val inferringModerate = JarvisRenderQuality.resolve(true, false, 2, false, true)
        assertTrue(inferringWarm.frameDelayMillis > idleWarm.frameDelayMillis)
        assertTrue(inferringWarm.particleCount < idleWarm.particleCount)
        assertTrue(inferringModerate.frameDelayMillis >= 80L)
        assertTrue(inferringModerate.particleCount <= 12)
        assertTrue("the head must keep moving; only the budget shrinks", inferringModerate.animate)
        assertEquals(JarvisRenderQuality.Tier.MEDIUM, inferringModerate.tier)
    }
}
