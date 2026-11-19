package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
import at.heinz.deviceai.cockpit.MissionOrder
import at.heinz.deviceai.cockpit.MissionOrderState
import at.heinz.deviceai.cockpit.MissionOrdersEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code71: the order rail of the Command03 stage. Orders exist only for real events – the steps of the running turn
 * as the productive [CockpitAnimationTriggers] report them, or the overview an owner mission publishes about itself –
 * and the mission overview no longer replaces a lane's own timeline in the activity record.
 */
class Command03OrdersProjectionTest {
    private var session = "chat-a"
    private val bus = CockpitAnimationBus()
    private val orders = Command03OrdersProjection { session }.also { bus.subscribe(it) }
    private val activity = Command03ActivityProjection().also { bus.subscribe(it) }
    private val triggers = CockpitAnimationTriggers(bus) { 1_000L }

    private fun snapshot(): JSONObject? = orders.snapshot(session)
    private fun items(value: JSONObject? = snapshot()): List<String> {
        val list = value!!.getJSONArray("items")
        return (0 until list.length()).map { index ->
            list.getJSONObject(index).let { "${it.getInt("number")} ${it.getString("label")}:${it.getString("state")}" }
        }
    }
    private fun activitySteps(): List<String> {
        val list = activity.snapshot()!!.getJSONArray("steps")
        return (0 until list.length()).map { list.getJSONObject(it).let { step -> step.getString("label") + ":" + step.getString("state") } }
    }
    private fun mission(id: String, total: Int, vararg states: Pair<String, MissionOrderState>) {
        // As OwnerMissionDirector.stage publishes it: the timeline first, then the exact overview.
        bus.emit(CockpitAnimationEvent.ProcessTimeline(id, 1L, states.mapIndexed { index, (label, state) ->
            ProcessStep("mission:$index", "${index + 1}/$total $label", when (state) {
                MissionOrderState.RUNNING -> StepState.ACTIVE
                MissionOrderState.WAITING -> StepState.WAITING
                MissionOrderState.DONE -> StepState.DONE
                MissionOrderState.FAILED -> StepState.FAILED
                else -> StepState.BLOCKED
            })
        }))
        bus.emit(MissionOrdersEvent(id, 1L, total, states.mapIndexed { index, (label, state) -> MissionOrder(index, label, state) }))
    }

    @Test fun withoutARealEventThereAreNoOrders() {
        assertNull(snapshot())
        // Events that are no order: a recall, a document, a boot.
        bus.emit(CockpitAnimationEvent.RecallHits("memory", 1L, "hash", 2, .5f))
        bus.emit(CockpitAnimationEvent.TurnEnded("turn-unknown", 1L, true))
        assertNull(snapshot())
    }

    @Test fun aRunningTurnShowsItsRealStepsUnderTheCommandItStartedWith() {
        triggers.commandAcknowledged("turn-1", "Suche die neueste Rechnung und fasse sie zusammen")
        var now = snapshot()!!
        assertEquals("TURN", now.getString("source"))
        assertEquals("Suche die neueste Rechnung und fasse sie zusammen", now.getString("title"))
        assertTrue(now.getBoolean("active"))
        assertTrue(now.isNull("outcome"))
        // The acknowledgement is not a step of work; the turn's own "Bearbeitung" is.
        assertEquals(listOf("1 Bearbeitung:ACTIVE"), items())
        assertEquals(1, now.getInt("total"))
        val key = now.getString("key")
        assertFalse("turn ids never leave the app", now.toString().contains("turn-1"))

        triggers.toolStarted("turn-1", "call-1", "find_files")
        triggers.workStep("turn-1", "summary", "Zusammenfassen", StepState.WAITING, CockpitAction.THINK)
        assertEquals(listOf("1 Bearbeitung:ACTIVE", "2 Werkzeug: find_files:ACTIVE", "3 Zusammenfassen:WAITING"), items())
        triggers.toolFinished("turn-1", "call-1", "find_files", succeeded = true)
        triggers.workStep("turn-1", "summary", "Zusammenfassen", StepState.ACTIVE)
        assertEquals(listOf("1 Bearbeitung:ACTIVE", "2 Werkzeug: find_files:DONE", "3 Zusammenfassen:ACTIVE"), items())
        assertEquals(key, snapshot()!!.getString("key"))

        triggers.turnEnded("turn-1", delivered = true, outcomeVerified = true)
        now = snapshot()!!
        assertFalse(now.getBoolean("active"))
        assertEquals("COMPLETED", now.getString("outcome"))
        assertEquals(listOf("1 Bearbeitung:DONE", "2 Werkzeug: find_files:DONE", "3 Zusammenfassen:UNVERIFIED"), items())
        // The finished orders stay until the next turn starts; that one gets a new key.
        triggers.commandAcknowledged("turn-2", "Und jetzt die Nachrichten")
        now = snapshot()!!
        assertNotEquals(key, now.getString("key"))
        assertEquals("Und jetzt die Nachrichten", now.getString("title"))
        assertEquals(listOf("1 Bearbeitung:ACTIVE"), items())
    }

    @Test fun aFailedToolAndAnUndeliveredTurnAreReportedAsTheyAre() {
        triggers.commandAcknowledged("turn-1", "Öffne die Datei")
        triggers.toolStarted("turn-1", "call-1", "open_file")
        triggers.toolFinished("turn-1", "call-1", "open_file", succeeded = false)
        assertEquals(listOf("1 Bearbeitung:ACTIVE", "2 Werkzeug: open_file:FAILED"), items())
        triggers.turnEnded("turn-1", delivered = false)
        val now = snapshot()!!
        assertEquals("FAILED", now.getString("outcome"))
        assertFalse(now.getBoolean("active"))
        assertEquals(listOf("1 Bearbeitung:FAILED", "2 Werkzeug: open_file:FAILED"), items())
    }

    @Test fun oneRevisionPerRealChange() {
        triggers.commandAcknowledged("turn-1", "Auftrag")
        val first = snapshot()!!.getLong("revision")
        // The same list again is no change.
        bus.emit(CockpitAnimationEvent.ProcessTimeline("turn-1", 2L, triggers.timeline("turn-1")))
        assertEquals(first, snapshot()!!.getLong("revision"))
        triggers.toolStarted("turn-1", "call-1", "find_files")
        assertTrue(snapshot()!!.getLong("revision") > first)
    }

    @Test fun aMissionShowsItsOwnStepsWithTheirExactOutcomes() {
        mission("mission-1", 4, "PDF öffnen" to MissionOrderState.DONE, "Seite 2 zusammenfassen" to MissionOrderState.RUNNING)
        var now = snapshot()!!
        assertEquals("MISSION", now.getString("source"))
        assertEquals(4, now.getInt("total"))
        assertTrue(now.getBoolean("active"))
        assertEquals(listOf("1 PDF öffnen:DONE", "2 Seite 2 zusammenfassen:ACTIVE"), items())
        mission("mission-1", 4, "PDF öffnen" to MissionOrderState.DONE, "Seite 2 zusammenfassen" to MissionOrderState.WAITING)
        assertEquals("2 Seite 2 zusammenfassen:WAITING", items().last())
        mission("mission-1", 4, "PDF öffnen" to MissionOrderState.DONE, "Seite 2 zusammenfassen" to MissionOrderState.STOPPED,
            "Als Word speichern" to MissionOrderState.SKIPPED)
        bus.emit(CockpitAnimationEvent.TurnEnded("mission-1", 3L, false))
        now = snapshot()!!
        assertFalse(now.getBoolean("active"))
        assertEquals("STOPPED", now.getString("outcome"))
        assertEquals(listOf("1 PDF öffnen:DONE", "2 Seite 2 zusammenfassen:STOPPED", "3 Als Word speichern:SKIPPED"), items())
        assertFalse(now.toString().contains("mission-1"))
    }

    @Test fun missionOutcomesFollowTheStepsThatReallyEnded() {
        mission("mission-1", 2, "A" to MissionOrderState.DONE, "B" to MissionOrderState.DONE)
        bus.emit(CockpitAnimationEvent.TurnEnded("mission-1", 2L, true))
        assertEquals("COMPLETED", snapshot()!!.getString("outcome"))
        // A correction reopens the completed mission: it is running again and has no outcome.
        mission("mission-1", 2, "A" to MissionOrderState.DONE, "B" to MissionOrderState.RUNNING)
        assertTrue(snapshot()!!.getBoolean("active"))
        assertTrue(snapshot()!!.isNull("outcome"))
        mission("mission-1", 2, "A" to MissionOrderState.DONE, "B" to MissionOrderState.FAILED)
        bus.emit(CockpitAnimationEvent.TurnEnded("mission-1", 3L, false))
        assertEquals("FAILED", snapshot()!!.getString("outcome"))
        // Ended without a failed or stopped step (the time limit before the next step started).
        mission("mission-2", 3, "A" to MissionOrderState.DONE)
        bus.emit(CockpitAnimationEvent.TurnEnded("mission-2", 4L, false))
        assertEquals("ENDED", snapshot()!!.getString("outcome"))
    }

    @Test fun whileAMissionIsOpenTheLanesTimelineStaysInTheWorkWindowAndTheOverviewInTheRail() {
        // The owner's sentence is a turn of its own; the mission it starts then takes the rail.
        triggers.commandAcknowledged("turn-order", "Öffne Argon4, fass Seite 2 zusammen und speichere es als Word")
        assertEquals("TURN", snapshot()!!.getString("source"))
        mission("mission-1", 2, "PDF öffnen" to MissionOrderState.RUNNING)
        assertEquals("MISSION", snapshot()!!.getString("source"))
        // The lane that does step 1 reports its own steps: they are the activity, not the rail.
        triggers.commandAcknowledged("turn-lane-1", "Öffne das PDF Argon4")
        triggers.workStep("turn-lane-1", "scan", "Download-Ordner durchsuchen", StepState.ACTIVE, CockpitAction.TOOL)
        assertEquals(listOf("1 PDF öffnen:ACTIVE"), items())
        assertEquals(listOf("Auftrag angenommen:DONE", "Bearbeitung:ACTIVE", "Download-Ordner durchsuchen:ACTIVE"), activitySteps())
        // The mission moves on: the rail changes, the activity still shows the lane ("latest emitter wins" is gone).
        mission("mission-1", 2, "PDF öffnen" to MissionOrderState.DONE, "Als Word speichern" to MissionOrderState.RUNNING)
        assertEquals(listOf("1 PDF öffnen:DONE", "2 Als Word speichern:ACTIVE"), items())
        assertEquals(listOf("Auftrag angenommen:DONE", "Bearbeitung:ACTIVE", "Download-Ordner durchsuchen:ACTIVE"), activitySteps())
        assertFalse(activity.snapshot().toString().contains("1/2"))
        // Late reports of the lane and of the turn that started the mission never take the rail back.
        mission("mission-1", 2, "PDF öffnen" to MissionOrderState.DONE, "Als Word speichern" to MissionOrderState.DONE)
        bus.emit(CockpitAnimationEvent.TurnEnded("mission-1", 5L, true))
        triggers.turnEnded("turn-lane-1", delivered = true)
        triggers.turnEnded("turn-order", delivered = true)
        assertEquals("MISSION", snapshot()!!.getString("source"))
        assertEquals("COMPLETED", snapshot()!!.getString("outcome"))
        // The next turn of the owner starts new orders.
        triggers.commandAcknowledged("turn-next", "Danke, und wie wird das Wetter?")
        assertEquals("TURN", snapshot()!!.getString("source"))
        assertEquals(listOf("1 Bearbeitung:ACTIVE"), items())
    }

    @Test fun ordersBelongToTheChatTheyStartedIn() {
        triggers.commandAcknowledged("turn-1", "Auftrag im ersten Gespräch")
        assertEquals("TURN", orders.snapshot("chat-a")!!.getString("source"))
        assertNull(orders.snapshot("chat-b"))
        // The owner switches the chat while the turn still reports: it stays the first chat's order.
        session = "chat-b"
        triggers.toolStarted("turn-1", "call-1", "find_files")
        assertNull(orders.snapshot("chat-b"))
        assertEquals(2, orders.snapshot("chat-a")!!.getInt("total"))
        orders.clear()
        assertNull(orders.snapshot("chat-a"))
    }

    @Test fun aBackgroundWorkerIsNotAnOwnerOrderAndLongListsAreBoundedAroundTheRunningStep() {
        triggers.workerStarted("worker-1", "Index aufbauen")
        assertNull(snapshot())
        triggers.commandAcknowledged("turn-1", "x".repeat(200) + "\u0007")
        assertTrue(snapshot()!!.getString("title").length <= Command03OrdersProjection.MAX_LABEL)
        repeat(12) { index -> triggers.toolStarted("turn-1", "call-$index", "tool_$index"); if (index < 11) triggers.toolFinished("turn-1", "call-$index", "tool_$index", true) }
        val now = snapshot()!!
        assertEquals(13, now.getInt("total"))
        assertEquals(Command03OrdersProjection.MAX_ITEMS, now.getJSONArray("items").length())
        // The numbers are the real positions; the running one is inside the window.
        assertTrue(items().any { it.startsWith("13 ") && it.endsWith(":ACTIVE") })
        assertEquals("6 Werkzeug: tool_4:DONE", items().first())
    }

    @Test fun theStatusStripCarriesOnlyValuesTheAppHolds() {
        val local = Command03TacticalStrip.snapshot(false, "ASK", 0, 87, false)
        assertEquals("LOCAL", local.getString("brain"))
        assertEquals("ASK", local.getString("internet"))
        assertEquals(0, local.getInt("thermal"))
        assertEquals(87, local.getInt("battery"))
        assertFalse(local.getBoolean("charging"))
        val cloud = Command03TacticalStrip.snapshot(true, "ALWAYS_ON", 3, 100, true)
        assertEquals("CLAUDE", cloud.getString("brain"))
        assertEquals("ALWAYS_ON", cloud.getString("internet"))
        assertTrue(cloud.getBoolean("charging"))
        // What the platform did not report, or reported out of range, is absent – never a default figure.
        val unknown = Command03TacticalStrip.snapshot(false, "SOMETIMES", 9, 101, null)
        for (name in listOf("internet", "thermal", "battery", "charging")) assertTrue(name, unknown.isNull(name))
        val missing = Command03TacticalStrip.snapshot(false, null, null, -1, null)
        for (name in listOf("internet", "thermal", "battery", "charging")) assertTrue(name, missing.isNull(name))
    }
}
