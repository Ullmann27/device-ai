package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar3d.CockpitStageFrame
import kotlin.math.PI
import org.junit.Assert.*
import org.junit.Test

class CockpitMonitorMotionTest {
    private val fold = CockpitStageFrame(852f, 420f, 153.36f, 180.6f, 698.64f, 412f)

    @Test fun restAndWorkingEndpointsAreSettled() {
        assertEquals(fold, CockpitMonitorMotion.pose(fold, 0f))
        val work = CockpitMonitorMotion.pose(fold, 1f)
        assertEquals(0f, work.yawDegrees, 0f)
        assertEquals(0f, work.pitchDegrees, 0f)
        assertEquals(0f, work.depthOffset, 0f)
        assertTrue(work.monitorWidth > fold.monitorWidth)
        assertTrue(work.monitorTop < fold.monitorTop)
        assertEquals(fold.monitorBottom, work.monitorBottom, 0f)
    }

    @Test fun flightUsesDepthAndPitchWhileContentStaysReadable() {
        val middle = CockpitMonitorMotion.pose(fold, .5f)
        assertTrue(middle.depthOffset < -40f)
        assertTrue(middle.pitchDegrees < -5f)
        assertTrue(kotlin.math.abs(middle.yawDegrees) > kotlin.math.abs(fold.yawDegrees))
        val pixels = middle.quad()
        assertTrue(pixels[2] - pixels[0] > fold.monitorWidth * .8f)
        assertTrue(pixels[6] < pixels[4] && pixels[1] < pixels[7])
    }

    @Test fun everyFrameFitsPhoneFoldAndTablet() {
        val bases = listOf(fold,
            CockpitStageFrame(336f, 320f, 0f, 137.6f, 336f, 312f),
            CockpitStageFrame(228f, 320f, 0f, 137.6f, 228f, 312f),
            CockpitStageFrame(1408f, 700f, 253.44f, 301f, 1154.56f, 692f))
        for (base in bases) for (step in 0..100) {
            val pose = CockpitMonitorMotion.pose(base, step / 100f)
            assertTrue("Valid pose at $step", pose.valid())
            val pixels = pose.quad()
            for (i in 0 until 4) {
                assertTrue("Horizontal clip at $step", pixels[i * 2] in 0f..base.width)
                assertTrue("Vertical clip at $step", pixels[i * 2 + 1] in 0f..base.height)
            }
        }
    }

    @Test fun reversalReusesContinuousPose() {
        val forward = (0..100).map { CockpitMonitorMotion.pose(fold, it / 100f) }
        val reverse = (100 downTo 0).map { CockpitMonitorMotion.pose(fold, it / 100f) }
        assertEquals(forward, reverse.reversed())
        val before = CockpitMonitorMotion.pose(fold, .447f).quad()
        val after = CockpitMonitorMotion.pose(fold, .4471f).quad()
        for (i in before.indices) assertTrue(kotlin.math.abs(before[i] - after[i]) < .1f)
    }

    @Test fun invalidProgressCannotPoisonRenderer() {
        assertEquals(fold, CockpitMonitorMotion.pose(fold, Float.NaN))
        assertEquals(fold, CockpitMonitorMotion.pose(fold, -1f))
        assertEquals(CockpitMonitorMotion.pose(fold, 1f), CockpitMonitorMotion.pose(fold, 2f))
    }

    @Test fun reversalKeepsMeasuredVelocityInsteadOfRestartingAtRest() {
        val opening = CockpitMonitorMotion.flight(0f, 0f, 1f)
        val stepMs = .1f
        for (time in listOf(40f, 120f, 210f, 350f)) {
            val old = opening.sample(time)
            val closing = CockpitMonitorMotion.flight(old.focus, old.velocityPerMs, 0f)
            assertEquals(old, closing.sample(0f))
            val incoming = (old.focus - opening.sample(time - stepMs).focus) / stepMs
            val outgoing = (closing.sample(stepMs).focus - old.focus) / stepMs
            assertEquals("Continuous measured velocity", incoming, outgoing, .00002f)
        }
        val old = opening.sample(210f)
        val closing = CockpitMonitorMotion.flight(old.focus, old.velocityPerMs, 0f)
        val retained = (closing.sample(stepMs).focus - old.focus) / stepMs
        val resetFraction = (.5 - .5 * kotlin.math.cos(PI * stepMs / 210f)).toFloat()
        val previousRestart = -old.focus * resetFraction / stepMs
        assertTrue("Old interpolator loses speed", kotlin.math.abs(previousRestart - old.velocityPerMs) > .003f)
        assertEquals(old.velocityPerMs, retained, .00002f)
    }

    @Test fun brakingJoinHasContinuousPositionAndVelocity() {
        val before = CockpitMonitorMotion.flight(0f, 0f, 1f).sample(210f)
        val flight = CockpitMonitorMotion.flight(before.focus, before.velocityPerMs, 0f)
        val h = .25f
        val at = flight.sample(70f)
        val left = flight.sample(70f - h)
        val right = flight.sample(70f + h)
        assertEquals(0f, at.velocityPerMs, .000001f)
        assertEquals((at.focus - left.focus) / h, (right.focus - at.focus) / h, .00003f)
        assertTrue(kotlin.math.abs(at.focus - left.focus) < .00001f)
        assertTrue(kotlin.math.abs(right.focus - at.focus) < .00001f)
    }

    @Test fun repeatedReversalsRemainBoundedAndFinite() {
        val random = java.util.Random(55L)
        var flight = CockpitMonitorMotion.flight(0f, 0f, 1f)
        var target = 1f
        repeat(200) {
            val old = flight.sample(flight.durationMs * (.05f + random.nextFloat() * .85f))
            target = 1f - target
            flight = CockpitMonitorMotion.flight(old.focus, old.velocityPerMs, target)
            assertTrue(flight.durationMs <= 480f)
            assertEquals(old, flight.sample(0f))
            for (step in 0..100) {
                val sample = flight.sample(flight.durationMs * step / 100f)
                assertTrue(sample.focus in 0f..1f)
                assertTrue(sample.velocityPerMs.isFinite())
            }
            assertEquals(CockpitMonitorMotion.Sample(target, 0f), flight.sample(flight.durationMs))
        }
    }

    @Test fun completeFlightFitsRequestedDurationAndSettles() {
        assertTrue(CockpitMonitorMotion.flight(.99f, .0001f, 0f).durationMs <= 480f)
        for ((start, target) in listOf(0f to 1f, 1f to 0f)) {
            val flight = CockpitMonitorMotion.flight(start, 0f, target)
            assertTrue(flight.durationMs in 320f..420f)
            assertEquals(CockpitMonitorMotion.Sample(start, 0f), flight.sample(0f))
            assertEquals(CockpitMonitorMotion.Sample(target, 0f), flight.sample(flight.durationMs + 100f))
        }
    }
}
