package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

/** B2 (FABLE51 handoff): web routing follows the knowledge need, never only the network mode. */
class FreshKnowledgeNeedTest {
    private fun need(s: String) = FreshKnowledgeNeed.classify(s)

    @Test fun presentStateQuestionsNeedCurrentWeb() {
        for (q in listOf(
            "Wer ist aktuell Bundeskanzler von Österreich?",
            "Wer ist der amtierende Präsident der EZB?",
            "Wie hoch ist der Leitzins der EZB derzeit?",
            "Was kostet Bitcoin heute?",
            "Welche Android-Version ist die neueste?",
            "Wann ist die nächste Nationalratswahl 2026?",
            "Wer ist Bürgermeister von Wien?",
            "Who is the current CEO of Samsung?"
        )) {
            assertEquals(q, FreshKnowledgeNeed.Need.CURRENT_FACT, need(q))
            assertTrue(q, FreshKnowledgeNeed.needsCurrentWeb(q))
        }
    }

    @Test fun conceptualQuestionsStayLocalEvenUnderAlwaysOn() {
        for (q in listOf(
            "Warum ist der Himmel blau?",
            "Wie funktioniert ein Lithium-Ionen-Akku?",
            "Erkläre mir den Unterschied zwischen RAM und Speicher.",
            "Was bedeutet Entropie?",
            "Wie entsteht Regen?",
            "Why does ice float on water?"
        )) {
            assertEquals(q, FreshKnowledgeNeed.Need.EXPLANATION, need(q))
            assertTrue(q, FreshKnowledgeNeed.prefersLocalExplanation(q))
            assertFalse(q, FreshKnowledgeNeed.needsCurrentWeb(q))
        }
    }

    @Test fun staticKnowledgeIsNeitherForcedOnlineNorBlockedFromAlwaysOn() {
        for (q in listOf("Was ist die Hauptstadt von Burkina Faso?", "Wie viele Planeten hat das Sonnensystem?")) {
            assertEquals(q, FreshKnowledgeNeed.Need.STATIC, need(q))
            assertFalse(q, FreshKnowledgeNeed.needsCurrentWeb(q))
            assertFalse(q, FreshKnowledgeNeed.prefersLocalExplanation(q))
        }
    }

    @Test fun sameTurnProhibitionAndPrivateMarkersWinOverFreshness() {
        // Minimal pairs: identical need, opposite consent outcome.
        assertTrue(FreshKnowledgeNeed.needsCurrentWeb("Wer ist aktuell Bundeskanzler?"))
        assertFalse(FreshKnowledgeNeed.needsCurrentWeb("Ohne Internet: Wer ist aktuell Bundeskanzler?"))
        assertFalse(FreshKnowledgeNeed.needsCurrentWeb("Antworte offline: Wer ist aktuell Bundeskanzler?"))
        assertFalse(FreshKnowledgeNeed.needsCurrentWeb("Wer ist aktuell Bundeskanzler? Nutze dabei nicht das Internet."))
        // Personal data never leaves the device as a search query, however fresh the question is.
        assertFalse(FreshKnowledgeNeed.needsCurrentWeb("Wo wohnt meine Tochter aktuell?"))
        assertFalse(FreshKnowledgeNeed.needsCurrentWeb("Wie ist mein aktuelles Passwort?"))
        // Non-questions, oversized or empty input never create a web need.
        assertFalse(FreshKnowledgeNeed.needsCurrentWeb("Heute ist ein schöner Tag."))
        assertFalse(FreshKnowledgeNeed.needsCurrentWeb(""))
        assertEquals(FreshKnowledgeNeed.Need.STATIC, need("wer ist aktuell " + "x".repeat(600)))
    }

    @Test fun explanationWithFreshnessMarkerStillNeedsCurrentWeb() {
        assertEquals(FreshKnowledgeNeed.Need.CURRENT_FACT, need("Warum ist der Leitzins derzeit so hoch?"))
    }

    @Test fun trustHorizonFollowsQuestionTypeNotJustADate() {
        assertEquals(1, FreshKnowledgeNeed.horizonDays("Wie ist das Wetter in Wien?"))
        assertEquals(1, FreshKnowledgeNeed.horizonDays("Was ist der aktuelle Goldpreis?"))
        assertEquals(7, FreshKnowledgeNeed.horizonDays("Welche Android-Version ist erschienen?"))
        assertEquals(30, FreshKnowledgeNeed.horizonDays("Wer ist Bundeskanzler von Österreich?"))
        assertEquals(30, FreshKnowledgeNeed.horizonDays("Was regelt § 1151 ABGB?"))
        assertEquals(7, FreshKnowledgeNeed.horizonDays("Was ist zurzeit los in Linz?"))
        assertEquals(365, FreshKnowledgeNeed.horizonDays("Warum ist der Himmel blau?"))
        val now = 1_761_955_200_000L
        assertEquals(FreshKnowledgeNeed.Freshness.FRESH, FreshKnowledgeNeed.freshness("Warum ist der Himmel blau?", now - 200L * 86_400_000, now).status)
        assertEquals(FreshKnowledgeNeed.Freshness.POSSIBLY_STALE, FreshKnowledgeNeed.freshness("Wie ist das Wetter?", now - 2L * 86_400_000, now).status)
    }
}
