package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCompactModuleGeometryTest {
    private data class Oval(val top: Float, val bottom: Float, val stroke: Float)
    private data class Capture(val box: JarvisShellLayout.Box, val firstGlyph: Oval)

    private fun render(width: Float, height: Float): Capture {
        val shell = JarvisShellLayout.create(width, height)
        val box = requireNotNull(shell[JarvisShellLayout.Slot.COCKPIT_SECONDARY])
        val ovals = mutableListOf<Oval>()
        val painter = object : ContinuumPainter by JarvisPresentationRecordingPainter() {
            override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float,
                                    width: Float, fill: Fill, alpha: Float) {
                ovals += Oval(top, bottom, width)
            }
        }
        JarvisCockpitGridContent.draw(painter, JarvisShellLayout.Slot.COCKPIT_SECONDARY,
            box, shell.grid, ContinuumDashboardState(), 0L, shell)
        return Capture(box, ovals.first())
    }

    @Test fun compactDatabaseStrokeStaysInsideItsRow() {
        val (box, glyph) = render(412f, 915f)
        val rowTop = box.top + 4f
        val rowBottom = rowTop + (box.height - 8f) / 5f
        assertTrue("Glyph stroke stays below row edge", glyph.top - glyph.stroke / 2f >= rowTop)
        assertTrue("Glyph stroke stays above divider", glyph.bottom + glyph.stroke / 2f <= rowBottom)
    }

    @Test fun innerDisplayRetainsOriginalIconDetail() {
        val (_, glyph) = render(884f, 760f)
        assertEquals("Unconstrained icons retain stroke", 18f / 13f, glyph.stroke, .0001f)
        assertEquals("Database ellipse retains height", 3.75f, glyph.bottom - glyph.top, .0001f)
    }
}
