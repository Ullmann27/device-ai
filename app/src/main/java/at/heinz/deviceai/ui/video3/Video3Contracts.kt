package at.heinz.deviceai.ui.video3

import at.heinz.deviceai.ui.ContinuumPainter

/*
 * Video 3 scenes (Alarm H, Warp C, Ganzkörper in der Energiekugel) – owner: Jarvis Lückenfüller.
 *
 * Only LOOK + MOTION live here. Every scene is a pure renderer: render(painter, width, height, seconds, …).
 * No triggers, no event bus, no timers: Jarvis Kern (CockpitAnimationPresenter / CockpitTransitionHook) decides
 * WHEN a scene runs and passes the elapsed time plus the real data of its event.
 *
 * The small types below are adapters with the same names/fields as Kern's contracts on #481
 * (CockpitAnimationEvent.AlarmRaised, AlarmKind, TransitionPattern.C_WARP, AvatarPresentation.FULL_BODY_ORB), so the
 * hook on the merged branch maps them field by field. This package does not depend on the cockpit package.
 */

/**
 * Mirror of Kern's `AlarmKind` (1:1 names). Only [SECURITY_FINDING] (a real finding from SecurityEventSource) plays
 * Alarm H; the other kinds are plain status displays and never start the scene (Kern decision 04.11.2026).
 */
enum class Video3AlarmKind {
    DATA_OVERLOAD, THERMAL, MEMORY_PRESSURE, SECURITY_FINDING;

    val playsAlarmScene: Boolean get() = this == SECURITY_FINDING
}

/**
 * Mirror of Kern's planned `CockpitAnimationEvent.AlarmRaised` payload. [measured]/[threshold] are the REAL values of
 * the event; null ⇒ the scene shows "nicht verfügbar" and draws no readable digits.
 */
data class Video3AlarmInfo(
    val kind: Video3AlarmKind,
    val sourceLabel: String,
    val measured: Double?,
    val threshold: Double?,
    val options: List<Video3ActionOption> = emptyList(),
)

/** Mirror of Kern's `ActionOption`. Disabled options are shown dimmed with [disabledReason]; nothing runs from here. */
data class Video3ActionOption(
    val id: String,
    val label: String,
    val toolName: String?,
    val enabled: Boolean,
    val disabledReason: String?,
)

/** Mirror of Kern's `CockpitAnimationEvent.CalmNotice` (real errors: tool failed, model error, ToolGate refusal). */
data class Video3CalmNoticeInfo(
    val sourceLabel: String,
    val message: String,
    val options: List<Video3ActionOption>,
)

/**
 * Question pills, Muster G (owner of the look: Jarvis Sicherheit, JarvisSequenceEffects component G). The scene only
 * lays out and calls this; a selection is reported to the caller, who forwards it to Kern's
 * PillActionGate.requestOption (ToolGate + owner yes). [Video3Pills] is the default implementation on JarvisMotion /
 * JarvisNeon (#479).
 */
interface PillPresenter {
    /** Draws [options] centred on [centerX] with the top at [top]; [elapsedMs] since the notice appeared. */
    fun draw(p: ContinuumPainter, width: Float, centerX: Float, top: Float, elapsedMs: Long,
             options: List<Video3ActionOption>, opts: Video3Options)
    /** Option hit by (x, y) in the last drawn frame; null for none or a disabled option. */
    fun optionAt(x: Float, y: Float): Video3ActionOption?
}

/**
 * Head slot (owner: Jarvis Avatar). Draws the ONE head into the box (l, t, w, h); the scene only transforms the canvas
 * (Z-zoom, shake). Production adapter once Integration has merged avatar-ref-match:
 * `HeadDrawer { p, l, t, w, h, s, glow -> p.save(); p.translate(l, t); renderer.draw(p, w, h, pose, s, motion = true); p.restore() }`
 * with one `ContinuumAvatarRenderer` instance (no per-frame allocation). [glowBoost] = 1 normal, 3 during Alarm H.
 */
fun interface HeadDrawer {
    fun draw(p: ContinuumPainter, left: Float, top: Float, width: Float, height: Float, seconds: Float, glowBoost: Float)

    companion object {
        /** Nothing registered: the scene stays honest and draws no head of its own. */
        val NONE = HeadDrawer { _, _, _, _, _, _, _ -> }
    }
}

/**
 * Alarm base effects that ALREADY exist in `JarvisSequenceEffects` (avatar-ref-match @2350ab7b, owner now Jarvis
 * Sicherheit): `alarm()`, `glowBoost()` (×3), `shakeOffsetX/Y()` (2 px). Same signatures, so the production adapter is
 * `object : AlarmBaseEffects { override fun glowBoost(n: Long) = fx.glowBoost(n); … }` – nothing is rebuilt here.
 * [Video3Curves.Fallback] is only used while that class is not on the merged branch.
 */
interface AlarmBaseEffects {
    fun glowBoost(nowMillis: Long): Float
    fun shakeOffsetX(nowMillis: Long): Float
    fun shakeOffsetY(nowMillis: Long): Float
}

/**
 * Energy-orb mesh slot (owner: Jarvis Gedächtnis, ContinuumHologramRenderer). The full-body scene draws only its own
 * pulse/glow overlay, magenta rings, lasers and the cockpit around it; the sphere mesh itself comes from here.
 */
fun interface OrbDrawer {
    /** Sphere centred at ([cx], [cy]) with radius [radius]; [energy] 0..1 from the scene pulse. */
    fun draw(p: ContinuumPainter, cx: Float, cy: Float, radius: Float, seconds: Float, energy: Float)

    companion object {
        val NONE = OrbDrawer { _, _, _, _, _, _ -> }
    }
}

/** Body anchor points (screen px) for the laser lines; filled by a [FullBodyFigure]. */
class BodyAnchors {
    var leftHandX = 0f; var leftHandY = 0f
    var rightHandX = 0f; var rightHandY = 0f
    var chestX = 0f; var chestY = 0f
    /** Head box (for [HeadDrawer]). */
    var headLeft = 0f; var headTop = 0f; var headSize = 0f
}

/**
 * Full-body figure for AvatarPresentation.FULL_BODY_ORB. Replaceable: Jarvis Avatar may take over the look by
 * providing another implementation; the scene only needs [draw] and [anchors]. The head is NEVER drawn by the body –
 * it always comes from [HeadDrawer].
 */
interface FullBodyFigure {
    /** Figure centred at ([cx], [cy]) with total height [height]; fills [out]. */
    fun anchors(cx: Float, cy: Float, height: Float, out: BodyAnchors)
    fun draw(p: ContinuumPainter, cx: Float, cy: Float, height: Float, seconds: Float, glow: Float, head: HeadDrawer)
}

/** A real gauge value (0..1) for the radial cockpit; [fraction] null ⇒ "nicht verfügbar". */
data class Video3Gauge(val label: String, val fraction: Float?, val valueText: String?)

/** A real time series for the line chart; [values] null or < 2 points ⇒ "nicht verfügbar". */
data class Video3Series(val label: String, val values: FloatArray?)

/** Data for the radial cockpit around the full body (only real sources, e.g. ContinuumMemoryStatsReader on #481). */
data class Video3CockpitData(val gauges: List<Video3Gauge>, val series: Video3Series?) {
    companion object { val UNAVAILABLE = Video3CockpitData(emptyList(), null) }
}

/** Render options shared by all three scenes. */
data class Video3Options(
    /** Settings.Global.ANIMATOR_DURATION_SCALE; 0 ⇒ reduced motion (static final frame, no shake/zoom/warp flight). */
    val animatorScale: Float = 1f,
    /** false ⇒ no bloom/halo layers (low power); lines and text stay. */
    val glow: Boolean = true,
) {
    val reducedMotion: Boolean get() = !(animatorScale > 0f) || !animatorScale.isFinite()
}
