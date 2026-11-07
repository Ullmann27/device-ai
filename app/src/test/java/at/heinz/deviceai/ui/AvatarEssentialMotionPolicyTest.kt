package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarEssentialMotionPolicyTest {
    @Test fun powerOrThermalLowBudgetKeepsEssentialMotionButStopsDecoration() {
        val low = JarvisRenderQuality.Budget(
            JarvisRenderQuality.Tier.LOW, 80L, 12, 40, false
        )
        val essential = AvatarEssentialMotionPolicy.essentialAllowed(
            foreground = true, animationsEnabled = true, systemAnimatorsEnabled = true
        )
        assertTrue(essential)
        assertFalse(AvatarEssentialMotionPolicy.decorativeAllowed(essential, low))
        assertEquals(50L, AvatarEssentialMotionPolicy.frameDelayMillis(low, speaking = true))
        assertEquals(80L, AvatarEssentialMotionPolicy.frameDelayMillis(low, speaking = false))
    }

    @Test fun accessibilityOrExplicitAnimationOffStillStopsMotion() {
        assertFalse(AvatarEssentialMotionPolicy.essentialAllowed(true, false, true))
        assertFalse(AvatarEssentialMotionPolicy.essentialAllowed(true, true, false))
        assertFalse(AvatarEssentialMotionPolicy.essentialAllowed(false, true, true))
    }

    @Test fun normalHighBudgetKeepsOriginalFrameDelayAndDecoration() {
        val high = JarvisRenderQuality.Budget(
            JarvisRenderQuality.Tier.HIGH, 16L, 70, 96, true
        )
        val essential = AvatarEssentialMotionPolicy.essentialAllowed(true, true, true)
        assertTrue(AvatarEssentialMotionPolicy.decorativeAllowed(essential, high))
        assertEquals(16L, AvatarEssentialMotionPolicy.frameDelayMillis(high, speaking = true))
    }
}
