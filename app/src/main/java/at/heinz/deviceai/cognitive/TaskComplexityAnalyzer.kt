package at.heinz.deviceai.cognitive

import java.util.Locale

data class TaskComplexityInput(
    val request: String,
    val ambiguity: Double = 0.0,
    val decompositionNeed: Double = 0.0,
    val freshnessNeeded: Boolean = false,
    val contradictionRisk: Double = 0.0,
    val toolRequired: Boolean = false,
    val researchRequired: Boolean = false,
    val relevantMemoryCount: Int = 0,
    val failureImpact: Double = 0.0,
    val evidenceCount: Int = 0,
    val existingAnswer: String? = null,
    val ownerRequestsDeepThinking: Boolean = false,
    val modelRequestsDeepThinking: Boolean = false
) {
    init {
        require(request.isNotBlank() && request.toByteArray(Charsets.UTF_8).size <= 8_192)
        require(listOf(ambiguity, decompositionNeed, contradictionRisk, failureImpact)
            .all { it.isFinite() && it in 0.0..1.0 })
        require(relevantMemoryCount in 0..10_000 && evidenceCount in 0..256)
        require(existingAnswer == null || (existingAnswer.isNotBlank() &&
            existingAnswer.toByteArray(Charsets.UTF_8).size <= 8_000))
    }
}

data class TaskComplexityAssessment(
    val ambiguity: Double,
    val decompositionNeed: Double,
    val factualFreshness: Boolean,
    val contradictionRisk: Double,
    val toolRequired: Boolean,
    val researchRequired: Boolean,
    val memoryRelevance: Double,
    val failureImpact: Double,
    val evidenceAvailability: Double,
    val score: Double,
    val reasons: List<String>
)

data class AdaptiveReasoningSelection(
    val assessment: TaskComplexityAssessment,
    val strategy: ReasoningStrategy,
    val budget: DeliberationBudget
)

/** Explainable initial heuristics; scores are not calibrated probabilities. */
class TaskComplexityAnalyzer {
    fun analyze(input: TaskComplexityInput): TaskComplexityAssessment {
        val words = input.request.lowercase(Locale.ROOT)
            .split(Regex("""[^\\p{L}\\p{N}_]+""")).toSet()
        val ambiguity = maxOf(input.ambiguity, if (words.any {
            it in setOf("alternatives", "alternative", "varianten", "options")
        }) 0.85 else 0.0)
        val decomposition = maxOf(input.decompositionNeed, if (words.any {
            it in setOf("constraints", "nebenbedingungen", "decompose", "zerlege")
        }) 0.65 else 0.0)
        val freshness = input.freshnessNeeded || words.any {
            it in setOf("latest", "current", "today", "neueste", "aktuell", "heute")
        }
        val contradiction = maxOf(input.contradictionRisk, if (words.any {
            it in setOf("contradiction", "contradictory", "widerspruch", "widersprüchlich")
        }) 0.75 else 0.0)
        val evidence = minOf(input.evidenceCount, 8) / 8.0
        val memory = minOf(input.relevantMemoryCount, 8) / 8.0
        val score = (0.25 * ambiguity + 0.25 * decomposition +
            0.20 * contradiction + 0.20 * input.failureImpact +
            0.10 * (1.0 - evidence)).coerceIn(0.0, 1.0)
        val reasons = buildList {
            if (input.toolRequired) add("RUNTIME_TOOL_REQUIREMENT")
            if (freshness || input.researchRequired) add("EVIDENCE_REQUIRED")
            if (ambiguity >= 0.8) add("MULTIPLE_PLAUSIBLE_SOLUTIONS")
            if (decomposition >= 0.6) add("DECOMPOSITION_REQUIRED")
            if (contradiction > 0.0) add("CONTRADICTION_RISK")
            if (input.failureImpact >= 0.5) add("HIGH_FAILURE_IMPACT")
            if (input.ownerRequestsDeepThinking) add("OWNER_REQUESTED_DEPTH")
            if (input.modelRequestsDeepThinking) add("MODEL_REQUEST_IS_NOT_AUTHORITY")
            if (isEmpty()) add("LOW_COMPLEXITY")
        }
        return TaskComplexityAssessment(ambiguity, decomposition, freshness, contradiction,
            input.toolRequired, input.researchRequired, memory, input.failureImpact,
            evidence, score, reasons)
    }
}

/** Runtime ceilings are immutable and never supplied by a model response. */
class AdaptiveReasoningPolicy(
    private val hardLimit: DeliberationBudget =
        DeliberationBudget.forStrategy(ReasoningStrategy.DEEP)
) {
    fun select(input: TaskComplexityInput): AdaptiveReasoningSelection {
        val assessment = TaskComplexityAnalyzer().analyze(input)
        val preference = when {
            input.ownerRequestsDeepThinking || input.modelRequestsDeepThinking ||
                assessment.score >= 0.4 || assessment.decompositionNeed >= 0.6 ||
                assessment.failureImpact >= 0.7 -> ReasoningStrategy.DEEP
            assessment.score >= 0.2 || assessment.contradictionRisk > 0.0 ||
                assessment.memoryRelevance > 0.0 -> ReasoningStrategy.STANDARD
            else -> null
        }
        val strategy = ReasoningStrategySelector.select(ReasoningTaskProfile(
            ambiguity = assessment.ambiguity,
            factualFreshnessNeeded = assessment.factualFreshness || assessment.researchRequired,
            expectedToolUse = assessment.toolRequired,
            verifyExistingAnswer = input.existingAnswer != null,
            explicitStrategy = if (assessment.ambiguity >= 0.8)
                ReasoningStrategy.MULTI_CANDIDATE else preference
        ))
        val preferred = DeliberationBudget.forStrategy(strategy)
        val calls = minOf(preferred.maxModelCalls, hardLimit.maxModelCalls)
        val budget = DeliberationBudget(
            calls,
            minOf(preferred.maxCandidateBranches, hardLimit.maxCandidateBranches),
            minOf(preferred.maxCriticPasses, hardLimit.maxCriticPasses),
            minOf(preferred.maxVerifierPasses, hardLimit.maxVerifierPasses),
            minOf(preferred.maxResearchRounds, hardLimit.maxResearchRounds),
            minOf(preferred.maxContextBytes, hardLimit.maxContextBytes),
            minOf(preferred.maxWallTimeMillis, hardLimit.maxWallTimeMillis),
            minOf(preferred.checkpointEveryCalls, hardLimit.checkpointEveryCalls, calls)
        )
        return AdaptiveReasoningSelection(assessment, strategy, budget)
    }
}
