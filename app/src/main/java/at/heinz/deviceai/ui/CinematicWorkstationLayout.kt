package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot

/** One scene, readable native controls. */
object CinematicWorkstationLayout {
    const val AVATAR_OPACITY = 1f
    const val AVATAR_SCALE = .76f
    const val MONITOR_YAW_DEGREES = -3f

    fun create(width: Float, viewport: Float, fontScale: Float = 1f): JarvisShellLayout.Shell {
        require(width.isFinite() && width >= 240f && viewport.isFinite() && viewport >= 160f)
        val profile = JarvisWindowProfile.resolve(width, viewport, fontScale)
        val font = maxOf(1f, profile.fontScale)
        val wide = width >= 600f
        val margin = if (wide) 16f else 12f
        val gap = 8f
        val right = width - margin
        val room = right - margin
        val headerH = 52f * font
        val inputH = 56f * font
        val dockH = JarvisTiles.bottomNavHeight(room, font)
        val statusH = 48f * font
        val dialogH = if (wide) 0f else 48f * font
        val reserved = headerH + inputH + dockH + statusH + dialogH + 6f * gap
        val sceneH = maxOf(if (wide) 310f else 320f, viewport - reserved)
        val sceneTop = gap + headerH + gap + dialogH
        val scene = Box(margin, sceneTop, right, sceneTop + sceneH)
        val headW = minOf(room * if (wide) .82f else .98f, sceneH * 1.65f)
        val headH = sceneH * .70f
        val headTop = sceneTop + sceneH * .055f
        val head = Box(width * .5f - headW * .5f, headTop,
            width * .5f + headW * .5f, headTop + headH)
        // Original rings surround solid skin.
        val orbSize = minOf(room * .98f, sceneH * .80f)
        val orb = Box(width * .5f - orbSize * .5f, sceneTop,
            width * .5f + orbSize * .5f, sceneTop + orbSize)
        fun roomBox(left: Float, top: Float, right: Float, bottom: Float) =
            Box(margin + room * left, sceneTop + sceneH * top,
                margin + room * right, sceneTop + sceneH * bottom)
        val monitor = if (wide) roomBox(.25f, .54f, .75f, .89f)
            else roomBox(.02f, .54f, .98f, .89f)
        val boxes = linkedMapOf(
            Slot.TOP_BAR to Box(margin, gap, right, gap + headerH),
            Slot.AVATAR to scene,
            Slot.WORK_PROCESS to monitor
        )
        if (wide) {
            val sideW = room * .20f
            boxes[Slot.DIALOG] = Box(margin, sceneTop + 12f, margin + sideW, sceneTop + 112f * font)
            boxes[Slot.CAMERA_STATUS] = Box(right - sideW, sceneTop + 12f, right, sceneTop + 60f * font)
            boxes[Slot.COCKPIT_SECONDARY] = Box(right - sideW, sceneTop + 68f * font, right, monitor.top - gap)
            boxes[Slot.WORK_DOCUMENT] = roomBox(.02f, .57f, .22f, .87f)
            boxes[Slot.WORK_DRAFT] = roomBox(.78f, .57f, .98f, .87f)
        } else {
            boxes[Slot.DIALOG] = Box(margin, gap + headerH + gap, right, sceneTop)
        }
        var y = scene.bottom + gap
        boxes[Slot.INPUT] = Box(margin, y, right, y + inputH)
        y += inputH + gap
        boxes[Slot.BOTTOM_NAV] = Box(margin, y, right, y + dockH)
        y += dockH + gap
        boxes[Slot.STATUS] = Box(margin, y, right, y + statusH)
        y += statusH + gap
        val grid = if (wide) JarvisCockpitGrid.create(width, maxOf(480f, viewport), profile.fontScale)
            .let { original ->
                original.copy(areas = original.areas + mapOf(
                    JarvisCockpitGrid.Area.HEADER to boxes.getValue(Slot.TOP_BAR),
                    JarvisCockpitGrid.Area.HEAD to head,
                    JarvisCockpitGrid.Area.ORB to orb,
                    JarvisCockpitGrid.Area.WORK_PROCESS to monitor
                ))
            } else null
        return JarvisShellLayout.Shell(JarvisShellLayout.formFactor(width, viewport), width,
            maxOf(viewport, y), boxes.filterValues { it.width > 0f && it.height > 0f }, 0, 0,
            overlays = setOf(Slot.AVATAR to Slot.WORK_PROCESS), grid = grid, viewportHeight = viewport,
            profile = profile, headBounds = head, orbBounds = orb, headStage = head, workstation = true,
            protectedFaceBounds = Box(width * .5f - headH * .29f, head.top,
                width * .5f + headH * .29f, head.top + headH * .64f))
    }
}
