package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.ContinuumIcons.Icon
import java.lang.reflect.Proxy
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisTileAmbientNeonTest {
    @Test fun slowColourCycleIsStableAndContinuous() {
        assertEquals(0f, JarvisTiles.ambientPhase("memory", 0L), 0f)
        assertEquals(0f, JarvisTiles.ambientPhase("memory", -1L), 0f)
        for (name in listOf("memory", "agents", "chat", "system")) {
            val samples = (1..1400).map { JarvisTiles.ambientPhase(name, (it * 10L)) }
            assertTrue(samples.all { it.isFinite() && it in 0f..1f })
            assertTrue(samples.maxOrNull()!! - samples.minOrNull()!! > .9f)
            assertTrue(samples.zipWithNext().all { (a, b) -> abs(a - b) < .015f })
            assertTrue(abs(JarvisTiles.ambientPhase(name, 13999L) -
                JarvisTiles.ambientPhase(name, 14000L)) < .01f)
            assertEquals(JarvisTiles.ambientPhase(name, 1000L),
                JarvisTiles.ambientPhase(name, 15000L), 1e-6f)
        }
    }

    private fun strokeCount(tile: JarvisTile, timeMs: Long): Int {
        var strokes = 0
        val canvas = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, _ ->
            when (method.name) {
                "strokeRoundRect" -> { strokes++; null }
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
        JarvisTiles.drawSurface(canvas, JarvisShellLayout.Box(0f, 0f, 166f, 51f),
            tile, highlighted = false, timeMs = timeMs)
        return strokes
    }

    @Test fun livingRimAddsTwoBoundedDrawsAndDisabledTileHasNoAmbientEffect() {
        val normal = JarvisTile("memory", "Gedächtnis", Icon.DATABASE)
        val disabled = normal.copy(controlState = JarvisControlState.DISABLED)
        assertEquals(strokeCount(normal, 0L) + 2, strokeCount(normal, 1000L))
        assertEquals(strokeCount(disabled, 0L), strokeCount(disabled, 1000L))
    }
}
