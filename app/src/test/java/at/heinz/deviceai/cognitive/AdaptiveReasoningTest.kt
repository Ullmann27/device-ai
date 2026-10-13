package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class AdaptiveReasoningTest {
    @Test fun selectsAllSevenStrategies() {
        val policy = AdaptiveReasoningPolicy()
        val cases = listOf(
            TaskComplexityInput("Say hello") to ReasoningStrategy.FAST,
            TaskComplexityInput("Explain this", relevantMemoryCount = 1) to ReasoningStrategy.STANDARD,
            TaskComplexityInput("Solve this", decompositionNeed = 0.8) to ReasoningStrategy.DEEP,
            TaskComplexityInput("Compare alternatives") to ReasoningStrategy.MULTI_CANDIDATE,
            TaskComplexityInput("Check this", existingAnswer = "Existing answer") to ReasoningStrategy.VERIFY_ONLY,
            TaskComplexityInput("What changed today?") to ReasoningStrategy.RESEARCH_BACKED,
            TaskComplexityInput("Delete this file", toolRequired = true) to ReasoningStrategy.TOOL_PLAN
        )
        cases.forEach { (input, expected) -> assertEquals(expected, policy.select(input).strategy) }
    }

    @Test fun depthRequestsCannotBypassToolOrResearchRouting() {
        val policy = AdaptiveReasoningPolicy()
        assertEquals(ReasoningStrategy.TOOL_PLAN, policy.select(TaskComplexityInput(
            "Device operation", toolRequired = true, modelRequestsDeepThinking = true
        )).strategy)
        assertEquals(ReasoningStrategy.RESEARCH_BACKED, policy.select(TaskComplexityInput(
            "Fresh facts", researchRequired = true, ownerRequestsDeepThinking = true
        )).strategy)
    }

    @Test fun modelRequestNeverIncreasesAnyRuntimeCeiling() {
        val hard = DeliberationBudget(2, 1, 0, 0, 0, 1_024, 250, 1)
        val selected = AdaptiveReasoningPolicy(hard).select(TaskComplexityInput(
            "Compare alternatives", modelRequestsDeepThinking = true
        )).budget
        assertTrue(selected.maxModelCalls <= hard.maxModelCalls)
        assertTrue(selected.maxCandidateBranches <= hard.maxCandidateBranches)
        assertTrue(selected.maxCriticPasses <= hard.maxCriticPasses)
        assertTrue(selected.maxVerifierPasses <= hard.maxVerifierPasses)
        assertTrue(selected.maxResearchRounds <= hard.maxResearchRounds)
        assertTrue(selected.maxContextBytes <= hard.maxContextBytes)
        assertTrue(selected.maxWallTimeMillis <= hard.maxWallTimeMillis)
        assertTrue(selected.checkpointEveryCalls <= hard.checkpointEveryCalls)
    }

    @Test fun nonFiniteSignalsAndOversizedUtf8AreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskComplexityInput("task", ambiguity = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskComplexityInput("task", failureImpact = Double.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskComplexityInput("界".repeat(3_000))
        }
    }

    @Test fun verifyOnlyChecksSuppliedAnswerWithoutGeneratingAnother() {
        var generated = false
        val runtime = BoundedDeliberationOrchestrator(
            DeliberationCandidateGenerator { _, _ -> generated = true; "wrong" },
            DeliberationCritic { _, _ -> "critic" },
            DeliberationVerifier { _, candidate ->
                assertEquals("Existing answer", candidate.text); "checked"
            },
            DeliberationJudge { _, _, _, _ -> "Verified answer" },
            nowNanos = { 0L }
        )
        val result = runtime.run("Check", ReasoningStrategy.VERIFY_ONLY, existingAnswer = "Existing answer")
        assertFalse(generated)
        assertEquals(2, result.modelCalls)
        assertEquals("Verified answer", result.finalText)
    }

    @Test fun verifyOnlyWithoutAnswerFailsBeforeAnyProviderCall() {
        var calls = 0
        val runtime = fixture { calls++; "answer" }
        assertThrows(IllegalArgumentException::class.java) {
            runtime.run("Check", ReasoningStrategy.VERIFY_ONLY)
        }
        assertEquals(0, calls)
    }

    @Test fun lastCallCannotReturnSuccessAfterDeadline() {
        var clock = 0L
        val runtime = fixture(nowNanos = { clock }) { clock = 5_000_000_000L; "late" }
        assertThrows(IllegalArgumentException::class.java) {
            runtime.run("task", ReasoningStrategy.FAST)
        }
    }

    @Test fun cancellationDuringFinalCallIsNotReportedAsSuccess() {
        val cancellation = DeliberationCancellation()
        val runtime = fixture { cancellation.cancel(); "late" }
        assertThrows(CancellationException::class.java) {
            runtime.run("task", ReasoningStrategy.FAST, cancellation = cancellation)
        }
    }

    @Test fun outputOverflowIsRejectedRatherThanSilentlyTruncated() {
        assertThrows(IllegalArgumentException::class.java) {
            fixture { "x".repeat(8_001) }.run("task", ReasoningStrategy.FAST)
        }
        assertThrows(IllegalArgumentException::class.java) {
            fixture { "界".repeat(3_000) }.run("task", ReasoningStrategy.FAST)
        }
    }

    @Test fun taskByteOverflowPreventsProviderExecution() {
        var calls = 0
        val budget = DeliberationBudget.forStrategy(ReasoningStrategy.FAST).copy(maxContextBytes = 1_024)
        assertThrows(IllegalArgumentException::class.java) {
            fixture { calls++; "answer" }.run("界".repeat(400), ReasoningStrategy.FAST, budget)
        }
        assertEquals(0, calls)
    }

    @Test fun checkpointsFollowCompletedCalls() {
        var completed = 0
        val observed = mutableListOf<Int>()
        fixture { completed++; "answer" }.run(
            "task", ReasoningStrategy.FAST, onCheckpoint = { count ->
                assertEquals(completed, count); observed += count
            }
        )
        assertEquals(listOf(1), observed)
    }

    @Test fun deterministicBenchmarkComparesActualSingleAndMultiPassExecutions() {
        val cases = listOf(
            ContinuumBenchmarkCase("android-order", BenchmarkCategory.DEVICE_REASONING, "Troubleshooting sequence"),
            ContinuumBenchmarkCase("bounded-loop", BenchmarkCategory.CODE_REASONING, "Bounded retry loop"),
            ContinuumBenchmarkCase("conflict", BenchmarkCategory.CONTRADICTION_HANDLING, "Conflicting supplied claims")
        )
        fun execute(case: ContinuumBenchmarkCase, adaptive: Boolean): BenchmarkExecution {
            val strategy = if (adaptive) AdaptiveReasoningPolicy().select(TaskComplexityInput(
                case.prompt, decompositionNeed = 0.8, contradictionRisk = 0.5
            )).strategy else ReasoningStrategy.FAST
            val runtime = fixture { "deterministic-fixture-answer" }
            val result = runtime.run(case.prompt, strategy)
            return BenchmarkExecution(
                success = result.finalText == "deterministic-fixture-answer",
                modelCalls = result.modelCalls, latencyMillis = 0,
                contextBytes = case.prompt.toByteArray(Charsets.UTF_8).size
            )
        }
        val (_, baseline) = ContinuumBenchmarkHarness.run(cases) { execute(it, false) }
        val (_, continuum) = ContinuumBenchmarkHarness.run(cases) { execute(it, true) }
        assertEquals(cases.size, baseline.totalModelCalls)
        assertTrue(continuum.totalModelCalls > baseline.totalModelCalls)
        assertEquals(baseline.successRate, continuum.successRate, 0.0)
        // Fixture latency is zero and task bytes are not full provider prompt bytes.
        // This proves pipeline execution/accounting, not improved model intelligence.
    }

    private fun fixture(
        nowNanos: () -> Long = { 0L },
        generate: () -> String
    ) = BoundedDeliberationOrchestrator(
        DeliberationCandidateGenerator { _, _ -> generate() },
        DeliberationCritic { _, _ -> "deterministic-fixture-answer" },
        DeliberationVerifier { _, _ -> "deterministic-fixture-answer" },
        DeliberationJudge { _, _, _, _ -> "deterministic-fixture-answer" },
        nowNanos
    )
}
