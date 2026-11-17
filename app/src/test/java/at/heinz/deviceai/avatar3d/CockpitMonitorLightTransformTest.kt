package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test

class CockpitMonitorLightTransformTest {
    @Test fun lightStaysInFrontOfTheTiltedMonitor() {
        for (yaw in listOf(-8f, 0f, 8f)) for (pitch in listOf(-12f, 0f, 12f)) {
            val frame = CockpitStageFrame(1080f, 1920f, 120f, 900f, 960f, 1700f, yaw, pitch)
            val matrix = frame.monitorTransform()
            val position = FloatArray(3)
            var range = Float.NaN
            CockpitMonitorLightTransform({ x, y, z ->
                position[0] = x; position[1] = y; position[2] = z
            }, { range = it }).update(matrix)
            val sx = frame.monitorWidth * frame.restCompensation * frame.worldScale
            val sy = frame.monitorHeight * frame.restCompensation * frame.worldScale
            val sz = minOf(frame.monitorWidth, frame.monitorHeight) * frame.restCompensation * frame.worldScale
            fun component(column: Int, scale: Float): Float = (0..2).sumOf {
                ((position[it] - matrix[12 + it]) * matrix[column + it] / scale).toDouble()
            }.toFloat()
            assertEquals("horizontal yaw=$yaw pitch=$pitch", 0f, component(0, sx), 1e-6f)
            assertEquals("vertical yaw=$yaw pitch=$pitch", -.30f * sy, component(4, sy), 1e-6f)
            assertEquals("front yaw=$yaw pitch=$pitch", .22f * maxOf(sx, sy), component(8, sz), 1e-6f)
            assertEquals(maxOf(sx, sy) * 1.6f, range, 1e-6f)
        }
    }

    @Test fun resizedMonitorUpdatesRangeEvenAtTheSameLightPosition() {
        var positions = 0
        val ranges = mutableListOf<Float>()
        val light = CockpitMonitorLightTransform({ _, _, _ -> positions++ }, { ranges += it })
        light.update(matrix())
        light.update(matrix(width = 2f, z = -.22f))
        assertEquals(1, positions)
        assertEquals(listOf(1.6f, 3.2f), ranges)
    }

    @Test fun unchangedTransformIsSilentAndClearUploadsAgain() {
        var positions = 0
        var ranges = 0
        val light = CockpitMonitorLightTransform({ _, _, _ -> positions++ }, { ranges++ })
        val frame = matrix()
        light.update(frame)
        repeat(60) { light.update(frame) }
        assertEquals(1, positions)
        assertEquals(1, ranges)
        light.clear()
        light.update(frame)
        assertEquals(2, positions)
        assertEquals(2, ranges)
    }

    @Test fun partialPositionFailureCannotReuseTheEarlierCache() {
        var positions = 0
        var ranges = 0
        var actualX = Float.NaN
        val light = CockpitMonitorLightTransform({ x, _, _ ->
            positions++
            actualX = x
            if (positions == 2) throw IllegalStateException("position")
        }, { ranges++ })
        light.update(matrix())
        val moved = matrix().also { it[12] = .1f }
        assertTrue(runCatching { light.update(moved) }.isFailure)
        light.update(matrix())
        assertEquals(0f, actualX, 0f)
        assertEquals(3, positions)
        assertEquals(1, ranges)
    }

    @Test fun rangeFailureRetriesOnlyTheFailedUpload() {
        var positions = 0
        var ranges = 0
        val light = CockpitMonitorLightTransform({ _, _, _ -> positions++ }, {
            ranges++
            if (ranges == 1) throw IllegalStateException("range")
        })
        assertTrue(runCatching { light.update(matrix()) }.isFailure)
        light.update(matrix())
        assertEquals(1, positions)
        assertEquals(2, ranges)
    }

    private fun matrix(width: Float = 1f, z: Float = 0f) = floatArrayOf(
        width, 0f, 0f, 0f, 0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f, 0f, z, 1f
    )
}
