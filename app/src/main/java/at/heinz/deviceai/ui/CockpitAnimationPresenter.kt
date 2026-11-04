package at.heinz.deviceai.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.view.View
import android.view.animation.DecelerateInterpolator
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
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
class CockpitAnimationPresenter(private val home: ContinuumHomeView) : CockpitAnimationRenderer {
    private val density = home.resources.displayMetrics.density
    private val power = home.context.getSystemService(Context.POWER_SERVICE) as PowerManager

    override fun render(event: CockpitAnimationEvent) {
        home.post { present(event) }
    }

    /**
     * The ONE area/scene transition. B: JarvisShellView entrance (shell stage) or card fly-in (cockpit).
     * A: minimal depth move with JarvisMotion.Depth (FAR scale → 1); full depth scene is Avatar/hook territory.
     */
    private fun playArea(event: CockpitAnimationEvent.AreaChanged) {
        when (event.pattern) {
            at.heinz.deviceai.cockpit.TransitionPattern.B_FLY_IN ->
                if (home.jarvisShellStage) home.jarvisShell.playEntrance() else if (home.landscapeCockpit) flyInCards()
            at.heinz.deviceai.cockpit.TransitionPattern.C_WARP -> Unit // visual via hook only (no default)
            at.heinz.deviceai.cockpit.TransitionPattern.A_DEPTH -> if (motionAllowed()) {
                val s = JarvisMotion.Depth.scale(JarvisMotion.Depth.Layer.FAR)
                home.animate().cancel()
                home.scaleX = s; home.scaleY = s
                home.animate().scaleX(1f).scaleY(1f).setDuration(JarvisMotion.Durations.SCENE_Z).start()
            }
        }
    }

    private fun motionAllowed() = ValueAnimator.areAnimatorsEnabled() && !power.isPowerSaveMode

    private fun present(event: CockpitAnimationEvent) {
        if (!home.isAttachedToWindow) return
        if (home.ownsAvatarSlot) home.avatarRenderer?.let { r -> runCatching { r.onAnimationEvent(event) } }
        val hook = CockpitTransitions.hook
        if (hook != null && runCatching { hook.play(event, home) }.getOrDefault(false)) return
        if (event is CockpitAnimationEvent.AreaChanged) return playArea(event)
        if (home.jarvisShellStage) {
            // Shell stage: pattern B entrance inside JarvisShellView (JarvisMotion).
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
            is CockpitAnimationEvent.CalculationShown -> home.showTransientCard("Berechnung · " + event.expression,
                event.steps.take(4).map { it.label + " = " + it.value } + ("Ergebnis = " + event.result + (event.unit?.let { " $it" } ?: "")),
                motionAllowed())
            is CockpitAnimationEvent.AreaChanged -> Unit // handled by playArea
            // Alarm visuals belong to Jarvis Lückenfüller (hook); minimal default: system card glows.
            is CockpitAnimationEvent.AlarmRaised -> highlight(Slot.SYSTEM_STATUS)
            is CockpitAnimationEvent.AlarmCleared -> Unit
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

    private fun flyInCards() {
        if (!motionAllowed()) return
        ContinuumLandscapeCockpitLayout.cards.forEachIndexed { index, slot ->
            val view = home.viewFor(slot) ?: return@forEachIndexed
            val fromLeft = slot in listOf(Slot.MODEL, Slot.PROCESSES, Slot.LIVE_DIALOG)
            view.animate().cancel()
            view.translationX = (if (fromLeft) -28f else 28f) * density
            view.alpha = .25f
            view.animate().translationX(0f).alpha(1f).setStartDelay(index * 45L).setDuration(280L)
                .setInterpolator(DecelerateInterpolator()).start()
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
