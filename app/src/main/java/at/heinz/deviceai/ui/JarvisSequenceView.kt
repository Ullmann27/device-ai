package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.StepState

/**
 * Transparent overlay View hosting [JarvisSequenceEffects] (put it above the cockpit). Trigger API for Jarvis Kern:
 * [command], [card], [step], [stepFinished], [result], [dismiss], [clear]. It redraws only while something moves and
 * clears itself when detached. Not clickable: touches pass through to the cockpit below.
 *
 * Docking: implements Jarvis Kern's [CockpitAnimationRenderer]; `bus.subscribe(view)` is the whole integration.
 * Events of a stale turn are dropped; [CockpitAnimationEvent.TurnEnded] flies all cards out.
 */
class JarvisSequenceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), CockpitAnimationRenderer {
    val effects = JarvisSequenceEffects()
    private val painter = AndroidContinuumPainter(context.assets)
    /** Injected clock for tests; defaults to the system monotonic clock. */
    var clock: () -> Long = { SystemClock.uptimeMillis() }

    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun command(text: String) { effects.command(text, clock()); kick() }
    fun card(id: String, kind: JarvisSequenceEffects.Kind, title: String, subtitle: String? = null) =
        effects.card(id, kind, title, subtitle, clock()).also { kick() }
    fun step(id: String, title: String) = effects.step(id, title, clock()).also { kick() }
    fun stepFinished(id: String, ok: Boolean) = effects.stepFinished(id, ok, clock()).also { kick() }
    fun result(id: String, title: String, subtitle: String? = null) = effects.result(id, title, subtitle, clock()).also { kick() }
    fun dismiss(id: String) = effects.dismiss(id, clock()).also { kick() }
    fun clear() { effects.clear(); invalidate() }

    private var turn: String? = null
    private val stepStates = HashMap<String, StepState>()

    /** Called on the emitting thread; hops to the UI thread. */
    override fun render(event: CockpitAnimationEvent) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) apply(event) else post { apply(event) }
    }

    internal fun apply(event: CockpitAnimationEvent) {
        if (event is CockpitAnimationEvent.CommandFlyIn) {
            if (turn != event.turnId) { effects.clear(); stepStates.clear() }
            turn = event.turnId
            command(event.commandPreview); return
        }
        if (event is CockpitAnimationEvent.Extension) {
            // Proposal for Kern: Extension("alarm") for overload/threat → Muster H. Unknown kinds are ignored.
            if (event.kind == "alarm") { effects.alarm(clock()); kick() }
            return
        }
        if (turn == null) turn = event.turnId
        if (event.turnId != turn) return
        val key = { k: String -> safeId("${event.turnId}.$k") }
        when (event) {
            is CockpitAnimationEvent.ActionStarted -> {
                step(key("a.${event.action.name}"), event.detail)
                if (event.action == at.heinz.deviceai.cockpit.CockpitAction.RESEARCH ||
                    event.action == at.heinz.deviceai.cockpit.CockpitAction.LEARN) { effects.dataFlight(5, clock()); kick() }
            }
            is CockpitAnimationEvent.ProcessTimeline -> for (s in event.steps) {
                val id = key("s.${s.key}")
                val old = stepStates[id]
                if (old == s.state) continue
                if (old == null) step(id, s.label)
                when (s.state) {
                    StepState.DONE -> stepFinished(id, true)
                    StepState.FAILED, StepState.BLOCKED -> stepFinished(id, false)
                    StepState.ACTIVE, StepState.WAITING -> Unit
                }
                stepStates[id] = s.state
            }
            is CockpitAnimationEvent.AccountCardFlyIn ->
                if (event.provider.contains("mail", ignoreCase = true)) {
                    // Muster F; follow-up labels: TODO(Kern) supply the real next actions on the event.
                    effects.envelope(event.accountLabel, emptyList(), clock()); kick()
                } else card(key("acc.${event.provider}"), JarvisSequenceEffects.Kind.ACCOUNT, event.provider, event.accountLabel)
            is CockpitAnimationEvent.DocumentQuestion -> {
                effects.closeEnvelope()
                card(key("doc"), JarvisSequenceEffects.Kind.DOCUMENT, event.documentName, null)
                // Muster G; TODO(Kern): DocumentQuestion carries one question, the pills need the answer options.
                effects.questionPills(listOf(event.question), clock()); kick()
            }
            is CockpitAnimationEvent.TurnEnded -> { effects.dismissAll(clock()); effects.clearPills(); effects.closeEnvelope(); stepStates.clear(); turn = null; kick() }
            else -> Unit
        }
    }

    private fun safeId(raw: String) = raw.map { if (it.isLetterOrDigit() && it.code < 128 || it in "._:-") it else '_' }
        .joinToString("").take(96)

    private fun kick() { if (isAttachedToWindow) postInvalidateOnAnimation() else invalidate() }

    override fun onDetachedFromWindow() { effects.clear(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val now = clock()
        painter.begin(canvas)
        val saved = canvas.save()
        canvas.scale(d, d)
        effects.draw(painter, width / d, height / d, now)
        canvas.restoreToCount(saved)
        if (isAttachedToWindow && isShown && effects.isAnimating(now)) postInvalidateOnAnimation()
    }
}
