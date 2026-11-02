package at.heinz.deviceai.cognitive

enum class StateTier { SEED, CRITICAL, EVIDENCE, CACHE }
enum class ReplicaHealth { AVAILABLE, MISSING, CORRUPT, UNKNOWN }
enum class RecoverySeverity { OK, DEGRADED, BLOCKED }

data class ReplicaPolicy(
    val tier: StateTier,
    val requiredHealthyReplicas: Int,
    val desiredReplicas: Int,
    val reconstructible: Boolean
) {
    init {
        require(requiredHealthyReplicas in 0..8)
        require(desiredReplicas in requiredHealthyReplicas..8)
        require(tier !in setOf(StateTier.SEED, StateTier.CRITICAL) || requiredHealthyReplicas >= 1)
    }

    companion object {
        fun defaults() = listOf(
            ReplicaPolicy(StateTier.SEED, 1, 2, false),
            ReplicaPolicy(StateTier.CRITICAL, 1, 2, false),
            ReplicaPolicy(StateTier.EVIDENCE, 1, 2, true),
            ReplicaPolicy(StateTier.CACHE, 0, 1, true)
        )
    }
}

data class ReplicaDescriptor(
    val replicaId: String,
    val objectId: ContinuumObjectId,
    val tier: StateTier,
    val storeId: String,
    val health: ReplicaHealth,
    val reconstructible: Boolean
) {
    init {
        require(replicaId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
        require(storeId.matches(Regex("[A-Za-z0-9._:-]{1,128}")))
    }
}

data class RecoveryFinding(
    val tier: StateTier,
    val severity: RecoverySeverity,
    val healthyReplicas: Int,
    val requiredHealthyReplicas: Int,
    val message: String
)

data class CognitiveRaidAssessment(
    val severity: RecoverySeverity,
    val findings: List<RecoveryFinding>
)

object CognitiveRaidPlanner {
    fun assess(
        replicas: List<ReplicaDescriptor>,
        policies: List<ReplicaPolicy> = ReplicaPolicy.defaults()
    ): CognitiveRaidAssessment {
        require(replicas.size <= 4_096)
        require(policies.map { it.tier }.distinct().size == policies.size)
        val findings = policies.map { policy ->
            val tierReplicas = replicas.filter { it.tier == policy.tier }
            val healthy = tierReplicas.count { it.health == ReplicaHealth.AVAILABLE }
            val severity = when {
                healthy >= policy.requiredHealthyReplicas -> RecoverySeverity.OK
                policy.reconstructible && healthy == 0 -> RecoverySeverity.DEGRADED
                else -> RecoverySeverity.BLOCKED
            }
            val message = when (severity) {
                RecoverySeverity.OK -> policy.tier.name + ": replica requirement satisfied."
                RecoverySeverity.DEGRADED -> policy.tier.name + ": no healthy replica; reconstruction is required."
                RecoverySeverity.BLOCKED -> policy.tier.name + ": required state is unavailable."
            }
            RecoveryFinding(policy.tier, severity, healthy, policy.requiredHealthyReplicas, message)
        }
        val overall = when {
            findings.any { it.severity == RecoverySeverity.BLOCKED } -> RecoverySeverity.BLOCKED
            findings.any { it.severity == RecoverySeverity.DEGRADED } -> RecoverySeverity.DEGRADED
            else -> RecoverySeverity.OK
        }
        return CognitiveRaidAssessment(overall, findings)
    }
}
