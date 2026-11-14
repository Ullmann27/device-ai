package at.heinz.deviceai.avatar3d

import kotlin.math.hypot
import kotlin.math.sin

/** Short gaze-change blink, purely sampled from the existing pose/frame clock. */
internal class GazeBlinkPolicy {
    private var previousX = Float.NaN
    private var previousY = Float.NaN
    private var triggeredAt = Float.NaN
    private var lastEventAt = Float.NEGATIVE_INFINITY
    private var lastSeconds = Float.NaN

    fun sample(x: Float, y: Float, seconds: Float, enabled: Boolean): Float {
        if (seconds.isFinite()) {
            if (seconds < lastSeconds) {
                previousX = Float.NaN
                previousY = Float.NaN
                triggeredAt = Float.NaN
                lastEventAt = Float.NEGATIVE_INFINITY
            }
            lastSeconds = seconds
        }
        if (!enabled || !x.isFinite() || !y.isFinite() || !seconds.isFinite()) {
            previousX = Float.NaN
            previousY = Float.NaN
            triggeredAt = Float.NaN
            return 0f
        }
        if (previousX.isFinite() && hypot(x - previousX, y - previousY) > .35f &&
            seconds - lastEventAt >= 1.8f) {
            triggeredAt = seconds
            lastEventAt = seconds
        }
        previousX = x
        previousY = y
        val age = seconds - triggeredAt
        if (!age.isFinite() || age < 0f || age > .11f) return 0f
        return sin((Math.PI * age / .11f).toFloat()).coerceIn(0f, 1f)
    }
}
