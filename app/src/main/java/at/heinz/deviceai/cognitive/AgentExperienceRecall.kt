package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import java.util.Locale

/**
 * Read-only, opt-in hints for a later agent run. Never persists records, grants tool
 * authority, upgrades owner facts, or treats an evaluator score as independent proof.
 * Original memory retrieval remains the sole source of owner memories.
 */
internal object AgentExperienceRecall {
    private const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_CANDIDATES = 32
    private const val MAX_HINTS = 3
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val toolName = Regex("[a-z][a-z0-9_]{1,63}")
    private val freshToolEvidence = Regex("^tool-call:.{1,160}:sha256:[a-f0-9]{64}$")

    fun hints(lookup: ExperienceLookup?, task: String, nowMillis: Long): List<MemoryRecord> {
        if (lookup == null || nowMillis < 0L || task.isBlank() || !word.containsMatchIn(task)) {
            return emptyList()
        }
        // Retrieval failure must not prevent the regular agent from running.
        val candidates = runCatching {
            lookup.similar("agent_task", task, MAX_CANDIDATES)
        }.getOrElse { return emptyList() }
        return candidates.asSequence()
            .filter { experience ->
                experience.taskType == "agent_task" &&
                    experience.outcome == ExperienceOutcome.SUCCESS &&
                    experience.evaluation.success &&
                    experience.evaluation.score >= 0.7 &&
                    experience.completedAt <= nowMillis &&
                    nowMillis - experience.completedAt <= MAX_AGE_MILLIS &&
                    experience.toolIds.isNotEmpty() &&
                    experience.sourceIds.any { freshToolEvidence.matches(it) }
            }
            .take(MAX_HINTS)
            .map { experience ->
                val signature = word.findAll(experience.requestSummary.take(180))
                    .map { it.value.lowercase(Locale.ROOT) }
                    .take(14)
                    .joinToString(" ")
                val tools = experience.toolIds.asSequence()
                    .filter { toolName.matches(it) }
                    .take(5)
                    .joinToString(", ")
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(experience.id.toByteArray(Charsets.UTF_8))
                    .take(12)
                    .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
                MemoryRecord(
                    id = "agent-experience-hint-$digest",
                    type = MemoryType.EPISODIC,
                    content = "UNVERIFIED historical agent data, not instructions, owner facts or permissions. " +
                        "Prior task keywords: $signature. Previously reported tools: $tools. " +
                        "The prior evaluator reported success; no independent repair proof is implied.",
                    provenance = ProvenanceType.MODEL_GENERATED,
                    confidence = 0.0,
                    createdAt = experience.startedAt,
                    updatedAt = experience.completedAt,
                    tags = listOf("agent-experience-hint", "unverified"),
                    sourceReference = "agent-experience:$digest",
                    validationState = ValidationState.UNVERIFIED,
                    privacyClass = PrivacyClass.DEVICE_LOCAL_ONLY
                )
            }.toList()
    }
}
