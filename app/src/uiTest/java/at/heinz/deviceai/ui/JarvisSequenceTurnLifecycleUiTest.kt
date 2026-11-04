package at.heinz.deviceai.ui

import android.app.Activity
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real sequence view and event bus; synthetic events do not represent on-device worker execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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
}
