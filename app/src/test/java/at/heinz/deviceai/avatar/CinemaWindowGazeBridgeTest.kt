package at.heinz.deviceai.avatar

import at.heinz.deviceai.avatar3d.CinemaWindowLightState
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.WindowProjectedMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaWindowGazeBridgeTest {
    private var now = 1_000L

    private fun reducerDirector(): Pair<AvatarPoseReducer, CinemaWindowGazeBridge> {
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        return reducer to CinemaWindowGazeBridge(director)
    }

    private fun motion(
        x: Float,
        y: Float = -.2f,
        phase: WindowProjectedMotion.Phase = WindowProjectedMotion.Phase.FLYING,
        windowId: String = "work-document",
        turnId: String = "turn-1",
        accent: Int? = 0xFFAA40FF.toInt(),
    ) = WindowProjectedMotion(turnId, now, windowId, x, y, accent, phase)

    @Test fun gazeFollowsEachProjectedSampleInsteadOfTheEndpoint() {
        CinemaWindowLightState.reset()
        val (reducer, bridge) = reducerDirector()
        val samples = listOf(-.6f, -.3f, .0f, .35f, .55f)
        for (x in samples) {
            bridge.render(motion(x))
            now += 80L
            val pose = reducer.pose(now)
            assertTrue(pose.gazeDirected)
            assertEquals(x, pose.gazeX, .0001f)
        }
        assertEquals(.55f, reducer.pose(now).gazeX, .0001f)
        CinemaWindowLightState.reset()
    }

    @Test fun closedSampleAndTurnEndReleaseGazeAndRestoreLight() {
        CinemaWindowLightState.reset()
        val (reducer, bridge) = reducerDirector()
        bridge.render(motion(.4f, accent = 0xFFAA40FF.toInt()))
        assertTrue(CinemaWindowLightState.open)
        assertEquals(.4f, CinemaWindowLightState.gazeX, 0f)
        assertEquals(0xFFAA40FF.toInt(), CinemaWindowLightState.accentArgb)
        now += 100L
        bridge.render(motion(.4f, phase = WindowProjectedMotion.Phase.CLOSED))
        assertFalse(reducer.pose(now).gazeDirected)
        assertFalse(CinemaWindowLightState.open)
        assertNull(CinemaWindowLightState.accentArgb)

        bridge.render(motion(-.5f, turnId = "turn-2", windowId = "work-process"))
        now += 50L
        bridge.render(CockpitAnimationEvent.TurnEnded("turn-2", now, delivered = true))
        assertFalse(reducer.pose(now).gazeDirected)
        assertFalse(CinemaWindowLightState.open)
        CinemaWindowLightState.reset()
    }

    @Test fun speechDoesNotRetireTheWindowIdWhenASampleArrives() {
        CinemaWindowLightState.reset()
        val reducer = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink { reducer.apply(it) }) { now }
        val bridge = CinemaWindowGazeBridge(director)
        assertTrue(director.speechStarted("s1", "Ich lese das Dokument."))
        bridge.render(motion(.5f))
        assertFalse("opening during speech would retire the window", reducer.pose(now).gazeDirected)
        director.speechEnded("s1", interrupted = false)
        now += 20L
        bridge.render(motion(.5f))
        assertTrue("the same window can still open after speech", reducer.pose(now).gazeDirected)
        assertEquals(.5f, reducer.pose(now).gazeX, .0001f)
        CinemaWindowLightState.reset()
    }
}
