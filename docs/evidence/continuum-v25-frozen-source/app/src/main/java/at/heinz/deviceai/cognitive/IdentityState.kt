package at.heinz.deviceai.cognitive

/**
 * Immutable snapshot of identity. Changes produce a new generation.
 * [integrityHash] is a SHA-256 hash for tamper detection only. It is NOT a cryptographic
 * signature and does not authenticate who created the state (future: Android Keystore signing).
 */
data class IdentityState(
    val identityId: String,
    val generation: Int,
    val chosenName: String?,
    val communicationProfile: Map<String, String>,
    val behaviorProfile: BehaviorProfile,
    val modelReputation: Map<String, Double>,
    val sourceReputation: Map<String, Double>,
    val selfModel: SelfModel,
    val preferredProblemSolvingMethods: List<String>,
    val skillRegistryRoot: String,
    val memoryRoot: String,
    val experienceRoot: String,
    val cognitiveGenomeHash: String,
    val parentGeneration: Int?,
    val parentHash: String?,
    val integrityHash: String = ""
) {
    init {
        require(identityId.isNotBlank()) { "identityId must not be blank" }
        require(generation >= 1) { "generation must be >= 1" }
        require((generation == 1) == (parentGeneration == null)) { "parentGeneration only absent for generation 1" }
        require(parentGeneration == null || parentGeneration == generation - 1) { "parentGeneration must be generation-1" }
        require((parentGeneration == null) == (parentHash == null)) { "parentHash must match parentGeneration presence" }
        require((modelReputation.values + sourceReputation.values).all { it.isUnit() }) { "reputations must be finite within 0..1" }
    }

    fun canonical(): String = listOf(
        identityId, generation, chosenName ?: "<undefined>",
        communicationProfile.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" },
        behaviorProfile.canonical(),
        modelReputation.toSortedMap().entries.joinToString(",") { "${it.key}=${canon(it.value)}" },
        sourceReputation.toSortedMap().entries.joinToString(",") { "${it.key}=${canon(it.value)}" },
        selfModel.canonical(), preferredProblemSolvingMethods.joinToString(","),
        skillRegistryRoot, memoryRoot, experienceRoot, cognitiveGenomeHash,
        parentGeneration ?: -1, parentHash ?: ""
    ).joinToString("|")

    fun seal(): IdentityState = copy(integrityHash = sha256Hex(canonical()))

    fun verify(): Boolean = integrityHash.isNotEmpty() && integrityHash == sha256Hex(canonical())

    /** Creates the next generation; this state is left untouched. */
    fun evolve(change: (IdentityState) -> IdentityState): IdentityState =
        change(this).copy(generation = generation + 1, parentGeneration = generation, parentHash = integrityHash).seal()

    companion object {
        /** identity minimal, name undefined, behavior neutral, everything unknown/empty. */
        fun initial(genome: CognitiveGenome): IdentityState = IdentityState(
            identityId = genome.identityId, generation = 1, chosenName = null,
            communicationProfile = emptyMap(), behaviorProfile = BehaviorProfile.neutral(),
            modelReputation = emptyMap(), sourceReputation = emptyMap(), selfModel = SelfModel.uncertain(),
            preferredProblemSolvingMethods = emptyList(),
            skillRegistryRoot = "", memoryRoot = "", experienceRoot = "",
            cognitiveGenomeHash = genome.hash(), parentGeneration = null, parentHash = null
        ).seal()
    }
}

/** Append-only generation history; auditable and exportable. */
class IdentityHistory(genesis: IdentityState) {
    private val chain = mutableListOf(genesis)
    init { require(genesis.generation == 1 && genesis.verify()) { "invalid genesis" } }

    val generations: List<IdentityState> get() = chain.toList()
    val current: IdentityState get() = chain.last()

    fun advance(change: (IdentityState) -> IdentityState): IdentityState = append(current.evolve(change))

    /** Appends only a verified direct successor of the current generation. */
    fun append(next: IdentityState): IdentityState {
        require(next.verify() && next.generation == current.generation + 1 &&
            next.parentGeneration == current.generation && next.parentHash == current.integrityHash) { "broken ancestry" }
        chain.add(next)
        return next
    }

    fun audit(): Boolean = chain.withIndex().all { (i, s) ->
        s.verify() && s.generation == i + 1 &&
            (i == 0 || (s.parentGeneration == chain[i - 1].generation && s.parentHash == chain[i - 1].integrityHash))
    }

    fun restore(generation: Int): IdentityState? = chain.firstOrNull { it.generation == generation }

    fun export(): String = chain.joinToString("\n") { it.canonical() + "#" + it.integrityHash }
}
