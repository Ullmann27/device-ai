package at.heinz.deviceai.ui

/** Measured content width (not device width); larger system fonts get fewer, wider action tiles. */
object ContinuumGridPolicy {
    fun gaugeColumns(contentWidthDp: Float, fontScale: Float): Int {
        val font = if (fontScale.isFinite()) fontScale.coerceAtLeast(1f) else 1f
        return when {
            contentWidthDp >= 336f * font -> 4
            contentWidthDp >= 168f * font -> 2
            else -> 1
        }
    }
    fun columns(contentWidthDp: Float, fontScale: Float, maximum: Int): Int {
        if (!contentWidthDp.isFinite() || contentWidthDp <= 0f) return 1
        val font = if (fontScale.isFinite()) fontScale.coerceAtLeast(1f) else 1f
        return ((contentWidthDp + 8f) / (156f * font + 8f)).toInt().coerceIn(1, maximum.coerceAtLeast(1))
    }
}
