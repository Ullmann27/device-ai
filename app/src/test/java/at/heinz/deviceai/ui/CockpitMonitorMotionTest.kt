package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar3d.CockpitStageFrame
import org.junit.Assert.*
import org.junit.Test

class CockpitMonitorMotionTest {
    @Test fun workMovesActualDepthAndReturnsExactly() {
        val base = CockpitStageFrame(884f, 420f, 210f, 210f, 674f, 390f)
        val focused = CockpitMonitorMotion.pose(base, 1f)
        assertEquals(base.monitorLeft, focused.monitorLeft, 0f)
        assertEquals(base.monitorRight, focused.monitorRight, 0f)
        assertTrue(focused.monitorTransform()[14] > base.monitorTransform()[14])
        val rest = base.copy(yawDegrees = 0f).quad()
        val front = focused.quad()
        assertTrue(front[2] - front[0] > rest[2] - rest[0])
        assertEquals(base, CockpitMonitorMotion.pose(base, 0f))
    }

    @Test fun reversingAnActiveFlightPreservesPositionAndVelocity() {
        val outward = CockpitMonitorMotion.flight(0f, 0f, 1f)
        val midpoint = outward.sample(180f)
        val back = CockpitMonitorMotion.flight(midpoint.focus, midpoint.velocityPerMs, 0f)
        assertEquals(midpoint, back.sample(0f))
        var previous = midpoint.focus
        for (t in 1..back.durationMs.toInt() + 1) {
            val sample = back.sample(t.toFloat())
            assertTrue(sample.focus in 0f..1f)
            assertTrue(kotlin.math.abs(sample.focus - previous) < .03f)
            previous = sample.focus
        }
        assertEquals(CockpitMonitorMotion.Sample(0f, 0f), back.sample(600f))
    }

    @Test fun monitorRemainsInsidePhoneAndFoldSceneThroughoutFlight() {
        for ((w, h) in listOf(412f to 915f, 884f to 760f)) {
            val layout = CinematicWorkstationLayout.create(w, h)
            val scene = layout[JarvisShellLayout.Slot.AVATAR]!!
            val monitor = layout[JarvisShellLayout.Slot.WORK_PROCESS]!!
            val base = CockpitStageFrame(scene.width, scene.height, monitor.left - scene.left,
                monitor.top - scene.top, monitor.right - scene.left, monitor.bottom - scene.top)
            for (i in 0..100) {
                val quad = CockpitMonitorMotion.pose(base, i / 100f).quad(0f, 0f, 1f, 1f)
                quad.toList().chunked(2).forEach { xy ->
                    assertTrue("x=${xy[0]} width=${scene.width}", xy[0] in 0f..scene.width)
                    assertTrue("y=${xy[1]} height=${scene.height}", xy[1] in 0f..scene.height)
                }
            }
        }
    }
}
