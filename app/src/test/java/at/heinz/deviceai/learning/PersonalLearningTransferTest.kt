package at.heinz.deviceai.learning

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class PersonalLearningTransferTest {
    @Test fun documentReaderBoundsEmptyReadsSizeAndInterruption() {
        val stalled = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
        }
        failure { PersonalLearningTransfer.readBackup(stalled) }
        failure { PersonalLearningTransfer.readBackup(java.io.ByteArrayInputStream(
            ByteArray(PersonalLearningTransfer.MAX_BYTES + 1))) }
        try {
            Thread.currentThread().interrupt()
            try {
                PersonalLearningTransfer.readBackup(java.io.ByteArrayInputStream(byteArrayOf(1)))
                fail("Interrupted read continued")
            } catch (_: java.io.InterruptedIOException) { }
        } finally { Thread.interrupted() }
    }

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
                out.writeInt(FileLearningExperienceStore.MAX_DURABLE_RECORDS + 1)
            }
        }.toByteArray()
        failure { PersonalLearningTransfer.inspect(payload + MessageDigest.getInstance("SHA-256").digest(payload)) }
    }

    @Test fun segmentedLedgerExportRoundTripsRevocationAndLargeTargetPreview() {
        val dir=Files.createTempDirectory("personal-transfer-lex3").toFile()
        try {
            val records=(0 until 1000).map { fact("Synthetic transfer record $it",now=it.toLong()) }
            val store=FileLearningExperienceStore(dir)
            assertEquals(1000,store.appendMissing(records))
            store.updateStatus(records[19].id,LearningStatus.REVOKED,2000)
            val exported=PersonalLearningTransfer.export(source,store)
            assertEquals(store.all().toSet(),PersonalLearningTransfer.inspect(exported).records.toSet())
            val plan=PersonalLearningTransfer.preview(exported,store)
            assertEquals(1000,plan.duplicateCount)
            assertFalse(plan.capacityExceeded)
            assertFalse(plan.canApplyAutomatically)
        } finally { dir.deleteRecursively() }
    }

    @Test fun nearMaximumLex3BytesExportCompletelyWithMaximumPackageLength() {
        val dir=Files.createTempDirectory("personal-transfer-byte-boundary").toFile()
        try {
            fun large(index:Int,chars:Int)=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
                LearningProvenance.SYNTHETIC_FIXTURE,LearningTrustClass.OWNER_CONFIRMED,
                LearningStatus.CONFIRMED,index.toString().padStart(4,'0')+"界".repeat(chars),
                "界".repeat(160),"界".repeat(500),index.toLong())
            val count=FileLearningExperienceStore.MAX_DURABLE_RECORDS
            val perRecord=LearningExperienceCodec.encode(listOf(large(0,1900))).size-40
            val baselineBytes=count.toLong()*perRecord+(count/32)*40
            var remaining=((Lex3LearningExperienceStore.MAX_TOTAL_BYTES-1024-baselineBytes)/3).toInt()
            val records=(0 until count).map { index ->
                val extra=minOf(remaining,144);remaining-=extra;large(index,1900+extra)
            }
            assertEquals(0,remaining)
            val segmentBytes=records.chunked(32).sumOf { LearningExperienceCodec.encode(it).size.toLong() }
            assertTrue(segmentBytes<Lex3LearningExperienceStore.MAX_TOTAL_BYTES)
            assertTrue(segmentBytes>Lex3LearningExperienceStore.MAX_TOTAL_BYTES-2048)
            val store=FileLearningExperienceStore(dir)
            assertEquals(count,store.appendMissing(records))
            val maxPackage="a."+"b".repeat(254)
            val exported=PersonalLearningTransfer.export(maxPackage,FileLearningExperienceStore(dir))
            assertTrue(exported.size>Lex3LearningExperienceStore.MAX_TOTAL_BYTES)
            val actual=PersonalLearningTransfer.inspect(exported)
            assertEquals(maxPackage,actual.claimedSourcePackage)
            assertEquals(records.toSet(),actual.records.toSet())
            println("LEX3_TRANSFER_METRIC records=$count segment_bytes=$segmentBytes export_bytes=${exported.size} " +
                "source_package_chars=${maxPackage.length} complete=true")
        } finally { dir.deleteRecursively() }
    }

    @Test fun capacityPressureIsExplicitAndDoesNotEvictExistingProtectedRecords() {
        val target = (1..FileLearningExperienceStore.MAX_DURABLE_RECORDS).map { fact("Synthetic existing fact $it.") }
        val bytes = PersonalLearningTransfer.encode(source, listOf(fact("Synthetic extra fact.")))
        val plan = PersonalLearningTransfer.preview(bytes, target)
        assertTrue(plan.capacityExceeded)
        assertFalse(plan.canApplyAutomatically)
        assertEquals(FileLearningExperienceStore.MAX_DURABLE_RECORDS, target.size)
    }
}
