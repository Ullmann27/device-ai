package at.heinz.deviceai.cognitive

data class BehaviorProfile(val traits: Map<String, Double> = mapOf("verbosity" to 0.5, "caution" to 0.5))

/** Immutable snapshot of identity. Changes produce a new generation. */
data class IdentityState(
    val identityId: String,
    val generation: Int,
    val chosenName: String?,
    val communicationProfile: Map<String, String>,
    val behaviorProfile: BehaviorProfile,
    val modelReputation: Map<String, Double>,
    val sourceReputation: Map<String, Double>,
    val capabilityEstimates: Map<String, Double>,
    val preferredProblemSolvingMethods: List<String>,
    val skillRegistryRoot: String,
    val memoryRoot: String,
    val selfModelRoot: String,
    val experienceRoot: String,
    val cognitiveGenomeHash: String,
    val parentGeneration: Int?,
    val parentSignature: String?,
    val signature: String = ""
) {
    fun canonical(): String = listOf(
        identityId, generation, chosenName ?: "<undefined>", communicationProfile.toSortedMap(),
        behaviorProfile.traits.toSortedMap(), modelReputation.toSortedMap(), sourceReputation.toSortedMap(),
        capabilityEstimates.toSortedMap(), preferredProblemSolvingMethods, skillRegistryRoot, memoryRoot,
        selfModelRoot, experienceRoot, cognitiveGenomeHash, parentGeneration ?: -1, parentSignature ?: ""
    ).joinToString("|")

    /** Hash-chain signature (integrity, not authenticity). */
    fun sign(): IdentityState = copy(signature = sha256Hex(canonical()))

    fun verify(): Boolean = signature == sha256Hex(canonical())

    /** Creates the next generation; this state is left untouched. */
    fun evolve(change: (IdentityState) -> IdentityState): IdentityState =
        change(this).copy(generation = generation + 1, parentGeneration = generation, parentSignature = signature).sign()

    companion object {
        /** identity minimal, name undefined, behavior neutral, everything unknown/empty. */
        fun initial(genome: CognitiveGenome): IdentityState = IdentityState(
            identityId = genome.identityId, generation = 1, chosenName = null,
            communicationProfile = emptyMap(), behaviorProfile = BehaviorProfile(),
            modelReputation = emptyMap(), sourceReputation = emptyMap(), capabilityEstimates = emptyMap(),
            preferredProblemSolvingMethods = emptyList(),
            skillRegistryRoot = "", memoryRoot = "", selfModelRoot = "uncertain", experienceRoot = "",
            cognitiveGenomeHash = genome.hash(), parentGeneration = null, parentSignature = null
        ).sign()
    }
}

/** Append-only generation history; auditable and exportable. */
class IdentityHistory(genesis: IdentityState) {
    private val chain = mutableListOf(genesis)
    val generations: List<IdentityState> get() = chain.toList()
    val current: IdentityState get() = chain.last()

    fun advance(change: (IdentityState) -> IdentityState): IdentityState {
        val next = current.evolve(change)
        chain.add(next)
        return next
    }

    fun audit(): Boolean = chain.withIndex().all { (i, s) ->
        s.verify() && s.generation == i + 1 &&
            (i == 0 || (s.parentGeneration == chain[i - 1].generation && s.parentSignature == chain[i - 1].signature))
    }

    fun restore(generation: Int): IdentityState? = chain.firstOrNull { it.generation == generation }

    fun export(): String = chain.joinToString("\n") { it.canonical() + "#" + it.signature }
}
