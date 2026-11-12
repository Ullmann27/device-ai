package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cockpit.CockpitAnimationBus
import org.junit.Assert.*
import org.junit.Test

class WorkflowReviewTest {
    private fun event(seq: Long, stage: CockpitWorkEvent.Stage, step: String? = null,
                      revision: Long = 0, frame: Long = 0,
                      task: String = "task", session: Long = 1) =
        CockpitWorkEvent(task, session, seq, seq, seq, stage, step,
            if (stage == CockpitWorkEvent.Stage.DISPATCHED) CockpitWorkEvent.Action.SET_TEXT else null,
            observationRevision = revision, frameSequence = frame)

    @Test fun preservesDraftPunctuation() {
        for (draft in listOf("Hallo!", "Kommst du?", "Hallo.")) {
            val parsed = OwnerComputerIntentParser.parse("Bereite eine Nachricht an Anna in WhatsApp vor: $draft")
            assertTrue(parsed is OwnerComputerIntentParser.Result.Ready)
            assertEquals(draft, (parsed as OwnerComputerIntentParser.Result.Ready).intent.draft)
        }
    }

    @Test fun observationsNeverMoveBackwards() {
        val reducer = CockpitWorkReducer().apply { bind("task", 1) }
        assertTrue(reducer.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 100))
        assertTrue(reducer.apply(event(2, CockpitWorkEvent.Stage.DISPATCHED, "A", 10), 100))
        assertTrue(reducer.apply(event(3, CockpitWorkEvent.Stage.OBSERVED, "A", 12, 12), 100))
        assertFalse(reducer.apply(event(4, CockpitWorkEvent.Stage.OBSERVED, "A", 11, 11), 100))
        assertEquals(12L, reducer.snapshot(100).observationRevision)
    }

    @Test fun staleDispatchCannotReusePreviousObservation() {
        val reducer = CockpitWorkReducer().apply { bind("task", 1) }
        assertTrue(reducer.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 100))
        assertTrue(reducer.apply(event(2, CockpitWorkEvent.Stage.DISPATCHED, "A", 10), 100))
        assertTrue(reducer.apply(event(3, CockpitWorkEvent.Stage.OBSERVED, "A", 11), 100))
        assertTrue(reducer.apply(event(4, CockpitWorkEvent.Stage.VERIFIED, "A", 11), 100))
        val dispatched = reducer.apply(event(5, CockpitWorkEvent.Stage.DISPATCHED, "B", 10), 100)
        val verified = reducer.apply(event(6, CockpitWorkEvent.Stage.VERIFIED, "B", 11), 100)
        assertFalse("B accepted stale dispatch=$dispatched and verification=$verified", dispatched || verified)
    }

    @Test fun lateFailureDoesNotPauseSuccessor() {
        val bus = CockpitAnimationBus()
        val reducer = CockpitWorkReducer().apply { bind("task", 1) }
        bus.subscribe { if (it is CockpitWorkEvent) reducer.apply(it, 100) }
        var now = 0L
        val emitter = CockpitWorkEmitter("task", 1, bus, nanos = { ++now }, wallMillis = { now })
        val adapter = LiveActionCockpitAdapter(emitter, 1)
        emitter.emit(CockpitWorkEvent.Stage.ACCEPTED)
        adapter.emit(LiveActionEvent.Dispatched("task", "A", NodeActionKind.SET_TEXT, "com.whatsapp", null, 10, 0))
        adapter.emit(LiveActionEvent.Observed("task", "A", ObservationStamp("task", 1, 11, "com.whatsapp", 42, "b", 11)))
        adapter.emit(LiveActionEvent.Verified("task", "A", NodeActionKind.SET_TEXT, EffectVerdict.CONFIRMED))
        adapter.emit(LiveActionEvent.Dispatched("task", "B", NodeActionKind.SET_TEXT, "com.whatsapp", null, 11, 0))
        adapter.emit(LiveActionEvent.Verified("task", "A", NodeActionKind.SET_TEXT, EffectVerdict.UNCLEAR_UNCHANGED))
        assertEquals(CockpitWorkEvent.Stage.DISPATCHED, reducer.snapshot(100).stage)
    }

    @Test fun freshSuccessorNeedsItsOwnObservation() {
        val reducer = CockpitWorkReducer().apply { bind("task", 1) }
        assertTrue(reducer.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 100))
        assertTrue(reducer.apply(event(2, CockpitWorkEvent.Stage.DISPATCHED, "A", 10), 100))
        assertTrue(reducer.apply(event(3, CockpitWorkEvent.Stage.OBSERVED, "A", 11), 100))
        assertTrue(reducer.apply(event(4, CockpitWorkEvent.Stage.VERIFIED, "A", 11), 100))
        assertTrue(reducer.apply(event(5, CockpitWorkEvent.Stage.DISPATCHED, "B", 11), 100))
        assertFalse(reducer.apply(event(6, CockpitWorkEvent.Stage.VERIFIED, "B", 11), 100))
        assertTrue(reducer.apply(event(7, CockpitWorkEvent.Stage.OBSERVED, "B", 12), 100))
        assertTrue(reducer.apply(event(8, CockpitWorkEvent.Stage.VERIFIED, "B", 12), 100))
        assertTrue(reducer.apply(event(9, CockpitWorkEvent.Stage.COMPLETED), 100))
    }

    @Test fun completedStepCannotDispatchTwice() {
        val reducer = CockpitWorkReducer().apply { bind("task", 1) }
        assertTrue(reducer.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 100))
        assertTrue(reducer.apply(event(2, CockpitWorkEvent.Stage.DISPATCHED, "A", 10), 100))
        assertTrue(reducer.apply(event(3, CockpitWorkEvent.Stage.OBSERVED, "A", 11), 100))
        assertTrue(reducer.apply(event(4, CockpitWorkEvent.Stage.VERIFIED, "A", 11), 100))
        assertFalse(reducer.apply(event(5, CockpitWorkEvent.Stage.DISPATCHED, "A", 11), 100))
        assertEquals(CockpitWorkEvent.Stage.VERIFIED, reducer.snapshot(100).stage)
    }

    @Test fun stopAndForeignIdentityRemainBlocked() {
        val reducer = CockpitWorkReducer().apply { bind("task", 1) }
        assertFalse(reducer.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED, task = "other"), 100))
        assertFalse(reducer.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED, session = 2), 100))
        assertTrue(reducer.apply(event(1, CockpitWorkEvent.Stage.ACCEPTED), 100))
        assertTrue(reducer.apply(event(2, CockpitWorkEvent.Stage.STOPPED), 100))
        assertFalse(reducer.apply(event(3, CockpitWorkEvent.Stage.DISPATCHED, "A", 10), 100))
        assertFalse(reducer.snapshot(100).active)
        assertEquals(0f, reducer.snapshot(100).clickPulse)
    }

    @Test fun searchNeverBecomesSend() {
        val parsed = OwnerComputerIntentParser.parse("Öffne WhatsApp und suche Anna")
        assertTrue(parsed is OwnerComputerIntentParser.Result.Ready)
        val intent = (parsed as OwnerComputerIntentParser.Result.Ready).intent
        assertEquals(ComputerGoal.SEARCH, intent.goal)
        assertFalse(ComputerPlanStep.REQUIRE_SEND_APPROVAL in intent.steps)
        assertFalse(ComputerPlanStep.SET_DRAFT in intent.steps)
        assertTrue(OwnerComputerIntentParser.parse("Öffne WhatsApp und suche Anna und sende Hallo") is OwnerComputerIntentParser.Result.Unsupported)
    }
}
