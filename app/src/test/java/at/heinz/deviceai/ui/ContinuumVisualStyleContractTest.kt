package at.heinz.deviceai.ui

import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visual-style contracts for Code 52 Bot 06.
 * Guards (#550 phase clamp, #594 buffer reuse, unique 0.78 r core) stay intact;
 * these checks only require that the sci-fi layers actually get painted.
 */
class ContinuumVisualStyleContractTest {

    @Test fun displayTypeUsesBundledOflExo2AndKeepsReadableText() {
        assertTrue(ContinuumFonts.assetPath(FontFamily.DISPLAY, 300).endsWith("Exo2-Light.ttf"))
        assertTrue(ContinuumFonts.assetPath(FontFamily.DISPLAY, 400).endsWith("Exo2-Regular.ttf"))
        assertTrue(ContinuumFonts.assetPath(FontFamily.DISPLAY, 500).endsWith("Exo2-Medium.ttf"))
        assertTrue(ContinuumFonts.assetPath(FontFamily.DISPLAY, 600).endsWith("Exo2-SemiBold.ttf"))
        assertTrue(ContinuumFonts.assetPath(FontFamily.TEXT, 400).endsWith("Roboto-Regular.ttf"))
        val root = File("app/src/main/assets")
        val assets = if (root.isDirectory) root else File("src/main/assets")
        for (path in ContinuumFonts.allAssets) {
            assertTrue("missing $path", File(assets, path).isFile)
        }
        assertTrue(File(assets, "fonts/OFL-Exo2.txt").isFile)
        assertTrue(
            File(assets, "fonts/OFL-Exo2.txt").readText().contains("SIL Open Font License")
        )
    }

    @Test fun brainOrbPaintsGlassCoreOrbitsPedestalAndStaysFinite() {
        val rec = record { painter ->
            ContinuumBrainOrbRenderer().draw(
                painter, 200f, 140f, 90f,
                ContinuumBrainOrbRenderer.Signals(phase = .4f, pulse = .5f, recall = .3f,
                    hypothesis = true, particles = 70, orbitSegments = 64)
            )
        }
        rec.assertFiniteAndBalanced()
        assertTrue("fresnel / glass rings", rec.strokeCircles >= 5)
        assertTrue("core, dust, nodes", rec.fillCircles >= 20)
        assertTrue("pedestal ellipses", rec.strokeOvals >= 6)
        assertTrue("orbits and neurons", rec.lines > 120)
        assertEquals("unique luminous core at 0.78 r", 1, rec.fillCirclesOf(90f * .78f))
        // Phase guard: huge / non-finite phase must not leak into primitives.
        val guarded = record { painter ->
            ContinuumBrainOrbRenderer().draw(
                painter, 0f, 0f, 80f,
                ContinuumBrainOrbRenderer.Signals(phase = Float.POSITIVE_INFINITY, pulse = Float.NaN)
            )
        }
        guarded.assertFiniteAndBalanced()
        assertTrue(guarded.primitives > 50)
    }

    @Test fun glassSurfaceHasOuterGlowSharpRimSheenAndReuse() {
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
        assertTrue("outer neon glow", first.strokeRoundRects.any { it >= 10f })
        assertTrue("sharp rim", first.strokeRoundRects.any { it in 0.8f..1.8f })
        assertTrue("glass body", first.fillRoundRects > 0)
        assertTrue("top sheen or inner wash", first.fillRects > 0)
        assertTrue("shader reuse", second.gradients.all { it in first.gradients })
    }

    @Test fun backgroundPaintsDeepSpaceNebula() {
        val rec = record { painter ->
            JarvisShellRenderer.background(painter, 884f, 760f)
        }
        rec.assertFiniteAndBalanced()
        assertTrue("base gradient", rec.fillRects >= 1)
        assertTrue(
            "large nebula washes",
            rec.fillCircleRadii.count { it > 150f } >= 3
        )
    }

    @Test fun dockButtonPaintsLayeredGlowAndPlatform() {
        val button = JarvisShellLayout.Box(40f, 20f, 140f, 88f)
        val rec = record { painter ->
            LuxuryHoloButtonRenderer.draw(painter, button, 90f, 30f,
                active = true, disabled = false, pressed = false)
        }
        rec.assertFiniteAndBalanced()
        assertTrue("outer glow", rec.strokeRoundRects.any { it >= 8f })
        assertTrue("glass body", rec.fillRoundRects >= 1)
        val platform = JarvisShellLayout.Box(20f, 400f, 800f, 470f)
        val floor = record { painter ->
            LuxuryHoloButtonRenderer.drawDockPlatform(painter, platform)
        }
        floor.assertFiniteAndBalanced()
        assertTrue(floor.primitives > 2)
    }

    private class Rec {
        var depth = 0
        var primitives = 0
        var lines = 0
        var fillCircles = 0
        var strokeCircles = 0
        var strokeOvals = 0
        var fillRoundRects = 0
        var fillRects = 0
        val strokeRoundRects = ArrayList<Float>()
        val fillCircleRadii = ArrayList<Float>()
        val gradients = HashSet<Fill>()

        fun fillCirclesOf(radius: Float) = fillCircleRadii.count { it == radius }

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
                "drawLine" -> { rec.lines++; rec.primitives++ }
                "fillCircle" -> {
                    rec.fillCircles++
                    rec.primitives++
                    rec.fillCircleRadii += args!![2] as Float
                    assertTrue((args[2] as Float) > 0f)
                }
                "strokeCircle" -> { rec.strokeCircles++; rec.primitives++ }
                "strokeOval" -> { rec.strokeOvals++; rec.primitives++ }
                "fillRoundRect" -> { rec.fillRoundRects++; rec.primitives++ }
                "fillRect" -> { rec.fillRects++; rec.primitives++ }
                "strokeRoundRect" -> {
                    rec.primitives++
                    rec.strokeRoundRects += args!![5] as Float
                }
                else -> if (method.name.startsWith("fill") || method.name.startsWith("stroke")) rec.primitives++
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
