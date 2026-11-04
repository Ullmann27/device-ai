package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumHomeLayout.Box
import at.heinz.deviceai.ui.ContinuumHomeLayout.Layout
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot

/**
 * The owner's November references: an unobstructed avatar/core stage, a conversation entrance,
 * glass action cards and actual device/work records. Typography and touch targets stay in dp.
 * Long pages scroll; fitting a reference image never makes a control too small to use.
 */
object ContinuumReferenceLayout {
    val actions = listOf(
        Slot.LABEL_THINKING, Slot.ACTION_AGENTS, Slot.ACTION_BOTS, Slot.ACTION_TASK,
        Slot.ACTION_MEMORY, Slot.LABEL_ANALYSING, Slot.LABEL_DEVELOPING,
        Slot.LABEL_LEARNING, Slot.ACTION_DEVICE, Slot.LABEL_CONNECTED
    )

    fun create(width: Float, height: Float, wide: Boolean, fontScale: Float, compact: Boolean = false): Layout {
        require(width.isFinite() && width > 0f && height.isFinite() && height >= 0f)
        require(fontScale.isFinite() && fontScale > 0f)
        val font = fontScale.coerceIn(.85f, 2f)
        val largeType = font > 1.3f
        val portrait = wide && height > 0f && ContinuumHomeLayout.isPortraitFold(width, height)
        val rail = if (wide) ContinuumHomeLayout.railWidthDp(width) else 0f
        val margin = if (wide) 16f else 12f
        val gap = if (wide) 12f else 10f
        val left = rail + margin
        val right = width - margin
        val room = (right - left).coerceAtLeast(1f)
        val boxes = linkedMapOf<Slot, Box>()

        val headerRows = room < 320f || (font > 1.1f && room < 420f)
        // Whole-dp header height: fractional font growth (e.g. 70.4 dp) made the 48 dp header
        // targets 47.999992 dp in float space. Snapping up keeps every target exactly 48 dp.
        val brandHeight = kotlin.math.ceil(56f + (font - 1f).coerceAtLeast(0f) * 24f)
        val headerBottom = if (headerRows) brandHeight + 62f else maxOf(72f, brandHeight + 8f)
        boxes[Slot.BRAND] = Box(left, 6f, if (headerRows) right else right - 168f, brandHeight + 6f)
        listOf(Slot.SEARCH, Slot.NOTIFICATIONS, Slot.PROFILE).forEachIndexed { index, slot ->
            val x = right - 160f + index * 56f
            val y = if (headerRows) brandHeight + 12f else (headerBottom - 48f) / 2f
            boxes[slot] = Box(x, y, x + 48f, y + 48f)
        }

        val selectorColumns = if (font > 1.3f && room < 420f) 1 else 3
        val selectorHeight = 48f + (font - 1f).coerceAtLeast(0f) * 16f
        val selectorWidth = (room - (selectorColumns - 1) * gap) / selectorColumns
        listOf(Slot.VIEW_AVATAR, Slot.VIEW_ORB, Slot.VIEW_COMPACT).forEachIndexed { index, slot ->
            val x = left + index % selectorColumns * (selectorWidth + gap)
            val y = headerBottom + 4f + index / selectorColumns * (selectorHeight + gap)
            boxes[slot] = Box(x, y, x + selectorWidth, y + selectorHeight)
        }
        val heroTop = headerBottom + 4f + ((3 + selectorColumns - 1) / selectorColumns) * (selectorHeight + gap)
        val sidebar = !compact && wide && !portrait && room >= 680f && !largeType
        val sideWidth = (room * .31f).coerceIn(236f, 320f)
        val heroHeight = when {
            compact -> 112f
            !wide -> (room * .79f).coerceIn(250f, 338f)
            portrait -> (room * .54f).coerceIn(338f, 430f)
            else -> (room * .44f).coerceIn(304f, 382f)
        }
        val heroRight = if (sidebar) right - sideWidth - gap else right
        boxes[Slot.HERO] = Box(left, heroTop, heroRight, heroTop + heroHeight)
        if (sidebar) {
            val middle = heroTop + (heroHeight - gap) * .54f
            boxes[Slot.SYSTEM_STATUS] = Box(heroRight + gap, heroTop, right, middle)
            boxes[Slot.WORKERS] = Box(heroRight + gap, middle + gap, right, heroTop + heroHeight)
        }

        val composerTop = heroTop + heroHeight + gap
        val composerHeight = 56f + (font - 1f).coerceAtLeast(0f) * 22f
        boxes[Slot.COMPOSER] = Box(left, composerTop, right, composerTop + composerHeight)
        val actionTop = composerTop + composerHeight + gap
        val actionColumns = when {
            room < 320f || largeType && room < 640f -> 1
            // Ten equal actions: 5 columns give two complete rows aligned with the composer.
            room >= 640f && font <= 1.3f -> 5
            else -> 2
        }
        val actionWidth = (room - (actionColumns - 1) * gap) / actionColumns
        val actionHeight = 72f + (font - 1f).coerceAtLeast(0f) * 64f
        actions.forEachIndexed { index, slot ->
            val x = left + index % actionColumns * (actionWidth + gap)
            val y = actionTop + index / actionColumns * (actionHeight + gap)
            boxes[slot] = Box(x, y, x + actionWidth, y + actionHeight)
        }
        val actionRows = (actions.size + actionColumns - 1) / actionColumns
        var top = actionTop + actionRows * (actionHeight + gap) + 6f

        // A phone/portrait cockpit puts its device summary before the detailed records.
        if (!sidebar) {
            val statusHeight = if (largeType) 228f + (font - 1.3f) * 110f else 164f
            boxes[Slot.SYSTEM_STATUS] = Box(left, top, right, top + statusHeight)
            top += statusHeight + gap
        }

        val metrics = mutableListOf(Slot.MODEL, Slot.STORAGE, Slot.LEARNING, Slot.DEVELOPMENT)
        if (!sidebar) metrics += Slot.WORKERS
        metrics += Slot.SYSTEM_ACTIVITY
        val cardColumns = when {
            largeType -> if (room >= 840f) 2 else 1
            room >= 1060f -> 4
            room >= 640f -> 3
            room >= 360f && font <= 1.1f -> 2
            else -> 1
        }
        val cardWidth = (room - (cardColumns - 1) * gap) / cardColumns
        val cardHeight = 204f + (font - 1f).coerceAtLeast(0f) * 164f
        metrics.forEachIndexed { index, slot ->
            // Expand the last incomplete row to keep all card edges aligned with the stage.
            val row = index / cardColumns
            val firstInRow = row * cardColumns
            val count = minOf(cardColumns, metrics.size - firstInRow)
            val rowWidth = if (count == cardColumns) cardWidth else (room - (count - 1) * gap) / count
            val x = left + index % cardColumns * (rowWidth + gap)
            val y = top + row * (cardHeight + gap)
            boxes[slot] = Box(x, y, x + rowWidth, y + cardHeight)
        }
        top += ((metrics.size + cardColumns - 1) / cardColumns) * (cardHeight + gap)
        val learningHeight = 64f + (font - 1f).coerceAtLeast(0f) * 46f
        boxes[Slot.LABEL_EVOLVING] = Box(left, top, right, top + learningHeight)
        val contentHeight = maxOf(top + learningHeight + 16f, height)
        if (wide) boxes[Slot.RAIL] = Box(0f, 0f, rail, contentHeight)
        else boxes[Slot.NAV] = Box(0f, contentHeight, width, contentHeight + ContinuumHomeLayout.PHONE_NAV_HEIGHT)
        return Layout(wide, width, 1f, contentHeight, boxes,
            adaptive = largeType || width in 240f..<320f,
            referenceCockpit = true, portrait = portrait)
    }
}
