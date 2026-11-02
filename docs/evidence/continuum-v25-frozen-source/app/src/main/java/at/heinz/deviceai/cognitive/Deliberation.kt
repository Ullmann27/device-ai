package at.heinz.deviceai.cognitive

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

enum class ReasoningStrategy {
    FAST,
    STANDARD,
    DEEP,
    MULTI_CANDIDATE,
    VERIFY_ONLY,
    RESEARCH_BACKED,
    TOOL_PLAN
}

data class DeliberationBudget(
    val maxModelCalls: Int,
    val maxCandidateBranches: Int,
    val maxCriticPasses: Int,
    val maxVerifierPasses: Int,
    val maxResearchRounds: Int,
    val maxContextBytes: Int,
    val maxWallTimeMillis: Long,
    val checkpointEveryCalls: Int
) {
    init {
        require(maxModelCalls in 1..64)
        require(maxCandidateBranches in 1..8)
        require(maxCriticPasses in 0..16)
        require(maxVerifierPasses in 0..16)
        require(maxResearchRounds in 0..3)
        require(maxContextBytes in 1_024..64 * 1024)
        require(maxWallTimeMillis in 250..120_000)
        require(checkpointEveryCalls in 1..maxModelCalls)
    }

    companion object {
        fun forStrategy(strategy: ReasoningStrategy): DeliberationBudget = when (strategy) {
            ReasoningStrategy.FAST -> DeliberationBudget(2, 1, 0, 0, 0, 6_000, 5_000, 1)
            ReasoningStrategy.STANDARD -> DeliberationBudget(5, 1, 1, 1, 0, 10_000, 15_000, 2)
            ReasoningStrategy.DEEP -> DeliberationBudget(12, 3, 3, 2, 0, 16_000, 45_000, 3)
            ReasoningStrategy.MULTI_CANDIDATE -> DeliberationBudget(14, 4, 4, 2, 0, 16_000, 45_000, 3)
            ReasoningStrategy.VERIFY_ONLY -> DeliberationBudget(4, 1, 0, 2, 0, 10_000, 15_000, 2)
            ReasoningStrategy.RESEARCH_BACKED -> DeliberationBudget(12, 2, 2, 2, 3, 16_000, 90_000, 3)
            ReasoningStrategy.TOOL_PLAN -> DeliberationBudget(6, 2, 1, 1, 0, 10_000, 20_000, 2)
        }
    }
}

data class ReasoningTaskProfile(
    val ambiguity: Double = 0.0,
    val factualFreshnessNeeded: Boolean = false,
    val expectedToolUse: Boolean = false,
    val verifyExistingAnswer: Boolean = false,
    val explicitStrategy: ReasoningStrategy? = null
) {
    init { require(ambiguity.isFinite() && ambiguity in 0.0..1.0) }
}

object ReasoningStrategySelector {
    fun select(profile: ReasoningTaskProfile): ReasoningStrategy = when {
        profile.expectedToolUse -> ReasoningStrategy.TOOL_PLAN
        profile.verifyExistingAnswer -> ReasoningStrategy.VERIFY_ONLY
        profile.factualFreshnessNeeded -> ReasoningStrategy.RESEARCH_BACKED
        profile.explicitStrategy != null -> profile.explicitStrategy
        profile.ambiguity >= 0.8 -> ReasoningStrategy.MULTI_CANDIDATE
        profile.ambiguity >= 0.5 -> ReasoningStrategy.DEEP
        profile.ambiguity >= 0.2 -> ReasoningStrategy.STANDARD
        else -> ReasoningStrategy.FAST
    }

    fun boundedUpgrade(
        current: ReasoningStrategy,
        requested: ReasoningStrategy,
        allowed: Set<ReasoningStrategy>
    ): ReasoningStrategy {
        require(current in allowed)
        return if (requested in allowed) requested else current
    }
}

class DeliberationCancellation {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true) }
    fun throwIfCancelled() {
        if (cancelled.get()) throw CancellationException("Deliberation cancelled.")
    }
}

data class DeliberationCandidate(val id: String, val text: String) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(text.isNotBlank() && text.length <= MAX_PASS_TEXT)
    }
}

data class DeliberationCritique(val candidateId: String, val text: String) {
    init { require(candidateId.isNotBlank() && text.length <= MAX_PASS_TEXT) }
}

data class DeliberationVerification(val candidateId: String, val text: String) {
    init { require(candidateId.isNotBlank() && text.length <= MAX_PASS_TEXT) }
}

data class DeliberationResult(
    val strategy: ReasoningStrategy,
    val finalText: String,
    val candidates: List<DeliberationCandidate>,
    val critiques: List<DeliberationCritique>,
    val verifications: List<DeliberationVerification>,
    val modelCalls: Int
) {
    init {
        require(finalText.isNotBlank() && finalText.length <= MAX_PASS_TEXT)
        require(modelCalls >= 1)
    }
}

fun interface DeliberationCandidateGenerator {
    fun generate(task: String, candidateIndex: Int): String
}

fun interface DeliberationCritic {
    fun critique(task: String, candidate: DeliberationCandidate): String
}

fun interface DeliberationVerifier {
    fun verify(task: String, candidate: DeliberationCandidate): String
}

fun interface DeliberationJudge {
    fun choose(
        task: String,
        candidates: List<DeliberationCandidate>,
        critiques: List<DeliberationCritique>,
        verifications: List<DeliberationVerification>
    ): String
}

class BoundedDeliberationOrchestrator(
    private val generator: DeliberationCandidateGenerator,
    private val critic: DeliberationCritic,
    private val verifier: DeliberationVerifier,
    private val judge: DeliberationJudge,
    private val nowNanos: () -> Long = System::nanoTime
) {
    fun run(
        task: String,
        strategy: ReasoningStrategy,
        budget: DeliberationBudget = DeliberationBudget.forStrategy(strategy),
        cancellation: DeliberationCancellation = DeliberationCancellation(),
        onCheckpoint: (Int) -> Unit = {},
        existingAnswer: String? = null
    ): DeliberationResult {
        require(task.isNotBlank() && task.length <= 8_192)
        require(task.toByteArray(Charsets.UTF_8).size <= budget.maxContextBytes) {
            "Task exceeds the deliberation context byte budget."
        }
        val started = nowNanos()
        var calls = 0

        fun checkLive() {
            cancellation.throwIfCancelled()
            if (Thread.currentThread().isInterrupted) {
                throw CancellationException("Deliberation interrupted.")
            }
            require(nowNanos() - started < budget.maxWallTimeMillis * 1_000_000L) {
                "Deliberation wall-time budget exceeded."
            }
        }

        fun checkedText(text: String): String {
            require(text.isNotBlank()) { "Empty thinking output." }
            require(text.length <= MAX_PASS_TEXT &&
                text.toByteArray(Charsets.UTF_8).size <= MAX_PASS_TEXT) {
                "Thinking output limit exceeded; silent truncation is forbidden."
            }
            return text
        }

        fun call(operation: () -> String): String {
            checkLive()
            require(calls < budget.maxModelCalls) {
                "Deliberation model-call budget exceeded."
            }
            calls++
            val result = checkedText(operation())
            checkLive()
            if (calls % budget.checkpointEveryCalls == 0) {
                onCheckpoint(calls)
                checkLive()
            }
            return result
        }

        checkLive()
        val branchCount = when (strategy) {
            ReasoningStrategy.MULTI_CANDIDATE -> minOf(4, budget.maxCandidateBranches)
            ReasoningStrategy.DEEP, ReasoningStrategy.RESEARCH_BACKED ->
                minOf(2, budget.maxCandidateBranches)
            else -> 1
        }
        val candidates = if (strategy == ReasoningStrategy.VERIFY_ONLY) {
            require(budget.maxVerifierPasses > 0) { "VERIFY_ONLY requires a verifier." }
            listOf(DeliberationCandidate("existing-answer", checkedText(
                requireNotNull(existingAnswer) { "VERIFY_ONLY requires an existing answer." }
            )))
        } else {
            (0 until branchCount).map { index ->
                DeliberationCandidate("candidate-$index", call { generator.generate(task, index) })
            }
        }

        val critiques = mutableListOf<DeliberationCritique>()
        candidates.take(budget.maxCriticPasses).forEach { candidate ->
            critiques += DeliberationCritique(candidate.id, call { critic.critique(task, candidate) })
        }
        val verifications = mutableListOf<DeliberationVerification>()
        candidates.take(budget.maxVerifierPasses).forEach { candidate ->
            verifications += DeliberationVerification(candidate.id, call { verifier.verify(task, candidate) })
        }
        val finalText = if (candidates.size == 1 && critiques.isEmpty() && verifications.isEmpty()) {
            candidates.single().text
        } else {
            call { judge.choose(task, candidates, critiques, verifications) }
        }
        checkLive()
        return DeliberationResult(strategy, finalText, candidates, critiques, verifications, calls)
    }
}

private const val MAX_PASS_TEXT = 8_000
