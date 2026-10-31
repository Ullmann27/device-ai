package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolGate
import at.heinz.deviceai.ToolPolicy
import at.heinz.deviceai.ToolRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Same-build, paired A/B WIRING benchmark using a deterministic mock model.
 *
 * This does NOT measure real LLM intelligence, on-device latency or energy.
 * Real-model A/B is a separate Fold7 experiment with a fixed model, task set
 * and independent outcome oracle.
 */
class AgentExperiencePairedBenchmarkTest {
    private val now = 4_000_000_000L
    private val tasks = (1..10).map { "repair battery service case $it" }

    private data class Trial(
        val enabled: Boolean,
        val acceptedTextCount: Int,
        val hintSeenCount: Int,
        val modelCalls: Int,
        val addedExperiences: Int,
        val elapsedNanos: Long
    )

    private fun historical(task: String, index: Int): Experience = Experience(
        id = "history-$index",
        taskType = "agent_task",
        requestSummary = task,
        sourceIds = listOf("tool-call:benchmark:sha256:" + "a".repeat(64)),
        toolIds = listOf("get_device_info"),
        modelIds = listOf("historical-fixture"),
        outcome = ExperienceOutcome.SUCCESS,
        startedAt = now - 11_000L,
        completedAt = now - 10_000L,
        evaluation = Evaluation(true, 0.95, "Fixture score, not independent evidence"),
        confidenceAfter = 0.95
    )

    private fun trial(enabled: Boolean): Trial {
        // Each arm starts with an identically seeded, separate store.
        val store = InMemoryExperienceStore()
        tasks.forEachIndexed { index, task -> store.store(historical(task, index)) }

        var acceptedText = 0
        var hintsSeen = 0
        var calls = 0
        val started = System.nanoTime()
        for ((index, task) in tasks.withIndex()) {
            val agent = BoundedAgentOrchestrator(
                model = AgentModel { seenTask, memories, _, observations ->
                    calls++
                    assertEquals(task, seenTask)
                    assertTrue(observations.isEmpty())
                    val hints = memories.filter { "agent-experience-hint" in it.tags }
                    assertTrue(hints.size <= 3)
                    assertEquals(enabled, hints.isNotEmpty())
                    if (hints.isNotEmpty()) hintsSeen++
                    AgentModelOutput("fixed-mock-model", "fixture-done")
                },
                memories = { emptyList() },
                toolGate = ToolGate(ToolPolicy(readOnlyEnabled = true),
                    ToolRunner { error("Benchmark forbids actual tool calls") }),
                evaluator = AgentEvaluator { response, _ ->
                    Evaluation(response == "fixture-done", 1.0)
                },
                experienceStore = store,
                recallPriorExperiences = enabled,
                clock = { now },
                callIdPrefix = "paired-$index"
            )
            val result = agent.run(task)
            if (result.experience?.outcome == ExperienceOutcome.PARTIAL &&
                result.response == "fixture-done") acceptedText++
        }
        val elapsed = System.nanoTime() - started
        return Trial(enabled, acceptedText, hintsSeen, calls,
            store.all().size - tasks.size, elapsed)
    }

    @Test fun sameBuildPairedFixtureConfirmsContextWiringWithoutClaimingIntelligenceGain() {
        val baseline = trial(enabled = false)
        val recall = trial(enabled = true)

        assertEquals(tasks.size, baseline.acceptedTextCount)
        assertEquals(tasks.size, recall.acceptedTextCount)
        assertEquals(0, baseline.hintSeenCount)
        assertEquals(tasks.size, recall.hintSeenCount)
        assertEquals(tasks.size, baseline.modelCalls)
        assertEquals(tasks.size, recall.modelCalls)
        assertEquals(tasks.size, baseline.addedExperiences)
        assertEquals(tasks.size, recall.addedExperiences)

        println("CONTINUUM MOCK A/B (NOT A REAL MODEL BENCHMARK): " +
            "tasks=${tasks.size}, " +
            "A_accepted_text=${baseline.acceptedTextCount}, B_accepted_text=${recall.acceptedTextCount}, " +
            "A_hint_seen=${baseline.hintSeenCount}, B_hint_seen=${recall.hintSeenCount}, " +
            "A_model_calls=${baseline.modelCalls}, B_model_calls=${recall.modelCalls}, " +
            "A_added_experiences=${baseline.addedExperiences}, " +
            "B_added_experiences=${recall.addedExperiences}, " +
            "A_elapsed_ns=${baseline.elapsedNanos}, B_elapsed_ns=${recall.elapsedNanos}. " +
            "Elapsed wall-clock on a test host is not Fold7 energy, inference latency, or cognitive gain.")
    }
}
