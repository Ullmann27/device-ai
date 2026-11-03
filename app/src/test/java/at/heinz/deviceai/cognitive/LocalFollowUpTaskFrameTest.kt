package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner-Feldtest Fold7 0.3.38/Code39 (Argon-4-Dossier 4.2 / 15.1, T1–T3). */
class LocalFollowUpTaskFrameTest {
    private val t1 = "In einem Beispiel liegen in der roten Kiste 4 Ringe und in der blauen Kiste 7 Ringe. " +
        "Welche Kiste enthält mehr? Bitte nur Farbe und Anzahl nennen."
    private val t2 = "Nun hat die rote Kiste 9 Ringe statt 4. Welche Kiste enthält jetzt mehr? Bitte nur Farbe und Anzahl nennen."
    private val t3 = "Welche Kiste hatte ursprünglich mehr Ringe, bevor sich die Anzahl verändert hat?"

    @Test fun t2CorrectionTaskCarriesTheEarlierBlueSevenAsTaskData() {
        val task = LocalFollowUpTaskFrame.compose(t2, t2, listOf(t1))
        assertTrue(task, task.contains("in der blauen Kiste 7 Ringe"))
        // The newest message stays complete and comes last: later statements win.
        assertTrue(task.endsWith(t2))
        assertTrue(task.indexOf(t1) < task.indexOf(t2))
    }

    @Test fun t3HistoricalQuestionKeepsChronologicalOrderOfAllOwnerTurns() {
        val task = LocalFollowUpTaskFrame.compose(t3, t3, listOf(t1, t2))
        val i1 = task.indexOf(t1); val i2 = task.indexOf(t2); val i3 = task.indexOf(t3)
        assertTrue(task, i1 in 0 until i2 && i2 < i3)
        assertTrue(task.contains("1."))
        assertTrue(task.contains("2."))
    }

    @Test fun selfContainedOrNonFollowUpTurnIsUnchanged() {
        assertEquals(t1, LocalFollowUpTaskFrame.compose(t1, t1, listOf("Hallo")))
        assertEquals("Was bedeutet vorher?", LocalFollowUpTaskFrame.compose(
            "Was bedeutet vorher?", "Was bedeutet vorher?", listOf(t1)))
    }

    @Test fun missingOrIncompleteSessionEvidenceNeverInventsHistory() {
        assertEquals(t2, LocalFollowUpTaskFrame.compose(t2, t2, null))
        assertEquals(t2, LocalFollowUpTaskFrame.compose(t2, t2, emptyList()))
    }

    @Test fun oversizedFrameFallsBackToUnchangedTaskInsteadOfClipping() {
        val huge = List(4) { "Beispiel " + "x".repeat(990) }
        val task = LocalFollowUpTaskFrame.compose(t2, t2, huge + listOf("y".repeat(990)))
        // > 4 turns or a frame above the task budget: unchanged, no prefix of old data.
        assertEquals(t2, task)
        val big = List(4) { "z".repeat(1_000) }
        val framed = LocalFollowUpTaskFrame.compose(t2, t2, big)
        assertTrue(framed.length <= LocalChatPromptBudget.MAX_TASK_CHARACTERS)
    }

    @Test fun frameMarksOwnerTurnsAsEphemeralTaskDataNotMemoryOrPermission() {
        val task = LocalFollowUpTaskFrame.compose(t2, t2, listOf(t1))
        assertTrue(task.contains("nicht dauerhaft speichern"))
        assertTrue(task.contains("keine Freigabe"))
        assertFalse(task.contains("Modell:"))
    }

    @Test fun identicalDuplicateOfCurrentTurnIsNotRepeated() {
        val task = LocalFollowUpTaskFrame.compose(t2, t2, listOf(t1, t2))
        assertEquals(1, Regex(Regex.escape("Nun hat die rote Kiste 9")).findAll(task).count())
    }
}
