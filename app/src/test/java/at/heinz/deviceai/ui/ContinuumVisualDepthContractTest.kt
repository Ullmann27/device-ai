package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visual-depth contracts for Code 52 Bot 06 (ref-d Bildwirkung).
 * Thickness comes from the cinema Z-slice and 14° cap; original orb guards stay intact.
 */
class ContinuumVisualDepthContractTest {

    @Test fun edgeThicknessIsProjectedFromCinemaZAndTiltCap() {
        val expected = 24f * sin(Math.toRadians(14.0)).toFloat()
        assertEquals(expected, JarvisVisualDepth.edgeThicknessDp(), 1e-5f)
        assertEquals(20f * sin(Math.toRadians(14.0)).toFloat(),
            JarvisVisualDepth.frontLiftDp(), 1e-5f)
        assertEquals(-24f, JarvisVisualDepth.REAR_GLASS_Z_DP, 0f)
        assertEquals(0f, JarvisVisualDepth.CONTENT_Z_DP, 0f)
        assertEquals(20f, JarvisVisualDepth.FRONT_BRACKET_Z_DP, 0f)
        assertTrue(JarvisVisualDepth.edgeThicknessDp(20f) <=
            JarvisVisualDepth.edgeThicknessDp(HollywoodFuiConstruct.ENTRANCE_TILT_DEGREES) + 1e-5f)
        assertTrue(JarvisVisualDepth.edgeThicknessDp(0f) == 0f)
    }

    @Test fun glassSurfacePaintsRearPlateShadowSlabsAndSharpRim() {
        val box = JarvisShellLayout.Box(10f, 12f, 220f, 110f)
        val first = record { painter ->
            JarvisReferenceHud.surface(painter, box, 14f,
                JarvisDesign.Colors.cyan, JarvisDesign.Colors.violetRim,
                JarvisReferenceHud.Interaction.NORMAL, decorativeRibbons = true)
        }
        val second = record { painter ->
            JarvisReferenceHud.surface(painter, box, 14f,
                JarvisDesign.Colors.cyan, JarvisDesign.Colors.violetRim,
                JarvisReferenceHud.Interaction.NORMAL, decorativeRibbons = true)
        }
        first.assertFiniteAndBalanced()
        assertTrue("shadow + rear plate + body", first.fillRoundRects >= 3)
        assertTrue(
            "rear plate or shadow sits on the plane below the control box",
            first.fillRoundRectBottoms.any { it > box.bottom + 1f }
        )
        assertTrue("visible glass edge slabs", first.fillRects >= 3)
        assertTrue("outer glow behind the rim", first.strokeRoundRects.any { it >= 10f })
        assertTrue("sharp rim on top of glow", first.strokeRoundRects.any { it in 0.8f..1.8f })
        assertTrue("shader reuse", second.gradients.all { it in first.gradients })
    }

    @Test fun backgroundHasFarMidAndNearDepthLayers() {
        val rec = record { painter ->
            JarvisShellRenderer.background(painter, 884f, 760f)
        }
        rec.assertFiniteAndBalanced()
        assertTrue("base gradient", rec.fillRects >= 1)
        assertTrue("far nebula washes", rec.fillCircleRadii.count { it > 150f } >= 3)
        assertTrue("far out-of-focus bokeh", rec.fillCircleRadii.any { it in 20f..50f })
        assertTrue("mid bokeh", rec.fillCircleRadii.any { it in 8f..16f })
        assertTrue("near dust stays sharp", rec.fillCircleRadii.any { it < 2f })
    }

    @Test fun originalOrbFillsTheHeroColumnWithoutLeavingIt() {
        val header = JarvisShellLayout.Box(100f, 10f, 300f, 60f)
        val orb = JarvisShellLayout.Box(100f, 64f, 300f, 210f)
        val column = ContinuumBrainOrbRenderer.columnFor(orb, header)
        assertEquals(10f, column.top, 0f)
        assertEquals(210f, column.bottom, 0f)
        val place = ContinuumBrainOrbRenderer.placementIn(column)
        val slotOnly = ContinuumBrainOrbRenderer.radiusFor(orb.width, orb.height)
        assertTrue("hero column is larger than the leftover orb box", place.radius > slotOnly)
        assertTrue(place.cx > column.left && place.cx < column.right)
        assertTrue(place.cy - place.radius >= column.top - 0.51f)
        assertTrue(place.cy + 1.44f * place.radius <= column.bottom + 0.51f)
        val unchanged = ContinuumBrainOrbRenderer.columnFor(orb, JarvisShellLayout.Box(0f, 0f, 40f, 20f))
        assertEquals(orb.left, unchanged.left, 0f)
        assertEquals(orb.top, unchanged.top, 0f)
    }

    @Test fun vacantPanelRecedesAndWorkStagePaintsARing() {
        val box = JarvisShellLayout.Box(20f, 40f, 300f, 220f)
        val recede = record { JarvisVisualDepth.recedeVacantPanel(it, box) }
        recede.assertFiniteAndBalanced()
        assertTrue(recede.fillRoundRects >= 1)
        val stage = record { JarvisVisualDepth.paintWorkStage(it, box) }
        stage.assertFiniteAndBalanced()
        assertTrue("ring platform", stage.strokeOvals >= 3)
    }

    @Test fun orbKeepsOriginalGuardsAndPaintsInnerVolume() {
        val rec = record { painter ->
            ContinuumBrainOrbRenderer().draw(
                painter, 200f, 140f, 90f,
                ContinuumBrainOrbRenderer.Signals(phase = .4f, pulse = .5f, recall = .3f,
                    hypothesis = true, particles = 70, orbitSegments = 64)
            )
        }
        rec.assertFiniteAndBalanced()
        assertTrue("fresnel / glass rings", rec.strokeCircles >= 5)
        assertTrue("volume, dust, nodes", rec.fillCircles >= 20)
        assertTrue("pedestal ellipses", rec.strokeOvals >= 6)
        assertEquals("unique luminous core at 0.78 r", 1, rec.fillCirclesOf(90f * .78f))
        assertTrue("inner glass cavity", rec.fillCirclesOf(90f * .88f) >= 1)
        val guarded = record { painter ->
            ContinuumBrainOrbRenderer().draw(
                painter, 0f, 0f, 80f,
                ContinuumBrainOrbRenderer.Signals(phase = Float.POSITIVE_INFINITY, pulse = Float.NaN)
            )
        }
        guarded.assertFiniteAndBalanced()
        assertTrue(guarded.primitives > 50)
        assertTrue("no extra 0.78 r core under bad phase", guarded.fillCirclesOf(80f * .78f) == 1)
    }

    private class Rec {
        var depth = 0
        var primitives = 0
        var fillCircles = 0
        var strokeCircles = 0
        var strokeOvals = 0
        var fillRoundRects = 0
        var fillRects = 0
        val strokeRoundRects = ArrayList<Float>()
        val fillCircleRadii = ArrayList<Float>()
        val fillRoundRectBottoms = ArrayList<Float>()
        val gradients = HashSet<Fill>()

        fun fillCirclesOf(radius: Float) = fillCircleRadii.count { abs(it - radius) < 1e-4f }

        fun assertFiniteAndBalanced() {
            assertEquals("painter save/restore", 0, depth)
            assertTrue(primitives > 0)
        }
    }

    private fun record(block: (ContinuumPainter) -> Unit): Rec {
        val rec = Rec()
        val painter = Proxy.newProxyInstance(
            ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)
        ) { _, method, args ->
            args.orEmpty().filterIsInstance<Float>().forEach {
                assertTrue("non-finite ${method.name} $it", it.isFinite())
            }
            args.orEmpty().filterIsInstance<Fill>().forEach { fill ->
                if (fill is Fill.Linear || fill is Fill.Radial) rec.gradients += fill
                if (fill is Fill.Radial) assertTrue(fill.radius > 0f)
            }
            when (method.name) {
                "save", "saveAlpha" -> rec.depth++
                "restore" -> {
                    assertTrue(rec.depth > 0); rec.depth--
                }
                "fillCircle" -> {
                    rec.fillCircles++
                    rec.primitives++
                    rec.fillCircleRadii += args!![2] as Float
                    assertTrue((args[2] as Float) > 0f)
                }
                "strokeCircle" -> { rec.strokeCircles++; rec.primitives++ }
                "strokeOval" -> { rec.strokeOvals++; rec.primitives++ }
                "fillRoundRect" -> {
                    rec.fillRoundRects++
                    rec.primitives++
                    rec.fillRoundRectBottoms += args!![3] as Float
                    assertTrue((args[4] as Float) > 0f)
                }
                "fillRect" -> { rec.fillRects++; rec.primitives++ }
                "strokeRoundRect" -> {
                    rec.primitives++
                    rec.strokeRoundRects += args!![5] as Float
                }
                else -> if (method.name.startsWith("fill") || method.name.startsWith("stroke") ||
                    method.name == "drawLine") rec.primitives++
            }
            when (method.name) {
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
        block(painter)
        return rec
    }
}
