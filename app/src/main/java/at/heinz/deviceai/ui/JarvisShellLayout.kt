package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import at.heinz.deviceai.ui.JarvisWindowProfile.WidthClass

/** Adaptive bounds for native controls and the existing, independently rendered head and brain sphere. */
object JarvisShellLayout {
    /** Kept for existing callers; layout decisions use the explicit [JarvisWindowProfile] width classes. */
    enum class FormFactor { PHONE_PORTRAIT, FOLD_OPEN, TABLET_LANDSCAPE }

    /** One-row holographic tool dock that replaces the retired left rail. */
    const val TOOL_DOCK_HEIGHT_DP = 76f
    const val TOOL_DOCK_GAP_DP = 12f
    /** Extra height so the status slot can show the telemetry band plus the full status line. */
    const val TELEMETRY_BAND_DP = 64f

    fun toolDockLayoutExtra(fontScale: Float = 1f): Float {
        val font = if (fontScale.isFinite() && fontScale > 0f) fontScale else 1f
        return (TOOL_DOCK_HEIGHT_DP + TOOL_DOCK_GAP_DP + TELEMETRY_BAND_DP) * font
    }
    enum class Rail { LABELLED, ICONS }

    enum class Slot {
        TOP_BAR, NAV_RAIL, BOTTOM_NAV, AVATAR, DIALOG, INPUT,
        COCKPIT_PRIMARY, COCKPIT_SECONDARY, TILES,
        ROW_AGENTS, ROW_TASKS, ROW_MEMORY, ROW_MODEL, STATUS, USER_CARD,
        CONTINUUM_HEADER, CAMERA_STATUS,
        WORK_DOCUMENT, WORK_PROCESS, WORK_DRAFT
    }

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
        fun contains(o: Box) = o.left >= left - .01f && o.right <= right + .01f && o.top >= top - .01f && o.bottom <= bottom + .01f
        fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
    }

    data class Shell(
        val formFactor: FormFactor,
        val width: Float,
        /** Content height; compact / large-text compositions may exceed [viewportHeight] and scroll. */
        val height: Float,
        val boxes: Map<Slot, Box>,
        val tileColumns: Int,
        val tileRows: Int,
        val rail: Rail? = null,
        val overlays: Set<Pair<Slot, Slot>> = emptySet(),
        val grid: JarvisCockpitGrid.Grid? = null,
        val viewportHeight: Float = height,
        val profile: JarvisWindowProfile = JarvisWindowProfile.resolve(width, viewportHeight),
        val tileGap: Float = 8f,
        val headBounds: Box? = null,
        val orbBounds: Box? = null,
        /** Native surface, behind dialogue. */
        val headStage: Box? = null
    ) {
        val headAnchorX: Float get() = if (headStage != null) JarvisCockpitGrid.HEAD_ANCHOR_X else .5f
        val headCloseUp: Float get() = if (headStage != null) JarvisCockpitGrid.HEAD_CLOSE_UP else 1f
        /** Compatibility name: now the latest owner reference, not the superseded eight-tile ref5. */
        val ref5Grid: Boolean get() = grid != null
        val hasFeatureTiles: Boolean get() = Slot.TILES in boxes
        val requiresVerticalScroll: Boolean get() = height > viewportHeight + .5f
        operator fun get(slot: Slot) = boxes[slot]

        /** Home destinations across window sizes. */
        fun tileBoxes(gap: Float = tileGap): List<Box> {
            val t = boxes[Slot.TILES] ?: return emptyList()
            if (tileColumns <= 0 || tileRows <= 0) return emptyList()
            require(gap.isFinite() && gap >= 0f)
            val w = (t.width - (tileColumns - 1) * gap) / tileColumns
            val h = (t.height - (tileRows - 1) * gap) / tileRows
            return List(minOf(10, tileColumns * tileRows)) { i ->
                val x = t.left + (i % tileColumns) * (w + gap)
                val y = t.top + (i / tileColumns) * (h + gap)
                Box(x, y, x + w, y + h)
            }
        }

        /** Shared drawing and touch bounds. */
        fun dialogControlBoxes(): List<Box> {
            boxes[Slot.INPUT]?.let { input ->
                val y = input.centerY - 24f
                return listOf(input.left + 4f, input.right - 52f)
                    .map { x -> Box(x, y, x + 48f, y + 48f) }
            }
            val b = boxes[Slot.DIALOG] ?: return emptyList()
            val size = minOf(48f, b.width / 2f - 2f)
            val y = b.bottom - size - 3f
            val gap = (b.width - 2f * size) / 3f
            return List(2) { i ->
                val left = b.left + gap + i * (size + gap)
                Box(left, y, left + size, y + size)
            }
        }

        /** Excludes microphone and send. */
        fun inputConversationBox(): Box? = boxes[Slot.INPUT]?.let {
            Box(it.left + 56f, it.top, it.right - 56f, it.bottom)
        }

        /** Only meaningful Android actions. Optional menu is omitted when three safe targets do not fit. */
        fun headerActionBoxes(): Map<String, Box> {
            val b = grid?.areas?.get(Area.ICONS) ?: boxes[Slot.TOP_BAR]?.let {
                Box(maxOf(it.left, it.right - 96f), it.top, it.right, it.bottom)
            } ?: return emptyMap()
            val targets = if (grid != null && b.width >= 144f) listOf("notifications", "settings", "more")
                else listOf("notifications", "settings")
            val size = minOf(48f, b.width / targets.size)
            val top = (b.centerY - size / 2f).coerceAtLeast(0f)
            return targets.mapIndexed { index, target ->
                val left = b.right - targets.size * size + index * size
                target to Box(left, top, left + size, top + size)
            }.toMap()
        }
    }

    fun formFactor(widthDp: Float, heightDp: Float): FormFactor = when {
        widthDp < JarvisWindowProfile.MEDIUM_MIN_DP -> FormFactor.PHONE_PORTRAIT
        widthDp / heightDp.coerceAtLeast(1f) < 1.35f -> FormFactor.FOLD_OPEN
        else -> FormFactor.TABLET_LANDSCAPE
    }

    /** Width is the available pane. */
    fun forContent(widthDp: Float, heightDp: Float, fontScale: Float = 1f, foldRail: Rail = Rail.LABELLED,
                   externalRailWidthDp: Float = 0f, externalBottomBarHeightDp: Float = 0f,
                   includeBottomNavigation: Boolean = false, compactPresentation: Boolean = false): Shell {
        require(externalRailWidthDp.isFinite() && externalRailWidthDp >= 0f)
        require(externalBottomBarHeightDp.isFinite() && externalBottomBarHeightDp >= 0f && externalBottomBarHeightDp < heightDp)
        val viewport = heightDp - externalBottomBarHeightDp
        return create(widthDp, viewport, fontScale, foldRail, includeBottomNavigation, compactPresentation)
    }

    fun create(widthDp: Float, heightDp: Float, fontScale: Float = 1f, foldRail: Rail = Rail.LABELLED,
               includeBottomNavigation: Boolean = false, compactPresentation: Boolean = false): Shell {
        require(widthDp.isFinite() && widthDp >= 240f && heightDp.isFinite() && heightDp >= 160f)
        val profile = JarvisWindowProfile.resolve(widthDp, heightDp, fontScale, foldRail)
        return if (profile.referenceComposition) reference(widthDp, heightDp, profile).let {
            if (compactPresentation) compactReference(it) else it
        } else reflow(widthDp, heightDp, profile, includeBottomNavigation, compactPresentation)
    }

    private fun compactReference(shell: Shell): Shell {
        return reflow(shell.width, shell.viewportHeight, shell.profile, false, true)
    }

    private fun reference(w: Float, h: Float, profile: JarvisWindowProfile): Shell {
        val gr = JarvisCockpitGrid.create(w, h, profile.fontScale)
        val head = gr[Area.HEAD]
        val orb = gr[Area.ORB]
        val stage = head
        val b = linkedMapOf(
            Slot.TOP_BAR to gr[Area.HEADER],
            Slot.AVATAR to Box(head.left, minOf(head.top, orb.top), orb.right, maxOf(head.bottom, orb.bottom)),
            Slot.DIALOG to gr[Area.DIALOG], Slot.INPUT to gr[Area.INPUT],
            Slot.CONTINUUM_HEADER to gr[Area.CONTINUUM_HEADER],
            Slot.CAMERA_STATUS to gr[Area.CAMERA_STATUS], Slot.COCKPIT_SECONDARY to gr[Area.MODULES],
            Slot.WORK_DOCUMENT to gr[Area.WORK_DOCUMENT],
            Slot.WORK_PROCESS to gr[Area.WORK_PROCESS],
            Slot.WORK_DRAFT to gr[Area.WORK_DRAFT],
            Slot.BOTTOM_NAV to gr[Area.TOOL_DOCK],
            Slot.STATUS to gr[Area.STATUS]
        )
        return Shell(formFactor(w, h), w, gr.height, b, JarvisCockpitGrid.TILE_COLUMNS, JarvisCockpitGrid.TILE_ROWS, null,
            setOf(Slot.AVATAR to Slot.CONTINUUM_HEADER),
            gr, h, profile, JarvisCockpitGrid.TILE_GAP_DP, head, orb, headStage = stage)
    }

    /**
     * Places the holographic tool dock between the destination tiles and the status band,
     * then expands the status slot so telemetry tiles and the full status line both fit.
     * Existing grid boxes above the tiles stay byte-aligned with [JarvisCockpitGrid].
     */
    private fun installToolDock(boxes: MutableMap<Slot, Box>, width: Float): Float {
        if (Slot.BOTTOM_NAV in boxes) return 0f
        val tiles = boxes[Slot.TILES] ?: return 0f
        val status = boxes[Slot.STATUS]
        val margin = status?.left ?: 12f
        val right = status?.right ?: (width - 12f)
        val gap = TOOL_DOCK_GAP_DP
        val dockH = TOOL_DOCK_HEIGHT_DP
        val extraStatus = TELEMETRY_BAND_DP
        val shiftBelowDock = dockH + gap
        val total = shiftBelowDock + extraStatus
        val dockTop = tiles.bottom + gap
        boxes[Slot.BOTTOM_NAV] = Box(margin, dockTop, right, dockTop + dockH)
        val cutoff = tiles.bottom + .5f
        for ((slot, box) in boxes.toList()) {
            if (slot == Slot.BOTTOM_NAV || slot == Slot.TILES) continue
            if (box.top < cutoff) continue
            boxes[slot] = if (slot == Slot.STATUS)
                Box(box.left, box.top + shiftBelowDock, box.right, box.bottom + shiftBelowDock + extraStatus)
            else
                Box(box.left, box.top + total, box.right, box.bottom + total)
        }
        return total
    }

    /** Reflow preserves readable labels. */
    private fun reflow(w: Float, viewport: Float, p: JarvisWindowProfile,
                       includeBottomNavigation: Boolean, compactPresentation: Boolean): Shell {
        val b = linkedMapOf<Slot, Box>()
        val gap = 10f
        val margin = 12f
        val font = maxOf(1f, p.fontScale)
        val compact = p.widthClass == WidthClass.COMPACT
        val left = margin
        val right = w - margin
        val room = right - left
        val top = if (compact) 64f * font else 8f + (w * .1065f).coerceIn(64f, 96f) * font
        b[Slot.TOP_BAR] = Box(margin, if (compact) 4f else 8f, right, top)
        var y = top + gap
        val head: Box
        val orb: Box
        if (compactPresentation) {
            b[Slot.CONTINUUM_HEADER] = Box(left, y, right, y + 70f * font)
            y += 70f * font + gap
            orb = Box(left, y, right, y + 112f)
            head = orb
            y = orb.bottom + gap
        } else {
            val minimumHero = maxOf(220f, 74f * font + 112f)
            val heroH = if (compact) minOf(320f, maxOf(minimumHero,
                viewport - y - 56f * font - gap - 178f))
                else (room * JarvisCockpitGrid.HERO_HEIGHT_RATIO).coerceAtMost(JarvisCockpitGrid.MAX_HERO_DP)
            val split = left + room * if (compact) .55f else .54f
            head = Box(left, y, split - gap / 2f, y + heroH)
            b[Slot.CONTINUUM_HEADER] = Box(split + gap / 2f, y, right, y + 74f * font)
            orb = Box(split + gap / 2f, y + 74f * font, right, y + heroH)
            y += heroH + gap
        }
        b[Slot.AVATAR] = Box(left, head.top, right, orb.bottom)
        val overlayBadge = !compact && !compactPresentation
        if (overlayBadge) {
            val badgeWidth = minOf(224f * font, head.width - 12f)
            b[Slot.DIALOG] = Box(left + 6f, head.bottom - 56f * font - 6f,
                left + 6f + badgeWidth, head.bottom - 6f)
        } else {
            b[Slot.DIALOG] = Box(left, y, right, y + 56f * font)
            y += 56f * font + gap
        }
        val inputHeight = if (compact) 56f * font else (room * .10f).coerceIn(56f, 88f) * font
        b[Slot.INPUT] = Box(left, y, right, y + inputHeight)
        y += inputHeight + gap
        val rows = (10 + p.tileColumns - 1) / p.tileColumns
        val statusH = (if (compact) 90f else 56f) * font + TELEMETRY_BAND_DP * font
        val tileGap = JarvisCockpitGrid.TILE_GAP_DP
        val tileWidth = (room - (p.tileColumns - 1) * tileGap) / p.tileColumns
        val tileH = maxOf(JarvisCockpitGrid.MIN_TILE_DP * font, tileWidth / JarvisCockpitGrid.TILE_ASPECT)
        b[Slot.TILES] = Box(left, y, right, y + rows * tileH + (rows - 1) * tileGap)
        y = b.getValue(Slot.TILES).bottom + gap
        if (!compact) {
            val dockH = TOOL_DOCK_HEIGHT_DP * font
            b[Slot.BOTTOM_NAV] = Box(left, y, right, y + dockH)
            y += dockH + gap
        }
        b[Slot.STATUS] = Box(margin, y, right, y + statusH)
        y += statusH + 24f

        val infoW = (room - (p.informationColumns - 1) * gap) / p.informationColumns
        fun informationRow(slots: List<Slot>, cardHeight: Float) {
            slots.forEachIndexed { i, slot ->
                val x = left + (i % p.informationColumns) * (infoW + gap)
                val t = y + (i / p.informationColumns) * (cardHeight + gap)
                b[slot] = Box(x, t, x + infoW, t + cardHeight)
            }
            y += ((slots.size + p.informationColumns - 1) / p.informationColumns) * (cardHeight + gap)
        }
        if (compact) {
            informationRow(listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL), 164f * font)
        } else {
            informationRow(listOf(Slot.ROW_MEMORY, Slot.ROW_MODEL), 190f * font)
            informationRow(listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS), 132f * font)
        }
        if (compact || p.informationColumns == 1) {
            b[Slot.CAMERA_STATUS] = Box(left, y, right, y + 56f * font)
            y += 56f * font + gap
            b[Slot.COCKPIT_SECONDARY] = Box(left, y, right, y + 164f * font)
            y += 164f * font + margin
        } else {
            val detailWidth = (room - gap) / 2f
            b[Slot.CAMERA_STATUS] = Box(left, y, left + detailWidth, y + 56f * font)
            b[Slot.COCKPIT_SECONDARY] = Box(left + detailWidth + gap, y, right, y + 164f * font)
            y += 164f * font + margin
        }
        if (compact && includeBottomNavigation) {
            val navH = 68f * font
            b[Slot.BOTTOM_NAV] = Box(0f, y, w, y + navH)
            y += navH
        }
        val overlays = buildSet {
            add(Slot.AVATAR to Slot.CONTINUUM_HEADER)
            if (overlayBadge) add(Slot.AVATAR to Slot.DIALOG)
        }
        return Shell(formFactor(w, viewport), w, maxOf(viewport, y), b, p.tileColumns, rows,
            null, overlays, viewportHeight = viewport, profile = p, tileGap = tileGap,
            headBounds = if (compactPresentation) null else head, orbBounds = orb)
    }
}
