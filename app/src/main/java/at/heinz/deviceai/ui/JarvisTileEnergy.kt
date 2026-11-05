package at.heinz.deviceai.ui

/**
 * Decorative, status-neutral light travelling along a tile's INNER rim.
 * Samples the existing shell drawing time. This object owns no timer, state or View.
 * The caller must stop sampling when its host is paused/hidden or reduced motion is enabled.
 */
internal object JarvisTileEnergy {
    const val CYCLE_MS = 13_000L
    private const val INSET_DP = 3f
    private const val MIN_WIDTH_DP = 48f
    private const val MIN_HEIGHT_DP = 48f

    data class Sample(val x: Float, val y: Float, val phase: Float)

    /** Coordinates relative to a tile (dp), with a mathematically closed, repeating rim path. */
    fun sample(timeMs: Long, widthDp: Float, heightDp: Float, target: String): Sample? {
        if (!widthDp.isFinite() || !heightDp.isFinite() ||
            widthDp < MIN_WIDTH_DP || heightDp < MIN_HEIGHT_DP) return null
        val w = widthDp - 2f * INSET_DP
        val h = heightDp - 2f * INSET_DP
        // Bounded remainders handle negative previews and Long.MIN_VALUE, without newer Android Math APIs.
        val offset = ((target.hashCode() % 19 + 19) % 19).toLong() * 317L
        val positionMs = (((timeMs % CYCLE_MS + CYCLE_MS) % CYCLE_MS) + offset) % CYCLE_MS
        val phase = positionMs / CYCLE_MS.toFloat()
        val path = phase * 2f * (w + h)
        return when {
            path < w -> Sample(INSET_DP + path, INSET_DP, phase)
            path < w + h -> Sample(INSET_DP + w, INSET_DP + path - w, phase)
            path < 2f * w + h -> Sample(INSET_DP + w - (path - w - h), INSET_DP + h, phase)
            else -> Sample(INSET_DP, INSET_DP + h - (path - 2f * w - h), phase)
        }
    }
}
