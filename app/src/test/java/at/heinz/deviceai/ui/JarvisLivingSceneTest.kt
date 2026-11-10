package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 59509 scene layers: bounded draw cost, continuous motion on the shared clock, still without motion. */
class JarvisLivingSceneTest {
    private class Recorder {
        val ops = mutableListOf<String>()
        val points = mutableListOf<Float>()
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader, arrayOf(ContinuumPainter::class.java)) { _, m, a ->
            when (m.name) {
                "measureText" -> 0f; "capHeight" -> 0f; "drawRaster" -> false
                "save", "restore", "translate", "scale", "rotate", "setAdditive", "clipRect", "clipRoundRect" -> null
                else -> { ops += m.name; (a ?: emptyArray()).filterIsInstance<Float>().take(2).forEach { points += it }; null }
            }
        } as ContinuumPainter
    }

    private fun flows(t: Float) = Recorder().also { JarvisEnergyFlows.draw(it.painter, 600f, 270f, t, .2f) }
    private fun aura(t: Float) = Recorder().also {
        JarvisHeadAura.draw(it.painter, JarvisShellLayout.Box(160f, 44f, 500f, 310f), JarvisCockpitGrid.HEAD_ANCHOR_X, t)
    }

    @Test fun drawCostPerFrameIsBoundedAndIndependentOfTime() {
        val f0 = flows(0f).ops.size; val f1 = flows(7.3f).ops.size
        val a0 = aura(0f).ops.size
        assertEquals("constant work per frame", f0, f1)
        assertTrue("energy streams $f0 primitives", f0 <= 64)
        assertTrue("head aura $a0 primitives", a0 <= 80)
    }

    @Test fun motionIsContinuousWithoutJumps() {
        val a = flows(3.000f).points; val b = flows(3.016f).points   // one 60 Hz frame apart
        assertEquals(a.size, b.size)
        val maxStep = a.indices.maxOf { kotlin.math.abs(a[it] - b[it]) }
        assertTrue("largest per-frame movement $maxStep dp", maxStep < 6f)
        val far = flows(5f).points
        assertTrue("the scene actually moves", a.indices.any { kotlin.math.abs(a[it] - far[it]) > 1f })
    }

    @Test fun reducedMotionTimeIsStableAndDisabledLayersDrawNothing() {
        assertEquals(flows(0f).points, flows(0f).points)
        val r = Recorder(); JarvisEnergyFlows.draw(r.painter, 600f, 270f, 1f, enabled = false)
        JarvisHeadAura.draw(r.painter, JarvisShellLayout.Box(0f, 0f, 300f, 300f), .7f, 1f, enabled = false)
        assertTrue(r.ops.isEmpty())
    }

    @Test fun huesCycleSmoothlyThroughTheNeonPalette() {
        var previous = JarvisEnergyFlows.hue(0f)
        for (i in 1..400) {
            val c = JarvisEnergyFlows.hue(i / 400f)
            val d = listOf(16, 8, 0).maxOf { kotlin.math.abs(((c shr it) and 255) - ((previous shr it) and 255)) }
            assertTrue("hue step $d at $i", d <= 12)
            previous = c
        }
    }

    @Test fun headStageCentersTheFaceBesideTheLiveCard() {
        for ((w, h) in listOf(840f to 560f, 884f to 760f, 960f to 640f, 1536f to 1024f)) {
            val shell = JarvisShellLayout.create(w, h)
            val stage = requireNotNull(shell.headStage); val dialog = requireNotNull(shell[JarvisShellLayout.Slot.DIALOG])
            assertEquals("$w×$h centered head composition", .5f, shell.headAnchorX, 0f)
            assertTrue("$w×$h live card stays left of the head", dialog.right < stage.left)
            assertTrue("$w×$h live card is tall enough to read", dialog.height >= 140f)
            assertEquals(1f, JarvisHeadAura.fadeAlpha(stage.width * shell.headAnchorX, stage.height * .4f, stage.width, stage.height), 0f)
        }
    }

    @Test fun realPulseAndProcessingBrightenTheOrbCore() {
        fun coreAlpha(s: ContinuumBrainOrbRenderer.Signals): Float {
            val alphas = mutableListOf<Float>()
            val p = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader, arrayOf(ContinuumPainter::class.java)) { _, m, a ->
                when (m.name) {
                    "measureText", "capHeight" -> 0f; "drawRaster" -> false
                    "fillCircle" -> { if ((a[2] as Float) == 100f * .78f) alphas += a[4] as Float; null }
                    else -> null
                }
            } as ContinuumPainter
            ContinuumBrainOrbRenderer().draw(p, 0f, 0f, 100f, s)
            return alphas.single()
        }
        val calm = coreAlpha(ContinuumBrainOrbRenderer.Signals(phase = 0f))
        val active = coreAlpha(ContinuumBrainOrbRenderer.Signals(phase = 0f, pulse = 1f, processing = true))
        assertTrue("pulse/processing must brighten the core: $calm -> $active", active > calm + .2f)
        assertTrue(active <= 1f)
    }
}
