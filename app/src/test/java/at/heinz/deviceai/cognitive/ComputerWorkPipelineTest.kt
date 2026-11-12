package at.heinz.deviceai.cognitive

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import org.junit.Assert.*
import org.junit.Test

class ComputerWorkPipelineTest {
    private class Scene {
        var now = 100_000_000L
        var played = 0
        var stopped = 0
        val queue = ArrayDeque<() -> Unit>()
        val pose = AvatarPoseReducer()
        val avatarEvents = mutableListOf<AvatarEvent>()
        val director = AvatarDirector(AvatarSink { avatarEvents += it; pose.apply(it) }) { now / 1_000_000 }
        val sounds = ComputerWorkCuePlayer({ queue.addLast(it) }, { played++ }, { stopped++ }, { now })
        val bridge = ComputerWorkAvatarBridge(director, sounds) { now }
        val bus = CockpitAnimationBus()
        val events = mutableListOf<Pair<CockpitWorkEvent, CockpitWorkReducer.Snapshot>>()

        init {
            bus.subscribe(bridge)
            bus.subscribe { if (it is CockpitWorkEvent) events += it to bridge.reducer.snapshot(now) }
        }

        fun bind(task: String = "task", session: Long = 9): CockpitWorkEmitter {
            bridge.bind(task, session)
            return CockpitWorkEmitter(task, session, bus, { now }, { now / 1_000_000 })
        }

        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().invoke() }
    }

    private class SearchPort(private val scene: Scene) : OwnerComputerWorkflow.Port {
        private val field = ActionNode("Suchen", "com.whatsapp:id/search_src_text", true, true,
            false, false, true, false, LiveTargetBounds(.1f, .1f, .8f, .2f))
        private var frame = NodeActionFrame(ObservationStamp("task", 47, 1, "com.whatsapp", 4,
            "before", scene.now, 1), true, true, listOf(field))
        private val journal = LiveActionJournal("task")
        val actions = mutableListOf<NodeActionRequest>()
        var dispatches = 0
        var poseDuringInput: AvatarPose? = null
        var afterDispatch: (() -> Unit)? = null

        override fun checkActive() = Unit
        override fun observe() = frame

        override fun perform(request: NodeActionRequest, events: LiveActionEventSink): NodeActionResult {
            actions += request
            return VerifiedNodeAction(journal, events).execute(request, object : NodeActionPort {
                override fun checkActive() = Unit
                override fun current() = frame
                override fun performOnce(request: NodeActionRequest, target: ActionNode): Boolean {
                    dispatches++
                    return true
                }
                override fun awaitPostcondition(): NodeActionFrame {
                    scene.now += 70_000_000
                    poseDuringInput = scene.pose.pose(scene.now / 1_000_000)
                    afterDispatch?.invoke()
                    scene.drain()
                    frame = frame.copy(stamp = frame.stamp.copy(revision = 2, digest = "after",
                        observedAtNanos = scene.now, frameSequence = 2),
                        nodes = listOf(field.copy(textDigest = liveSha256(checkNotNull(request.text)))))
                    return frame
                }
            })
        }

        override fun click(before: NodeActionFrame, label: String, expectedLabel: String,
                           expectedEditable: Boolean, expectedHeading: Boolean,
                           expectedSearchField: Boolean,
                           onDispatched: (LiveTargetBounds?, Long) -> Unit): NodeActionFrame =
            error("SEARCH_ALREADY_VISIBLE")
    }

    private fun search() = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEARCH, "Anna")

    @Test fun realVerifiedActionReachesBridgeAndEndsMotion() {
        val scene = Scene()
        val flow = OwnerComputerWorkflow("task", "com.whatsapp", 47, scene.bind())
        val port = SearchPort(scene)

        val result = flow.run(search(), port)
        scene.drain()

        assertEquals(OwnerComputerWorkflow.Status.VERIFIED, result.status)
        assertEquals(1, port.dispatches)
        assertEquals(listOf(NodeActionKind.SET_TEXT), port.actions.map { it.kind })
        assertEquals(AvatarWorkInput.TEXT_SET, port.poseDuringInput?.workInput)
        assertTrue(checkNotNull(port.poseDuringInput).rightShoulder > .5f)
        val verified = scene.events.single { it.first.stage == CockpitWorkEvent.Stage.VERIFIED }
        assertEquals(CockpitWorkEvent.Stage.VERIFIED, verified.second.stage)
        assertEquals(2L, verified.second.observationRevision)
        assertEquals(CockpitWorkEvent.Stage.COMPLETED, scene.bridge.reducer.snapshot(scene.now).stage)
        assertEquals(1, scene.played)
        assertNull(scene.pose.pose(scene.now / 1_000_000).workInput)
        assertFalse(scene.pose.pose(scene.now / 1_000_000).reading)
    }

    @Test fun stopRejectsLateRealSuccessAndQueuedSound() {
        val scene = Scene()
        val flow = OwnerComputerWorkflow("task", "com.whatsapp", 47, scene.bind())
        val port = SearchPort(scene).apply { afterDispatch = flow::cancel }

        val result = flow.run(search(), port)
        scene.drain()

        assertEquals(OwnerComputerWorkflow.Status.CANCELLED, result.status)
        assertEquals(1, port.dispatches)
        assertEquals(CockpitWorkEvent.Stage.STOPPED, scene.bridge.reducer.snapshot(scene.now).stage)
        assertFalse(scene.events.any { it.first.stage == CockpitWorkEvent.Stage.VERIFIED })
        assertEquals(0, scene.played)
        assertNull(scene.pose.pose(scene.now / 1_000_000).workInput)
        assertFalse(scene.pose.pose(scene.now / 1_000_000).reading)
    }

    @Test fun differentTasksInSameSessionEachAnimate() {
        val scene = Scene()
        for (task in listOf("task-A", "task-B")) {
            val source = scene.bind(task)
            source.emit(CockpitWorkEvent.Stage.ACCEPTED)
            source.emit(CockpitWorkEvent.Stage.DISPATCHED, "computer-1", CockpitWorkEvent.Action.SET_TEXT,
                observationRevision = 1)
            scene.now += 70_000_000
            assertEquals(task, AvatarWorkInput.TEXT_SET, scene.pose.pose(scene.now / 1_000_000).workInput)
            scene.drain()
            source.emit(CockpitWorkEvent.Stage.STOPPED)
            scene.now += 1_000_000
        }
        val inputs = scene.avatarEvents.filterIsInstance<AvatarEvent.WorkInput>().filter { it.kind != null }
        assertEquals(2, inputs.size)
        assertEquals(2, inputs.map { it.actionId }.distinct().size)
        assertEquals(2, scene.played)
    }

    @Test fun rebindingDropsOldCallbacksAndQueuedSound() {
        val scene = Scene()
        val old = scene.bind("old", 9)
        old.emit(CockpitWorkEvent.Stage.ACCEPTED)
        old.emit(CockpitWorkEvent.Stage.DISPATCHED, "computer-1", CockpitWorkEvent.Action.CLICK,
            observationRevision = 1)
        val current = scene.bind("current", 10)
        current.emit(CockpitWorkEvent.Stage.ACCEPTED)
        old.emit(CockpitWorkEvent.Stage.OBSERVED, "computer-1", observationRevision = 2)
        old.emit(CockpitWorkEvent.Stage.VERIFIED, "computer-1", observationRevision = 2)
        old.emit(CockpitWorkEvent.Stage.STOPPED)
        scene.drain()
        assertEquals(0, scene.played)
        assertEquals(CockpitWorkEvent.Stage.ACCEPTED, scene.bridge.reducer.snapshot(scene.now).stage)
        current.emit(CockpitWorkEvent.Stage.DISPATCHED, "computer-1", CockpitWorkEvent.Action.CLICK,
            observationRevision = 1)
        scene.drain()
        assertEquals(1, scene.played)
        scene.bridge.close()
        current.emit(CockpitWorkEvent.Stage.OBSERVED, "computer-1", observationRevision = 2)
        current.emit(CockpitWorkEvent.Stage.VERIFIED, "computer-1", observationRevision = 2)
        scene.drain()
        assertEquals(1, scene.played)
        assertNull(scene.pose.pose(scene.now / 1_000_000).workInput)
    }
}
