package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisRenderQualityAdaptiveTest {
    @Test fun cinemaOnOffNeverBypassesModerateThermalFloor() {
        val base = JarvisRenderQuality.resolve(true, false, 2, false, false)
        val on = JarvisRenderQuality.adaptiveAnimation(true, false, 2, false, false,
            cinemaOptIn = true, experimentEnabled = true)
        val off = JarvisRenderQuality.adaptiveAnimation(true, false, 2, false, false,
            cinemaOptIn = true, experimentEnabled = false)
        assertEquals(base, off)
        assertEquals(JarvisRenderQuality.Tier.MEDIUM, on.tier)
        assertTrue(on.animate)
        assertEquals(66L, on.frameDelayMillis)
        assertEquals(12, on.particleCount)
        assertEquals(40, on.orbitSegments)
        assertTrue(on.frameDelayMillis >= off.frameDelayMillis)
        assertTrue(on.particleCount < off.particleCount)
        assertTrue(on.orbitSegments < off.orbitSegments)
        assertNotEquals(on, off)
    }

    @Test fun exp15NeverIncreasesRenderWorkAcrossHeatAndInference() {
        for (thermal in listOf(null, 0, 1, 2, 3, 4, 5, 6))
            for (inference in listOf(false, true))
                for (enabled in listOf(false, true)) {
                    val base = JarvisRenderQuality.resolve(true, false, thermal, false, inference)
                    val actual = JarvisRenderQuality.adaptiveAnimation(
                        true, false, thermal, false, inference,
                        cinemaOptIn = true, experimentEnabled = enabled
                    )
                    assertTrue("heat=$thermal inference=$inference enabled=$enabled",
                        actual.frameDelayMillis >= base.frameDelayMillis)
                    assertTrue(actual.particleCount <= base.particleCount)
                    assertTrue(actual.orbitSegments <= base.orbitSegments)
                    if (!base.animate) assertFalse(actual.animate)
                    if (!enabled) assertEquals(base, actual)
                }
    }

    @Test fun exp15ReducesDecorationsDuringInferenceAndPreservesHardStops() {
        val inference = JarvisRenderQuality.adaptiveAnimation(
            true, false, 0, false, true, cinemaOptIn = true, experimentEnabled = true)
        assertEquals(JarvisRenderQuality.Tier.MEDIUM, inference.tier)
        assertTrue(inference.animate)
        assertFalse(inference.reducedBloom)
        assertTrue(inference.coolInferenceLighting)
        assertTrue(inference.frameDelayMillis >= 50L)
        assertTrue(inference.particleCount <= 12)
        val warm = JarvisRenderQuality.adaptiveAnimation(
            true, false, 1, false, true, cinemaOptIn = true, experimentEnabled = true)
        assertTrue(warm.reducedBloom)
        assertFalse(warm.coolInferenceLighting)
        val severe = JarvisRenderQuality.adaptiveAnimation(
            true, false, 3, false, true, cinemaOptIn = true, experimentEnabled = false)
        assertEquals(JarvisRenderQuality.Tier.LOW, severe.tier)
        assertFalse(severe.animate)
    }

    @Test fun productionWithoutCinemaKeepsThermalBudget() {
        val production = JarvisRenderQuality.adaptiveAnimation(true, false, 2, false, false,
            cinemaOptIn = false, experimentEnabled = false)
        val resolved = JarvisRenderQuality.resolve(true, false, 2, false, false)
        assertEquals(resolved, production)
        assertEquals(JarvisRenderQuality.Tier.MEDIUM, production.tier)
    }

    @Test fun hardSafetyFloorsRemainWhenTheExperimentIsOff() {
        val hidden = JarvisRenderQuality.adaptiveAnimation(false, false, 0, false, false,
            cinemaOptIn = true, experimentEnabled = false)
        assertEquals(JarvisRenderQuality.Tier.SUSPENDED, hidden.tier)
        assertFalse(hidden.animate)
        val reduced = JarvisRenderQuality.adaptiveAnimation(true, false, 0, true, false,
            cinemaOptIn = true, experimentEnabled = false)
        assertEquals(JarvisRenderQuality.Tier.LOW, reduced.tier)
        assertFalse(reduced.animate)
        val power = JarvisRenderQuality.adaptiveAnimation(true, true, 0, false, false,
            cinemaOptIn = true, experimentEnabled = false)
        assertEquals(JarvisRenderQuality.Tier.LOW, power.tier)
        assertFalse(power.animate)
        val severe = JarvisRenderQuality.adaptiveAnimation(true, false, 3, false, false,
            cinemaOptIn = true, experimentEnabled = false)
        assertEquals(JarvisRenderQuality.Tier.LOW, severe.tier)
        assertFalse(severe.animate)
    }
}
