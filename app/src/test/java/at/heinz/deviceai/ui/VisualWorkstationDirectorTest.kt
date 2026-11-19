package at.heinz.deviceai.ui

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLaneResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Code73: one work scene at a time, decided from the lanes' real records only. */
class VisualWorkstationDirectorTest {
    private val session = "12345678-1234-1234-1234-123456789abc"

    private fun clean(phase: String, updated: Long = 1) = JSONObject().put("schema", "continuum.deepclean.v1")
        .put("runId", "run-1").put("phase", phase).put("updatedAtMs", updated)
    private fun mail(phase: String, revision: Long) = JSONObject().put("version", 1).put("revision", revision).put("phase", phase)
    private fun research(active: Boolean, revision: Long = 3) = JSONObject().put("revision", revision).put("kind", "RESEARCH")
        .put("title", "Wetter").put("detail", "").put("active", active)
    private fun snapshot(vararg entries: Pair<String, Any>) = JSONObject().apply { entries.forEach { (key, value) -> put(key, value) } }
    private fun kind(scene: JSONObject?) = scene?.getString("kind")

    @Test fun nothingReportedMeansNoScene() {
        val director = VisualWorkstationDirector()
        assertNull(director.decide(snapshot("busy" to true), 0))
        // An empty or malformed record is no lane.
        assertNull(director.decide(snapshot("deepClean" to JSONObject().put("phase", "IDLE"), "mailWork" to JSONObject()), 1))
    }

    @Test fun aRunningStorageCleanIsTheSceneAndKeepsItsStartTime() {
        val director = VisualWorkstationDirector()
        val first = director.decide(snapshot("deepClean" to clean("SCAN")), 100)!!
        assertEquals("deepclean", first.getString("kind"))
        assertEquals(100L, first.getLong("since"))
        assertEquals(100L, director.decide(snapshot("deepClean" to clean("CLEAN", 2)), 900)!!.getLong("since"))
    }

    @Test fun theSceneShownStaysWhileAnotherLaneStartsSoTheGazeIsNotFoughtOver() {
        val director = VisualWorkstationDirector()
        assertEquals("mail", kind(director.decide(snapshot("mailWork" to mail("SEARCH", 1)), 0)))
        // A storage run starts as well: the mail scene stays the one on stage while it works.
        assertEquals("mail", kind(director.decide(snapshot("mailWork" to mail("READ", 2), "deepClean" to clean("SCAN")), 10)))
        // The mail lane ends: the working storage run takes the stage at once.
        val next = director.decide(snapshot("mailWork" to mail("DONE", 3), "deepClean" to clean("CLEAN")), 20)!!
        assertEquals("deepclean", next.getString("kind"))
        assertEquals(20L, next.getLong("since"))
    }

    @Test fun theApprovedComputerTaskWinsAmongNewlyStartedLanes() {
        val director = VisualWorkstationDirector()
        val computer = JSONObject().put("taskId", "t1").put("captureSession", "7").put("stage", "DISPATCHED").put("consentPending", false)
        assertEquals("computer", kind(director.decide(snapshot("computerWork" to computer, "deepClean" to clean("SCAN")), 0)))
        // A finished task leaves the stage to the running lane.
        assertEquals("deepclean", kind(director.decide(snapshot("computerWork" to JSONObject(computer.toString()).put("stage", "COMPLETED"),
            "deepClean" to clean("SCAN")), 5)))
        // A consent that is open counts as the computer task.
        val fresh = VisualWorkstationDirector()
        assertEquals("computer", kind(fresh.decide(snapshot("computerWork" to JSONObject().put("consentPending", true)), 0)))
    }

    @Test fun anEndedLaneStaysForItsRetentionThenLeavesAndDoesNotComeBack() {
        val director = VisualWorkstationDirector(retainMillis = 1_000)
        director.decide(snapshot("deepClean" to clean("CLEAN")), 0)
        assertEquals("deepclean", kind(director.decide(snapshot("deepClean" to clean("DONE", 2)), 100)))
        assertEquals("deepclean", kind(director.decide(snapshot("deepClean" to clean("DONE", 2)), 1_100)))
        assertNull(director.decide(snapshot("deepClean" to clean("DONE", 2)), 1_101))
        assertNull(director.decide(snapshot("deepClean" to clean("DONE", 2)), 5_000))
        // A new run is a new record.
        assertEquals("deepclean", kind(director.decide(snapshot("deepClean" to clean("SCAN", 9)), 6_000)))
    }

    @Test fun aRunThatEndedBetweenTwoSnapshotsIsShownOnce() {
        val director = VisualWorkstationDirector(retainMillis = 1_000)
        assertEquals("deepclean", kind(director.decide(snapshot("deepClean" to clean("DONE", 4)), 0)))
        assertNull(director.decide(snapshot("deepClean" to clean("DONE", 4)), 2_000))
    }

    @Test fun mailWorksAsAnOptionalRecordAndADraftWaitsAsLive() {
        val director = VisualWorkstationDirector(retainMillis = 1_000)
        assertEquals("mail", kind(director.decide(snapshot("mailWork" to mail("DRAFT", 5)), 0)))
        assertEquals("mail", kind(director.decide(snapshot("mailWork" to mail("DRAFT", 5)), 50_000)))
        assertEquals("mail", kind(director.decide(snapshot("mailWork" to mail("FAILED", 6)), 50_001)))
        assertNull(director.decide(snapshot("mailWork" to mail("FAILED", 6)), 51_002))
    }

    @Test fun webResearchIsAWorkingLaneOnlyWhileItRunsAndFilesOnlyDuringATurn() {
        val director = VisualWorkstationDirector()
        assertEquals("web", kind(director.decide(snapshot("activity" to research(true)), 0)))
        assertNull(director.decide(snapshot("activity" to research(false, 4)), 10))
        val files = JSONObject().put("revision", 2).put("kind", "SEARCH")
        assertNull(director.decide(snapshot("files" to files, "busy" to false), 20))
        assertEquals("files", kind(director.decide(snapshot("files" to files, "busy" to true), 30)))
    }

    @Test fun theSendRecordFollowsTheLanesOwnOutcomes() {
        val send = Command03SendWork()
        assertNull(send.snapshot(session))
        send.started(session, 10)
        assertEquals("RUNNING", send.snapshot(session)!!.getString("phase"))
        assertNull(send.snapshot("abcdefab-1234-1234-1234-123456789abc"))
        send.settled(session, OwnerLaneOutcome.NeedsOwner(OwnerLaneOutcome.Need.CONFIRMATION, "Soll ich senden?"), 20)
        val waiting = send.snapshot(session)!!
        assertEquals("WAITING", waiting.getString("phase"))
        assertFalse("The lane's question is not copied", waiting.toString().contains("Soll ich"))
        send.started(session, 30)
        send.settled(session, OwnerLaneOutcome.Done(OwnerLaneResult.Delivered("WhatsApp", "Anna", null,
            OwnerLaneResult.Delivered.State.SENT)), 40)
        val sent = send.snapshot(session)!!
        assertEquals(listOf("SENT", "WhatsApp", "Anna"), listOf(sent.getString("phase"), sent.getString("channel"), sent.getString("to")))
        // Another lane's result does not change the record.
        send.settled(session, OwnerLaneOutcome.Done(OwnerLaneResult.FilesFound("a", listOf("a.pdf"))), 50)
        assertEquals("SENT", send.snapshot(session)!!.getString("phase"))
        val director = VisualWorkstationDirector()
        assertEquals("send", kind(director.decide(snapshot("sendWork" to sent), 60)))
        val lanes = VisualWorkstationDirector.lanes(snapshot("sendWork" to sent))
        assertTrue(lanes.single().kind == "send" && !lanes.single().live)
    }
}
