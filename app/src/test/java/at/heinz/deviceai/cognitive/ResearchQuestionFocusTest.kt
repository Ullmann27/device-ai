package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchQuestionFocusTest {
    private val risQuery =
        "Suche online im österreichischen RIS die aktuelle Fassung von § 1295 ABGB. " +
            "Nenne die konkrete Quelle und den Link."

    @Test fun imperativeWebSearchesUseTopicalTermsInsteadOfSearchOperatorWords() {
        assertEquals("österreichischen ris 1295 abgb", ResearchQuestionFocus.searchText(risQuery))
    }

    @Test fun rejectsSearchHomepagesAsEvidenceEvenWithRelevantSearchInstructions() {
        assertFalse(ResearchQuestionFocus.titleMatches(risQuery, "Google Bilder Anmelden"))
        assertFalse(ResearchQuestionFocus.titleMatches(risQuery, "Yahoo Suche Websuche Suchmaschine"))
        assertTrue(ResearchQuestionFocus.titleMatches(risQuery,
            "RIS Allgemeines bürgerliches Gesetzbuch § 1295"))
    }

    @Test fun ordinaryWebTopicsAndTitlesKeepWorking() {
        assertEquals("solarzellen", ResearchQuestionFocus.searchText("Wie funktionieren Solarzellen?"))
        assertTrue(ResearchQuestionFocus.titleMatches("Wie funktionieren Solarzellen?",
            "Solarzellen und photovoltaischer Effekt"))
        assertFalse(ResearchQuestionFocus.titleMatches("Wie funktionieren Solarzellen?",
            "Facebook Anmeldung"))
    }
}
