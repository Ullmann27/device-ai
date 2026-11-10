package at.heinz.deviceai.ui

import android.graphics.RectF

/**
 * EXP03 contract: a protected depth plate distinct from the generic window backing
 * (`DEPTH_GLASS_FILL`). Each plate recedes inside an already computed rear sheet and stays
 * behind the window's clip-out of head, orb and dialog. No clock of its own; writes [out]
 * so a frame never allocates.
 */
object JarvisProtectedDepthSurfaces {
    /** Extra inward recess beyond [JarvisWorkWindowDepth.INSET_DP], in dp. */
    const val RECESS_DP = 4f
    /** Inner Fresnel stroke relative to the plate's glass alpha. */
    const val FRESNEL_ALPHA = .22f

    /**
     * Recessed plate inside [sheet]. Returns false and leaves [out] untouched when the
     * input is not drawable (the caller then keeps the generic backing only).
     */
    fun recess(sheet: RectF, density: Float, out: RectF): Boolean {
        if (!usable(sheet) || !density.isFinite() || density <= 0f) return false
        val inset = minOf(RECESS_DP * density, minOf(sheet.width(), sheet.height()) * .12f)
        if (sheet.width() - 2f * inset <= 0f || sheet.height() - 2f * inset <= 0f) return false
        out.set(sheet.left + inset, sheet.top + inset, sheet.right - inset, sheet.bottom - inset)
        return usable(out)
    }

    fun plateAlpha(layerAlpha: Float): Float {
        if (!layerAlpha.isFinite()) return 0f
        return (layerAlpha * .85f).coerceIn(0f, 1f)
    }

    private fun usable(box: RectF) = box.left.isFinite() && box.top.isFinite() &&
        box.right.isFinite() && box.bottom.isFinite() && box.width() > 0f && box.height() > 0f
}
