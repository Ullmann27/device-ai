package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors
import at.heinz.deviceai.ui.JarvisDesign.Styles
import at.heinz.deviceai.ui.JarvisDesign.Type

/**
 * Fold7 side rail exactly after ref5 (open Fold7, both orientations; cover display and phone keep the 74 dp bottom
 * bar). Colours, type and the button style come ONLY from Welten-Optik's ref5 tokens (#479 @69b76b2f:
 * JarvisDesign.Colors / Type.nav / Styles.button, JarvisNeon.button/glassCard). No own animation: motion is
 * triggered through CockpitAnimationTriggers.areaChanged and played by Kern's presenter.
 *
 * Geometry measured in ref5 (1448 x 1086 px, Fold7 screen x 74..1375; px x 0.642 = dp like Welten-Optik):
 * - left margin screen → rail panel 22 px = 14 dp, panel/button width 169 px = 108 dp (= Styles.button width)
 * - rail column (margin + panel) 191 px of 1301 px = 14.7 % of the window width
 * - device status block: device name baseline 10 dp, clock baseline 44 dp, date baseline 58 dp, text x 24 dp
 * - rail panel top 120 px = 77 dp, first button top 153 px = 98 dp, button pitch 54.7 px = 35 dp
 *   (Styles.button 32 dp + Styles.buttonGap 6 dp ≈ 38 dp)
 * The owner requested adjoining visible buttons. Each button fills its independent 48 dp touch row; outlines and
 * their soft light can meet without ambiguous or overlapping hit targets. The first visible button stays at 98 dp.
 */
object JarvisMenuRail {
    enum class Mode { SIDE_RAIL, BOTTOM_BAR }

    const val WIDTH_FRACTION = .147f   // ref5: 191 px / 1301 px
    const val MARGIN = 14f             // ref5: 22 px screen edge → panel
    const val MIN_WIDTH = MARGIN + 108f // margin + Styles.button width (108 dp)
    const val MAX_WIDTH = 168f         // = JarvisDesign.Metrics.railLabelled (tablets)
    const val HEADER = 77f             // ref5: device status block above the panel
    const val FIRST_ROW = 98f          // first visible button top; its touch row shares the same bounds
    const val PAD = 10f                // panel bottom padding
    const val MIN_ROW = 48f            // touch target

    /** Visual pitch in ref5 is button + gap; the hit row never drops below [MIN_ROW]. */
    val ROW: Float get() = maxOf(MIN_ROW, Styles.button.height + Styles.buttonGap)

    fun mode(widthDp: Float, heightDp: Float): Mode =
        if (ContinuumHomeLayout.isWide(widthDp, heightDp)) Mode.SIDE_RAIL else Mode.BOTTOM_BAR

    /** Rail width in dp for a window; 0 for cover display / phone. */
    fun widthDp(widthDp: Float, heightDp: Float): Float =
        if (mode(widthDp, heightDp) == Mode.SIDE_RAIL) ContinuumHomeLayout.railWidthDp(widthDp) else 0f

    /** Row (top, bottom) per entry in rail coordinates; fixed ref5 start, independent of the window height. */
    @Suppress("UNUSED_PARAMETER")
    fun rows(heightDp: Float, count: Int = JarvisMenu.railEntries.size): List<Pair<Float, Float>> =
        List(count) { FIRST_ROW + it * ROW to FIRST_ROW + (it + 1) * ROW }

    /** Height the rail needs so every row keeps 48 dp (the rail scrolls when the IME shrinks the window). */
    fun minHeightDp(count: Int = JarvisMenu.railEntries.size): Float = FIRST_ROW + count * ROW + PAD

    fun hit(y: Float, heightDp: Float): Int = rows(heightDp).indexOfFirst { (t, b) -> y >= t && y < b }

    /** Adjoining visual surface shares its row bounds; glow may extend beyond these hit boundaries. */
    fun button(w: Float, row: Pair<Float, Float>): FloatArray {
        return floatArrayOf(MARGIN, row.first, w, row.second)
    }
}

/**
 * Device status block of the rail column (ref5 top left: "Galaxy Z Fold7", "12:47", "Wed, Jan 29").
 * Only real values: null → "—" in the same style (never the numbers from ref5).
 */
data class JarvisDeviceStatus(val deviceName: String?, val time: String?, val date: String?) {
    companion object {
        const val NONE = "—"
        val EMPTY = JarvisDeviceStatus(null, null, null)
    }
}

class JarvisMenuRailElement(var selected: Int = 0, textScale: Float = 1f,
                            private val entries: List<JarvisMenu.Entry> = JarvisMenu.railEntries,
                            var device: JarvisDeviceStatus = JarvisDeviceStatus.EMPTY) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        drawDeviceStatus(p, w)
        val rows = JarvisMenuRail.rows(h, entries.size)
        val panelBottom = minOf(h - 2f, rows.last().second + JarvisMenuRail.PAD)
        // ref5 rail panel: glass container from 77 dp, the buttons hug its sides.
        JarvisNeon.glassCard(p, JarvisMenuRail.MARGIN, JarvisMenuRail.HEADER, w - 1f, panelBottom,
            Styles.button.radius, JarvisDesign.Glow.OFF)
        rows.forEachIndexed { i, row ->
            val e = entries[i]
            val (l, t, r, b) = JarvisMenuRail.button(w - 1f, row).let { listOf(it[0] + 1f, it[1], it[2] - 1f, it[3]) }
            JarvisNeon.button(p, l, t, r, b, JarvisMenu.fitLabel(p, e, r - l), icon(e), active = i == selected)
        }
    }

    private fun icon(e: JarvisMenu.Entry) = JarvisNeon.IconPainter { p, left, top, size, color ->
        // ref5: "System Status" carries the violet pulse icon, all others cyan.
        val c = if (e.target == "system") Colors.violetSoft else color
        if (!JarvisRailIcons.draw(p, e.target, left, top, size, c))
            ContinuumIcons.draw(p, e.icon, left + size / 2f, top + size / 2f, size, Fill.Solid(c), 1.4f)
    }

    private fun drawDeviceStatus(p: ContinuumPainter, w: Float) {
        val x = 24f; val max = w - x - 2f
        fun line(v: String?, style: TextStyle, y: Float) =
            p.drawText(JarvisShellRenderer.fit(p, v ?: JarvisDeviceStatus.NONE, style, max), x, y, style, Fill.Solid(Colors.text))
        line(device.deviceName, Type.caption, 10f)
        line(device.time, Type.title, 44f)
        line(device.date, Type.nav, 58f)
    }

    override fun describe(s: ContinuumDashboardState) =
        "Gerät: " + (device.deviceName ?: JarvisDeviceStatus.NONE) + ", " + (device.time ?: JarvisDeviceStatus.NONE) + ", " +
            (device.date ?: JarvisDeviceStatus.NONE) + ". Navigation: " + entries.joinToString(", ") { it.label } +
            ". Aktiv: " + (entries.getOrNull(selected)?.label ?: "keiner")
}
