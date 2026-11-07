package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import java.util.Collections
import java.util.IdentityHashMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisReferenceSurfaceTest {
    @Test fun featureIconsLeaveThePainterReadyForText() {
        for (tile in JarvisTiles.reference) {
            for (size in listOf(20f, 34f, 48f)) {
                for (state in JarvisControlState.values()) {
                    val probe = PainterProbe()
                    JarvisTiles.drawFeatureIcon(probe.painter, tile.copy(controlState = state),
                        120f, 90f, size)
                    probe.assertBalanced()
                    assertTrue(probe.primitives > 0)
                    assertEquals(0, probe.rasters)
                    assertEquals(0, probe.texts)
                }
            }
        }
    }

    @Test fun invalidIconBoundsLeaveThePainterUntouched() {
        val tile = JarvisTiles.reference.first()
        val cases = listOf(
            Triple(0f, 0f, 0f), Triple(0f, 0f, -1f), Triple(0f, 0f, Float.NaN),
            Triple(0f, 0f, Float.POSITIVE_INFINITY), Triple(Float.NaN, 0f, 32f),
            Triple(0f, Float.NEGATIVE_INFINITY, 32f))
        for ((x, y, size) in cases) {
            val probe = PainterProbe()
            JarvisTiles.drawFeatureIcon(probe.painter, tile, x, y, size)
            probe.assertBalanced()
            assertEquals(0, probe.primitives)
        }
    }

    @Test fun glassSurfacesBalancePaintStateAndReuseShaders() {
        val boxes = listOf(
            JarvisShellLayout.Box(8f, 8f, 144f, 62f),
            JarvisShellLayout.Box(8f, 8f, 156f, 180f),
            JarvisShellLayout.Box(8f, 8f, 756f, 64f))
        for (box in boxes) {
            for (state in JarvisReferenceHud.Interaction.values()) {
                val first = PainterProbe()
                val second = PainterProbe()
                for (probe in listOf(first, second)) {
                    JarvisReferenceHud.surface(probe.painter, box, 12f,
                        JarvisDesign.Colors.cyan, JarvisDesign.Colors.violetRim,
                        state, decorativeRibbons = true)
                    probe.assertBalanced()
                    assertTrue(probe.primitives > 0)
                    assertEquals(0, probe.rasters)
                    assertEquals(0, probe.texts)
                }
                assertTrue(first.gradients.isNotEmpty())
                assertEquals(first.gradients.size, second.gradients.size)
                assertTrue(second.gradients.all { it in first.gradients })
            }
        }
    }

    private class PainterProbe {
        private var depth = 0
        private var additive = false
        var primitives = 0
        var rasters = 0
        var texts = 0
        val gradients: MutableSet<Fill> = Collections.newSetFromMap(IdentityHashMap<Fill, Boolean>())

        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            args.orEmpty().filterIsInstance<Float>().forEach {
                assertTrue("Non-finite ${method.name}", it.isFinite())
            }
            args.orEmpty().filterIsInstance<Fill>().forEach { fill ->
                if (fill is Fill.Linear || fill is Fill.Radial) gradients += fill
                if (fill is Fill.Radial) assertTrue(fill.radius > 0f)
            }
            when (method.name) {
                "save", "saveAlpha" -> depth++
                "restore" -> { assertTrue("Unbalanced restore", depth > 0); depth-- }
                "setAdditive" -> additive = args!![0] as Boolean
                "drawRaster" -> rasters++
                "drawText" -> texts++
            }
            if (method.name.startsWith("fill") || method.name.startsWith("stroke") ||
                method.name == "drawLine") primitives++
            when (method.name) {
                "fillCircle", "strokeCircle" -> assertTrue((args!![2] as Float) > 0f)
                "fillRoundRect", "strokeRoundRect", "clipRoundRect" ->
                    assertTrue((args!![4] as Float) > 0f)
            }
            when (method.name) {
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter

        fun assertBalanced() {
            assertEquals(0, depth)
            assertFalse(additive)
        }
    }
}
