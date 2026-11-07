package at.heinz.deviceai.cockpit

import android.graphics.Rect
import android.view.ViewGroup
import at.heinz.deviceai.avatar.AvatarEvent
import at.heinz.deviceai.avatar.AvatarSink
import at.heinz.deviceai.avatar.AvatarSinkRegistry
import at.heinz.deviceai.avatar.AvatarState

/*
 * Interfaces for the bots (owner: Jarvis Kern; docs/ARCHITEKTUR-JARVIS.md, "Schnittstellen für die Bots").
 * Changes only through Jarvis Kern. Implementations live with the named bot; the cockpit
 * (CockpitAnimationPresenter) consumes and sequences them.
 */

// ---- Avatar slot (implemented by Jarvis Avatar) ----

/** The ONE avatar slot. The cockpit draws no head/orb of its own; null renderer = empty named placeholder. Main thread. */
interface JarvisAvatarRenderer {
    fun attach(host: ViewGroup, slot: Rect)
    fun onAvatarState(state: AvatarState)
    fun onAnimationEvent(event: CockpitAnimationEvent)
    fun onCockpitCue(step: TimelineStep): Boolean = false
    fun detach()
    /** Optional: slot moved/resized (rotation, fold, font scale). */
    fun onSlotChanged(slot: Rect) = Unit
    /** Optional: head only vs. full body with orb (video 3). */
    fun onPresentation(mode: AvatarPresentation) = Unit
    /** Optional: full avatar contract events (speech, visemes, audio level, gaze) for lip-sync. */
    fun onAvatarEvent(event: AvatarEvent) = Unit
}

enum class AvatarPresentation { HEAD, FULL_BODY_ORB }

object JarvisAvatarSlot {
    // A new identity on every registration also invalidates queued work for a reused renderer.
    private class Registration(val renderer: JarvisAvatarRenderer?)
    @Volatile private var registration = Registration(null)

    var renderer: JarvisAvatarRenderer?
        get() = registration.renderer
        private set(value) { registration = Registration(value) }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(JarvisAvatarRenderer?) -> Unit>()

    /** Register (or with null: unregister → the host detaches the old one). Hosts are notified. */
    fun register(r: JarvisAvatarRenderer?) { renderer = r; listeners.forEach { it(r) } }

    /** Host side (ContinuumHomeView): re-dock when the registration changes. */
    fun addListener(l: (JarvisAvatarRenderer?) -> Unit): AutoCloseable { listeners += l; return AutoCloseable { listeners -= l } }

    /** AvatarDirector sink: existing AvatarSinkRegistry sink plus the docked renderer (posted to [main]). */
    fun fanOut(main: (Runnable) -> Unit): AvatarSink = AvatarSink { event ->
        // Each optional graphics consumer is isolated, as in AvatarDirector's event emission.
        runCatching { AvatarSinkRegistry.sink.onAvatarEvent(event) }
        val target = registration
        target.renderer?.let { r ->
            main(Runnable {
                if (registration !== target) return@Runnable
                if (event is AvatarEvent.StateChanged) runCatching { r.onAvatarState(event.state) }
                // A state callback can synchronously unregister or re-dock the renderer.
                if (registration === target) runCatching { r.onAvatarEvent(event) }
            })
        }
    }
}

// ---- Calculation, pattern E (implemented by Jarvis Gedächtnis, real computations only) ----

data class CalcStep(val label: String, val value: Double)

enum class ChartKind { NONE, DONUT, BAR, LINE }

/** Listener may be called off the main thread; the cockpit posts before touching views. */
interface CalculationEventSource {
    fun addListener(l: (CockpitAnimationEvent.CalculationShown) -> Unit): AutoCloseable
}

// ---- Navigation / menu, ref5 rail, action bar (implemented by Jarvis Integration) ----

interface JarvisNavigator {
    /** [target] = an existing JarvisTile.target / JarvisTiles.NavItem.target. */
    fun navigate(target: String)
}

data class ActionAvailability(val enabled: Boolean, val reason: String?)

interface CockpitActionHandler {
    fun availability(action: CockpitAction): ActionAvailability
    fun perform(action: CockpitAction, turnId: String)
}

/** A = menu/scene change as depth move (JarvisMotion.Depth); B = area/panel fly-in (JarvisShellView.playEntrance). */
enum class TransitionPattern { A_DEPTH, B_FLY_IN, C_WARP }

const val NAV_TURN = "nav"

// ---- Alarms, video 3 (Jarvis Lückenfüller draws them; emitted only from real signals) ----

/**
 * DATA_OVERLOAD: memory event hub dropped events (droppedEventCount rising) or the worker queue is full.
 * THERMAL: PowerManager thermal status. MEMORY_PRESSURE: onTrimMemory / low-memory.
 */
enum class AlarmKind {
    /** Status only (no alarm scene). */
    DATA_OVERLOAD, THERMAL, MEMORY_PRESSURE,
    /** The ONLY kind that starts alarm scene H: a real security event of the connected system (e.g. virus-scan finding). */
    SECURITY_FINDING
}

/**
 * Real security events only. Quelle fehlt: the app has no virus scanner. No auto-remediation; whether coding
 * agents remediate is a separate, pending chief decision.
 */
fun interface SecurityEventSource {
    fun addListener(l: (CockpitAnimationEvent.AlarmRaised) -> Unit): AutoCloseable
}

// ---- Answer pills and Gmail, patterns F/G (implemented by Jarvis Sicherheit) ----

enum class AnswerPillKind { DIREKT_ANTWORTEN, AUSARBEITEN, STUDIE, ANRUFEN }

data class AnswerPill(val kind: AnswerPillKind, val toolName: String?, val enabled: Boolean, val disabledReason: String?)

/**
 * Pills only REQUEST an action: ToolGate plus an explicit owner yes, never auto-run. [onDecision] reports the
 * owner's decision; the action itself runs inside the gate.
 */
interface PillActionGate {
    fun pillsFor(event: CockpitAnimationEvent.DocumentQuestion): List<AnswerPill>
    fun request(pill: AnswerPill, turnId: String, onDecision: (approvedByOwner: Boolean) -> Unit)
}

/** Emits only after a real, ToolGate-approved account tool call. Listener may run off the main thread. */
interface AccountEventSource {
    fun addListener(l: (CockpitAnimationEvent.AccountCardFlyIn) -> Unit): AutoCloseable
}

// ---- Owner recognition card "User Recognized" (ref5, view by Jarvis Lückenfüller #487) ----

enum class OwnerRecognitionState { RECOGNIZED, NOT_RECOGNIZED, UNKNOWN }

/** Only real signals. [method] names the signal (e.g. "Geräte-Entsperrung"); null when nothing is known. */
data class OwnerRecognition(val state: OwnerRecognitionState, val method: String?, val sinceMillis: Long?) {
    companion object {
        const val METHOD_UNLOCK = "Geräte-Entsperrung"
        val UNKNOWN = OwnerRecognition(OwnerRecognitionState.UNKNOWN, null, null)
    }
}

interface OwnerRecognitionSource {
    fun current(): OwnerRecognition
    fun addListener(l: (OwnerRecognition) -> Unit): AutoCloseable
}

fun interface UserCardRenderer { fun render(model: OwnerRecognition) }

/**
 * The ONE user card slot (left of the head, ref5 grid area USER_CARD). Without a renderer the cockpit draws a
 * small "—". The host publishes the latest [OwnerRecognition]; a newly registered renderer gets it immediately.
 */
object JarvisUserCardSlot {
    @Volatile var renderer: UserCardRenderer? = null
        private set
    @Volatile var latest: OwnerRecognition = OwnerRecognition.UNKNOWN
        private set
    /** Slot bounds in window pixels (left, top, right, bottom), set by the cockpit host; null before layout. */
    @Volatile var bounds: Rect? = null
        internal set

    fun register(r: UserCardRenderer?) { renderer = r; r?.render(latest) }

    fun publish(model: OwnerRecognition) { latest = model; renderer?.render(model) }
}
