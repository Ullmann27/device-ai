package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.ContinuumPainter
import at.heinz.deviceai.ui.Fill
import at.heinz.deviceai.ui.TextAlign
import at.heinz.deviceai.ui.TextStyle
import at.heinz.deviceai.ui.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Video3FileTunnelTest {
    @Test fun idleHasNoPaintOrFrameRequest() {
        val tunnel = Video3FileTunnel()
        val p = RecordingPainter()
        assertFalse(tunnel.isActive)
        assertFalse(tunnel.draw(p, 884f, 760f, 1_000L))
        assertEquals(0, p.draws)
    }

    @Test fun playbackEndsWithoutAnAdditionalEvent() {
        val tunnel = Video3FileTunnel()
        val p = RecordingPainter()
        assertTrue(tunnel.start(100L))
        assertTrue(tunnel.draw(p, 884f, 760f, 1_600L))
        assertTrue(p.cards > 0)
        assertTrue(tunnel.draw(p, 884f, 760f, 3_099L))
        val before = p.draws
        assertFalse(tunnel.draw(p, 884f, 760f, 3_100L))
        assertFalse(tunnel.isActive)
        assertFalse(tunnel.draw(p, 884f, 760f, 20_000L))
        assertEquals(before, p.draws)
    }

    @Test fun repeatedEventsDoNotExtendTheDeadline() {
        val tunnel = Video3FileTunnel()
        assertTrue(tunnel.start(0L))
        for (time in 1L..2_999L step 37L) assertFalse(tunnel.start(time))
        assertFalse(tunnel.draw(RecordingPainter(), 884f, 760f, 3_000L))
        assertTrue(tunnel.start(4_000L))
        assertTrue(tunnel.draw(RecordingPainter(), 884f, 760f, 4_500L))
    }

    @Test fun stopDiscardsInsteadOfPausing() {
        val tunnel = Video3FileTunnel()
        val p = RecordingPainter()
        tunnel.start(0L)
        tunnel.draw(p, 884f, 760f, 1_200L)
        tunnel.clear()
        tunnel.clear()
        val before = p.draws
        assertFalse(tunnel.draw(p, 884f, 760f, 1_400L))
        assertFalse(tunnel.isActive)
        assertEquals(before, p.draws)
        assertTrue(tunnel.start(2_000L))
    }

    @Test fun hiddenSurfaceDiscardsWithoutReplay() {
        val tunnel = Video3FileTunnel()
        val p = RecordingPainter()
        tunnel.start(0L)
        tunnel.draw(p, 884f, 760f, 1_200L)
        val before = p.draws
        assertFalse(tunnel.draw(p, 884f, 760f, 1_250L, visible = false))
        assertFalse(tunnel.isActive)
        assertFalse(tunnel.draw(p, 884f, 760f, 1_300L, visible = true))
        assertEquals(before, p.draws)
    }

    @Test fun disabledOrInvalidMotionNeverDraws() {
        for (scale in floatArrayOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val tunnel = Video3FileTunnel()
            val p = RecordingPainter()
            assertFalse(tunnel.start(0L, scale))
            assertFalse(tunnel.isActive)
            assertTrue(tunnel.start(100L))
            assertFalse(tunnel.draw(p, 884f, 760f, 300L, animatorScale = scale))
            assertFalse(tunnel.isActive)
            assertFalse(tunnel.draw(p, 884f, 760f, 400L))
            assertEquals(0, p.draws)
        }
    }

    @Test fun positiveAnimatorScaleCannotExceedTheTimeBudget() {
        for (scale in floatArrayOf(.5f, 1f, 10f, Float.MAX_VALUE)) {
            val tunnel = Video3FileTunnel()
            assertTrue(tunnel.start(0L, scale))
            val end = if (scale < 1f) 1_500L else 3_000L
            assertTrue(tunnel.draw(RecordingPainter(), 884f, 760f, end - 1L, scale))
            assertFalse(tunnel.draw(RecordingPainter(), 884f, 760f, end, scale))
        }
    }

    @Test fun invalidLayoutDiscardsInsteadOfDelayingPlayback() {
        for (size in floatArrayOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.MAX_VALUE)) {
            for (badWidth in listOf(true, false)) {
                val tunnel = Video3FileTunnel()
                val p = RecordingPainter()
                tunnel.start(0L)
                assertFalse(tunnel.draw(p, if (badWidth) size else 884f, if (badWidth) 760f else size, 1_000L))
                assertFalse(tunnel.isActive)
                assertFalse(tunnel.draw(p, 884f, 760f, 1_100L))
                assertEquals(0, p.draws)
            }
        }
    }

    @Test fun aClockRewindDiscardsTheSequence() {
        val tunnel = Video3FileTunnel()
        val p = RecordingPainter()
        assertFalse(tunnel.start(-1L))
        tunnel.start(1_000L)
        tunnel.draw(p, 884f, 760f, 2_000L)
        val before = p.draws
        assertFalse(tunnel.draw(p, 884f, 760f, 1_999L))
        assertFalse(tunnel.isActive)
        assertEquals(before, p.draws)
        assertTrue(tunnel.start(Long.MAX_VALUE - 2_000L))
        assertTrue(tunnel.draw(p, 884f, 760f, Long.MAX_VALUE - 1L))
        assertFalse(tunnel.draw(p, 884f, 760f, Long.MIN_VALUE))
    }

    @Test fun tunnelMovesDeterministicallyAndNeverInventsData() {
        val a = frameAt(1_200L)
        val b = frameAt(1_200L)
        val later = frameAt(1_400L)
        assertEquals(a.geometry, b.geometry)
        assertNotEquals(a.geometry, later.geometry)
        assertTrue(a.cards > 0)
        assertTrue(a.segmentBatches.contains(72 * 4))
        assertTrue(a.segmentBatches.contains(12))
        assertEquals(0, a.fullRects)
        assertEquals(0, a.texts)
    }

    @Test fun everyFrameHasBoundedDrawsAndRestoresPainterState() {
        val tunnel = Video3FileTunnel()
        tunnel.start(0L)
        for (time in 0L until 3_000L step 17L) {
            val p = RecordingPainter()
            assertTrue(tunnel.draw(p, 884f, 760f, time))
            assertTrue("draw budget: ${p.draws}", p.draws <= 320)
            assertEquals(0, p.depth)
            assertFalse(p.additiveEnabled)
            assertEquals(0, p.fullRects)
            assertEquals(0, p.texts)
        }
        assertFalse(tunnel.draw(RecordingPainter(), 884f, 760f, 3_000L))
    }

    @Test fun disablingGlowKeepsTheDocumentFlight() {
        val bright = frameAt(1_200L)
        val lean = frameAt(1_200L, glow = false)
        assertEquals(bright.cards, lean.cards)
        assertTrue(lean.cards > 0)
        assertTrue(lean.draws < bright.draws)
    }

    private fun frameAt(time: Long, glow: Boolean = true): RecordingPainter {
        val tunnel = Video3FileTunnel()
        val p = RecordingPainter()
        assertTrue(tunnel.start(0L))
        assertTrue(tunnel.draw(p, 884f, 760f, time, glow = glow))
        return p
    }

    private class RecordingPainter : ContinuumPainter {
        val geometry = mutableListOf<Float>()
        val segmentBatches = mutableListOf<Int>()
        var draws = 0
        var cards = 0
        var fullRects = 0
        var texts = 0
        var depth = 0
        var additiveEnabled = false
        private fun draw(alpha: Float, vararg values: Float) {
            assertTrue("alpha $alpha", alpha.isFinite() && alpha in 0f..1f)
            values.forEach { assertTrue("coordinate $it", it.isFinite()) }
            geometry.addAll(values.toList())
            geometry += alpha
            draws++
        }
        override fun save() { depth++ }
        override fun restore() { depth--; assertTrue(depth >= 0) }
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float, px: Float, py: Float) = Unit
        override fun rotate(degrees: Float, px: Float, py: Float) = Unit
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) = Unit
        override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) = Unit
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {
            fullRects++
            draw(alpha, left, top, right, bottom)
        }
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) {
            cards++
            draw(alpha, left, top, right, bottom, radius)
        }
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) =
            draw(alpha, left, top, right, bottom, radius, width)
        override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) = draw(alpha, left, top, right, bottom)
        override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) = draw(alpha, left, top, right, bottom, width)
        override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) =
            draw(alpha, left, top, right, bottom, startDegrees, sweepDegrees, width)
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) = draw(alpha, x0, y0, x1, y1, width)
        override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) = draw(alpha)
        override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) = draw(alpha, width)
        override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) {
            texts++
            draw(alpha, x, y)
        }
        override fun measureText(text: String, style: TextStyle) = text.length * style.size * .55f
        override fun capHeight(style: TextStyle) = style.size * .7f
        override fun setAdditive(on: Boolean) { additiveEnabled = on }
        override fun strokeSegments(points: FloatArray, count: Int, width: Float, fill: Fill, alpha: Float) {
            assertTrue(count <= points.size && count % 4 == 0)
            segmentBatches += count
            draw(alpha, *points.copyOf(count), width)
        }
    }
}
