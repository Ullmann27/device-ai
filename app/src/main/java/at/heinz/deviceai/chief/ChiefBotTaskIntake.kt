package at.heinz.deviceai.chief

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.concurrent.TimeUnit

/** An explicit owner UI choice; addressing a bot or generating text never creates one. */
enum class ChiefBotTaskMode { PRIVATE_REPLY, LOCAL_TOOLS, CODING_DRAFT }

data class ChiefBotTaskRequest(
    val agentId: String,
    val profileVersion: Long,
    val text: String,
    val mode: ChiefBotTaskMode
) {
    init {
        require(ChiefBotProfile.ID.matches(agentId) && profileVersion >= 1) { "BOT_TASK_ID_INVALID" }
        require(text.isNotBlank() && text.none {
            it.code in 0..8 || it.code in 11..12 || it.code in 14..31 || it.code == 127
        }) { "BOT_TASK_TEXT_INVALID" }
        require(Charsets.UTF_8.newEncoder().canEncode(text)) { "BOT_TASK_TEXT_INVALID" }
        val bytes = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text)).remaining()
        require(bytes <= MAX_TEXT_BYTES) { "BOT_TASK_TEXT_TOO_LARGE" }
        require(mode != ChiefBotTaskMode.CODING_DRAFT || text.length <= MAX_CODING_CHARACTERS) {
            "BOT_CODING_GOAL_TOO_LARGE"
        }
    }

    companion object {
        const val MAX_TEXT_BYTES = 4_000
        const val MAX_CODING_CHARACTERS = 600
    }
}

/** Loads existing identity off the UI thread. This does not start or authorize a tool or job. */
class ChiefBotTaskIntake(
    private val profiles: ChiefBotProfileStore,
    private val nanoTime: () -> Long = System::nanoTime
) {
    fun prepare(request: ChiefBotTaskRequest, sessionId: String): ChiefBotTaskTicket {
        require(sessionId.isNotBlank() && sessionId.length <= 160 && sessionId.none { it.isISOControl() })
        val profile = profiles.get(request.agentId) ?: error("BOT_TASK_PROFILE_MISSING")
        check(profile.profileVersion == request.profileVersion) { "BOT_TASK_PROFILE_CHANGED" }
        if (request.mode != ChiefBotTaskMode.CODING_DRAFT)
            check(profile.resourceBudget.maxModelCalls > 0) { "BOT_MODEL_CALL_BUDGET_EXHAUSTED" }
        return ChiefBotTaskTicket(request, profile, sessionId, profiles, nanoTime)
    }
}

/**
 * One-shot attribution and resource accounting for the existing owner execution lane.
 * This is not a scheduler, persistent backlog, sandbox or ToolGate grant.
 * PRIVATE_REPLY retains its existing profile budget. Explicit LOCAL_TOOLS is a separate owner
 * task, advertised in the UI as at most four actual model calls, four tools and two minutes.
 */
class ChiefBotTaskTicket internal constructor(
    val request: ChiefBotTaskRequest,
    val profile: ChiefBotProfile,
    private val sessionId: String,
    private val profiles: ChiefBotProfileStore,
    private val nanoTime: () -> Long
) {
    val taskId: String = "bot-task-" + UUID.randomUUID().toString()
    val deadlineMillis: Long = minOf(profile.resourceBudget.deadlineMillis, MAX_OWNER_DEADLINE_MILLIS)
    val maxToolCalls: Int = minOf(profile.resourceBudget.maxToolCalls, MAX_OWNER_TOOL_CALLS)
    private val startedAtNanos = nanoTime()
    private var claimed = false
    private var cancelled = false
    private var ended = false
    private var modelCalls = 0

    @Synchronized fun claim(currentSessionId: String): Boolean {
        if (claimed || cancelled || ended || currentSessionId != sessionId || remainingMillis() <= 0) return false
        claimed = true
        return true
    }

    @Synchronized fun isCurrent(currentSessionId: String): Boolean =
        claimed && !cancelled && !ended && currentSessionId == sessionId && remainingMillis() > 0

    /** Disk-bound check for the executor, never a per-frame UI status read. */
    fun profileCurrent(): Boolean = runCatching { profiles.get(profile.agentId) == profile }.getOrDefault(false)

    @Synchronized fun reserveModelCall(): Long {
        check(!cancelled) { "BOT_TASK_CANCELLED" }
        check(claimed && !ended) { "BOT_TASK_NOT_ACTIVE" }
        val remaining = remainingMillis()
        check(remaining > 0) { "BOT_TASK_DEADLINE_EXCEEDED" }
        val maximum = if (request.mode == ChiefBotTaskMode.LOCAL_TOOLS) MAX_OWNER_MODEL_CALLS
            else profile.resourceBudget.maxModelCalls
        check(modelCalls < maximum) { "BOT_TASK_MODEL_BUDGET_EXHAUSTED" }
        modelCalls++
        return remaining
    }

    fun remainingMillis(): Long {
        val elapsed = nanoTime() - startedAtNanos
        if (elapsed < 0) return 0
        val remaining = TimeUnit.MILLISECONDS.toNanos(deadlineMillis) - elapsed
        return if (remaining <= 0) 0 else TimeUnit.NANOSECONDS.toMillis(remaining)
    }

    @Synchronized fun cancel() { cancelled = true }
    @Synchronized fun finish() { ended = true }

    companion object {
        const val MAX_OWNER_MODEL_CALLS = 4
        const val MAX_OWNER_TOOL_CALLS = 4
        const val MAX_OWNER_DEADLINE_MILLIS = 120_000L
    }
}
