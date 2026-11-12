package at.heinz.deviceai.cognitive

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import java.io.File
import java.util.PriorityQueue
import org.junit.Assert.*
import org.junit.Test

class ComputerWorkObservationExperimentTest {
    private enum class Outcome { MATCH, CONTRADICT, STOP }
    private data class Job(val at: Long, val order: Int, val body: () -> Unit)
    private data class Seen(val event: CockpitWorkEvent, val at: Long, val proof: Boolean)

    private class Trial(val outcome: Outcome, val observationMs: Long, val cueMs: Long) {
        val name = "${outcome.name.lowercase()}-o$observationMs-c$cueMs"
        var now = START
        private var serial = 0
        private val queue = PriorityQueue<Job>(compareBy<Job> { it.at }.thenBy { it.order })
        val events = mutableListOf<Seen>()
        val avatarInputs = mutableListOf<Long>()
        val cues = mutableListOf<Long>()
        var stopAt: Long? = null
        var inputs = 0
        var observationReturned = false
        var correctTextObserved = false
        var maxAvatarShoulder = 0f
        var maxWorkShoulder = 0f
        var maxShoulderAfterStop = 0f
        val pose = AvatarPoseReducer()
        val director = AvatarDirector(AvatarSink {
            if (it is AvatarEvent.WorkInput && it.kind != null) avatarInputs += now
            pose.apply(it)
        }) { now / MS }
        val sounds = ComputerWorkCuePlayer({ post(cueMs, it) }, { cues += now }, {}, { now })
        val bridge = ComputerWorkAvatarBridge(director, sounds) { now }
        private val bus = CockpitAnimationBus()
        private val intent = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEARCH, "Probe")
        private val grant = checkNotNull(OwnerComputerTaskGrant.fromOwnerDecision(
            TASK, PACKAGE, EPOCH, intent, true) { now / MS })
        private val flow: OwnerComputerWorkflow
        private val journal = LiveActionJournal(TASK)
        private val field = ActionNode("Suchen", "$PACKAGE:id/search_src_text", true, true,
            false, false, true, false, LiveTargetBounds(.1f, .1f, .8f, .2f), liveSha256(""))
        private var frame = NodeActionFrame(ObservationStamp(TASK, EPOCH, 1, PACKAGE, 4,
            "before", START - 1, 1, SESSION), true, true, listOf(field))
        lateinit var result: OwnerComputerWorkflow.Result

        init {
            bridge.bind(TASK, SESSION)
            bus.subscribe {
                if (it is CockpitWorkEvent) events += Seen(it, now, observationReturned && correctTextObserved)
            }
            bus.subscribe(bridge)
            flow = OwnerComputerWorkflow(TASK, PACKAGE, EPOCH,
                CockpitWorkEmitter(TASK, SESSION, bus, { now }, { now / MS }))
            if (outcome == Outcome.STOP) post(50) {
                stopAt = now
                grant.revoke()
                flow.cancel()
            }
        }

        private fun post(delayMs: Long, body: () -> Unit) {
            queue += Job(now + delayMs * MS, serial++, body)
        }

        private fun sample() {
            val avatar = pose.pose(now / MS)
            maxAvatarShoulder = maxOf(maxAvatarShoulder, avatar.rightShoulder)
            maxWorkShoulder = maxOf(maxWorkShoulder, bridge.reducer.snapshot(now).rightShoulder)
            if (stopAt?.let { now >= it } == true) {
                maxShoulderAfterStop = maxOf(maxShoulderAfterStop, avatar.rightShoulder)
            }
        }

        private fun advance(target: Long) {
            require(target >= now)
            while (true) {
                while (queue.peek()?.at?.let { it <= now } == true) queue.remove().body()
                sample()
                if (now == target) return
                now = minOf(target, now + 10 * MS, queue.peek()?.at ?: Long.MAX_VALUE)
            }
        }

        fun run(): Trial {
            result = flow.run(intent, object : OwnerComputerWorkflow.Port {
                override fun checkActive() = grant.checkActive()
                override fun observe() = frame
                override fun perform(request: NodeActionRequest, events: LiveActionEventSink): NodeActionResult {
                    checkActive()
                    grant.authorize(request, frame)
                    return VerifiedNodeAction(journal, events).execute(request, object : NodeActionPort {
                        override fun checkActive() = grant.checkActive()
                        override fun current() = frame
                        override fun performOnce(request: NodeActionRequest, target: ActionNode): Boolean {
                            inputs++
                            return true
                        }
                        override fun awaitPostcondition(): NodeActionFrame {
                            advance(START + observationMs * MS)
                            val observedText = if (outcome == Outcome.CONTRADICT) "Andere" else request.text!!
                            frame = frame.copy(stamp = frame.stamp.copy(revision = 2, digest = "after",
                                observedAtNanos = now, frameSequence = 2),
                                nodes = listOf(field.copy(textDigest = liveSha256(observedText))))
                            observationReturned = true
                            correctTextObserved = observedText == request.text
                            return frame
                        }
                    })
                }
                override fun click(before: NodeActionFrame, label: String, expectedLabel: String,
                                   expectedEditable: Boolean, expectedHeading: Boolean,
                                   expectedSearchField: Boolean,
                                   onDispatched: (LiveTargetBounds?, Long) -> Unit): NodeActionFrame =
                    error("SEARCH_ALREADY_VISIBLE")
            })
            advance(now + maxOf(cueMs + 1, 500) * MS)
            save()
            return this
        }

        fun count(stage: CockpitWorkEvent.Stage) = events.count { it.event.stage == stage }
        fun first(stage: CockpitWorkEvent.Stage) = events.firstOrNull { it.event.stage == stage }?.at
        fun earlySuccess() = events.count { it.event.stage in SUCCESS && !it.proof }
        private fun elapsed(value: Long?) = value?.let { (it - START).toDouble() / MS }

        private fun save() {
            val path = System.getProperty("continuum.experiment.output") ?: return
            val output = File(path)
            val finalPose = pose.pose(now / MS)
            val values = listOf(name, if (outcome != Outcome.MATCH) outcome.name else if (observationMs == 0L) "A" else "B",
                observationMs, cueMs, inputs, count(CockpitWorkEvent.Stage.DISPATCHED),
                count(CockpitWorkEvent.Stage.OBSERVED), count(CockpitWorkEvent.Stage.VERIFIED),
                count(CockpitWorkEvent.Stage.COMPLETED), earlySuccess(), result.status,
                bridge.reducer.snapshot(now).stage, avatarInputs.size, cues.size,
                elapsed(avatarInputs.firstOrNull()), elapsed(cues.firstOrNull()),
                elapsed(first(CockpitWorkEvent.Stage.VERIFIED)), elapsed(first(CockpitWorkEvent.Stage.COMPLETED)),
                maxAvatarShoulder, maxWorkShoulder, maxShoulderAfterStop,
                finalPose.workInput != null, finalPose.reading)
            if (!output.exists()) output.writeText(HEADER + "\n")
            output.appendText(values.joinToString(",") { it?.toString().orEmpty() } + "\n")
        }
    }

    private fun common(trial: Trial) {
        assertEquals(trial.name, 1, trial.inputs)
        assertEquals(trial.name, 1, trial.count(CockpitWorkEvent.Stage.DISPATCHED))
        assertEquals(trial.name, 0, trial.earlySuccess())
        assertEquals(trial.name, 1, trial.avatarInputs.size)
        assertEquals(trial.name, START, trial.avatarInputs.single())
        assertNull(trial.name, trial.pose.pose(trial.now / MS).workInput)
        assertFalse(trial.name, trial.pose.pose(trial.now / MS).reading)
        assertEquals(trial.name, 0f, trial.bridge.reducer.snapshot(trial.now).rightShoulder, 0f)
        if (trial.observationMs >= 70 && trial.outcome != Outcome.STOP) {
            assertTrue(trial.name, trial.maxAvatarShoulder > .5f)
            assertTrue(trial.name, trial.maxWorkShoulder > .5f)
        }
    }

    @Test
    fun immediateAndDelayedObservationsRemainCausal() {
        for (observation in OBSERVATIONS) for (cue in CUES) {
            val trial = Trial(Outcome.MATCH, observation, cue).run()
            common(trial)
            assertEquals(trial.name, OwnerComputerWorkflow.Status.VERIFIED, trial.result.status)
            assertEquals(trial.name, 1, trial.count(CockpitWorkEvent.Stage.OBSERVED))
            assertEquals(trial.name, 1, trial.count(CockpitWorkEvent.Stage.VERIFIED))
            assertEquals(trial.name, 1, trial.count(CockpitWorkEvent.Stage.COMPLETED))
            assertEquals(trial.name, START + observation * MS, trial.first(CockpitWorkEvent.Stage.COMPLETED))
            assertEquals(trial.name, CockpitWorkEvent.Stage.COMPLETED, trial.bridge.reducer.snapshot(trial.now).stage)
            assertEquals(trial.name, if (cue <= 150 && cue <= observation) 1 else 0, trial.cues.size)
        }
    }

    @Test
    fun contradictoryFreshObservationNeverReportsSuccess() {
        for (observation in OBSERVATIONS) for (cue in CUES) {
            val trial = Trial(Outcome.CONTRADICT, observation, cue).run()
            common(trial)
            assertEquals(trial.name, OwnerComputerWorkflow.Status.PAUSED, trial.result.status)
            assertEquals(trial.name, 1, trial.count(CockpitWorkEvent.Stage.OBSERVED))
            assertEquals(trial.name, 0, trial.count(CockpitWorkEvent.Stage.VERIFIED))
            assertEquals(trial.name, 0, trial.count(CockpitWorkEvent.Stage.COMPLETED))
            assertEquals(trial.name, CockpitWorkEvent.Stage.PAUSED, trial.bridge.reducer.snapshot(trial.now).stage)
            assertEquals(trial.name, if (cue <= 150 && cue <= observation) 1 else 0, trial.cues.size)
        }
    }

    @Test
    fun ownerStopInvalidatesLateSuccessAndQueuedCue() {
        for (cue in CUES) {
            val trial = Trial(Outcome.STOP, 500, cue).run()
            common(trial)
            assertNotEquals(trial.name, OwnerComputerWorkflow.Status.VERIFIED, trial.result.status)
            assertEquals(trial.name, 0, trial.count(CockpitWorkEvent.Stage.VERIFIED))
            assertEquals(trial.name, 0, trial.count(CockpitWorkEvent.Stage.COMPLETED))
            assertEquals(trial.name, CockpitWorkEvent.Stage.STOPPED, trial.bridge.reducer.snapshot(trial.now).stage)
            assertTrue(trial.name, trial.cues.all { it < checkNotNull(trial.stopAt) })
            assertEquals(trial.name, if (cue == 0L) 1 else 0, trial.cues.size)
            assertEquals(trial.name, 0f, trial.maxShoulderAfterStop, 0f)
        }
    }

    companion object {
        private const val START = 1_000_000_000L
        private const val MS = 1_000_000L
        private const val TASK = "observation-experiment"
        private const val PACKAGE = "com.whatsapp"
        private const val EPOCH = 47L
        private const val SESSION = 9L
        private val OBSERVATIONS = listOf(0L, 70L, 500L, 1500L)
        private val CUES = listOf(0L, 149L, 150L, 151L)
        private val SUCCESS = setOf(CockpitWorkEvent.Stage.VERIFIED, CockpitWorkEvent.Stage.COMPLETED)
        private const val HEADER = "case,arm,observation_ms,cue_post_ms,native_inputs,dispatched,observed,verified,completed," +
            "early_success,result,final_stage,avatar_inputs,cues,avatar_latency_ms,cue_latency_ms," +
            "verified_latency_ms,completed_latency_ms,max_avatar_shoulder,max_work_shoulder," +
            "max_shoulder_after_stop,final_work_input,final_reading"
    }
}
