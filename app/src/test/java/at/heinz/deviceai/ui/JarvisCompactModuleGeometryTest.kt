package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCompactModuleGeometryTest {
    private data class Oval(val top: Float, val bottom: Float, val stroke: Float)
    private data class Capture(val box: JarvisShellLayout.Box, val firstGlyph: Oval, val extents: List<Oval>)

    private fun render(width: Float, height: Float): Capture {
        val shell = JarvisShellLayout.create(width, height)
        val box = requireNotNull(shell[JarvisShellLayout.Slot.COCKPIT_SECONDARY])
        val ovals = mutableListOf<Oval>()
        val extents = mutableListOf<Oval>()
        var drawingFirstGlyph = false
        var glyphEnded = false
        val painter = object : ContinuumPainter by JarvisPresentationRecordingPainter() {
            override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float,
                                    width: Float, fill: Fill, alpha: Float) {
                ovals += Oval(top, bottom, width)
                if (!glyphEnded) {
                    drawingFirstGlyph = true
                    extents += Oval(top, bottom, width)
                }
            }
            override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float,
                                   startDegrees: Float, sweepDegrees: Float, width: Float,
                                   fill: Fill, alpha: Float, roundCap: Boolean) {
                if (drawingFirstGlyph) extents += Oval(top, bottom, width)
            }
            override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float,
                                  width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {
                if (drawingFirstGlyph) extents += Oval(minOf(y0, y1), maxOf(y0, y1), width)
            }
            override fun drawText(text: String, x: Float, y: Float, style: TextStyle,
                                  fill: Fill, align: TextAlign, alpha: Float) {
                if (drawingFirstGlyph) { drawingFirstGlyph = false; glyphEnded = true }
            }
        }
        JarvisCockpitGridContent.draw(painter, JarvisShellLayout.Slot.COCKPIT_SECONDARY,
            box, shell.grid, ContinuumDashboardState(), 0L, shell)
        return Capture(box, ovals.first(), extents)
    }

    @Test fun compactDatabaseStrokeStaysInsideItsRow() {
        val (box, glyph, extents) = render(412f, 915f)
        val rowTop = box.top + 4f
        val rowBottom = rowTop + (box.height - 8f) / 5f
        assertTrue("Glyph stroke stays below row edge", glyph.top - glyph.stroke / 2f >= rowTop)
        assertEquals("Complete database strokes", 5, extents.size)
        assertTrue("Glyph stroke stays above divider", extents.maxOf { it.bottom + it.stroke / 2f } <= rowBottom)
    }

    @Test fun innerDisplayRetainsOriginalIconDetail() {
        val (_, glyph) = render(884f, 760f)
        assertEquals("Unconstrained icons retain stroke", 18f / 13f, glyph.stroke, .0001f)
        assertEquals("Database ellipse retains height", 3.75f, glyph.bottom - glyph.top, .0001f)
    }
}
