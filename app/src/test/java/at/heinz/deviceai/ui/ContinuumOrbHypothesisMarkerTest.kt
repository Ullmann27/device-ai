package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Marker must mean an unconfirmed hypothesis, never a confirmed memory write. */
class ContinuumOrbHypothesisMarkerTest {
    private fun markerArcs(signals: ContinuumBrainOrbRenderer.Signals): List<Pair<Float, Float>> {
        val arcs = mutableListOf<Pair<Float, Float>>()
        val painter = Proxy.newProxyInstance(
            ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)
        ) { _, method, arguments ->
            if (method.name == "strokeArc" && arguments != null && arguments.size >= 9 &&
                (arguments[7] as? Fill.Solid)?.argb == JarvisDesign.Colors.violetRim &&
                (arguments[8] as? Float) == .65f
            ) {
                arcs += (arguments[4] as Float) to (arguments[5] as Float)
            }
            when (method.name) {
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
        ContinuumBrainOrbRenderer().draw(painter, 0f, 0f, 100f, signals)
        return arcs
    }

    @Test fun hypothesisMarkerHasTwelveSeparatedVioletArcs() {
        val arcs = markerArcs(ContinuumBrainOrbRenderer.Signals(hypothesis = true))
        assertEquals(12, arcs.size)
        assertEquals((0 until 12).map { it * 30f }, arcs.map { it.first })
        assertTrue(arcs.all { it.second == 16f })
    }

    @Test fun idleAndConfirmedActivityDoNotShowHypothesisMarker() {
        assertTrue(markerArcs(ContinuumBrainOrbRenderer.Signals()).isEmpty())
        assertTrue(markerArcs(ContinuumBrainOrbRenderer.Signals(pulse = 1f, processing = true)).isEmpty())
        assertEquals(12, markerArcs(ContinuumBrainOrbRenderer.Signals(
            pulse = 1f, processing = true, hypothesis = true
        )).size)
    }
}
