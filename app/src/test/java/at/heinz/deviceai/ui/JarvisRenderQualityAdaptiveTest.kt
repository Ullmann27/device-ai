package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisRenderQualityAdaptiveTest {
    @Test fun cinemaOnOffChangesModerateThermalBudget() {
        val on = JarvisRenderQuality.adaptiveAnimation(true, false, 2, false, false,
            cinemaOptIn = true, experimentEnabled = true)
        val off = JarvisRenderQuality.adaptiveAnimation(true, false, 2, false, false,
            cinemaOptIn = true, experimentEnabled = false)
        assertEquals(JarvisRenderQuality.Tier.MEDIUM, on.tier)
        assertTrue(on.animate)
        assertEquals(JarvisRenderQuality.Tier.HIGH, off.tier)
        assertTrue(off.animate)
        assertTrue(off.frameDelayMillis < on.frameDelayMillis)
        assertTrue(off.particleCount > on.particleCount)
        assertNotEquals(on, off)
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
