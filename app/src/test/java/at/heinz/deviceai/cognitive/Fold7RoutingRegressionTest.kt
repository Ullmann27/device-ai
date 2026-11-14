package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerComputerIntentParser.Result
import org.junit.Assert.*
import org.junit.Test

class Fold7RoutingRegressionTest {
    @Test fun spokenSingularDownloadUsesApprovedComputerPlan() {
        for (request in listOf("öffne Download", "Öffne Downloads", "Öffne den Download-Ordner")) {
            val result = OwnerComputerIntentParser.parse(request) as Result.Ready
            assertEquals(ComputerApp.FILES, result.intent.app)
            assertEquals(ComputerGoal.OPEN_DOWNLOADS, result.intent.goal)
            assertTrue(result.intent.steps.contains(ComputerPlanStep.VERIFY_FOLDER))
        }
        assertNull(OwnerAppLookupIntent.inspect("öffne Download"))
        assertTrue(OwnerComputerIntentParser.parse("Öffne nicht Download") !is Result.Ready)
    }

    @Test fun informalJusticeResearchRemovesSpokenFillerWithoutLosingSubject() {
        for (request in listOf(
            "Recherchiere zu denn Thema Justiz Österreich",
            "Recherchiere zu dem Thema Justiz Österreich",
            "Recherchiere zum Thema Justiz Österreich"
        )) {
            val result = OwnerResearchCommand.parse(request) as OwnerResearchCommand.Outcome.Research
            assertEquals(request, "Justiz Österreich", result.topic)
        }
    }

    @Test fun funeralAndUnrelatedWebClaimsCannotBecomeJusticeEvidence() {
        val question = "Recherchiere zu denn Thema Justiz Österreich"
        assertEquals("justiz österreich", ResearchQuestionFocus.searchText(question))
        assertFalse(ResearchQuestionFocus.titleMatches(question,
            "Sehen Sie hier ein paar Beispiele zu Sterbefällen und Bestattung"))
        assertFalse(ResearchQuestionFocus.sourceTitleMatches("Justiz Österreich",
            "Aktuelle Sterbefälle – Bestattung Wimmer aus Weng"))
        assertTrue(ResearchQuestionFocus.sourceTitleMatches("Justiz Österreich",
            "Justiz in Österreich – Bundesministerium"))
    }
}
