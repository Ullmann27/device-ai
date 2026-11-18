package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cognitive.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class Command03ComputerWorkProjectionTest {
    private val chat = "11111111-1111-4111-8111-111111111111"
    private val otherChat = "22222222-2222-4222-8222-222222222222"
    private var now = 1_000_000L
    private val bus = CockpitAnimationBus()
    private val projection = Command03ComputerWorkProjection { now }.also { bus.subscribe(it) }
    private val intent = OwnerComputerIntent(ComputerApp.WHATSAPP, ComputerGoal.SEARCH, "Testkontakt")

    private fun emitter(task: String = "computer-test", session: Long = 7) =
        CockpitWorkEmitter(task, session, bus, nanos = { ++now })

    private fun actions(data: JSONObject?) = checkNotNull(data).getJSONArray("actions").let { list ->
        (0 until list.length()).map { list.getJSONObject(it) }
    }

    @Test fun nothingIsReportedWithoutARealEventAndConsentAloneIsOnlyConsent() {
        assertNull(projection.snapshot(chat, null, consentPending = false))
        val waiting = checkNotNull(projection.snapshot(chat, null, consentPending = true))
        assertTrue(waiting.getBoolean("consentPending"))
        assertEquals("Only the consent fact", 1, waiting.length())
        // Events of other kinds on the same bus never create a record.
        bus.emit(CockpitAnimationEvent.TurnEnded("turn", 1L, delivered = true))
        assertNull(projection.snapshot(chat, null, consentPending = false))
    }

    @Test fun aDispatchedTapCarriesItsMeasuredTargetAndBecomesConfirmedOnlyAfterVerification() {
        val work = emitter()
        work.emit(CockpitWorkEvent.Stage.ACCEPTED)
        work.emit(CockpitWorkEvent.Stage.DISPATCHED, "step-1", CockpitWorkEvent.Action.CLICK,
            CockpitWorkEvent.Target(.25f, .75f), observationRevision = 3, frameSequence = 12)
        var data = checkNotNull(projection.snapshot(chat, null, false))
        assertEquals("computer-test", data.getString("taskId"))
        assertEquals("The capture session is a string like in the frame packets", "7", data.get("captureSession"))
        assertEquals("DISPATCHED", data.getString("stage"))
        assertFalse(data.getBoolean("consentPending"))
        assertFalse("No panel state, no app label is invented", data.has("app"))
        val tap = actions(data).single()
        assertEquals("CLICK", tap.getString("kind"))
        assertEquals("DISPATCHED", tap.getString("state"))
        assertEquals(.25, tap.getDouble("x"), 1e-6)
        assertEquals(.75, tap.getDouble("y"), 1e-6)
        assertEquals(12L, tap.getLong("frameSequence"))
        assertEquals(3L, tap.getLong("observationRevision"))
        // An observation alone confirms nothing.
        work.emit(CockpitWorkEvent.Stage.OBSERVED, "step-1", observationRevision = 4, frameSequence = 13)
        assertEquals("DISPATCHED", actions(projection.snapshot(chat, null, false)).single().getString("state"))
        work.emit(CockpitWorkEvent.Stage.VERIFIED, "step-1", CockpitWorkEvent.Action.CLICK, observationRevision = 4)
        data = checkNotNull(projection.snapshot(chat, null, false))
        assertEquals("VERIFIED", data.getString("stage"))
        assertEquals("VERIFIED", actions(data).single().getString("state"))
    }

    @Test fun textAndScrollHaveNoTargetUnlessMeasuredAndAnUnverifiedEffectIsReportedAsUnconfirmed() {
        val work = emitter()
        work.emit(CockpitWorkEvent.Stage.ACCEPTED)
        work.emit(CockpitWorkEvent.Stage.DISPATCHED, "type", CockpitWorkEvent.Action.SET_TEXT, observationRevision = 2)
        val typed = actions(projection.snapshot(chat, null, false)).single()
        assertEquals("SET_TEXT", typed.getString("kind"))
        assertFalse("No coordinate is made up for an input without a measured target", typed.has("x") || typed.has("y"))
        assertFalse("Owner text never enters the stage record", typed.toString().contains("Testkontakt"))
        // The workflow pauses because the effect could not be verified.
        work.emit(CockpitWorkEvent.Stage.PAUSED, "type")
        val data = checkNotNull(projection.snapshot(chat, null, false))
        assertEquals("PAUSED", data.getString("stage"))
        assertEquals("UNCONFIRMED", actions(data).single().getString("state"))
    }

    @Test fun aStoppedTaskKeepsWhatWasReallyDoneAndMarksTheOpenInputUnconfirmed() {
        val work = emitter()
        work.emit(CockpitWorkEvent.Stage.ACCEPTED)
        work.emit(CockpitWorkEvent.Stage.DISPATCHED, "a", CockpitWorkEvent.Action.SCROLL, observationRevision = 2)
        work.emit(CockpitWorkEvent.Stage.OBSERVED, "a", observationRevision = 3, frameSequence = 5)
        work.emit(CockpitWorkEvent.Stage.VERIFIED, "a", CockpitWorkEvent.Action.SCROLL, observationRevision = 3)
        work.emit(CockpitWorkEvent.Stage.DISPATCHED, "b", CockpitWorkEvent.Action.CLICK,
            CockpitWorkEvent.Target(.5f, .5f), observationRevision = 3, frameSequence = 5)
        work.emit(CockpitWorkEvent.Stage.STOPPED)
        val data = checkNotNull(projection.snapshot(chat, null, false))
        assertEquals("STOPPED", data.getString("stage"))
        assertEquals(listOf("SCROLL:VERIFIED", "CLICK:UNCONFIRMED"),
            actions(data).map { it.getString("kind") + ":" + it.getString("state") })
        // Nothing arrives after the end of a task.
        assertFalse(work.emit(CockpitWorkEvent.Stage.DISPATCHED, "c", CockpitWorkEvent.Action.CLICK, observationRevision = 9))
        assertEquals(2, actions(projection.snapshot(chat, null, false)).size)
    }

    @Test fun onlyAdmittedEventsOfTheBoundTaskCountAndTheLogIsBounded() {
        val work = emitter()
        // Without ACCEPTED there is no task; a foreign task or an out-of-order event changes nothing.
        work.emit(CockpitWorkEvent.Stage.DISPATCHED, "early", CockpitWorkEvent.Action.CLICK, observationRevision = 1)
        assertNull(projection.snapshot(chat, null, false))
        work.emit(CockpitWorkEvent.Stage.ACCEPTED)
        bus.emit(CockpitWorkEvent("other-task", 9, 1, now, 1L, CockpitWorkEvent.Stage.DISPATCHED, "x",
            CockpitWorkEvent.Action.CLICK, observationRevision = 1))
        bus.emit(CockpitWorkEvent("computer-test", 7, 1, now, 1L, CockpitWorkEvent.Stage.DISPATCHED, "replay",
            CockpitWorkEvent.Action.CLICK, observationRevision = 1))
        assertEquals(0, actions(projection.snapshot(chat, null, false)).size)
        repeat(Command03ComputerWorkProjection.MAX_ACTIONS + 3) { index ->
            val revision = 2L + index * 2
            work.emit(CockpitWorkEvent.Stage.DISPATCHED, "s$index", CockpitWorkEvent.Action.CLICK,
                CockpitWorkEvent.Target(.1f, .1f), observationRevision = revision, frameSequence = revision)
            work.emit(CockpitWorkEvent.Stage.OBSERVED, "s$index", observationRevision = revision + 1,
                frameSequence = revision + 1)
            work.emit(CockpitWorkEvent.Stage.VERIFIED, "s$index", CockpitWorkEvent.Action.CLICK,
                observationRevision = revision + 1)
        }
        val list = actions(projection.snapshot(chat, null, false))
        assertEquals(Command03ComputerWorkProjection.MAX_ACTIONS, list.size)
        assertTrue("The newest actions are kept, in order",
            list.map { it.getLong("sequence") }.zipWithNext().all { (a, b) -> a < b })
        assertTrue(list.all { it.getString("state") == "VERIFIED" })
    }

    @Test fun appAndGoalComeOnlyFromThePanelOfTheSameTaskAndARecordNeverFollowsIntoAnotherChat() {
        val work = emitter()
        work.emit(CockpitWorkEvent.Stage.ACCEPTED)
        val reducer = CockpitWorkReducer().apply { bind("computer-test", 7) }
        assertTrue(reducer.apply(CockpitWorkEvent("computer-test", 7, 1, now, 1L, CockpitWorkEvent.Stage.ACCEPTED), now))
        val panel = checkNotNull(ComputerPanelState.from("computer-test", 7, intent, reducer.snapshot(now)))
        val data = checkNotNull(projection.snapshot(chat, panel, false))
        assertEquals("WhatsApp", data.getString("app"))
        assertEquals("Suche prüfen", data.getString("goal"))
        val foreign = CockpitWorkReducer().apply { bind("another", 8) }
        assertTrue(foreign.apply(CockpitWorkEvent("another", 8, 1, now, 1L, CockpitWorkEvent.Stage.ACCEPTED), now))
        val other = checkNotNull(ComputerPanelState.from("another", 8, intent, foreign.snapshot(now)))
        assertFalse(checkNotNull(projection.snapshot(chat, other, false)).has("app"))
        assertNull("Another chat never inherits the record", projection.snapshot(otherChat, panel, false))
        assertNull("…and it does not come back", projection.snapshot(chat, panel, false))
        // A new approved task starts a new record.
        val next = emitter("computer-next", 8)
        next.emit(CockpitWorkEvent.Stage.ACCEPTED)
        val fresh = checkNotNull(projection.snapshot(otherChat, null, true))
        assertEquals("computer-next", fresh.getString("taskId"))
        assertTrue(fresh.getBoolean("consentPending"))
        assertEquals(0, actions(fresh).size)
    }
}
