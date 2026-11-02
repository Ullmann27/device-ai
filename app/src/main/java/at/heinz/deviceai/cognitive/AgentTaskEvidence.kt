package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus

/** Informational states, never capabilities or grants. Unobserved voice phases are not emitted. */
enum class CognitiveWorkState {
    LISTENING, UNDERSTANDING, PLANNING, WAITING_FOR_APPROVAL, EXECUTING,
    VERIFYING, SPEAKING, BLOCKED, COMPLETED
}

/** Cross-thread notifications: consumers order by (taskId, attempt, sequence), not arrival. */
data class AgentCognitiveEvent(
    val sequence: Int,
    val taskId: String,
    val attempt: Int,
    val state: CognitiveWorkState,
    val elapsedMillis: Long,
    val callId: String? = null
)

/** A fresh returned observation is distinct from a persisted/replayed execution status.
 * Hashes identify bytes, not independent semantic truth. No arguments or output text retained.
 */
data class AgentToolEvidence(
    val callId: String,
    val toolName: String,
    val argumentFingerprint: String,
    val approvalRequested: Boolean,
    val ownerApproved: Boolean?,
    val status: ToolExecutionStatus,
    val outputSha256: String?,
    val freshOutput: Boolean
)

/** Reuses the trusted existing call namespace as task identity. Not a second task store.
 * outcomeVerified means the caller's outcome evaluator accepted fresh observations;
 * it does not imply an independent verifier, Android execution or skill promotion.
 */
data class AgentTaskEvidence(
    val taskId: String,
    val attempt: Int,
    val requestSha256: String,
    val plannedToolNames: List<String>,
    val calls: List<AgentToolEvidence>,
    val events: List<AgentCognitiveEvent>,
    val evaluatorSucceeded: Boolean,
    val evaluationConfirmsOutcome: Boolean,
    val outcomeVerified: Boolean,
    val learningCandidateExperienceId: String?
)
