package at.heinz.deviceai.cognitive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.file.Files

class ContinuumPersistenceFailureTest {
    @Test
    fun objectStoreRejectsCorruptAndTruncatedFilesAndIgnoresMissingObjects() {
        val dir = Files.createTempDirectory("continuum-object-failures").toFile()
        try {
            val store = FileContinuumObjectStore(dir)
            val metadata = store.put(ContinuumObjectType.MEMORY_BLOCK, "memory".toByteArray(), 10)
            val file = File(dir, "${metadata.id.value}.obj")
            val encoded = file.readBytes()

            assertNull(store.get(ContinuumObjectId.of(ContinuumObjectType.CACHE, "missing".toByteArray())))

            val corrupted = encoded.copyOf().also {
                it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
            }
            file.writeBytes(corrupted)
            assertThrows(IllegalArgumentException::class.java) { store.get(metadata.id) }

            file.writeBytes(encoded.copyOf(encoded.size - 1))
            assertThrows(IllegalArgumentException::class.java) { store.get(metadata.id) }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun olderManifestSchemaIsRejected() {
        val dir = Files.createTempDirectory("continuum-old-manifest").toFile()
        try {
            val manifest = runtimeStore(dir, TestSigner()).openOrCreate().manifest
            val encoded = ContinuumStateManifestCodec.encode(manifest)
            val schemaOffset = 2 + "DEVICE_AI_CONTINUUM_STATE_MANIFEST".toByteArray().size
            encoded[schemaOffset + 3] = 0

            assertThrows(IllegalArgumentException::class.java) {
                ContinuumStateManifestCodec.decode(encoded)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun olderSeedStateFormatIsRejectedEvenWhenResigned() {
        val dir = Files.createTempDirectory("continuum-old-seed").toFile()
        try {
            val signer = TestSigner()
            val seed = seed()
            val payload = ContinuumSeedCodec.seal(seed, signer).payload.copyOf()
            val stateFormatOffset =
                4 + 4 + seed.instanceId.value.toByteArray().size + 4
            payload[stateFormatOffset + 3] = 0
            File(dir, "continuum-seed.bin").writeBytes(
                ContinuumSeedCodec.encodeEnvelope(SignedContinuumSeed(payload, signer.sign(payload)))
            )
            val store = FileContinuumSeedStore(dir, signer)

            assertTrue(store.hasStoredState())
            assertNull(store.load())
            assertThrows(IllegalStateException::class.java) { store.requireLoad() }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun tamperedIdentityHashCannotBeReconciledAsValidRuntimeState() {
        val dir = Files.createTempDirectory("continuum-tampered-identity").toFile()
        try {
            val signer = TestSigner()
            val runtime = runtimeStore(dir, signer)
            val state = runtime.openOrCreate()
            val archiveFile = File(dir, "cognitive/cognitive-state.bin")
            val encoded = archiveFile.readBytes()
            val input = DataInputStream(ByteArrayInputStream(encoded))
            input.readUTF()
            input.readInt()
            val payloadSize = input.readInt()
            val payloadOffset = encoded.size - input.available()
            val payload = encoded.copyOfRange(payloadOffset, payloadOffset + payloadSize)
            val hashOffset = String(payload, Charsets.ISO_8859_1).indexOf(state.archive.identityState.integrityHash)
            assertTrue(hashOffset >= 0)
            payload[hashOffset] = (payload[hashOffset].toInt() xor 1).toByte()
            payload.copyInto(encoded, payloadOffset)
            sha256Bytes(payload).copyInto(encoded, payloadOffset + payloadSize)
            archiveFile.writeBytes(encoded)

            assertThrows(IllegalStateException::class.java) { runtime.openOrCreate() }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun tamperedSeedSignatureCannotCreateReplacementIdentity() {
        val dir = Files.createTempDirectory("continuum-tampered-signature").toFile()
        try {
            val signer = TestSigner()
            val runtime = runtimeStore(dir, signer)
            runtime.openOrCreate()
            val seedFile = File(dir, "seed/continuum-seed.bin")
            val envelope = ContinuumSeedCodec.decodeEnvelope(seedFile.readBytes())
            val signature = envelope.signature.copyOf().also {
                it[0] = (it[0].toInt() xor 1).toByte()
            }
            seedFile.writeBytes(
                ContinuumSeedCodec.encodeEnvelope(SignedContinuumSeed(envelope.payload, signature))
            )

            assertThrows(IllegalArgumentException::class.java) { runtime.openOrCreate() }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingCommittedSeedOrStateRootFailsClosed() {
        val missingSeedDir = Files.createTempDirectory("continuum-missing-seed").toFile()
        try {
            val signer = TestSigner()
            val runtime = runtimeStore(missingSeedDir, signer)
            runtime.openOrCreate()
            assertTrue(File(missingSeedDir, "seed/continuum-seed.bin").delete())

            assertThrows(IllegalArgumentException::class.java) { runtime.openOrCreate() }
        } finally {
            missingSeedDir.deleteRecursively()
        }

        val missingRootDir = Files.createTempDirectory("continuum-missing-root").toFile()
        try {
            val signer = TestSigner()
            val runtime = runtimeStore(missingRootDir, signer)
            val state = runtime.openOrCreate()
            assertTrue(File(missingRootDir, "objects/${state.seed.currentStateRoot.value}.obj").delete())

            assertThrows(IllegalStateException::class.java) { runtime.openOrCreate() }
        } finally {
            missingRootDir.deleteRecursively()
        }
    }

    @Test
    fun unrenamedSeedTempFileIsIgnoredAfterInterruptedWrite() {
        val dir = Files.createTempDirectory("continuum-interrupted-seed").toFile()
        try {
            val signer = TestSigner()
            val store = FileContinuumSeedStore(dir, signer)
            val committed = seed("committed-root")
            val interrupted = seed("uncommitted-root")
            store.save(committed)
            val primary = File(dir, "continuum-seed.bin")
            val committedBytes = primary.readBytes()
            val temp = File(dir, "continuum-seed-interrupted.tmp")
            temp.writeBytes(ContinuumSeedCodec.encodeEnvelope(ContinuumSeedCodec.seal(interrupted, signer)))

            assertEquals(committed, store.requireLoad().seed)
            assertArrayEquals(committedBytes, primary.readBytes())
            assertTrue(temp.isFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun duplicateObjectWritesDoNotCreateAdditionalObjects() {
        val dir = Files.createTempDirectory("continuum-duplicate-object").toFile()
        try {
            val store = FileContinuumObjectStore(dir)
            val first = store.put(ContinuumObjectType.MEMORY_BLOCK, "same-memory".toByteArray(), 10)
            val second = store.put(ContinuumObjectType.MEMORY_BLOCK, "same-memory".toByteArray(), 20)

            assertEquals(first.id, second.id)
            assertEquals(first.createdAtMillis, second.createdAtMillis)
            assertEquals(1, dir.listFiles { file -> file.name.endsWith(".obj") }.orEmpty().size)
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun modelSwapPersistsWithoutChangingIdentity() {
        val dir = Files.createTempDirectory("continuum-model-swap").toFile()
        try {
            val runtime = runtimeStore(dir, TestSigner())
            val genesis = runtime.openOrCreate()
            val memory = memory()
            val firstExperience = experience("model-before")
            val firstCheckpoint = runtime.checkpoint(
                genesis,
                memories = listOf(memory),
                experiences = listOf(firstExperience)
            )
            val swappedCheckpoint = runtime.checkpoint(
                firstCheckpoint,
                experiences = listOf(firstExperience.copy(modelIds = listOf("model-after")))
            )
            val restored = runtimeStore(dir, TestSigner()).openOrCreate()

            assertEquals(genesis.archive.identityId, restored.archive.identityId)
            assertEquals(genesis.archive.identityState, restored.archive.identityState)
            assertEquals(genesis.manifest.identityGeneration, swappedCheckpoint.manifest.identityGeneration)
            assertEquals(listOf(memory), restored.archive.memories)
            assertEquals(listOf("model-after"), restored.archive.experiences.single().modelIds)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun runtimeStore(dir: File, signer: IdentitySigner) = ContinuumRuntimeStore(
        baseDirectory = dir,
        signer = signer,
        ownerIdentityReference = "owner:test",
        localNodeId = ComputeNodeId("test-local"),
        nowMillis = { 100 },
        instanceIdFactory = { ContinuumInstanceId("continuum-persistence-test") }
    )

    private fun seed(root: String = "root") = ContinuumSeed(
        instanceId = ContinuumInstanceId("continuum-persistence-test"),
        governanceVersion = GovernanceVersion(1),
        stateFormatVersion = StateFormatVersion(1),
        memorySchemaVersion = 1,
        skillSchemaVersion = 1,
        modelAdapterAbiVersion = ModelAdapterAbiVersion(1),
        ownerIdentityReference = "owner:test",
        authorizedNodeIds = listOf(ComputeNodeId("test-local")),
        currentStateRoot = ContinuumObjectId.of(ContinuumObjectType.STATE_MANIFEST, root.toByteArray()),
        createdAtMillis = 100
    )

    private fun memory() = MemoryRecord(
        id = "persisted-memory",
        type = MemoryType.SEMANTIC,
        content = "Model selection does not change persistent identity.",
        provenance = ProvenanceType.USER_PROVIDED,
        confidence = 0.9,
        createdAt = 10,
        updatedAt = 10,
        privacyClass = PrivacyClass.PRIVATE
    )

    private fun experience(modelId: String) = Experience(
        id = "model-swap-experience",
        taskType = "continuity",
        requestSummary = "Record a model interaction",
        modelIds = listOf(modelId),
        startedAt = 20,
        completedAt = 30,
        evaluation = Evaluation(true, 0.9)
    )

    private class TestSigner : IdentitySigner {
        private val secret = "continuum-persistence-test-secret".toByteArray()
        override fun sign(payload: ByteArray): ByteArray = sha256Bytes(secret + payload)
        override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
            sign(payload).contentEquals(signature)
    }
}
