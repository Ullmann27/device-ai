package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box

/** Packed Fold/tablet composition: one viewport without scrolling. Phone/large-text may scroll via reflow. */
object JarvisCockpitGrid {
    enum class Area {
        HEADER, DEVICE_STATUS, BRAND, MODE_PILL, PRIVACY_PILL, ICONS,
        RAIL, USER_CARD, HEAD, ORB, DIALOG, INPUT, MODULES, TILES,
        ROW_AGENTS, ROW_TASKS, ROW_MEMORY, ROW_MODEL, STATUS,
        TAGLINE, CONTINUUM_HEADER, CAMERA_STATUS,
        WORK_DOCUMENT, WORK_PROCESS, WORK_DRAFT, TOOL_DOCK
    }

    val overlays: Set<Pair<Area, Area>> = setOf(
        Area.HEADER to Area.DEVICE_STATUS, Area.HEADER to Area.BRAND,
        Area.HEADER to Area.MODE_PILL, Area.HEADER to Area.PRIVACY_PILL,
        Area.HEADER to Area.ICONS, Area.HEADER to Area.TAGLINE
    )

    data class Grid(val width: Float, val height: Float, val unit: Float, val areas: Map<Area, Box>) {
        operator fun get(a: Area): Box = areas.getValue(a)
    }

    const val REF_W = 1536f
    const val REF_H = 1024f
    const val MIN_TOUCH_DP = 48f
    const val MIN_DIALOG_DP = 56f
    const val MIN_TILE_DP = 158f
    const val TILE_COLUMNS = 5
    const val TILE_ROWS = 2
    const val TILE_GAP_DP = 12f
    const val TILE_ASPECT = .74f
    const val HERO_HEIGHT_RATIO = .72f
    const val MAX_HERO_DP = 640f
    const val HEAD_ANCHOR_X = .50f
    const val HEAD_CLOSE_UP = .92f
    const val PACKED_HERO_MAX_DP = 280f
    const val PACKED_HERO_MIN_DP = 160f
    const val PACKED_WORK_MIN_DP = 132f

    fun applies(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
        widthDp.isFinite() && heightDp.isFinite() && widthDp > 0f && heightDp.isFinite() && heightDp > 0f &&
            JarvisWindowProfile.resolve(widthDp, heightDp, fontScale).referenceComposition

    fun unit(widthDp: Float, heightDp: Float): Float = minOf(widthDp / REF_W, heightDp / REF_H)

    fun create(widthDp: Float, heightDp: Float, fontScale: Float = 1f): Grid {
        require(widthDp.isFinite() && widthDp >= 600f && heightDp.isFinite() && heightDp >= 480f)
        require(fontScale.isFinite() && fontScale > 0f)
        val profile = JarvisWindowProfile.resolve(widthDp, heightDp, fontScale)
        val font = maxOf(1f, profile.fontScale)
        val margin = 12f
        val gap = 8f
        val left = margin
        val right = widthDp - margin
        val room = right - left
        val a = linkedMapOf<Area, Box>()

        val headerH = 52f * font
        val headerBottom = 8f + headerH
        a[Area.HEADER] = Box(margin, 8f, right, headerBottom)
        val iconsW = if (room >= 680f) 96f else 72f
        val iconsLeft = right - iconsW
        val privacyW = (widthDp * .17f).coerceIn(108f, 168f)
        val privacyRight = iconsLeft - gap
        val privacyLeft = privacyRight - privacyW
        val modeW = if (room >= 680f) 104f else 88f
        val modeRight = privacyLeft - gap
        val modeLeft = modeRight - modeW
        val clockW = if (room >= 640f) 84f else 72f
        val clockRight = left + clockW
        val brandLeft = clockRight + 6f
        val brandRight = minOf(modeLeft - 8f, brandLeft + (widthDp * .24f).coerceIn(148f, 220f))
            .coerceAtLeast(brandLeft + 72f)
        a[Area.DEVICE_STATUS] = Box(left, 8f, clockRight, headerBottom)
        a[Area.BRAND] = Box(brandLeft, 8f, brandRight, headerBottom)
        a[Area.MODE_PILL] = Box(modeLeft, 8f, modeRight, headerBottom)
        a[Area.PRIVACY_PILL] = Box(privacyLeft, 8f, privacyRight, headerBottom)
        a[Area.ICONS] = Box(iconsLeft, 8f, right, headerBottom)
        if (modeLeft - brandRight >= 140f)
            a[Area.TAGLINE] = Box(brandRight + gap, 14f, modeLeft - gap, headerBottom - 6f)

        val missionH = 56f * font
        val dockH = JarvisShellLayout.TOOL_DOCK_HEIGHT_DP * font
        val statusH = 48f * font
        val bottomMargin = 10f
        val yAfterHeader = headerBottom + gap
        val reservedBottom = dockH + gap + statusH + bottomMargin
        val available = (heightDp - yAfterHeader - missionH - gap - reservedBottom).coerceAtLeast(280f)
        val heroMax = PACKED_HERO_MAX_DP * font
        val heroMin = PACKED_HERO_MIN_DP * font
        val workMin = PACKED_WORK_MIN_DP * font
        var heroH = (available * .46f).coerceIn(heroMin, heroMax)
        var workH = available - heroH - gap
        if (workH < workMin) {
            heroH = (available - workMin - gap).coerceIn(140f * font, heroMax)
            workH = (available - heroH - gap).coerceAtLeast(120f * font)
        }

        val heroTop = yAfterHeader
        val heroBottom = heroTop + heroH
        val liveW = (room * .19f).coerceIn(120f, 188f)
        val rightW = (room * .20f).coerceIn(128f, 200f)
        val mid = (room - liveW - rightW - 2f * gap).coerceAtLeast(200f)
        val headW = mid * .54f
        val liveRight = left + liveW
        val headLeft = liveRight + gap
        val headRight = headLeft + headW
        val orbLeft = headRight + gap
        val orbRight = orbLeft + (mid - headW - gap)
        val modulesLeft = orbRight + gap

        a[Area.DIALOG] = Box(left, heroTop, liveRight, heroBottom)
        a[Area.HEAD] = Box(headLeft, heroTop, headRight, heroBottom)
        val continuumH = minOf(56f * font, heroH * .28f).coerceAtLeast(36f * font)
        a[Area.CONTINUUM_HEADER] = Box(orbLeft, heroTop, orbRight, heroTop + continuumH)
        a[Area.ORB] = Box(orbLeft, heroTop + continuumH + 4f, orbRight, heroBottom)
        val cameraH = minOf(56f * font, heroH * .28f).coerceAtLeast(40f * font)
        a[Area.CAMERA_STATUS] = Box(modulesLeft, heroTop, right, heroTop + cameraH)
        a[Area.MODULES] = Box(modulesLeft, heroTop + cameraH + gap, right, heroBottom)

        var y = heroBottom + gap
        a[Area.INPUT] = Box(left, y, right, y + missionH)
        y += missionH + gap
        val workW = (room - 2f * gap) / 3f
        a[Area.WORK_DOCUMENT] = Box(left, y, left + workW, y + workH)
        a[Area.WORK_PROCESS] = Box(left + workW + gap, y, left + 2f * workW + gap, y + workH)
        a[Area.WORK_DRAFT] = Box(left + 2f * (workW + gap), y, right, y + workH)
        y += workH + gap
        a[Area.TOOL_DOCK] = Box(left, y, right, y + dockH)
        y += dockH + gap
        a[Area.STATUS] = Box(margin, y, right, y + statusH)

        return Grid(widthDp, heightDp, unit(widthDp, heightDp), a)
    }
}
