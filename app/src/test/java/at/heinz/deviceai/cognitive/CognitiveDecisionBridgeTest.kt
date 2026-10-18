package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumCognitiveRuntime
import at.heinz.deviceai.learning.ContinuumLearningCore
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class CognitiveDecisionBridgeTest {
    @Test fun modelPreservesRawOwnerFactWithoutRepeatingUiBoilerplateOrDialogueTurn() {
        val dir = Files.createTempDirectory("continuum-bridge-grounded").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt seit 2024 in Graz.")
            val model = ContinuumReasoningAdapter { request ->
                assertEquals(ReasoningPassRole.CANDIDATE, request.role)
                assertTrue(request.prompt.contains("Leo wohnt seit 2024 in Graz."))
                assertFalse(request.prompt.contains("Dazu finde ich in meinem Gedächtnis:"))
                ReasoningPassResult("local-fixture", "Leo lebt seit 2024 in Graz.", 2L)
            }
            val result = CognitiveDecisionBridge(ContinuumCognitiveRuntime(dir, core), model)
                .respond("Wo wohnt Leo?")
            assertTrue(result.usedLocalModel)
            assertFalse(result.anchorRejectedModelOutput)
            assertEquals("Leo lebt seit 2024 in Graz.", result.finalAnswer)
            assertEquals(1, result.evidenceHits.size)
            assertEquals(1, core.dialogueHistory().size)
            assertEquals(result.turn.dialogue.memoryIds, result.evidenceHits.map { it.id })
        } finally { dir.deleteRecursively() }
    }

    @Test fun alteredNumberAndLocationBothFailClosed() {
        val dir = Files.createTempDirectory("continuum-bridge-tamper").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt seit 2024 in Graz.")
            for (answer in listOf("Leo wohnt seit 2025 in Graz.",
                                  "Leo wohnt seit 2024 in Wien.")) {
                val model = ContinuumReasoningAdapter {
                    ReasoningPassResult("local-fixture", answer, 2L)
                }
                val result = CognitiveDecisionBridge(ContinuumCognitiveRuntime(dir, core), model)
                    .respond("Wo wohnt Leo?")
                assertFalse(result.usedLocalModel)
                assertTrue(result.anchorRejectedModelOutput)
                assertTrue(result.finalAnswer.contains("Graz"))
                assertTrue(result.finalAnswer.contains("2024"))
            }
            assertEquals(2, core.dialogueHistory().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun modelAttemptingToCallToolsDuringReasoningIsNeverAccepted() {
        val dir = Files.createTempDirectory("continuum-bridge-tool").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val model = ContinuumReasoningAdapter {
                ReasoningPassResult("local-fixture", "Leo wohnt in Graz.", 2L, 1)
            }
            val result = CognitiveDecisionBridge(ContinuumCognitiveRuntime(dir, core), model)
                .respond("Wo wohnt Leo?")
            assertFalse(result.usedLocalModel)
            assertTrue(result.anchorRejectedModelOutput)
            assertEquals(1, core.dialogueHistory().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun unavailableModelFallsBackWithoutForgettingOrAdditionalHistoryWrites() {
        val dir = Files.createTempDirectory("continuum-bridge-unavailable").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Leo wohnt in Graz.")
            val result = CognitiveDecisionBridge(ContinuumCognitiveRuntime(dir, core), null)
                .respond("Wo wohnt Leo?")
            assertFalse(result.usedLocalModel)
            assertFalse(result.anchorRejectedModelOutput)
            assertTrue(result.finalAnswer.contains("Graz"))
            assertEquals(1, core.dialogueHistory().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun modelFailureReturnsDeterministicFallbackAndNoFactIsCreated() {
        val dir = Files.createTempDirectory("continuum-bridge-exception").toFile()
        try {
            val core = ContinuumLearningCore(dir)
            val model = ContinuumReasoningAdapter { throw IllegalStateException("offline") }
            val result = CognitiveDecisionBridge(ContinuumCognitiveRuntime(dir, core), model)
                .respond("Wo wohnt Leo?")
            assertFalse(result.usedLocalModel)
            assertFalse(result.turn.grounded)
            assertTrue(result.evidenceHits.isEmpty())
            assertEquals(1, core.dialogueHistory().size)
        } finally { dir.deleteRecursively() }
    }
}
