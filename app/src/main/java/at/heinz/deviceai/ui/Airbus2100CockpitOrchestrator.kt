package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.AlarmKind
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
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

    private val tick = Runnable { tickMissionClock() }

    @Synchronized
    override fun render(event: CockpitAnimationEvent) {
        if (closed) return
        when (event) {
            is CockpitAnimationEvent.CommandFlyIn -> beginMission(event.turnId)
            is CockpitAnimationEvent.ActionStarted -> if (event.turnId == activeTurnId) {
                val next = when (event.action) {
                    CockpitAction.THINK -> Phase.LOCAL_REASONING
                    CockpitAction.TOOL, CockpitAction.RESEARCH, CockpitAction.DEVELOP,
                    CockpitAction.EXECUTE, CockpitAction.VERIFY, CockpitAction.LEARN,
                    CockpitAction.WORKER -> Phase.TOOL_ACTIVE
                }
                promote(next, sceneFor(event))
            }
            is CockpitAnimationEvent.ProcessTimeline -> if (event.turnId == activeTurnId) {
                if (event.steps.any { it.state == StepState.WAITING }) promote(Phase.AWAITING_DECISION, null)
            }
            is CockpitAnimationEvent.AccountCardFlyIn -> if (event.turnId == activeTurnId) {
                promote(Phase.TOOL_ACTIVE, scene(JarvisActionScenes.Kind.APP))
            }
            is CockpitAnimationEvent.DocumentQuestion -> if (event.turnId == activeTurnId) {
                promote(Phase.AWAITING_DECISION, JarvisActionScenes.forDocument())
            }
            is CockpitAnimationEvent.CalculationShown -> if (event.turnId == activeTurnId) {
                promote(Phase.TOOL_ACTIVE, JarvisActionScenes.forCalculation())
            }
            is CockpitAnimationEvent.AlarmRaised -> if (event.kind == AlarmKind.SECURITY_FINDING) {
                onSceneProfile(scene(JarvisActionScenes.Kind.STATUS))
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
        onTelemetry(null)
    }

    private fun beginMission(turnId: String) {
        if (activeTurnId != turnId) {
            activeTurnId?.let { sideQuestEngine.stopMissionCountdown(it, clockNanos()) }
            activeTurnId = turnId
            phase = Phase.LOCAL_REASONING
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
        CockpitAction.THINK -> null
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
