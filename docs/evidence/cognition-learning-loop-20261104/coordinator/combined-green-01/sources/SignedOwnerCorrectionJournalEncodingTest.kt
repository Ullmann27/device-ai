package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.IdentitySigner
import at.heinz.deviceai.cognitive.MemoryMutationPayload
import at.heinz.deviceai.cognitive.MemoryMutationReason
import at.heinz.deviceai.cognitive.ProofCarryingMemoryMutation
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.cognitive.memoryRecordHash
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SignedOwnerCorrectionJournalEncodingTest {
    private class CountingSigner : IdentitySigner {
        var signaturesCreated = 0
        private fun mac(payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec("canonical-memory-test-only-key".toByteArray(), "HmacSHA256"))
            doFinal(payload)
        }
        override fun sign(payload: ByteArray): ByteArray {
            signaturesCreated++
            return mac(payload)
        }
        override fun verify(payload: ByteArray, signature: ByteArray) =
            MessageDigest.isEqual(mac(payload), signature)
    }

    private val legacyBytes = Base64.getDecoder().decode("djEJbTJjLTdhM2JlZTAxMTk4YTgwMGVkNDZjMzk2MTU0MjZhYTg1CWJIZ3ROems0WkRCaVpXSTBPR1UzWVRJNU5UQm1NV0V5TjJRMgktCTQ2NTMzNjg2MGVjYzA1NGEyNzMzNDlmZWNhNmY1OTg4MmUwNTEyYWM2MjQzMWU4NjUwNzlkNTY5YTc0ZTQ5NDIJTkVXX0VWSURFTkNFCVVTRVJfUFJPVklERUQJYjNkdVpYSXRjM1JoZEdWdFpXNTBPbXg0TFRjNU9HUXdZbVZpTkRobE4yRXlPVFV3WmpGaE1qZGtOZwkJLQkxMDAwCTFvdExmNnJnbEFYRnRMbG93VFlFaVA0alZwOXlLQ2xHNXRrUnVGbTNBSXMKdjEJbTJjLTkzYjViNzZmMjk5MWYxZmQwY2NlYTM4OTNhMjcxMTQwCWJIZ3RNR0UwTkdJek5tUmhZV0UzTVRkaE56ZzJaVGN4TXpWaAktCTVjNmFmNDY2ZWEzMmI5ZWY2MzgxZmI2NmE2OGYwZTQ1MWY0NWJmYzY5YTVmYWI4NGNjMTIzNmJjODcxOGUyZmMJTkVXX0VWSURFTkNFCVVTRVJfUFJPVklERUQJYjNkdVpYSXRjM1JoZEdWdFpXNTBPbXg0TFRCaE5EUmlNelprWVdGaE56RTNZVGM0Tm1VM01UTTFZUQkJOTM2ODdlYWUxYjQzN2I4ZDQxMjAyOWY3OGEyZTdiMGYwODg2NTNkZGVmOTg3MmJlZTBhYzliNzI4MmU2ZjRlZgkyMDAwCUM0YjdsMDlCUk5qSF9oTGQxbEN1NmczT0YxVVVielQ2OVJWSy10RFpaSGsK")
    private val legacyHashes = listOf(
        "93687eae1b437b8d412029f78a2e7b0f088653ddef9872bee0ac9b7282e6f4ef",
        "ee2a3d96dc3873e1a9f5fbf2f020e7bd6af231e891fe6441a54575117a03729f"
    )

    private fun fact(content: String, at: Long) = LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT, LearningProvenance.USER_CONFIRMED,
        LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, content, now = at)

    private fun facts() = listOf(
        fact("Das blaue Testobjekt steht in Halle A.", 1_000L),
        fact("Das rote Testobjekt steht in Halle B.", 2_000L)
    )

    private fun withJournal(legacy: Boolean = false, block: (SignedOwnerCorrectionJournal, File, CountingSigner) -> Unit) {
        val directory = Files.createTempDirectory("pcm-encoding-test").toFile()
        try {
            val file = File(directory, SignedOwnerCorrectionJournal.FILE_NAME)
            if (legacy) file.writeBytes(legacyBytes)
            val signer = CountingSigner()
            block(SignedOwnerCorrectionJournal(directory, signer), file, signer)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun appendFirst(journal: SignedOwnerCorrectionJournal) {
        val first = facts().first()
        assertEquals(1, journal.recordDiff(emptyMap(), mapOf(first.id to first), 1_000L))
    }

    @Test fun oldWriterFixtureVerifiesWithoutResigningOrRewriting() = withJournal(true) { journal, file, signer ->
        val entries = journal.entries()
        assertEquals(listOf(1, 1), entries.map { it.payload.schemaVersion })
        assertEquals(legacyHashes, entries.map { it.payload.hash() })
        assertEquals(CorrectionAuditStatus.VERIFIED, journal.audit(facts()).status)
        assertEquals(0, signer.signaturesCreated)
        assertArrayEquals(legacyBytes, file.readBytes())
    }

    @Test fun newWritesUseV2AndRoundTripThroughTheRealWriter() = withJournal { journal, file, signer ->
        appendFirst(journal)
        assertTrue(file.readText().startsWith("v2\t"))
        val entry = journal.entries().single()
        assertEquals(2, entry.payload.schemaVersion)
        assertEquals(memoryRecordHash(SignedOwnerCorrectionJournal.toMemoryRecord(facts().first())), entry.payload.afterHash)
        assertEquals(CorrectionAuditStatus.VERIFIED, journal.audit(facts().take(1)).status)
        assertEquals(1, signer.signaturesCreated)
    }

    @Test fun mixedChainPreservesOldBytesAndAuditsEachRecordVersion() = withJournal(true) { journal, file, signer ->
        val original = facts()
        val revoked = original.first().copy(status = LearningStatus.REVOKED, updatedAt = 3_000L)
        val after = listOf(revoked, original.last())
        assertEquals(1, journal.recordDiff(original.associateBy { it.id }, after.associateBy { it.id }, 3_000L))
        val entries = journal.entries()
        assertEquals(listOf(1, 1, 2), entries.map { it.payload.schemaVersion })
        assertEquals(legacyHashes.last(), entries.last().payload.previousMutationHash)
        assertEquals(memoryRecordHash(SignedOwnerCorrectionJournal.toMemoryRecord(original.first()), 2),
            entries.last().payload.beforeHash)
        assertEquals(CorrectionAuditStatus.VERIFIED, journal.audit(after).status)
        assertArrayEquals(legacyBytes, file.readBytes().copyOfRange(0, legacyBytes.size))
        assertEquals(1, signer.signaturesCreated)
    }

    @Test fun tamperedLegacyEntryStillFailsClosed() = withJournal(true) { journal, file, signer ->
        val tampered = file.readText().replaceFirst("NEW_EVIDENCE", "CONSOLIDATION").toByteArray()
        file.writeBytes(tampered)
        assertEquals(CorrectionAuditStatus.TAMPERED, journal.audit(facts()).status)
        assertEquals(0, signer.signaturesCreated)
        assertArrayEquals(tampered, file.readBytes())
    }

    @Test fun downgradedOrUnknownJournalVersionsAreRejected() = withJournal { journal, file, signer ->
        appendFirst(journal)
        val original = file.readText()
        for (version in listOf("v1", "v3")) {
            file.writeText(version + original.substring(2))
            assertEquals(version, CorrectionAuditStatus.TAMPERED, journal.audit(facts().take(1)).status)
        }
        assertEquals(1, signer.signaturesCreated)
    }

    @Test fun malformedV2StringsAndEmptyReferenceItemsAreRejected() = withJournal { journal, file, _ ->
        appendFirst(journal)
        val fields = file.readText().trimEnd('\n').split('\t')
        for ((index, invalid) in listOf(2 to "AA", 7 to fields[7] + ",", 8 to "AA")) {
            val changed = fields.toMutableList()
            changed[index] = invalid
            file.writeText(changed.joinToString("\t") + "\n")
            assertEquals(CorrectionAuditStatus.TAMPERED, journal.audit(facts().take(1)).status)
        }
    }

    @Test fun v2WireRetainsSeparatorsUnicodeAndUnpairedCodeUnits() = withJournal { journal, file, signer ->
        val payload = payload().copy(recordId = "record|\n\ud800",
            evidenceReferences = listOf("a,b", "c|d", "e\nf", "\u00e4\ud83e\udde0", "\ud800"),
            contradictionReferences = listOf("x,y", "z\n\u0000"))
        val mutation = ProofCarryingMemoryMutation(payload, signer.sign(payload.canonicalBytes()))
        file.writeText(wireV2(mutation) + "\n")
        val restored = journal.entries().single()
        assertEquals(payload, restored.payload)
        assertArrayEquals(mutation.signature, restored.signature)
    }

    @Test fun repartitionedV2EvidenceCannotReuseASignature() = withJournal { journal, file, signer ->
        val original = payload().copy(evidenceReferences = listOf("source:a,source:b"))
        val signature = signer.sign(original.canonicalBytes())
        val forged = ProofCarryingMemoryMutation(original.copy(evidenceReferences = listOf("source:a", "source:b")), signature)
        file.writeText(wireV2(forged) + "\n")
        assertEquals(CorrectionAuditStatus.TAMPERED, journal.audit(emptyList()).status)
    }

    @Test fun unsignedRecordChangesAreDetectedWithBothHashVersions() {
        for (legacy in listOf(true, false)) withJournal(legacy) { journal, _, _ ->
            if (!legacy) appendFirst(journal)
            val originals = if (legacy) facts() else facts().take(1)
            val altered = originals.first().copy(status = LearningStatus.REVOKED, updatedAt = 3_000L)
            val audit = journal.audit(listOf(altered) + originals.drop(1))
            assertEquals(CorrectionAuditStatus.UNSIGNED_LEDGER_CHANGE, audit.status)
            assertEquals(listOf(altered.id), audit.unsignedRecordIds)
        }
    }

    @Test fun canonicalVersionIsBoundAndUnknownVersionsAreRejected() {
        val signer = CountingSigner()
        val original = payload()
        val signature = signer.sign(original.canonicalBytes())
        assertFalse(ProofCarryingMemoryMutation(original.copy(schemaVersion = 1), signature).verify(signer))
        assertThrows(IllegalArgumentException::class.java) { original.copy(schemaVersion = 3) }
        assertThrows(IllegalArgumentException::class.java) {
            memoryRecordHash(SignedOwnerCorrectionJournal.toMemoryRecord(facts().first()), 0)
        }
    }

    private fun payload() = MemoryMutationPayload("mutation-1", "record-1", null, "a".repeat(64),
        MemoryMutationReason.NEW_EVIDENCE, ProvenanceType.USER_PROVIDED,
        listOf("source:a"), emptyList(), null, 1L)

    private fun wireV2(mutation: ProofCarryingMemoryMutation): String {
        fun field(value: String): String {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { it.writeChars(value) }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray())
        }
        val p = mutation.payload
        return listOf("v2", p.mutationId, field(p.recordId), p.beforeHash ?: "-", p.afterHash,
            p.reason.name, p.provenance.name, p.evidenceReferences.joinToString(",") { field(it) },
            p.contradictionReferences.joinToString(",") { field(it) }, p.previousMutationHash ?: "-",
            p.createdAtMillis.toString(), Base64.getUrlEncoder().withoutPadding().encodeToString(mutation.signature))
            .joinToString("\t")
    }
}
