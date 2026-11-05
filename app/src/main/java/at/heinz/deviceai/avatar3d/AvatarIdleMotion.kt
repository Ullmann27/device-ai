package at.heinz.deviceai.avatar3d

import kotlin.math.PI
import kotlin.math.sin

/**
 * Cosmetic motion sampled from the existing 460-second view clock. No event/state inference,
 * random source, timer, listener or integration history. Owned by one renderer-thread mapper.
 * Sampling the same time always gives the same pose, including after pause or skipped frames.
 */
internal class AvatarIdleMotion {
    var headYaw = 0f; private set
    var headPitch = 0f; private set
    var eyeYaw = 0f; private set
    var eyePitch = 0f; private set
    var blinkWeight = 0f; private set

    fun update(seconds: Float) {
        headYaw = 0f; headPitch = 0f; eyeYaw = 0f; eyePitch = 0f; blinkWeight = 0f
        if (!seconds.isFinite()) return
        val remainder = seconds.toDouble() % CYCLE_SECONDS
        val t = when {
            // binarySearch orders -0.0 before STARTS[0]; canonicalize exact cycle boundaries.
            remainder == 0.0 -> 0.0
            remainder < 0.0 -> remainder + CYCLE_SECONDS
            else -> remainder
        }
        val phase = t * (2.0 * PI / CYCLE_SECONDS)
        // Preserve the existing small head harmonics and their continuous clock wrap.
        // |headYaw| <= HEAD_YAW_AMPLITUDE and |headPitch| <= HEAD_PITCH_AMPLITUDE (sum of the harmonic amplitudes).
        headYaw = (1.20 * sin(phase * 27) + .35 * sin(phase * 65)).toFloat()
        headPitch = (.62 * sin(phase * 34) + .20 * sin(phase * 67)).toFloat()

        val found = STARTS.binarySearch(t)
        val slot = if (found >= 0) found.coerceAtMost(SLOTS - 1) else -found - 2
        val previous = if (slot == 0) SLOTS - 1 else slot - 1
        val u = ((t - STARTS[slot]) / SACCADE_SECONDS).coerceIn(0.0, 1.0)
        // Zero velocity/acceleration at both ends; hold the target between saccades.
        val blend = (u * u * u * (u * (u * 6.0 - 15.0) + 10.0)).toFloat()
        eyeYaw = interpolate(target(previous, 17, 3.5f), target(slot, 17, 3.5f), blend)
        eyePitch = interpolate(target(previous, 71, 1.4f), target(slot, 71, 1.4f), blend)

        val group = (t / BLINK_PERIOD).toInt().coerceIn(0, 99)
        val local = (t - group * BLINK_PERIOD).toFloat()
        // Vary cadence without a random timer.
        val primary = 4.525f - blinkLead(group)
        val extra = if (group % 5 == 2) pulse(local, primary - .30f) else 0f
        blinkWeight = maxOf(pulse(local, primary), extra)
    }

    private fun blinkLead(group: Int): Float {
        if (group == 0 || group == 99) return 0f
        var bits = group + 13579
        bits = (bits xor (bits ushr 16)) * 0x45d9f3b
        bits = (bits xor (bits ushr 16)) * 0x45d9f3b
        bits = bits xor (bits ushr 16)
        return ((bits ushr 1) % 1001) / 1000f * .44f
    }

    /**
     * Eyelid closure inside the unchanged 150 ms window [center − 75 ms, center + 75 ms]: 55 ms close, 20 ms full
     * closure ending exactly at [center] (existing contract: closed at 4.525 s), 75 ms reopen. Each phase uses the
     * minimum-jerk quintic, so the lid starts, settles and reopens with zero velocity instead of the former
     * triangular kink (idea from #551, ported to this cadence).
     */
    private fun pulse(t: Float, center: Float): Float {
        val offset = t - center
        val progress = when {
            offset <= -BLINK_HOLD_S -> (offset + BLINK_HALF_S) / BLINK_CLOSE_S
            offset <= 0f -> 1f
            else -> (BLINK_HALF_S - offset) / BLINK_HALF_S
        }
        val u = progress.coerceIn(0f, 1f)
        return (u * u * u * (u * (u * 6f - 15f) + 10f)).coerceIn(0f, 1f)
    }
    private fun interpolate(a: Float, b: Float, amount: Float) = a + (b - a) * amount

    private fun target(slot: Int, salt: Int, amplitude: Float): Float {
        // End at the neutral target so the next cycle starts without a positional jump.
        if (slot == SLOTS - 1) return 0f
        var bits = slot + 1 + salt
        bits = (bits xor (bits ushr 16)) * 0x45d9f3b
        bits = (bits xor (bits ushr 16)) * 0x45d9f3b
        bits = bits xor (bits ushr 16)
        return (((bits ushr 1) % 2001 - 1000) / 1000f) * amplitude
    }

    internal companion object {
        /** Upper bounds of the cosmetic head harmonics above (degrees, before the mapper's idle gain). */
        const val HEAD_YAW_AMPLITUDE = 1.20f + .35f
        const val HEAD_PITCH_AMPLITUDE = .62f + .20f
        private const val BLINK_HALF_S = .075f
        private const val BLINK_HOLD_S = .020f
        private const val BLINK_CLOSE_S = BLINK_HALF_S - BLINK_HOLD_S
        private const val SLOTS = 115
        private const val CYCLE_SECONDS = 460.0
        private const val SACCADE_SECONDS = .18
        private const val BLINK_PERIOD = 4.6
        // Each pair sums to 8 s; the last slot is 4 s. Every interval is 3..5 s.
        // Constructed once, not once per frame. Quarter-second values are exactly representable.
        private val STARTS = DoubleArray(SLOTS + 1).also { starts ->
            for (slot in 0 until SLOTS) {
                val short = 3.0 + ((slot / 2 * 7) % 5) * .25
                val duration = when {
                    slot == SLOTS - 1 -> 4.0
                    slot % 2 == 0 -> short
                    else -> 8.0 - short
                }
                starts[slot + 1] = starts[slot] + duration
            }
            check(starts.last() == CYCLE_SECONDS)
        }
    }
}
