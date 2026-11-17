package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumOrbNonFiniteSignalsTest {
    @Test fun invalidOrExcessiveSignalsCannotReachPainterCoordinatesOrAlpha() {
        val inputs = listOf(
            ContinuumBrainOrbRenderer.Signals(
                pulse = Float.NaN, recall = Float.POSITIVE_INFINITY, phase = Float.NaN,
                processing = true
            ),
            ContinuumBrainOrbRenderer.Signals(
                pulse = Float.POSITIVE_INFINITY, recall = Float.NEGATIVE_INFINITY,
                phase = Float.POSITIVE_INFINITY
            ),
            ContinuumBrainOrbRenderer.Signals(pulse = -9f, recall = 10f, phase = -20f),
            ContinuumBrainOrbRenderer.Signals(pulse = .7f, recall = .9f,
                phase = Float.MAX_VALUE, processing = true),
            ContinuumBrainOrbRenderer.Signals(pulse = 1f, phase = -Float.MAX_VALUE),
            ContinuumBrainOrbRenderer.Signals(phase = 1_000_000f)
        )
        for (signals in inputs) {
            var primitives = 0
            val p = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
                arrayOf(ContinuumPainter::class.java)) { _, method, arguments ->
                if (method.name !in setOf("setAdditive", "save", "restore") && arguments != null) {
                    for (number in arguments.filterIsInstance<Float>()) {
                        assertTrue("non-finite " + method.name + " parameter " + number, number.isFinite())
                    }
                    primitives++
                }
                when (method.name) {
                    "measureText", "capHeight" -> 0f
                    "drawRaster" -> false
                    else -> null
                }
            } as ContinuumPainter
            ContinuumBrainOrbRenderer().draw(p, 0f, 0f, 100f, signals)
            assertTrue("orb must still render", primitives > 50)
        }
    }
}
