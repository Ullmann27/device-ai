package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.MEMORY_TURN
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
    private val memoryRenderer = BrainOrbAnimationRenderer(effects) { clock() }
    // Unlike View.post, the main Handler does not defer delivery until this View attaches.
    private val completionHandler = Handler(android.os.Looper.getMainLooper())
    @Volatile private var deliveryGeneration = 0L
    val cueGeneration: Long get() = deliveryGeneration
    @Volatile private var detached = false
    @Volatile private var motionAllowed = true
    private val headIcons = at.heinz.deviceai.avatar.AvatarIconBurst()
    private val fileTunnel = at.heinz.deviceai.ui.video3.Video3FileTunnel()
    internal var onMemoryScene: ((Long) -> Boolean)? = null
    private val headRegion = android.graphics.RectF()
    private var frameDelayMillis = 16L
    private var reducedBloom = false

    fun setFrameBudget(delayMillis: Long, reducedBloom: Boolean) {
        frameDelayMillis = delayMillis.coerceIn(16L, 80L)
        this.reducedBloom = reducedBloom
    }

    fun setHeadRegion(left: Float, top: Float, right: Float, bottom: Float) {
        headRegion.set(left, top, right, bottom)
    }

    fun playCue(step: at.heinz.deviceai.cockpit.TimelineStep): Boolean {
        if (step.cue != at.heinz.deviceai.cockpit.Cue.HEAD_ICON_BURST &&
            step.cue != at.heinz.deviceai.cockpit.Cue.FILE_ICONS_FROM_HEAD &&
            step.cue != at.heinz.deviceai.cockpit.Cue.AREA_WARP) return false
        if (step.event is CockpitAnimationEvent.DocumentLearned && step.event.entriesAdded <= 0) return true
        if (!detached && motionAllowed && isAttachedToWindow && isShown && step.event.turnId !in retiredTurns) {
            if (step.cue == at.heinz.deviceai.cockpit.Cue.AREA_WARP) fileTunnel.start(clock())
            else headIcons.start(clock())
            kick()
        }
        return true
    }

    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun command(text: String) { effects.command(text, clock()); kick() }
    fun card(id: String, kind: JarvisSequenceEffects.Kind, title: String, subtitle: String? = null) =
        effects.card(id, kind, title, subtitle, clock()).also { kick() }
    fun step(id: String, title: String) = effects.step(id, title, clock()).also { kick() }
    fun stepFinished(id: String, ok: Boolean) = effects.stepFinished(id, ok, clock()).also { kick() }
    fun result(id: String, title: String, subtitle: String? = null) = effects.result(id, title, subtitle, clock()).also { kick() }
    fun dismiss(id: String) = effects.dismiss(id, clock()).also { kick() }
    fun clear() {
        deliveryGeneration++
        effects.clear(); stepStates.clear(); turn = null
        headIcons.clear()
        fileTunnel.clear()
        invalidate()
    }

    /**
     * While a work window is open, the step cards and the command chip dock into the window's status strip: they are
     * not drawn over the avatar's face or the orb. Their state is kept; on undocking they return to their place.
     */
    fun setCardsDocked(docked: Boolean) {
        if (effects.docked == docked) return
        effects.docked = docked
        kick()
    }

    /** The containing live surface supplies its foreground, visibility, thermal and reduced-motion policy. */
    fun setMotionAllowed(allowed: Boolean) {
        if (motionAllowed == allowed) return
        motionAllowed = allowed
        if (!allowed) clear() else kick()
    }

    private var turn: String? = null
    private val stepStates = HashMap<String, StepState>()
    // Bounded presentation history, matching the producer's recent-turn window.
    // A completed or displaced turn must not become visible again through a late callback.
    private val retiredTurns = LinkedHashSet<String>()

    private fun retireTurn(id: String) {
        retiredTurns += id
        while (retiredTurns.size > MAX_RETIRED_TURNS) retiredTurns.remove(retiredTurns.first())
    }

    /** Called on the emitting thread; hops to the UI thread. */
    override fun render(event: CockpitAnimationEvent) {
        // Completion is presentation history, not an animation. Keep a completion this
        // subscriber received even when its visual state is cleared before UI delivery.
        val completion = event is CockpitAnimationEvent.TurnEnded
        if (!completion && (detached || !motionAllowed)) return
        val generation = deliveryGeneration
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) apply(event)
        else if (completion) completionHandler.post { apply(event) }
        else post { if (generation == deliveryGeneration) apply(event) }
    }

    internal fun apply(event: CockpitAnimationEvent) {
        if (event is CockpitAnimationEvent.TurnEnded) {
            headIcons.clear()
            fileTunnel.clear()
            // Remember completion even if another command already replaced this turn on screen.
            retireTurn(event.turnId)
            if (turn == event.turnId) {
                effects.dismissAll(clock()); effects.clearPills(); effects.closeEnvelope()
                stepStates.clear(); turn = null; kick()
            }
            return
        }
        if (detached || !motionAllowed) return
        // Ledger/retrieval events have their own stream and must not be rejected by the current conversation id.
        if (event is CockpitAnimationEvent.DocumentLearned || event is CockpitAnimationEvent.MemoryStored ||
            event is CockpitAnimationEvent.RecallHits) {
            if (event.turnId == MEMORY_TURN) {
                if (event is CockpitAnimationEvent.DocumentLearned && event.entriesAdded > 0 ||
                    event is CockpitAnimationEvent.RecallHits && event.count > 0) {
                    val now = clock()
                    if (onMemoryScene?.invoke(now) != true) fileTunnel.start(now)
                }
                memoryRenderer.render(event)
                kick()
            }
            return
        }
        if (event is CockpitAnimationEvent.Extension) {
            // Global extension effects do not acquire a conversation/worker slot.
            if (event.kind == "alarm") { effects.alarm(clock()); kick() }
            return
        }
        if (event.turnId in retiredTurns) return
        if (event is CockpitAnimationEvent.CommandFlyIn) {
            if (turn != event.turnId) {
                turn?.let(::retireTurn)
                effects.clear(); stepStates.clear()
            }
            turn = event.turnId
            command(event.commandPreview); return
        }
        // Boot, navigation, calculation and global status events have no work cards here.
        // Letting them set `turn` would hide the next worker, which has no CommandFlyIn.
        if (event !is CockpitAnimationEvent.ActionStarted && event !is CockpitAnimationEvent.ProcessTimeline &&
            event !is CockpitAnimationEvent.AccountCardFlyIn && event !is CockpitAnimationEvent.DocumentQuestion) return
        if (turn == null) turn = event.turnId
        if (event.turnId != turn) return
        val key = { k: String -> safeId("${event.turnId}.$k") }
        val mainSceneId = key("main_scene")
        fun primaryTimelineStep(steps: List<at.heinz.deviceai.cockpit.ProcessStep>):
            at.heinz.deviceai.cockpit.ProcessStep? {
            // One visible work scene per turn. Prefer a real active/waiting tool or milestone,
            // then the generic work step, then acknowledgement/completed history.
            return steps.lastOrNull { it.state == StepState.FAILED || it.state == StepState.BLOCKED }
                ?: steps.lastOrNull { it.state == StepState.WAITING }
                ?: steps.lastOrNull { it.state == StepState.ACTIVE && it.key != "work" }
                ?: steps.lastOrNull { it.key == "work" }
                ?: steps.lastOrNull()
        }
        when (event) {
            // THINK/tool/research are phases of the same turn scene, never parallel cards.
            is CockpitAnimationEvent.ActionStarted -> {
                val id = if (event.action == at.heinz.deviceai.cockpit.CockpitAction.WORKER)
                    event.stepKey?.let { key("s.$it") } ?: key("a.${event.action.name}")
                else mainSceneId
                step(id, event.detail)
            }
            is CockpitAnimationEvent.ProcessTimeline -> {
                val primary = primaryTimelineStep(event.steps)
                if (primary != null) {
                    val old = stepStates[mainSceneId]
                    step(mainSceneId, primary.label)
                    when (primary.state) {
                        StepState.DONE -> if (event.steps.none {
                            it.state == StepState.ACTIVE || it.state == StepState.WAITING
                        }) stepFinished(mainSceneId, true)
                        StepState.FAILED, StepState.BLOCKED -> stepFinished(mainSceneId, false)
                        StepState.ACTIVE, StepState.WAITING -> Unit
                    }
                    stepStates[mainSceneId] = primary.state
                    if (old != primary.state) kick()
                }
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
            else -> Unit
        }
    }

    private fun safeId(raw: String) = raw.map { if (it.isLetterOrDigit() && it.code < 128 || it in "._:-") it else '_' }
        .joinToString("").take(96)

    private fun requestFrame() {
        if (frameDelayMillis <= 16L) postInvalidateOnAnimation()
        else postInvalidateDelayed(frameDelayMillis - 16L)
    }

    private fun kick() { if (isAttachedToWindow && motionAllowed) requestFrame() else invalidate() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); deliveryGeneration++; detached = false }

    override fun onDetachedFromWindow() { detached = true; clear(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val now = clock()
        painter.begin(canvas)
        val saved = canvas.save()
        canvas.scale(d, d)
        val tunnelActive = fileTunnel.draw(painter, width / d, height / d, now,
            visible = motionAllowed && isAttachedToWindow && isShown, glow = !reducedBloom)
        effects.draw(painter, width / d, height / d, now)
        if (headIcons.active(now)) {
            val w = if (headRegion.isEmpty) width / d * .66f else headRegion.width()
            val h = if (headRegion.isEmpty) height / d else headRegion.height()
            val scale = minOf(w / 300f, h / 345f)
            painter.save()
            painter.translate(headRegion.left + w * .5f, headRegion.top + h * .385f)
            painter.scale(scale, scale)
            headIcons.draw(painter, now)
            painter.restore()
        }
        canvas.restoreToCount(saved)
        if (motionAllowed && isAttachedToWindow && isShown &&
            (effects.isAnimating(now) || headIcons.active(now) || tunnelActive)) requestFrame()
    }

    private companion object { const val MAX_RETIRED_TURNS = 24 }
}
