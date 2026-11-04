package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisDesign.Colors

/**
 * Fold7 side rail after ref5 (Heinz-Entscheid: open Fold7 in BOTH orientations; cover display and phone keep the
 * 74 dp bottom bar). Width ~20 % (Bildbeschrieb), glass #0A1128 @50 % with 1 px cyan rim, active entry white with
 * cyan glow, the others muted. Colours/glow only from JarvisDesign / JarvisNeon. No own animation here: motion is
 * triggered through CockpitAnimationTriggers.areaChanged and played by Kern's presenter.
 */
object JarvisMenuRail {
    enum class Mode { SIDE_RAIL, BOTTOM_BAR }

    const val WIDTH_FRACTION = .20f
    const val MIN_WIDTH = 132f   // icon + "Live-Status" / "Entwicklung" readable at font scale 1
    const val MAX_WIDTH = 200f   // tablets: keep the cockpit dominant
    const val PAD = 10f
    const val MIN_ROW = 48f      // touch target
    const val MAX_ROW = 64f

    fun mode(widthDp: Float, heightDp: Float): Mode =
        if (ContinuumHomeLayout.isWide(widthDp, heightDp)) Mode.SIDE_RAIL else Mode.BOTTOM_BAR

    /** Rail width in dp for a window; 0 for cover display / phone. */
    fun widthDp(widthDp: Float, heightDp: Float): Float =
        if (mode(widthDp, heightDp) == Mode.SIDE_RAIL) ContinuumHomeLayout.railWidthDp(widthDp) else 0f

    /** Row (top, bottom) per entry in rail coordinates. */
    fun rows(heightDp: Float, count: Int = JarvisMenu.railEntries.size): List<Pair<Float, Float>> {
        val row = ((heightDp - 2 * PAD) / count).coerceIn(MIN_ROW, MAX_ROW)
        return List(count) { PAD + it * row to PAD + (it + 1) * row }
    }

    fun hit(y: Float, heightDp: Float): Int = rows(heightDp).indexOfFirst { (t, b) -> y >= t && y < b }
}

class JarvisMenuRailElement(var selected: Int = 0, textScale: Float = 1f,
                            private val entries: List<JarvisMenu.Entry> = JarvisMenu.railEntries) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        val inset = 4f
        JarvisNeon.glassCard(p, inset, inset, w - inset, h - inset, JarvisDesign.Metrics.radiusCard, JarvisDesign.Glow.SUBTLE)
        JarvisMenuRail.rows(h, entries.size).forEachIndexed { i, (top, bottom) ->
            val e = entries[i]
            val active = i == selected
            val l = inset + 6f; val r = w - inset - 6f
            val cy = (top + bottom) / 2f
            if (active) {
                p.fillRoundRect(l, top + 3f, r, bottom - 3f, JarvisDesign.Metrics.radiusControl, Fill.Solid(Argb.withAlpha(Colors.cyan, .16f)))
                JarvisNeon.bloomRoundRect(p, l, top + 3f, r, bottom - 3f, JarvisDesign.Metrics.radiusControl, Colors.cyan, .8f)
                p.strokeRoundRect(l, top + 3f, r, bottom - 3f, JarvisDesign.Metrics.radiusControl, 1f, Fill.Solid(Colors.activeWhite), .7f)
            }
            val iconX = l + 18f
            val color = if (active) Colors.activeWhite else Colors.textSecondary
            if (active) JarvisNeon.bloom(p, iconX, cy, 12f, Colors.cyan, .7f)
            ContinuumIcons.draw(p, e.icon, iconX, cy, 20f, Fill.Solid(color), 1.5f)
            val labelX = iconX + 18f
            val style = fittedTitle(p, e.label, if (active) 600 else 400, 13f, r - labelX - 4f)
            val y = cy + p.capHeight(style) / 2f
            if (active) JarvisNeon.text(p, e.label, labelX, y, style, Colors.activeWhite, glow = JarvisDesign.Glow.STRONG, active = true)
            else p.drawText(e.label, labelX, y, style, Fill.Solid(color))
        }
    }

    override fun describe(s: ContinuumDashboardState) =
        "Navigation: " + entries.joinToString(", ") { it.label } + ". Aktiv: " + (entries.getOrNull(selected)?.label ?: "keiner")
}
