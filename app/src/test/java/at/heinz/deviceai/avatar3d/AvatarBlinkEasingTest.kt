package at.heinz.deviceai.avatar3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Exact 460-s app-clock animation; no independent timer or synthetic expression state. */
class AvatarBlinkEasingTest {
    private fun sample(seconds: Float): AvatarIdleMotion = AvatarIdleMotion().also { it.update(seconds) }
    private fun lid(seconds: Float): Float = sample(seconds).blinkWeight

    private fun velocityJump(seconds: Float): Float {
        val delta = .001f
        val left = (lid(seconds) - lid(seconds - delta)) / delta
        val right = (lid(seconds + delta) - lid(seconds)) / delta
        return abs(left - right)
    }

    @Test fun closureStartsAndReopensWithoutAngularVelocityCusp() {
        for (join in listOf(4.45f, 4.505f, 4.60f, 13.35f, 13.405f, 13.50f)) {
            assertTrue("sharp eyelid velocity jump at $join: ${velocityJump(join)}",
                velocityJump(join) < 1.0f)
        }
    }

    @Test fun primaryAndSecondaryBlinkKeepReviewedDurationAndApex() {
        assertEquals(0f, lid(4.449f), .001f)
        assertTrue(lid(4.505f) > .99f)
        assertEquals(0f, lid(4.601f), .001f)
        assertEquals(0f, lid(13.349f), .001f)
        assertTrue(lid(13.405f) > .99f)
        assertEquals(0f, lid(13.501f), .001f)
        assertTrue("no secondary blink outside every fifth group", lid(8.825f) < .001f)
    }

    @Test fun originalMapperBlinkContractRemainsFullyClosedAtFourPointFiveTwoFive() {
        // Existing Avatar3dContractTest expects an exact 1f at 4.525 s.
        assertEquals(1f, lid(4.505f), 0f)
        assertEquals(1f, lid(4.525f), 0f)
        assertEquals(1f, lid(13.405f), 0f)
        assertEquals(1f, lid(13.425f), 0f)
        for (join in listOf(4.525f, 13.425f)) {
            assertTrue("sharp plateau exit at $join", velocityJump(join) < 1f)
        }
    }

    @Test fun invalidClockAndNegativeWrapStayNeutralAndDeterministic() {
        for (seconds in listOf(0f, -0f, -460f, -920f, 460f)) {
            val s = sample(seconds)
            assertEquals(0f, s.headYaw, .0001f)
            assertEquals(0f, s.eyeYaw, .0001f)
            assertEquals(0f, s.blinkWeight, .0001f)
        }
        for (seconds in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val s = sample(seconds)
            assertEquals(0f, s.headPitch, 0f)
            assertEquals(0f, s.blinkWeight, 0f)
        }
        val first = sample(21.625f)
        val second = sample(21.625f)
        assertEquals(first.blinkWeight, second.blinkWeight, 0f)
        assertEquals(first.eyeYaw, second.eyeYaw, 0f)
    }

    @Test fun boundedAcrossEveryTenthSecondOfFullCycle() {
        for (frame in 0..4600) {
            val value = lid(frame / 10f)
            assertTrue("invalid blink weight at frame $frame: $value", value.isFinite() && value in 0f..1f)
        }
    }
}
