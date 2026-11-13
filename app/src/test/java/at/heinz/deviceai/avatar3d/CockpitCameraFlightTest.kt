package at.heinz.deviceai.avatar3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class CockpitCameraFlightTest {
    private val rest = 2.4
    private val pivot = doubleArrayOf(.35, -.12, .05)
    private val width = .62
    private fun pose(t: Double) = CockpitCameraFlight.pose(t, pivot[0], pivot[1], pivot[2], width, rest)

    @Test fun flightEndsExactlyInTheRestCamera() {
        assertTrue(CockpitCameraFlight.nearlyRest(pose(1.0), rest))
    }

    @Test fun flightStartsBehindAboveAndCloseToTheMonitor() {
        val p = pose(0.0)
        assertTrue("behind the monitor", p.eyeZ < pivot[2])
        assertTrue("above it", p.eyeY > pivot[1])
        assertEquals(pivot[0], p.targetX, 1e-12)
        val radius = sqrt((p.eyeX - pivot[0]).let { it * it } + (p.eyeY - pivot[1]).let { it * it } +
            (p.eyeZ - pivot[2]).let { it * it })
        assertEquals(width * CockpitCameraFlight.START_RADIUS_MONITOR_WIDTHS, radius, 1e-9)
    }

    @Test fun pathSweepsOverTheSideContinuouslyWithAValidUpVector() {
        var sideSeen = false
        var previous = pose(0.0)
        for (i in 1..400) {
            val p = pose(i / 400.0)
            val dx = p.eyeX - p.targetX; val dy = p.eyeY - p.targetY; val dz = p.eyeZ - p.targetZ
            if (abs(dx) > abs(dz) && abs(dx) > .1) sideSeen = true
            val step = sqrt((p.eyeX - previous.eyeX).let { it * it } + (p.eyeY - previous.eyeY).let { it * it } +
                (p.eyeZ - previous.eyeZ).let { it * it })
            assertTrue("no jump at $i: $step", step < .05)
            val upLength = sqrt(p.upX * p.upX + p.upY * p.upY + p.upZ * p.upZ)
            assertEquals(1.0, upLength, 1e-9)
            val view = sqrt(dx * dx + dy * dy + dz * dz)
            assertEquals("up stays perpendicular to the view", 0.0, (dx * p.upX + dy * p.upY + dz * p.upZ) / view, 1e-9)
            assertTrue("camera never rolls upside down", p.upY > .5)
            previous = p
        }
        assertTrue("the camera passes the monitor's side", sideSeen)
    }

    @Test fun easingStartsAndEndsWithoutVelocity() {
        val h = 1e-4
        assertEquals(0.0, CockpitCameraFlight.smootherstep(h) / h, 1e-6)
        assertEquals(0.0, (1 - CockpitCameraFlight.smootherstep(1 - h)) / h, 1e-6)
        assertEquals(.5, CockpitCameraFlight.smootherstep(.5), 1e-12)
    }

    @Test fun clockRunsOnceAndStopReturnsToRest() {
        val clock = CockpitCameraFlight.Clock()
        assertNull(clock.progress(10))
        clock.start(1_000)
        assertEquals(0.0, clock.progress(1_000)!!, 0.0)
        assertEquals(.5, clock.progress(1_000 + CockpitCameraFlight.DURATION_NANOS / 2)!!, 1e-9)
        assertNull(clock.progress(1_000 + CockpitCameraFlight.DURATION_NANOS))
        assertFalse("a finished flight ends itself", clock.active)
        clock.start(5_000)
        clock.cancel()
        assertNull(clock.progress(5_001))
    }

    @Test fun quaternionSlerpStaysUnitAndHitsBothKeys() {
        val a = CockpitCameraFlight.startOrientation
        val b = CockpitCameraFlight.Quat.IDENTITY
        for (i in 0..20) {
            val q = CockpitCameraFlight.slerp(a, b, i / 20.0)
            assertEquals(1.0, sqrt(q.dot(q)), 1e-12)
        }
        val end = CockpitCameraFlight.slerp(a, b, 1.0)
        assertEquals(1.0, abs(end.w), 1e-12)
    }
}
