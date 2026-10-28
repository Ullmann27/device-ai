package at.heinz.deviceai.learning

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class PersonalLearningTransferTest {
    private val source = "at.heinz.deviceai.mentorpaired"
    private fun fact(text: String, status: LearningStatus = LearningStatus.CONFIRMED, now: Long = 100L) =
        LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
            LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED, status, text, now = now)
    private fun failure(block: () -> Unit) {
        try { block(); fail("Invalid backup was accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun realLedgerExportPreservesFactsAndRevocationsWithoutChangingSource() {
        val dir = Files.createTempDirectory("personal-transfer-source").toFile()
        try {
            val store = FileLearningExperienceStore(dir)
            val active = fact("Synthetic project uses local evidence.")
            val revoked = fact("Synthetic project uses old evidence.", LearningStatus.REVOKED)
            store.append(active); store.append(revoked)
            val before = dir.resolve("experiences-v2.bin").readBytes()
            val bytes = PersonalLearningTransfer.export(source, store)
            val backup = PersonalLearningTransfer.inspect(bytes)
            assertEquals(source, backup.claimedSourcePackage)
            assertEquals(setOf(active, revoked), backup.records.toSet())
            assertTrue(backup.sha256.matches(Regex("[a-f0-9]{64}")))
            assertArrayEquals(before, dir.resolve("experiences-v2.bin").readBytes())
            assertArrayEquals(bytes, PersonalLearningTransfer.encode(source, listOf(revoked, active)))
        } finally { dir.deleteRecursively() }
    }

    @Test fun targetRevocationCannotBeUndoneEvenByNewerLookingBackup() {
        val old = fact("Synthetic owner fact.", LearningStatus.REVOKED, 100L)
        val newer = fact("Synthetic owner fact.", LearningStatus.CONFIRMED, 900L)
        val plan = PersonalLearningTransfer.preview(PersonalLearningTransfer.encode(source, listOf(newer)), listOf(old))
        assertEquals(listOf(old.id), plan.blockedReactivations)
        assertTrue(plan.additionsForReview.isEmpty())
        assertTrue(plan.revocationsForReview.isEmpty())
        assertFalse(plan.canApplyAutomatically)
        assertTrue(plan.requiresPerFactOwnerConfirmation)
    }

    @Test fun incomingRevocationsAndMissingTombstonesArePreservedForExplicitReview() {
        val active = fact("Synthetic active fact.")
        val revoked = active.copy(status = LearningStatus.REVOKED, updatedAt = 200L)
        val missing = fact("Synthetic already revoked elsewhere.", LearningStatus.REVOKED)
        val plan = PersonalLearningTransfer.preview(PersonalLearningTransfer.encode(source, listOf(revoked, missing)), listOf(active))
        assertEquals(listOf(active.id), plan.revocationsForReview)
        assertEquals(listOf(missing), plan.additionsForReview)
        assertFalse(plan.canApplyAutomatically)
    }

    @Test fun previewIsIdempotentAndDoesNotMutateActualTargetLedger() {
        val dir = Files.createTempDirectory("personal-transfer-target").toFile()
        try {
            val store = FileLearningExperienceStore(dir)
            val existing = fact("Synthetic known fact.")
            val added = fact("Synthetic new fact.")
            store.append(existing)
            val before = dir.resolve("experiences-v2.bin").readBytes()
            val bytes = PersonalLearningTransfer.encode(source, listOf(existing, added))
            val first = PersonalLearningTransfer.preview(bytes, store)
            val second = PersonalLearningTransfer.preview(bytes, store)
            assertEquals(first, second)
            assertEquals(1, first.duplicateCount)
            assertEquals(listOf(added), first.additionsForReview)
            assertArrayEquals(before, dir.resolve("experiences-v2.bin").readBytes())
            assertEquals(listOf(existing), store.all())
        } finally { dir.deleteRecursively() }
    }

    @Test fun sourceClaimsTrainingAndCommandsNeverBecomeAutomaticallyConfirmedFacts() {
        val research = LearningExperienceFactory.create(LearningExperienceType.SOURCE_TEXT,
            LearningProvenance.REVIEWED_WEB_SOURCE, LearningTrustClass.SOURCE_OBSERVATION,
            LearningStatus.OBSERVED, "Synthetic research observation", sourceRef = "https://example.org/fixture", now = 100L)
        val command = LearningExperienceFactory.create(LearningExperienceType.COMMAND_MAPPING,
            LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.CONFIRMED, "Synthetic command", annotation = "get_storage_info", now = 100L)
        val plan = PersonalLearningTransfer.preview(PersonalLearningTransfer.encode(source, listOf(research, command)), emptyList())
        assertEquals(2, plan.reviewOnlyCount)
        assertEquals(2, plan.backup.records.size)
        assertTrue(plan.additionsForReview.isEmpty())
        assertFalse(plan.canApplyAutomatically)
    }

    @Test fun malformedChecksumTrailingDataUnknownSchemaAndHugeInputAreRejected() {
        val bytes = PersonalLearningTransfer.encode(source, listOf(fact("Synthetic fact.")))
        failure { PersonalLearningTransfer.inspect(bytes.copyOf().also { it[12] = (it[12].toInt() xor 1).toByte() }) }
        failure { PersonalLearningTransfer.inspect(bytes + byteArrayOf(0)) }
        val payload = bytes.copyOfRange(0, bytes.size - 32)
        payload[7] = 2 // Unknown schema with an otherwise valid checksum.
        failure { PersonalLearningTransfer.inspect(payload + MessageDigest.getInstance("SHA-256").digest(payload)) }
        failure { PersonalLearningTransfer.inspect(ByteArray(PersonalLearningTransfer.MAX_BYTES + 1)) }
    }

    @Test fun boundedCountsDuplicateIdsAndInvalidSourceAreRejected() {
        val item = fact("Synthetic fact.")
        failure { PersonalLearningTransfer.encode(source, listOf(item, item)) }
        failure { PersonalLearningTransfer.encode("not a package", listOf(item)) }
        failure { PersonalLearningTransfer.encode(source, listOf(fact("Malformed unicode \uD800"))) }
        val payload = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(0x434c5054); out.writeInt(1)
                val name = source.toByteArray(Charsets.UTF_8)
                out.writeInt(name.size); out.write(name)
                out.writeInt(FileLearningExperienceStore.MAX_RECORDS + 1)
            }
        }.toByteArray()
        failure { PersonalLearningTransfer.inspect(payload + MessageDigest.getInstance("SHA-256").digest(payload)) }
    }

    @Test fun capacityPressureIsExplicitAndDoesNotEvictExistingProtectedRecords() {
        val target = (1..FileLearningExperienceStore.MAX_RECORDS).map { fact("Synthetic existing fact $it.") }
        val bytes = PersonalLearningTransfer.encode(source, listOf(fact("Synthetic extra fact.")))
        val plan = PersonalLearningTransfer.preview(bytes, target)
        assertTrue(plan.capacityExceeded)
        assertFalse(plan.canApplyAutomatically)
        assertEquals(FileLearningExperienceStore.MAX_RECORDS, target.size)
    }
}
