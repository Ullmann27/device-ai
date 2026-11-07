package at.heinz.deviceai.cockpit

import at.heinz.deviceai.chief.TurnMilestone
import at.heinz.deviceai.chief.WorkerReport
import at.heinz.deviceai.chief.WorkerStatus

/**
 * Maps real Continuum facts to [CockpitAnimationEvent]s. Callers are the existing paths only:
 * TurnLifecycle.acknowledge/milestone/deliver, the ToolGate runner (actual tool start/finish) and
 * ChiefWorkerPool submit/report. Nothing here guesses: a tool *name* never produces an account or
 * document card – those need the explicit [accountToolUsed] / [documentReceived] intake calls.
 *
 * Thread-safe; one bounded step list per turn (at most [MAX_STEPS], at most [MAX_TURNS] turns kept).
 */
class CockpitAnimationTriggers(private val bus: CockpitAnimationBus, private val clock: () -> Long = System::currentTimeMillis) {
    private val timelines = LinkedHashMap<String, MutableList<ProcessStep>>()
    private val ended = LinkedHashSet<String>()

    fun commandAcknowledged(turnId: String, command: String) {
        val steps = synchronized(this) {
            if (turnId in ended || turnId in timelines) return
            val list = open(turnId)
            list += ProcessStep("ack", "Auftrag angenommen", StepState.DONE)
            list += ProcessStep("work", "Bearbeitung", StepState.ACTIVE)
            list.toList()
        }
        val now = clock()
        bus.emit(CockpitAnimationEvent.CommandFlyIn(turnId, now, clean(command, 60)))
        bus.emit(CockpitAnimationEvent.ActionStarted(turnId, now, CockpitAction.THINK, "Auftrag angenommen", stepKey = "ack"))
        bus.emit(CockpitAnimationEvent.ProcessTimeline(turnId, now, steps))
    }

    fun milestone(turnId: String, kind: TurnMilestone, detail: String = "") {
        val text = clean(detail, 48).let { if (it.isEmpty()) kind.label else kind.label + ": " + it }
        val state = when (kind) {
            TurnMilestone.APPROVAL_NEEDED -> StepState.WAITING
            TurnMilestone.BLOCKED -> StepState.BLOCKED
            TurnMilestone.TOOL_VERIFIED, TurnMilestone.WORKER_REPORTED -> StepState.DONE
            TurnMilestone.TOOL_STARTED, TurnMilestone.WORKER_STARTED -> StepState.ACTIVE
        }
        // A tool start is already a precise step from the runner; the milestone only adds non-duplicates.
        if (kind == TurnMilestone.TOOL_STARTED && detail.isBlank()) return
        update(turnId) { it += ProcessStep("m:" + kind.name + ":" + it.size, text, state) }
    }

    fun toolStarted(turnId: String, callId: String, toolName: String) {
        val action = if (toolName in RESEARCH_TOOLS) CockpitAction.RESEARCH else CockpitAction.TOOL
        val label = (if (action == CockpitAction.RESEARCH) "Recherche: " else "Werkzeug: ") + clean(toolName, 32)
        val key = "tool:" + clean(callId, 40)
        if (!update(turnId) { it += ProcessStep(key, label, StepState.ACTIVE) }) return
        bus.emit(CockpitAnimationEvent.ActionStarted(turnId, clock(), action, clean(toolName, 32), stepKey = key))
    }

    fun toolFinished(turnId: String, callId: String, toolName: String, succeeded: Boolean) {
        val key = "tool:" + clean(callId, 40)
        update(turnId) { list ->
            val index = list.indexOfFirst { it.key == key }
            if (index >= 0) list[index] = list[index].copy(state = if (succeeded) StepState.DONE else StepState.FAILED)
        }
    }

    fun workerStarted(workerId: String, goal: String) {
        val steps = synchronized(this) {
            if (workerId in ended || workerId in timelines) return
            open(workerId).also { it += ProcessStep("worker", "Hintergrund: " + clean(goal, 40), StepState.ACTIVE) }.toList()
        }
        val now = clock()
        bus.emit(CockpitAnimationEvent.ActionStarted(workerId, now, CockpitAction.WORKER, clean(goal, 40), stepKey = "worker"))
        bus.emit(CockpitAnimationEvent.ProcessTimeline(workerId, now, steps))
    }

    fun workerReported(report: WorkerReport) {
        val ok = report.status == WorkerStatus.DONE
        val changed = update(report.id) { list ->
            for (i in list.indices) if (list[i].state == StepState.ACTIVE)
                list[i] = list[i].copy(state = if (ok) StepState.DONE else StepState.FAILED)
        }
        if (changed) end(report.id, ok)
    }

    fun turnEnded(turnId: String, delivered: Boolean) {
        val changed = update(turnId) { list ->
            for (i in list.indices) if (list[i].state == StepState.ACTIVE || list[i].state == StepState.WAITING)
                list[i] = list[i].copy(state = if (delivered) StepState.DONE else StepState.FAILED)
        }
        if (changed) end(turnId, delivered)
    }

    /** (d) Only for a real account tool execution. No such path exists yet (Quelle fehlt, see docs). */
    fun accountToolUsed(turnId: String, provider: String, accountLabel: String) {
        if (isClosed(turnId)) return
        bus.emit(CockpitAnimationEvent.AccountCardFlyIn(turnId, clock(), clean(provider, 24), clean(accountLabel, 64)))
    }

    /** (e) Only for a real document intake. No general PDF intake exists yet (Quelle fehlt, see docs). */
    fun documentReceived(turnId: String, documentName: String, mimeType: String, question: String) {
        if (isClosed(turnId)) return
        bus.emit(CockpitAnimationEvent.DocumentQuestion(turnId, clock(), clean(documentName, 64), clean(mimeType, 64),
            clean(question, 120)))
    }

    @Synchronized fun timeline(turnId: String): List<ProcessStep> = timelines[turnId]?.toList().orEmpty()

    /** Steps of the most recent turn that has not ended yet – what a work window's status strip shows; empty when none runs. */
    @Synchronized fun activeTimeline(): List<ProcessStep> =
        timelines.entries.lastOrNull { it.key !in ended }?.value?.toList().orEmpty()

    /** Most recent turn/worker timeline – what the "Aktuelle Prozesse" card shows. */
    @Synchronized fun latestTimeline(): List<ProcessStep> = timelines.values.lastOrNull()?.toList().orEmpty()

    @Synchronized private fun isClosed(turnId: String) = turnId in ended

    private fun open(turnId: String): MutableList<ProcessStep> {
        while (timelines.size >= MAX_TURNS) timelines.remove(timelines.keys.first())
        return mutableListOf<ProcessStep>().also { timelines[turnId] = it }
    }

    /** Applies [change] to an open turn and emits the full list; false for closed or unknown turns. */
    private fun update(turnId: String, change: (MutableList<ProcessStep>) -> Unit): Boolean {
        val steps = synchronized(this) {
            if (turnId in ended) return false
            val list = timelines[turnId] ?: return false
            change(list)
            while (list.size > MAX_STEPS) list.removeAt(0)
            list.toList()
        }
        bus.emit(CockpitAnimationEvent.ProcessTimeline(turnId, clock(), steps))
        return true
    }

    private fun end(turnId: String, delivered: Boolean) {
        synchronized(this) {
            ended += turnId
            while (ended.size > MAX_TURNS * 4) ended.remove(ended.first())
        }
        bus.emit(CockpitAnimationEvent.TurnEnded(turnId, clock(), delivered))
    }

    companion object {
        const val MAX_STEPS = 8
        const val MAX_TURNS = 6
        val RESEARCH_TOOLS = setOf("web_search", "web_read")
        fun clean(value: String, max: Int): String = value.filterNot { it.isISOControl() }.trim().take(max)
    }

    /** Area/menu change from the rail, action bar or a panel (Jarvis Integration calls this). Blank target is ignored. */
    fun areaChanged(from: String?, to: String, pattern: TransitionPattern) {
        val target = clean(to, 64)
        if (target.isEmpty()) return
        bus.emit(CockpitAnimationEvent.AreaChanged(NAV_TURN, clock(), from?.let { clean(it, 64) }?.ifEmpty { null }, target, pattern))
    }

    /** Real first cockpit start (the Activity built the home). Emits scene 1 once per process. */
    fun bootOnce(): Boolean {
        if (!bootPlayed.compareAndSet(false, true)) return false
        bus.emit(CockpitAnimationEvent.BootStarted(BOOT_TURN, clock(), CockpitTimeline.GREETING_TEXT))
        return true
    }

    private val activeAlarms = java.util.EnumSet.noneOf(AlarmKind::class.java)

    /** Real alarm signal (edge-triggered): raises once when [active] turns true, clears once when it turns false. */
    fun alarm(kind: AlarmKind, active: Boolean, sourceLabel: String, measured: Double? = null, threshold: Double? = null) {
        val changed = synchronized(activeAlarms) { if (active) activeAlarms.add(kind) else activeAlarms.remove(kind) }
        if (!changed) return
        bus.emit(if (active) CockpitAnimationEvent.AlarmRaised(ALARM_TURN, clock(), kind, clean(sourceLabel, 64), measured, threshold)
            else CockpitAnimationEvent.AlarmCleared(ALARM_TURN, clock(), kind))
    }
}

const val ALARM_TURN = "alarm"

/** Process-wide: scene 1 plays once per process, on the first real cockpit start. */
private val bootPlayed = java.util.concurrent.atomic.AtomicBoolean()

internal fun resetBootForTest() = bootPlayed.set(false)
