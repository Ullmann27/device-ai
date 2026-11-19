package at.heinz.deviceai.cognitive.loop

import at.heinz.deviceai.ToolRegistry
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState

/** Loop event on the cockpit bus for ActionScene / head / orb (renderers that do not know the kind ignore it). */
class CognitiveLoopAnimationEvent(val loopEvent: CognitiveLoopEvent) : CockpitAnimationEvent.Extension(KIND) {
    override val turnId: String get() = loopEvent.turnId
    override val atMillis: Long get() = loopEvent.atMillis
    override fun equals(other: Any?) = other is CognitiveLoopAnimationEvent && other.loopEvent == loopEvent
    override fun hashCode() = loopEvent.hashCode()
    override fun toString() = "CognitiveLoopAnimationEvent($loopEvent)"
    companion object { const val KIND = "cognitive_loop" }
}

/**
 * Maps loop events onto the existing [CockpitAnimationBus] (CalculationEventSource carries only
 * CalculationShown, so it does not fit). Per event: the raw [CognitiveLoopAnimationEvent], the complete
 * [CockpitAnimationEvent.ProcessTimeline] of the turn and, for a tool request, ActionStarted(TOOL).
 * Never emits AlarmRaised: an abort is a FAILED/BLOCKED timeline step with the calm note (CalmNotice is missing).
 */
class CognitiveLoopCockpitBridge(private val bus: CockpitAnimationBus) : CognitiveLoopListener {
    private val timelines = HashMap<String, MutableList<ProcessStep>>()

    override fun onEvent(event: CognitiveLoopEvent) {
        bus.emit(CognitiveLoopAnimationEvent(event))
        val snapshot = synchronized(timelines) {
            val steps = timelines.getOrPut(event.turnId) { mutableListOf() }
            fun settle() { steps.replaceAll { if (it.state == StepState.ACTIVE || it.state == StepState.WAITING) it.copy(state = StepState.DONE) else it } }
            fun add(key: String, label: String, state: StepState) {
                settle(); steps.removeAll { it.key == key }; steps += ProcessStep(key, label.take(MAX_LABEL), state)
                while (steps.size > MAX_STEPS) steps.removeAt(0)
            }
            val s = "s${event.stepIndex}"
            when (event) {
                is CognitiveLoopEvent.GoalSet -> { steps.clear(); add("goal", event.goal, StepState.DONE) }
                is CognitiveLoopEvent.PlanMade -> add("$s-plan", event.steps.first().description, StepState.DONE)
                is CognitiveLoopEvent.ToolRequested -> add("$s-tool", ToolRegistry.label(event.toolName), StepState.ACTIVE)
                is CognitiveLoopEvent.ToolResult -> {
                    val i = steps.indexOfLast { it.key == "$s-tool" }
                    if (i >= 0) steps[i] = steps[i].copy(state = if (event.ok) StepState.DONE else StepState.FAILED)
                }
                is CognitiveLoopEvent.ResultVerified -> add("$s-verify", event.reason.ifBlank { "Prüfung" },
                    if (event.passed) StepState.DONE else StepState.FAILED)
                is CognitiveLoopEvent.MemoryWritten -> add("$s-memory", "Gedächtnis", StepState.DONE)
                is CognitiveLoopEvent.AskOwner -> add("$s-ask", event.question, StepState.WAITING)
                is CognitiveLoopEvent.Aborted -> add("end", event.calmNote,
                    if (event.reason == AbortReason.ERROR) StepState.FAILED else StepState.BLOCKED)
                is CognitiveLoopEvent.Finished -> add("end", event.summary.ifBlank { "Fertig" }, StepState.DONE)
            }
            val copy = steps.toList()
            if (event is CognitiveLoopEvent.Aborted || event is CognitiveLoopEvent.Finished) timelines.remove(event.turnId)
            copy
        }
        if (event is CognitiveLoopEvent.ToolRequested) {
            bus.emit(CockpitAnimationEvent.ActionStarted(event.turnId, event.atMillis, CockpitAction.TOOL, event.toolName))
        }
        bus.emit(CockpitAnimationEvent.ProcessTimeline(event.turnId, event.atMillis, snapshot))
    }

    private companion object { const val MAX_STEPS = 64; const val MAX_LABEL = 200 }
}
