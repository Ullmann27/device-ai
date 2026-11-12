package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import kotlin.math.PI
import kotlin.math.sin

/** Real work on the existing bus. */
data class CockpitWorkEvent(
    val taskId: String,
    val sessionId: Long,
    val sequence: Long,
    val occurredAtNanos: Long,
    override val atMillis: Long,
    val stage: Stage,
    val stepId: String? = null,
    val action: Action? = null,
    val target: Target? = null,
    val observationRevision: Long = 0,
    val frameSequence: Long = 0
) : CockpitAnimationEvent.Extension("live-computer-v1") {
    override val turnId: String get() = taskId

    enum class Stage { ACCEPTED, FOCUS, DISPATCHED, OBSERVED, VERIFIED, ASK_OWNER, PAUSED, STOPPED, ERROR, COMPLETED }
    enum class Action { OPEN_APP, CLICK, SET_TEXT, SCROLL }
    data class Target(val x: Float, val y: Float) {
        init { require(x in 0f..1f && y in 0f..1f) }
    }

    init {
        require(taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(sessionId > 0 && sequence > 0 && occurredAtNanos >= 0)
        require(stepId == null || stepId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(observationRevision >= 0 && frameSequence >= 0)
    }
}

/** No owner text enters animation. */
class CockpitWorkEmitter(
    val taskId: String,
    val sessionId: Long,
    private val bus: CockpitAnimationBus,
    private val nanos: () -> Long = System::nanoTime,
    private val wallMillis: () -> Long = System::currentTimeMillis
) {
    private var sequence = 0L
    private var ended = false

    @Synchronized fun emit(
        stage: CockpitWorkEvent.Stage,
        stepId: String? = null,
        action: CockpitWorkEvent.Action? = null,
        target: CockpitWorkEvent.Target? = null,
        observationRevision: Long = 0,
        frameSequence: Long = 0
    ): Boolean {
        if (ended) return false
        val event = CockpitWorkEvent(taskId, sessionId, ++sequence, nanos(), wallMillis(), stage,
            stepId, action, target, observationRevision, frameSequence)
        if (stage in TERMINAL) ended = true
        bus.emit(event)
        return true
    }

    companion object {
        val TERMINAL = setOf(CockpitWorkEvent.Stage.STOPPED, CockpitWorkEvent.Stage.ERROR,
            CockpitWorkEvent.Stage.COMPLETED)
    }
}

/** Bounded presentation, never device authority. */
class CockpitWorkReducer {
    data class Snapshot(
        val taskId: String?, val sessionId: Long?, val stage: CockpitWorkEvent.Stage?,
        val action: CockpitWorkEvent.Action?, val target: CockpitWorkEvent.Target?,
        val inputProgress: Float, val clickPulse: Float,
        val leftShoulder: Float, val rightShoulder: Float,
        val observationRevision: Long, val frameSequence: Long
    ) {
        val active: Boolean get() = stage != null && stage !in CockpitWorkEmitter.TERMINAL
        val working: Boolean get() = active && stage !in setOf(CockpitWorkEvent.Stage.ASK_OWNER,
            CockpitWorkEvent.Stage.PAUSED)
    }

    private var taskId: String? = null
    private var sessionId: Long? = null
    private var sequence = 0L
    private var newestTime = Long.MIN_VALUE
    private var stage: CockpitWorkEvent.Stage? = null
    private var lastAction: CockpitWorkEvent.Action? = null
    private var target: CockpitWorkEvent.Target? = null
    private var inputAt = Long.MIN_VALUE
    private var pendingStep: String? = null
    private var beforeRevision = 0L
    private var observationRevision = 0L
    private var frameSequence = 0L
    private val dispatched = LinkedHashSet<String>()

    @Synchronized fun bind(taskId: String, sessionId: Long) {
        if (this.taskId == taskId && this.sessionId == sessionId) return
        require(taskId.isNotBlank() && sessionId > 0)
        this.taskId = taskId; this.sessionId = sessionId
        sequence = 0; newestTime = Long.MIN_VALUE; stage = null
        lastAction = null; target = null; inputAt = Long.MIN_VALUE
        pendingStep = null; beforeRevision = 0; observationRevision = 0; frameSequence = 0
        dispatched.clear()
    }

    @Synchronized fun apply(event: CockpitWorkEvent, nowNanos: Long): Boolean {
        if (event.taskId != taskId || event.sessionId != sessionId ||
            event.sequence <= sequence || event.occurredAtNanos < newestTime ||
            event.occurredAtNanos > nowNanos || stage in CockpitWorkEmitter.TERMINAL) return false
        if (stage == null && event.stage != CockpitWorkEvent.Stage.ACCEPTED) return false
        if (stage != null && event.stage == CockpitWorkEvent.Stage.ACCEPTED) return false
        when (event.stage) {
            CockpitWorkEvent.Stage.DISPATCHED -> {
                val id = event.stepId ?: return false
                if (event.action == null || pendingStep != null || id in dispatched || dispatched.size >= MAX_STEPS ||
                    event.observationRevision <= 0) return false
                dispatched += id
                pendingStep = id
                beforeRevision = event.observationRevision
                lastAction = event.action
                target = event.target
                inputAt = event.occurredAtNanos
            }
            CockpitWorkEvent.Stage.OBSERVED -> {
                if (event.stepId != pendingStep || pendingStep == null ||
                    event.observationRevision <= beforeRevision) return false
                observationRevision = event.observationRevision
                frameSequence = event.frameSequence
            }
            CockpitWorkEvent.Stage.VERIFIED -> {
                if (event.stepId != pendingStep || pendingStep == null ||
                    observationRevision <= beforeRevision || event.observationRevision != observationRevision) return false
                pendingStep = null
            }
            CockpitWorkEvent.Stage.FOCUS -> {
                if (pendingStep != null) return false
                target = event.target
            }
            CockpitWorkEvent.Stage.COMPLETED -> if (pendingStep != null) return false
            else -> Unit
        }
        if (event.stage in setOf(CockpitWorkEvent.Stage.ASK_OWNER, CockpitWorkEvent.Stage.PAUSED) ||
            event.stage in CockpitWorkEmitter.TERMINAL) {
            inputAt = Long.MIN_VALUE; lastAction = null; target = null; pendingStep = null
        }
        sequence = event.sequence; newestTime = event.occurredAtNanos; stage = event.stage
        return true
    }

    @Synchronized fun snapshot(nowNanos: Long): Snapshot {
        val duration = when (lastAction) {
            CockpitWorkEvent.Action.CLICK -> 220_000_000L
            CockpitWorkEvent.Action.SET_TEXT -> 280_000_000L
            CockpitWorkEvent.Action.SCROLL -> 420_000_000L
            else -> 0L
        }
        val elapsed = if (inputAt == Long.MIN_VALUE) Long.MAX_VALUE else nowNanos - inputAt
        val progress = if (duration > 0 && elapsed in 0 until duration) elapsed.toFloat() / duration else -1f
        val pulse = if (progress >= 0) sin(progress * PI).toFloat() else 0f
        return Snapshot(taskId, sessionId, stage, lastAction, target, progress,
            if (lastAction == CockpitWorkEvent.Action.CLICK) pulse else 0f,
            if (lastAction == CockpitWorkEvent.Action.SET_TEXT) pulse * .6f else 0f,
            if (lastAction == CockpitWorkEvent.Action.SET_TEXT) pulse * .8f else pulse,
            observationRevision, frameSequence)
    }

    companion object { const val MAX_STEPS = 64 }
}
