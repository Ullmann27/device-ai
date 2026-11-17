package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.AgentFollowupProposal
import at.heinz.deviceai.cognitive.DeliberationCancellation
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

enum class ChiefFollowupDecisionCode {
    OWNER_APPROVAL_REQUIRED, SOURCE_NOT_COMPLETE, SOURCE_CHANGED, NO_PROPOSAL,
    UNSUPPORTED_CAPABILITY, TOOLS_NOT_AUTHORIZED, MODEL_UNAVAILABLE, MODEL_BUDGET_UNAVAILABLE,
    DUPLICATE_GOAL, ALREADY_SUBMITTED, TEAM_BUSY, HISTORY_FULL
}

data class ChiefFollowupDecision(val code: ChiefFollowupDecisionCode, val review: ChiefFollowupReview? = null)

/** Bound draft, never execution permission. */
class ChiefFollowupReview internal constructor(
    internal val authority: Any,
    internal val source: ChiefTeamTask,
    val sourceStage: String,
    val proposal: AgentFollowupProposal,
    val teamRevision: Long,
    internal val admissionRevision: Long,
    internal val ownerSession: String?,
    internal val modelSha256: String
) {
    val requestId: String = requestId(source.id, sourceStage,
        source.responses.single { it.stage == sourceStage }.outputSha256)
    private val revoked = AtomicBoolean()
    private val execution = AtomicReference<DeliberationCancellation?>()

    fun revoke() {
        revoked.set(true)
        execution.get()?.cancel()
    }

    internal fun current(): Boolean = !revoked.get()

    internal fun claim(cancellation: DeliberationCancellation) {
        check(current()) { "BOT_FOLLOWUP_EXPIRED" }
        check(execution.compareAndSet(null, cancellation)) { "BOT_FOLLOWUP_ALREADY_SUBMITTED" }
        if (!current()) cancellation.cancel()
        cancellation.throwIfCancelled()
    }

    internal companion object {
        fun requestId(sourceId: String, stage: String, hash: String): String = "team-" +
            UUID.nameUUIDFromBytes("$sourceId\n$stage\n$hash".toByteArray(Charsets.UTF_8))
    }
}
