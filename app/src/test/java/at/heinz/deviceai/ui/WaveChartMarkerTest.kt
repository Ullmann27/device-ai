package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The traveling marker of the measured learning wave must sit on the drawn curve and show no invented values. */
class WaveChartMarkerTest {
    private val series = listOf(0f, 3f, 1f, 7f, 7f, 2f, 9f, 4f)
    private val l = 14f; private val t = 72f; private val r = 260f; private val b = 112f
    private val maxV = series.max()
    private fun py(i: Int) = b - (b - t) * .95f * (series[i] / maxV)

    @Test fun markerHitsEveryRealSampleExactly() {
        for (i in series.indices) {
            val (x, y) = WaveChart.markerOnCurve(l, t, r, b, series, maxV, i / (series.size - 1f))
            assertEquals(l + (r - l) * i / (series.size - 1f), x, 1e-3f)
            assertEquals(py(i), y, 1e-3f)
        }
    }

    @Test fun markerStaysOnTheDrawnBezierBetweenSamples() {
        // Dense points of the drawn front curve: cubic from (x0, y0) via (mid, y0), (mid, y1) to (x1, y1).
        val curve = ArrayList<Pair<Float, Float>>()
        for (i in 1 until series.size) {
            val x0 = l + (r - l) * (i - 1) / (series.size - 1f); val x1 = l + (r - l) * i / (series.size - 1f)
            val mid = (x0 + x1) / 2f
            for (k in 0..400) {
                val u = k / 400f; val v = 1f - u
                curve += (v * v * v * x0 + 3 * v * v * u * mid + 3 * v * u * u * mid + u * u * u * x1) to
                    (v * v * v * py(i - 1) + 3 * v * v * u * py(i - 1) + 3 * v * u * u * py(i) + u * u * u * py(i))
            }
        }
        for (k in 0..997) {
            val phase = k / 997f
            val (x, y) = WaveChart.markerOnCurve(l, t, r, b, series, maxV, phase)
            val nearest = curve.minByOrNull { abs(it.first - x) }!!
            assertTrue("phase $phase: marker y $y, curve y ${nearest.second}", abs(nearest.second - y) < .2f)
            val seg = (phase * (series.size - 1)).toInt().coerceAtMost(series.size - 2)
            val lo = minOf(py(seg), py(seg + 1)) - 1e-3f; val hi = maxOf(py(seg), py(seg + 1)) + 1e-3f
            assertTrue("phase $phase: no value outside the two neighbouring samples", y in lo..hi)
        }
    }
}
