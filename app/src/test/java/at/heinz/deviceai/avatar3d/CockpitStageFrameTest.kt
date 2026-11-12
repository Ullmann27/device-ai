package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class CockpitStageFrameTest {
    @Test fun projectedPixelsKeepOrientationAtBothYawLimits() {
        for (yaw in listOf(-8f, -3f, 0f, 3f, 8f)) {
            val frame = CockpitStageFrame(884f, 420f, 200f, 170f, 684f, 410f, yaw)
            val p = frame.quad()
            assertTrue(p[0] < p[2])
            assertTrue(p[6] < p[4])
            assertTrue(p[1] < p[7])
            assertTrue(p[3] < p[5])
            assertTrue(p.all(Float::isFinite))
        }
    }

    @Test fun viewBufferIsBoundedWithoutChangingAspect() {
        val frame = CockpitStageFrame(2496f, 1600f, 400f, 600f, 2000f, 1500f)
        val size = frame.bufferSize()
        assertEquals(1280, size.first)
        assertTrue(size.second <= 1280)
        assertEquals(frame.width / frame.height, size.first.toFloat() / size.second, .003f)
    }

    @Test fun invalidSourceGeometryCannotReachRenderer() {
        val frame = CockpitStageFrame(800f, 400f, 100f, 100f, 700f, 390f)
        assertFalse(frame.copy(width = Float.NaN).valid())
        assertFalse(frame.copy(monitorRight = 801f).valid())
        assertFalse(frame.copy(monitorBottom = frame.monitorTop).valid())
        assertFalse(frame.copy(yawDegrees = 90f).valid())
    }
}
