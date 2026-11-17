package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ensures reused projection buffers do not corrupt the visible Brain orb line geometry. */
class ContinuumBrainOrbProjectionReuseTest {
    private val renderer = ContinuumBrainOrbRenderer()

    private fun frame(phase: Float, segments: Int): List<List<Float>> {
        val lines = ArrayList<List<Float>>()
        val painter = Proxy.newProxyInstance(
            ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)
        ) { _, method, arguments ->
            if (method.name == "drawLine" && arguments != null) {
                val geometry = arguments.filterIsInstance<Float>()
                assertTrue("Each line has endpoints, width and alpha", geometry.size >= 6)
                assertTrue("Non-finite line geometry", geometry.all { it.isFinite() })
                lines += geometry
            }
            when (method.name) {
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
        renderer.draw(
            painter, 206f, 117f, 101f,
            ContinuumBrainOrbRenderer.Signals(phase = phase, pulse = .6f, recall = .4f,
                hypothesis = true, orbitSegments = segments)
        )
        return lines
    }

    @Test fun repeatedFramesKeepGeometryAndPhaseChangesRemainVisible() {
        for (segments in listOf(32, 96, 128)) {
            val first = frame(.4f, segments)
            assertTrue("Visible projected lines", first.size > 100)
            assertEquals("Scratch buffers must not leak between frames", first, frame(.4f, segments))
            val advanced = frame(2.1f, segments)
            assertFalse("A changing phase must still move geometry", first == advanced)
            assertEquals("Revisiting the original phase restores the geometry", first, frame(.4f, segments))
        }
    }
}
