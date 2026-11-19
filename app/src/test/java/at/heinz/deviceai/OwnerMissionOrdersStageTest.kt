package at.heinz.deviceai

import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.ARGON
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_2
import at.heinz.deviceai.OwnerDocumentDeskHarness.Companion.SUMMARY_3
import at.heinz.deviceai.OwnerMissionHarness.Companion.ANNA
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MissionOrdersEvent
import at.heinz.deviceai.ui.Command03ActivityProjection
import at.heinz.deviceai.ui.Command03OrdersProjection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code71: what the productive mission director hands to the stage reaches the order rail of the Command03 snapshot
 * with the exact outcome of every step – done, waiting for the owner, failed, stopped, skipped – and no longer
 * replaces the activity record of the work window. Planner, director and lanes are the productive code (see
 * [OwnerMissionHarness]); the page and the device are NOT_RUN here.
 */
class OwnerMissionOrdersStageTest {
    private val argonOrder = "Öffne das PDF Argon4, fass Seite 2 zusammen, speichere es als Word und schick es per Mail an $ANNA"

    private fun argon(): OwnerMissionHarness = OwnerMissionHarness().apply {
        deskHost.put("Argon4.pdf", pages = ARGON)
        deskHost.model = { prompt -> OwnerWorkGeneration(if (prompt.contains("Seite drei")) SUMMARY_3 else SUMMARY_2, null, true) }
    }

    /** The stage's two records after everything the mission staged so far, in the order it was staged. */
    private class Stage(h: OwnerMissionHarness) {
        val orders = Command03OrdersProjection { "chat" }
        val activity = Command03ActivityProjection()
        val revisions = mutableListOf<List<String>>()
        init {
            for (event in h.staged) {
                orders.render(event); activity.render(event)
                orders.snapshot("chat")?.let { rows(it) }?.let { if (revisions.lastOrNull() != it) revisions += it }
            }
        }
        val snapshot: JSONObject? get() = orders.snapshot("chat")
        val rows: List<String> get() = rows(snapshot!!)
        private fun rows(value: JSONObject): List<String> {
            val list = value.getJSONArray("items")
            return (0 until list.length()).map { index ->
                list.getJSONObject(index).let { "${it.getInt("number")} ${it.getString("label")}:${it.getString("state")}" }
            }
        }
    }

    @Test fun theArgonMissionReachesTheRailStepByStepAndEndsCompleted() {
        val h = argon()
        assertEquals("MISSION", h.owner(argonOrder))
        val stage = Stage(h)
        val now = stage.snapshot!!
        assertEquals("MISSION", now.getString("source"))
        assertEquals(3, now.getInt("total"))
        assertFalse(now.getBoolean("active"))
        assertEquals("COMPLETED", now.getString("outcome"))
        assertEquals(listOf("1 Das PDF „Argon4“ suchen und öffnen:DONE", "2 Seite 2 zusammenfassen und als Word-Datei speichern:DONE",
            "3 Per E-Mail an $ANNA schicken:DONE"), stage.rows)
        // Each order appeared when its step was handed over – never before – and then changed its state.
        assertEquals(listOf(
            listOf("1 Das PDF „Argon4“ suchen und öffnen:ACTIVE"),
            listOf("1 Das PDF „Argon4“ suchen und öffnen:DONE"),
            listOf("1 Das PDF „Argon4“ suchen und öffnen:DONE", "2 Seite 2 zusammenfassen und als Word-Datei speichern:ACTIVE")),
            stage.revisions.take(3))
        // The overview is the rail's; it does not occupy the activity record of the work window any more.
        assertNull(stage.activity.snapshot())
        // Both forms of the overview are staged together, and the timeline is recognised as the mission's own.
        val timelines = h.staged.filterIsInstance<CockpitAnimationEvent.ProcessTimeline>()
        assertTrue(timelines.isNotEmpty() && timelines.all(MissionOrdersEvent::isOverview))
        assertEquals(timelines.size, h.staged.count { it is MissionOrdersEvent })
    }

    @Test fun aFailedStepIsStampedFailedAndTheStepThatNeverStartedIsNotShown() {
        val h = argon()
        h.owner(argonOrder.replace("Seite 2", "Seite 9"))
        val stage = Stage(h)
        assertEquals(listOf("1 Das PDF „Argon4“ suchen und öffnen:DONE", "2 Seite 9 zusammenfassen und als Word-Datei speichern:FAILED"),
            stage.rows)
        assertEquals("FAILED", stage.snapshot!!.getString("outcome"))
        assertEquals(3, stage.snapshot!!.getInt("total"))
    }

    @Test fun aStopIsStampedStoppedNotFailedOrBlocked() {
        val h = argon()
        h.deskHost.duringGeneration = { assertTrue(h.interrupt("Stopp")) }
        h.owner(argonOrder)
        val stage = Stage(h)
        assertEquals(listOf("1 Das PDF „Argon4“ suchen und öffnen:DONE", "2 Seite 2 zusammenfassen und als Word-Datei speichern:STOPPED"),
            stage.rows)
        assertEquals("STOPPED", stage.snapshot!!.getString("outcome"))
        assertFalse(stage.snapshot!!.getBoolean("active"))
    }

    @Test fun aStepThatWaitsForTheOwnerSaysSoAndAStepTheOwnerDeclinedIsSkipped() {
        val waiting = argon()
        waiting.deskHost.put("Argon4 Datenblatt.pdf", folder = "Documents", pages = listOf("Datenblatt."))
        waiting.deskHost.put("Argon4 Handbuch.pdf", folder = "Documents", pages = listOf("Handbuch."))
        waiting.deskHost.library.removeAll { it.name == "Argon4.pdf" }
        waiting.owner("Öffne das PDF Argon4, fass Seite 1 zusammen, speichere es als Word und schick es per Mail an $ANNA")
        var stage = Stage(waiting)
        assertEquals(listOf("1 Das PDF „Argon4“ suchen und öffnen:WAITING"), stage.rows)
        assertTrue(stage.snapshot!!.getBoolean("active"))
        assertTrue(stage.snapshot!!.isNull("outcome"))

        val declined = argon()
        declined.owner(argonOrder)
        declined.owner("Ich meinte als PDF")
        assertEquals("MISSION", declined.owner("Nein"))
        stage = Stage(declined)
        assertEquals("3 Per E-Mail an $ANNA schicken:SKIPPED", stage.rows.last())
        assertTrue(stage.rows.toString(), stage.rows.none { it.endsWith(":BLOCKED") })
    }
}
