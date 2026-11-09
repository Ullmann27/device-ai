package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import at.heinz.deviceai.localmodel.LocalModelThermalGuard
import org.junit.Assert.*
import org.junit.Test

/** Failed native text cannot become a successful or falsely diagnosed owner turn. */
class LocalGenerationFailureTruthTest {
    private fun runPartially(failure: String?) = BoundedAgentOrchestrator(
        model = AgentModel { _, _, _, _ ->
            AgentModelOutput.ownerDialogue("local-test", "Teilweise Antwort, noch nicht fertig.",
                LocalGenerationCompletion.FAILED, failure)
        },
        memories = { emptyList() },
        toolGate = ToolGate(ToolPolicy(), ToolRunner { error("No tool execution expected") }),
        evaluator = AgentEvaluator { _, _ -> Evaluation(true, 0.6) },
        experienceStore = InMemoryExperienceStore(),
        evaluationConfirmsOutcome = false
    ).run("Was kann die App?")

    @Test fun trueHeatStopHasSpecificReasonAndNoVerifiedOutcome() {
        val result = runPartially(LocalModelThermalGuard.STOPPED_THERMAL)
        assertFalse(result.responseComplete)
        assertTrue(result.blocker.orEmpty().contains("Android meldet starke Gerätewärme"))
        assertTrue(result.response.contains("Teilweise Antwort"))
        assertNull(result.experience)
        assertFalse(result.taskEvidence!!.outcomeVerified)
    }

    @Test fun arbitraryNativeFailureDoesNotInventThermalCause() {
        val result = runPartially("NATIVE_RUNTIME_FAILURE")
        assertFalse(result.responseComplete)
        assertTrue(result.blocker.orEmpty().contains("Modelllaufzeit meldete einen Fehler"))
        assertFalse(result.blocker.orEmpty().contains("Gerätewärme"))
        assertNull(result.experience)
    }

    @Test fun missingErrorReasonIsReportedAsUnknown() {
        val result = runPartially(null)
        assertFalse(result.responseComplete)
        assertTrue(result.blocker.orEmpty().contains("Ursache ist nicht bestätigt"))
        assertFalse(result.blocker.orEmpty().contains("Gerätewärme"))
    }

    @Test fun preciseNonThermalTerminalReasonsStayDistinct() {
        assertEquals(LocalGenerationFailureCause.THERMAL_PAUSED,
            LocalGenerationFailureCause.classify(LocalGenerationCompletion.FAILED, LocalModelThermalGuard.PAUSED_THERMAL))
        assertEquals(LocalGenerationFailureCause.CONTEXT_UNVERIFIED,
            LocalGenerationFailureCause.classify(LocalGenerationCompletion.FAILED, "LOCAL_MODEL_CONTEXT_UNVERIFIED_OR_FULL"))
        assertEquals(LocalGenerationFailureCause.STALLED,
            LocalGenerationFailureCause.classify(LocalGenerationCompletion.STALLED, null))
        assertEquals(LocalGenerationFailureCause.OUTPUT_LIMIT,
            LocalGenerationFailureCause.classify(LocalGenerationCompletion.OUTPUT_LIMIT, null))
        assertEquals(LocalGenerationFailureCause.UNKNOWN,
            LocalGenerationFailureCause.classify(LocalGenerationCompletion.FAILED, null))
    }

    @Test fun completeGenerationNeverCarriesFailureCause() {
        val output = AgentModelOutput.ownerDialogue("local-test", "Vollständig.", LocalGenerationCompletion.COMPLETE,
            LocalModelThermalGuard.STOPPED_THERMAL)
        assertTrue(output.responseComplete)
        assertNull(output.failureCause)
    }
}
