package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files

class ContinuumSeedStoreTest {
    @Test
    fun primaryRoundTripAndLkgFallbackWork() {
        val dir = Files.createTempDirectory("continuum-seed-store").toFile()
        try {
            val signer = TestSigner()
            val store = FileContinuumSeedStore(dir, signer)
            val first = seed("root-a")
            val second = first.copy(
                currentStateRoot = ContinuumObjectId.of(
                    ContinuumObjectType.STATE_MANIFEST,
                    "root-b".toByteArray()
                ),
                migrationHistoryReferences = listOf("migration:1")
            )
            store.save(first)
            store.save(second)

            assertEquals(second, store.requireLoad().seed)

            val primary = java.io.File(dir, "continuum-seed.bin")
            val bytes = primary.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
            primary.writeBytes(bytes)

            val recovered = store.requireLoad()
            assertEquals(ContinuumSeedLoadSource.LAST_KNOWN_GOOD, recovered.source)
            assertEquals(first, recovered.seed)
            assertNotNull(recovered.seed.contentHash())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun seed(root: String) = ContinuumSeed(
        instanceId = ContinuumInstanceId("continuum-store-test"),
        governanceVersion = GovernanceVersion(1),
        stateFormatVersion = StateFormatVersion(1),
        memorySchemaVersion = 1,
        skillSchemaVersion = 1,
        modelAdapterAbiVersion = ModelAdapterAbiVersion(1),
        ownerIdentityReference = "owner:test",
        authorizedNodeIds = listOf(ComputeNodeId("fold")),
        currentStateRoot = ContinuumObjectId.of(
            ContinuumObjectType.STATE_MANIFEST,
            root.toByteArray()
        ),
        createdAtMillis = 100
    )

    private class TestSigner : IdentitySigner {
        private val secret = "seed-store-secret".toByteArray()
        override fun sign(payload: ByteArray): ByteArray = sha256Bytes(secret + payload)
        override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
            sign(payload).contentEquals(signature)
    }
}
