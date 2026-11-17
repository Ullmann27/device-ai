package at.heinz.deviceai.cognitive

import java.util.Locale

enum class IntelligenceTaskClass {
    GENERAL,
    DEVICE,
    CODE,
    RESEARCH,
    MEMORY,
    PLANNING,
    VERIFICATION
}

data class IntelligenceTaskAssessment(
    val taskClass: IntelligenceTaskClass,
    val capability: ModelCapability,
    val domain: String,
    val complexity: Double,
    val freshnessNeeded: Boolean,
    val expectedToolUse: Boolean,
    val verifyExistingAnswer: Boolean,
    val requestedStrategy: ReasoningStrategy? = null
) {
    init {
        require(domain.isNotBlank())
        require(complexity.isFinite() && complexity in 0.0..1.0)
    }

    fun strategyProfile(): ReasoningTaskProfile = ReasoningTaskProfile(
        ambiguity = complexity,
        factualFreshnessNeeded = freshnessNeeded,
        expectedToolUse = expectedToolUse,
        verifyExistingAnswer = verifyExistingAnswer,
        explicitStrategy = requestedStrategy
    )
}

object ContinuumTaskClassifier {
    private val wordSplitter = Regex("""[^\p{L}\p{N}_]+""")
    private val shortPrefixStems = setOf("prüf")
    private const val EXACT_WORD_MAX_LENGTH = 4

    fun classify(request: String, requestedStrategy: ReasoningStrategy? = null): IntelligenceTaskAssessment {
        require(request.isNotBlank() && request.length <= 16_000)
        val text = request.lowercase(Locale.ROOT)
        val words = text.split(wordSplitter).filter { it.isNotEmpty() }

        val research = containsAny(text, words, "latest", "heute", "aktuell", "news", "quelle", "quellen", "recherch")
        val code = containsAny(text, words, "code", "kotlin", "java", "gradle", "github", "compile", "build", "repository", "repo")
        val device = containsAny(text, words, "android", "shizuku", "akku", "battery", "app", "apk", "speicher", "logcat", "gerät")
        val memory = containsAny(text, words, "erinner", "memory", "gemerkt", "weißt du noch", "vorher")
        val verify = containsAny(text, words, "prüf", "verify", "kontroll", "stimmt das", "gegenprüf")
        val plan = containsAny(text, words, "plan", "schritt", "strategie", "roadmap", "vorgehen")

        val taskClass = when {
            research -> IntelligenceTaskClass.RESEARCH
            code -> IntelligenceTaskClass.CODE
            device -> IntelligenceTaskClass.DEVICE
            memory -> IntelligenceTaskClass.MEMORY
            verify -> IntelligenceTaskClass.VERIFICATION
            plan -> IntelligenceTaskClass.PLANNING
            else -> IntelligenceTaskClass.GENERAL
        }
        val capability = when (taskClass) {
            IntelligenceTaskClass.CODE -> ModelCapability.CODING
            IntelligenceTaskClass.DEVICE -> ModelCapability.ANDROID_DEBUGGING
            IntelligenceTaskClass.RESEARCH -> ModelCapability.RESEARCH
            IntelligenceTaskClass.MEMORY -> ModelCapability.FACTUAL_ACCURACY
            IntelligenceTaskClass.PLANNING -> ModelCapability.PLANNING
            IntelligenceTaskClass.VERIFICATION -> ModelCapability.SOURCE_EVALUATION
            IntelligenceTaskClass.GENERAL -> ModelCapability.FACTUAL_ACCURACY
        }
        val domain = when (taskClass) {
            IntelligenceTaskClass.CODE -> "code"
            IntelligenceTaskClass.DEVICE -> "android"
            IntelligenceTaskClass.RESEARCH -> "research"
            IntelligenceTaskClass.MEMORY -> "personal-memory"
            IntelligenceTaskClass.PLANNING -> "planning"
            IntelligenceTaskClass.VERIFICATION -> "verification"
            IntelligenceTaskClass.GENERAL -> "general"
        }

        var complexity = when {
            request.length >= 4_000 -> 0.9
            request.length >= 1_500 -> 0.75
            request.length >= 600 -> 0.55
            request.length >= 200 -> 0.35
            else -> 0.15
        }
        val multipleConditions = containsAny(
            text, words, "mehrere", "vergleich", "widerspruch", "constraint", "bedingung", "komplex"
        )
        if (multipleConditions) complexity += 0.15
        if (plan && multipleConditions) complexity += 0.1
        if (research || verify) complexity += 0.1
        complexity = complexity.coerceIn(0.0, 1.0)

        val expectedToolUse = device && containsAny(
            text, words, "öffne", "starte", "stoppe", "install", "lösche", "suche", "zeige", "prüfe", "finde"
        )

        return IntelligenceTaskAssessment(
            taskClass = taskClass,
            capability = capability,
            domain = domain,
            complexity = complexity,
            freshnessNeeded = research,
            expectedToolUse = expectedToolUse,
            verifyExistingAnswer = verify,
            requestedStrategy = requestedStrategy
        )
    }

    /** Multi-word needles use substring match; short words must match exactly; longer ones as word prefix. */
    private fun containsAny(text: String, words: List<String>, vararg needles: String): Boolean =
        needles.any { needle ->
            when {
                needle.contains(' ') -> text.contains(needle)
                needle in shortPrefixStems -> words.any { it.startsWith(needle) }
                needle.length <= EXACT_WORD_MAX_LENGTH -> words.any { it == needle }
                else -> words.any { it.startsWith(needle) }
            }
        }
}

enum class ReasoningPassRole { CANDIDATE, CRITIC, VERIFIER, JUDGE }

data class ReasoningPassRequest(
    val taskId: String,
    val role: ReasoningPassRole,
    val prompt: String,
    val maxOutputChars: Int,
    val deadlineMillis: Long
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(prompt.isNotBlank() && prompt.length <= 24_000)
        require(maxOutputChars in 256..12_000)
        require(deadlineMillis >= 0)
    }
}

data class ReasoningPassResult(
    val modelId: String,
    val text: String,
    val latencyMillis: Long,
    val attemptedToolProposals: Int = 0
) {
    init {
        require(modelId.isNotBlank())
        require(text.isNotBlank() && text.length <= 12_000)
        require(latencyMillis >= 0)
        require(attemptedToolProposals >= 0)
    }
}

fun interface ContinuumReasoningAdapter {
    fun execute(request: ReasoningPassRequest): ReasoningPassResult

    /** Optional cancellation for adapters that wait on an asynchronous local runtime. */
    fun cancel() {}
}

data class ContinuumIntelligenceRequest(
    val taskId: String,
    val userRequest: String,
    val compiledContext: String,
    val requestedStrategy: ReasoningStrategy? = null
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(userRequest.isNotBlank() && userRequest.length <= 16_000)
        require(compiledContext.isNotBlank() && compiledContext.length <= 64_000)
    }
}

data class ContinuumIntelligenceResult(
    val assessment: IntelligenceTaskAssessment,
    val strategy: ReasoningStrategy,
    val budget: DeliberationBudget,
    val deliberation: DeliberationResult,
    val modelIds: Set<String>
)

class ContinuumIntelligenceCoordinator(
    private val adapter: ContinuumReasoningAdapter,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val nowNanos: () -> Long = System::nanoTime
) {
    fun run(
        request: ContinuumIntelligenceRequest,
        cancellation: DeliberationCancellation = DeliberationCancellation()
    ): ContinuumIntelligenceResult {
        val assessment = ContinuumTaskClassifier.classify(request.userRequest, request.requestedStrategy)
        val strategy = ReasoningStrategySelector.select(assessment.strategyProfile())
        val budget = DeliberationBudget.forStrategy(strategy)
        val modelIds = linkedSetOf<String>()

        fun invoke(role: ReasoningPassRole, prompt: String): String {
            cancellation.throwIfCancelled()
            val result = adapter.execute(
                ReasoningPassRequest(
                    taskId = request.taskId,
                    role = role,
                    prompt = prompt.take(24_000),
                    maxOutputChars = 8_000,
                    deadlineMillis = nowMillis() + budget.maxWallTimeMillis
                )
            )
            require(result.attemptedToolProposals == 0) {
                "Internal Continuum reasoning passes are data-only; tool proposals are rejected."
            }
            modelIds += result.modelId
            return result.text.take(8_000)
        }

        val orchestrator = BoundedDeliberationOrchestrator(
            generator = DeliberationCandidateGenerator { task, index ->
                invoke(ReasoningPassRole.CANDIDATE, candidatePrompt(request.compiledContext, task, index, strategy))
            },
            critic = DeliberationCritic { task, candidate ->
                invoke(ReasoningPassRole.CRITIC, criticPrompt(request.compiledContext, task, candidate))
            },
            verifier = DeliberationVerifier { task, candidate ->
                invoke(ReasoningPassRole.VERIFIER, verifierPrompt(request.compiledContext, task, candidate))
            },
            judge = DeliberationJudge { task, candidates, critiques, verifications ->
                invoke(
                    ReasoningPassRole.JUDGE,
                    judgePrompt(request.compiledContext, task, candidates, critiques, verifications)
                )
            },
            nowNanos = nowNanos
        )

        val deliberation = orchestrator.run(
            task = request.userRequest,
            strategy = strategy,
            budget = budget,
            cancellation = cancellation
        )
        return ContinuumIntelligenceResult(assessment, strategy, budget, deliberation, modelIds)
    }

    private fun candidatePrompt(
        context: String,
        task: String,
        index: Int,
        strategy: ReasoningStrategy
    ): String = buildString {
        append("You are an internal reasoning worker for one persistent assistant.\n")
        append("You have NO tool authority and must not request shell/device actions.\n")
        append("Produce candidate ").append(index + 1).append(" for strategy ").append(strategy.name).append(".\n")
        append("Separate facts, assumptions, uncertainty, and proposed conclusion.\n\n")
        append("CONTEXT:\n").append(context.take(14_000)).append("\n\n")
        append("TASK:\n").append(task.take(7_000))
    }.take(24_000)

    private fun criticPrompt(context: String, task: String, candidate: DeliberationCandidate): String =
        buildString {
            append("You are the skeptical critic in a data-only reasoning pipeline.\n")
            append("Do not execute or request tools. Find factual gaps, unsupported assumptions, ")
            append("internal contradictions, missing constraints, and failure modes.\n\n")
            append("CONTEXT:\n").append(context.take(10_000)).append("\n\n")
            append("TASK:\n").append(task.take(5_000)).append("\n\n")
            append("CANDIDATE:\n").append(candidate.text.take(7_000))
        }.take(24_000)

    private fun verifierPrompt(context: String, task: String, candidate: DeliberationCandidate): String =
        buildString {
            append("You are the verifier in a data-only reasoning pipeline.\n")
            append("Use only evidence present in the supplied context. Do not invent citations, ")
            append("do not browse, and do not request tools. Mark unsupported claims explicitly.\n\n")
            append("CONTEXT:\n").append(context.take(12_000)).append("\n\n")
            append("TASK:\n").append(task.take(4_000)).append("\n\n")
            append("CANDIDATE:\n").append(candidate.text.take(7_000))
        }.take(24_000)

    private fun judgePrompt(
        context: String,
        task: String,
        candidates: List<DeliberationCandidate>,
        critiques: List<DeliberationCritique>,
        verifications: List<DeliberationVerification>
    ): String = buildString {
        append("You are the final judge for one persistent assistant. No tools.\n")
        append("Prefer supported, internally consistent claims and preserve unresolved uncertainty.\n\n")
        append("CONTEXT:\n").append(context.take(8_000)).append("\n\n")
        append("TASK:\n").append(task.take(4_000)).append("\n\n")
        candidates.forEach {
            append("CANDIDATE ").append(it.id).append(":\n").append(it.text.take(3_500)).append("\n\n")
        }
        critiques.forEach {
            append("CRITIQUE ").append(it.candidateId).append(":\n").append(it.text.take(2_000)).append("\n\n")
        }
        verifications.forEach {
            append("VERIFY ").append(it.candidateId).append(":\n").append(it.text.take(2_000)).append("\n\n")
        }
        append("Return one final answer only.")
    }.take(24_000)
}

