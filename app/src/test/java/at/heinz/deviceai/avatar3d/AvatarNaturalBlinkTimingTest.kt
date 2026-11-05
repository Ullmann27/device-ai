package at.heinz.deviceai.avatar3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preserve stable clock behavior with a varied blink cadence. */
class AvatarNaturalBlinkTimingTest {
    private fun pose(t: Float): AvatarIdleMotion = AvatarIdleMotion().also { it.update(t) }

    @Test fun initialBlinkAndClockWrapRemainUnchanged() {
        assertEquals(1f, pose(4.525f).blinkWeight, 0f)
        assertEquals(0f, pose(4.44f).blinkWeight, 0f)
        for (t in listOf(0f, -0f, 460f, -460f, 920f, -920f, -4600f)) {
            val p = pose(t)
            assertEquals(0f, p.blinkWeight, 0f)
            assertEquals(0f, p.headYaw, 0f)
        }
    }

    @Test fun blinkTimingVariesWithoutInvalidOrStuckWeights() {
        val p = AvatarIdleMotion()
        var starts = 0
        var previouslyClosed = true
        for (step in 0..92_000) {
            p.update(step * .005f)
            val weight = p.blinkWeight
            assertTrue("invalid blink weight at $step", weight.isFinite() && weight in 0f..1f)
            if (weight > 0f && previouslyClosed) starts++
            previouslyClosed = weight == 0f
        }
        assertEquals("100 primary + 20 secondary blinks", 120, starts)
        val primaryTimes = (0 until 100).map { group ->
            var peak = 0f
            var peakAt = 0f
            for (sample in 0..920) {
                val t = group * 4.6f + sample * .005f
                p.update(t)
                if (p.blinkWeight > peak) { peak = p.blinkWeight; peakAt = t }
            }
            peakAt
        }
        val gaps = primaryTimes.zipWithNext { a, b -> b - a }
        assertTrue("fixed 4.6s metronome remains", gaps.maxOrNull()!! - gaps.minOrNull()!! > .2f)
    }

    @Test fun sameTimestampReplaysExactlyAfterManyFrames() {
        val p = AvatarIdleMotion()
        for (t in listOf(.0f, 4.525f, 11.1f, 45.8f, 125.7f, 459.9f)) {
            p.update(t)
            val original = listOf(p.blinkWeight, p.eyeYaw, p.eyePitch, p.headYaw, p.headPitch)
            p.update(t + 3f)
            p.update(t)
            assertEquals(original, listOf(p.blinkWeight, p.eyeYaw, p.eyePitch, p.headYaw, p.headPitch))
        }
    }
}
