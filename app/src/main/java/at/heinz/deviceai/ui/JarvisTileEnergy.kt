package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decorative, status-neutral light travelling along a tile's rounded INNER rim.
 * Samples the existing shell draw time: no timer, runtime state or additional View.
 * The caller owns pause/visibility and reduced-motion gating.
 */
internal object JarvisTileEnergy {
    const val CYCLE_MS = 13_000L
    private const val INSET_DP = 3f
    private const val MIN_SIZE_DP = 48f
    private const val MAX_SIZE_DP = 16_384f
    private const val DEFAULT_CORNER_DP = 8f
    private val QUARTER_TURN = (PI / 2.0).toFloat()

    data class Sample(val x: Float, val y: Float, val phase: Float)

    /**
     * Constant-speed, tangent-continuous closed path following the glass control's rounded rim.
     * The particle remains [INSET_DP] inside the outer stroke, including at all four corners.
     * All geometry and time inputs are supplied by the existing renderer (dp and uptime ms).
     */
    fun sample(timeMs: Long, widthDp: Float, heightDp: Float, target: String,
               cornerRadiusDp: Float = DEFAULT_CORNER_DP): Sample? {
        if (!widthDp.isFinite() || !heightDp.isFinite() || !cornerRadiusDp.isFinite() ||
            widthDp < MIN_SIZE_DP || heightDp < MIN_SIZE_DP ||
            widthDp > MAX_SIZE_DP || heightDp > MAX_SIZE_DP || cornerRadiusDp < 0f) return null
        val w = widthDp - 2f * INSET_DP
        val h = heightDp - 2f * INSET_DP
        val r = (cornerRadiusDp - INSET_DP).coerceIn(0f, minOf(w, h) / 2f)
        val top = w - 2f * r
        val side = h - 2f * r
        val arc = QUARTER_TURN * r
        val perimeter = 2f * (top + side) + 4f * arc
        // Safe for Long.MIN_VALUE; the target offset decorrelates adjacent decorative tiles.
        val offset = ((target.hashCode() % 19 + 19) % 19).toLong() * 317L
        val positionMs = (((timeMs % CYCLE_MS + CYCLE_MS) % CYCLE_MS) + offset) % CYCLE_MS
        val phase = positionMs / CYCLE_MS.toFloat()
        var distance = phase * perimeter
        fun sample(x: Float, y: Float) = Sample(x, y, phase)
        if (r == 0f) {
            // Sharp-corner fallback only when the actual control has no usable corner radius.
            return when {
                distance < w -> sample(INSET_DP + distance, INSET_DP)
                distance < w + h -> sample(INSET_DP + w, INSET_DP + distance - w)
                distance < 2f * w + h -> sample(INSET_DP + w - (distance - w - h), INSET_DP + h)
                else -> sample(INSET_DP, INSET_DP + h - (distance - 2f * w - h))
            }
        }
        if (distance < top) return sample(INSET_DP + r + distance, INSET_DP)
        distance -= top
        if (distance < arc) {
            val angle = -QUARTER_TURN + distance / r
            return sample(INSET_DP + w - r + r * cos(angle), INSET_DP + r + r * sin(angle))
        }
        distance -= arc
        if (distance < side) return sample(INSET_DP + w, INSET_DP + r + distance)
        distance -= side
        if (distance < arc) {
            val angle = distance / r
            return sample(INSET_DP + w - r + r * cos(angle), INSET_DP + h - r + r * sin(angle))
        }
        distance -= arc
        if (distance < top) return sample(INSET_DP + w - r - distance, INSET_DP + h)
        distance -= top
        if (distance < arc) {
            val angle = QUARTER_TURN + distance / r
            return sample(INSET_DP + r + r * cos(angle), INSET_DP + h - r + r * sin(angle))
        }
        distance -= arc
        if (distance < side) return sample(INSET_DP, INSET_DP + h - r - distance)
        distance -= side
        val angle = 2f * QUARTER_TURN + distance / r
        return sample(INSET_DP + r + r * cos(angle), INSET_DP + r + r * sin(angle))
    }
}
