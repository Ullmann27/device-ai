package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot

/** Command workstation using the original head, orb and native work surfaces. */
object CinematicWorkstationLayout {
    const val AVATAR_OPACITY = 1f
    const val AVATAR_SCALE = .90f
    const val MONITOR_YAW_DEGREES = -3f
    // The real composer takes priority in exceptionally short IME windows; ordinary chat uses 96–260 dp.
    const val MIN_CONVERSATION_PRESENCE_DP = 24f
    const val MAX_CONVERSATION_PRESENCE_DP = 320f

    /** Shared by the Home scene and the existing conversation's native chat list. */
    fun chatRailWidth(width: Float, fontScale: Float = 1f): Float {
        require(width.isFinite() && width > 0f)
        if (width < 600f) return 0f
        val font = if (fontScale.isFinite()) fontScale.coerceIn(1f, 2f) else 1f
        return 160f + (font - 1f) * 20f
    }

    /**
     * A temporary viewport onto the same mounted Filament scene while the real conversation is visible.
     * The complete original head and rest rings fit the upper strip. The existing monitor keeps a valid
     * frame below that strip, so changing presentation does not detach the stage or discard its feed.
     * HomeView owns clipping and suppresses offscreen native controls in this presentation.
     */
    fun conversationPresence(width: Float, visibleHeight: Float,
                             fontScale: Float = 1f): JarvisShellLayout.Shell {
        require(width.isFinite() && width >= 240f)
        require(visibleHeight.isFinite() &&
            visibleHeight in MIN_CONVERSATION_PRESENCE_DP..MAX_CONVERSATION_PRESENCE_DP)
        val room = width - 24f
        val center = width * .5f
        val headH = minOf(visibleHeight * .62f, room * .78f)
        val headW = minOf(room * .98f, headH * 1.36f)
        val headTop = visibleHeight * .26f
        val head = Box(center - headW * .5f, headTop, center + headW * .5f, headTop + headH)
        val orbSize = minOf(room * .59f, headH * .90f)
        val orbCenterY = headTop + headH * .36f
        val orb = Box(center - orbSize * .5f, orbCenterY - orbSize * .5f,
            center + orbSize * .5f, orbCenterY + orbSize * .5f)
        val monitorTop = visibleHeight + 12f
        val monitor = Box(12f + room * .08f, monitorTop, width - 12f - room * .08f,
            monitorTop + maxOf(144f, visibleHeight * .70f))
        val scene = Box(0f, 0f, width, monitor.bottom + 12f)
        return JarvisShellLayout.Shell(JarvisShellLayout.formFactor(width, visibleHeight),
            width, scene.height, linkedMapOf(Slot.AVATAR to scene, Slot.WORK_PROCESS to monitor), 0, 0,
            overlays = setOf(Slot.AVATAR to Slot.WORK_PROCESS), viewportHeight = visibleHeight,
            profile = JarvisWindowProfile.resolve(width, visibleHeight, fontScale),
            headBounds = head, orbBounds = orb, headStage = head, workstation = true,
            protectedFaceBounds = Box(center - headH * .37f, head.top,
                center + headH * .37f, head.top + headH * .82f))
    }

    fun create(width: Float, viewport: Float, fontScale: Float = 1f,
               compactPresentation: Boolean = false): JarvisShellLayout.Shell {
        require(width.isFinite() && width >= 240f && viewport.isFinite() && viewport >= 160f)
        val profile = JarvisWindowProfile.resolve(width, viewport, fontScale)
        val font = maxOf(1f, profile.fontScale)
        val railW = chatRailWidth(width, font)
        val wide = railW > 0f
        val margin = if (wide) 16f else 12f
        val gap = 8f
        val left = margin + if (wide) railW + gap else 0f
        val right = width - margin
        val room = right - left
        val center = (left + right) * .5f
        // Use the available stage width, rather than the Fold's full window width.
        // At 600 dp a real chat list leaves too little room for three readable columns.
        val flanking = wide && room >= 600f
        val sideW = if (flanking) (room * .205f).coerceIn(120f, 196f)
            else (room - 112f - 2f * gap) * .5f
        val headerH = 52f * font
        val statusH = 48f * font
        val dockH = JarvisTiles.bottomNavHeight(room, font)
        val boxes = linkedMapOf<Slot, Box>()
        boxes[Slot.TOP_BAR] = Box(left, gap, right, gap + headerH)
        var y = boxes.getValue(Slot.TOP_BAR).bottom + gap
        // Keep shared dock geometry and hit targets; only its slot moves above the scene.
        boxes[Slot.BOTTOM_NAV] = Box(left, y, right, y + dockH)
        y += dockH + gap

        fun badges(top: Float, badgeW: Float) {
            boxes[Slot.DIALOG] = Box(left, top, left + badgeW, top + 74f * font)
            boxes[Slot.CAMERA_STATUS] = Box(right - badgeW, top, right, top + 48f * font)
            boxes[Slot.COCKPIT_SECONDARY] = Box(right - badgeW, top + 48f * font + gap,
                right, top + 96f * font + gap)
        }
        if (!wide) {
            boxes[Slot.DIALOG] = Box(left, y, right, y + 48f * font)
            y += 48f * font + gap
        } else if (!flanking && !compactPresentation) {
            badges(y, (room - gap) * .5f)
            y += 96f * font + 2f * gap
        }

        val sceneTop = y
        val scene: Box
        val head: Box?
        val orb: Box
        if (compactPresentation) {
            val compactH = if (wide) maxOf(112f, 24f + 96f * font + gap) else 112f
            scene = Box(left, sceneTop, right, sceneTop + compactH)
            head = null
            orb = Box(center - 56f, scene.centerY - 56f, center + 56f, scene.centerY + 56f)
            boxes[Slot.AVATAR] = orb
            if (wide) badges(sceneTop + 12f, sideW)
        } else {
            // A uniform model transform uses this portrait window. Width never stretches the GLB.
            val headH = if (flanking) (room * .43f).coerceIn(260f, 330f)
                else minOf(286f, room * .77f)
            val headW = minOf(room * .98f, headH * 1.36f)
            val headTop = sceneTop + 28f + headH * .26f
            head = Box(center - headW * .5f, headTop, center + headW * .5f, headTop + headH)
            // The authored orb's outer rings extend beyond its nominal sphere bounds.
            // Reserve real material thickness above the crown and beside the shoulders.
            // Keep the actual containment shell around the complete posed face on narrow screens,
            // while its authored outer rings retain a visible margin inside the scene.
            val orbSize = minOf(room * .635f, headH * .86f)
            val orbCenterY = headTop + headH * .38f
            orb = Box(center - orbSize * .5f, orbCenterY - orbSize * .5f,
                center + orbSize * .5f, orbCenterY + orbSize * .5f)

            val monitorLeft: Float
            val monitorRight: Float
            val monitorTop: Float
            if (flanking) {
                badges(sceneTop + 12f, sideW)
                monitorLeft = left + sideW + 18f
                monitorRight = right - sideW - 18f
                monitorTop = maxOf(head.bottom - headH * .18f + 22f,
                    boxes.getValue(Slot.COCKPIT_SECONDARY).bottom + gap)
            } else {
                val instrumentsTop = head.bottom + gap
                val paired = room >= 2f * 120f * font + gap
                val instrumentW = if (paired) (room - gap) * .5f else room
                val documentBottom = instrumentsTop + 208f * font
                boxes[Slot.WORK_DOCUMENT] = Box(left, instrumentsTop, left + instrumentW, documentBottom)
                val draftTop = if (paired) instrumentsTop else documentBottom + gap
                val instrumentsBottom = draftTop + 208f * font
                boxes[Slot.WORK_DRAFT] = Box(right - instrumentW, draftTop, right, instrumentsBottom)
                // The focus flight moves the original monitor towards the camera. A proportional
                // inset also contains that projected width on a small unfolded display.
                val monitorInset = maxOf(8f, room * .025f)
                monitorLeft = left + monitorInset
                monitorRight = right - monitorInset
                monitorTop = instrumentsBottom + 20f
            }
            // The existing Android composer is a fixed sibling of the scrollable stage.
            val afterScene = statusH + 2f * gap
            val projectionBottomInset = 32f
            val monitorH = maxOf(220f * font, viewport - monitorTop - projectionBottomInset - afterScene)
            val monitor = Box(monitorLeft, monitorTop, monitorRight, monitorTop + monitorH)
            boxes[Slot.WORK_PROCESS] = monitor
            if (flanking) {
                val instrumentsTop = boxes.getValue(Slot.COCKPIT_SECONDARY).bottom + gap
                val instrumentsBottom = maxOf(monitor.bottom, instrumentsTop + 208f * font)
                boxes[Slot.WORK_DOCUMENT] = Box(left, instrumentsTop, left + sideW, instrumentsBottom)
                boxes[Slot.WORK_DRAFT] = Box(right - sideW, instrumentsTop, right, instrumentsBottom)
            }
            val sceneBottom = maxOf(monitor.bottom, boxes[Slot.WORK_DOCUMENT]?.bottom ?: 0f) + projectionBottomInset
            scene = Box(left, sceneTop, right, sceneBottom)
            boxes[Slot.AVATAR] = scene
        }

        y = scene.bottom + gap
        boxes[Slot.STATUS] = Box(left, y, right, y + statusH)
        y += statusH + gap
        val contentHeight = if (compactPresentation) y else maxOf(viewport, y)
        if (wide) boxes[Slot.NAV_RAIL] = Box(margin, gap, margin + railW, contentHeight - gap)

        // Use actual slots. A copied legacy grid would leave invisible BRAND/MODE hit targets
        // across the new chat list and tool dock.
        val grid = if (wide && head != null) {
            val areas = linkedMapOf(
                JarvisCockpitGrid.Area.HEADER to boxes.getValue(Slot.TOP_BAR),
                JarvisCockpitGrid.Area.RAIL to boxes.getValue(Slot.NAV_RAIL),
                JarvisCockpitGrid.Area.HEAD to head,
                JarvisCockpitGrid.Area.ORB to orb,
                JarvisCockpitGrid.Area.DIALOG to boxes.getValue(Slot.DIALOG),
                JarvisCockpitGrid.Area.CAMERA_STATUS to boxes.getValue(Slot.CAMERA_STATUS),
                JarvisCockpitGrid.Area.MODULES to boxes.getValue(Slot.COCKPIT_SECONDARY),
                JarvisCockpitGrid.Area.WORK_DOCUMENT to boxes.getValue(Slot.WORK_DOCUMENT),
                JarvisCockpitGrid.Area.WORK_PROCESS to boxes.getValue(Slot.WORK_PROCESS),
                JarvisCockpitGrid.Area.WORK_DRAFT to boxes.getValue(Slot.WORK_DRAFT),
                JarvisCockpitGrid.Area.TOOL_DOCK to boxes.getValue(Slot.BOTTOM_NAV),
                JarvisCockpitGrid.Area.STATUS to boxes.getValue(Slot.STATUS))
            JarvisCockpitGrid.Grid(width, contentHeight, JarvisCockpitGrid.unit(width, viewport), areas)
        } else null
        val sceneOverlays = if (compactPresentation) emptySet() else
            listOf(Slot.DIALOG, Slot.CAMERA_STATUS, Slot.COCKPIT_SECONDARY,
                Slot.WORK_DOCUMENT, Slot.WORK_PROCESS, Slot.WORK_DRAFT)
                .filter { slot -> boxes[slot]?.let { it.top >= scene.top && it.bottom <= scene.bottom } == true }
                .map { Slot.AVATAR to it }.toSet()
        return JarvisShellLayout.Shell(JarvisShellLayout.formFactor(width, viewport), width, contentHeight,
            boxes, 0, 0, rail = JarvisShellLayout.Rail.LABELLED.takeIf { wide }, overlays = sceneOverlays,
            grid = grid, viewportHeight = viewport, profile = profile,
            headBounds = head, orbBounds = orb, headStage = head, workstation = true,
            protectedFaceBounds = head?.let { Box(center - it.height * .37f, it.top,
                center + it.height * .37f, it.top + it.height * .82f) })
    }
}
