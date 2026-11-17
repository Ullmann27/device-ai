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
        val sheetW = sheet.right - sheet.left
        val sheetH = sheet.bottom - sheet.top
        val inset = minOf(RECESS_DP * density, minOf(sheetW, sheetH) * .12f)
        if (sheetW - 2f * inset <= 0f || sheetH - 2f * inset <= 0f) return false
        out.left = sheet.left + inset
        out.top = sheet.top + inset
        out.right = sheet.right - inset
        out.bottom = sheet.bottom - inset
        return usable(out)
    }

    fun plateAlpha(layerAlpha: Float): Float {
        if (!layerAlpha.isFinite()) return 0f
        return (layerAlpha * .85f).coerceIn(0f, 1f)
    }

    private fun usable(box: RectF) = box.left.isFinite() && box.top.isFinite() &&
        box.right.isFinite() && box.bottom.isFinite() &&
        box.right - box.left > 0f && box.bottom - box.top > 0f
}
