package at.heinz.deviceai.ui

/**
 * Positions of every start-screen element, measured from the owner's reference images.
 *
 * Phone reference: 412 dp wide (reference-phone.png, 713 px screen, 1.7306 px/dp), origin below the status bar.
 * Wide/Fold reference: 840 dp wide (reference-fold.png, 1294 px screen, 1.54 px/dp), origin at the screen top.
 * Other window sizes scale the whole composition uniformly with the width, so proportions never change.
 */
object ContinuumHomeLayout {
    enum class Slot {
        BRAND, SEARCH, NOTIFICATIONS, PROFILE, MODEL_PILL, HERO,
        LABEL_CONNECTED, LABEL_LEARNING, LABEL_DEVELOPING, LABEL_THINKING, LABEL_ANALYSING, LABEL_EVOLVING,
        MODEL, LEARNING, STORAGE, DEVELOPMENT, SYSTEM_STATUS, SYSTEM_ACTIVITY, WORKERS, NAV, RAIL
    }

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        fun scaled(u: Float) = Box(left * u, top * u, right * u, bottom * u)
    }

    /** [scale] maps reference dp to window dp; [contentHeight] is the scrolling part above a phone navigation bar. */
    data class Layout(val wide: Boolean, val width: Float, val scale: Float, val contentHeight: Float, val boxes: Map<Slot, Box>) {
        operator fun get(slot: Slot): Box? = boxes[slot]
    }

    const val PHONE_REFERENCE_WIDTH = 412f
    const val WIDE_REFERENCE_WIDTH = 840f
    const val PHONE_NAV_HEIGHT = 74f

    /** Shared Fold rail width in dp, used for both the rendered rail and the dashboard offset. */
    fun railWidthDp(windowWidthDp: Float): Float {
        require(windowWidthDp.isFinite() && windowWidthDp > 0f)
        return windowWidthDp * 76f / WIDE_REFERENCE_WIDTH
    }

    /**
     * The Activity may keep the cover-display configuration while its real window has
     * expanded to the Fold inner display. Rebuild only on a substantial actual WIDTH
     * change; height-only changes (IME / system bars) must never reset the screen.
     */
    fun needsWindowWidthReflow(configuredWidthDp: Float, measuredWidthDp: Float): Boolean {
        if (!configuredWidthDp.isFinite() || !measuredWidthDp.isFinite() ||
            configuredWidthDp <= 0f || measuredWidthDp <= 0f
        ) return false
        return kotlin.math.abs(configuredWidthDp - measuredWidthDp) >= 24f
    }

    /**
     * Wide layout (navigation rail) from 600 dp width and 480 dp height: the unfolded Fold also with a larger display
     * zoom (owner decision, 22.10.2026), tablets; phone otherwise (also the Fold cover display).
     */
    fun isWide(widthDp: Float, heightDp: Float) = widthDp >= 600f && heightDp >= 480f

    /** Unfolded Fold7 held upright: use the owner's 3-by-2 reference, never the landscape rail raster. */
    fun isPortraitFold(widthDp: Float, heightDp: Float): Boolean =
        isWide(widthDp, heightDp) && heightDp >= widthDp * 1.02f

    // Centerline of the hologram is 206 dp. Opposing controls use equal widths and mirrored x-coordinates.
    private val phoneReference = mapOf(
        Slot.BRAND to Box(0f, 0f, 276f, 68.8f),
        Slot.SEARCH to Box(279f, 22f, 311f, 54f),
        Slot.NOTIFICATIONS to Box(322f, 22f, 354f, 54f),
        Slot.PROFILE to Box(364f, 22f, 396f, 54f),
        Slot.HERO to Box(0f, 68.8f, 412f, 375.6f),
        Slot.LABEL_CONNECTED to Box(10f, 96f, 119f, 136f),
        Slot.LABEL_LEARNING to Box(10f, 165.4f, 119f, 205.4f),
        Slot.LABEL_DEVELOPING to Box(10f, 242.2f, 119f, 282.2f),
        Slot.LABEL_THINKING to Box(293f, 96f, 402f, 136f),
        Slot.LABEL_ANALYSING to Box(293f, 165.4f, 402f, 205.4f),
        Slot.LABEL_EVOLVING to Box(293f, 242.2f, 402f, 282.2f),
        Slot.MODEL to Box(13.3f, 380.3f, 398.8f, 469.4f),
        Slot.LEARNING to Box(13.3f, 479.6f, 215.5f, 661.6f),
        Slot.STORAGE to Box(224.8f, 479.6f, 398.8f, 661.6f),
        Slot.DEVELOPMENT to Box(13.3f, 673.2f, 398.8f, 773.2f),
        Slot.NAV to Box(0f, 787f, 412f, 787f + PHONE_NAV_HEIGHT)
    )

    // Wide reference centerline is 417 dp (the center of the existing hero slot).
    private val wideReference = mapOf(
        Slot.RAIL to Box(0f, 0f, 76f, 649f),
        Slot.BRAND to Box(80f, 30f, 420f, 86f),
        Slot.SEARCH to Box(579.9f, 42.2f, 608.5f, 70.8f),
        Slot.NOTIFICATIONS to Box(616.9f, 42.2f, 645.5f, 70.8f),
        Slot.PROFILE to Box(657.7f, 42.2f, 686.3f, 70.8f),
        Slot.MODEL_PILL to Box(709f, 30.5f, 826f, 72.7f),
        Slot.HERO to Box(212f, 0f, 622f, 291f),
        Slot.LABEL_CONNECTED to Box(188f, 97f, 302f, 138f),
        Slot.LABEL_LEARNING to Box(188f, 148.5f, 302f, 192f),
        Slot.LABEL_DEVELOPING to Box(188f, 201.3f, 302f, 247f),
        Slot.LABEL_THINKING to Box(532f, 97f, 646f, 138f),
        Slot.LABEL_ANALYSING to Box(532f, 148.5f, 646f, 192f),
        Slot.LABEL_EVOLVING to Box(532f, 201.3f, 646f, 247f),
        Slot.SYSTEM_STATUS to Box(657.1f, 84.4f, 829.2f, 276f),
        Slot.MODEL to Box(87.7f, 290.9f, 335.7f, 475.3f),
        Slot.LEARNING to Box(345.5f, 290.9f, 613f, 475.3f),
        Slot.STORAGE to Box(624.7f, 290.9f, 829.2f, 475.3f),
        Slot.DEVELOPMENT to Box(87.7f, 485.7f, 492.9f, 642.9f),
        Slot.SYSTEM_ACTIVITY to Box(504.5f, 485.7f, 829.2f, 642.9f)
    )

    fun phone(widthDp: Float): Layout {
        require(widthDp > 0f)
        val u = widthDp / PHONE_REFERENCE_WIDTH
        return Layout(false, widthDp, u, 787f * u, phoneReference.mapValues { it.value.scaled(u) })
    }

    const val WIDE_REFERENCE_HEIGHT = 649f
    private val TOP_BAR = setOf(Slot.BRAND, Slot.SEARCH, Slot.NOTIFICATIONS, Slot.PROFILE, Slot.MODEL_PILL)
    private val HERO_BAND = setOf(Slot.LABEL_CONNECTED, Slot.LABEL_LEARNING, Slot.LABEL_DEVELOPING, Slot.LABEL_THINKING,
        Slot.LABEL_ANALYSING, Slot.LABEL_EVOLVING, Slot.SYSTEM_STATUS)
    private val FIRST_ROW = setOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE)
    private val SECOND_ROW = setOf(Slot.DEVELOPMENT, Slot.SYSTEM_ACTIVITY)

    /**
     * Owner's supplied portrait reference: large left/right action cards around the orb,
     * a narrow status column, and two IDENTICAL three-column dashboard rows.
     * Reference canvas: 840 x 886 dp (content/window dimensions, excluding system bars).
     * Larger upright windows add height to the hero and both dashboard rows; compact
     * Fold7 display zoom retains the same horizontal card boundaries and scrolls
     * instead of clipping information. No rail exists in this composition.
     */
    fun portraitFold(widthDp: Float, heightDp: Float): Layout {
        require(widthDp > 0f && heightDp > 0f)
        val u = widthDp / WIDE_REFERENCE_WIDTH
        val requestedHeight = heightDp / u
        val frame = requestedHeight.coerceAtLeast(800f)
        val extra = (frame - 886f).coerceAtLeast(0f)
        // Image-registered portrait proportions: taller canvases grow the hero gently
        // and retain the reference's lower starfield margin, not giant stretched panels.
        val heroExtra = (extra * .22f).coerceAtMost(100f)
        val top = 400f + heroExtra
        val gap = 12f
        val bottomMargin = 12f + extra * .46f
        val cardHeight = ((frame - top - bottomMargin - gap) / 2f).coerceAtLeast(196f)
        val row2 = top + cardHeight + gap
        val positions = linkedMapOf(
            Slot.BRAND to Box(16f, 5f, 355f, 62f),
            Slot.SEARCH to Box(557f, 16f, 585f, 48f),
            Slot.NOTIFICATIONS to Box(600f, 16f, 628f, 48f),
            Slot.PROFILE to Box(640f, 15f, 670f, 49f),
            Slot.HERO to Box(229f, 60f, 501f, 390f + heroExtra),
            Slot.LABEL_CONNECTED to Box(12f, 77f, 229f, 166f + heroExtra / 3f),
            Slot.LABEL_LEARNING to Box(12f, 177f + heroExtra / 3f, 229f, 266f + heroExtra * 2f / 3f),
            Slot.LABEL_DEVELOPING to Box(12f, 277f + heroExtra * 2f / 3f, 229f, 366f + heroExtra),
            Slot.LABEL_THINKING to Box(501f, 77f, 669f, 166f + heroExtra / 3f),
            Slot.LABEL_ANALYSING to Box(501f, 177f + heroExtra / 3f, 669f, 266f + heroExtra * 2f / 3f),
            Slot.LABEL_EVOLVING to Box(501f, 277f + heroExtra * 2f / 3f, 669f, 366f + heroExtra),
            Slot.SYSTEM_STATUS to Box(679f, 74f, 828f, 383f + heroExtra),
            Slot.MODEL to Box(12f, top, 276f, top + cardHeight),
            Slot.LEARNING to Box(288f, top, 552f, top + cardHeight),
            Slot.STORAGE to Box(564f, top, 828f, top + cardHeight),
            Slot.DEVELOPMENT to Box(12f, row2, 276f, row2 + cardHeight),
            Slot.SYSTEM_ACTIVITY to Box(288f, row2, 552f, row2 + cardHeight),
            Slot.WORKERS to Box(564f, row2, 828f, row2 + cardHeight)
        )
        val finalHeight = maxOf(frame, row2 + cardHeight + 12f)
        return Layout(true, widthDp, u, finalHeight * u, positions.mapValues { it.value.scaled(u) })
    }

    /**
     * Wide composition for a [widthDp] x [heightDp] window, scaled uniformly with the width like the reference
     * (840 x 649 dp, landscape). A window that is taller than that proportion – the Fold7 main display held upright is
     * almost square – would otherwise end in an empty band. The extra height first enlarges the hero stage: the hologram
     * box grows in height and, as far as the side columns allow, in width, so the core itself becomes larger; the side
     * labels and the system status column move with the hologram's centre. What remains opens the gaps above both card
     * rows equally. Cards keep their reference shape. [heightDp] = 0 keeps the reference proportion.
     */
    fun wide(widthDp: Float, heightDp: Float = 0f): Layout {
        require(widthDp > 0f)
        if (heightDp > 0f && isPortraitFold(widthDp, heightDp)) return portraitFold(widthDp, heightDp)
        val u = widthDp / WIDE_REFERENCE_WIDTH
        val extra = if (heightDp > 0f) (heightDp / u - WIDE_REFERENCE_HEIGHT).coerceIn(0f, 420f) else 0f
        val hero = wideReference.getValue(Slot.HERO)
        val heroGrow = minOf(extra, 150f)
        // The renderer's width/height ratio governs enlargement, capped by the 8dp label clearance below.
        // Keep the actually rendered glass sphere outside both mirrored control columns.
        // Tall Fold windows enlarge the hero vertically; its width must not grow past the
        // inner text edges. The renderer's radius/sphere factors are the source of truth.
        val foldGeometry = ContinuumHologramRenderer.Geometry.FOLD
        val heroCenterX = (hero.left + hero.right) / 2f
        val controlInnerEdge = wideReference.getValue(Slot.LABEL_THINKING).left
        val sphereClearance = 8f
        val permittedSphereRadius = (controlInnerEdge - heroCenterX - sphereClearance).coerceAtLeast(0f)
        val permittedHeroWidth = permittedSphereRadius / (foldGeometry.radiusOfWidth * foldGeometry.sphere)
        val heroWiden = minOf(
            heroGrow * (foldGeometry.radiusOfHeight / foldGeometry.radiusOfWidth),
            140f,
            (permittedHeroWidth - hero.width).coerceAtLeast(0f)
        )
        val rowGap = (extra - heroGrow) / 2f
        val firstRow = heroGrow + rowGap
        val secondRow = heroGrow + 2f * rowGap
        val boxes = wideReference.mapValues { (slot, b) ->
            when {
                slot in TOP_BAR -> b
                slot == Slot.HERO -> Box(b.left - heroWiden / 2f, b.top, b.right + heroWiden / 2f, b.bottom + heroGrow)
                slot == Slot.RAIL -> b.copy(bottom = b.bottom + extra)
                slot in HERO_BAND -> {
                    val shift = heroGrow * ((b.top + b.bottom) / 2f - hero.top) / hero.height
                    b.copy(top = b.top + shift, bottom = b.bottom + shift)
                }
                slot in FIRST_ROW -> b.copy(top = b.top + firstRow, bottom = b.bottom + firstRow)
                slot in SECOND_ROW -> b.copy(top = b.top + secondRow, bottom = b.bottom + secondRow)
                else -> b
            }.scaled(u)
        }
        return Layout(true, widthDp, u, (WIDE_REFERENCE_HEIGHT + extra) * u, boxes)
    }
}
