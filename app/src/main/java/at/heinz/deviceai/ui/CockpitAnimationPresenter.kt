package at.heinz.deviceai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.view.View
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.WindowProjectedMotion
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot

/**
 * Transition hook for Welten-Optik / Jarvis Avatar: return true when the event was fully played, then the
 * minimal default below is skipped. Called on the UI thread with the live cockpit; must not change data.
 */
fun interface CockpitTransitionHook {
    fun play(event: CockpitAnimationEvent, home: ContinuumHomeView): Boolean
}

object CockpitTransitions {
    @Volatile var hook: CockpitTransitionHook? = null
}

/**
 * Minimal, replaceable default visuals for [CockpitAnimationEvent]s on the landscape cockpit: card fly-in,
 * button pulse, process-card highlight, transient account/document card. ViewPropertyAnimator only (RenderThread
 * transforms, no per-frame allocation); disabled for "Animationen entfernen" and energy saving.
 */
class CockpitAnimationPresenter(
    private val home: ContinuumHomeView,
    /** Bus for timeline-emitted events (CockpitEntered); null = timeline emits nothing. */
    private val bus: at.heinz.deviceai.cockpit.CockpitAnimationBus? = null,
    private val sessionId: String = "",
    /** Default for the GREETING cue without an Avatar cue renderer: show the line in the live dialog. */
    private val onGreeting: (String) -> Unit = {},
    /** Integration (#486): camera drift paused during the Muster-A depth change. */
    private val drift: JarvisCameraDrift? = null,
) : CockpitAnimationRenderer {
    private val density = home.resources.displayMetrics.density
    private val power = home.context.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun render(event: CockpitAnimationEvent) {
        val generation = home.presentationGeneration
        val avatar = home.avatarRenderer
        val cueGeneration = avatar?.cueGeneration
        val initialBoot = event is CockpitAnimationEvent.BootStarted && avatar == null && !home.isAttachedToWindow
        home.post {
            if (event is CockpitAnimationEvent.TurnEnded ||
                generation == home.presentationGeneration &&
                (initialBoot || avatar === home.avatarRenderer && cueGeneration == avatar?.cueGeneration)) {
                if (event is CockpitAnimationEvent.BootStarted) presentBootWhenReady(event, generation)
                else present(event)
            }
        }
    }

    private fun presentBootWhenReady(event: CockpitAnimationEvent.BootStarted, generation: Long) {
        fun ready() = home.isPresentationActive() && home.width > 0 && home.height > 0 &&
            (!home.ownsAvatarSlot || home.avatarRenderer != null)
        if (generation != home.presentationGeneration) return
        if (ready()) { present(event); return }
        val observer = home.viewTreeObserver
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
            private fun dispose() {
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                else home.viewTreeObserver.removeOnPreDrawListener(this)
                home.removeOnAttachStateChangeListener(this)
            }
            override fun onPreDraw(): Boolean {
                if (generation != home.presentationGeneration) dispose()
                else if (ready()) { dispose(); present(event) }
                return true
            }
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = dispose()
        }
        observer.addOnPreDrawListener(listener)
        home.addOnAttachStateChangeListener(listener)
    }

    /**
     * The ONE area/scene transition (look + motion: Jarvis Integration, JarvisMenuMotion on JarvisMotion).
     * B: JarvisShellView entrance (shell stage) or card fly-in (cockpit). A: scene change in Z depth (0,8 s):
     * leaving – the cockpit flies through the camera (>300 %, alpha 0); "Zurück" to home – it comes 0→100 % from the
     * centre. C: hook only.
     */
    private fun playArea(event: CockpitAnimationEvent.AreaChanged) {
        when (event.pattern) {
            at.heinz.deviceai.cockpit.TransitionPattern.B_FLY_IN ->
                if (home.jarvisShellStage) home.jarvisShell.playEntrance() else if (home.landscapeCockpit) flyInCards()
            at.heinz.deviceai.cockpit.TransitionPattern.C_WARP -> Unit // visual via hook only (no default)
            at.heinz.deviceai.cockpit.TransitionPattern.A_DEPTH -> sceneDepth(home, entering = event.toTarget == "home")
        }
    }

    private fun sceneDepth(view: View, entering: Boolean) {
        if (!motionAllowed()) return
        val end = JarvisMenuMotion.patternA(Long.MAX_VALUE / 4)
        val ease = JarvisMotion.Easing.easeInExpo
        drift?.pause()
        view.animate().cancel()
        view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
        val restore = Runnable { view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f; drift?.restart() }
        val (scale, alpha) = if (entering) {
            view.scaleX = 0f; view.scaleY = 0f; view.alpha = 0f
            end.newScale to end.newAlpha
        } else end.oldScale to end.oldAlpha
        view.animate().scaleX(scale).scaleY(scale).alpha(alpha).setDuration(JarvisMotion.Durations.SCENE_Z)
            .setInterpolator { ease(it) }.withEndAction(restore).start()
    }

    private fun motionAllowed() = ValueAnimator.areAnimatorsEnabled() && !power.isPowerSaveMode

    private fun present(event: CockpitAnimationEvent) {
        if (event is CockpitAnimationEvent.Extension && event.kind == WindowProjectedMotion.KIND) return
        if (!home.isAttachedToWindow) return
        if (event !is CockpitAnimationEvent.TurnEnded && !home.isPresentationActive()) return
        bindProjectedMotion(event)
        if (home.ownsAvatarSlot) home.avatarRenderer?.let { r -> runCatching { r.onAnimationEvent(event) } }
        if (!home.isPresentationActive()) return
        val hook = CockpitTransitions.hook
        if (hook != null && runCatching { hook.play(event, home) }.getOrDefault(false)) return
        runTimeline(event)
    }

    /** Sender hook for Bot 03: bind the triggering turn and emit CLOSED on STOP/TurnEnded. */
    private fun bindProjectedMotion(event: CockpitAnimationEvent) {
        if (!home.jarvisShellStage) return
        if (bus != null) home.jarvisShell.onProjectedMotion = { bus.emit(it) }
        when (JarvisCinemaEntrance.triggerFor(event)) {
            JarvisCinemaEntrance.Trigger.ENTER,
            JarvisCinemaEntrance.Trigger.STEP -> home.jarvisShell.bindCinemaTurn(event.turnId)
            JarvisCinemaEntrance.Trigger.EXIT -> {
                val stop = event is CockpitAnimationEvent.TurnEnded ||
                    (event is CockpitAnimationEvent.AreaChanged &&
                        event.toTarget == JarvisCockpitPanels.STOP_TARGET)
                if (stop) home.jarvisShell.closeProjectedMotion(event.turnId, event.atMillis)
            }
            JarvisCinemaEntrance.Trigger.IGNORE -> Unit
        }
    }

    /** Executes CockpitTimeline: each cue goes to its owner's CockpitCueRenderer, else the minimal default. */
    private fun runTimeline(event: CockpitAnimationEvent) {
        val motion = motionAllowed()
        val generation = home.presentationGeneration
        val avatar = home.avatarRenderer
        val cueGeneration = avatar?.cueGeneration
        fun current() = home.isPresentationActive() && generation == home.presentationGeneration &&
            avatar === home.avatarRenderer && cueGeneration == avatar?.cueGeneration
        for (step in at.heinz.deviceai.cockpit.CockpitTimeline.steps(event)) {
            val delay = if (motion) step.atMs else 0L
            if (delay == 0L) execute(step) else home.postDelayed({ if (current()) execute(step) }, delay)
        }
        if (event is CockpitAnimationEvent.BootStarted && bus != null) {
            val end = if (motion) at.heinz.deviceai.cockpit.CockpitTimeline.bootEndMs else 0L
            home.postDelayed({
                if (current()) at.heinz.deviceai.cockpit.CockpitTimeline
                    .cockpitEntered(sessionId, System.currentTimeMillis())?.let(bus::emit)
            }, end)
        }
    }

    private fun execute(step: at.heinz.deviceai.cockpit.TimelineStep) {
        if (home.ownsAvatarSlot &&
            runCatching { home.avatarRenderer?.onCockpitCue(step) == true }.getOrDefault(false)) return
        val renderer = at.heinz.deviceai.cockpit.CockpitCues.rendererFor(step.cue.owner)
        if (renderer != null && runCatching { renderer.play(step) }.getOrDefault(false)) return
        when (step.cue) {
            at.heinz.deviceai.cockpit.Cue.GREETING ->
                (step.event as? CockpitAnimationEvent.BootStarted)?.let { onGreeting(it.greeting) }
            // Avatar's head visuals: nothing of our own (empty slot without a renderer).
            at.heinz.deviceai.cockpit.Cue.AVATAR_FROM_DEPTH, at.heinz.deviceai.cockpit.Cue.AVATAR_SHRINK,
            at.heinz.deviceai.cockpit.Cue.HEAD_ICON_BURST, at.heinz.deviceai.cockpit.Cue.FILE_ICONS_FROM_HEAD -> Unit
            at.heinz.deviceai.cockpit.Cue.COCKPIT_FLY_IN ->
                if (home.jarvisShellStage) {
                    if (step.event is CockpitAnimationEvent.CommandFlyIn) home.jarvisShell.playWorkStage()
                    else home.jarvisShell.playEntrance()
                } else if (home.landscapeCockpit) flyInCards()
            else -> legacyPresent(step.event)
        }
    }

    /** Minimal default visuals per event (kept from the first cockpit version; owners replace them via cue renderers). */
    private fun legacyPresent(event: CockpitAnimationEvent) {
        if (event is CockpitAnimationEvent.AreaChanged) return playArea(event)
        if (home.jarvisShellStage) {
            // Shell stage: cinema entrance/exit inside JarvisShellView (same uptime clock, real events only).
            if (bus != null) home.jarvisShell.onProjectedMotion = { bus.emit(it) }
            when (JarvisCinemaEntrance.triggerFor(event)) {
                JarvisCinemaEntrance.Trigger.ENTER -> {
                    home.jarvisShell.bindCinemaTurn(event.turnId)
                    home.jarvisShell.playWorkStage()
                }
                JarvisCinemaEntrance.Trigger.STEP -> {
                    home.jarvisShell.bindCinemaTurn(event.turnId)
                    home.jarvisShell.playWorkWindowEntrance()
                }
                JarvisCinemaEntrance.Trigger.EXIT -> {
                    home.jarvisShell.playExit()
                    val stop = event is CockpitAnimationEvent.TurnEnded ||
                        (event is CockpitAnimationEvent.AreaChanged &&
                            event.toTarget == JarvisCockpitPanels.STOP_TARGET)
                    if (stop) home.jarvisShell.closeProjectedMotion(event.turnId, event.atMillis)
                }
                JarvisCinemaEntrance.Trigger.IGNORE -> Unit
            }
            return
        }
        if (!home.landscapeCockpit) return
        when (event) {
            is CockpitAnimationEvent.CommandFlyIn -> flyInCards()
            is CockpitAnimationEvent.ActionStarted -> slotFor(event.action)?.let { slot ->
                (home.viewFor(slot) as? ContinuumHomeView.ElementView)?.let { view ->
                    (view.element as? CockpitActionElement)?.active = true
                    view.invalidate()
                    pulse(view)
                }
            }
            is CockpitAnimationEvent.ProcessTimeline -> highlight(Slot.PROCESSES)
            is CockpitAnimationEvent.AccountCardFlyIn ->
                home.showTransientCard("Konto · " + event.provider, listOf(event.accountLabel), motionAllowed())
            is CockpitAnimationEvent.DocumentQuestion ->
                home.showTransientCard("Dokument · " + event.documentName, listOf(event.question), motionAllowed())
            is CockpitAnimationEvent.TurnEnded -> for (slot in ContinuumLandscapeCockpitLayout.actionBar) {
                (home.viewFor(slot) as? ContinuumHomeView.ElementView)?.let { view ->
                    (view.element as? CockpitActionElement)?.let { it.active = false; view.invalidate() }
                }
            }
            // Orb visuals (document into the brain, light points) belong to Avatar; minimal default: brain card glows.
            is CockpitAnimationEvent.DocumentLearned, is CockpitAnimationEvent.MemoryStored -> highlight(Slot.MODEL)
            is CockpitAnimationEvent.RecallHits -> if (event.count > 0) highlight(Slot.MODEL)
            is CockpitAnimationEvent.CalculationShown -> home.showTransientCard("Berechnung · " + event.expression,
                event.steps.take(4).map { it.label + " = " + it.value } + ("Ergebnis = " + event.result + (event.unit?.let { " $it" } ?: "")),
                motionAllowed())
            is CockpitAnimationEvent.AreaChanged -> Unit // handled by playArea
            // Alarm visuals belong to Jarvis Lückenfüller (hook); minimal default: system card glows.
            is CockpitAnimationEvent.AlarmRaised -> highlight(Slot.SYSTEM_STATUS) // status default; scene H = Lückenfüller
            is CockpitAnimationEvent.AlarmCleared -> Unit
            is CockpitAnimationEvent.BootStarted, is CockpitAnimationEvent.CockpitEntered -> Unit
            is CockpitAnimationEvent.Extension -> Unit // unknown kinds are ignored by design
        }
    }

    private fun slotFor(action: CockpitAction): Slot? = when (action) {
        CockpitAction.THINK -> Slot.LABEL_THINKING
        CockpitAction.RESEARCH -> Slot.LABEL_ANALYSING
        CockpitAction.LEARN -> Slot.LABEL_LEARNING
        CockpitAction.DEVELOP -> Slot.LABEL_DEVELOPING
        CockpitAction.EXECUTE, CockpitAction.TOOL -> Slot.ACTION_TASK
        CockpitAction.VERIFY -> Slot.ACTION_VERIFY
        CockpitAction.WORKER -> Slot.WORKERS
    }

    /** Muster B (JarvisMenuMotion.patternB): ±64 dp from the side, 80→100 %, alpha 0→1, 0,6 s cubic ease-out, centre first. */
    private fun flyInCards() {
        if (!motionAllowed()) return
        val views = ContinuumLandscapeCockpitLayout.cards.mapNotNull { home.viewFor(it) }
        val ease = JarvisMotion.Easing.easeOutCubic
        JarvisMenuMotion.patternB(views.map { it.left + it.width / 2f }, home.width / 2f).forEach { step ->
            val view = views[step.index]
            val spec = step.spec
            view.animate().cancel()
            view.translationX = spec.direction.dx * spec.distanceDp * density
            view.scaleX = spec.fromScale; view.scaleY = spec.fromScale
            view.alpha = spec.fromAlpha
            view.animate().translationX(0f).scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(step.delayMs)
                .setDuration(spec.durationMs).setInterpolator { ease(it) }.start()
        }
    }

    private fun pulse(view: View) {
        if (!motionAllowed()) return
        view.animate().cancel()
        view.animate().scaleX(1.06f).scaleY(1.06f).setDuration(110L).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
        }.start()
    }

    private fun highlight(slot: Slot) {
        val view = home.viewFor(slot) as? ContinuumHomeView.ElementView ?: return
        val card = view.element as? CockpitCard ?: return
        card.highlighted = true
        view.invalidate()
        view.removeCallbacks(view.tag as? Runnable)
        val clear = Runnable { card.highlighted = false; view.invalidate() }
        view.tag = clear
        view.postDelayed(clear, 700L)
    }
}

/** Glass notice card for the account / document scenes (shown only for real intake events). */
class CockpitNoticeCard(private val title: String, private val lines: List<String>, textScale: Float) : ContinuumElement(textScale) {
    override fun draw(p: ContinuumPainter, w: Float, h: Float, s: ContinuumDashboardState) {
        JarvisNeon.glassCard(p, 1f, 1f, w - 1f, h - 1f, glow = JarvisDesign.Glow.STRONG, active = true)
        val ts = d(600, 13f)
        p.drawText(ContinuumSurfaces.fit(p, title, ts, w - 28f), 14f, 24f, ts, solid(JarvisDesign.Colors.cyan))
        val body = t(400, 11f)
        lines.take(2).forEachIndexed { i, line ->
            p.drawText(ContinuumSurfaces.fit(p, line, body, w - 28f), 14f, 44f + i * 16f, body, solid(JarvisDesign.Colors.text))
        }
    }
    override fun describe(s: ContinuumDashboardState) = title + ". " + lines.joinToString(". ")
}
