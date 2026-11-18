package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cognitive.CockpitWorkEmitter
import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.CockpitWorkReducer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Projects the real execution events of one owner-approved computer task into the bounded "computerWork" record of
 * the Command03 snapshot, so the page's monitor can show what was really done: where a tap was dispatched, whether
 * text was entered or the view scrolled, and whether the effect was confirmed.
 *
 * It listens on the existing cockpit bus to the same [CockpitWorkEvent]s the native monitor draws its click ring
 * from, and admits them with the existing [CockpitWorkReducer] – no second event system and no second rule set.
 * Only [at.heinz.deviceai.OwnerComputerSessionController] produces these events, and it dispatches nothing without
 * the owner's task approval; a listed action therefore always ran inside an approved task. No owner text, no label
 * of a tapped element and no pixels are carried – only kind, state and the normalized target the producer measured.
 * Nothing is synthesized: without an event there is no record, and an action whose effect was not confirmed is
 * reported as exactly that.
 */
class Command03ComputerWorkProjection(private val nanos: () -> Long = System::nanoTime) : CockpitAnimationRenderer {
    private class Action(val sequence: Long, val step: String, val kind: CockpitWorkEvent.Action,
        val target: CockpitWorkEvent.Target?, val frameSequence: Long, val observationRevision: Long) {
        var state = DISPATCHED
    }

    private val reducer = CockpitWorkReducer()
    private var taskId: String? = null
    private var captureSession = 0L
    private var stage: CockpitWorkEvent.Stage? = null
    private var chatSession: String? = null
    private val actions = ArrayDeque<Action>()

    @Synchronized override fun render(event: CockpitAnimationEvent) {
        if (event !is CockpitWorkEvent) return
        val time = maxOf(nanos(), event.occurredAtNanos)
        if (event.stage == CockpitWorkEvent.Stage.ACCEPTED &&
            (event.taskId != taskId || event.sessionId != captureSession)) {
            // A new approved task: the previous record ends where the reducer admits the new one.
            reducer.bind(event.taskId, event.sessionId)
            if (!reducer.apply(event, time)) return
            taskId = event.taskId; captureSession = event.sessionId
            stage = event.stage; chatSession = null; actions.clear()
            return
        }
        if (event.taskId != taskId || event.sessionId != captureSession) return
        if (!reducer.apply(event, time)) return
        stage = event.stage
        when (event.stage) {
            CockpitWorkEvent.Stage.DISPATCHED -> {
                val kind = event.action ?: return
                val step = event.stepId ?: return
                actions.addLast(Action(event.sequence, step, kind, event.target, event.frameSequence,
                    event.observationRevision))
                while (actions.size > MAX_ACTIONS) actions.removeFirst()
            }
            CockpitWorkEvent.Stage.VERIFIED ->
                actions.lastOrNull { it.step == event.stepId && it.state == DISPATCHED }?.state = VERIFIED
            CockpitWorkEvent.Stage.ASK_OWNER, CockpitWorkEvent.Stage.PAUSED -> unconfirmPending()
            else -> if (event.stage in CockpitWorkEmitter.TERMINAL) unconfirmPending()
        }
    }

    /** A dispatched input whose effect was never verified stays what it is: not confirmed. */
    private fun unconfirmPending() { actions.forEach { if (it.state == DISPATCHED) it.state = UNCONFIRMED } }

    /**
     * The record for [session] (the chat it is shown in), or null when there is nothing real to show. [panel] is the
     * cockpit's own panel state of the running task (app and goal as the owner approved them); [consentPending] is
     * true while Android's screen-capture consent has been requested and not yet answered or applied.
     * A record never follows the owner into another chat.
     */
    @Synchronized fun snapshot(session: String, panel: ComputerPanelState?, consentPending: Boolean): JSONObject? {
        val task = taskId
        if (task != null) {
            if (chatSession == null) chatSession = session
            else if (chatSession != session) clear()
        }
        val current = taskId
        if (current == null) return if (consentPending) JSONObject().put("consentPending", true) else null
        val list = JSONArray()
        actions.forEach { action ->
            val item = JSONObject().put("sequence", action.sequence).put("kind", action.kind.name)
                .put("state", action.state).put("frameSequence", action.frameSequence)
                .put("observationRevision", action.observationRevision)
            action.target?.let { item.put("x", it.x.toDouble()).put("y", it.y.toDouble()) }
            list.put(item)
        }
        val data = JSONObject().put("consentPending", consentPending).put("taskId", current)
            .put("captureSession", captureSession.toString())
            .put("stage", stage?.name ?: JSONObject.NULL).put("actions", list)
        panel?.takeIf { it.taskId == current && it.sessionId == captureSession }?.let {
            data.put("app", bounded(it.appLabel, MAX_LABEL)).put("goal", bounded(it.goalLabel, MAX_LABEL))
        }
        return data
    }

    @Synchronized fun clear() {
        taskId = null; captureSession = 0L; stage = null; chatSession = null; actions.clear()
    }

    private fun bounded(text: String, max: Int): String {
        val clean = text.filter { !it.isISOControl() }.trim()
        if (clean.length <= max) return clean
        var end = max - 1
        if (end > 0 && clean[end - 1].isHighSurrogate()) end--
        return clean.substring(0, end) + "…"
    }

    companion object {
        const val MAX_ACTIONS = 6
        const val MAX_LABEL = 80
        const val DISPATCHED = "DISPATCHED"
        const val VERIFIED = "VERIFIED"
        const val UNCONFIRMED = "UNCONFIRMED"
    }
}
