package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.GazeTarget
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarMorphMapper
import org.junit.Assert.*
import org.junit.Test

class JarvisWindowGazeTest {
    @Test fun projectedPositionRatherThanDestinationDeterminesTheGaze() {
        val start = JarvisWindowGaze.fromProjection(320f, 260f, 280f, 260f, 884f, 760f)
        val flight = JarvisWindowGaze.fromProjection(440f, 320f, 280f, 260f, 884f, 760f)
        val dock = JarvisWindowGaze.fromProjection(630f, 390f, 280f, 260f, 884f, 760f)
        assertTrue(start.x < flight.x && flight.x < dock.x)
        assertEquals(320f / 884f, flight.x, .00001f)
        assertEquals(-60f / 760f, flight.y, .00001f)
        assertEquals(.7f, dock.x, 0f)
    }

    @Test fun translationDensityAndFoldedViewportRemainInTheSameCoordinateSpace() {
        val expected = JarvisWindowGaze.fromProjection(600f, 400f, 300f, 260f, 884f, 760f)
        assertEquals(expected, JarvisWindowGaze.fromProjection(700f, 450f, 400f, 310f, 884f, 760f))
        assertEquals(expected, JarvisWindowGaze.fromProjection(1200f, 800f, 600f, 520f, 1768f, 1520f))
        val folded = JarvisWindowGaze.fromProjection(200f, 600f, 200f, 220f, 400f, 800f)
        assertEquals(GazeTarget(0f, -.4f), folded)
    }

    @Test fun projectedTargetsRespectExistingGazeBounds() {
        assertEquals(GazeTarget(-.7f, .4f),
            JarvisWindowGaze.fromProjection(-1000f, -1000f, 200f, 200f, 400f, 800f))
        assertEquals(GazeTarget(.7f, -.4f),
            JarvisWindowGaze.fromProjection(2000f, 2000f, 200f, 200f, 400f, 800f))
    }

    @Test fun invalidGeometryCannotSilentlySteerTheRig() {
        assertThrows(IllegalArgumentException::class.java) {
            JarvisWindowGaze.fromProjection(Float.NaN, 0f, 0f, 0f, 400f, 800f)
        }
        for (width in listOf(0f, -1f, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                JarvisWindowGaze.fromProjection(0f, 0f, 0f, 0f, width, 800f)
            }
        }

    }

    @Test fun originalRigFollowsEyesFirstWithAVisibleBoundedHeadTurnAndKeepsWorking() {
        var now = 1_000L
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        val mapper = AvatarMorphMapper(AvatarGlbContract.TARGETS)
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        director.windowWorkStarted("document", GazeTarget.OWNER)
        mapper.update(reducer.pose(now), now / 1000f, true)
        val target = JarvisWindowGaze.fromProjection(630f, 390f, 280f, 260f, 884f, 760f)
        director.windowMoved("document", target)
        now += 50L
        mapper.update(reducer.pose(now), now / 1000f, true)
        assertTrue("eyes lead the delayed head", mapper.eyeYaw > mapper.headYaw)
        repeat(40) {
            now += 16L
            mapper.update(reducer.pose(now), now / 1000f, true)
        }
        assertTrue("visible turn in the requested natural range", mapper.headYaw in 15f..30f)
        assertEquals(mapper.headYaw * AvatarMorphMapper.NECK_SHARE, mapper.neckYaw, .001f)
        val eyes = mutableListOf<Float>()
        repeat(180) {
            now += 16L
            mapper.update(reducer.pose(now), now / 1000f, true)
            assertTrue(reducer.pose(now).reading)
            eyes += mapper.eyeYaw
        }
        assertTrue("existing work motion continues beyond the entrance", eyes.max() - eyes.min() > 2f)
        director.windowWorkEnded("document")
        assertFalse(reducer.pose(now).reading)
    }
}
