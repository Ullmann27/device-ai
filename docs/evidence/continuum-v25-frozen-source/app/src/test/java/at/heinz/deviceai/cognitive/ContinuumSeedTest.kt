package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ContinuumSeedTest {
    @Test
    fun signedSeedRoundTripsAndVerifies() {
        val signer = TestSigner()
        val seed = seed()
        val sealed = ContinuumSeedCodec.seal(seed, signer)
        val encoded = ContinuumSeedCodec.encodeEnvelope(sealed)
        val decoded = ContinuumSeedCodec.decodeEnvelope(encoded)
        val opened = ContinuumSeedCodec.open(decoded, signer)

        assertEquals(seed, opened)
        assertEquals(seed.contentHash(), opened.contentHash())
        assertTrue(decoded.signature.isNotEmpty())
    }

    @Test
    fun tamperedEnvelopeIsRejected() {
        val signer = TestSigner()
        val encoded = ContinuumSeedCodec.encodeEnvelope(ContinuumSeedCodec.seal(seed(), signer))
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 0x01).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSeedCodec.decodeEnvelope(encoded)
        }
    }

    @Test
    fun unsupportedFutureSchemaIsRejectedBeforeSigning() {
        assertThrows(IllegalArgumentException::class.java) {
            ContinuumSeedCodec.seal(seed().copy(schemaVersion = 2), TestSigner())
        }
    }

    @Test
    fun oversizedMigrationReferenceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            seed().copy(migrationHistoryReferences = listOf("x".repeat(300)))
        }
    }

    private fun seed() = ContinuumSeed(
        instanceId = ContinuumInstanceId("continuum-test"),
        governanceVersion = GovernanceVersion(1),
        stateFormatVersion = StateFormatVersion(1),
        memorySchemaVersion = 1,
        skillSchemaVersion = 1,
        modelAdapterAbiVersion = ModelAdapterAbiVersion(1),
        ownerIdentityReference = "owner:test-key",
        authorizedNodeIds = listOf(ComputeNodeId("fold")),
        currentStateRoot = ContinuumObjectId.of(
            ContinuumObjectType.STATE_MANIFEST,
            "state-root".toByteArray()
        ),
        migrationHistoryReferences = listOf("migration:genesis"),
        createdAtMillis = 100
    )

    private class TestSigner : IdentitySigner {
        private val secret = "test-secret".toByteArray()

        override fun sign(payload: ByteArray): ByteArray = sha256Bytes(secret + payload)

        override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
            sign(payload).contentEquals(signature)
    }
}
