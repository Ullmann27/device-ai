package at.heinz.deviceai.cognitive

/** Estimated capability domains. This models competence, not consciousness. */
enum class Capability {
    ANDROID_DEBUGGING, CODING, MATHEMATICS, RESEARCH, DOCUMENT_ANALYSIS, PLANNING,
    FACTUAL_ACCURACY, VISUAL_REASONING, SOURCE_EVALUATION, TOOL_USE
}

data class CapabilityEvidence(
    val taskId: String,
    val capability: Capability,
    val predictedConfidence: Double,
    val actualScore: Double,
    val evaluator: String,
    val timestamp: Long
)

/**
 * competence: smoothed actual score. calibrationError: smoothed |predicted - actual|.
 * confidence: how far the system's own predictions can be trusted (1 - calibrationError);
 * it is NOT certainty and is 0 until at least one task was evaluated.
 */
data class CapabilityEstimate(
    val competence: Double = 0.5,
    val calibrationError: Double = 0.5,
    val evaluatedTaskCount: Int = 0,
    val knownLimitations: List<String> = emptyList()
) {
    init {
        require(competence.isUnit() && calibrationError.isUnit() && evaluatedTaskCount >= 0)
    }
    val confidence: Double get() = if (evaluatedTaskCount == 0) 0.0 else 1.0 - calibrationError
}

/** Immutable, explainable self-model. Starts uncertain. */
class SelfModel private constructor(
    private val estimates: Map<Capability, CapabilityEstimate>,
    private val log: List<CapabilityEvidence>
) {
    operator fun get(c: Capability): CapabilityEstimate = estimates.getValue(c)
    fun estimates(): Map<Capability, CapabilityEstimate> = estimates
    fun evidenceLog(): List<CapabilityEvidence> = log

    /** Answers "why does the system believe this about capability c?" */
    fun explain(c: Capability): List<CapabilityEvidence> = log.filter { it.capability == c }

    fun withLimitation(c: Capability, limitation: String): SelfModel =
        SelfModel(estimates + (c to this[c].copy(knownLimitations = this[c].knownLimitations + limitation)), log)

    /** Small, gradual update; [rate] comes from the genome's learning rate. */
    fun withEvidence(e: CapabilityEvidence, rate: Double): SelfModel {
        require(e.taskId.isNotBlank() && e.evaluator.isNotBlank()) { "taskId/evaluator required" }
        require(e.predictedConfidence.isUnit() && e.actualScore.isUnit()) { "scores must be finite within 0..1" }
        require(rate.isUnit()) { "invalid rate" }
        require(log.none { it.taskId == e.taskId && it.capability == e.capability }) { "duplicate evidence ${e.taskId}" }
        val old = this[e.capability]
        val err = kotlin.math.abs(e.predictedConfidence - e.actualScore)
        val updated = old.copy(
            competence = old.competence + rate * (e.actualScore - old.competence),
            calibrationError = old.calibrationError + rate * (err - old.calibrationError),
            evaluatedTaskCount = old.evaluatedTaskCount + 1
        )
        return SelfModel(estimates + (e.capability to updated), log + e)
    }

    fun canonical(): String = Capability.values().joinToString(";") {
        val s = this[it]
        "${it.name}=${canon(s.competence)},${canon(s.calibrationError)},${s.evaluatedTaskCount},${s.knownLimitations.joinToString("/")}"
    } + "#" + log.joinToString(",") { "${it.taskId}:${it.capability.name}:${canon(it.predictedConfidence)}:${canon(it.actualScore)}:${it.evaluator}:${it.timestamp}" }

    override fun equals(other: Any?) = other is SelfModel && other.estimates == estimates && other.log == log
    override fun hashCode() = estimates.hashCode() * 31 + log.hashCode()

    companion object {
        fun uncertain() = SelfModel(Capability.values().associateWith { CapabilityEstimate() }, emptyList())

        internal fun restored(
            estimates: Map<Capability, CapabilityEstimate>,
            evidence: List<CapabilityEvidence>
        ): SelfModel {
            require(estimates.keys == Capability.values().toSet()) { "incomplete self-model estimates" }
            require(evidence.map { it.taskId to it.capability }.distinct().size == evidence.size) {
                "duplicate self-model evidence"
            }
            require(evidence.all {
                it.taskId.isNotBlank() && it.evaluator.isNotBlank() &&
                    it.predictedConfidence.isUnit() && it.actualScore.isUnit()
            }) { "invalid self-model evidence" }
            return SelfModel(estimates.toMap(), evidence.toList())
        }
    }
}
