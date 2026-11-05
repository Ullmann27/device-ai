package at.heinz.deviceai.avatar3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Smooth eyelids on the existing 460 s view clock (idea from #551, ported to the varied cadence of #564).
 * The former triangular pulse jumped from 0 to 13.3 weight/s at the start of every blink.
 */
class AvatarBlinkEasingTest {
    private fun lid(seconds: Float) = AvatarIdleMotion().also { it.update(seconds) }.blinkWeight

    /** Change of the lid velocity across [seconds] (1 ms one-sided differences). */
    private fun velocityJump(seconds: Float): Float {
        val d = .001f
        val left = (lid(seconds) - lid(seconds - d)) / d
        val right = (lid(seconds + d) - lid(seconds)) / d
        return abs(left - right)
    }

    /** Apex (end of the full closure) of every primary and secondary blink, sampled at 1 ms. */
    private val apexes: List<Float> by lazy {
        val out = ArrayList<Float>(); var previous = 0f
        for (ms in 0 until 460_000) {
            val w = lid(ms / 1000f)
            if (previous == 1f && w < 1f) out += (ms - 1) / 1000f
            previous = w
        }
        out
    }

    @Test fun everyBlinkStartsClosesAndReopensWithoutAVelocityKink() {
        assertEquals("100 primary + 20 secondary blinks", 120, apexes.size)
        for (apex in apexes) for (join in listOf(apex - .075f, apex - .020f, apex, apex + .075f)) {
            assertTrue("lid velocity kink at $join: ${velocityJump(join)}", velocityJump(join) < 1.5f)
        }
    }

    @Test fun existingClosureContractAndWindowAreKept() {
        assertEquals(1f, lid(4.525f), 0f)
        assertEquals(1f, lid(4.506f), 0f)
        assertEquals(0f, lid(4.44f), 0f)
        assertEquals(0f, lid(4.449f), 0f)
        assertEquals(0f, lid(4.601f), 0f)
        assertTrue("closing is under way after 20 ms", lid(4.47f) in .05f..0.6f)
    }
}
