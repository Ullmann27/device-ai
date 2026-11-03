package at.heinz.deviceai.ui

/** Adaptive native cockpit geometry. Controls remain usable as the window changes size. */
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

    /** Coordinates are real dp. [scale] remains 1; font size never follows window width. */
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

    /** Unfolded Fold7 held upright: balanced core, six cards, persistent work-area navigation. */
    fun isPortraitFold(widthDp: Float, heightDp: Float): Boolean =
        isWide(widthDp, heightDp) && heightDp >= widthDp * 1.02f

    const val WIDE_REFERENCE_HEIGHT = 649f

    @JvmOverloads
    fun phone(widthDp: Float, fontScale: Float = 1f): Layout = adaptive(widthDp, 0f, false, fontScale)

    @JvmOverloads
    fun portraitFold(widthDp: Float, heightDp: Float, fontScale: Float = 1f): Layout =
        adaptive(widthDp, heightDp, true, fontScale)

    @JvmOverloads
    fun wide(widthDp: Float, heightDp: Float = 0f, fontScale: Float = 1f): Layout =
        adaptive(widthDp, heightDp, true, fontScale)

    private fun adaptive(width: Float, height: Float, wide: Boolean, fontScale: Float): Layout {
        require(width.isFinite() && width >= 240f && height.isFinite() && height >= 0f)
        require(fontScale.isFinite() && fontScale > 0f)
        val font = fontScale.coerceIn(.85f, 2f)
        val gap = 12f
        val portrait = wide && height > 0f && isPortraitFold(width, height)
        // Activity owns the existing navigation rail and its callbacks. Keep its exact offset.
        val rail = if (wide && !portrait) railWidthDp(width) else 0f
        val left = rail + 12f
        val right = width - 12f
        val room = right - left
        val boxes = linkedMapOf<Slot, Box>()
        val separateHeaderRows = room < 420f || font > 1.3f && room < 650f
        val brandHeight = 54f + (font - 1f).coerceAtLeast(0f) * 20f
        val headerBottom = if (separateHeaderRows) brandHeight + 56f else maxOf(64f, brandHeight)
        boxes[Slot.BRAND] = Box(left, 4f, if (separateHeaderRows) right else right - 168f, brandHeight)
        listOf(Slot.SEARCH, Slot.NOTIFICATIONS, Slot.PROFILE).forEachIndexed { i, slot ->
            val x = right - 160f + i * 56f
            val y = if (separateHeaderRows) brandHeight + 4f else (headerBottom - 48f) / 2f
            boxes[slot] = Box(x, y, x + 48f, y + 48f)
        }
        val heroTop = headerBottom + gap
        val heroHeight = when {
            !wide -> (room * 1.02f).coerceIn(310f, 390f)
            else -> (room * .58f).coerceIn(390f, 470f)
        }
        val sidebar = wide && room >= 1000f && font <= 1.3f
        val sideWidth = 292f
        boxes[Slot.HERO] = Box(left, heroTop, if (sidebar) right - sideWidth - gap else right, heroTop + heroHeight)
        if (sidebar) {
            val middle = heroTop + (heroHeight - gap) / 2f
            boxes[Slot.SYSTEM_STATUS] = Box(right - sideWidth, heroTop, right, middle)
            boxes[Slot.SYSTEM_ACTIVITY] = Box(right - sideWidth, middle + gap, right, heroTop + heroHeight)
        }
        val actions = listOf(Slot.LABEL_THINKING, Slot.LABEL_ANALYSING, Slot.LABEL_LEARNING,
            Slot.LABEL_DEVELOPING, Slot.LABEL_CONNECTED, Slot.LABEL_EVOLVING)
        val actionColumns = when {
            room < 420f && font > 1.3f -> 1
            room >= 1000f && font <= 1.3f -> 6
            room >= 530f && font <= 1.6f -> 3
            else -> 2
        }
        val actionWidth = (room - (actionColumns - 1) * gap) / actionColumns
        val actionHeight = 104f + (font - 1f).coerceAtLeast(0f) * 64f
        val actionTop = heroTop + heroHeight + gap
        actions.forEachIndexed { index, slot ->
            val x = left + (index % actionColumns) * (actionWidth + gap)
            val y = actionTop + (index / actionColumns) * (actionHeight + gap)
            boxes[slot] = Box(x, y, x + actionWidth, y + actionHeight)
        }
        val cards = mutableListOf(Slot.MODEL, Slot.LEARNING, Slot.STORAGE, Slot.DEVELOPMENT, Slot.WORKERS)
        if (!sidebar) cards += listOf(Slot.SYSTEM_STATUS, Slot.SYSTEM_ACTIVITY)
        val columns = when {
            room >= 1000f && font <= 1.3f -> 3
            room >= 530f && room >= 420f * font -> 2
            else -> 1
        }
        val cardWidth = (room - (columns - 1) * gap) / columns
        val cardHeight = 232f + (font - 1f).coerceAtLeast(0f) * 160f
        val cardTop = actionTop + ((actions.size + actionColumns - 1) / actionColumns) * (actionHeight + gap) + 4f
        cards.forEachIndexed { index, slot ->
            val x = left + (index % columns) * (cardWidth + gap)
            val y = cardTop + (index / columns) * (cardHeight + gap)
            boxes[slot] = Box(x, y, x + cardWidth, y + cardHeight)
        }
        val contentHeight = cardTop + ((cards.size + columns - 1) / columns) * (cardHeight + gap)
        if (rail > 0f) boxes[Slot.RAIL] = Box(0f, 0f, rail, contentHeight)
        if (!wide) boxes[Slot.NAV] = Box(0f, contentHeight, width, contentHeight + PHONE_NAV_HEIGHT)
        return Layout(wide, width, 1f, contentHeight, boxes)
    }
}
