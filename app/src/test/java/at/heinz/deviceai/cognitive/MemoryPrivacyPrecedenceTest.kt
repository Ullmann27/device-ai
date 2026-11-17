package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPrivacyPrecedenceTest {
    @Test fun sensitiveDeviceFlagCannotWeakenPrivacy() {
        val decision = MemoryWritePolicy().decide(context().copy(containsDeviceInformation = true))
        assertTrue(decision.shouldWrite)
        assertEquals(PrivacyClass.SENSITIVE, decision.privacyClass)
    }

    @Test fun deviceProvenanceCannotWeakenSensitivePrivacy() {
        val decision = MemoryWritePolicy().decide(context().copy(provenance = ProvenanceType.DEVICE_OBSERVATION))
        assertEquals(PrivacyClass.SENSITIVE, decision.privacyClass)
    }

    @Test fun classificationMatrixPreservesEveryOtherClass() {
        for (provenance in ProvenanceType.values()) for (mask in 0 until 8) {
            val sensitive = mask and 1 != 0
            val device = mask and 2 != 0
            val personal = mask and 4 != 0
            val input = context().copy(provenance = provenance, containsSensitiveInformation = sensitive,
                containsDeviceInformation = device, containsPersonalInformation = personal)
            val expected = when {
                sensitive -> PrivacyClass.SENSITIVE
                device || provenance == ProvenanceType.DEVICE_OBSERVATION -> PrivacyClass.DEVICE_LOCAL_ONLY
                personal || provenance == ProvenanceType.USER_PROVIDED -> PrivacyClass.PRIVATE
                else -> PrivacyClass.PUBLIC
            }
            assertEquals("$provenance mask=$mask", expected, MemoryWritePolicy().decide(input).privacyClass)
        }
    }

    @Test fun classifiedSensitiveDeviceRecordStaysOutOfDefaultContext() {
        val decision = decision()
        val record = record(decision.privacyClass)
        val result = AiContextCompiler(AiContextPolicy(includeToolCatalog = false), nowMillis = { 10L })
            .compile(ContextIntegrityFixture.input("zircon", listOf(record)))
        assertTrue(result.includedMemoryIds.isEmpty())
        assertFalse(result.text.contains("R4_SENSITIVE_DEVICE_DETAIL"))
    }

    @Test fun explicitSensitivePolicyStillAllowsTheSameEvidence() {
        val result = AiContextCompiler(AiContextPolicy(allowedPrivacy = setOf(PrivacyClass.SENSITIVE),
            includeToolCatalog = false), nowMillis = { 10L })
            .compile(ContextIntegrityFixture.input("zircon", listOf(record(decision().privacyClass))))
        assertEquals(listOf("sensitive-device"), result.includedMemoryIds)
        assertTrue(result.text.contains("R4_SENSITIVE_DEVICE_DETAIL"))
    }

    @Test fun proofCarryingMutationAcceptsTheSensitiveClassification() {
        val mutation = mutation(record(PrivacyClass.SENSITIVE), decision())
        assertTrue(mutation.verify(SIGNER))
        val ledger = MemoryMutationLedger(SIGNER)
        ledger.append(mutation)
        assertTrue(ledger.verifyChain())
        assertEquals(memoryRecordHash(record(PrivacyClass.SENSITIVE)), mutation.payload.afterHash)
    }

    @Test fun proofCarryingMutationRejectsDeviceClassDowngrade() {
        assertThrows(IllegalArgumentException::class.java) {
            mutation(record(PrivacyClass.DEVICE_LOCAL_ONLY), decision())
        }
    }

    @Test fun sensitivityDoesNotAuthorizeLowConfidenceOrEphemeralWrites() {
        val unverified = context().copy(provenance = ProvenanceType.UNVERIFIED, confidence = 0.1)
        assertFalse(MemoryWritePolicy().decide(unverified).shouldWrite)
        val ephemeral = context().copy(durableKnowledge = false)
        assertFalse(MemoryWritePolicy().decide(ephemeral).shouldWrite)
    }

    private fun context() = MemoryWriteContext(MemoryType.SEMANTIC, ProvenanceType.USER_PROVIDED,
        confidence = 0.9, durableKnowledge = true, containsSensitiveInformation = true)

    private fun decision() = MemoryWritePolicy().decide(context().copy(
        provenance = ProvenanceType.DEVICE_OBSERVATION, containsDeviceInformation = true))

    private fun record(privacy: PrivacyClass) = ContextIntegrityFixture.memory(
        "sensitive-device", "zircon R4_SENSITIVE_DEVICE_DETAIL"
    ).copy(provenance = ProvenanceType.DEVICE_OBSERVATION, privacyClass = privacy)

    private fun mutation(record: MemoryRecord, decision: MemoryWriteDecision) = ProofCarryingMemoryMutation.create(
        "r4-privacy", null, record, MemoryMutationReason.NEW_EVIDENCE,
        listOf("synthetic:r4-evidence"), emptyList(), null, decision, SIGNER, 10L
    )

    private companion object {
        val SIGNER = object : IdentitySigner {
            override fun sign(payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec("synthetic-r4-test-key".toByteArray(), "HmacSHA256"))
                doFinal(payload)
            }
            override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
                MessageDigest.isEqual(sign(payload), signature)
        }
    }
}
