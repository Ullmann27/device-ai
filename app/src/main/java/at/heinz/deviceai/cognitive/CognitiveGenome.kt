package at.heinz.deviceai.cognitive

import java.security.MessageDigest

fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Stable foundation. Changes only via a new genome and a new identity generation. */
data class CognitiveGenome(
    val identityId: String,
    val architectureVersion: Int = 1,
    val operatorAuthorityPolicy: String = "operator_is_highest_authority",
    val learningPolicy: String = "propose_only_operator_approves",
    val memoryPolicy: String = "append_only_with_provenance",
    val evidencePolicy: String = "claims_require_evidence",
    val sourcePolicy: String = "trust_unknown_until_observed",
    val riskPolicy: String = "confirm_risky_deny_forbidden",
    val behaviorBounds: Map<String, Double> = mapOf("min" to 0.0, "max" to 1.0),
    val learningRates: Map<String, Double> = mapOf("reputation" to 0.1, "selfModel" to 0.05),
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
        require(!allowSelfReplication && !allowHiddenPersistence && !allowShutdownEvasion) {
            "Forbidden capabilities cannot be enabled"
        }
    }

    fun canonical(): String = listOf(
        identityId, architectureVersion, operatorAuthorityPolicy, learningPolicy, memoryPolicy,
        evidencePolicy, sourcePolicy, riskPolicy, behaviorBounds.toSortedMap(), learningRates.toSortedMap(),
        toolPolicy, modelSelectionPrinciples, identityUpdatePolicy, checkpointPolicy
    ).joinToString("|")

    fun hash(): String = sha256Hex(canonical())
}
