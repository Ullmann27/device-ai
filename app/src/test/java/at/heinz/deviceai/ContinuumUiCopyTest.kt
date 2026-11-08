package at.heinz.deviceai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumUiCopyTest {
    @Test fun corruptDecoderIsNotMisdiagnosedAsMissingOrSentToTraining() {
        val corrupt = ContinuumUiCopy.cdxUnavailableStatus("CORRUPT")
        assertTrue(corrupt.contains("beschädigt"))
        assertFalse(corrupt.contains("Paarlabor"))
        assertFalse(corrupt.contains("noch keinen"))
        assertTrue(ContinuumUiCopy.cdxUnavailableStatus("NO_CHECKPOINT").contains("Paarlabor"))
        assertTrue(ContinuumUiCopy.cdxUnavailableStatus("RECOVERED_LKG").contains("Sicherung"))
        assertFalse(ContinuumUiCopy.cdxUnavailableStatus("UNKNOWN").contains("Paarlabor"))
    }
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

    @Test fun retainedModelResourcesRequireExplicitUnloadBeforeRetry() {
        val message = ContinuumUiCopy.modelFailure("LOCAL_MODEL_UNLOAD_REQUIRED")
        assertTrue(message.contains("Entlade"))
        assertTrue(message.contains("Modellverwaltung"))
        assertTrue(message.contains("erneut"))
        assertFalse(message.contains("Dateiformat"))
        assertFalse(message.contains("bereits entladen"))
    }

    @Test fun oversizedModelResponseExplainsOutputBudgetAndRequestsShorterAnswer() {
        val message = ContinuumUiCopy.modelFailure("LOCAL_MODEL_RESPONSE_EXCEEDS_AGENT_LIMIT")
        assertTrue(message.contains("Ausgabelimit"))
        assertTrue(message.contains("kürzere Antwort"))
        assertFalse(message.contains("Dateiformat"))
        assertFalse(message.contains("zu lange gedauert"))
    }

    @Test fun interruptionsAndPermissionsExplainNextAction() {
        assertTrue(ContinuumUiCopy.researchFailure("cancelled").contains("beendet"))
        assertTrue(ContinuumUiCopy.researchFailure(null).contains("erneut"))
        assertTrue(ContinuumUiCopy.deviceBridgeMissing().contains("Shizuku"))
        assertTrue(ContinuumUiCopy.developerModelMissing().contains("Cloud-Ersatz"))
        assertTrue(ContinuumUiCopy.modelStatus(false).contains("noch nicht geladen"))
    }

    @Test fun pendingToolReviewFooterSpeaksGermanAndCountsHonestly() {
        // Fold7 IST 2026-11-08 showed the raw English "1 tool call(s) require operator review" under the cockpit.
        val one = ContinuumUiCopy.pendingToolReview(1)
        val many = ContinuumUiCopy.pendingToolReview(3)
        assertTrue(one, one.startsWith("1 Werkzeugaufruf wartet"))
        assertTrue(many, many.startsWith("3 Werkzeugaufrufe warten"))
        assertFalse(one.contains("tool call"))
        assertTrue(one.contains("Freigabe"))
    }
}
