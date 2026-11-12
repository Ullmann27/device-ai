package at.heinz.deviceai.cognitive

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import org.junit.Assert.*
import org.junit.Test

class CockpitWorkEventTest {
    private fun event(seq: Long, stage: CockpitWorkEvent.Stage, action: CockpitWorkEvent.Action? = null,
                      revision: Long = 0, step: String? = null, time: Long = seq * 1_000_000) =
        CockpitWorkEvent("task", 9, seq, time, seq, stage, step, action,
            CockpitWorkEvent.Target(.4f, .6f), revision)

    @Test fun bindAndFreshOrderingAreRequired() {
        val r = CockpitWorkReducer()
        assertFalse(r.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 10_000_000))
        r.bind("task", 9)
        assertFalse(r.apply(event(2, CockpitWorkEvent.Stage.DISPATCHED, CockpitWorkEvent.Action.CLICK, 2, "click"), 10_000_000))
        assertTrue(r.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 10_000_000))
        assertFalse(r.apply(event(2, CockpitWorkEvent.Stage.FOCUS).copy(sessionId = 10), 10_000_000))
        assertFalse(r.apply(event(2, CockpitWorkEvent.Stage.FOCUS).copy(taskId = "other"), 10_000_000))
        assertFalse(r.apply(event(2, CockpitWorkEvent.Stage.FOCUS, time = 20_000_000), 10_000_000))
        assertTrue(r.apply(event(2, CockpitWorkEvent.Stage.FOCUS), 10_000_000))
    }

    @Test fun dispatchIsNotVerifiedUntilNewObservation() {
        val r = CockpitWorkReducer().also { it.bind("task", 9) }
        r.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 20_000_000)
        r.apply(event(2, CockpitWorkEvent.Stage.DISPATCHED, CockpitWorkEvent.Action.SET_TEXT, 5, "type"), 20_000_000)
        assertFalse(r.apply(event(3, CockpitWorkEvent.Stage.VERIFIED, revision = 6, step = "type"), 20_000_000))
        assertFalse(r.apply(event(3, CockpitWorkEvent.Stage.OBSERVED, revision = 5, step = "type"), 20_000_000))
        assertTrue(r.apply(event(3, CockpitWorkEvent.Stage.OBSERVED, revision = 6, step = "type"), 20_000_000))
        assertTrue(r.apply(event(4, CockpitWorkEvent.Stage.VERIFIED, revision = 6, step = "type"), 20_000_000))
        assertFalse(r.apply(event(5, CockpitWorkEvent.Stage.DISPATCHED, CockpitWorkEvent.Action.SET_TEXT, 7, "type"), 20_000_000))
    }

    @Test fun bulkTextHasOneBoundedShoulderPulse() {
        val r = CockpitWorkReducer().also { it.bind("task", 9) }
        r.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 2_000_000)
        r.apply(event(2, CockpitWorkEvent.Stage.DISPATCHED, CockpitWorkEvent.Action.SET_TEXT, 5, "type"), 2_000_000)
        assertTrue(r.snapshot(142_000_000).leftShoulder > .5f)
        assertTrue(r.snapshot(142_000_000).rightShoulder > .7f)
        assertEquals(0f, r.snapshot(283_000_000).rightShoulder)
        assertEquals(-1f, r.snapshot(1_000_000_000).inputProgress)
    }

    @Test fun stoppedSessionCannotReviveFromLateCallbacks() {
        val r = CockpitWorkReducer().also { it.bind("task", 9) }
        r.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 20_000_000)
        r.apply(event(2, CockpitWorkEvent.Stage.DISPATCHED, CockpitWorkEvent.Action.CLICK, 5, "click"), 20_000_000)
        assertTrue(r.apply(event(3, CockpitWorkEvent.Stage.STOPPED), 20_000_000))
        r.bind("task", 9)
        assertFalse(r.apply(event(4, CockpitWorkEvent.Stage.ACCEPTED), 20_000_000))
        assertFalse(r.apply(event(5, CockpitWorkEvent.Stage.OBSERVED, revision = 6, step = "click"), 20_000_000))
        assertFalse(r.snapshot(50_000_000).active)
        assertEquals(0f, r.snapshot(50_000_000).clickPulse)
    }

    @Test fun cueQueueDropsStoppedAndStaleClicks() {
        var now = 5_000_000L
        var played = 0
        var stopped = 0
        val queue = ArrayDeque<() -> Unit>()
        val sound = ComputerWorkCuePlayer({ queue.addLast(it) }, { played++ }, { stopped++ }, { now })
        val click = event(2, CockpitWorkEvent.Stage.DISPATCHED, CockpitWorkEvent.Action.CLICK, 1, "click")
        sound.dispatched(click)
        sound.stop()
        while (queue.isNotEmpty()) queue.removeFirst().invoke()
        assertEquals(0, played)
        assertEquals(1, stopped)
        sound.dispatched(click)
        now = 200_000_000L
        queue.removeFirst().invoke()
        assertEquals(0, played)
        now = 6_000_000L
        sound.dispatched(click)
        queue.removeFirst().invoke()
        assertEquals(1, played)
    }

    @Test fun bridgeUsesActualDispatchAndStopsPose() {
        var nanos = 1_000_000L
        val pose = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink(pose::apply)) { nanos / 1_000_000 }
        val bus = CockpitAnimationBus()
        val bridge = ComputerWorkAvatarBridge(director, nanos = { nanos })
        bridge.bind("task", 9)
        bus.subscribe(bridge)
        val emitter = CockpitWorkEmitter("task", 9, bus, { nanos }, { nanos / 1_000_000 })
        emitter.emit(CockpitWorkEvent.Stage.ACCEPTED)
        assertNull(pose.pose(2).workInput)
        nanos = 2_000_000
        emitter.emit(CockpitWorkEvent.Stage.DISPATCHED, "click", CockpitWorkEvent.Action.CLICK,
            CockpitWorkEvent.Target(.9f, .5f), 2)
        assertEquals(AvatarWorkInput.CLICK, pose.pose(110).workInput)
        assertTrue(pose.pose(110).rightShoulder > .9f)
        nanos = 111_000_000
        emitter.emit(CockpitWorkEvent.Stage.STOPPED)
        assertNull(pose.pose(112).workInput)
        assertFalse(emitter.emit(CockpitWorkEvent.Stage.DISPATCHED, "late", CockpitWorkEvent.Action.CLICK, observationRevision = 3))
    }

    @Test fun sourceEpochIsSeparateFromCaptureSession() {
        val bus = CockpitAnimationBus()
        val seen = mutableListOf<CockpitWorkEvent>()
        bus.subscribe { if (it is CockpitWorkEvent) seen += it }
        val adapter = LiveActionCockpitAdapter(CockpitWorkEmitter("task", 9, bus), 47)
        adapter.emit(LiveActionEvent.Observed("task", "input", ObservationStamp("task", 47, 8, "com.example.app", 1, "digest", 1)))
        assertEquals(1, seen.size)
        adapter.emit(LiveActionEvent.Observed("task", "input", ObservationStamp("task", 48, 9, "com.example.app", 1, "digest2", 2)))
        assertEquals(1, seen.size)
    }
}
