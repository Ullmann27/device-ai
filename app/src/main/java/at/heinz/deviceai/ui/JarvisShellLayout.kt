package at.heinz.deviceai.ui

/**
 * Responsive shell for the Jarvis app (ref2 overview/tablet, ref4 phone portrait, ref5 Fold7 open).
 * Pure dp geometry, no Android dependency. Jarvis Kern fills COCKPIT_*, TILES, ROW_*, STATUS, DIALOG, INPUT;
 * Jarvis Avatar draws into AVATAR (head + brain orb). Slots absent for a form factor are simply missing.
 * DIALOG may lie inside AVATAR ([overlays]): the speech bubble floats over the hero as in ref4/ref5.
 */
object JarvisShellLayout {
    enum class FormFactor { PHONE_PORTRAIT, FOLD_OPEN, TABLET_LANDSCAPE }

    /** Navigation rail style. Fold7: LABELLED (Heinz-Entscheid 4.11., ref5/Videos); ICONS optional. */
    enum class Rail { LABELLED, ICONS }

    enum class Slot {
        TOP_BAR,           // brand / Continuum logo, mode, actions
        NAV_RAIL,          // fold / tablet navigation
        BOTTOM_NAV,        // phone only
        AVATAR,            // Jarvis Avatar: head + brain orb on pedestal
        DIALOG,            // Jarvis speech bubble (live dialogue)
        INPUT,             // "Message Continuum" + voice
        COCKPIT_PRIMARY,   // tablet: core status
        COCKPIT_SECONDARY, // fold: module list right of the head; tablet: live feed / resources
        TILES,             // action tile grid (phone 2×4, fold 4×2)
        ROW_AGENTS, ROW_TASKS, ROW_MEMORY, ROW_MODEL, // fold: agent / task-flow / memory / model row (ref5)
        STATUS,            // status strip (fold: full width)
        USER_CARD          // fold (ref5 grid): owner recognition card left of the head (JarvisUserCardSlot, Lückenfüller #487)
    }

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2f
        fun contains(o: Box) = o.left >= left - .01f && o.right <= right + .01f && o.top >= top - .01f && o.bottom <= bottom + .01f
    }

    data class Shell(val formFactor: FormFactor, val width: Float, val height: Float, val boxes: Map<Slot, Box>,
                     val tileColumns: Int, val tileRows: Int, val rail: Rail? = null,
                     val overlays: Set<Pair<Slot, Slot>> = emptySet(),
                     /** Fold7 open: Kern's ref5 grid ([JarvisCockpitGrid]); null = legacy composition. */
                     val grid: JarvisCockpitGrid.Grid? = null) {
        val ref5Grid: Boolean get() = grid != null
        operator fun get(slot: Slot) = boxes[slot]

        /** Individual tile rectangles inside [Slot.TILES], row-major. */
        fun tileBoxes(gap: Float = 8f): List<Box> {
            val t = boxes[Slot.TILES] ?: return emptyList()
            if (tileColumns <= 0 || tileRows <= 0) return emptyList()
            val w = (t.width - (tileColumns - 1) * gap) / tileColumns
            val h = (t.height - (tileRows - 1) * gap) / tileRows
            return List(tileColumns * tileRows) { i ->
                val x = t.left + (i % tileColumns) * (w + gap); val y = t.top + (i / tileColumns) * (h + gap)
                Box(x, y, x + w, y + h)
            }
        }
    }

    /** Fold7 open is ~ square (aspect < 1.35), wide >= 600dp; tablets are clearly landscape. */
    fun formFactor(widthDp: Float, heightDp: Float): FormFactor = when {
        widthDp < 600f -> FormFactor.PHONE_PORTRAIT
        widthDp / heightDp.coerceAtLeast(1f) < 1.35f -> FormFactor.FOLD_OPEN
        else -> FormFactor.TABLET_LANDSCAPE
    }

    fun create(widthDp: Float, heightDp: Float, fontScale: Float = 1f, foldRail: Rail = Rail.LABELLED): Shell {
        require(widthDp.isFinite() && widthDp >= 240f && heightDp.isFinite() && heightDp >= 320f)
        val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 2f) else 1f
        val big = (font - 1f).coerceAtLeast(0f)
        val m = JarvisDesign.Metrics
        val g = m.gap
        val b = linkedMapOf<Slot, Box>()
        return when (formFactor(widthDp, heightDp)) {
            FormFactor.PHONE_PORTRAIT -> { // ref4: hero, bubble, input, 2×4 tiles, status, bottom nav
                val l = m.margin; val r = widthDp - m.margin; val room = r - l
                val top = 64f
                b[Slot.TOP_BAR] = Box(0f, 0f, widthDp, top)
                val navTop = heightDp - m.bottomNavHeight
                b[Slot.BOTTOM_NAV] = Box(0f, navTop, widthDp, heightDp)
                val statusTop = navTop - g - (104f + big * 40f)
                b[Slot.STATUS] = Box(l, statusTop, r, navTop - g)
                val rows = 4; val rowH = 56f + big * 28f
                val tilesTop = statusTop - g - rows * rowH - (rows - 1) * 8f
                b[Slot.TILES] = Box(l, tilesTop, r, statusTop - g)
                val inputTop = tilesTop - g - m.inputBarHeight * .85f
                b[Slot.INPUT] = Box(l, inputTop, r, tilesTop - g)
                val avatarBottom = inputTop - g
                b[Slot.AVATAR] = Box(l, top, r, avatarBottom)
                b[Slot.DIALOG] = Box(l, avatarBottom - (80f + big * 36f), l + room * .5f, avatarBottom)
                Shell(FormFactor.PHONE_PORTRAIT, widthDp, heightDp, b, 2, rows, overlays = setOf(Slot.AVATAR to Slot.DIALOG))
            }
            FormFactor.FOLD_OPEN -> if (foldRail == Rail.LABELLED && JarvisCockpitGrid.applies(widthDp, heightDp, font)) {
                // §11: ref5 grid (Jarvis Kern), everything visible at once, compact sizes.
                val gr = JarvisCockpitGrid.create(widthDp, heightDp)
                fun box(a: JarvisCockpitGrid.Area) = gr[a]
                val head = box(JarvisCockpitGrid.Area.HEAD); val orb = box(JarvisCockpitGrid.Area.ORB)
                b[Slot.TOP_BAR] = box(JarvisCockpitGrid.Area.HEADER)
                b[Slot.NAV_RAIL] = box(JarvisCockpitGrid.Area.RAIL)
                b[Slot.AVATAR] = Box(head.left, head.top, orb.right, head.bottom) // head + brain orb
                b[Slot.USER_CARD] = box(JarvisCockpitGrid.Area.USER_CARD)
                b[Slot.DIALOG] = box(JarvisCockpitGrid.Area.DIALOG)
                b[Slot.COCKPIT_SECONDARY] = box(JarvisCockpitGrid.Area.MODULES)
                b[Slot.TILES] = box(JarvisCockpitGrid.Area.TILES)
                b[Slot.ROW_AGENTS] = box(JarvisCockpitGrid.Area.ROW_AGENTS)
                b[Slot.ROW_TASKS] = box(JarvisCockpitGrid.Area.ROW_TASKS)
                b[Slot.ROW_MEMORY] = box(JarvisCockpitGrid.Area.ROW_MEMORY)
                b[Slot.ROW_MODEL] = box(JarvisCockpitGrid.Area.ROW_MODEL)
                b[Slot.STATUS] = box(JarvisCockpitGrid.Area.STATUS)
                Shell(FormFactor.FOLD_OPEN, widthDp, heightDp, b, 4, 2, foldRail,
                    overlays = setOf(Slot.AVATAR to Slot.DIALOG, Slot.AVATAR to Slot.USER_CARD), grid = gr)
            } else { // Review 1: 3 columns ~20/55/25 (labelled nav / avatar + tiles / status), status strip below
                val statusH = 34f + big * 14f
                val statusTop = heightDp - m.margin - statusH
                b[Slot.STATUS] = Box(m.margin, statusTop, widthDp - m.margin, heightDp - m.margin)
                val inner = widthDp - 2 * m.margin - 2 * g
                val navW = if (foldRail == Rail.LABELLED) inner * .20f else m.railCompact
                val statusW = inner * .25f
                val top = 60f
                b[Slot.TOP_BAR] = Box(m.margin, 0f, widthDp - m.margin, top - 4f)
                b[Slot.NAV_RAIL] = Box(m.margin, top, m.margin + navW, statusTop - g)
                val l = m.margin + navW + g; val r = widthDp - m.margin - statusW - g
                val cols = if (font > 1.3f) 2 else 4
                val rows = if (font > 1.3f) 4 else 2
                val bottom = statusTop - g
                val tilesH = rows * (58f + big * 24f) + (rows - 1) * 8f
                b[Slot.TILES] = Box(l, bottom - tilesH, r, bottom)
                val avatarBottom = bottom - tilesH - g
                b[Slot.AVATAR] = Box(l, top, r, avatarBottom)
                b[Slot.DIALOG] = Box(l, avatarBottom - (84f + big * 36f), l + (r - l) * .42f, avatarBottom)
                val sl = widthDp - m.margin - statusW
                val right = listOf(Slot.COCKPIT_SECONDARY, Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL)
                val weights = floatArrayOf(1.4f, 1f, 1f, .8f, .8f)
                val avail = bottom - top - (right.size - 1) * g
                var y = top
                right.forEachIndexed { k, s -> val h = avail * weights[k] / weights.sum(); b[s] = Box(sl, y, widthDp - m.margin, y + h); y += h + g }
                Shell(FormFactor.FOLD_OPEN, widthDp, heightDp, b, cols, rows, foldRail, overlays = setOf(Slot.AVATAR to Slot.DIALOG))
            }
            FormFactor.TABLET_LANDSCAPE -> { // ref2: rail, bubble | dominant centred head | status/live feed, input under head
                val railW = m.railLabelled
                b[Slot.NAV_RAIL] = Box(0f, 0f, railW, heightDp)
                val l = railW + g; val r = widthDp - m.margin; val room = r - l
                val top = 60f
                b[Slot.TOP_BAR] = Box(l, 0f, r, top)
                val inputTop = heightDp - m.margin - m.inputBarHeight
                val sideL = room * .22f; val sideR = room * .27f
                val avL = l + sideL + g; val avR = r - sideR - g
                b[Slot.DIALOG] = Box(l, top, l + sideL, inputTop - g)
                b[Slot.AVATAR] = Box(avL, top, avR, inputTop - g)
                b[Slot.INPUT] = Box(avL, inputTop, avR, inputTop + m.inputBarHeight)
                val cl = r - sideR
                val mid = top + (inputTop - g - top) * .5f
                b[Slot.COCKPIT_PRIMARY] = Box(cl, top, r, mid)
                b[Slot.COCKPIT_SECONDARY] = Box(cl, mid + g, r, inputTop - g)
                b[Slot.STATUS] = Box(cl, inputTop, r, inputTop + m.inputBarHeight)
                Shell(FormFactor.TABLET_LANDSCAPE, widthDp, heightDp, b, 0, 0, Rail.LABELLED)
            }
        }
    }
}
