package at.heinz.deviceai.ui

/** Measured content width (not device width); larger system fonts get fewer, wider action tiles. */
object ContinuumGridPolicy {
    fun columns(contentWidthDp: Float, fontScale: Float, maximum: Int): Int {
        if (!contentWidthDp.isFinite() || contentWidthDp <= 0f) return 1
        val font = if (fontScale.isFinite()) fontScale.coerceAtLeast(1f) else 1f
        return ((contentWidthDp + 8f) / (156f * font + 8f)).toInt().coerceIn(1, maximum.coerceAtLeast(1))
    }
}
