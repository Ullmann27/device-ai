package at.heinz.deviceai.avatar3d

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CockpitWorldTransformsTest {
    private val frames = listOf(CockpitStageFrame(852f, 480f, 213f, 259.2f, 639f, 427.2f),
        CockpitStageFrame(388f, 531f, 15.52f, 286.74f, 372.48f, 472.59f))

    @Test fun roomAndSidePlanesKeepTheirNativePixelBounds() {
        for (frame in frames) {
            val matrix = CockpitWorldTransforms.room(frame)
            for (x in listOf(.02f, .22f, .5f, .78f, .98f)) for (y in listOf(.05f, .57f, .87f, .93f)) {
                val point = transform(matrix, doubleArrayOf(x - .5, .5 - y, 0.0))
                val projected = frame.project(point[0].toFloat(), point[1].toFloat(), point[2].toFloat())
                assertEquals(frame.width * x, projected.first, .0002f)
                assertEquals(frame.height * y, projected.second, .0002f)
            }
            assertEquals(frame.restDepth * frame.worldScale, matrix[14], 0f)
        }
    }

    @Test fun avatarWorldMatchesOriginalTransformOrderAndPivot() {
        val frame = frames.first()
        val fitted = AvatarFraming.Window(-.22, .28, .23, .24, .76, .44)
        for (azimuth in listOf(-25.0, 0.0, 31.0)) {
            val focus = AvatarGlbContract.Focus(.04, .3, .08, .1, .2, azimuth)
            val actual = CockpitWorldTransforms.avatar(frame, 130f, 24f, 722f, 360f, fitted, focus)
            val scale = 336.0 * frame.worldScale / (2 * fitted.halfHeight)
            val x = (130.0 + 592.0 * fitted.anchorX - frame.width * .5) * frame.worldScale
            val y = (frame.height * .5 - 192.0) * frame.worldScale
            val angle = Math.toRadians(-azimuth)
            val c = cos(angle); val s = sin(angle)
            for (p in listOf(doubleArrayOf(.04, .24, .08), doubleArrayOf(-.1, .18, .14),
                doubleArrayOf(.12, .41, -.04))) {
                val shifted = doubleArrayOf(p[0] - focus.centerX, p[1] - fitted.cameraCenterY,
                    p[2] - focus.centerZ)
                val expected = doubleArrayOf(x + scale * (c * shifted[0] + s * shifted[2]),
                    y + scale * shifted[1], scale * (-s * shifted[0] + c * shifted[2]))
                val point = transform(actual, p)
                for (axis in 0..2) assertEquals(expected[axis], point[axis], .000001)
            }
        }
    }

    @Test fun physicalOrbBoundsKeepTheirCenterAndEquatorBehindAvatar() {
        for (frame in frames) {
            val l = frame.width * .2f; val r = frame.width * .8f
            val t = frame.height * .1f; val b = frame.height * .5f
            val matrix = CockpitWorldTransforms.orb(frame, l, t, r, b)
            val radius = minOf(r - l, b - t) * .5f
            for (p in listOf(doubleArrayOf(-1.0, 0.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, -1.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0))) {
                val world = transform(matrix, p)
                val screen = frame.project(world[0].toFloat(), world[1].toFloat(), world[2].toFloat())
                assertEquals((l + r) * .5f + (radius * p[0]).toFloat(), screen.first, .0002f)
                assertEquals((t + b) * .5f - (radius * p[1]).toFloat(), screen.second, .0002f)
                assertTrue(world[2] < 0.0)
            }
        }
    }

    @Test fun ringsRotateInsideTheirAuthoredPlaneAndReuseOutput() {
        val rest = floatArrayOf(2f, 0f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, -4f, 0f, 0f, .2f, .3f, .4f, 1f)
        for (index in 0..3) {
            val seconds = 3f
            val angle = Math.toRadians((seconds * (3.5 + index * 1.2)))
            val expected = transform(rest, doubleArrayOf(cos(angle), sin(angle), 0.0))
            val out = rest.copyOf()
            assertSame(out, CockpitWorldTransforms.ringTransform(out, index, seconds, true, out))
            val point = transform(out, doubleArrayOf(1.0, 0.0, 0.0))
            for (axis in 0..2) assertEquals(expected[axis], point[axis], .000001)
            assertArrayEquals(rest, CockpitWorldTransforms.ringTransform(rest, index, seconds, false), 0f)
            assertEquals(0f, CockpitWorldTransforms.ringAngleDegrees(index, Float.NaN, true), 0f)
            assertTrue(CockpitWorldTransforms.ringAngleDegrees(index, 3f, true) >
                CockpitWorldTransforms.ringAngleDegrees(index, 1f, true))
        }
    }

    private fun transform(matrix: FloatArray, point: DoubleArray) = DoubleArray(3) { row ->
        matrix[row] * point[0] + matrix[4 + row] * point[1] + matrix[8 + row] * point[2] + matrix[12 + row]
    }
}
