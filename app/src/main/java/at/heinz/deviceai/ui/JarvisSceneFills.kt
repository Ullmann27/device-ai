package at.heinz.deviceai.ui

/**
 * Allocation-free fills for the per-frame scene layers. Android caches one native shader per [Fill] instance, so the
 * animated layers reuse instances: halos use one unit radial fill per colour (moved by transform, faded by alpha),
 * other fills are cached by a caller key and rebuilt only when that key (geometry or a quantised hue step) changes.
 */
object JarvisSceneFills {
    private val unitHalos = HashMap<Int, Fill>()
    private val keyed = HashMap<Any, Fill>()

    /** Same look as [JarvisNeon.halo] with [strength] as paint alpha; no new Fill or shader per frame. */
    fun halo(p: ContinuumPainter, cx: Float, cy: Float, radius: Float, color: Int, strength: Float) {
        ellipseHalo(p, cx, cy, radius, radius, color, strength)
    }

    /** Cached elliptical surface light. */
    fun ellipseHalo(p: ContinuumPainter, cx: Float, cy: Float, rx: Float, ry: Float,
                    color: Int, strength: Float) {
        if (!cx.isFinite() || !cy.isFinite() || !rx.isFinite() || !ry.isFinite() || rx <= 0f || ry <= 0f) return
        if (unitHalos.size >= 256 && !unitHalos.containsKey(color or (0xFF shl 24))) unitHalos.clear()
        val fill = unitHalos.getOrPut(color or (0xFF shl 24)) {
            Fill.Radial(0f, 0f, 1f, intArrayOf(Argb.withAlpha(color, 1f), Argb.withAlpha(color, 0f)))
        }
        p.save(); p.translate(cx, cy); p.scale(rx, ry)
        p.fillCircle(0f, 0f, 1f, fill, strength.coerceIn(0f, 1f))
        p.restore()
    }

    /** Fill cached under [key]; [build] runs only for a new key. The cache is bounded. */
    fun cached(key: Any, build: () -> Fill): Fill {
        keyed[key]?.let { return it }
        if (keyed.size > 256) keyed.clear()
        return build().also { keyed[key] = it }
    }
}
