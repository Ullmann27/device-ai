package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class OwnerObjectStateLedgerPromptBudgetTest {
    private fun ledger() = OwnerObjectStateLedger().apply {
        observeOwnerTurn("Ich habe eine blaue Werkzeugkiste mit zwölf Schrauben in der Garage.")
        observeOwnerTurn("Nicht zwölf, sondern siebzehn; im Keller statt Garage; Farbe unverändert.")
    }

    private fun saturatedLedger() = OwnerObjectStateLedger().apply {
        val objects = listOf("Werkzeugkiste", "Reisetasche", "Vorratsdose", "Einkaufskorb", "Schublade", "Bücherregal")
        for (noun in objects) observeOwnerTurn(
            "Ich habe eine blaue $noun mit 10 Schrauben, 20 Muttern, 30 Ringen und 40 Kabeln im Keller.")
        for (turn in 1..24) for (noun in objects) {
            val color = if (turn % 2 == 0) "dunkelblau" else "dunkelgrün"
            val place = "Elektroinstallationsmateriallager" + ('A'.code + turn).toChar()
            observeOwnerTurn("Die $noun ist jetzt $color. Die $noun steht jetzt im $place. " +
                "Die $noun hat jetzt ${100 + turn} Schrauben, ${200 + turn} Muttern, " +
                "${300 + turn} Ringe und ${400 + turn} Kabel.")
        }
    }

    @Test fun ordinaryRelatedTaskFitsWithSixFullyPopulatedObjects() {
        val state = saturatedLedger()
        val request = "Nenne drei Ideen für meine Werkzeugkiste."
        assertNull(state.answer(request))
        val block = state.promptBlock(request)
        val bytes = block.toByteArray(Charsets.UTF_8).size
        println("ordinary-related-ledger-bytes=$bytes")
        assertNotNull("Mandatory frame failed at $bytes bytes", LocalPromptFrame.assemble(
            "Aktueller Auftrag: $request\n$block", "", null, "\nInternetmodus: OFF", 15_500))
        assertTrue("Current-state projection leaves prompt space: $bytes", bytes <= 3_000)
        assertTrue(block, block.contains("Anzahl Schrauben 124"))
        assertTrue(block, block.contains("Anzahl Muttern 224"))
        assertTrue(block, block.contains("Anzahl Ringen 324"))
        assertTrue(block, block.contains("Anzahl Kabeln 424"))
        assertTrue(block, block.contains("Ort im ElektroinstallationsmateriallagerY"))
        assertTrue(block, block.contains("Änderungsverläufe") && block.contains("ausgelassen"))
        assertEquals("", state.promptBlock("Erkläre mir kurz die Photosynthese."))
    }

    @Test fun compactProjectionKeepsUncertaintyAndUnresolvedCorrections() {
        val state = ledger().apply {
            observeOwnerTurn("Ich habe eine rote Tasche mit drei Schrauben im Flur.")
            observeOwnerTurn("Die Werkzeugkiste enthält vielleicht 18 Schrauben.")
            observeOwnerTurn("Nicht zwei, sondern sechs.")
        }
        val block = state.promptBlock("Nenne drei Ideen für meine Werkzeugkiste.")
        assertTrue(block, block.contains("18 (unsicher: unsicher formuliert)"))
        assertTrue(block, block.contains("Ungeklärt:"))
        assertFalse(block, block.contains("12 → 17"))
        assertTrue(block, block.contains("ausgelassen"))
    }

    @Test fun projectionDoesNotDiscardExistingHistoricalEvidence() {
        val state = ledger()
        val fullBefore = state.render()
        val original = state.answer("Wie viele Schrauben waren es ursprünglich?")
        val history = state.answer("In welcher Reihenfolge habe ich die Anzahl korrigiert?")
        repeat(4) { state.promptBlock("Nenne drei Ideen für meine Werkzeugkiste.") }
        assertEquals(fullBefore, state.render())
        assertEquals(original, state.answer("Wie viele Schrauben waren es ursprünglich?"))
        assertEquals(history, state.answer("In welcher Reihenfolge habe ich die Anzahl korrigiert?"))
        assertTrue(original, original!!.contains("12 (deine erste Angabe)"))
        val historicalTask = "Vergleiche die Anzahl ursprünglich und jetzt in meiner Werkzeugkiste."
        assertNull(state.answer(historicalTask))
        assertTrue(state.promptBlock(historicalTask).contains("12 → 17"))
        assertTrue(state.promptBlock("Erkläre den früheren Standort der Werkzeugkiste.")
            .contains("in der Garage → im Keller"))
    }

    @Test fun compactProjectionDoesNotReclassifyTruncatedHistoryAsComplete() {
        val state = saturatedLedger()
        val fullBefore = state.render()
        assertTrue(fullBefore.contains("früherer Verlauf nicht mehr vollständig"))
        state.promptBlock("Nenne drei Ideen für meine Werkzeugkiste.")
        assertEquals(fullBefore, state.render())
        assertNull(state.answer("Wie viele Schrauben waren ursprünglich in der Werkzeugkiste?"))
        val historical = state.promptBlock("Vergleiche die ursprüngliche Anzahl in der Werkzeugkiste mit jetzt.")
        assertTrue(historical, historical.contains("früherer Verlauf nicht mehr vollständig"))
    }

    @Test fun pendingCorrectionUsesItsCurrentValuesWithoutMutatingTheLedger() {
        val state = ledger()
        val block = state.promptBlock("Nicht siebzehn, sondern neunzehn; nein, neunzehn auch nicht, " +
            "es sind einundzwanzig; Farbe und Standort bleiben gleich.")
        assertTrue(block, block.contains("Farbe blau"))
        assertTrue(block, block.contains("Anzahl Schrauben 21"))
        assertTrue(block, block.contains("Ort im Keller"))
        assertFalse(block, block.contains("17 →"))
        assertTrue(block, block.contains("ausgelassen"))
        assertTrue(state.answer("Wie viele Schrauben sind darin?")!!.contains("Anzahl Schrauben: 17"))
    }
}
