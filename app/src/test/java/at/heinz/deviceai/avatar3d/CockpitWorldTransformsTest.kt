package at.heinz.deviceai.avatar3d

import at.heinz.deviceai.ui.ContinuumOrbitalRing
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
            assertArrayEquals(frame.orbTransform(l, t, r, b), matrix, 0f)
            assertEquals(CockpitStageFrame.ORB_FRONT_LIMIT,
                matrix[14] + matrix[0] * CockpitStageFrame.ORB_MAX_RADIUS, .000001f)
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

    @Test fun fiveAuthoredRingsMatchOriginalPhaseProjection() {
        val glb = GlbOrbGeometry()
        assertEquals((0..4).map { "orb-orbit-$it" }, glb.names.filter { it.startsWith("orb-orbit-") })
        val orbits = glb.source.getJSONArray("orbits")
        assertEquals(5, orbits.length())
        for (index in 0..4) {
            val parameters = orbits.getJSONObject(index)
            val speed = parameters.getDouble("speed").toFloat()
            assertEquals(speed.toDouble(), glb.extras("orb-orbit-$index").getDouble("original_speed"), .000001)
            val original = ContinuumOrbitalRing(parameters.getDouble("a").toFloat(),
                parameters.getDouble("b").toFloat(), parameters.getDouble("tilt").toFloat())
            for (phase in listOf(.4f, 1.2192f, 2.0384f, 3.6768f, 9.4f)) {
                original.project(phase, speed, 0f, 0f, 100f)
                val endpoints = (original.front.take(original.frontSize) +
                    original.back.take(original.backSize)).chunked(2)
                val matrix = CockpitWorldTransforms.ringTransform(glb.rest("orb-orbit-$index"), index, phase, true)
                for (segment in 0..3) for (sample in listOf(0, 16, 32)) {
                    val tube = glb.localVertices("orbit-$index-$segment-core")
                    assertEquals(49 * 6, tube.size)
                    val center = DoubleArray(3) { axis -> (0..5).sumOf { tube[sample * 6 + it][axis] } / 6 }
                    val point = transform(matrix, center)
                    val perspective = 100.0 * 8 / (8 - point[2])
                    val x = point[0] * perspective; val y = -point[1] * perspective
                    assertTrue("Original ring $index/$phase/$segment/$sample",
                        endpoints.any { abs(it[0] - x) < .02 && abs(it[1] - y) < .02 })
                }
            }
        }
    }

    @Test fun fiveRingsReuseOutputAndKeepNeutralPhase() {
        val rest = floatArrayOf(2f, 0f, 0f, 0f, 0f, 0f, 3f, 0f, 0f, -4f, 0f, 0f, .2f, .3f, .4f, 1f)
        for (index in 0..4) {
            val phase = 3f
            val expected = CockpitWorldTransforms.ringTransform(rest, index, phase, true)
            val out = rest.copyOf()
            assertSame(out, CockpitWorldTransforms.ringTransform(out, index, phase, true, out))
            assertArrayEquals(expected, out, 0f)
            assertArrayEquals(rest, CockpitWorldTransforms.ringTransform(rest, index, phase, false), 0f)
            assertArrayEquals(rest, CockpitWorldTransforms.ringTransform(rest, index, .4f, true), 0f)
            assertArrayEquals(rest, CockpitWorldTransforms.ringTransform(rest, index, Float.NaN, true), 0f)
            assertArrayEquals(rest, CockpitWorldTransforms.ringTransform(rest, index, Float.POSITIVE_INFINITY, true), 0f)
        }
        assertThrows(IllegalArgumentException::class.java) { CockpitWorldTransforms.ringTransform(rest, 5, .4f, true) }
        assertThrows(IllegalArgumentException::class.java) { CockpitWorldTransforms.ringTransform(rest, -1, .4f, true) }
    }

    @Test fun actualOrbGeometryRemainsBehindTheAvatarForEveryRingPhase() {
        val geometry = GlbOrbGeometry()
        val rest = geometry.vertices(.4f)
        for (axis in 0..2) {
            assertTrue(rest.minOf { it.point[axis] } <= -1.0)
            assertTrue(rest.maxOf { it.point[axis] } >= 1.0)
        }
        val moving = rest.filter { it.mesh.startsWith("orbit-") }
        assertTrue(moving.isNotEmpty())
        assertTrue(moving.maxOf { sqrt(it.point.sumOf { value -> value * value }) } < CockpitStageFrame.ORB_MAX_RADIUS)
        val fixed = rest.filterNot { it.mesh.startsWith("orbit-") }
        assertTrue(fixed.maxOf { it.point[2] } < CockpitStageFrame.ORB_MAX_RADIUS)
        for (phase in listOf(.4f, 1.2192f, 2.0384f, 3.6768f, 99f, 999.9f)) {
            val vertices = geometry.vertices(phase)
            for (frame in frames) {
                val matrix = CockpitWorldTransforms.orb(frame, frame.width * .2f, frame.height * .1f,
                    frame.width * .8f, frame.height * .5f)
                val depth = vertices.map { transform(matrix, it.point)[2] }
                assertTrue("Every actual vertex stays behind skin", depth.max() <= CockpitStageFrame.ORB_FRONT_LIMIT + .000001)
                assertTrue("Orb remains before far plane", depth.min() > frame.distance * frame.worldScale - frame.far)
            }
        }
    }

    private fun transform(matrix: FloatArray, point: DoubleArray) = DoubleArray(3) { row ->
        matrix[row] * point[0] + matrix[4 + row] * point[1] + matrix[8 + row] * point[2] + matrix[12 + row]
    }
}
