package at.heinz.deviceai.cognitive

import kotlin.math.abs

data class IdentityDriftResult(
    val overallDrift: Double,
    val behaviorDrift: Double,
    val capabilityDrift: Double,
    val communicationDrift: Double,
    val withinAllowedRange: Boolean
)

/**
 * Measures continuity of persistent state between two identity states (0 = identical).
 * Uses only behavior, capability competence and communication profile; no session data.
 * It does not measure consciousness or personhood.
 */
object IdentityDrift {
    const val W_BEHAVIOR = 0.5
    const val W_CAPABILITY = 0.3
    const val W_COMMUNICATION = 0.2

    fun compute(genome: CognitiveGenome, from: IdentityState, to: IdentityState): IdentityDriftResult {
        val b = BehaviorTrait.values().map { abs(from.behaviorProfile[it] - to.behaviorProfile[it]) }.average()
        val c = Capability.values().map { abs(from.selfModel[it].competence - to.selfModel[it].competence) }.average()
        val keys = from.communicationProfile.keys + to.communicationProfile.keys
        val m = if (keys.isEmpty()) 0.0
        else keys.count { from.communicationProfile[it] != to.communicationProfile[it] }.toDouble() / keys.size
        val overall = W_BEHAVIOR * b + W_CAPABILITY * c + W_COMMUNICATION * m
        return IdentityDriftResult(overall, b, c, m, overall <= genome.updateLimits.maxDriftPerGeneration)
    }
}
