package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import kotlin.math.abs

class JarvisFluidLightTest {
    private val high = JarvisRenderQuality.resolve(true, false, 0, false, false)

    @Test fun realWorkKeepsFlowAfterConstructionAndStopsImmediately() {
        for (elapsed in listOf(900L, 5_000L, 30_000L, 600_000L, Long.MAX_VALUE)) {
            val sample = JarvisFluidLight.workingSample(elapsed, true, false, high)
            assertTrue(sample.energy in .65f.. .81f)
            assertTrue(sample.phase in 0f..1f)
            assertEquals(JarvisFluidLight.OFF,
                JarvisFluidLight.workingSample(elapsed, false, false, high))
            assertEquals(JarvisFluidLight.OFF,
                JarvisFluidLight.workingSample(elapsed, true, true, high))
        }
    }

    @Test fun workFlowWrapsContinuouslyAndHonorsSafetyBudgets() {
        for (time in listOf(3_200L, 6_400L, 600_000L)) {
            val before = JarvisFluidLight.workingSample(time - 1, true, false, high)
            val after = JarvisFluidLight.workingSample(time + 1, true, false, high)
            assertEquals(before.phase, after.phase, .002f)
            assertEquals(before.energy, after.energy, .002f)
        }
        for (budget in listOf(
            JarvisRenderQuality.resolve(false, false, 0, false, false),
            JarvisRenderQuality.resolve(true, false, 3, false, false),
            JarvisRenderQuality.resolve(true, true, 0, false, false))) {
            assertEquals(JarvisFluidLight.OFF,
                JarvisFluidLight.workingSample(5_000, true, false, budget))
        }
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(JarvisFluidLight.OFF,
                JarvisFluidLight.workingSample(5_000, true, false, high, scale))
        }
    }

    @Test fun lightBridgesDepartureAndReconstruction() {
        assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(0))
        assertTrue(JarvisFluidLight.sample(280).energy > .3f)
        assertTrue(JarvisFluidLight.sample(420).energy > .6f)
        assertTrue(JarvisFluidLight.sample(650).rebuilding)
        assertTrue(JarvisFluidLight.sample(900).energy > .6f)
        assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(1600))
    }

    @Test fun suspendedAndThermalBudgetsCannotAnimate() {
        for (budget in listOf(
            JarvisRenderQuality.resolve(false, false, 0, false, false),
            JarvisRenderQuality.resolve(true, false, 3, false, false),
            JarvisRenderQuality.resolve(true, true, 0, false, false))) {
            assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(650, budget = budget))
            assertEquals(0, JarvisFluidLight.strandCount(budget))
        }
        assertEquals(4, JarvisFluidLight.strandCount(high))
        assertEquals(2, JarvisFluidLight.strandCount(JarvisRenderQuality.resolve(true, false, 1, false, true)))
    }

    @Test fun replayIsBoundedAndUsesTheExistingStagger() {
        for (time in -200L..2000L step 13) {
            val a = JarvisFluidLight.sample(time, 2, high)
            assertEquals(a, JarvisFluidLight.sample(time, 2, high))
            assertTrue(a.energy in 0f..1f && a.phase in 0f..1f)
            assertEquals(a, JarvisFluidLight.sample(time - 140, 0, high))
        }
        assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(Long.MAX_VALUE))
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(JarvisFluidLight.OFF, JarvisFluidLight.sample(650, animatorScale = scale))
        }
    }

    @Test fun cancellingAnyConstructionFramePreservesTheVisiblePose() {
        val slots = listOf(JarvisShellLayout.Slot.WORK_DOCUMENT, JarvisShellLayout.Slot.WORK_PROCESS,
            JarvisShellLayout.Slot.WORK_DRAFT, JarvisShellLayout.Slot.BOTTOM_NAV, JarvisShellLayout.Slot.TILES)
        for (slot in slots) for (rank in 0..4) for (time in 0L..1800L step 37) {
            val working = JarvisCinemaEntrance.workOrderPose(slot, rank, time, 1f, high)
            val cancelled = JarvisCinemaEntrance.returnPose(slot, rank, 0, time, 1f, high)
            assertEquals("$slot $rank $time", working, cancelled)
            val settled = JarvisCinemaEntrance.returnPose(slot, rank, 3000, time, 1f, high)
            assertTrue(settled.settled && !settled.hidden)
        }
    }
    private data class Batch(val points: FloatArray, val width: Float, val alpha: Float)
    private class Probe(val failOnStroke: Boolean = false) {
        val batches = mutableListOf<Batch>()
        var additive = false
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, args ->
            when (method.name) {
                "setAdditive" -> additive = args!![0] as Boolean
                "strokeSegments" -> {
                    if (failOnStroke) throw IllegalStateException("stroke failed")
                    batches += Batch((args!![0] as FloatArray).copyOf(args[1] as Int),
                        args[2] as Float, args[4] as Float)
                }
            }
            null
        } as ContinuumPainter
    }

    private val source = JarvisShellLayout.Box(20f, 30f, 200f, 110f)
    private fun transfer(phase: Float, budget: JarvisRenderQuality.Budget = high): Probe = Probe().also {
        JarvisFluidLight.drawTransfer(it.painter, source, 160f, 360f,
            JarvisFluidLight.Sample(phase, .8f, phase > .5f), budget)
    }

    @Test fun ribbonsRemainConnectedAndBoundedAtEveryPhase() {
        for (phase in 0..100) {
            val probe = transfer(phase / 100f)
            assertEquals(4, probe.batches.size)
            assertFalse(probe.additive)
            val body = probe.batches[1]
            assertEquals(4 * 18 * 3 * 4, body.points.size)
            assertEquals(3.2f, body.width, 0f)
            assertTrue(probe.batches.all { it.points.all(Float::isFinite) && it.alpha in 0f..1f })
            for (strand in 0..3) {
                val start = strand * 18 * 3 * 4
                assertEquals(source.bottom, body.points[start + 1], .001f)
                for (step in 1..17) {
                    val prior = start + (step - 1) * 12 + 4
                    val next = start + step * 12 + 4
                    assertEquals(body.points[prior + 2], body.points[next], 0f)
                    assertEquals(body.points[prior + 3], body.points[next + 1], 0f)
                }
                val end = start + 17 * 12 + 4
                assertEquals(160f, body.points[end + 2], .001f)
                assertEquals(360f, body.points[end + 3], .001f)
            }
        }
    }

    @Test fun warmBudgetDropsGlowAndHalvesGeometry() {
        val warm = JarvisRenderQuality.resolve(true, false, 2, false, true)
        val batches = transfer(.5f, warm).batches
        assertEquals(3, batches.size)
        assertEquals(2 * 18 * 3 * 4, batches.first().points.size)
        assertTrue(batches.all { it.width <= 3.2f })
        assertTrue(transfer(.5f, JarvisRenderQuality.resolve(false, false, 0, false, false)).batches.isEmpty())
    }

    @Test fun replayAndReversalHaveNoTopologyJump() {
        val phases = (0..100).map { it / 100f }
        val forward = phases.map { transfer(it).batches[1].points }
        for (index in phases.indices.reversed()) {
            assertArrayEquals(forward[index], transfer(phases[index]).batches[1].points, 0f)
            if (index > 0) {
                val maximumMove = forward[index].zip(forward[index - 1]) { a, b -> abs(a - b) }.max()
                assertTrue("move=$maximumMove", maximumMove < 1.5f)
            }
        }
    }

    @Test fun invalidGeometryDrawsNothingAndFailureRestoresBlending() {
        for (box in listOf(source.copy(left = Float.NaN), source.copy(right = Float.POSITIVE_INFINITY),
            source.copy(right = source.left))) {
            val probe = Probe()
            JarvisFluidLight.drawTransfer(probe.painter, box, 160f, 360f,
                JarvisFluidLight.Sample(.5f, 1f, false), high)
            assertTrue(probe.batches.isEmpty())
            assertFalse(probe.additive)
        }
        val probe = Probe(failOnStroke = true)
        try {
            JarvisFluidLight.drawTransfer(probe.painter, source, 160f, 360f,
                JarvisFluidLight.Sample(.5f, 1f, false), high)
            fail("Expected painter failure")
        } catch (_: IllegalStateException) {
            assertFalse(probe.additive)
        }
    }

}
