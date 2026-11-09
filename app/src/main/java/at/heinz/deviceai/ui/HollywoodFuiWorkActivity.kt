package at.heinz.deviceai.ui

/** Restrained, decorative work light: never a progress indication. Uses the shell clock. */
internal object HollywoodFuiWorkActivity {
    const val SETTLED_RASTER_OPACITY = .09f
    private const val SCAN_EDGE_SHARE = .08f

    /** Keeps a faint raster while real work owns this window, without obscuring native text. */
    fun rasterOpacity(grid: Float, data: Float, working: Boolean, animate: Boolean): Float {
        if (!grid.isFinite() || !data.isFinite()) return 0f
        val g = grid.coerceIn(0f, 1f)
        val d = data.coerceIn(0f, 1f)
        val live = if (working && animate) SETTLED_RASTER_OPACITY else 0f
        return (g * (1f - d + live * d)).coerceIn(0f, 1f)
    }

    /** Fade at the scan wrap so the return does not flash across the settled glass. */
    fun scanEnvelope(phase: Float): Float {
        if (!phase.isFinite()) return 0f
        val p = phase.coerceIn(0f, 1f)
        return (minOf(p, 1f - p) / SCAN_EDGE_SHARE).coerceIn(0f, 1f)
    }
}
