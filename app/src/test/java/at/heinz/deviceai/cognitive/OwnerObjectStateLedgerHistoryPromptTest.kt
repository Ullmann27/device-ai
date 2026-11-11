package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerObjectStateLedgerHistoryPromptTest {
    private fun ledger() = OwnerObjectStateLedger().apply {
        observeOwnerTurn("Ich habe eine blaue Werkzeugkiste mit zwölf Schrauben in der Garage.")
        observeOwnerTurn("Nicht zwölf, sondern siebzehn; im Keller statt Garage; Farbe unverändert.")
        observeOwnerTurn("Nicht siebzehn, sondern einundzwanzig; Farbe und Standort bleiben gleich.")
    }

    private fun assertHistory(request: String) {
        val state = ledger()
        val before = state.render()
        val block = state.promptBlock(request)
        assertTrue("$request\n$block", block.contains("12 → 17 → 21"))
        assertTrue("$request\n$block", block.contains("in der Garage → im Keller"))
        assertFalse("$request\n$block", block.contains("für diese Anfrage ausgelassen"))
        assertEquals("Projection mutated evidence: $request", before, state.render())
    }

    @Test fun correctionSummaryKeepsHistoricalEvidence() {
        val request = "Fasse meine Korrekturen an der Werkzeugkiste zusammen."
        assertNull(ledger().answer(request))
        assertHistory(request)
    }

    @Test fun questionAboutPastChangesKeepsHistoricalEvidence() {
        val request = "Was hat sich an der Werkzeugkiste geändert?"
        assertNull(ledger().answer(request))
        assertHistory(request)
    }

    @Test fun recentPastQuestionKeepsHistoricalEvidenceWithoutGuessingAPreviousValue() {
        val request = "Wie viele Schrauben waren es vorhin?"
        assertNull(ledger().answer(request))
        assertHistory(request)
    }

    @Test fun inflectedHistoryNounsKeepHistoricalEvidence() {
        for (request in listOf(
            "Erkläre die Schritte des Verlaufs meiner Angaben zur Werkzeugkiste.",
            "Fasse die Stationen des Verlaufes meiner Angaben zur Werkzeugkiste zusammen.",
            "Beschreibe die Verläufe meiner Angaben zur Werkzeugkiste."
        )) assertHistory(request)
    }

    @Test fun explicitChangeAndCorrectionRecallKeepsHistoricalEvidence() {
        for (request in listOf(
            "Welche Änderungen gab es an der Werkzeugkiste?",
            "Beschreibe die Veränderungen an der Werkzeugkiste.",
            "Nenne meine bisherigen Korrekturen an der Werkzeugkiste.",
            "Was habe ich an der Werkzeugkiste korrigiert?",
            "Was wurde an der Werkzeugkiste geändert?",
            "Wie hat sich die Anzahl der Schrauben verändert?",
            "Erkläre den alten Standort der Werkzeugkiste."
        )) assertHistory(request)
    }

    @Test fun previouslySupportedZuvorAndCurrentRecallStayUnchanged() {
        val state = ledger()
        val previous = "Wie viele Schrauben waren es zuvor?"
        assertTrue(state.answer(previous)!!.contains("17 (vor der aktuellen Angabe 21)"))
        assertHistory(previous)
        assertTrue(state.answer("Wie viele Schrauben sind nach allen Korrekturen darin?")!!
            .contains("Anzahl Schrauben: 21"))
    }

    @Test fun planningAndCurrentStateTasksStayCompact() {
        val state = ledger()
        val before = state.render()
        for (request in listOf(
            "Nenne drei Ideen für meine Werkzeugkiste.",
            "Nenne drei Ideen für Änderungen an meiner Werkzeugkiste.",
            "Fasse die geplanten Korrekturen an der Werkzeugkiste zusammen.",
            "Welche Änderungen könnte ich an der Werkzeugkiste vornehmen?",
            "Was soll an der Werkzeugkiste geändert werden?",
            "Beschreibe mögliche Veränderungen an der Werkzeugkiste.",
            "Erkläre, wie ich die Farbe der Werkzeugkiste ändern kann.",
            "Nenne die aktuellen Werte nach meinen Korrekturen an der Werkzeugkiste.",
            "Fasse die aktuellen Angaben zur Werkzeugkiste zusammen.",
            "Nenne drei Ideen für meine alte Werkzeugkiste.",
            "Korrigiere die Anzahl in der Werkzeugkiste auf 22 Schrauben."
        )) {
            val block = state.promptBlock(request)
            assertTrue("$request\n$block", block.contains("für diese Anfrage ausgelassen"))
            assertFalse("$request\n$block", block.contains("12 → 17"))
            assertEquals("Projection mutated evidence: $request", before, state.render())
        }
    }

    @Test fun unrelatedChangeRequestsDoNotPullObjectEvidence() {
        val state = ledger()
        for (request in listOf(
            "Was hat sich am Wetter geändert?",
            "Fasse die Änderungen am Stundenplan zusammen.",
            "Erkläre die Geschichte der Photosynthese."
        )) assertEquals(request, "", state.promptBlock(request))
    }

    @Test fun historicalProjectionPreservesUncertaintyAndUnresolvedReferences() {
        val state = ledger().apply {
            observeOwnerTurn("Ich habe eine rote Tasche mit drei Schrauben im Flur.")
            observeOwnerTurn("Die Werkzeugkiste enthält vielleicht 22 Schrauben.")
            observeOwnerTurn("Nicht zwei, sondern sechs.")
        }
        val before = state.render()
        val block = state.promptBlock("Fasse meine Korrekturen an der Werkzeugkiste zusammen.")
        assertTrue(block, block.contains("12 → 17 → 21 → 22"))
        assertTrue(block, block.contains("22 (unsicher: unsicher formuliert)"))
        assertTrue(block, block.contains("Ungeklärt:"))
        assertEquals(before, state.render())
    }

    @Test fun historicalProjectionKeepsTruncationExplicit() {
        val state = ledger().apply {
            repeat(30) { observeOwnerTurn("Die Werkzeugkiste hat jetzt ${30 + it} Schrauben.") }
        }
        val before = state.render()
        val block = state.promptBlock("Was hat sich an der Werkzeugkiste geändert?")
        assertTrue(block, block.contains("früherer Verlauf nicht mehr vollständig"))
        assertFalse(block, block.contains("für diese Anfrage ausgelassen"))
        assertNull(state.answer("Wie viele Schrauben waren ursprünglich in der Werkzeugkiste?"))
        assertEquals(before, state.render())
    }
}
