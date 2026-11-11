package at.heinz.deviceai.ui

import android.graphics.RectF

/**
 * Depth of the work window (owner brief 06.11.2026, „Dokument tritt hinter den Entwurf zurück – zwei Ebenen“).
 *
 * Behind the front glass of a document window stand narrow, translucent sheets – one for each REAL further page of the
 * document, at most three, never for a page count that is not known. When the draft unfolds out of the document window
 * the document plane steps back into the first rear sheet of the draft, and the real page recedes from its place in the
 * document window into the source-card slot of the draft, where the body's own page view takes over.
 *
 * Pure geometry on the shell's frame clock: every function writes into a caller-owned [RectF], nothing is allocated per
 * frame, and a non-finite or empty input is refused (returns false, target untouched) instead of drawing garbage.
 */
object JarvisWorkWindowDepth {
    /** Sheets behind the front window at most – more would read as a fan, not as pages. */
    const val MAX_LAYERS = 3
    /** Visible strip of each rear sheet above the one in front of it, dp. */
    const val STEP_DP = 7f
    /** Each sheet is narrower by this on both sides (further away in a slight perspective), dp. */
    const val INSET_DP = 9f
    /** Opacity of the first rear sheet relative to the front glass; the ones behind fade further. */
    const val LAYER_ALPHA = .42f
    private const val LAYER_FADE = .26f
    /** The receding page stays fully visible until here, then hands over to the real page view. */
    private const val HANDOVER_FROM = .72f

    // ---- Cinema 2.0: the front glass is dense only where real content stands --------------------------------------

    /** Dense glass continues this far below the last content pixel (dp). */
    const val GLASS_CONTENT_MARGIN_DP = 12f
    /** Height over which the dense glass thins out into the vacant smoke (dp). */
    const val GLASS_FADE_DP = 64f
    /** Density of the vacant glass relative to the dense sheet: smoke, never fully clear. */
    const val GLASS_VACANT_DENSITY = .34f
    private const val GLASS_MIN_VACANT_DP = 56f

    /** Where the dense sheet ends in a panel whose content does not fill it. */
    class GlassVacancy(val denseBottomDp: Float) {
        val fadeEndDp: Float get() = denseBottomDp + GLASS_FADE_DP
        /** Glass density at [yDp] (panel coordinates): 1 behind content, falling smoothly to the smoke floor. */
        fun density(yDp: Float): Float {
            if (!yDp.isFinite() || yDp <= denseBottomDp) return 1f
            val t = ((yDp - denseBottomDp) / GLASS_FADE_DP).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            return 1f - (1f - GLASS_VACANT_DENSITY) * eased
        }
    }

    /**
     * A work window whose content ends well above its bottom no longer stands there as one opaque slab: below the
     * content the glass clears to smoke and the holographic stage stays visible. Null keeps the single dense sheet –
     * for content that fills or scrolls the panel and for every non-finite input. Text never stands on clear glass.
     */
    fun glassVacancy(panelHeightDp: Float, contentBottomDp: Float): GlassVacancy? {
        if (!panelHeightDp.isFinite() || !contentBottomDp.isFinite() || panelHeightDp <= 0f || contentBottomDp < 0f)
            return null
        val dense = contentBottomDp + GLASS_CONTENT_MARGIN_DP
        return if (panelHeightDp - dense < GLASS_MIN_VACANT_DP) null else GlassVacancy(dense)
    }

    /** Rear sheets for a document of [pageCount] pages: its further pages, capped. */
    fun layers(pageCount: Int): Int = (pageCount - 1).coerceIn(0, MAX_LAYERS)

    /**
     * Rear sheet [index] (1 = directly behind the front) of a window whose front glass is [front] at [openness]
     * (0 = folded shut, sheets hidden behind the seed; 1 = open, sheets fanned out). Writes [out].
     */
    fun layer(front: RectF, index: Int, openness: Float, density: Float, out: RectF): Boolean {
        if (index !in 1..MAX_LAYERS || !usable(front) || !openness.isFinite() || !density.isFinite() || density <= 0f) return false
        val spread = openness.coerceIn(0f, 1f) * index
        val lift = STEP_DP * density * spread
        // Never let the two insets meet: a narrow window (outer display) keeps at least half its width.
        val inset = minOf(INSET_DP * density * spread, front.width() * .25f)
        out.set(front.left + inset, front.top - lift, front.right - inset, front.bottom)
        return true
    }

    /** Opacity of rear sheet [index] while the window's own glass has [opacity]. */
    fun layerAlpha(index: Int, opacity: Float): Float {
        if (index !in 1..MAX_LAYERS || !opacity.isFinite()) return 0f
        return (LAYER_ALPHA * (1f - LAYER_FADE * (index - 1)) * opacity.coerceIn(0f, 1f)).coerceAtLeast(0f)
    }

    /**
     * The page on its way back: from [from] (its place in the document window) to [to] (the source-card slot of the
     * draft), with an ease-out so it leaves quickly and settles softly. Writes [out].
     */
    fun recede(from: RectF, to: RectF, progress: Float, out: RectF): Boolean {
        if (!usable(from) || !usable(to) || !progress.isFinite()) return false
        val e = easeOut(progress.coerceIn(0f, 1f))
        out.set(from.left + (to.left - from.left) * e, from.top + (to.top - from.top) * e,
            from.right + (to.right - from.right) * e, from.bottom + (to.bottom - from.bottom) * e)
        return true
    }

    /** The drawn page is opaque on its way and fades only at the end, when the body's own page view shows it. */
    fun recedeAlpha(progress: Float): Float {
        if (!progress.isFinite()) return 0f
        if (progress <= HANDOVER_FROM) return 1f
        if (progress >= 1f) return 0f
        return 1f - (progress - HANDOVER_FROM) / (1f - HANDOVER_FROM)
    }

    /** The real page view in the body may show itself. */
    fun handedOver(progress: Float): Boolean = progress.isFinite() && progress >= 1f

    /**
     * The document plane while the draft unfolds in front of it: from the document window [document] to the first rear
     * sheet of the draft's [destination]. Writes [out].
     */
    fun plane(document: RectF, destination: RectF, progress: Float, density: Float, out: RectF): Boolean {
        if (!usable(document) || !progress.isFinite()) return false
        val rearLeft: Float; val rearTop: Float; val rearRight: Float; val rearBottom: Float
        run {
            // The settled sheet, computed inline so no scratch rectangle is needed per frame.
            if (!usable(destination) || !density.isFinite() || density <= 0f) return false
            val inset = minOf(INSET_DP * density, destination.width() * .25f)
            rearLeft = destination.left + inset; rearTop = destination.top - STEP_DP * density
            rearRight = destination.right - inset; rearBottom = destination.bottom
        }
        val e = easeOut(progress.coerceIn(0f, 1f))
        out.set(document.left + (rearLeft - document.left) * e, document.top + (rearTop - document.top) * e,
            document.right + (rearRight - document.right) * e, document.bottom + (rearBottom - document.bottom) * e)
        return true
    }

    /** The plane dims from the full front glass to the first rear sheet, never to nothing. */
    fun planeAlpha(progress: Float): Float {
        if (!progress.isFinite()) return 0f
        val settled = layerAlpha(1, 1f)
        return 1f + (settled - 1f) * easeOut(progress.coerceIn(0f, 1f))
    }

    private fun easeOut(p: Float): Float { val q = 1f - p; return 1f - q * q * q }

    private fun usable(box: RectF) = box.left.isFinite() && box.top.isFinite() && box.right.isFinite() &&
        box.bottom.isFinite() && box.width() > 0f && box.height() > 0f
}
