package at.heinz.deviceai.avatar

import at.heinz.deviceai.cognitive.CockpitWorkEvent
import org.junit.Assert.*
import org.junit.Test

class ComputerWorkAttentionTest {
    private var now = 1_000L
    private val pose = AvatarPoseReducer()
    private val director = AvatarDirector(AvatarSink(pose::apply)) { now }
    private val target = GazeTarget(.52f, -.27f)

    @Test fun workYieldsToOwnerAcrossEveryInterruption() {
        val interruptions = listOf(
            AvatarRuntimeSignals(listening = true),
            AvatarRuntimeSignals(speaking = true),
            AvatarRuntimeSignals(awaitingApproval = true),
            AvatarRuntimeSignals(blocked = true)
        )
        for (signals in interruptions) {
            director.onRuntime(AvatarRuntimeSignals(busy = true))
            director.windowWorkStarted("document", target)
            assertTrue(pose.pose(now).reading)
            now++
            director.onRuntime(signals)
            val interrupted = pose.pose(now)
            assertFalse("reading in ${interrupted.state}", interrupted.reading)
            assertFalse(interrupted.gazeDirected)
            assertEquals(0f, interrupted.gazeX, 0f)
            assertEquals(0f, interrupted.gazeY, 0f)
            now++
            director.windowMoved("document", GazeTarget(-.4f, -.2f))
            director.onRuntime(AvatarRuntimeSignals(busy = true))
            assertTrue(pose.pose(now).reading)
            assertEquals(-.4f, pose.pose(now).gazeX, 0f)
        }
    }

    @Test fun workArrivingDuringApprovalWaitsForOwner() {
        director.onRuntime(AvatarRuntimeSignals(awaitingApproval = true))
        director.windowWorkStarted("document", target)
        assertFalse(pose.pose(now).reading)
        assertFalse(director.canAdmitWindowGlance())
        now++
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertTrue(pose.pose(now).reading)
        assertEquals(target.x, pose.pose(now).gazeX, 0f)
    }

    @Test fun speechEndingCannotStealApprovalAttention() {
        director.windowWorkStarted("document", target)
        director.speechStarted("question", "Fortsetzen?")
        director.onRuntime(AvatarRuntimeSignals(awaitingApproval = true))
        now++
        director.speechEnded("question", false)
        assertFalse(pose.pose(now).reading)
        assertEquals(0f, pose.pose(now).gazeX, 0f)
    }

    @Test fun rendererRejectsDirectedGazeDuringApproval() {
        pose.apply(AvatarEvent.StateChanged(AvatarState.WAITING_FOR_APPROVAL,
            AvatarExpression.ATTENTIVE, GazeTarget.OWNER, now))
        pose.apply(AvatarEvent.AnimationIntentsChanged(1, target, now, Long.MIN_VALUE,
            ++now, windowReading = true))
        assertFalse(pose.pose(now).reading)
        pose.apply(AvatarEvent.Gesture(AvatarGestureIntent.WINDOW_FOCUS, target, ++now))
        assertFalse(pose.pose(now).gazeDirected)
    }

    @Test fun interruptionDoesNotRenewExpiredWork() {
        director.windowWorkStarted("document", target)
        now += 20
        director.onRuntime(AvatarRuntimeSignals(awaitingApproval = true))
        now += AvatarPoseReducer.MAX_WINDOW_WORK_MS
        director.windowMoved("document", target)
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertFalse(pose.pose(now).reading)
    }

    @Test fun pauseThenFreshFocusResumesWorkWithoutInputReplay() {
        val bridge = ComputerWorkAvatarBridge(director, projectGaze = { target }, nanos = { now * 1_000_000 })
        bridge.bind("task", 9)
        bridge.render(event(1, CockpitWorkEvent.Stage.ACCEPTED))
        assertTrue(pose.pose(now).reading)
        bridge.render(event(2, CockpitWorkEvent.Stage.PAUSED))
        assertFalse(pose.pose(now).reading)
        bridge.render(event(3, CockpitWorkEvent.Stage.FOCUS))
        assertTrue(pose.pose(now).reading)
        assertNull(pose.pose(now).workInput)
        bridge.close()
    }

    @Test fun stopRejectsLateFocusAndRuntimeResume() {
        val bridge = ComputerWorkAvatarBridge(director, projectGaze = { target }, nanos = { now * 1_000_000 })
        bridge.bind("task", 9)
        bridge.render(event(1, CockpitWorkEvent.Stage.ACCEPTED))
        bridge.render(event(2, CockpitWorkEvent.Stage.STOPPED))
        bridge.render(event(3, CockpitWorkEvent.Stage.FOCUS))
        director.onRuntime(AvatarRuntimeSignals(busy = true))
        assertFalse(pose.pose(now).reading)
        assertNull(pose.pose(now).workInput)
        bridge.close()
    }

    @Test fun projectionMovementUpdatesWithoutReplayingInput() {
        var projected: GazeTarget? = target
        val bridge = ComputerWorkAvatarBridge(director, projectGaze = { projected }, nanos = { now * 1_000_000 })
        bridge.bind("task", 9)
        bridge.render(event(1, CockpitWorkEvent.Stage.ACCEPTED))
        assertEquals(target.x, pose.pose(now).gazeX, 0f)
        projected = GazeTarget(-.2f, -.3f)
        bridge.refreshProjection()
        assertEquals(-.2f, pose.pose(now).gazeX, 0f)
        assertNull(pose.pose(now).workInput)
        projected = null
        bridge.refreshProjection()
        assertFalse(pose.pose(now).reading)
        bridge.render(event(2, CockpitWorkEvent.Stage.STOPPED))
        projected = target
        bridge.refreshProjection()
        assertFalse(pose.pose(now).reading)
    }

    @Test fun missingProjectionDoesNotInventMonitorCoordinates() {
        val bridge = ComputerWorkAvatarBridge(director, nanos = { now * 1_000_000 })
        bridge.bind("task", 9)
        bridge.render(event(1, CockpitWorkEvent.Stage.ACCEPTED))
        assertFalse(pose.pose(now).reading)
        assertFalse(pose.pose(now).gazeDirected)
    }

    @Test fun restoredProjectionCannotRenewExpiredWork() {
        var projected: GazeTarget? = target
        val bridge = ComputerWorkAvatarBridge(director, projectGaze = { projected }, nanos = { now * 1_000_000 })
        bridge.bind("task", 9)
        bridge.render(event(1, CockpitWorkEvent.Stage.ACCEPTED))
        assertTrue(pose.pose(now).reading)
        projected = null
        bridge.refreshProjection()
        now += AvatarPoseReducer.MAX_WINDOW_WORK_MS
        projected = target
        bridge.refreshProjection()
        assertFalse(pose.pose(now).reading)
    }

    private fun event(sequence: Long, stage: CockpitWorkEvent.Stage): CockpitWorkEvent {
        now++
        return CockpitWorkEvent("task", 9, sequence, now * 1_000_000, now, stage,
            target = CockpitWorkEvent.Target(.7f, .6f))
    }
}
