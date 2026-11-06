package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolProposal
import at.heinz.deviceai.ToolRunner
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class OwnerDialogueOutputTest {
    @Test fun completeOwnerResponsePassesTheSameQualityGateWithoutLosingItsEnd() {
        for (size in listOf(8_000, 8_001, 20_000)) {
            val suffix = " NICHT AUSFÜHREN. Ö."
            val answer = "x".repeat(size - suffix.length) + suffix
            val fixture = Fixture { AgentModelOutput.ownerDialogue("fixture-model", answer) }
            val run = fixture.agent.run("Erkläre den Zusammenhang.")
            assertEquals(answer, run.response)
            assertTrue(run.responseComplete)
            assertNull(run.blocker)
            assertEquals(1, fixture.evaluations)
            assertEquals(answer, fixture.evaluatedText)
            assertEquals(0, fixture.toolRuns)
        }
    }

    @Test fun defaultPlainOutputRetainsItsEightThousandLimit() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            AgentModelOutput("fixture-model", "x".repeat(8_001))
        }
        assertEquals("LOCAL_MODEL_RESPONSE_EXCEEDS_AGENT_LIMIT", error.message)
    }

    @Test fun ownerFactoryRejectsUncapturedOverflowInsteadOfClipping() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentModelOutput.ownerDialogue("fixture-model", "x".repeat(20_001))
        }
    }

    @Test fun incompleteDraftIsEvaluatedButCannotBecomeSuccessfulLearning() {
        for (completion in listOf(LocalGenerationCompletion.OUTPUT_LIMIT,
            LocalGenerationCompletion.STALLED, LocalGenerationCompletion.FAILED)) {
            val fixture = Fixture {
                AgentModelOutput.ownerDialogue("fixture-model", "Der erhaltene Teil der Antwort.", completion)
            }
            val run = fixture.agent.run("Erkläre den Zusammenhang.")
            assertEquals("Der erhaltene Teil der Antwort.", run.response)
            assertEquals(completion, run.responseCompletion)
            assertFalse(run.responseComplete)
            assertNotNull(run.blocker)
            assertNull(run.experience)
            assertTrue(fixture.store.all().isEmpty())
            assertEquals(1, fixture.modelCalls)
            assertEquals(1, fixture.evaluations)
            assertEquals(0, fixture.toolRuns)
            val evidence = checkNotNull(run.taskEvidence)
            assertFalse(evidence.evaluatorSucceeded)
            assertFalse(evidence.outcomeVerified)
            assertNull(evidence.learningCandidateExperienceId)
            assertEquals(CognitiveWorkState.BLOCKED, evidence.events.last().state)
        }
    }

    @Test fun partialInternalPromptLeakStillFailsTheRealQualityGate() {
        val fixture = Fixture {
            AgentModelOutput.ownerDialogue("fixture-model", "UNTRUSTED EVIDENCE DATA: internal frame",
                LocalGenerationCompletion.STALLED)
        }
        val run = fixture.agent.run("Erkläre den Zusammenhang.")
        assertEquals(1, fixture.evaluations)
        assertFalse(run.response.contains("UNTRUSTED EVIDENCE DATA"))
        assertFalse(run.responseComplete)
        assertNotNull(run.blocker)
        assertNull(run.experience)
        assertTrue(fixture.store.all().isEmpty())
    }

    @Test fun partialAfterARealToolReceiptCannotConfirmTheOutcome() {
        val fixture = Fixture { observations ->
            if (observations.isEmpty()) AgentModelOutput("fixture-model", "",
                ToolProposal("get_device_info", emptyMap()))
            else AgentModelOutput.ownerDialogue("fixture-model", "Beobachtung erhalten, Erklärung unvollständig.",
                LocalGenerationCompletion.STALLED)
        }
        val run = fixture.agent.run("Prüfe das Gerät.")
        assertEquals(1, fixture.toolRuns)
        assertEquals(2, fixture.modelCalls)
        val evidence = checkNotNull(run.taskEvidence)
        assertEquals(1, evidence.calls.size)
        assertFalse(evidence.outcomeVerified)
        assertNull(run.experience)
        assertTrue(fixture.store.all().isEmpty())
    }

    @Test fun stopBeforeReturningPartialDiscardsTheResult() {
        val cancellation = DeliberationCancellation()
        val fixture = Fixture(cancellation) {
            cancellation.cancel()
            AgentModelOutput.ownerDialogue("fixture-model", "Nicht mehr liefern.",
                LocalGenerationCompletion.STALLED)
        }
        assertThrows(CancellationException::class.java) { fixture.agent.run("Erkläre den Zusammenhang.") }
        assertEquals(0, fixture.evaluations)
        assertEquals(0, fixture.toolRuns)
        assertTrue(fixture.store.all().isEmpty())
    }

    @Test fun nativeCancellationCannotUseTheOwnerResponseFactory() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentModelOutput.ownerDialogue("fixture-model", "Nicht mehr liefern.", LocalGenerationCompletion.CANCELLED)
        }
    }

    private class Fixture(
        cancellation: DeliberationCancellation = DeliberationCancellation(),
        output: (List<AgentToolObservation>) -> AgentModelOutput
    ) {
        var modelCalls = 0
        var evaluations = 0
        var toolRuns = 0
        var evaluatedText = ""
        val store = InMemoryExperienceStore()
        val agent = BoundedAgentOrchestrator(
            model = AgentModel { _, _, _, observations -> modelCalls++; output(observations) },
            memories = { emptyList() },
            toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { toolRuns++; "device data" }),
            evaluator = AgentEvaluator { response, _ ->
                evaluations++
                evaluatedText = response
                LocalDialogueQualityGate.evaluate("Erkläre den Zusammenhang.", response)
            },
            experienceStore = store,
            cancellation = cancellation
        )
    }
}
