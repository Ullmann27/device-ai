package at.heinz.deviceai.cognitive

/** Data-only development workers: nothing in this file executes, writes or applies anything. */

fun utf8Bytes(value: String): Int = value.toByteArray(Charsets.UTF_8).size

enum class DevelopmentRole { DEVELOPER, CRITIC, TEST_DESIGNER, JUDGE }

enum class DevelopmentJobState {
    PENDING, RUNNING_DEVELOPER, RUNNING_CRITIC, RUNNING_TEST_DESIGNER, RUNNING_JUDGE,
    COMPLETED, FAILED, CANCELLED, TIMED_OUT;

    val terminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMED_OUT
}

enum class DevelopmentFailureKind {
    NO_MODEL, CONTEXT_TOO_LARGE, INVALID_INPUT, MODEL_FAILURE, OUTPUT_TOO_LARGE,
    MALFORMED_OUTPUT, POLICY_REJECTED, TOOL_PROPOSAL, BUDGET_EXHAUSTED
}

/** Hard ceilings the model can never raise; a caller may only choose smaller values. */
data class DevelopmentLimits(
    val maxModelCalls: Int = HARD_MAX_CALLS,
    val maxDurationMillis: Long = HARD_MAX_DURATION_MILLIS,
    val maxSourceBytes: Int = DEFAULT_SOURCE_BYTES,
    val maxOutputBytesPerCall: Int = DEFAULT_OUTPUT_BYTES,
    val maxCumulativePromptBytes: Int = HARD_MAX_CUMULATIVE_PROMPT_BYTES
) {
    init {
        require(maxModelCalls in 1..HARD_MAX_CALLS)
        require(maxDurationMillis in 1..HARD_MAX_DURATION_MILLIS)
        require(maxSourceBytes in 1..HARD_MAX_SOURCE_BYTES)
        require(maxOutputBytesPerCall in 256..HARD_MAX_OUTPUT_BYTES)
        require(maxCumulativePromptBytes in 1..HARD_MAX_CUMULATIVE_PROMPT_BYTES)
    }

    companion object {
        const val HARD_MAX_CALLS = 4
        const val HARD_MAX_DURATION_MILLIS = 60_000L
        const val HARD_MAX_SOURCE_BYTES = 8_000
        const val DEFAULT_SOURCE_BYTES = 4_000
        const val HARD_MAX_OUTPUT_BYTES = 6_000
        const val DEFAULT_OUTPUT_BYTES = 4_000
        const val HARD_MAX_CUMULATIVE_PROMPT_BYTES = 56_000
        const val MAX_PROMPT_CHARS = 16_000
        const val MAX_GOAL_BYTES = 2_000
        const val MAX_SOURCES = 4
    }
}

/** Relative-path rules shared by source selection and proposed edits. Returns an error or null. */
object DevelopmentPathPolicy {
    private val sensitiveSegments = setOf(".git", ".github", ".gradle", "gradle", "workflows", ".ssh", ".gnupg")
    private val sensitiveFragments = listOf(
        "toolgate", "identity", "governance", "credential", "secret", "keystore", "signing",
        "securetoken", "id_rsa", "id_ed25519", "privatekey", "private_key"
    )
    private val sensitiveNames = setOf(
        "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        "gradle.properties", "gradlew", "gradlew.bat", "local.properties", "render.yaml",
        ".env", "dockerfile"
    )
    private val sensitiveExtensions = setOf(".jks", ".keystore", ".pem", ".p12", ".pfx", ".key", ".gpg")

    fun validate(path: String): String? {
        if (path.isEmpty() || path.length > 200) return "Path must be 1..200 characters."
        if (path.startsWith("/")) return "Absolute paths are not allowed."
        if (path.contains('\\')) return "Backslashes are not allowed in paths."
        if (path.any { it.code < 0x20 || it.code == 0x7f }) return "Control characters are not allowed in paths."
        if (path.contains(':')) return "Drive or scheme prefixes are not allowed in paths."
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return "Path traversal is not allowed."
        val lower = path.lowercase()
        if (segments.any { it.lowercase() in sensitiveSegments }) return "Sensitive location is not allowed in this slice."
        val name = segments.last().lowercase()
        if (name in sensitiveNames || sensitiveExtensions.any { name.endsWith(it) } ||
            sensitiveFragments.any { lower.contains(it) }) {
            return "Sensitive file is not allowed in this slice."
        }
        return null
    }
}

/** Untrusted source text plus its SHA-256; the hash is verified at construction. */
data class SourceSnapshot(val path: String, val content: String, val sha256: String) {
    init {
        DevelopmentPathPolicy.validate(path)?.let { throw IllegalArgumentException("$path: $it") }
        require(content.none { it == '\u0000' }) { "$path: NUL characters are not allowed." }
        require(sha256 == sha256Hex(content)) { "$path: SHA-256 does not match content." }
    }

    companion object {
        fun of(path: String, content: String) = SourceSnapshot(path, content, sha256Hex(content))
    }
}

data class DevelopmentTask(
    val taskId: String,
    val goal: String,
    val sources: List<SourceSnapshot>,
    val limits: DevelopmentLimits = DevelopmentLimits()
) {
    init {
        require(taskId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(goal.isNotBlank() && utf8Bytes(goal) <= DevelopmentLimits.MAX_GOAL_BYTES) { "Goal is empty or too long." }
        require(sources.isNotEmpty()) { "Select at least one source file." }
        require(sources.size <= DevelopmentLimits.MAX_SOURCES) { "At most ${DevelopmentLimits.MAX_SOURCES} source files." }
        require(sources.map { it.path }.toSet().size == sources.size) { "Duplicate source paths." }
        require(sources.sumOf { utf8Bytes(it.content) } <= limits.maxSourceBytes) {
            "Selected sources exceed ${limits.maxSourceBytes} UTF-8 bytes; select a smaller source set."
        }
    }
}

data class ProposedEdit(
    val path: String,
    val expectedBaseSha256: String,
    val replacement: String,
    val rationale: String
)

enum class FindingSeverity { INFO, WARNING, BLOCKER }
enum class CriticVerdict { ACCEPT, REVISE, REJECT }
enum class JudgeDecision { RECOMMEND, NEEDS_CHANGES, REJECT }

data class DeveloperResult(val summary: String, val edits: List<ProposedEdit>, val notes: List<String>)
data class CriticFinding(val severity: FindingSeverity, val message: String)
data class CriticResult(val verdict: CriticVerdict, val findings: List<CriticFinding>)
data class SuggestedTest(val name: String, val description: String)
data class TestDesignResult(val tests: List<SuggestedTest>)
data class JudgeResult(val decision: JudgeDecision, val rationale: String)

/** Capabilities the first slice deliberately does not have; every one is reported as not executed. */
object NotExecuted {
    val items = listOf(
        "Edits applied to any file",
        "Code compiled",
        "Generated tests executed",
        "APK built or installed",
        "Changes pushed to GitHub"
    )
    /** The job is recorded in the development backlog (DeviceDevelopmentBacklog), not the proposal itself. */
    const val RECOVERY = "Job recorded in the development backlog: an app restart mid-job returns it to READY when its lease ends; " +
        "the proposal waits there (VERIFYING) for executed test evidence."
}

data class DevelopmentProposal(
    val taskId: String,
    val summary: String,
    val edits: List<ProposedEdit>,
    val notes: List<String>,
    val critic: CriticResult,
    val suggestedTests: List<SuggestedTest>,
    val judge: JudgeResult,
    val modelIds: Set<String>,
    val modelCalls: Int,
    val patchPreview: String
) {
    fun render(): String = buildString {
        appendLine("DEVELOPMENT PROPOSAL (review only) · task $taskId")
        appendLine("Judge: ${judge.decision} — ${judge.rationale}")
        appendLine("Critic: ${critic.verdict}")
        critic.findings.forEach { appendLine("  [${it.severity}] ${it.message}") }
        appendLine("Summary: $summary")
        notes.forEach { appendLine("  note: $it") }
        appendLine("Suggested tests (NOT EXECUTED, suggestions only):")
        suggestedTests.forEach { appendLine("  - ${it.name}: ${it.description}") }
        appendLine("NOT EXECUTED:")
        NotExecuted.items.forEach { appendLine("  - $it") }
        appendLine(NotExecuted.RECOVERY)
        appendLine("Model calls: $modelCalls · models: ${modelIds.sorted().joinToString()}")
        appendLine()
        append(patchPreview)
    }
}

data class DevelopmentFailure(val kind: DevelopmentFailureKind, val message: String)

data class DevelopmentOutcome(
    val state: DevelopmentJobState,
    val proposal: DevelopmentProposal? = null,
    val failure: DevelopmentFailure? = null,
    val modelCallsUsed: Int = 0
) {
    init {
        require(state.terminal)
        require((state == DevelopmentJobState.COMPLETED) == (proposal != null))
    }
}
