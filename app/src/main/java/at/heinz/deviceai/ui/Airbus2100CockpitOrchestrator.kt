package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.AlarmKind
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.DocumentDraftReadyEvent
import at.heinz.deviceai.cockpit.DocumentPipelineStepEvent
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.ProactiveSideQuestAndCountdownEngine

/**
 * Coordination layer for the existing canonical work-window stack.
 *
 * It owns no tool authority, no second renderer and no second audio engine. Real cockpit events only:
 * - promote the existing FUI profile in place,
 * - feed the bounded mission clock,
 * - request the existing policy-gated UI cue,
 * - signal post-mission initiative evaluation.
 */
class Airbus2100CockpitOrchestrator(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val onSceneProfile: (JarvisActionScenes.Scene) -> Unit,
    private val onLiveData: (HollywoodFuiLiveData?) -> Unit,
    private val onTelemetry: (String?) -> Unit,
    private val onHoloCue: () -> Unit,
    private val onPostMission: (String) -> Unit,
    private val clockNanos: () -> Long = System::nanoTime,
    private val sideQuestEngine: ProactiveSideQuestAndCountdownEngine =
        ProactiveSideQuestAndCountdownEngine()
) : CockpitAnimationRenderer, AutoCloseable {

    internal enum class Phase(val rank: Int) {
        IDLE(0), LOCAL_REASONING(1), TOOL_ACTIVE(2), AWAITING_DECISION(3)
    }

    private var activeTurnId: String? = null
    private var phase = Phase.IDLE
    private var foreground = true
    private var closed = false
    private var tickScheduled = false
    private val document = DocumentBlueprintProjection()
    private val swarmWorkers = LinkedHashMap<String, HollywoodFuiLiveData.Timeline.Line>()

    private val tick = Runnable { tickMissionClock() }

    /** Honest Document-Blueprint snapshot; null when no document is in this turn. */
    @Synchronized
    fun documentSnapshot(): HollywoodFuiLiveData.Document? = document.snapshot()

    @Synchronized
    override fun render(event: CockpitAnimationEvent) {
        if (closed) return
        when (event) {
            is CockpitAnimationEvent.CommandFlyIn -> {
                beginMission(event.turnId)
                // Local Reasoning world: morphs an already-open window, never opens a new one.
                onSceneProfile(scene(JarvisActionScenes.Kind.GENERIC))
            }
            is CockpitAnimationEvent.ActionStarted -> {
                if (event.action == CockpitAction.WORKER) {
                    if (activeTurnId == null) beginMission(event.turnId)
                    val label = event.detail.take(64).ifBlank { event.turnId.take(32) }
                    swarmWorkers[event.turnId] = HollywoodFuiLiveData.Timeline.Line(label, StepState.ACTIVE)
                    promote(Phase.TOOL_ACTIVE, scene(JarvisActionScenes.Kind.PLANNING))
                    onLiveData(HollywoodFuiLiveData.Timeline(swarmWorkers.values.toList()))
                    return
                }
                if (event.turnId != activeTurnId) return
                val next = when (event.action) {
                    CockpitAction.THINK -> Phase.LOCAL_REASONING
                    CockpitAction.TOOL, CockpitAction.RESEARCH, CockpitAction.DEVELOP,
                    CockpitAction.EXECUTE, CockpitAction.VERIFY, CockpitAction.LEARN,
                    CockpitAction.WORKER -> Phase.TOOL_ACTIVE
                }
                promote(next, sceneFor(event))
                if (event.detail.isNotBlank() && !document.hasDocument) {
                    onLiveData(HollywoodFuiLiveData.Timeline(listOf(
                        HollywoodFuiLiveData.Timeline.Line(event.detail.take(64), StepState.ACTIVE)
                    )))
                }
            }
            is CockpitAnimationEvent.ProcessTimeline -> if (event.turnId == activeTurnId) {
                document.onTimeline(event.steps)
                if (document.hasDocument) onLiveData(document.snapshot())
                else onLiveData(HollywoodFuiLiveData.Timeline.from(event.steps))
                if (event.steps.any { it.state == StepState.WAITING }) promote(Phase.AWAITING_DECISION, null)
            }
            is CockpitAnimationEvent.AccountCardFlyIn -> if (event.turnId == activeTurnId) {
                promote(Phase.TOOL_ACTIVE, scene(JarvisActionScenes.Kind.APP))
                onLiveData(HollywoodFuiLiveData.app(event.provider, event.accountLabel))
            }
            is CockpitAnimationEvent.DocumentQuestion -> {
                // Owner-selected intake: attach to the running mission or start one. Do not
                // drop it when the verbal command used a different turn id than document-<episode>.
                if (activeTurnId == null) beginMission(event.turnId)
                promote(Phase.AWAITING_DECISION, JarvisActionScenes.forDocument())
                document.onQuestion(event)
                onLiveData(document.snapshot())
            }
            is CockpitAnimationEvent.DocumentLearned -> {
                // MEMORY_TURN is session-scoped, not a mission id.
                document.onLearned(event)
                if (document.hasDocument) {
                    promote(Phase.TOOL_ACTIVE, JarvisActionScenes.forDocument())
                    onLiveData(document.snapshot())
                }
            }
            is CockpitAnimationEvent.RecallHits -> {
                promote(Phase.TOOL_ACTIVE, scene(JarvisActionScenes.Kind.MEMORY))
                onLiveData(HollywoodFuiLiveData.evidence(event.count, event.topScore, "Recall"))
            }
            is DocumentPipelineStepEvent -> {
                if (activeTurnId == null) beginMission(event.turnId)
                document.onPipelineStep(event)
                if (document.hasDocument) onLiveData(document.snapshot())
            }
            is DocumentDraftReadyEvent -> {
                if (activeTurnId == null) beginMission(event.turnId)
                document.onDraft(event)
                if (document.hasDocument) {
                    promote(Phase.AWAITING_DECISION, JarvisActionScenes.forDocument())
                    onLiveData(document.snapshot())
                }
            }
            is CockpitAnimationEvent.CalculationShown -> if (event.turnId == activeTurnId) {
                promote(Phase.TOOL_ACTIVE, JarvisActionScenes.forCalculation())
                onLiveData(HollywoodFuiLiveData.calculation(
                    event.expression, event.steps, event.result, event.unit
                ))
            }
            is CockpitAnimationEvent.AlarmRaised -> if (event.kind == AlarmKind.SECURITY_FINDING) {
                onSceneProfile(scene(JarvisActionScenes.Kind.STATUS))
                onLiveData(HollywoodFuiLiveData.security(
                    event.sourceLabel, event.measured, event.threshold
                ))
                onHoloCue()
            }
            is CockpitAnimationEvent.TurnEnded -> if (event.turnId == activeTurnId) finishMission(event.turnId)
            else -> Unit
        }
    }

    @Synchronized
    fun setForeground(active: Boolean) {
        if (foreground == active || closed) return
        foreground = active
        if (!active) {
            cancelTick()
        } else if (activeTurnId != null) {
            publishTimer()
            scheduleTick()
        }
    }

    @Synchronized
    fun evaluatePostMissionInitiative(
        records: Collection<MemoryRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): ProactiveSideQuestAndCountdownEngine.ProactiveSideQuest? =
        if (closed) null else sideQuestEngine.findTimelySideQuestOrNull(records, nowMillis)

    internal fun phaseRankForTest(): Int = synchronized(this) { phase.rank }
    internal fun activeTurnForTest(): String? = synchronized(this) { activeTurnId }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        cancelTick()
        activeTurnId?.let { sideQuestEngine.stopMissionCountdown(it, clockNanos()) }
        activeTurnId = null
        phase = Phase.IDLE
        document.reset()
        swarmWorkers.clear()
        onLiveData(null)
        onTelemetry(null)
    }

    private fun beginMission(turnId: String) {
        if (activeTurnId != turnId) {
            activeTurnId?.let { sideQuestEngine.stopMissionCountdown(it, clockNanos()) }
            activeTurnId = turnId
            phase = Phase.LOCAL_REASONING
            document.reset()
            swarmWorkers.clear()
            onLiveData(null)
            sideQuestEngine.startMissionCountdown(turnId, nowNanos = clockNanos())
        }
        publishTimer()
        if (foreground) scheduleTick()
    }

    private fun promote(next: Phase, scene: JarvisActionScenes.Scene?) {
        // Phase authority is monotonic; a later real event may still change the visual world
        // without lowering that authority (for example calculation while approval remains pending).
        if (next.rank > phase.rank) phase = next
        if (scene != null) {
            onSceneProfile(scene)
            onHoloCue()
        }
    }

    private fun finishMission(turnId: String) {
        cancelTick()
        val snapshot = sideQuestEngine.stopMissionCountdown(turnId, clockNanos())
        onTelemetry(snapshot?.let { "${it.formattedSecondsGerman} SEK · MISSION ABGESCHLOSSEN" })
        activeTurnId = null
        phase = Phase.IDLE
        onPostMission(turnId)
    }

    private fun publishTimer() {
        val id = activeTurnId ?: return
        val snapshot = sideQuestEngine.sampleMissionTimer(id, clockNanos()) ?: return
        onTelemetry("MISSION // ${snapshot.formattedSecondsGerman} s")
    }

    @Synchronized
    private fun tickMissionClock() {
        tickScheduled = false
        if (closed || !foreground || activeTurnId == null) return
        publishTimer()
        scheduleTick()
    }

    private fun scheduleTick() {
        if (tickScheduled || closed || !foreground || activeTurnId == null) return
        tickScheduled = true
        schedule(tick, TICK_MS)
    }

    private fun cancelTick() {
        if (!tickScheduled) return
        cancel(tick)
        tickScheduled = false
    }

    private fun sceneFor(event: CockpitAnimationEvent.ActionStarted): JarvisActionScenes.Scene? = when (event.action) {
        CockpitAction.THINK ->
            if (phase.rank <= Phase.LOCAL_REASONING.rank) scene(JarvisActionScenes.Kind.GENERIC) else null
        CockpitAction.TOOL, CockpitAction.EXECUTE -> JarvisActionScenes.forTool(event.detail)
        CockpitAction.RESEARCH, CockpitAction.VERIFY -> scene(JarvisActionScenes.Kind.WEB)
        CockpitAction.DEVELOP -> scene(JarvisActionScenes.Kind.DEVELOPMENT)
        CockpitAction.WORKER -> scene(JarvisActionScenes.Kind.PLANNING)
        CockpitAction.LEARN -> scene(JarvisActionScenes.Kind.LEARNING)
    }

    private fun scene(kind: JarvisActionScenes.Kind): JarvisActionScenes.Scene =
        JarvisActionScenes.Scene(
            kind = kind,
            origin = JarvisActionScenes.Origin.BesideFace,
            accent = JarvisActionScenes.accent(kind)
        )

    private companion object {
        const val TICK_MS = 100L
    }
}
