package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class ContinuumComputeReuseResearchTest {
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 0xff) }

    private fun key(
        owner: String = "owner",
        epoch: Long = 0,
        model: String = digest("model"),
        policy: String = digest("policy"),
        context: String = digest("context")
    ): ContinuumComputeReuseResearch.PureStageKey =
        ContinuumComputeReuseResearch.PureStageKey(
            owner, context, model, policy, epoch,
            ContinuumComputeReuseResearch.Stage.SOURCE_PARSE
        )

    @Test fun defaultDenyRejectsUnverifiedResults() {
        val cache = ContinuumComputeReuseResearch()
        assertFalse(cache.putVerified(key(), byteArrayOf(1), "receipt"))
        assertNull(cache.read(key()))
        assertEquals(1L, cache.metrics().rejects)
    }

    @Test fun verifiedResultIsCopiedNotAliased() {
        val cache = ContinuumComputeReuseResearch(receiptVerifier = { _, _, evidence -> evidence == "trusted" })
        val bytes = byteArrayOf(1, 2, 3)
        assertTrue(cache.putVerified(key(), bytes, "trusted"))
        bytes[0] = 99
        assertArrayEquals(byteArrayOf(1, 2, 3), cache.read(key()))
        val result = cache.read(key())!!
        result[1] = 88
        assertArrayEquals(byteArrayOf(1, 2, 3), cache.read(key()))
    }

    @Test fun ownerAndMemoryEpochAreHardCachePartitions() {
        val cache = ContinuumComputeReuseResearch(receiptVerifier = { _, _, _ -> true })
        assertTrue(cache.putVerified(key(), byteArrayOf(4), "trusted"))
        assertNull(cache.read(key(owner = "other")))
        assertNull(cache.read(key(epoch = 1)))
        assertArrayEquals(byteArrayOf(4), cache.read(key()))
    }

    @Test fun modelPolicyAndContextRevisionsInvalidateCache() {
        val cache = ContinuumComputeReuseResearch(receiptVerifier = { _, _, _ -> true })
        cache.putVerified(key(), byteArrayOf(5), "trusted")
        assertNull(cache.read(key(model = digest("new-model"))))
        assertNull(cache.read(key(policy = digest("new-policy"))))
        assertNull(cache.read(key(context = digest("new-context"))))
        assertNotNull(cache.read(key()))
    }

    @Test fun timeToLiveExpiresWithoutServingStaleData() {
        var time = 1000L
        val cache = ContinuumComputeReuseResearch(
            receiptVerifier = { _, _, _ -> true },
            clockMillis = { time }, ttlMillis = 100
        )
        cache.putVerified(key(), byteArrayOf(1), "trusted")
        time = 1100L
        assertNull(cache.read(key()))
        assertEquals(0, cache.metrics().entries)
    }

    @Test fun sizeQuotaAndInvalidReceiptFailClosed() {
        val cache = ContinuumComputeReuseResearch(
            maxResultBytes = 2,
            receiptVerifier = { _, _, evidence -> evidence == "trusted" }
        )
        assertFalse(cache.putVerified(key(), byteArrayOf(1, 2, 3), "trusted"))
        assertFalse(cache.putVerified(key(), byteArrayOf(1), ""))
        assertFalse(cache.putVerified(key(), byteArrayOf(1), "invalid"))
        assertEquals(3L, cache.metrics().rejects)
    }

    @Test fun boundedLeastRecentlyUsedStorageEvictsOldest() {
        val cache = ContinuumComputeReuseResearch(
            maxEntries = 2, receiptVerifier = { _, _, _ -> true }
        )
        val a = key(context = digest("a"))
        val b = key(context = digest("b"))
        val c = key(context = digest("c"))
        cache.putVerified(a, byteArrayOf(1), "ok")
        cache.putVerified(b, byteArrayOf(2), "ok")
        assertNotNull(cache.read(a))
        cache.putVerified(c, byteArrayOf(3), "ok")
        assertNull(cache.read(b))
        assertNotNull(cache.read(a))
        assertNotNull(cache.read(c))
        assertEquals(2, cache.metrics().entries)
    }

    @Test fun rejectingVerifierCannotPoisonExistingVerifiedArtifact() {
        var allow = true
        val cache = ContinuumComputeReuseResearch(receiptVerifier = { _, _, _ -> allow })
        assertTrue(cache.putVerified(key(), byteArrayOf(7), "trusted"))
        allow = false
        assertFalse(cache.putVerified(key(), byteArrayOf(8), "forged"))
        assertArrayEquals(byteArrayOf(7), cache.read(key()))
    }

    @Test fun bridgeRunsActualExistingContextCompilerWithoutCachingAnswers() {
        val now = 100_000L
        val genome = CognitiveGenome("continuum-context")
        val identity = IdentityState.initial(genome)
        val seed = ContinuumSeed(
            instanceId = ContinuumInstanceId(identity.identityId),
            governanceVersion = GovernanceVersion(1),
            stateFormatVersion = StateFormatVersion(1),
            memorySchemaVersion = 1,
            skillSchemaVersion = 1,
            modelAdapterAbiVersion = ModelAdapterAbiVersion(1),
            ownerIdentityReference = "owner:test",
            authorizedNodeIds = listOf(ComputeNodeId("fold")),
            currentStateRoot = ContinuumObjectId.of(
                ContinuumObjectType.STATE_MANIFEST, "root".toByteArray()
            ),
            createdAtMillis = now
        )
        val memory = MemoryRecord(
            id = "confirmed-context",
            type = MemoryType.SEMANTIC,
            content = "Continuum source analysis result",
            provenance = ProvenanceType.USER_PROVIDED,
            confidence = 1.0,
            createdAt = now,
            updatedAt = now,
            validationState = ValidationState.VERIFIED,
            validationEvidenceReferences = listOf("owner-confirmation"),
            privacyClass = PrivacyClass.PRIVATE
        )
        val sensitive = memory.copy(
            id = "sensitive-context",
            content = "secret training evidence",
            privacyClass = PrivacyClass.SENSITIVE
        )
        val input = AiContextInput(
            seed = seed,
            genome = genome,
            identityState = identity,
            userRequest = "Continuum source analysis",
            memories = listOf(memory, sensitive)
        )
        val cache = ContinuumComputeReuseResearch(
            compiler = AiContextCompiler(
                AiContextPolicy(
                    maxBytes = 6000, allowedPrivacy = setOf(PrivacyClass.PUBLIC, PrivacyClass.PRIVATE)
                ),
                nowMillis = { now }
            )
        )
        val observed = cache.compileObserved(input)
        assertTrue(observed.compiled.text.contains("CONTINUUM IDENTITY / GOVERNANCE"))
        assertTrue(observed.compiled.includedMemoryIds.contains("confirmed-context"))
        assertFalse(observed.compiled.includedMemoryIds.contains("sensitive-context"))
        assertFalse(observed.compiled.text.contains("secret training evidence"))
        assertEquals(digest(observed.compiled.text), observed.contextSha256)
        assertEquals(1, observed.includedMemoryCount)
        assertEquals(0, cache.metrics().entries)
        assertEquals(0L, cache.metrics().verifiedWrites)
    }
}
