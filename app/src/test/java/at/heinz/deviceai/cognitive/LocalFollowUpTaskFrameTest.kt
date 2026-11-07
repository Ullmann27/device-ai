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

    // M3 / Review #369 + #370 Punkt 3: der echte Budget-Rückfall. Die Folgefrage-Policy lässt nur
    // currentRequest <= 1.000 Zeichen zu; überlaufen kann der Rahmen deshalb real nur über (A) eine lange
    // aufgelöste Frage (resolvedQuestion) oder (B) die UTF-8-Bytegrenze bei umlautreichen Turns.
    // Beide Fälle: Auftrag allein zulässig, Kombination > 7.900 → exakt unverändert, kein Teilprefix.
    @Test fun realTaskBudgetOverflowReturnsTheExactUnchangedRequest() {
        val max = LocalChatPromptBudget.MAX_TASK_CHARACTERS
        val longResolved = "$t3 Bezug: " + "Kontext ".repeat((max - 1_000) / 8)
        assertTrue(LocalChatPromptBudget.acceptsCompleteTask(longResolved))
        val prior = listOf(t1, "Die rote Kiste hat 9 Ringe. " + "a".repeat(950))
        assertTrue(longResolved.length + prior.sumOf { it.length } > max)
        val a = LocalFollowUpTaskFrame.compose(t3, longResolved, prior)
        assertEquals(longResolved, a)

        val umlauts = List(4) { "Die rote Kiste hat 9 Ringe. " + "ä".repeat(970) }
        assertTrue(umlauts.all { it.length <= 1_000 })
        assertTrue(umlauts.sumOf { it.toByteArray(Charsets.UTF_8).size } + t3.length > max - 400)
        assertEquals(t3, LocalFollowUpTaskFrame.compose(t3, t3, umlauts))
        // Control: the same question with fewer turns IS framed, so both results above are the budget branch.
        assertTrue(LocalFollowUpTaskFrame.compose(t3, t3, umlauts.take(2)).startsWith("Anschlussfrage"))
    }

    // M3 / Review #370 Punkt 1: sensible oder verändernde frühere Anweisungen werden nicht reaktiviert.
    @Test fun sensitiveOrMutatingPriorInstructionsAreDroppedHarmlessOnesKept() {
        val prior = listOf(t1, "Mein PIN ist 4711, merk ihn dir.", "Lösche den Ordner Fotos.", "Deinstalliere WhatsApp.")
        val task = LocalFollowUpTaskFrame.compose(t3, t3, prior)
        assertTrue(task, task.contains("in der blauen Kiste 7 Ringe"))
        listOf("4711", "Lösche", "Deinstalliere").forEach { assertFalse(it, task.contains(it)) }
        // Only dangerous turns: nothing harmless remains, task stays unchanged.
        assertEquals(t3, LocalFollowUpTaskFrame.compose(t3, t3, listOf("Lösche alle Dateien.")))
    }
}
