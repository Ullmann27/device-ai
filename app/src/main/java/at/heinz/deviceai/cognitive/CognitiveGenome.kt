package at.heinz.deviceai.cognitive

import java.security.MessageDigest

fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Deterministic double formatting for canonical serialization (normalizes -0.0). */
internal fun canon(d: Double): String = (d + 0.0).toString()

internal fun Double.isUnit(): Boolean = isFinite() && this in 0.0..1.0

/** Allowed range for every behavior trait. */
data class BehaviorBounds(val min: Double = 0.05, val max: Double = 0.95) {
    init {
        require(min.isUnit() && max.isUnit() && min <= max) { "Invalid behavior bounds $min..$max" }
    }

    fun contains(v: Double): Boolean = v.isFinite() && v in min..max
    fun clamp(v: Double): Double = v.coerceIn(min, max)
}

/** Limits for persistent behavior updates and identity drift. */
data class UpdateLimits(
    /** Maximum absolute trait change per accepted update. */
    val maxBehaviorStep: Double = 0.05,
    /** Minimum aggregated evidence strength for a behavior update. */
    val minEvidenceStrength: Double = 0.2,
    /** Maximum overall drift between consecutive generations. */
    val maxDriftPerGeneration: Double = 0.1
) {
    init {
        require(maxBehaviorStep.isUnit() && minEvidenceStrength.isUnit() && maxDriftPerGeneration.isUnit())
    }
}

/**
 * Stable foundation. Changes only via a new genome and a new identity generation.
 * Its [hash] is an integrity hash (tamper detection), not a cryptographic signature.
 */
data class CognitiveGenome(
    val identityId: String,
    val architectureVersion: Int = 1,
    val operatorAuthorityPolicy: String = "operator_is_highest_authority",
    val learningPolicy: String = "propose_only_operator_approves",
    val memoryPolicy: String = "append_only_with_provenance",
    val evidencePolicy: String = "claims_require_evidence",
    val sourcePolicy: String = "trust_unknown_until_observed",
    val riskPolicy: String = "confirm_risky_deny_forbidden",
    val behaviorBounds: BehaviorBounds = BehaviorBounds(),
    val updateLimits: UpdateLimits = UpdateLimits(),
    val learningRates: Map<String, Double> = mapOf("behavior" to 0.1, "selfModel" to 0.1),
    val toolPolicy: String = "registry_only_risk_gated",
    val modelSelectionPrinciples: String = "provider_agnostic_swappable",
    val identityUpdatePolicy: String = "new_generation_never_overwrite",
    val checkpointPolicy: String = "checkpoint_every_generation",
    /** Research-safety invariants; always false, never configurable. */
    val allowSelfReplication: Boolean = false,
    val allowHiddenPersistence: Boolean = false,
    val allowShutdownEvasion: Boolean = false
) {
    init {
        require(identityId.isNotBlank()) { "identityId must not be blank" }
        require(!allowSelfReplication && !allowHiddenPersistence && !allowShutdownEvasion) {
            "Forbidden capabilities cannot be enabled"
        }
        require(learningRates.values.all { it.isUnit() }) { "Learning rates must be finite within 0..1" }
    }

    fun learningRate(name: String, default: Double = 0.1): Double = learningRates[name] ?: default

    fun canonical(): String = listOf(
        identityId, architectureVersion, operatorAuthorityPolicy, learningPolicy, memoryPolicy,
        evidencePolicy, sourcePolicy, riskPolicy,
        "${canon(behaviorBounds.min)}..${canon(behaviorBounds.max)}",
        "${canon(updateLimits.maxBehaviorStep)},${canon(updateLimits.minEvidenceStrength)},${canon(updateLimits.maxDriftPerGeneration)}",
        learningRates.toSortedMap().entries.joinToString(",") { "${it.key}=${canon(it.value)}" },
        toolPolicy, modelSelectionPrinciples, identityUpdatePolicy, checkpointPolicy
    ).joinToString("|")

    fun hash(): String = sha256Hex(canonical())
}
