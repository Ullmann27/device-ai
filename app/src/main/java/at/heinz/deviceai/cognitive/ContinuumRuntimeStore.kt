package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.File
import java.util.UUID

data class ContinuumStateManifest(
    val generation: Long,
    val instanceId: ContinuumInstanceId,
    val identityGeneration: Int,
    val archiveSha256: String,
    val memoryCount: Int,
    val experienceCount: Int,
    val previousRoot: ContinuumObjectId?,
    val createdAtMillis: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    init {
        require(generation >= 1)
        require(identityGeneration >= 1)
        require(archiveSha256.matches(Regex("[a-f0-9]{64}")))
        require(memoryCount in 0..10_000 && experienceCount in 0..10_000)
        require((generation == 1L) == (previousRoot == null))
        require(createdAtMillis >= 0)
        require(schemaVersion == CURRENT_SCHEMA_VERSION)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

object ContinuumStateManifestCodec {
    private const val MAGIC = "DEVICE_AI_CONTINUUM_STATE_MANIFEST"
    private const val MAX_BYTES = 16 * 1024

    fun encode(manifest: ContinuumStateManifest): ByteArray {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(manifest.schemaVersion)
                out.writeLong(manifest.generation)
                out.writeUTF(manifest.instanceId.value)
                out.writeInt(manifest.identityGeneration)
                out.writeUTF(manifest.archiveSha256)
                out.writeInt(manifest.memoryCount)
                out.writeInt(manifest.experienceCount)
                out.writeBoolean(manifest.previousRoot != null)
                manifest.previousRoot?.let { out.writeUTF(it.value) }
                out.writeLong(manifest.createdAtMillis)
            }
        }.toByteArray()
        require(bytes.size <= MAX_BYTES)
        return bytes
    }

    fun decode(bytes: ByteArray): ContinuumStateManifest = try {
        require(bytes.size in 1..MAX_BYTES)
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readUTF() == MAGIC) { "Unrecognized Continuum state manifest." }
        val schema = input.readInt()
        require(schema == ContinuumStateManifest.CURRENT_SCHEMA_VERSION) {
            "Unsupported Continuum state manifest schema."
        }
        val manifest = ContinuumStateManifest(
            generation = input.readLong(),
            instanceId = ContinuumInstanceId(input.readUTF()),
            identityGeneration = input.readInt(),
            archiveSha256 = input.readUTF(),
            memoryCount = input.readInt(),
            experienceCount = input.readInt(),
            previousRoot = if (input.readBoolean()) ContinuumObjectId(input.readUTF()) else null,
            createdAtMillis = input.readLong(),
            schemaVersion = schema
        )
        require(input.available() == 0) { "Unexpected trailing Continuum state-manifest data." }
        manifest
    } catch (e: IOException) {
        throw IllegalArgumentException("Corrupted Continuum state manifest.", e)
    }
}

data class ContinuumRuntimeState(
    val seed: ContinuumSeed,
    val archive: CognitiveArchive,
    val manifest: ContinuumStateManifest,
    val seedLoadSource: ContinuumSeedLoadSource,
    val archiveLoadSource: ArchiveLoadSource
)

class ContinuumRuntimeStore(
    baseDirectory: File,
    private val signer: IdentitySigner,
    private val ownerIdentityReference: String = "android-keystore:device-ai-identity-signing-key",
    private val localNodeId: ComputeNodeId = ComputeNodeId("android-local"),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val instanceIdFactory: () -> ContinuumInstanceId = {
        ContinuumInstanceId("continuum-" + UUID.randomUUID().toString().replace("-", ""))
    }
) {
    private val seedStore = FileContinuumSeedStore(File(baseDirectory, "seed"), signer)
    private val objectStore = FileContinuumObjectStore(File(baseDirectory, "objects"))
    private val cognitiveDirectory = File(baseDirectory, "cognitive")

    init {
        require(ownerIdentityReference.matches(Regex("[A-Za-z0-9._:@/-]{1,256}")))
    }

    @Synchronized
    fun openOrCreate(): ContinuumRuntimeState {
        val loadedSeed = seedStore.load()
        if (loadedSeed == null) {
            require(!seedStore.hasStoredState()) {
                "Continuum seed files exist but neither primary nor LKG is valid."
            }
            return createGenesis()
        }

        val seed = loadedSeed.seed
        val storedManifest = objectStore.get(seed.currentStateRoot)
            ?: error("Continuum state root is missing; recovery from a configured replica is required.")
        require(storedManifest.metadata.type == ContinuumObjectType.STATE_MANIFEST) {
            "Continuum state root does not reference a state manifest."
        }
        val manifest = ContinuumStateManifestCodec.decode(storedManifest.payload)
        require(manifest.instanceId == seed.instanceId) { "Continuum seed/state identity mismatch." }

        val archiveStore = CognitiveArchiveStore(cognitiveDirectory, seed.instanceId.value)
        val matching = archiveStore.loadCandidates().firstOrNull { candidate ->
            candidate.archive.identityGeneration == manifest.identityGeneration &&
                archiveHash(archiveStore, candidate.archive) == manifest.archiveSha256
        } ?: error("No cognitive archive matches the signed Continuum state root.")

        require(matching.archive.memories.size == manifest.memoryCount)
        require(matching.archive.experiences.size == manifest.experienceCount)

        return ContinuumRuntimeState(
            seed = seed,
            archive = matching.archive,
            manifest = manifest,
            seedLoadSource = loadedSeed.source,
            archiveLoadSource = matching.source
        )
    }

    @Synchronized
    fun checkpoint(
        current: ContinuumRuntimeState,
        identityState: IdentityState = current.archive.identityState,
        genome: CognitiveGenome = current.archive.genome,
        memories: List<MemoryRecord> = current.archive.memories,
        experiences: List<Experience> = current.archive.experiences
    ): ContinuumRuntimeState {
        require(current.seed.instanceId.value == identityState.identityId)
        require(genome.identityId == identityState.identityId)
        val archive = CognitiveArchive(identityState, genome, memories, experiences)
        val archiveStore = CognitiveArchiveStore(cognitiveDirectory, current.seed.instanceId.value)

        archiveStore.save(archive)
        val manifest = ContinuumStateManifest(
            generation = current.manifest.generation + 1,
            instanceId = current.seed.instanceId,
            identityGeneration = archive.identityGeneration,
            archiveSha256 = archiveHash(archiveStore, archive),
            memoryCount = archive.memories.size,
            experienceCount = archive.experiences.size,
            previousRoot = current.seed.currentStateRoot,
            createdAtMillis = nowMillis()
        )
        val manifestMetadata = objectStore.put(
            ContinuumObjectType.STATE_MANIFEST,
            ContinuumStateManifestCodec.encode(manifest),
            manifest.createdAtMillis
        )
        val seed = current.seed.copy(currentStateRoot = manifestMetadata.id)
        seedStore.save(seed)

        return ContinuumRuntimeState(
            seed = seed,
            archive = archive,
            manifest = manifest,
            seedLoadSource = ContinuumSeedLoadSource.PRIMARY,
            archiveLoadSource = ArchiveLoadSource.PRIMARY
        )
    }

    private fun createGenesis(): ContinuumRuntimeState {
        require(!cognitiveDirectory.exists() || cognitiveDirectory.listFiles().orEmpty().isEmpty()) {
            "Orphaned cognitive state exists without a valid Continuum seed."
        }

        val id = instanceIdFactory()
        val genome = CognitiveGenome(id.value)
        val identity = IdentityState.initial(genome)
        val archive = CognitiveArchive(identity, genome)
        val archiveStore = CognitiveArchiveStore(cognitiveDirectory, id.value)
        archiveStore.save(archive)

        val manifest = ContinuumStateManifest(
            generation = 1,
            instanceId = id,
            identityGeneration = archive.identityGeneration,
            archiveSha256 = archiveHash(archiveStore, archive),
            memoryCount = 0,
            experienceCount = 0,
            previousRoot = null,
            createdAtMillis = nowMillis()
        )
        val metadata = objectStore.put(
            ContinuumObjectType.STATE_MANIFEST,
            ContinuumStateManifestCodec.encode(manifest),
            manifest.createdAtMillis
        )
        val seed = ContinuumSeed(
            instanceId = id,
            governanceVersion = GovernanceVersion(1),
            stateFormatVersion = StateFormatVersion(1),
            memorySchemaVersion = 1,
            skillSchemaVersion = 1,
            modelAdapterAbiVersion = ModelAdapterAbiVersion(1),
            ownerIdentityReference = ownerIdentityReference,
            authorizedNodeIds = listOf(localNodeId),
            currentStateRoot = metadata.id,
            createdAtMillis = manifest.createdAtMillis
        )
        seedStore.save(seed)
        return ContinuumRuntimeState(
            seed = seed,
            archive = archive,
            manifest = manifest,
            seedLoadSource = ContinuumSeedLoadSource.PRIMARY,
            archiveLoadSource = ArchiveLoadSource.PRIMARY
        )
    }

    private fun archiveHash(store: CognitiveArchiveStore, archive: CognitiveArchive): String =
        sha256Bytes(store.export(archive).toByteArray(Charsets.UTF_8)).toHex()
}
