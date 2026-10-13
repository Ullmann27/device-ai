package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class DeliberationTest {
    @Test
    fun selectorUsesResearchForFreshFactsAndToolPlanForDeviceActions() {
        assertEquals(
            ReasoningStrategy.RESEARCH_BACKED,
            ReasoningStrategySelector.select(ReasoningTaskProfile(factualFreshnessNeeded = true))
        )
        assertEquals(
            ReasoningStrategy.TOOL_PLAN,
            ReasoningStrategySelector.select(ReasoningTaskProfile(expectedToolUse = true))
        )
    }

    @Test
    fun multiCandidateRunIsBoundedAndProducesOneFinalAnswer() {
        val orchestrator = BoundedDeliberationOrchestrator(
            generator = DeliberationCandidateGenerator { _, index -> "candidate " + index },
            critic = DeliberationCritic { _, candidate -> "critic " + candidate.id },
            verifier = DeliberationVerifier { _, candidate -> "verified " + candidate.id },
            judge = DeliberationJudge { _, candidates, _, _ -> "winner " + candidates.first().id },
            nowNanos = { 1_000L }
        )

        val result = orchestrator.run(
            task = "solve",
            strategy = ReasoningStrategy.MULTI_CANDIDATE,
            budget = DeliberationBudget.forStrategy(ReasoningStrategy.MULTI_CANDIDATE)
        )

        assertEquals(4, result.candidates.size)
        assertEquals(4, result.critiques.size)
        assertEquals(2, result.verifications.size)
        assertEquals("winner candidate-0", result.finalText)
        assertTrue(result.modelCalls <= 14)
    }

    @Test
    fun hardModelCallBudgetCannotBeRaisedByThePipeline() {
        val orchestrator = BoundedDeliberationOrchestrator(
            generator = DeliberationCandidateGenerator { _, index -> "candidate " + index },
            critic = DeliberationCritic { _, _ -> "critique" },
            verifier = DeliberationVerifier { _, _ -> "verify" },
            judge = DeliberationJudge { _, _, _, _ -> "judge" },
            nowNanos = { 1_000L }
        )
        val tinyBudget = DeliberationBudget(
            maxModelCalls = 2,
            maxCandidateBranches = 4,
            maxCriticPasses = 4,
            maxVerifierPasses = 2,
            maxResearchRounds = 0,
            maxContextBytes = 4_096,
            maxWallTimeMillis = 1_000,
            checkpointEveryCalls = 1
        )

        assertThrows(IllegalArgumentException::class.java) {
            orchestrator.run("hard task", ReasoningStrategy.MULTI_CANDIDATE, tinyBudget)
        }
    }

    @Test
    fun cancellationStopsBeforeModelWork() {
        var calls = 0
        val cancellation = DeliberationCancellation().also { it.cancel() }
        val orchestrator = BoundedDeliberationOrchestrator(
            generator = DeliberationCandidateGenerator { _, _ -> calls++; "candidate" },
            critic = DeliberationCritic { _, _ -> "critic" },
            verifier = DeliberationVerifier { _, _ -> "verify" },
            judge = DeliberationJudge { _, _, _, _ -> "judge" }
        )

        assertThrows(CancellationException::class.java) {
            orchestrator.run("task", ReasoningStrategy.FAST, cancellation = cancellation)
        }
        assertEquals(0, calls)
    }
}
