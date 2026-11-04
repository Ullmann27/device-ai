package at.heinz.deviceai.ui

import android.content.Context
import at.heinz.deviceai.cockpit.AlarmKind
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

/** Original production view/event/effects logic, with explicit non-Android platform stand-ins. */
class SequenceLifecycleHostTest {
    private val view = JarvisSequenceView(Context()).apply { clock = { 1_000L } }
    private val rendererFailures = mutableListOf<Throwable>()
    private val bus = CockpitAnimationBus().apply {
        subscribe { event ->
            // The real bus isolates renderer errors. Record them before its runCatching,
            // so a forbidden fixture call cannot produce a falsely passing negative case.
            try { view.render(event) } catch (failure: Throwable) { rendererFailures += failure }
        }
    }

    @After fun noRendererOrFixtureFailureWasHidden() {
        assertTrue("Renderer/fixture errors: $rendererFailures", rendererFailures.isEmpty())
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
        assertEquals("Boot cannot claim worker ownership", 1, view.effects.cardCount)
    }

    @Test fun navigationEventDoesNotBlockTheFirstWorker() {
        bus.emit(CockpitAnimationEvent.AreaChanged(NAV_TURN, 0L, "home", "agents", TransitionPattern.B_FLY_IN))
        worker("worker-1")
        assertEquals("Navigation cannot claim worker ownership", 1, view.effects.cardCount)
    }

    @Test fun lateTimelineCannotReopenACompletedTurn() {
        command("turn-1")
        timeline("turn-1")
        assertEquals(1, view.effects.cardCount)
        end("turn-1")
        assertEquals(0, view.effects.cardCount)
        timeline("turn-1")
        assertEquals("A finished work card cannot be resurrected", 0, view.effects.cardCount)
    }

    @Test fun delayedCommandCannotReopenACompletedTurn() {
        end("turn-1")
        command("turn-1")
        timeline("turn-1")
        assertEquals("End wins over a delayed command", 0, view.effects.cardCount)
    }

    @Test fun delayedOldCommandCannotDisplaceTheCurrentTurn() {
        command("old-turn")
        command("current-turn")
        timeline("current-turn")
        assertEquals(1, view.effects.cardCount)
        command("old-turn")
        assertEquals("A displaced command cannot remove current cards", 1, view.effects.cardCount)
    }

    @Test fun lateCallbackFromANonvisibleCompletedTurnIsDropped() {
        command("current-turn")
        end("never-visible")
        end("current-turn")
        worker("never-visible")
        assertEquals("A background completion must still close the old turn", 0, view.effects.cardCount)
    }

    @Test fun aNewWorkerCanStartAfterACompletedTurn() {
        command("turn-1")
        end("turn-1")
        worker("worker-2")
        assertEquals(1, view.effects.cardCount)
    }

    @Test fun memoryRecallRemainsIndependentOfCompletedTurns() {
        command("turn-1")
        end("turn-1")
        bus.emit(CockpitAnimationEvent.RecallHits(MEMORY_TURN, 4L, "synthetic-query", 2, 0.7f))
        assertEquals(2, view.effects.recallCount)
        assertEquals(0.7f, view.effects.recallBrightness(1_000L), 0f)
        assertEquals(0, view.effects.cardCount)
    }

    @Test fun alarmStatusCannotBlockAWorker() {
        bus.emit(CockpitAnimationEvent.AlarmRaised("alarm", 0L, AlarmKind.THERMAL, "test", 4.0, 4.0))
        worker("worker-1")
        assertEquals("A global status event cannot claim a task", 1, view.effects.cardCount)
    }

    @Test fun alarmExtensionStillRunsWithoutOwningATurn() {
        bus.emit(object : CockpitAnimationEvent.Extension("alarm") {
            override val turnId = "alarm"
            override val atMillis = 1L
        })
        assertTrue(view.effects.alarmActive(1_000L))
        worker("worker-1")
        assertEquals(1, view.effects.cardCount)
    }

    @Test fun activeTurnStillRejectsUnrelatedWorkerCallbacks() {
        command("current-turn")
        worker("old-worker")
        assertEquals(0, view.effects.cardCount)
        worker("current-turn")
        assertEquals(1, view.effects.cardCount)
    }

    @Test fun disabledMotionStillClearsEffectsAndRejectsEvents() {
        worker("worker-1")
        view.setMotionAllowed(false)
        worker("worker-2")
        assertEquals(0, view.effects.cardCount)
        assertFalse(view.effects.isAnimating(1_000L))
    }

    @Test fun recentCompletionsRemainClosedAfterALongSession() {
        repeat(1_000) { end("completed-$it") }
        for (id in 980 until 1_000) worker("completed-$id")
        assertEquals("Recent retirement survives more turns than the history window", 0, view.effects.cardCount)
        worker("new-worker")
        assertEquals("Retirement does not disable new work after the window fills", 1, view.effects.cardCount)
    }

    @Test fun motionPauseKeepsCompletedTurnsClosed() {
        command("completed")
        end("completed")
        view.setMotionAllowed(false)
        view.setMotionAllowed(true)
        worker("completed")
        assertEquals("A visual clear does not erase known completion", 0, view.effects.cardCount)
    }

    @Test fun motionPauseDoesNotDeclareActiveWorkCompleted() {
        command("active")
        timeline("active")
        view.setMotionAllowed(false)
        view.setMotionAllowed(true)
        worker("active")
        assertEquals("The active job may report again after a visual pause", 1, view.effects.cardCount)
    }
}
