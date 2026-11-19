package at.heinz.deviceai.deepclean

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerMissionDirector
import at.heinz.deviceai.OwnerMissionHost
import at.heinz.deviceai.OwnerMissionLane
import at.heinz.deviceai.cognitive.ChainPlan
import at.heinz.deviceai.cognitive.OwnerDeliverable
import at.heinz.deviceai.cognitive.OwnerMissionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * „Prüf zuerst meinen Speicher, bereinige die sicheren temporären Dateien und sag mir danach, wie viel Platz ich wieder
 * habe“ is ONE order: the mission layer plans three storage steps, hands each to the storage lane and continues on the
 * lane's typed outcome; the closing report only repeats measured figures.
 */
class DeepCleanMissionTest {
    private val sentence = "Prüf zuerst meinen Speicher, bereinige die sicheren temporären Dateien und sag mir danach, wie viel Platz ich wieder habe"

    @Test fun theSentenceIsPlannedAsThreeStorageSteps() {
        assertEquals(listOf("STORAGE:ANALYSE", "STORAGE:SAFE_CLEAN", "STORAGE:REPORT"), OwnerMissionPlanner.plan(sentence, "Jarvis")?.codes)
        // The lane itself does not take the whole sentence: two orders are a mission.
        assertNull(DeepCleanCommands.parse(sentence, "Jarvis"))
        // A restriction keeps a sentence out of a plan; the lane takes it whole and narrows it.
        assertNull(OwnerMissionPlanner.plan("Bereinige mein Handy, aber lass die Fotos in Ruhe", "Jarvis"))
        assertNull(OwnerMissionPlanner.plan("Mach eine Tiefenbereinigung und sag mir danach, wie viel Platz ich wieder habe", "Jarvis"))
    }

    private class Host(val clean: FakeDeepCleanHost) : OwnerMissionHost {
        lateinit var mission: OwnerMissionDirector
        lateinit var lane: DeepCleanDirector
        val said = mutableListOf<String>()
        private val queue = ArrayDeque<() -> Unit>()
        override val sessionId: String get() = clean.sessionId
        override val assistantName: String? = "Jarvis"
        override fun nowMillis(): Long = clean.nowMillis()
        override fun acceptOwnerTurn(request: String) { clean.turns += request }
        override fun reply(request: String, text: String) { said += text }
        override fun say(text: String) { said += text }
        override fun activity(line: String) {}
        override fun idle(): Boolean = !lane.busy
        override fun later(action: () -> Unit) { queue += action }
        override fun desk(order: String): Boolean = false
        override fun deskOpenFound(order: String, name: String, folderHint: String?) {}
        override fun deskIntercept(request: String): Boolean = false
        override fun deskCorrect(request: String): Boolean = false
        override fun openDocumentName(): String? = null
        override fun work(order: String): Boolean = false
        override fun continueOn(result: OwnerDeliverable): Boolean = false
        override fun chain(order: String, plan: ChainPlan): Boolean = false
        override fun cancelRunning() {}
        override fun deepClean(order: String): Boolean = lane.handle(order)
        fun settle() { while (queue.isNotEmpty()) queue.removeFirst().invoke() }
    }

    /** The lane's host reports to the mission and shows a handed-over step only once (as the Android adapter does). */
    private class LaneHost(val inner: FakeDeepCleanHost, val mission: () -> OwnerMissionDirector) : DeepCleanHost by inner {
        override fun acceptOwnerTurn(request: String) { if (!mission().laneTurn(OwnerMissionLane.DEEPCLEAN)) inner.acceptOwnerTurn(request) }
        override fun outcome(outcome: OwnerLaneOutcome) { inner.outcome(outcome); mission().laneOutcome(OwnerMissionLane.DEEPCLEAN, outcome) }
    }

    @Test fun theMissionRunsAllThreeStepsOnTheLanesTypedOutcomes() {
        val clean = FakeDeepCleanHost()
        clean.phone.file(clean.phone.cache, "previews/a.png", 5_000)
        clean.phone.file(clean.phone.files, "journal/journal-1234567890.tmp", 2_000)
        clean.phone.file(clean.phone.shared, "DCIM/Camera/IMG_1.jpg", 9_000)
        val host = Host(clean)
        val mission = OwnerMissionDirector(host)
        host.mission = mission
        host.lane = DeepCleanDirector(LaneHost(clean) { mission })
        assertTrue(mission.handle(sentence))
        host.settle()
        // Three answers of the lane: the check (nothing deleted), the clean-up, the measured report.
        assertEquals(3, clean.answers.size)
        assertTrue(clean.answers[0], clean.answers[0].contains("geprüft und nichts gelöscht"))
        assertTrue(clean.answers[1], clean.answers[1].contains("Entfernt habe ich 2 Dateien, zusammen " + DeepCleanText.bytes(7_000)))
        assertTrue(clean.answers[2], clean.answers[2].contains("entfernt habe ich 2 Dateien"))
        assertEquals(2, clean.counting.deleteCalls)
        val report = host.said.last()
        assertTrue(report, report.startsWith("Fertig – alle drei Schritte sind erledigt."))
        assertTrue(report, report.contains("2 Dateien entfernt (" + DeepCleanText.bytes(7_000) + " Dateigröße)"))
        assertTrue(clean.phone.shared.resolve("DCIM/Camera/IMG_1.jpg").exists())
        // The owner's sentence was shown once; the handed-over steps were not shown as owner turns.
        assertEquals(listOf(sentence), clean.turns)
    }
}
