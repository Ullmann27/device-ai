package at.heinz.deviceai.continuum

enum class StateTier { SEED, CRITICAL, EVIDENCE, CACHE }
enum class ReplicaHealth { AVAILABLE, UNAVAILABLE, CORRUPT }

data class ReplicaPolicy(
    val tier: StateTier,
    val requiredHealthyReplicas: Int,
    val requiredForRecovery: Boolean,
    val reconstructible: Boolean
) {
    init { require(requiredHealthyReplicas in 0..8) }
}

data class ReplicaDescriptor(
    val objectId: ContinuumObjectId,
    val tier: StateTier,
    val replicaId: String,
    val health: ReplicaHealth,
    val reconstructible: Boolean,
    val isLocal: Boolean
) {
    init { require(replicaId.matches(Regex("[A-Za-z0-9._-]{1,96}"))) }
}

data class ReplicaRecoveryWarning(
    val objectId: ContinuumObjectId,
    val tier: StateTier,
    val healthyReplicaCount: Int,
    val requiredReplicaCount: Int,
    val message: String
)

data class ReplicaVerificationPlan(
    val localObjectsToVerify: List<ContinuumObjectId>,
    val recoveryWarnings: List<ReplicaRecoveryWarning>,
    val toleratedMissingCacheObjects: List<ContinuumObjectId>
)

object CognitiveRaidPlanner {
    val defaultPolicies = mapOf(
        StateTier.SEED to ReplicaPolicy(StateTier.SEED, 1, requiredForRecovery = true, reconstructible = false),
        StateTier.CRITICAL to ReplicaPolicy(StateTier.CRITICAL, 1, requiredForRecovery = true, reconstructible = false),
        StateTier.EVIDENCE to ReplicaPolicy(StateTier.EVIDENCE, 1, requiredForRecovery = false, reconstructible = true),
        StateTier.CACHE to ReplicaPolicy(StateTier.CACHE, 0, requiredForRecovery = false, reconstructible = true)
    )

    fun plan(
        descriptors: Collection<ReplicaDescriptor>,
        policies: Map<StateTier, ReplicaPolicy> = defaultPolicies,
        requiredObjects: Map<StateTier, Set<ContinuumObjectId>> = emptyMap()
    ): ReplicaVerificationPlan {
        require(descriptors.size <= 16_384)
        require(requiredObjects.values.sumOf { it.size } <= ContinuumStateRoot.MAX_ROOT_OBJECTS)
        require(policies.keys.containsAll(StateTier.values().toSet()))
        require(policies.all { (tier, policy) -> tier == policy.tier })
        val grouped = descriptors.groupBy { it.objectId to it.tier }
        require(grouped.values.all { replicas -> replicas.map { it.replicaId }.distinct().size == replicas.size }) {
            "Duplicate replica descriptors are not allowed."
        }
        val requiredKeys = requiredObjects.flatMap { (tier, ids) -> ids.map { it to tier } }
        require(requiredObjects.keys.all { it in StateTier.values() })
        val verify = descriptors.filter { it.isLocal }.map { it.objectId }.distinct().sortedBy { it.value }
        val warnings = mutableListOf<ReplicaRecoveryWarning>()
        val tolerated = mutableListOf<ContinuumObjectId>()
        (grouped.keys + requiredKeys).distinct().forEach { key ->
            val replicas = grouped[key].orEmpty()
            val policy = policies.getValue(key.second)
            val available = replicas.count { it.health == ReplicaHealth.AVAILABLE }
            if (available < policy.requiredHealthyReplicas && policy.requiredForRecovery) {
                warnings += ReplicaRecoveryWarning(
                    key.first,
                    key.second,
                    available,
                    policy.requiredHealthyReplicas,
                    "Required ${key.second.name.lowercase()} state has fewer healthy replicas than policy requires."
                )
            } else if (available == 0 && key.second == StateTier.CACHE &&
                (replicas.all { it.reconstructible } || policy.reconstructible)
            ) {
                tolerated += key.first
            }
        }
        return ReplicaVerificationPlan(
            verify.sortedBy { it.value },
            warnings.sortedWith(compareBy<ReplicaRecoveryWarning> { it.tier.ordinal }.thenBy { it.objectId.value }),
            tolerated.distinct().sortedBy { it.value }
        )
    }
}
