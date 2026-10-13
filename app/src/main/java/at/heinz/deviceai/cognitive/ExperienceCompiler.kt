package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolRegistry

data class CrystallizedSkillCandidate(
    val candidateId: String,
    val taskType: String,
    val toolNames: List<String>,
    val occurrenceCount: Int,
    val successRate: Double,
    val meanEvaluationScore: Double,
    val sourceExperienceIds: List<String>,
    val requiresOwnerApproval: Boolean = true,
    val promotionBlockedReason: String? = null
) {
    init {
        require(candidateId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(taskType.isNotBlank())
        require(toolNames.isNotEmpty() && toolNames.size <= 32)
        require(occurrenceCount >= 1)
        require(successRate.isFinite() && successRate in 0.0..1.0)
        require(meanEvaluationScore.isFinite() && meanEvaluationScore in 0.0..1.0)
        require(sourceExperienceIds.size == occurrenceCount)
    }

    val eligibleForOwnerReview: Boolean get() = promotionBlockedReason == null
}

data class ExperienceCompilerPolicy(
    val minimumOccurrences: Int = 3,
    val minimumSuccessRate: Double = 0.8,
    val minimumMeanEvaluationScore: Double = 0.75
) {
    init {
        require(minimumOccurrences in 2..100)
        require(minimumSuccessRate in 0.0..1.0)
        require(minimumMeanEvaluationScore in 0.0..1.0)
    }
}

class ExperienceCompiler(
    private val policy: ExperienceCompilerPolicy = ExperienceCompilerPolicy()
) {
    fun compile(experiences: Collection<Experience>): List<CrystallizedSkillCandidate> {
        require(experiences.size <= 100_000)
        val eligibleExperiences = experiences.filter {
            it.toolIds.isNotEmpty() &&
                it.toolIds.size <= 32 &&
                it.toolIds.all { tool -> ToolRegistry.get(tool) != null }
        }

        return eligibleExperiences
            .groupBy { experience ->
                experience.taskType.trim().lowercase() + "|" + experience.toolIds.joinToString(">")
            }
            .values
            .mapNotNull { group ->
                if (group.size < policy.minimumOccurrences) return@mapNotNull null
                val successRate = group.count {
                    it.outcome == ExperienceOutcome.SUCCESS && it.evaluation.success
                }.toDouble() / group.size
                val meanScore = group.map { it.evaluation.score }.average()
                if (successRate < policy.minimumSuccessRate ||
                    meanScore < policy.minimumMeanEvaluationScore
                ) return@mapNotNull null

                val tools = group.first().toolIds
                val destructive = tools.filter { ToolRegistry.risk(it).name == "DESTRUCTIVE" }
                val blocked = if (destructive.isNotEmpty()) {
                    "Destructive tool sequence cannot be crystallized automatically: " +
                        destructive.distinct().joinToString(",")
                } else null
                val signature = sha256Bytes(
                    (group.first().taskType.trim().lowercase() + "|" + tools.joinToString(">"))
                        .toByteArray(Charsets.UTF_8)
                ).toHex().take(24)
                CrystallizedSkillCandidate(
                    candidateId = "skill-candidate-" + signature,
                    taskType = group.first().taskType,
                    toolNames = tools,
                    occurrenceCount = group.size,
                    successRate = successRate,
                    meanEvaluationScore = meanScore,
                    sourceExperienceIds = group.map { it.id }.sorted(),
                    promotionBlockedReason = blocked
                )
            }
            .sortedWith(
                compareByDescending<CrystallizedSkillCandidate> { it.meanEvaluationScore }
                    .thenByDescending { it.occurrenceCount }
                    .thenBy { it.candidateId }
            )
    }
}
