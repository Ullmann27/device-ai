package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class LocalContextIntegrityProbeTest {
    private val previous = listOf(
        "Die rote Kiste enthält 4 Ringe, die blaue 7."
    )
    private val rendered = "\nGESPRÄCHSDATEN — UNBESTÄTIGT:\nNutzer: ${previous[0]}\n"
    private val current = "Nun hat die rote Kiste 9 statt 4 Ringe."

    @Test fun detectsExactCompleteHistoryBeforeNativeDispatch() {
        val prompt = "Aktueller Auftrag: $current$rendered\nKeine Werkzeuge."
        val result = LocalContextIntegrityProbe.inspect(true, previous, rendered, prompt)
        assertEquals(LocalContextIntegrityProbe.Status.HISTORY_IN_PROMPT, result.status)
        assertTrue(result.requiredHistoryAdmitted)
        assertEquals(1, result.previousOwnerTurns)
        assertEquals(rendered.toByteArray(Charsets.UTF_8).size, result.renderedHistoryBytes)
        assertEquals(prompt.toByteArray(Charsets.UTF_8).size, result.promptBytes)
        assertFalse(result.diagnosticLine().contains("Kiste"))
        assertFalse(result.diagnosticLine().contains("blaue"))
        assertFalse(result.diagnosticLine().contains("Ringe"))
    }

    @Test fun missingAndIncompletePreviousOwnerEvidenceFailClosed() {
        val first = LocalContextIntegrityProbe.inspect(true, emptyList(), rendered, current)
        assertEquals(LocalContextIntegrityProbe.Status.NO_PRIOR_OWNER_TURN, first.status)
        assertFalse(first.requiredHistoryAdmitted)
        val incomplete = LocalContextIntegrityProbe.inspect(true, null, rendered, current)
        assertEquals(LocalContextIntegrityProbe.Status.OWNER_EVIDENCE_INCOMPLETE, incomplete.status)
        assertFalse(incomplete.requiredHistoryAdmitted)
        val lost = LocalContextIntegrityProbe.inspect(true, previous, "", current)
        assertEquals(LocalContextIntegrityProbe.Status.RENDERED_HISTORY_UNAVAILABLE, lost.status)
        assertFalse(lost.requiredHistoryAdmitted)
    }

    @Test fun detectsOmittedHistoryEvenWhenPromptHasSimilarNumbers() {
        val noHistoryPrompt = "Aktueller Auftrag: $current \nRot 9, blau 7."
        val result = LocalContextIntegrityProbe.inspect(true, previous, rendered, noHistoryPrompt)
        assertEquals(LocalContextIntegrityProbe.Status.REQUIRED_HISTORY_NOT_IN_PROMPT, result.status)
        assertFalse(result.requiredHistoryAdmitted)
    }

    @Test fun optionalHistoryMayBeOmittedWithoutClaimingModelSuccess() {
        val noHistory = LocalContextIntegrityProbe.inspect(false, emptyList(), "", current)
        assertEquals(LocalContextIntegrityProbe.Status.NO_OPTIONAL_HISTORY, noHistory.status)
        assertTrue(noHistory.requiredHistoryAdmitted)
        val dropped = LocalContextIntegrityProbe.inspect(false, previous, rendered, current)
        assertEquals(LocalContextIntegrityProbe.Status.OPTIONAL_HISTORY_NOT_IN_PROMPT, dropped.status)
        assertTrue(dropped.requiredHistoryAdmitted)
        assertTrue(dropped.diagnosticLine().contains("Modellverständnis nicht geprüft"))
    }

    @Test fun unavailableFinalPromptIsDifferentFromAbsentOwnerHistory() {
        val receipt = LocalContextIntegrityProbe.inspect(true, previous, rendered, null)
        assertEquals(LocalContextIntegrityProbe.Status.PROMPT_UNAVAILABLE, receipt.status)
        assertEquals(0, receipt.promptBytes)
        assertFalse(receipt.requiredHistoryAdmitted)
    }
}
