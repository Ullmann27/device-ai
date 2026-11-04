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
        STATUS             // status strip (fold: full width)
    }

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2f
        fun contains(o: Box) = o.left >= left - .01f && o.right <= right + .01f && o.top >= top - .01f && o.bottom <= bottom + .01f
    }

    data class Shell(val formFactor: FormFactor, val width: Float, val height: Float, val boxes: Map<Slot, Box>,
                     val tileColumns: Int, val tileRows: Int, val rail: Rail? = null,
                     val overlays: Set<Pair<Slot, Slot>> = emptySet()) {
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
            FormFactor.FOLD_OPEN -> { // ref5: rail, hero row (bubble | head+orb | modules), 4×2 tiles, agent/task row, status
                val railW = if (foldRail == Rail.LABELLED) m.railFold else m.railCompact
                val statusH = 34f + big * 14f
                val statusTop = heightDp - m.margin - statusH
                b[Slot.STATUS] = Box(m.margin, statusTop, widthDp - m.margin, heightDp - m.margin)
                val l = railW + m.margin + g; val r = widthDp - m.margin; val room = r - l
                val top = 64f
                b[Slot.NAV_RAIL] = Box(m.margin, top, m.margin + railW, statusTop - g)
                b[Slot.TOP_BAR] = Box(m.margin, 0f, r, top - 4f)
                val cols = if (font > 1.3f) 2 else 4
                val rows = if (font > 1.3f) 4 else 2
                val content = statusTop - g - top
                val rowCards = content * .2f
                val tilesH = rows * (content * .09f + big * 20f) + (rows - 1) * 8f
                val rowTop = statusTop - g - rowCards
                val tilesTop = rowTop - g - tilesH
                b[Slot.TILES] = Box(l, tilesTop, r, rowTop - g)
                val q = (room - 3 * g) / 4f
                listOf(Slot.ROW_AGENTS, Slot.ROW_TASKS, Slot.ROW_MEMORY, Slot.ROW_MODEL).forEachIndexed { i, s ->
                    val x = l + i * (q + g); b[s] = Box(x, rowTop, x + q, statusTop - g)
                }
                val heroBottom = tilesTop - g
                val leftW = room * .25f; val rightW = room * .22f
                b[Slot.DIALOG] = Box(l, top, l + leftW, heroBottom)
                b[Slot.AVATAR] = Box(l + leftW + g, top, r - rightW - g, heroBottom)
                b[Slot.COCKPIT_SECONDARY] = Box(r - rightW, top, r, heroBottom)
                Shell(FormFactor.FOLD_OPEN, widthDp, heightDp, b, cols, rows, foldRail)
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
