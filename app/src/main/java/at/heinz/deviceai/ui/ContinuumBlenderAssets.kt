package at.heinz.deviceai.ui

/**
 * Blender-rendered decorative rasters (tools/blender/continuum_scenes.py → tools/blender/export_assets.py):
 * glass orb with brain on a light pedestal, spaceship-interior background with depth of field, one small hologram
 * scene per dock tile. Pure presentation: no runtime status is baked into any image.
 *
 * Every entry point returns false when the raster could not be drawn (painter without raster support, asset
 * missing, decode failure, cache full, [enabled] off); callers then keep their existing vector path unchanged.
 * Isolated loader so parallel renderer work only needs a one-line hook.
 */
object ContinuumBlenderAssets {
    /** Global switch (owner setting / thermal fallback). Off = every hook falls back to the vector renderer. */
    @Volatile var enabled: Boolean = true

    const val DIR = "jarvis-reference/blender/"

    data class Asset(val path: String, val width: Int, val height: Int)

    val ORB_LARGE = Asset(DIR + "orb-1024.webp", 1024, 1024)
    val ORB_SMALL = Asset(DIR + "orb-512.webp", 512, 512)
    /** Fold7 inner display, tablets, landscape phones. */
    val BACKGROUND_WIDE = Asset(DIR + "bg-wide-1600x1200.webp", 1600, 1200)
    /** Portrait phones (S26, Xiaomi; ~9:19.5). */
    val BACKGROUND_TALL = Asset(DIR + "bg-tall-900x1800.webp", 900, 1800)

    /** Dock target → tile scene (320 × 240). Targets without a scene keep the plain glass body. */
    val TILES: Map<String, Asset> = listOf("chat", "files", "research", "agents", "memory", "device", "settings",
        "home", "web").associateWith { Asset(DIR + "tile-$it.webp", 320, 240) }

    /**
     * Orb geometry inside the orb image, as fractions of the image width (square image), measured by projecting the
     * sphere centre and equator in the Blender camera (tools/blender/out/metrics.json, copied into manifest.json).
     */
    const val ORB_CENTER_X = 0.5f
    const val ORB_CENTER_Y = 0.457f
    const val ORB_RADIUS = 0.2415f

    /** Raster opacity over the AGSL/vector background: keeps panel text contrast and some animated haze. */
    const val BACKGROUND_ALPHA = .86f
    /** Tile scene opacity under the glass gloss, icon and label. */
    const val TILE_ALPHA = .50f

    /** Image whose pixels best match an orb of radius [radiusDp] at [density] (no needless 1024 decode on phones). */
    fun orbAsset(radiusDp: Float, density: Float): Asset {
        val imagePx = radiusDp / ORB_RADIUS * density.coerceAtLeast(.5f)
        return if (imagePx <= ORB_SMALL.width * 1.25f) ORB_SMALL else ORB_LARGE
    }

    fun backgroundAsset(width: Float, height: Float): Asset =
        if (height > 0f && width / height < .75f) BACKGROUND_TALL else BACKGROUND_WIDE

    /** Centre-crop ("cover") rect of an [asset] scaled to fill [w] × [h] at origin [l],[t]. */
    fun cover(asset: Asset, l: Float, t: Float, w: Float, h: Float): FloatArray {
        val scale = maxOf(w / asset.width, h / asset.height)
        val dw = asset.width * scale; val dh = asset.height * scale
        val left = l + (w - dw) / 2f; val top = t + (h - dh) / 2f
        return floatArrayOf(left, top, left + dw, top + dh)
    }

    private fun valid(vararg v: Float) = v.all { it.isFinite() }

    /** Orb (sphere centre [cx],[cy], radius [r] in painter units). */
    fun drawOrb(p: ContinuumPainter, cx: Float, cy: Float, r: Float, density: Float, alpha: Float = 1f): Boolean {
        if (!enabled || r <= 0f || !valid(cx, cy, r, density, alpha)) return false
        val asset = orbAsset(r, density)
        val size = r / ORB_RADIUS
        val left = cx - ORB_CENTER_X * size; val top = cy - ORB_CENTER_Y * size
        return p.drawRaster(asset.path, left, top, left + size, top + size, alpha)
    }

    /** Full-page background, centre-cropped and clipped to [w] × [h]. */
    fun drawBackground(p: ContinuumPainter, w: Float, h: Float, alpha: Float = BACKGROUND_ALPHA): Boolean {
        if (!enabled || w <= 0f || h <= 0f || !valid(w, h, alpha)) return false
        val asset = backgroundAsset(w, h)
        val rect = cover(asset, 0f, 0f, w, h)
        p.save()
        try {
            p.clipRect(0f, 0f, w, h)
            return p.drawRaster(asset.path, rect[0], rect[1], rect[2], rect[3], alpha)
        } finally { p.restore() }
    }

    /** Tile scene for [target] inside the rounded tile body; false when the target has no scene. */
    fun drawTile(p: ContinuumPainter, target: String, l: Float, t: Float, r: Float, b: Float, radius: Float,
                 alpha: Float = TILE_ALPHA): Boolean {
        if (!enabled || r <= l || b <= t || !valid(l, t, r, b, radius, alpha)) return false
        val asset = TILES[target] ?: return false
        val rect = cover(asset, l, t, r - l, b - t)
        p.save()
        try {
            p.clipRoundRect(l, t, r, b, radius)
            return p.drawRaster(asset.path, rect[0], rect[1], rect[2], rect[3], alpha)
        } finally { p.restore() }
    }

    /** All packaged paths (tests verify presence, format and size). */
    val all: List<Asset> get() = listOf(ORB_LARGE, ORB_SMALL, BACKGROUND_WIDE, BACKGROUND_TALL) + TILES.values
}
