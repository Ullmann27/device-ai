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
        if (radius <= 0f) return  // alpha 0 still draws: constant, predictable work per frame
        val fill = unitHalos.getOrPut(color or (0xFF shl 24)) {
            Fill.Radial(0f, 0f, 1f, intArrayOf(Argb.withAlpha(color, 1f), Argb.withAlpha(color, 0f)))
        }
        p.save(); p.translate(cx, cy); p.scale(radius, radius)
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
