package at.heinz.deviceai.cockpit

/**
 * Typed animation triggers of the Jarvis cockpit (docs/CONTINUUM_COCKPIT.md).
 *
 * Every event is emitted only by a real app path (TurnLifecycle, ToolGate runner, ChiefWorkerPool,
 * account / document intake). Renderers – the replaceable default View animations or the transitions of
 * Jarvis Avatar / Welten-Optik – only visualise them; they never create events themselves.
 * Speaking and lip-sync are deliberately NOT part of this bus: they stay on the avatar contract
 * (AvatarDirector / AvatarSinkRegistry).
 */
sealed class CockpitAnimationEvent {
    /** Turn (or worker) the event belongs to; renderers drop events of a stale turn. */
    abstract val turnId: String
    /** Wall clock of the real occurrence. */
    abstract val atMillis: Long

    /** (a) A command arrived and was acknowledged: cockpit cards fly in. */
    data class CommandFlyIn(override val turnId: String, override val atMillis: Long, val commandPreview: String) :
        CockpitAnimationEvent()

    /** (b) Work really started: immediate visible feedback on the matching control. */
    data class ActionStarted(override val turnId: String, override val atMillis: Long, val action: CockpitAction,
                             val detail: String) : CockpitAnimationEvent()

    /** (c) The complete, current step list of the turn (oldest first); renderers animate the difference. */
    data class ProcessTimeline(override val turnId: String, override val atMillis: Long, val steps: List<ProcessStep>) :
        CockpitAnimationEvent()

    /** (d) A real account tool path (e.g. Gmail) was used: the account card flies in. Source currently missing. */
    data class AccountCardFlyIn(override val turnId: String, override val atMillis: Long, val provider: String,
                                val accountLabel: String) : CockpitAnimationEvent()

    /** (e) A real document (PDF) intake: the document card appears and Jarvis asks about it. Source currently missing. */
    data class DocumentQuestion(override val turnId: String, override val atMillis: Long, val documentName: String,
                                val mimeType: String, val question: String) : CockpitAnimationEvent()

    /** The turn delivered its single result (or was dropped); renderers settle all running effects. */
    data class TurnEnded(override val turnId: String, override val atMillis: Long, val delivered: Boolean) :
        CockpitAnimationEvent()

    /**
     * Memory events (#371 line). Not bound to a turn: [turnId] is [MEMORY_TURN]. Visuals of the orb (document
     * flying into the brain with lightning, light points for recall hits) belong to Jarvis Avatar / Welten-Optik.
     * Production source: "Quelle fehlt bis ContinuumMemoryEvents (#371-Linie)" (see [MemoryEventSources]).
     */
    data class DocumentLearned(override val turnId: String, override val atMillis: Long, val docId: String,
                               val kind: String, val entriesAdded: Int) : CockpitAnimationEvent()

    /**
     * Memory writes (coalesced): latest [entryId]/[status] (MemoryEntryStatus name; HYPOTHETICAL for hypotheses)
     * and the number of ledger writes since the previous event ([count]).
     */
    data class MemoryStored(override val turnId: String, override val atMillis: Long, val entryId: String,
                            val status: String, val count: Int = 1) : CockpitAnimationEvent()

    /** A recall returned [count] hits (light points in the orb); [topScore] of rank 1 or null without hits. */
    data class RecallHits(override val turnId: String, override val atMillis: Long, val queryHash: String,
                          val count: Int, val topScore: Float?) : CockpitAnimationEvent()

    /** Scene 1: the real first cockpit start of this session (once per process). [turnId] = [BOOT_TURN]. */
    data class BootStarted(override val turnId: String, override val atMillis: Long, val greeting: String) :
        CockpitAnimationEvent()

    /** Scene 1 finished, the cockpit is visible: exactly once per app session (triggers Avatar's headIconBurst, scene 2). */
    data class CockpitEntered(override val turnId: String, override val atMillis: Long, val sessionId: String) :
        CockpitAnimationEvent()

    /** Pattern E: a real calculation with its steps and result; [chart] selects the live chart (NONE = numbers only). */
    data class CalculationShown(override val turnId: String, override val atMillis: Long, val expression: String,
                                val steps: List<CalcStep>, val result: Double, val unit: String?, val chart: ChartKind) :
        CockpitAnimationEvent()

    /** The ONE fly-in/scene-change logic: rail, action bar or panel switched area ([turnId] = [NAV_TURN]). */
    data class AreaChanged(override val turnId: String, override val atMillis: Long, val fromTarget: String?,
                           val toTarget: String, val pattern: TransitionPattern) : CockpitAnimationEvent()

    /** A real alarm signal crossed its threshold ([measured]/[threshold] null when the platform gives only a level). */
    data class AlarmRaised(override val turnId: String, override val atMillis: Long, val kind: AlarmKind,
                           val sourceLabel: String, val measured: Double?, val threshold: Double?) : CockpitAnimationEvent()

    data class AlarmCleared(override val turnId: String, override val atMillis: Long, val kind: AlarmKind) :
        CockpitAnimationEvent()

    /**
     * Extension point for future event families, first of all the calculation / math visualisation of the
     * reference videos (e.g. `class MathVisualization(...) : CockpitAnimationEvent.Extension("math")`).
     * Not implemented yet; renderers that do not know a [kind] must ignore it.
     */
    abstract class Extension(val kind: String) : CockpitAnimationEvent()
}

const val MEMORY_TURN = "memory"
const val BOOT_TURN = "boot"

/** Controls of the cockpit action bar plus the two non-bar work kinds. */
enum class CockpitAction { THINK, RESEARCH, LEARN, DEVELOP, EXECUTE, VERIFY, TOOL, WORKER }

enum class StepState { ACTIVE, WAITING, DONE, BLOCKED, FAILED }

data class ProcessStep(val key: String, val label: String, val state: StepState)

/** Renderer of animation events. Called on the thread that emitted; Android renderers post to the UI thread. */
fun interface CockpitAnimationRenderer {
    fun render(event: CockpitAnimationEvent)
}

/**
 * Small, thread-safe in-process bus. Listener failures are isolated so a broken visual can never break a turn.
 * No history, no persistence: the cockpit state cards read the real sources, the bus only triggers motion.
 */
class CockpitAnimationBus {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<CockpitAnimationRenderer>()

    fun subscribe(renderer: CockpitAnimationRenderer): AutoCloseable {
        listeners += renderer
        return AutoCloseable { listeners.remove(renderer) }
    }

    fun emit(event: CockpitAnimationEvent) {
        for (listener in listeners) runCatching { listener.render(event) }
    }

    val listenerCount: Int get() = listeners.size
}
