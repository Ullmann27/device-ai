package at.heinz.deviceai.ui

/**
 * Responsive shell for the Jarvis app (ref2 overview, ref4 phone portrait, ref5 Fold7 open).
 * Pure dp geometry, no Android dependency. Jarvis Kern fills the COCKPIT_* / TILES / STATUS slots,
 * Jarvis Avatar draws into AVATAR (head + brain orb). Slots absent for a form factor are simply missing.
 */
object JarvisShellLayout {
    enum class FormFactor { PHONE_PORTRAIT, FOLD_OPEN, TABLET_LANDSCAPE }

    enum class Slot {
        TOP_BAR,           // brand / Continuum logo, menu
        NAV_RAIL,          // fold (icons) / tablet (icons + labels)
        BOTTOM_NAV,        // phone only
        AVATAR,            // Jarvis Avatar: head + brain orb on pedestal
        COCKPIT_PRIMARY,   // fold: dialogue card left; tablet: status column right
        COCKPIT_SECONDARY, // fold: core modules right; tablet: live feed / resources
        TILES,             // phone: tile grid under the hero
        STATUS,            // phone: status strip; fold/tablet: bottom status line
        INPUT              // message / voice input bar
    }

    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    data class Shell(val formFactor: FormFactor, val width: Float, val height: Float, val boxes: Map<Slot, Box>,
                     val tileColumns: Int) {
        operator fun get(slot: Slot) = boxes[slot]
    }

    /** Fold7 open is ~ square (aspect < 1.35), wide >= 600dp; tablets are clearly landscape. */
    fun formFactor(widthDp: Float, heightDp: Float): FormFactor = when {
        widthDp < 600f -> FormFactor.PHONE_PORTRAIT
        widthDp / heightDp.coerceAtLeast(1f) < 1.35f -> FormFactor.FOLD_OPEN
        else -> FormFactor.TABLET_LANDSCAPE
    }

    fun create(widthDp: Float, heightDp: Float, fontScale: Float = 1f): Shell {
        require(widthDp.isFinite() && widthDp >= 240f && heightDp.isFinite() && heightDp >= 320f)
        val font = if (fontScale.isFinite()) fontScale.coerceIn(.85f, 2f) else 1f
        val m = JarvisDesign.Metrics
        val g = m.gap
        val ff = formFactor(widthDp, heightDp)
        val b = linkedMapOf<Slot, Box>()
        var cols = 0
        when (ff) {
            FormFactor.PHONE_PORTRAIT -> { // ref4
                val top = 56f
                val nav = m.bottomNavHeight
                b[Slot.TOP_BAR] = Box(0f, 0f, widthDp, top)
                b[Slot.BOTTOM_NAV] = Box(0f, heightDp - nav, widthDp, heightDp)
                val l = m.margin; val r = widthDp - m.margin
                val status = 48f * font
                val statusTop = heightDp - nav - g - status
                b[Slot.STATUS] = Box(l, statusTop, r, statusTop + status)
                cols = if (font > 1.3f || r - l < 300f) 2 else 3
                val tileH = 76f * font
                val rows = 2
                val tilesTop = statusTop - g - rows * tileH - (rows - 1) * g
                val avatarBottom = (tilesTop - g).coerceAtLeast(top + 200f)
                b[Slot.AVATAR] = Box(l, top, r, avatarBottom)
                b[Slot.TILES] = Box(l, avatarBottom + g, r, statusTop - g)
            }
            FormFactor.FOLD_OPEN -> { // ref5 / ref2 left
                val rail = m.railCompact
                b[Slot.NAV_RAIL] = Box(0f, 0f, rail, heightDp)
                val l = rail + g; val r = widthDp - m.margin
                val top = 60f
                b[Slot.TOP_BAR] = Box(l, 0f, r, top)
                val input = m.inputBarHeight
                val status = 36f
                b[Slot.STATUS] = Box(l, heightDp - m.margin - status, r, heightDp - m.margin)
                val inputTop = heightDp - m.margin - status - g - input
                val room = r - l
                val leftW = room * .36f; val rightW = room * .26f
                b[Slot.COCKPIT_PRIMARY] = Box(l, top, l + leftW, inputTop - g)
                b[Slot.AVATAR] = Box(l + leftW + g, top, r - rightW - g, inputTop - g)
                b[Slot.COCKPIT_SECONDARY] = Box(r - rightW, top, r, inputTop - g)
                b[Slot.INPUT] = Box(l, inputTop, r - rightW - g, inputTop + input)
            }
            FormFactor.TABLET_LANDSCAPE -> { // ref2 centre / top
                val rail = m.railLabelled
                b[Slot.NAV_RAIL] = Box(0f, 0f, rail, heightDp)
                val l = rail + g; val r = widthDp - m.margin
                val top = 60f
                b[Slot.TOP_BAR] = Box(l, 0f, r, top)
                val input = m.inputBarHeight
                val inputTop = heightDp - m.margin - input
                val room = r - l
                val avatarW = room * .40f
                b[Slot.AVATAR] = Box(l, top, l + avatarW, inputTop - g)
                val cl = l + avatarW + g
                val mid = top + (inputTop - g - top) * .55f
                b[Slot.COCKPIT_PRIMARY] = Box(cl, top, r, mid)
                b[Slot.COCKPIT_SECONDARY] = Box(cl, mid + g, r, inputTop - g)
                b[Slot.INPUT] = Box(l, inputTop, l + avatarW, inputTop + input)
                b[Slot.STATUS] = Box(cl, inputTop, r, inputTop + input)
            }
        }
        return Shell(ff, widthDp, heightDp, b, cols)
    }
}
