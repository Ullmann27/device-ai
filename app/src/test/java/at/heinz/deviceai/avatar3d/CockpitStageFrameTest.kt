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
        assertFalse(frame.copy(pitchDegrees = Float.NaN).valid())
        assertFalse(frame.copy(pitchDegrees = 13f).valid())
        assertFalse(frame.copy(depthOffset = Float.POSITIVE_INFINITY).valid())
        assertFalse(frame.copy(depthOffset = frame.distance).valid())
    }

    @Test fun simultaneousPitchYawAndDepthMatchModelMatrix() {
        for (yaw in listOf(-8f, 0f, 8f)) for (pitch in listOf(-12f, 0f, 12f)) {
            val frame = CockpitStageFrame(884f, 420f, 180f, 150f, 704f, 410f,
                yaw, pitch, -68f)
            val actual = frame.quad()
            val y = Math.toRadians(yaw.toDouble())
            val p = Math.toRadians(pitch.toDouble())
            val rotationY = arrayOf(
                doubleArrayOf(kotlin.math.cos(y), 0.0, kotlin.math.sin(y), 0.0),
                doubleArrayOf(0.0, 1.0, 0.0, 0.0),
                doubleArrayOf(-kotlin.math.sin(y), 0.0, kotlin.math.cos(y), 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0))
            val rotationX = arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, kotlin.math.cos(p), -kotlin.math.sin(p), 0.0),
                doubleArrayOf(0.0, kotlin.math.sin(p), kotlin.math.cos(p), 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0))
            val corners = listOf(.035 to .12, .965 to .12, .965 to .945, .035 to .945)
            corners.forEachIndexed { index, (u, v) ->
                val vertex = doubleArrayOf((u - .5) * frame.monitorWidth,
                    (.5 - v) * frame.monitorHeight, minOf(frame.monitorWidth, frame.monitorHeight) * .032, 1.0)
                val rotated = multiply(rotationY, multiply(rotationX, vertex))
                rotated[0] += (frame.monitorLeft + frame.monitorRight - frame.width) * .5
                rotated[1] += (frame.height - frame.monitorTop - frame.monitorBottom) * .5
                rotated[2] += frame.depthOffset
                val cameraZ = frame.distance - rotated[2]
                val expectedX = frame.width * .5 + frame.distance * rotated[0] / cameraZ
                val expectedY = frame.height * .5 - frame.distance * rotated[1] / cameraZ
                assertEquals(expectedX.toFloat(), actual[index * 2], .0002f)
                assertEquals(expectedY.toFloat(), actual[index * 2 + 1], .0002f)
            }
        }
    }

    @Test fun depthUsesPerspectiveWithoutMirroringPixels() {
        val base = CockpitStageFrame(800f, 400f, 200f, 100f, 600f, 300f, 0f)
        val near = base.copy(depthOffset = 100f).quad()
        val far = base.copy(depthOffset = -100f).quad()
        assertTrue(near[0] < far[0])
        assertTrue(near[2] > far[2])
        assertTrue(near[1] < far[1])
        assertTrue(near[7] > far[7])
    }

    private fun multiply(matrix: Array<DoubleArray>, vertex: DoubleArray) =
        DoubleArray(4) { row -> (0..3).sumOf { column -> matrix[row][column] * vertex[column] } }
}
