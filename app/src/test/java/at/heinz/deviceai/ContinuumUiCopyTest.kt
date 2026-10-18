package at.heinz.deviceai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumUiCopyTest {
    @Test fun showsPlainLanguageMissingPrerequisites() {
        val state = ContinuumUiCopy.developmentRequirements(
            modelReady = false, hasGoal = false, sources = 0
        )
        assertTrue(state.contains("Sprachmodell fehlt"))
        assertTrue(state.contains("Beschreibe"))
        assertTrue(state.contains("Quelldatei"))
        assertFalse(state.contains("READY"))
        assertFalse(state.contains("Developer, Critic"))
    }

    @Test fun showsReadinessWithoutClaimingAutomaticExecution() {
        val state = ContinuumUiCopy.developmentRequirements(true, true, 2)
        assertTrue(state.contains("KI-Sprachmodell ist bereit"))
        assertTrue(state.contains("2 Quelldatei(en)"))
        assertTrue(state.contains("weder Builds noch automatische Codeänderungen"))
    }

    @Test fun sourceErrorsStayUnderstandableAndUnverified() {
        assertTrue(ContinuumUiCopy.emptyResearch("Research response is too large")
            .contains("keine überprüfbaren Inhalte"))
        assertTrue(ContinuumUiCopy.emptyResearch("Research total deadline exceeded")
            .contains("zu lange"))
        assertTrue(ContinuumUiCopy.emptyResearch("general web discovery is not configured")
            .contains("Suchdienst"))
        assertTrue(ContinuumUiCopy.emptyResearch(null).contains("keine verlässlichen Quellen"))
    }

    @Test fun commonModelErrorsArePlainGermanAndSuggestNextAction() {
        assertTrue(ContinuumUiCopy.modelFailure("SHA-256 mismatch").contains("Prüfwert"))
        assertTrue(ContinuumUiCopy.modelFailure("unsupported backend").contains("Modellformat"))
        assertTrue(ContinuumUiCopy.modelFailure("out of memory").contains("Arbeitsspeicher"))
        assertTrue(ContinuumUiCopy.modelFailure("initialization exceeded deadline").contains("zu lange"))
        assertTrue(ContinuumUiCopy.modelFailure("busy").contains("Warte"))
        assertTrue(ContinuumUiCopy.modelFailure(null).contains("Dateiformat"))
    }

    @Test fun interruptionsAndPermissionsExplainNextAction() {
        assertTrue(ContinuumUiCopy.researchFailure("cancelled").contains("beendet"))
        assertTrue(ContinuumUiCopy.researchFailure(null).contains("erneut"))
        assertTrue(ContinuumUiCopy.deviceBridgeMissing().contains("Shizuku"))
        assertTrue(ContinuumUiCopy.developerModelMissing().contains("Cloud-Ersatz"))
        assertTrue(ContinuumUiCopy.modelStatus(false).contains("noch nicht geladen"))
    }
}
