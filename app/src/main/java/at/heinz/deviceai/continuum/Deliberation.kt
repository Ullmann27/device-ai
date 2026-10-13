package at.heinz.deviceai.continuum

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class ReasoningStrategy {
    FAST, STANDARD, DEEP, MULTI_CANDIDATE, VERIFY_ONLY, RESEARCH_BACKED, TOOL_PLAN
}

data class DeliberationBudget(
    val maxModelCalls: Int = 8,
    val maxCandidateBranches: Int = 3,
    val maxCriticPasses: Int = 1,
    val maxVerifierPasses: Int = 1,
    val maxResearchRounds: Int = 0,
    val maxContextBytes: Int = 16 * 1024,
    val maxOutputBytes: Int = 8 * 1024,
    val maxWallTimeMillis: Long = 30_000,
    val checkpointCadence: Int = 1
) {
    init {
        require(maxModelCalls in 1..32)
        require(maxCandidateBranches in 0..4)
        require(maxCriticPasses in 0..4 && maxVerifierPasses in 0..4)
        require(maxResearchRounds in 0..3)
        require(maxContextBytes in 512..AiContextCompiler.MAX_CONTEXT_BYTES)
        require(maxOutputBytes in 1..64 * 1024)
        require(maxWallTimeMillis in 1..120_000)
        require(checkpointCadence in 1..maxModelCalls)
    }
}

data class TaskProperties(
    val complexity: Int = 0,
    val highImpact: Boolean = false,
    val uncertainty: Boolean = false,
    val toolPlanningRequired: Boolean = false,
    val researchRequestedByUser: Boolean = false,
    val verificationRequestedByUser: Boolean = false,
    val explicitStrategy: ReasoningStrategy? = null
) {
    init { require(complexity in 0..10) }
}

object ReasoningStrategySelector {
    fun select(task: TaskProperties): ReasoningStrategy {
        task.explicitStrategy?.let {
            if (it != ReasoningStrategy.RESEARCH_BACKED || task.researchRequestedByUser) return it
        }
        if (task.researchRequestedByUser) return ReasoningStrategy.RESEARCH_BACKED
        if (task.toolPlanningRequired) return ReasoningStrategy.TOOL_PLAN
        if (task.verificationRequestedByUser) return ReasoningStrategy.VERIFY_ONLY
        if (task.highImpact || (task.uncertainty && task.complexity >= 5)) return ReasoningStrategy.DEEP
        if (task.complexity >= 7) return ReasoningStrategy.MULTI_CANDIDATE
        if (task.complexity >= 3 || task.uncertainty) return ReasoningStrategy.STANDARD
        return ReasoningStrategy.FAST
    }

    fun promoteForModelRequest(current: ReasoningStrategy, requested: ReasoningStrategy): ReasoningStrategy {
        val permitted = requested in setOf(
            ReasoningStrategy.STANDARD, ReasoningStrategy.DEEP,
            ReasoningStrategy.MULTI_CANDIDATE, ReasoningStrategy.VERIFY_ONLY
        )
        return if (permitted && strategyDepth(requested) > strategyDepth(current)) requested else current
    }

    private fun strategyDepth(strategy: ReasoningStrategy): Int = when (strategy) {
        ReasoningStrategy.FAST -> 0
        ReasoningStrategy.STANDARD -> 1
        ReasoningStrategy.TOOL_PLAN, ReasoningStrategy.RESEARCH_BACKED -> 2
        ReasoningStrategy.VERIFY_ONLY -> 3
        ReasoningStrategy.DEEP -> 4
        ReasoningStrategy.MULTI_CANDIDATE -> 5
    }
}

fun interface DeliberationCancellation {
    fun isCancelled(): Boolean
}

fun interface DataOnlyReasoner {
    /** This interface intentionally exposes no tools, executor, shell, or device authority. */
    fun generate(
        prompt: String,
        maxOutputBytes: Int,
        deadlineNanos: Long,
        isCancelled: () -> Boolean
    ): String
}

data class CandidateCriticResult(
    val candidates: List<String>,
    val criticObservations: List<String>,
    val verifierObservations: List<String>,
    val finalOutput: String,
    val modelCalls: Int,
    val elapsedMillis: Long
)

class BoundedCandidateOrchestrator {
    fun run(
        task: String,
        reasoner: DataOnlyReasoner,
        budget: DeliberationBudget,
        cancellation: DeliberationCancellation = DeliberationCancellation { false },
        checkpoint: (completedCalls: Int) -> Unit = {}
    ): CandidateCriticResult {
        require(task.isNotBlank())
        require(task.toByteArray(Charsets.UTF_8).size <= budget.maxContextBytes)
        require(budget.maxCandidateBranches in 2..4) {
            "Candidate orchestration requires a budget for at least two branches."
        }
        val enforcer = DeliberationBudgetEnforcer(budget, cancellation, checkpoint)
        val modelExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "continuum-data-only-reasoner").apply { isDaemon = true }
        }

        fun ask(prompt: String): String {
            val boundedPrompt = truncateUtf8(prompt, budget.maxContextBytes)
            enforcer.beforeModelCall(boundedPrompt)
            val future = modelExecutor.submit<String> {
                reasoner.generate(
                    boundedPrompt,
                    budget.maxOutputBytes,
                    enforcer.deadlineNanos
                ) { cancellation.isCancelled() || System.nanoTime() >= enforcer.deadlineNanos }
            }
            try {
                while (true) {
                    enforcer.checkActive()
                    val remainingNanos = enforcer.deadlineNanos - System.nanoTime()
                    try {
                        val result = future.get(
                            minOf(remainingNanos, MODEL_CANCELLATION_POLL_NANOS),
                            TimeUnit.NANOSECONDS
                        )
                        enforcer.afterModelCall(result)
                        require(result.isNotBlank()) { "Reasoner returned an empty result." }
                        return result
                    } catch (_: TimeoutException) {
                        continue
                    }
                }
            } catch (failure: ExecutionException) {
                throw failure.cause ?: failure
            } catch (failure: Throwable) {
                future.cancel(true)
                throw failure
            }
        }

        return try {
            val candidates = List(budget.maxCandidateBranches) { branch ->
                enforcer.consumeCandidateBranch()
                ask("Produce one independent candidate solution. Candidate ${branch + 1} of ${budget.maxCandidateBranches}.\nTask:\n$task")
            }
            val critics = List(budget.maxCriticPasses) {
                enforcer.consumeCriticPass()
                ask("Critique the following candidates for errors and assumptions. Do not execute actions.\n${candidates.joinToString("\n---\n")}")
            }
            val verifiers = List(budget.maxVerifierPasses) {
                enforcer.consumeVerifierPass()
                ask("Verify claims and identify unsupported points in the candidate analysis. Do not use tools.\n${(candidates + critics).joinToString("\n---\n")}")
            }
            val final = ask(
                "Synthesize a bounded final response. Surface unresolved uncertainty. Do not execute tools.\n" +
                    (candidates + critics + verifiers).joinToString("\n---\n")
            )
            enforcer.checkActive()
            CandidateCriticResult(
                candidates, critics, verifiers, final, enforcer.modelCalls, enforcer.elapsedMillis
            )
        } finally {
            modelExecutor.shutdownNow()
        }
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        var index = 0
        var bytes = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val charCount = Character.charCount(codePoint)
            val encodedSize = when {
                codePoint <= 0x7f -> 1
                codePoint <= 0x7ff -> 2
                codePoint in 0xd800..0xdfff -> 1
                codePoint <= 0xffff -> 3
                else -> 4
            }
            if (bytes + encodedSize > maxBytes) break
            bytes += encodedSize
            index += charCount
        }
        return value.substring(0, index)
    }

    private companion object {
        const val MODEL_CANCELLATION_POLL_NANOS = 50_000_000L
    }
}

class DeliberationBudgetEnforcer(
    private val budget: DeliberationBudget,
    private val cancellation: DeliberationCancellation = DeliberationCancellation { false },
    private val checkpoint: (completedCalls: Int) -> Unit = {}
) {
    private val startedAtNanos = System.nanoTime()
    val deadlineNanos: Long = startedAtNanos + budget.maxWallTimeMillis * NANOS_PER_MILLI
    var modelCalls: Int = 0
        private set
    private var candidateBranches = 0
    private var criticPasses = 0
    private var verifierPasses = 0
    private var researchRounds = 0
    val elapsedMillis: Long get() = (System.nanoTime() - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_MILLI

    fun beforeModelCall(context: String) {
        checkActive()
        require(context.toByteArray(Charsets.UTF_8).size <= budget.maxContextBytes) {
            "Deliberation context budget exceeded."
        }
        check(modelCalls < budget.maxModelCalls) { "Deliberation model-call budget exhausted." }
        modelCalls++
    }

    fun afterModelCall(output: String) {
        checkActive()
        require(output.toByteArray(Charsets.UTF_8).size <= budget.maxOutputBytes) {
            "Reasoner exceeded the output bound."
        }
        if (modelCalls % budget.checkpointCadence == 0) checkpoint(modelCalls)
    }

    fun consumeCandidateBranch() {
        checkActive()
        check(candidateBranches < budget.maxCandidateBranches) { "Candidate-branch budget exhausted." }
        candidateBranches++
    }

    fun consumeCriticPass() {
        checkActive()
        check(criticPasses < budget.maxCriticPasses) { "Critic-pass budget exhausted." }
        criticPasses++
    }

    fun consumeVerifierPass() {
        checkActive()
        check(verifierPasses < budget.maxVerifierPasses) { "Verifier-pass budget exhausted." }
        verifierPasses++
    }

    fun consumeResearchRound() {
        checkActive()
        check(researchRounds < budget.maxResearchRounds) { "Research-round budget exhausted." }
        researchRounds++
    }

    fun checkActive() {
        if (cancellation.isCancelled()) throw CancellationException("Deliberation cancelled.")
        if (System.nanoTime() >= deadlineNanos) throw DeliberationDeadlineExceeded()
    }

    private companion object { const val NANOS_PER_MILLI = 1_000_000L }
}

class DeliberationDeadlineExceeded : RuntimeException("Deliberation deadline exceeded.")
