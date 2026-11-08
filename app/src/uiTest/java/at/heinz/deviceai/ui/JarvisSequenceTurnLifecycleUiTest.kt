package at.heinz.deviceai.ui

import android.app.Activity
import android.os.Looper
import android.widget.FrameLayout
import at.heinz.deviceai.cockpit.BOOT_TURN
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MEMORY_TURN
import at.heinz.deviceai.cockpit.NAV_TURN
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import at.heinz.deviceai.cockpit.TransitionPattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Real sequence view and event bus; synthetic events do not represent on-device worker execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisSequenceTurnLifecycleUiTest {
    private val controller = Robolectric.buildActivity(Activity::class.java).setup()
    private val activity = controller.get()
    private val view = JarvisSequenceView(activity).apply { clock = { 1_000L } }
    private val rendererFailures = mutableListOf<Throwable>()
    private val bus = CockpitAnimationBus().apply {
        subscribe { event ->
            try { view.render(event) } catch (failure: Throwable) { rendererFailures += failure }
        }
    }

    @After fun releaseActivity() {
        try {
            assertTrue("Renderer errors must not disappear inside the event bus: $rendererFailures", rendererFailures.isEmpty())
        } finally {
            view.clear()
            controller.pause().stop().destroy()
        }
    }

    private fun command(id: String) = bus.emit(CockpitAnimationEvent.CommandFlyIn(id, 1L, "Prüfe den Auftrag"))
    private fun timeline(id: String) = bus.emit(CockpitAnimationEvent.ProcessTimeline(id, 2L,
        listOf(ProcessStep("work", "Bearbeitung", StepState.ACTIVE))))
    private fun end(id: String) = bus.emit(CockpitAnimationEvent.TurnEnded(id, 3L, true))
    private fun worker(id: String) = bus.emit(CockpitAnimationEvent.ActionStarted(id, 4L,
        CockpitAction.WORKER, "Lokale Prüfung"))

    private fun attachSequence(): FrameLayout {
        val host = FrameLayout(activity)
        activity.setContentView(host)
        host.addView(view)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("The queue/lifecycle case requires a real attached View", view.isAttachedToWindow)
        return host
    }

    private fun emitOffMain(action: () -> Unit) {
        val emitter = Thread(action, "sequence-lifecycle-emitter")
        emitter.start()
        emitter.join(5_000L)
        assertFalse("The background emitter must finish before the lifecycle boundary", emitter.isAlive)
    }

    @Test fun bootEventDoesNotBlockTheFirstWorker() {
        bus.emit(CockpitAnimationEvent.BootStarted(BOOT_TURN, 0L, "Bereit"))

        worker("worker-1")

        assertEquals("The boot animation does not own a worker turn", 1, view.effects.cardCount)
    }

    @Test fun navigationEventDoesNotBlockTheFirstWorker() {
        bus.emit(CockpitAnimationEvent.AreaChanged(NAV_TURN, 0L, "home", "agents", TransitionPattern.B_FLY_IN))

        worker("worker-1")

        assertEquals("Navigation must not consume the next worker's slot", 1, view.effects.cardCount)
    }

    @Test fun lateTimelineCannotReopenACompletedTurn() {
        command("turn-1")
        timeline("turn-1")
        assertEquals(1, view.effects.cardCount)
        end("turn-1")
        assertEquals(0, view.effects.cardCount)

        timeline("turn-1")

        assertEquals("A late callback cannot revive the completed work card", 0, view.effects.cardCount)
    }

    @Test fun delayedCommandCannotReopenACompletedTurn() {
        end("turn-1")

        command("turn-1")
        timeline("turn-1")

        assertEquals("Completion wins even if the command was delayed in another UI queue", 0, view.effects.cardCount)
    }

    @Test fun delayedOldCommandCannotDisplaceTheCurrentTurn() {
        command("old-turn")
        command("current-turn")
        timeline("current-turn")
        assertEquals(1, view.effects.cardCount)

        command("old-turn")

        assertEquals("The current work card survives a command from the displaced turn", 1, view.effects.cardCount)
    }

    @Test fun lateCallbackFromANonvisibleCompletedTurnIsDropped() {
        command("current-turn")
        end("never-visible")
        end("current-turn")

        worker("never-visible")

        assertEquals("The view remembers completion even when another turn was visible", 0, view.effects.cardCount)
    }

    @Test fun aNewWorkerCanStartAfterACompletedTurn() {
        command("turn-1")
        end("turn-1")

        worker("worker-2")

        assertEquals("A different real worker does not need a command animation", 1, view.effects.cardCount)
    }

    @Test fun memoryRecallRemainsIndependentOfCompletedTurns() {
        command("turn-1")
        end("turn-1")

        bus.emit(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 4L, "synthetic-query", 2, 0.7f))

        assertEquals(2, view.effects.recallCount)
        assertEquals(0.7f, view.effects.recallBrightness(1_000L), 0f)
        assertEquals(0, view.effects.cardCount)
    }

    @Test fun recentCompletionsRemainClosedAfterALongSession() {
        repeat(1_000) { end("completed-$it") }
        for (id in 980 until 1_000) worker("completed-$id")
        assertEquals(0, view.effects.cardCount)
        worker("new-worker")
        assertEquals(1, view.effects.cardCount)
    }

    @Test fun motionPauseKeepsCompletedTurnsClosed() {
        command("completed")
        end("completed")
        view.setMotionAllowed(false)
        view.setMotionAllowed(true)

        worker("completed")

        assertEquals(0, view.effects.cardCount)
    }

    @Test fun motionPauseDoesNotDeclareActiveWorkCompleted() {
        command("active")
        timeline("active")
        view.setMotionAllowed(false)
        view.setMotionAllowed(true)

        worker("active")

        assertEquals(1, view.effects.cardCount)
    }

    @Test fun completionQueuedBeforeMotionPauseSurvivesGenerationChange() {
        attachSequence()
        worker("completed")
        emitOffMain { end("completed") }
        view.setMotionAllowed(false)
        view.setMotionAllowed(true)
        shadowOf(Looper.getMainLooper()).idle()

        worker("completed")

        assertEquals("A queued completion survives the visual generation change", 0, view.effects.cardCount)
    }

    @Test fun completionQueuedBeforeDetachSurvivesReattach() {
        val host = attachSequence()
        worker("completed")
        emitOffMain { end("completed") }
        host.removeView(view)
        assertFalse(view.isAttachedToWindow)
        host.addView(view)
        assertTrue(view.isAttachedToWindow)
        shadowOf(Looper.getMainLooper()).idle()

        timeline("completed")

        assertEquals("An already received completion survives detaching the same subscriber", 0, view.effects.cardCount)
    }

    @Test fun directlySubscribedViewRetainsCompletionReceivedWhileDetached() {
        val host = attachSequence()
        worker("completed")
        host.removeView(view)
        assertFalse(view.isAttachedToWindow)
        // This test keeps the direct bus subscription. It does not simulate an unsubscribed/new view.
        end("completed")
        host.addView(view)
        assertTrue(view.isAttachedToWindow)
        shadowOf(Looper.getMainLooper()).idle()

        worker("completed")

        assertEquals("The same subscriber remembers a completion it really received", 0, view.effects.cardCount)
    }

    @Test fun directlySubscribedViewRetainsBackgroundCompletionReceivedWhileDetached() {
        val host = attachSequence()
        worker("completed")
        host.removeView(view)
        assertFalse(view.isAttachedToWindow)
        emitOffMain { end("completed") }
        host.addView(view)
        assertTrue(view.isAttachedToWindow)
        shadowOf(Looper.getMainLooper()).idle()

        worker("completed")

        assertEquals("An off-main completion received by this subscriber survives reattach", 0, view.effects.cardCount)
    }

    @Test fun backgroundCompletionBeforeFirstAttachDoesNotWaitForAttachment() {
        assertFalse(view.isAttachedToWindow)
        view.setMotionAllowed(false)
        emitOffMain { end("completed-before-attach") }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("This subscriber has never been attached", view.isAttachedToWindow)
        assertFalse(view.effects.isAnimating(1_000L))
        view.setMotionAllowed(true)

        worker("completed-before-attach")

        assertEquals("Completion delivery must not depend on a future View attachment", 0, view.effects.cardCount)
    }

    @Test fun detachedSubscriberRetiresBackgroundCompletionsWithoutReattach() {
        val host = attachSequence()
        host.removeView(view)
        assertFalse(view.isAttachedToWindow)
        emitOffMain { repeat(1_000) { end("detached-completed-$it") } }
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("No reattach may be needed to drain completion delivery", view.isAttachedToWindow)
        assertEquals(0, view.effects.cardCount)
        assertFalse(view.effects.isAnimating(1_000L))

        // Inspect only the bounded presentation history: reattaching to observe cards
        // would itself drain View.post's deferred queue and hide the delivery defect.
        val history = JarvisSequenceView::class.java.getDeclaredField("retiredTurns").apply {
            isAccessible = true
        }.get(view) as Set<*>
        assertEquals("All completions drain while detached, retaining only the recent window",
            (976 until 1_000).map { "detached-completed-$it" }.toSet(), history)
    }

    @Test fun unfinishedWorkCanResumeAfterDetach() {
        val host = attachSequence()
        worker("still-active")
        host.removeView(view)
        assertFalse(view.isAttachedToWindow)
        host.addView(view)
        assertTrue(view.isAttachedToWindow)
        shadowOf(Looper.getMainLooper()).idle()

        worker("still-active")

        assertEquals("Detaching is not evidence of job completion", 1, view.effects.cardCount)
    }

    @Test fun queuedVisualEventIsStillDiscardedAcrossMotionPause() {
        attachSequence()
        emitOffMain { worker("still-active") }
        view.setMotionAllowed(false)
        view.setMotionAllowed(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Only completion survives a visual generation boundary", 0, view.effects.cardCount)

        worker("still-active")

        assertEquals("Discarding a visual callback does not retire the live job", 1, view.effects.cardCount)
    }

    @Test fun queuedVisualEventIsStillDiscardedAcrossDetach() {
        val host = attachSequence()
        emitOffMain { worker("still-active") }
        host.removeView(view)
        assertFalse(view.isAttachedToWindow)
        host.addView(view)
        assertTrue(view.isAttachedToWindow)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("An old visual callback cannot cross the attachment boundary", 0, view.effects.cardCount)

        worker("still-active")

        assertEquals("A fresh callback from unfinished work is accepted", 1, view.effects.cardCount)
    }

    @Test fun completionReceivedWhileMotionIsPausedRejectsLateWorker() {
        worker("completed")
        view.setMotionAllowed(false)
        end("completed")
        assertFalse(view.effects.isAnimating(1_000L))
        view.setMotionAllowed(true)

        worker("completed")

        assertEquals("A received completion survives a visual pause", 0, view.effects.cardCount)
    }

    @Test fun completionReceivedWhileMotionIsPausedRejectsLateTimeline() {
        command("completed")
        timeline("completed")
        view.setMotionAllowed(false)
        end("completed")
        view.setMotionAllowed(true)

        timeline("completed")

        assertEquals("A timeline cannot revive work completed during the pause", 0, view.effects.cardCount)
    }

    @Test fun unseenCompletionReceivedDuringPauseRejectsDelayedCommand() {
        view.setMotionAllowed(false)
        end("never-visible")
        view.setMotionAllowed(true)

        command("never-visible")
        timeline("never-visible")

        assertEquals("A received end is authoritative even without a visible command", 0, view.effects.cardCount)
    }

    @Test fun unrelatedCompletionDuringPauseDoesNotRetireLiveWork() {
        worker("still-active")
        view.setMotionAllowed(false)
        end("different-completed-turn")
        view.setMotionAllowed(true)

        worker("still-active")

        assertEquals("Only the completed turn is retired", 1, view.effects.cardCount)
    }

    @Test fun newWorkStartsAfterCompletionReceivedDuringPause() {
        view.setMotionAllowed(false)
        end("completed")
        assertEquals(0, view.effects.cardCount)
        assertFalse(view.effects.isAnimating(1_000L))
        view.setMotionAllowed(true)

        worker("new-worker")

        assertEquals("Completion bookkeeping does not acquire a work slot", 1, view.effects.cardCount)
    }

    /**
     * Owner addendum 06.11.: „Auftrag angenommen“ appeared twice. The triggers announce one fact twice – as the action
     * that started and as its timeline step. The card follows the fact's identity (step key), so each step shows once;
     * no valid event is lost (the following tool step still appears).
     */
    @Test fun reasoningAndToolContinuationShareOneMainScene() {
        val triggers = at.heinz.deviceai.cockpit.CockpitAnimationTriggers(bus) { 1_000L }
        triggers.commandAcknowledged("turn-1", "Prüfe den Auftrag")
        assertEquals("THINK + ack/work render as one main scene", 1, view.effects.cardCount)
        triggers.toolStarted("turn-1", "call-1", "launch_app")
        assertEquals("tool takeover updates the same main scene", 1, view.effects.cardCount)
        triggers.toolFinished("turn-1", "call-1", "launch_app", succeeded = true)
        assertEquals("continuing reasoning stays in the same scene", 1, view.effects.cardCount)
    }
}
