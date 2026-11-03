package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class TaskScopedRingStateProjectorTest {
    private val initial =
        "In einem Beispiel liegen in der roten Kiste 4 Ringe und in der blauen Kiste 7 Ringe. Welche Kiste enthält mehr?"
    private val correction =
        "Nun hat die rote Kiste 9 Ringe statt 4. Welche Kiste enthält jetzt mehr?"
    private val historical = "Welche Kiste hatte ursprünglich mehr Ringe?"

    @Test fun carriesOriginalAndUpdatedCountsAcrossDistinctOwnerTurns() {
        val firstFollowUp = TaskScopedRingStateProjector.project(listOf(initial), correction)
        assertNotNull(firstFollowUp)
        assertEquals(4, firstFollowUp!!.red.original)
        assertEquals(9, firstFollowUp.red.current)
        assertEquals(7, firstFollowUp.blue.original)
        assertEquals(7, firstFollowUp.blue.current)

        val thirdTurn = TaskScopedRingStateProjector.project(listOf(initial, correction), historical)
        assertNotNull(thirdTurn)
        assertEquals(firstFollowUp, thirdTurn)
        assertTrue(thirdTurn!!.render().contains("ursprünglich=4, aktuell=9"))
        assertTrue(thirdTurn.render().contains("ursprünglich=7, aktuell=7"))
    }

    @Test fun futureChangeDoesNotOverwriteCurrentAndOriginal() {
        val projection = TaskScopedRingStateProjector.project(
            listOf(initial, correction), "Ab morgen hat die blaue Kiste 8 Ringe."
        )!!
        assertEquals(7, projection.blue.current)
        assertEquals(8, projection.blue.future)
        assertEquals(7, projection.blue.original)
        assertEquals(9, projection.red.current)
    }

    @Test fun noSaveInstructionDoesNotWritePersonalMemory() {
        val projected = TaskScopedRingStateProjector.project(
            listOf("Für diese Testaufgabe gilt: In der roten Kiste 4 Ringe und in der blauen Kiste 7 Ringe. Speichere diese Angaben nicht dauerhaft."),
            correction
        )
        assertNotNull(projected)
        assertTrue(projected!!.render().contains("keine Erinnerung"))
        assertTrue(projected.render().contains("nicht dauerhaft speichern"))
        // No MemoryStore, file, tool, model, app context or persistence dependency exists.
    }

    @Test fun refusesOrdinaryRealWorldClaimsOrMissingInitialFacts() {
        assertNull(TaskScopedRingStateProjector.project(
            listOf("In der roten Kiste liegen 4 Ringe und in der blauen 7 Ringe."), correction
        ))
        assertNull(TaskScopedRingStateProjector.project(emptyList(), correction))
        assertNull(TaskScopedRingStateProjector.project(listOf("Hallo"), correction))
        assertNull(TaskScopedRingStateProjector.project(
            listOf("Ein Beispiel: in der roten Kiste sind 4 Ringe."), correction
        ))
    }

    @Test fun mixedOrNegatedFutureTimelineAbstainsInsteadOfInventingStates() {
        assertNull(TaskScopedRingStateProjector.project(
            listOf(initial), "Ab morgen hat die rote Kiste 9 Ringe, aber heute die blaue Kiste 7 Ringe."
        ))
        assertNull(TaskScopedRingStateProjector.project(
            listOf(initial), "Nicht ab morgen hat die blaue Kiste 8 Ringe."
        ))
    }

    @Test fun secondHypotheticalScenarioNeverInheritsFirstScenarioValues() {
        val second = "Neues Beispiel: In der roten Kiste 2 Ringe, in der blauen Kiste 3 Ringe."
        val changed = "Nun hat die rote Kiste 4 Ringe statt 2."
        val result = TaskScopedRingStateProjector.project(listOf(initial, second), changed)
        assertNotNull(result)
        assertEquals(2, result!!.red.original)
        assertEquals(4, result.red.current)
        assertEquals(3, result.blue.original)
        assertEquals(3, result.blue.current)

        assertNull(TaskScopedRingStateProjector.project(
            listOf(initial), "Neues Beispiel: Nun hat die rote Kiste 9 Ringe."
        ))
    }

    @Test fun refusesIncompleteOrOversizedOwnerEvidence() {
        assertNull(TaskScopedRingStateProjector.project(listOf(initial + "x".repeat(1000)), correction))
        assertNull(TaskScopedRingStateProjector.project(listOf(initial), correction + "x".repeat(1000)))
        assertNull(TaskScopedRingStateProjector.project(List(5) { initial }, correction))
    }

    @Test fun negatedOrAmbiguousQuantitiesNeverBecomeTimeSlots() {
        val ambiguous = "In einer Testaufgabe sind in der roten Kiste vielleicht 4 Ringe und in der blauen Kiste 7 Ringe."
        assertNull(TaskScopedRingStateProjector.project(listOf(ambiguous), correction))
        val negated = "Im Beispiel liegen in der roten Kiste nicht 4 sondern 5 Ringe und in der blauen Kiste 7 Ringe."
        assertNull(TaskScopedRingStateProjector.project(listOf(negated), correction))
    }
}
