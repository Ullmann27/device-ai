package at.heinz.deviceai.learning

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Uses real LEX2 persistence, never a synthetic replacement for the project codec. */
class HistoricalLearningLedgerTest {
    private fun fact(status: LearningStatus, now: Long = 100L) =
        LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
            LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED,
            status, "Synthetic historical owner lesson.", now = now)

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Invalid historical ledger accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun original256RecordSnapshotIsReadOnlyAndRetainsEveryStatus() {
        val dir = Files.createTempDirectory("historical-ledger").toFile()
        try {
            val source = FileLearningExperienceStore(dir)
            val revoked = fact(LearningStatus.REVOKED)
            source.append(revoked)
            source.appendMissing((1..255).map { index ->
                LearningExperienceFactory.create(LearningExperienceType.TRAINING_AUDIT,
                    LearningProvenance.LOCAL_TRAINING, LearningTrustClass.SYSTEM_MEASUREMENT,
                    LearningStatus.REJECTED, "Synthetic audit $index", now = index.toLong())
            })
            val expected = source.all().toSet() // verified open (may rotate rolling backups) before the read-only check
            val primary = dir.resolve("experiences-v2.bin")
            val bytes = primary.readBytes()
            val beforeFiles = dir.listFiles()!!.associate { it.name to it.readBytes().toList() }
            val inspected = PersonalLearningTransfer.inspectDocument(bytes)
            assertEquals(256, inspected.records.size)
            assertEquals(expected, inspected.records.toSet())
            assertEquals(1, inspected.records.count { it.status == LearningStatus.REVOKED })
            assertEquals("unverified.legacy", inspected.claimedSourcePackage)
            assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }, inspected.sha256)
            assertEquals(beforeFiles, dir.listFiles()!!.associate { it.name to it.readBytes().toList() })
            assertArrayEquals(bytes, primary.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rawPreviewProtectsExistingRevocationEvenAgainstNewerActiveSource() {
        val dir = Files.createTempDirectory("historical-active").toFile()
        try {
            val source = FileLearningExperienceStore(dir)
            source.append(fact(LearningStatus.CONFIRMED, 900L))
            val target = fact(LearningStatus.REVOKED, 100L)
            val bytes = dir.resolve("experiences-v2.bin").readBytes()
            val plan = PersonalLearningTransfer.previewDocument(bytes, listOf(target))
            assertEquals(listOf(target.id), plan.blockedReactivations)
            assertTrue(plan.additionsForReview.isEmpty())
            assertFalse(plan.canApplyAutomatically)
            assertTrue(plan.requiresPerFactOwnerConfirmation)
        } finally { dir.deleteRecursively() }
    }

    @Test fun rawRevocationNeverDisappearsFromReviewAndPreviewDoesNotWriteTarget() {
        val sourceDir = Files.createTempDirectory("historical-revoked").toFile()
        val targetDir = Files.createTempDirectory("historical-target").toFile()
        try {
            val source = FileLearningExperienceStore(sourceDir)
            source.append(fact(LearningStatus.REVOKED, 200L))
            val target = FileLearningExperienceStore(targetDir)
            target.append(fact(LearningStatus.CONFIRMED))
            val before = targetDir.resolve("experiences-v2.bin").readBytes()
            val bytes = sourceDir.resolve("experiences-v2.bin").readBytes()
            val first = PersonalLearningTransfer.previewDocument(bytes, target)
            assertEquals(listOf(fact(LearningStatus.CONFIRMED).id), first.revocationsForReview)
            assertEquals(first, PersonalLearningTransfer.previewDocument(bytes, target))
            assertArrayEquals(before, targetDir.resolve("experiences-v2.bin").readBytes())
        } finally { sourceDir.deleteRecursively(); targetDir.deleteRecursively() }
    }

    @Test fun portableFormatRemainsUnchangedAndRawFormatIsNotAcceptedAsPortable() {
        val item = fact(LearningStatus.CONFIRMED)
        val portable = PersonalLearningTransfer.encode("at.heinz.deviceai.continuumlocal", listOf(item))
        assertEquals(PersonalLearningTransfer.inspect(portable),
            PersonalLearningTransfer.inspectDocument(portable))
        val dir = Files.createTempDirectory("historical-format").toFile()
        try {
            FileLearningExperienceStore(dir).append(item)
            rejects { PersonalLearningTransfer.inspect(dir.resolve("experiences-v2.bin").readBytes()) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptTruncatedOversizedAndUnknownRawDocumentsFailClosed() {
        val dir = Files.createTempDirectory("historical-corrupt").toFile()
        try {
            FileLearningExperienceStore(dir).append(fact(LearningStatus.CONFIRMED))
            val bytes = dir.resolve("experiences-v2.bin").readBytes()
            rejects { PersonalLearningTransfer.inspectDocument(bytes.copyOf().also {
                it[20] = (it[20].toInt() xor 1).toByte()
            }) }
            rejects { PersonalLearningTransfer.inspectDocument(bytes.copyOf(39)) }
            rejects { PersonalLearningTransfer.inspectDocument(ByteArray(1024 * 1024 + 1)) }
            val payload = bytes.copyOfRange(0, bytes.size - 32)
            payload[3] = 0x34 // unknown LEX4 with an otherwise matching checksum.
            rejects { PersonalLearningTransfer.inspectDocument(payload +
                MessageDigest.getInstance("SHA-256").digest(payload)) }
        } finally { dir.deleteRecursively() }
    }
}
