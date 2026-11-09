package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import java.util.Collections
import java.util.IdentityHashMap
import org.junit.Assert.*
import org.junit.Test

class HollywoodFuiMaterialRegressionTest {
    private val box = HollywoodFuiCinematics.Box(200f, 120f, 520f, 360f)

    @Test fun settledAnchorSubmitsNoHaloGlintOrBeam() {
        val probe = Probe()
        HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan).drawAnchor(
            probe.painter, 40f, 180f, box, HollywoodFuiConstruct.frame(900L), true)
        assertEquals(0, probe.draws)
    }

    @Test fun closePulseReignitesButCompletedCloseSubmitsNothing() {
        val painter = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
        val live = Probe()
        painter.drawAnchor(live.painter, 40f, 180f, box, HollywoodFuiConstruct.frame(900L, 120L), true)
        assertTrue(live.draws > 0)
        val closed = Probe()
        painter.drawAnchor(closed.painter, 40f, 180f, box, HollywoodFuiConstruct.frame(900L, 420L), true)
        assertEquals(0, closed.draws)
    }

    @Test fun glassGradientsSpanThePaneRatherThanOneDrawingUnit() {
        val probe = Probe()
        HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan).drawGlassLayers(
            probe.painter, box, HollywoodFuiConstruct.frame(900L), 6f)
        val gradients = probe.fills.filterIsInstance<Fill.Linear>()
        assertTrue(gradients.isNotEmpty())
        assertTrue(gradients.all { kotlin.math.hypot(it.x1 - it.x0, it.y1 - it.y0) >= box.height * .4f })
        probe.assertBalanced()
    }

    @Test fun liveDrawingReusesGradientAndPathIdentities() {
        val painter = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
        val probe = Probe()
        repeat(200) { i ->
            val frame = HollywoodFuiConstruct.frame(900L + i * 16)
            painter.drawGlassLayers(probe.painter, box, frame, 6f)
            painter.drawGridAndScan(probe.painter, box, frame, true)
        }
        assertTrue("Unbounded shader identities: ${probe.fills.size}", probe.fills.size <= 12)
        assertTrue("Unbounded path identities: ${probe.paths.size}", probe.paths.size <= 3)
        probe.assertBalanced()
    }

    @Test fun particleRequestCannotExceedTheHardBudget() {
        assertEquals(HollywoodFuiCinematics.MAX_MOTES,
            HollywoodFuiCinematics.motes(box, 12_345L, 10_000, 7).size)
    }

    @Test fun sustainedWorkReflectionsMoveOutsideTextAndStopWithTheWork() {
        val painter = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
        fun draw(time: Long, working: Boolean): Probe = Probe().also {
            painter.drawMotesAndEnergy(it.painter, box, HollywoodFuiConstruct.frame(time),
                time, 0, 0, working, true, 6f)
            it.assertBalanced()
        }
        val first = draw(5_000, true)
        val later = draw(6_000, true)
        assertEquals(4, first.lines.size)
        assertNotEquals(first.lines, later.lines)
        assertTrue(first.lines.all { line ->
            line[0] >= box.left && line[2] <= box.right &&
                (line[1] < box.top || line[1] > box.bottom) && line[1] == line[3]
        })
        assertEquals(0, draw(6_000, false).draws)
        assertEquals(first.lines, draw(5_000, true).lines)
    }

    private class Probe {
        var draws = 0
        private var depth = 0
        private var additive = false
        val fills: MutableSet<Fill> = Collections.newSetFromMap(IdentityHashMap())
        val paths: MutableSet<VectorPath> = Collections.newSetFromMap(IdentityHashMap())
        val lines = mutableListOf<List<Float>>()
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            args.orEmpty().filterIsInstance<Float>().forEach { assertTrue(it.isFinite()) }
            fills.addAll(args.orEmpty().filterIsInstance<Fill>().filterNot { it is Fill.Solid })
            paths.addAll(args.orEmpty().filterIsInstance<VectorPath>())
            when (method.name) {
                "save", "saveAlpha" -> depth++
                "restore" -> { depth--; assertTrue(depth >= 0) }
                "setAdditive" -> additive = args!![0] as Boolean
            }
            if (method.name.startsWith("fill") || method.name.startsWith("stroke") || method.name == "drawLine") draws++
            if (method.name == "drawLine") lines += args!!.take(4).map { it as Float }
            when (method.name) {
                "measureText", "capHeight" -> 0f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
        fun assertBalanced() { assertEquals(0, depth); assertFalse(additive) }
    }
}
