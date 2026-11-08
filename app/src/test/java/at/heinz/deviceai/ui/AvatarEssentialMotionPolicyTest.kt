package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarEssentialMotionPolicyTest {
    @Test fun powerOrThermalLowBudgetKeepsAvatarEssentialMotionButStopsDecoration() {
        val low = JarvisRenderQuality.Budget(
            JarvisRenderQuality.Tier.LOW, 80L, 12, 40, false
        )
        val essential = AvatarEssentialMotionPolicy.frameLoopAllowed(
            foreground = true, animationsEnabled = true, systemAnimatorsEnabled = true,
            avatarEnabled = true, budget = low
        )
        assertTrue(essential)
        assertFalse(AvatarEssentialMotionPolicy.decorativeAllowed(essential, low))
        assertEquals(50L, AvatarEssentialMotionPolicy.frameDelayMillis(low, speaking = true))
        assertEquals(80L, AvatarEssentialMotionPolicy.frameDelayMillis(low, speaking = false))
    }

    @Test fun lowBudgetOrbOnlyPresentationStopsTheFrameLoop() {
        val low = JarvisRenderQuality.Budget(
            JarvisRenderQuality.Tier.LOW, 80L, 12, 40, false
        )
        assertFalse(AvatarEssentialMotionPolicy.frameLoopAllowed(
            foreground = true, animationsEnabled = true, systemAnimatorsEnabled = true,
            avatarEnabled = false, budget = low
        ))
    }

    @Test fun accessibilityExplicitAnimationOffOrBackgroundStillStopsMotion() {
        val high = JarvisRenderQuality.Budget(
            JarvisRenderQuality.Tier.HIGH, 16L, 70, 96, true
        )
        assertFalse(AvatarEssentialMotionPolicy.frameLoopAllowed(true, false, true, true, high))
        assertFalse(AvatarEssentialMotionPolicy.frameLoopAllowed(true, true, false, true, high))
        assertFalse(AvatarEssentialMotionPolicy.frameLoopAllowed(false, true, true, true, high))
    }

    @Test fun normalHighBudgetKeepsOriginalFrameDelayAndDecorationEvenWithoutAvatar() {
        val high = JarvisRenderQuality.Budget(
            JarvisRenderQuality.Tier.HIGH, 16L, 70, 96, true
        )
        val loop = AvatarEssentialMotionPolicy.frameLoopAllowed(true, true, true, false, high)
        assertTrue(loop)
        assertTrue(AvatarEssentialMotionPolicy.decorativeAllowed(loop, high))
        assertEquals(16L, AvatarEssentialMotionPolicy.frameDelayMillis(high, speaking = true))
    }
}
