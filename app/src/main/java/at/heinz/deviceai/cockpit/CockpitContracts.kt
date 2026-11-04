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
    @Volatile var renderer: JarvisAvatarRenderer? = null
        private set

    fun register(r: JarvisAvatarRenderer?) { renderer = r }

    /** AvatarDirector sink: existing AvatarSinkRegistry sink plus the docked renderer (posted to [main]). */
    fun fanOut(main: (Runnable) -> Unit): AvatarSink = AvatarSink { event ->
        AvatarSinkRegistry.sink.onAvatarEvent(event)
        renderer?.let { r ->
            main(Runnable {
                if (event is AvatarEvent.StateChanged) r.onAvatarState(event.state)
                r.onAvatarEvent(event)
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
