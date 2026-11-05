package at.heinz.deviceai

import at.heinz.deviceai.learning.ContinuumMemoryDialogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #567 P0-12 (Fold7 Code43): „Erkläre mir in genau zwei Sätzen, warum sich eine Eisenbrücke bei Wärme ausdehnt.“
 * got „Dazu habe ich noch keine passende bestätigte Erinnerung …“ while the local model was not ready; with Gemma
 * READY the same question was answered. A general question without a model is not a memory gap.
 */
class MemoryFallbackModelNotReadyTest {
    private val gap = ContinuumMemoryDialogue.NO_EVIDENCE_ANSWER

    @Test fun withoutAModelTheAnswerNamesTheRealCause() {
        val text = ContinuumUiCopy.memoryFallbackAnswer(gap, grounded = false, modelReady = false)
        assertTrue("names the model state: $text", text.contains("Sprachmodell") && text.contains("nicht geladen"))
        assertTrue("honest that no web search ran", text.contains("nicht im Internet"))
        assertFalse("no invented knowledge", text.contains("ausdehn", ignoreCase = true))
    }

    @Test fun groundedRecallsAndOtherAnswersStayExact() {
        assertEquals(gap, ContinuumUiCopy.memoryFallbackAnswer(gap, grounded = false, modelReady = true))
        val recall = "Dazu finde ich in meinem Gedächtnis: Mein Testwort lautet Nebel 731."
        assertEquals(recall, ContinuumUiCopy.memoryFallbackAnswer(recall, grounded = true, modelReady = false))
        assertEquals("Andere Antwort", ContinuumUiCopy.memoryFallbackAnswer("Andere Antwort", grounded = false, modelReady = false))
    }

    /** Gitar review on #565: a personal question or a revoked memory keeps the „Merke dir“ guidance. */
    @Test fun theOriginalMemoryAnswerAndItsGuidanceStay() {
        val text = ContinuumUiCopy.memoryFallbackAnswer(gap, grounded = false, modelReady = false)
        assertTrue("original reply first: $text", text.startsWith(gap))
        assertTrue(text.contains("Merke dir"))
    }
}
