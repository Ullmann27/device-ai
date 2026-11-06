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
}
