package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.cognitive.*
import org.junit.Assert.*
import org.junit.Test

class ComputerMonitorGazeTest {
    private val frame = CockpitStageFrame(1000f, 900f, 100f, 400f, 900f, 800f, yawDegrees = 0f)
    private val projection = ComputerMonitorGaze(frame, 500f, 230f)
    private val binding = LiveFrameBinding("task", 9, "com.example.app", "com.example.owner", LiveFrameOrigin.ACCESSIBILITY_WINDOW)
    private val meta = LiveFrameMeta(9, 1, 800, 400, "com.example.app", 4, false,
        observationRevision = 7, sourceViewport = LiveTargetBounds(.2f, .1f, .8f, .9f))
    private fun state() = LiveMonitorStatus(LiveMonitorState.LIVE, null, binding, meta, 12, 0)
    private fun event(x: Float = .5f, y: Float = .5f) = CockpitWorkEvent("task", 9, 2, 1, 1,
        CockpitWorkEvent.Stage.DISPATCHED, "step", CockpitWorkEvent.Action.CLICK,
        CockpitWorkEvent.Target(x, y), observationRevision = 7, frameSequence = 12)

    @Test fun croppedCenterMatchesActualMonitorCenter() {
        val actual = requireNotNull(projection.project(event(), state()))
        val center = frame.quad(.5f, .5325f, .5f, .5325f)
        val expected = JarvisWindowGaze.fromProjection(center[0], center[1], 500f, 230f, 1000f, 900f)
        assertEquals(expected.x, actual.x, .00001f)
        assertEquals(expected.y, actual.y, .00001f)
    }

    @Test fun portraitSourceUsesPillarboxing() {
        val wide = requireNotNull(projection.project(event(.8f), state()))
        val portrait = requireNotNull(projection.project(event(.8f), state().copy(meta = meta.copy(width = 400, height = 800))))
        assertTrue(portrait.x > 0f)
        assertTrue(portrait.x < wide.x * .4f)
    }

    @Test fun landscapeSourceUsesLetterboxing() {
        val normal = requireNotNull(projection.project(event(y = .1f), state()))
        val wide = requireNotNull(projection.project(event(y = .1f), state().copy(meta = meta.copy(width = 2000, height = 400))))
        assertTrue(wide.y < normal.y)
    }

    @Test fun monitorMotionChangesTheRealTargetProjection() {
        val positioned = projection.copy(headY = 400f)
        val rest = requireNotNull(positioned.project(event(.7f, .2f), state()))
        val moved = requireNotNull(positioned.copy(frame = frame.copy(monitorLeft = 50f, monitorRight = 850f, monitorTop = 350f, monitorBottom = 750f, yawDegrees = 7f, pitchDegrees = -9f,
            depthOffset = 140f)).project(event(.7f, .2f), state()))
        assertTrue(kotlin.math.abs(rest.x - moved.x) > .005f)
        assertTrue(kotlin.math.abs(rest.y - moved.y) > .005f)
    }

    @Test fun unavailableOrUnboundTargetsNeverProduceGaze() {
        assertNull(projection.project(event(.9f), state()))
        assertNull(projection.project(event().copy(taskId = "other"), state()))
        assertNull(projection.project(event().copy(sessionId = 10), state()))
        assertNull(projection.project(event().copy(frameSequence = 11), state()))
        assertNull(projection.project(event().copy(observationRevision = 6), state()))
        assertNull(projection.project(event(), state().copy(state = LiveMonitorState.PAUSED)))
        assertNull(projection.project(event(), state().copy(meta = meta.copy(sourceViewport = null))))
    }
}
