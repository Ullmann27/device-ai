package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box

/** Reference composition; readable controls scroll. */
object JarvisCockpitGrid {
    enum class Area {
        HEADER, DEVICE_STATUS, BRAND, MODE_PILL, PRIVACY_PILL, ICONS,
        RAIL, USER_CARD, HEAD, ORB, DIALOG, INPUT, MODULES, TILES,
        ROW_AGENTS, ROW_TASKS, ROW_MEMORY, ROW_MODEL, STATUS,
        TAGLINE, CONTINUUM_HEADER, CAMERA_STATUS
    }

    val overlays: Set<Pair<Area, Area>> = setOf(
        Area.HEAD to Area.DIALOG,
        Area.HEADER to Area.DEVICE_STATUS, Area.HEADER to Area.BRAND,
        Area.HEADER to Area.MODE_PILL, Area.HEADER to Area.PRIVACY_PILL,
        Area.HEADER to Area.ICONS, Area.HEADER to Area.TAGLINE
    )

    data class Grid(val width: Float, val height: Float, val unit: Float, val areas: Map<Area, Box>) {
        operator fun get(a: Area): Box = areas.getValue(a)
    }

    const val REF_W = 1536f
    const val REF_H = 1024f
    const val TILE_COLUMNS = 5
    const val TILE_ROWS = 2
    const val MIN_TOUCH_DP = 48f
    const val MIN_DIALOG_DP = 56f
    const val MIN_TILE_DP = 158f
    const val TILE_GAP_DP = 8f
    const val HEAD_ANCHOR_X = .50f
    const val HEAD_CLOSE_UP = .92f

    fun applies(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
        widthDp.isFinite() && heightDp.isFinite() && widthDp > 0f && heightDp > 0f &&
            JarvisWindowProfile.resolve(widthDp, heightDp, fontScale).referenceComposition

    fun unit(widthDp: Float, heightDp: Float): Float = minOf(widthDp / REF_W, heightDp / REF_H)

    fun create(widthDp: Float, heightDp: Float, fontScale: Float = 1f): Grid {
        require(widthDp.isFinite() && widthDp >= 600f && heightDp.isFinite() && heightDp >= 480f)
        require(fontScale.isFinite() && fontScale > 0f)
        val profile = JarvisWindowProfile.resolve(widthDp, heightDp, fontScale)
        val font = maxOf(1f, profile.fontScale)
        val margin = 12f
        val gap = 12f
        val left = profile.railWidthDp + gap
        val right = widthDp - margin
        val room = right - left
        val a = linkedMapOf<Area, Box>()

        val headerBottom = 8f + 64f * font
        a[Area.HEADER] = Box(margin, 8f, right, headerBottom)
        val iconsLeft = right - 96f
        val privacyRight = iconsLeft - gap
        val privacyLeft = privacyRight - (widthDp * .19f).coerceIn(136f, 176f)
        val modeRight = privacyLeft - gap
        val modeLeft = modeRight - 112f
        val brandRight = minOf(modeLeft - gap, margin + (widthDp * .28f).coerceIn(180f, 264f))
        a[Area.BRAND] = Box(margin + 6f, 8f, brandRight, headerBottom)
        a[Area.MODE_PILL] = Box(modeLeft, 8f, modeRight, headerBottom)
        a[Area.PRIVACY_PILL] = Box(privacyLeft, 8f, privacyRight, headerBottom)
        a[Area.ICONS] = Box(iconsLeft, 8f, right, headerBottom)
        if (modeLeft - brandRight >= 164f)
            a[Area.TAGLINE] = Box(brandRight + gap, 14f, modeLeft - gap, headerBottom - 6f)

        val heroTop = headerBottom + gap
        val tileWidth = (room - (TILE_COLUMNS - 1) * TILE_GAP_DP) / TILE_COLUMNS
        val tileHeight = maxOf(MIN_TILE_DP, minOf(176f, tileWidth * 1.30f)) * font
        val tilesHeight = TILE_ROWS * tileHeight + TILE_GAP_DP
        val inputHeight = 56f * font
        val statusHeight = 56f * font
        val fixedHeight = heroTop + inputHeight + tilesHeight + statusHeight + 4f * gap + margin
        val heroHeight = (heightDp - fixedHeight).coerceIn(260f, 480f)
        val heroBottom = heroTop + heroHeight
        val split = left + room * .54f
        a[Area.HEAD] = Box(left, heroTop, split - gap / 2f, heroBottom)
        a[Area.CONTINUUM_HEADER] = Box(split + gap / 2f, heroTop, right, heroTop + 70f * font)
        a[Area.ORB] = Box(split + gap / 2f, heroTop + 70f * font + 4f, right, heroBottom)
        val badgeWidth = minOf(224f * font, a.getValue(Area.HEAD).width - 12f)
        a[Area.DIALOG] = Box(left + 6f, heroBottom - MIN_DIALOG_DP * font - 6f,
            left + 6f + badgeWidth, heroBottom - 6f)

        var y = heroBottom + gap
        a[Area.INPUT] = Box(left, y, right, y + inputHeight)
        y += inputHeight + gap
        a[Area.TILES] = Box(left, y, right, y + tilesHeight)
        y += tilesHeight + gap
        a[Area.STATUS] = Box(margin, y, right, y + statusHeight)
        a[Area.RAIL] = Box(margin, heroTop, profile.railWidthDp, y - gap)
        y += statusHeight + 24f

        val informationColumns = profile.informationColumns
        val infoWidth = (room - (informationColumns - 1) * gap) / informationColumns
        val infoHeight = 164f * font
        listOf(Area.ROW_AGENTS, Area.ROW_TASKS, Area.ROW_MEMORY, Area.ROW_MODEL).forEachIndexed { i, area ->
            val x = left + (i % informationColumns) * (infoWidth + gap)
            val top = y + (i / informationColumns) * (infoHeight + gap)
            a[area] = Box(x, top, x + infoWidth, top + infoHeight)
        }
        y += ((4 + informationColumns - 1) / informationColumns) * (infoHeight + gap)
        val detailWidth = (room - gap) / 2f
        a[Area.CAMERA_STATUS] = Box(left, y, left + detailWidth, y + 56f * font)
        a[Area.MODULES] = Box(left + detailWidth + gap, y, right, y + 164f * font)
        val contentHeight = maxOf(heightDp, a.getValue(Area.MODULES).bottom + margin)
        return Grid(widthDp, contentHeight, unit(widthDp, heightDp), a)
    }
}
