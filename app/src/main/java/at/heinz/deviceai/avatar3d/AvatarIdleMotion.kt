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
        // Preserve 150-ms windows and the existing full-closure contract at 4.525 s.
        // Close in 55 ms, hold for 20 ms, reopen in 75 ms; all joins are smooth.
        // Every fifth group adds a second, earlier blink (100 + 20 per 460-second cycle).
        val extra = if (group % 5 == 2) pulse(local, 4.205f) else 0f
        blinkWeight = maxOf(pulse(local, 4.505f), extra)
    }

    private fun pulse(t: Float, apex: Float): Float {
        val offset = t - apex
        val progress = when {
            offset <= 0f -> (offset + .055f) / .055f
            offset <= .020f -> 1f
            else -> (.095f - offset) / .075f
        }
        val u = progress.coerceIn(0f, 1f)
        // Minimum-jerk eyelid easing: no triangular cusp at start, apex or finish.
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

    private companion object {
        const val SLOTS = 115
        const val CYCLE_SECONDS = 460.0
        const val SACCADE_SECONDS = .18
        const val BLINK_PERIOD = 4.6
        // Each pair sums to 8 s; the last slot is 4 s. Every interval is 3..5 s.
        // Constructed once, not once per frame. Quarter-second values are exactly representable.
        val STARTS = DoubleArray(SLOTS + 1).also { starts ->
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
