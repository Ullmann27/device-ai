package at.heinz.deviceai.cognitive

/**
 * Read-only maturity assessment over the existing ExperienceStore.
 * It does not create a second store or promote a skill.
 */
internal object ExperiencePatternMaturity {
    enum class Maturity {
        NO_EVIDENCE,
        QUARANTINED_OBSERVATION,
        VALIDATED_PATTERN,
        KNOWN_TRAP
    }

    data class Assessment(
        val maturity: Maturity,
        val taskType: String,
        val toolSequence: List<String>,
        val observations: Int,
        val verifiedSuccesses: Int,
        val failures: Int,
        val meanScore: Double,
        val sourceExperienceIds: List<String>,
        val contextDiversity: Int = 0
    )

    fun assess(
        experiences: Collection<Experience>,
        taskType: String,
        toolSequence: List<String>,
        minimumValidatedRuns: Int = 3
    ): Assessment {
        require(taskType.isNotBlank())
        require(toolSequence.size <= 32 && toolSequence.none(String::isBlank))
        require(minimumValidatedRuns in 2..20)

        val group = experiences.distinctBy { it.id }.filter {
            it.taskType.equals(taskType, ignoreCase = true) && it.toolIds == toolSequence
        }
        if (group.isEmpty()) return Assessment(
            Maturity.NO_EVIDENCE, taskType, toolSequence, 0, 0, 0, 0.0, emptyList(), 0)

        val verified = group.filter {
            it.outcome == ExperienceOutcome.SUCCESS &&
                it.evaluation.success &&
                it.evaluation.score >= MIN_SUCCESS_SCORE &&
                it.sourceIds.isNotEmpty()
        }
        val failed = group.filter {
            it.outcome == ExperienceOutcome.FAILURE || !it.evaluation.success
        }
        val verifiedSuccesses = verified.size
        val failures = failed.size
        val successDiversity = verified.map { contextSignature(it.requestSummary) }.distinct().size
        val failureDiversity = failed.map { contextSignature(it.requestSummary) }.distinct().size
        val mean = group.map { it.evaluation.score }.average()

        val maturity = when {
            failures >= 2 && verifiedSuccesses == 0 && failureDiversity >= MIN_CONTEXT_DIVERSITY ->
                Maturity.KNOWN_TRAP
            verifiedSuccesses >= minimumValidatedRuns &&
                successDiversity >= MIN_CONTEXT_DIVERSITY &&
                verifiedSuccesses.toDouble() / group.size >= MIN_SUCCESS_RATE &&
                mean >= MIN_SUCCESS_SCORE -> Maturity.VALIDATED_PATTERN
            else -> Maturity.QUARANTINED_OBSERVATION
        }
        return Assessment(
            maturity, group.first().taskType, toolSequence, group.size,
            verifiedSuccesses, failures, mean, group.map { it.id }.sorted(),
            contextDiversity = maxOf(successDiversity, failureDiversity)
        )
    }

    private fun contextSignature(value: String): String =
        value.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)

    private const val MIN_CONTEXT_DIVERSITY = 2
    private const val MIN_SUCCESS_RATE = .80
    private const val MIN_SUCCESS_SCORE = .75
}
