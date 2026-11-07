package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisCockpitGrid.Area
import at.heinz.deviceai.ui.JarvisWindowProfile.WidthClass

/** Adaptive bounds for native controls and the existing, independently rendered head and brain sphere. */
object JarvisShellLayout {
    /** Kept for existing callers; layout decisions use the explicit [JarvisWindowProfile] width classes. */
    enum class FormFactor { PHONE_PORTRAIT, FOLD_OPEN, TABLET_LANDSCAPE }
    enum class Rail { LABELLED, ICONS }

    enum class Slot {
        TOP_BAR, NAV_RAIL, BOTTOM_NAV, AVATAR, DIALOG, INPUT,
        COCKPIT_PRIMARY, COCKPIT_SECONDARY, TILES,
        ROW_AGENTS, ROW_TASKS, ROW_MEMORY, ROW_MODEL, STATUS, USER_CARD,
        CONTINUUM_HEADER, CAMERA_STATUS
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
        val requiresVerticalScroll: Boolean get() = height > viewportHeight + .5f
        operator fun get(slot: Slot) = boxes[slot]

        /** Exactly ten independently reachable tiles, including an incomplete last row when reflowed. */
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
                return listOf(input.left + 4f, input.right - 100f, input.right - 52f)
                    .map { x -> Box(x, y, x + 48f, y + 48f) }
            }
            val b = boxes[Slot.DIALOG] ?: return emptyList()
            val size = minOf(48f, b.width / 3f - 2f)
            val y = b.bottom - size - 3f
            if (b.width * .24f >= size + 2f) return listOf(.26f, .50f, .74f).map { fraction ->
                val cx = b.left + b.width * fraction
                Box(cx - size / 2f, y, cx + size / 2f, y + size)
            }
            val gap = (b.width - 3f * size) / 4f
            return List(3) { i ->
                val left = b.left + gap + i * (size + gap)
                Box(left, y, left + size, y + size)
            }
        }

        /** Excludes microphone, camera and appearance. */
        fun inputConversationBox(): Box? = boxes[Slot.INPUT]?.let {
            Box(it.left + 56f, it.top, it.right - 104f, it.bottom)
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

    /**
     * The Activity can retain its rail / bottom bar on non-dashboard panes. Subtract these reservations exactly
     * once. Dashboard callers normally pass zero rail because the full-width home owns its reference rail.
     */
    fun forContent(widthDp: Float, heightDp: Float, fontScale: Float = 1f, foldRail: Rail = Rail.LABELLED,
                   externalRailWidthDp: Float = 0f, externalBottomBarHeightDp: Float = 0f,
                   includeBottomNavigation: Boolean = false, compactPresentation: Boolean = false): Shell {
        require(externalRailWidthDp.isFinite() && externalRailWidthDp >= 0f)
        require(externalBottomBarHeightDp.isFinite() && externalBottomBarHeightDp >= 0f && externalBottomBarHeightDp < heightDp)
        val viewport = heightDp - externalBottomBarHeightDp
        val full = create(widthDp + externalRailWidthDp, viewport, fontScale, foldRail, includeBottomNavigation, compactPresentation)
        if (externalRailWidthDp == 0f) return full
        fun shift(box: Box): Box = Box(maxOf(0f, box.left - externalRailWidthDp), box.top,
            maxOf(0f, box.right - externalRailWidthDp), box.bottom)
        val boxes = full.boxes.filterKeys { it != Slot.NAV_RAIL }.mapValues { shift(it.value) }
            .filterValues { it.width > 0f }
        val grid = full.grid?.let { original ->
            val areas = original.areas.filterKeys { it != Area.RAIL && it != Area.DEVICE_STATUS }
                .mapValues { shift(it.value) }.filterValues { it.width > 0f }
            original.copy(width = widthDp, areas = areas)
        }
        return full.copy(width = widthDp, boxes = boxes, grid = grid,
            headBounds = full.headBounds?.let(::shift), orbBounds = full.orbBounds?.let(::shift),
            headStage = full.headStage?.let(::shift))
    }

    fun create(widthDp: Float, heightDp: Float, fontScale: Float = 1f, foldRail: Rail = Rail.LABELLED,
               includeBottomNavigation: Boolean = false, compactPresentation: Boolean = false): Shell {
        require(widthDp.isFinite() && widthDp >= 240f && heightDp.isFinite() && heightDp >= 160f)
        val profile = JarvisWindowProfile.resolve(widthDp, heightDp, fontScale, foldRail)
        return if (profile.referenceComposition) reference(widthDp, heightDp, profile, foldRail).let {
            if (compactPresentation) compactReference(it) else it
        } else reflow(widthDp, heightDp, profile, foldRail, includeBottomNavigation, compactPresentation)
    }

    private fun compactReference(shell: Shell): Shell {
        return reflow(shell.width, shell.viewportHeight, shell.profile,
            shell.rail ?: Rail.LABELLED, false, true)
    }

    private fun reference(w: Float, h: Float, profile: JarvisWindowProfile, rail: Rail): Shell {
        val gr = JarvisCockpitGrid.create(w, h, profile.fontScale)
        val head = gr[Area.HEAD]
        val orb = gr[Area.ORB]
        val stage = head
        val b = linkedMapOf(
            Slot.TOP_BAR to gr[Area.HEADER], Slot.NAV_RAIL to gr[Area.RAIL],
            Slot.AVATAR to Box(stage.left, minOf(head.top, orb.top), orb.right, maxOf(head.bottom, orb.bottom)),
            Slot.DIALOG to gr[Area.DIALOG], Slot.INPUT to gr[Area.INPUT],
            Slot.CONTINUUM_HEADER to gr[Area.CONTINUUM_HEADER],
            Slot.CAMERA_STATUS to gr[Area.CAMERA_STATUS], Slot.COCKPIT_SECONDARY to gr[Area.MODULES],
            Slot.TILES to gr[Area.TILES], Slot.ROW_AGENTS to gr[Area.ROW_AGENTS],
            Slot.ROW_TASKS to gr[Area.ROW_TASKS], Slot.ROW_MEMORY to gr[Area.ROW_MEMORY],
            Slot.ROW_MODEL to gr[Area.ROW_MODEL], Slot.STATUS to gr[Area.STATUS]
        )
        return Shell(formFactor(w, h), w, gr.height, b, 5, 2, rail,
            setOf(Slot.AVATAR to Slot.DIALOG, Slot.AVATAR to Slot.CONTINUUM_HEADER),
            gr, h, profile, JarvisCockpitGrid.TILE_GAP_DP, head, orb, headStage = stage)
    }

    /** Reflow preserves readable labels. */
    private fun reflow(w: Float, viewport: Float, p: JarvisWindowProfile, rail: Rail,
                       includeBottomNavigation: Boolean, compactPresentation: Boolean): Shell {
        val b = linkedMapOf<Slot, Box>()
        val gap = 10f
        val margin = 12f
        val font = maxOf(1f, p.fontScale)
        val compact = p.widthClass == WidthClass.COMPACT
        val left = if (compact) margin else p.railWidthDp + gap
        val right = w - margin
        val room = right - left
        val top = 64f * font
        b[Slot.TOP_BAR] = Box(margin, 4f, right, top)
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
                else maxOf(280f, minOf(room * .54f, 400f))
            val split = left + room * .55f
            head = Box(left, y, split - gap / 2f, y + heroH)
            b[Slot.CONTINUUM_HEADER] = Box(split + gap / 2f, y, right, y + 74f * font)
            orb = Box(split + gap / 2f, y + 74f * font, right, y + heroH)
            y += heroH + gap
        }
        b[Slot.AVATAR] = Box(left, head.top, right, orb.bottom)
        b[Slot.DIALOG] = Box(left, y, right, y + 56f * font)
        y += 56f * font + gap
        b[Slot.INPUT] = Box(left, y, right, y + 56f * font)
        y += 56f * font + gap
        val rows = (10 + p.tileColumns - 1) / p.tileColumns
        val tileH = JarvisCockpitGrid.MIN_TILE_DP * font
        b[Slot.TILES] = Box(left, y, right, y + rows * tileH + (rows - 1) * gap)
        y = b.getValue(Slot.TILES).bottom + gap
        val statusH = if (compact) 90f * font else 56f * font
        b[Slot.STATUS] = Box(margin, y, right, y + statusH)
        if (!compact) b[Slot.NAV_RAIL] = Box(margin, top + gap, p.railWidthDp, y - gap)
        y += statusH + 24f

        val infos = listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL)
        val infoH = 164f * font
        val infoW = (room - (p.informationColumns - 1) * gap) / p.informationColumns
        infos.forEachIndexed { i, slot ->
            val x = left + (i % p.informationColumns) * (infoW + gap)
            val t = y + (i / p.informationColumns) * (infoH + gap)
            b[slot] = Box(x, t, x + infoW, t + infoH)
        }
        y += ((infos.size + p.informationColumns - 1) / p.informationColumns) * (infoH + gap)
        if (compact) {
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
        return Shell(formFactor(w, viewport), w, maxOf(viewport, y), b, p.tileColumns, rows,
            if (compact) null else rail, setOf(Slot.AVATAR to Slot.CONTINUUM_HEADER),
            viewportHeight = viewport, profile = p, tileGap = gap,
            headBounds = if (compactPresentation) null else head, orbBounds = orb)
    }
}
