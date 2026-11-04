package at.heinz.deviceai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.view.View
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.TransitionPattern
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
class CockpitAnimationPresenter(private val home: ContinuumHomeView,
                                private val drift: JarvisCameraDrift? = null) : CockpitAnimationRenderer {
    private val density = home.resources.displayMetrics.density
    private val power = home.context.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun render(event: CockpitAnimationEvent) {
        home.post { present(event) }
    }

    private fun motionAllowed() = ValueAnimator.areAnimatorsEnabled() && !power.isPowerSaveMode

    private fun present(event: CockpitAnimationEvent) {
        if (!home.isAttachedToWindow) return
        val hook = CockpitTransitions.hook
        if (hook != null && runCatching { hook.play(event, home) }.getOrDefault(false)) return
        if (event is CockpitAnimationEvent.AreaChanged) return areaChanged(event)
        if (home.jarvisShellStage) {
            // Shell stage: Welten-Optik's own entrance (JarvisMotion pattern B inside JarvisShellView).
            if (event is CockpitAnimationEvent.CommandFlyIn) home.jarvisShell.playEntrance()
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
            is CockpitAnimationEvent.AreaChanged -> Unit // handled above (areaChanged)
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

    /** Navigation (Jarvis Integration): B = panels fly in, A = scene change in Z depth. */
    private fun areaChanged(event: CockpitAnimationEvent.AreaChanged) {
        when (event.pattern) {
            TransitionPattern.B_FLY_IN -> when {
                home.jarvisShellStage -> home.jarvisShell.playEntrance()
                home.landscapeCockpit -> flyInCards()
            }
            TransitionPattern.A_DEPTH -> sceneDepth(home, entering = event.toTarget == "home")
        }
    }

    /** Muster B (JarvisMenuMotion.patternB): ±64 dp from the side, 80→100 %, alpha 0→1, 0,6 s cubic ease-out, centre first. */
    private fun flyInCards() {
        if (!motionAllowed()) return
        val views = ContinuumLandscapeCockpitLayout.cards.mapNotNull { home.viewFor(it) }
        val centers = views.map { it.left + it.width / 2f }
        val ease = JarvisMotion.Easing.easeOutCubic
        JarvisMenuMotion.patternB(centers, home.width / 2f).forEach { step ->
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

    /**
     * Muster A (JarvisMenuMotion.patternA, 0,8 s exponential ease-in): leaving – the cockpit flies through the camera
     * (>300 %, alpha 0) while the new screen opens; entering ("Zurück") – the cockpit comes 0→100 % from the centre.
     * The camera drift pauses meanwhile and restarts at 1,00.
     */
    private fun sceneDepth(view: View, entering: Boolean) {
        if (!motionAllowed()) return
        val end = JarvisMenuMotion.patternA(Long.MAX_VALUE / 4)
        drift?.pause()
        view.animate().cancel()
        view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
        val restore = Runnable { view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f; drift?.restart() }
        if (entering) {
            view.scaleX = 0f; view.scaleY = 0f; view.alpha = 0f
            view.animate().scaleX(end.newScale).scaleY(end.newScale).alpha(end.newAlpha)
                .setDuration(JarvisMotion.Durations.SCENE_Z).setInterpolator { JarvisMotion.Easing.easeInExpo(it) }
                .withEndAction(restore).start()
        } else {
            view.animate().scaleX(end.oldScale).scaleY(end.oldScale).alpha(end.oldAlpha)
                .setDuration(JarvisMotion.Durations.SCENE_Z).setInterpolator { JarvisMotion.Easing.easeInExpo(it) }
                .withEndAction(restore).start()
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
