package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ProofCarryingMemoryCanonicalEncodingTest {
    private val signer = object : IdentitySigner {
        override fun sign(payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec("canonical-memory-test-only-key".toByteArray(), "HmacSHA256"))
            doFinal(payload)
        }

        override fun verify(payload: ByteArray, signature: ByteArray): Boolean =
            MessageDigest.isEqual(sign(payload), signature)
    }

    private fun payload() = MemoryMutationPayload(
        mutationId = "mutation-1", recordId = "record-1", beforeHash = null,
        afterHash = "a".repeat(64), reason = MemoryMutationReason.NEW_EVIDENCE,
        provenance = ProvenanceType.USER_PROVIDED, evidenceReferences = listOf("source:a"),
        contradictionReferences = emptyList(), previousMutationHash = null, createdAtMillis = 1L
    )

    private fun record() = MemoryRecord(
        id = "record-1", type = MemoryType.SEMANTIC, content = "synthetic memory",
        provenance = ProvenanceType.USER_PROVIDED, confidence = 1.0, createdAt = 1L, updatedAt = 1L
    )

    @Test fun evidenceItemsHaveDistinctBoundaries() {
        val one = payload().copy(evidenceReferences = listOf("source:a,source:b"))
        val two = payload().copy(evidenceReferences = listOf("source:a", "source:b"))
        assertFalse(one.canonicalBytes().contentEquals(two.canonicalBytes()))
        assertNotEquals(one.hash(), two.hash())
    }

    @Test fun contradictionItemsHaveDistinctBoundaries() {
        val one = payload().copy(contradictionReferences = listOf("claim:a,claim:b"))
        val two = payload().copy(contradictionReferences = listOf("claim:a", "claim:b"))
        assertNotEquals(one.hash(), two.hash())
    }

    @Test fun referencesCannotCrossEvidenceAndContradictionFields() {
        val first = payload().copy(evidenceReferences = listOf("a|b"), contradictionReferences = listOf("c"))
        val second = payload().copy(evidenceReferences = listOf("a"), contradictionReferences = listOf("b|c"))
        assertNotEquals(first.hash(), second.hash())
    }

    @Test fun signatureRejectsRepartitionedEvidence() {
        val original = payload().copy(evidenceReferences = listOf("source:a,source:b"))
        val signed = ProofCarryingMemoryMutation(original, signer.sign(original.canonicalBytes()))
        val altered = signed.copy(payload = original.copy(evidenceReferences = listOf("source:a", "source:b")))
        assertTrue(signed.verify(signer))
        assertFalse(altered.verify(signer))
    }

    @Test fun recordIdentityAndContentCannotTradeDelimiters() {
        val first = record().copy(id = "id|SEMANTIC|part", content = "memo")
        val second = record().copy(id = "id", content = "part|SEMANTIC|memo")
        assertNotEquals(memoryRecordHash(first), memoryRecordHash(second))
    }

    @Test fun recordTagItemsHaveDistinctBoundaries() {
        assertNotEquals(memoryRecordHash(record().copy(tags = listOf("tag:a,tag:b"))),
            memoryRecordHash(record().copy(tags = listOf("tag:a", "tag:b"))))
    }

    @Test fun recordParentItemsHaveDistinctBoundaries() {
        assertNotEquals(memoryRecordHash(record().copy(parentIds = listOf("parent:a,parent:b"))),
            memoryRecordHash(record().copy(parentIds = listOf("parent:a", "parent:b"))))
    }

    @Test fun recordValidationEvidenceHasDistinctBoundaries() {
        assertNotEquals(memoryRecordHash(record().copy(validationEvidenceReferences = listOf("a,b"))),
            memoryRecordHash(record().copy(validationEvidenceReferences = listOf("a", "b"))))
    }

    @Test fun absentAndEmptySourceReferencesRemainDistinct() {
        assertNotEquals(memoryRecordHash(record().copy(sourceReference = null)),
            memoryRecordHash(record().copy(sourceReference = "")))
    }

    @Test fun setNormalizationRemainsDeterministic() {
        assertEquals(payload().copy(evidenceReferences = listOf("b", "a", "b")).hash(),
            payload().copy(evidenceReferences = listOf("a", "b")).hash())
        assertEquals(memoryRecordHash(record().copy(tags = listOf("b", "a", "b"))),
            memoryRecordHash(record().copy(tags = listOf("a", "b"))))
    }

    @Test fun UnicodeAndSeparatorBearingReferencesVerify() {
        val p = payload().copy(evidenceReferences = listOf("\u00e4,|\n\u0000\ud83e\udde0"))
        assertTrue(ProofCarryingMemoryMutation(p, signer.sign(p.canonicalBytes())).verify(signer))
        assertNotEquals(p.hash(), p.copy(evidenceReferences = listOf("\u00e4", "|\n\u0000\ud83e\udde0")).hash())
    }

    @Test fun unpairedCodeUnitsCannotAliasReplacementCharacters() {
        assertNotEquals(payload().copy(evidenceReferences = listOf("\ud800")).hash(),
            payload().copy(evidenceReferences = listOf("?")).hash())
    }
}
