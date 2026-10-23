package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The system-activity ridge shows only measured values: one point per sample, mesh up to the drawn curve. */
class RidgeChartTest {
    private val l = 10f; private val t = 34f; private val r = 310f; private val b = 73f

    @Test fun everySamplePointIsAMeasuredValue() {
        val series = listOf(3f, 9f, 4f, 12f, 6f, 0f, 8f)
        val p = RecordingPainter()
        WaveChart.drawRidge(p, l, t, r, b, series)
        val cores = p.circles.filter { kotlin.math.abs(it.third - .95f) < 1e-3f }
        assertEquals(series.size, cores.size)
        for ((i, c) in cores.withIndex()) {
            assertEquals(l + (r - l) * i / (series.size - 1), c.first, 1e-3f)
            assertEquals(b - (b - t) * .95f * series[i] / 12f, c.second, 1e-3f)
        }
        assertFalse("light mode is switched off again", p.lightMode)
    }

    @Test fun meshLinesEndOnTheDrawnCurveAndNeverAboveTheHighestSample() {
        val series = listOf(2f, 7f, 5f, 10f)
        val p = RecordingPainter()
        WaveChart.drawRidge(p, l, t, r, b, series)
        val mesh = p.lines.filter { it[0] == it[2] && it[1] == b && it[3] < b }
        assertTrue(mesh.size > 40)
        val top = b - (b - t) * .95f
        for (m in mesh) assertTrue("mesh line ends inside the chart (${m[3]})", m[3] >= top - 1e-3f)
    }

    @Test fun withoutMeasurementsOnlyTheAxisIsDrawn() {
        for (series in listOf(emptyList(), listOf(5f), listOf(0f, 0f, 0f))) {
            val p = RecordingPainter()
            WaveChart.drawRidge(p, l, t, r, b, series)
            assertEquals(1, p.lines.size)
            assertTrue(p.circles.isEmpty() && p.paths == 0)
        }
    }

    private class RecordingPainter : ContinuumPainter {
        val lines = ArrayList<FloatArray>()
        val circles = ArrayList<Triple<Float, Float, Float>>()
        var paths = 0
        var lightMode = false
        override fun setAdditive(on: Boolean) { lightMode = on }
        override fun save() {}
        override fun restore() {}
        override fun translate(dx: Float, dy: Float) {}
        override fun scale(sx: Float, sy: Float, px: Float, py: Float) {}
        override fun rotate(degrees: Float, px: Float, py: Float) {}
        override fun clipRect(left: Float, top: Float, right: Float, bottom: Float) {}
        override fun clipRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {}
        override fun fillRect(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {}
        override fun fillRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Fill, alpha: Float) {}
        override fun strokeRoundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, width: Float, fill: Fill, alpha: Float) {}
        override fun fillOval(left: Float, top: Float, right: Float, bottom: Float, fill: Fill, alpha: Float) {
            circles += Triple((left + right) / 2f, (top + bottom) / 2f, (right - left) / 2f)
        }
        override fun strokeOval(left: Float, top: Float, right: Float, bottom: Float, width: Float, fill: Fill, alpha: Float) {}
        override fun strokeArc(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float,
                               width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {}
        override fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) {
            if (width < 1f || lines.isEmpty()) lines += floatArrayOf(x0, y0, x1, y1)
        }
        override fun fillPath(path: VectorPath, fill: Fill, alpha: Float) { paths++ }
        override fun strokePath(path: VectorPath, width: Float, fill: Fill, alpha: Float, roundCap: Boolean) { paths++ }
        override fun drawText(text: String, x: Float, y: Float, style: TextStyle, fill: Fill, align: TextAlign, alpha: Float) {}
        override fun measureText(text: String, style: TextStyle): Float = 0f
        override fun capHeight(style: TextStyle): Float = 0f
    }
}
