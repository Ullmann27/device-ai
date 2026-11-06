package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.CockpitWorkJournal.Kind
import at.heinz.deviceai.cognitive.CockpitWorkJournal.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CockpitWorkJournalTest {
    @get:Rule val dir = TemporaryFolder()

    @Test fun theReachedStateSurvivesARestart() {
        var now = 1_000L
        val first = CockpitWorkJournal(dir.root) { now }
        val e = first.begin(Kind.DOCUMENT, "Lies diese PDF und erstelle mir eine Zusammenfassung als Entwurf.",
            State.DRAFTING, "Projektübersicht.pdf", 3, 68, "content://docs/1")
        now = 5_000L
        first.update(e.id) { it.copy(state = State.DRAFTED, draft = "Kernaussagen: …", coveredChars = 68) }
        now = 9_000L
        first.update(e.id) { it.copy(draft = "Kernaussagen: … (bearbeitet)", ownerEdits = it.ownerEdits + 1) }
        val restarted = CockpitWorkJournal(dir.root) { now }.all().single()
        assertEquals(State.DRAFTED, restarted.state)
        assertEquals("Kernaussagen: … (bearbeitet)", restarted.draft)
        assertEquals(1, restarted.ownerEdits)
        assertEquals("content://docs/1", restarted.documentUri)
        assertEquals("the start time stays the start time", 1_000L, restarted.startedAt)
        assertEquals(9_000L, restarted.updatedAt)
    }

    @Test fun anAbortedOrCutOffRunIsNeverDone() {
        val aborted = CockpitWorkJournal.Episode("a", Kind.DOCUMENT, "x", 0, 0, State.ABORTED)
        assertEquals("mit STOP abgebrochen, nicht erledigt", CockpitWorkJournal.stateText(aborted, 1))
        assertEquals("neu starten oder verwerfen", CockpitWorkJournal.nextStep(aborted, 1))
        val drafting = aborted.copy(state = State.DRAFTING, updatedAt = 0)
        assertEquals("Entwurf in Arbeit", CockpitWorkJournal.stateText(drafting, 60_000))
        assertEquals("Entwurf unterbrochen, nicht fertig", CockpitWorkJournal.stateText(drafting, 11 * 60_000L))
        assertEquals("Entwurf neu starten", CockpitWorkJournal.nextStep(drafting, 11 * 60_000L))
        assertNull("saved is complete", CockpitWorkJournal.nextStep(aborted.copy(state = State.SAVED), 1))
    }

    @Test fun historyIsBoundedAndABrokenLineDoesNotLoseTheRest() {
        var now = 0L
        val journal = CockpitWorkJournal(dir.root) { now }
        repeat(45) { now = it * 1_000L; journal.begin(Kind.DOCUMENT, "Auftrag $it", State.READ) }
        val kept = journal.all()
        assertEquals(CockpitWorkJournal.MAX_EPISODES, kept.size)
        assertEquals("Auftrag 5", kept.first().request)
        val file = File(dir.root, "cockpit-work/journal-v1.jsonl")
        file.appendText("{kaputt\n")
        assertEquals("a damaged line is skipped, the others stay", CockpitWorkJournal.MAX_EPISODES, journal.all().size)
        assertTrue(journal.all().last().request == "Auftrag 44")
    }

    /**
     * Temporal honesty after a restart (owner brief 06.11.2026): a draft that was still running when the app went away
     * did not finish – in the new process it is INTERRUPTED at once, not „in Arbeit“ for another ten minutes.
     */
    @Test fun aDraftOfAnEarlierProcessIsInterruptedNotRunning() {
        var now = 1_000L
        val before = CockpitWorkJournal(dir.root) { now }
        val running = before.begin(Kind.DOCUMENT, "Fasse den Bericht zusammen", State.DRAFTING, "Bericht.pdf", 2, 40)
        now = 2_000L
        val drafted = before.begin(Kind.DOCUMENT, "Fasse die Notizen zusammen", State.DRAFTED, "Notizen.pdf", 1, 20)
        now = 61_000L
        val restarted = CockpitWorkJournal(dir.root) { now }
        assertEquals("Entwurf in Arbeit", CockpitWorkJournal.stateText(restarted.all().first { it.id == running.id }, now))
        val ownDraft = restarted.begin(Kind.DOCUMENT, "Fasse das Protokoll zusammen", State.DRAFTING, "Protokoll.pdf", 1, 10)
        assertEquals(1, restarted.recoverInterrupted(processStartedAt = 60_000L))
        assertEquals("a draft of this process keeps running", State.DRAFTING, restarted.all().first { it.id == ownDraft.id }.state)
        restarted.update(ownDraft.id) { it.copy(state = State.DRAFTED, draft = "x") }
        val cut = restarted.all().first { it.id == running.id }
        assertEquals(State.INTERRUPTED, cut.state)
        assertEquals("Entwurf unterbrochen (App wurde beendet), nicht fertig", CockpitWorkJournal.stateText(cut, now))
        assertEquals("Entwurf neu starten", CockpitWorkJournal.nextStep(cut, now))
        assertEquals("other episodes keep their state", State.DRAFTED, restarted.all().first { it.id == drafted.id }.state)
        assertEquals("nothing left to recover", 0, restarted.recoverInterrupted(processStartedAt = 60_000L))
        assertEquals("open steps, newest first", listOf("Protokoll.pdf: Entwurf prüfen und speichern",
            "Notizen.pdf: Entwurf prüfen und speichern", "Bericht.pdf: Entwurf neu starten"), restarted.openSteps(now))
    }
}
