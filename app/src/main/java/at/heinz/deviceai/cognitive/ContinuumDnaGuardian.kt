package at.heinz.deviceai.cognitive

import java.io.File

enum class DnaIntegrityState {
    NOT_INITIALIZED,
    VERIFIED,
    INVALID
}

data class DnaIntegrityAssessment(
    val state: DnaIntegrityState,
    val identityGeneration: Int? = null
)

/**
 * Read-only inspection of Continuum's signed seed, state root and matching
 * cognitive archive. Never creates a genesis, repairs files or promotes a model.
 * Invalid stored evidence must be handled outside this class by the owner.
 */
class ContinuumDnaGuardian(
    private val directory: File,
    private val signerFactory: () -> IdentitySigner
) {
    fun inspect(): DnaIntegrityAssessment {
        if (directory.exists() && !directory.isDirectory) {
            return DnaIntegrityAssessment(DnaIntegrityState.INVALID)
        }
        val seed = File(directory, "seed")
        val primary = File(seed, "continuum-seed.bin")
        val backup = File(seed, "continuum-seed.lkg")
        if (!primary.isFile && !backup.isFile) {
            val hasOrphans = listOf(
                seed,
                File(directory, "objects"),
                File(directory, "cognitive")
            ).any { folder ->
                folder.exists() && (!folder.isDirectory || !folder.listFiles().isNullOrEmpty())
            }
            return DnaIntegrityAssessment(
                if (hasOrphans) DnaIntegrityState.INVALID else DnaIntegrityState.NOT_INITIALIZED
            )
        }

        // openOrCreate() cannot take its genesis path here: a seed file exists.
        // The runtime verifies the signed seed, state object, archive hash and
        // matching genome/identity state before returning.
        return try {
            val state = ContinuumRuntimeStore(directory, signerFactory()).openOrCreate()
            check(state.archive.identityState.verify())
            check(state.archive.genome.hash() == state.archive.identityState.cognitiveGenomeHash)
            check(state.archive.identityState.identityId == state.seed.instanceId.value)
            DnaIntegrityAssessment(DnaIntegrityState.VERIFIED, state.archive.identityGeneration)
        } catch (_: Exception) {
            DnaIntegrityAssessment(DnaIntegrityState.INVALID)
        }
    }
}
