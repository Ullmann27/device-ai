package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Slot
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner Fold7 report 06.11.2026: the white „Device AI“ brand sat right at the top screen edge. The three brand lines
 * have a fixed type size while the brand box scales with the screen, so on the Fold7 inner screen the centred block
 * was centred with almost no room above it. Every brand line keeps visible breathing room inside the brand box.
 */
class JarvisHeaderBrandFitTest {
    private class Tracking(private val measureFactor: Float = .5f) : ContinuumPainter {
        private data class T(val sx: Float, val sy: Float, val tx: Float, val ty: Float)
        private var cur = T(1f, 1f, 0f, 0f)
        private val stack = ArrayDeque<T>()
        val lines = mutableListOf<Triple<String, Float, Float>>() // text, top, baseline (screen dp)
        val rights = mutableMapOf<String, Float>() // text -> right edge (screen dp)
        override fun save() { stack.addLast(cur) }
        override fun restore() { cur = stack.removeLast() }
        override fun translate(dx: Float, dy: Float) { cur = cur.copy(tx = cur.tx + cur.sx * dx, ty = cur.ty + cur.sy * dy) }
        override fun scale(sx: Float, sy: Float, px: Float, py: Float) {
            translate(px, py); cur = cur.copy(sx = cur.sx * sx, sy = cur.sy * sy); translate(-px, -py)
        }
        override fun rotate(degrees: Float, px: Float, py: Float) {}
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}
        override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {}
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {}
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) {}
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) {}
        override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {}
        override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) {}
        override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
        override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) {}
        override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
        override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) {
            lines += Triple(text, cur.sy * (y - capHeight(style)) + cur.ty, cur.sy * y + cur.ty)
            rights[text] = cur.sx * (x + measureText(text, style)) + cur.tx
        }
        override fun measureText(text: String, style: TextStyle) = text.length * style.size * measureFactor
        override fun capHeight(style: TextStyle) = style.size * .7f
    }

    @Test fun brandLinesStayInsideTheBrandBox() {
        for ((w, h) in listOf(884f to 760f, 1000f to 700f, 1536f to 1024f)) {
            val grid = JarvisCockpitGrid.create(w, h)
            val brand = grid.areas.getValue(JarvisCockpitGrid.Area.BRAND)
            val p = Tracking()
            JarvisCockpitGridContent.draw(p, Slot.TOP_BAR, grid.areas.getValue(JarvisCockpitGrid.Area.HEADER), grid,
                ContinuumDashboardState(), 0L)
            for (prefix in listOf("Device", "Contin", "Deine")) {
                val (text, top, baseline) = p.lines.first { it.first.startsWith(prefix) }
                assertTrue("$text top $top needs breathing room below ${brand.top} at ${w}x$h", top >= brand.top + 5f)
                assertTrue("$text baseline $baseline below the brand box ${brand.bottom} at ${w}x$h", baseline <= brand.bottom)
            }
        }
    }

    /** Gitar review on #565: the text room must be measured from the scale pivot, or fitting text gets an ellipsis. */
    @Test fun scaledBrandKeepsTextThatFitsAndStaysInsideTheBox() {
        val grid = JarvisCockpitGrid.create(600f, 480f)
        val brand = grid.areas.getValue(JarvisCockpitGrid.Area.BRAND)
        val p = Tracking(measureFactor = .62f)
        JarvisCockpitGridContent.draw(p, Slot.TOP_BAR, grid.areas.getValue(JarvisCockpitGrid.Area.HEADER), grid,
            ContinuumDashboardState(), 0L)
        val texts = p.lines.map { it.first }
        for (full in listOf("Device AI", "Continuum", "Deine KI-Steuerzentrale")) {
            assertTrue("$full must not be shortened: $texts", full in texts)
            assertTrue("$full ends inside the brand box", p.rights.getValue(full) <= brand.right + .5f)
        }
    }

    /**
     * Host capture 06.11.2026: at 07:4x the header clock read „07:…“ – proportional Montserrat digits made the time
     * wider than its box and the generic ellipsis cut the minutes. A time is never shortened: every time of day is
     * drawn in full and stays inside the clock box.
     */
    @Test fun theClockShowsEveryTimeInFullAndStaysInItsBox() {
        val grid = JarvisCockpitGrid.create(884f, 760f)
        val clock = grid.areas.getValue(JarvisCockpitGrid.Area.DEVICE_STATUS)
        for ((hour, minute) in listOf(7 to 44, 20 to 48, 0 to 0, 11 to 11)) {
            val now = java.util.Calendar.getInstance().apply {
                set(2026, java.util.Calendar.NOVEMBER, 6, hour, minute, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val expected = "%02d:%02d".format(hour, minute)
            val p = Tracking(measureFactor = .62f)
            JarvisCockpitGridContent.draw(p, Slot.TOP_BAR, grid.areas.getValue(JarvisCockpitGrid.Area.HEADER), grid,
                ContinuumDashboardState(), now)
            val texts = p.lines.map { it.first }
            assertTrue("$expected must be drawn in full: $texts", expected in texts)
            assertTrue("$expected ends inside the clock box", p.rights.getValue(expected) <= clock.right + .5f)
            assertTrue("the date is not shortened either: $texts", texts.none { it.startsWith("Fr") && it.endsWith("…") })
        }
    }
}
