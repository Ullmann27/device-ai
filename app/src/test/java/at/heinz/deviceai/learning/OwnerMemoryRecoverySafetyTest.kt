package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

class OwnerMemoryRecoverySafetyTest {
    private fun fixture(block:(File)->Unit) {
        val dir=Files.createTempDirectory("owner-recovery-safety-").toFile()
        try { block(dir) } finally {
            OwnerMemoryRecovery.beforeWriteForTest=null
            dir.deleteRecursively()
        }
    }
    private fun primary(dir:File)=File(dir,"learning-core-v2/experiences-v2.bin")
    private fun marker(dir:File)=File(dir,"learning-core-v2/${OwnerMemoryRecovery.MARKER_NAME}")
    private fun corrupt(dir:File):ByteArray = primary(dir).readBytes().also {
        it[it.lastIndex]=(it.last().toInt() xor 0x55).toByte();primary(dir).writeBytes(it)
    }
    private fun damagedFacts(dir:File):ByteArray {
        val core=ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr.")
        core.confirmOwnerFact("Mein Hund heißt Bruno.")
        core.confirmOwnerFact("Ich trinke gerne Tee.")
        return corrupt(dir)
    }
    private fun recovered(dir:File):ContinuumLearningCore = ContinuumLearningCore.withAutomaticRecovery(dir).also {
        assertTrue(it.activeOwnerFacts().isEmpty())
    }
    private fun proof(review:OwnerRecoveryReview, facts:List<RecoveredOwnerFact> = review.facts) = OwnerReconfirmationProof(
        OwnerConfirmationSource.OWNER_PILL,true,"owner-recovery-turn",System.currentTimeMillis(),
        review.fingerprint,facts.associate { it.id to it.contentHash })

    @Test fun ownerDecisionReconfirmsOnlyTheConcreteDisplayedSubset() = fixture { dir ->
        damagedFacts(dir)
        val core=recovered(dir)
        val review=core.recoveredOwnerFactsForReview()
        assertEquals(2,review.facts.size)
        val chosen=review.facts.single { it.content.contains("Steyr") }
        val decision=proof(review,listOf(chosen))
        assertEquals(setOf(chosen.id),core.reconfirmRecoveredOwnerFacts(review,decision))
        assertEquals(listOf(chosen.id),core.activeOwnerFacts().map { it.id })
        assertEquals(listOf(chosen.id),ContinuumLearningCore.withAutomaticRecovery(dir).activeOwnerFacts().map { it.id })
        assertEquals(1,core.recoveredOwnerFactsForReview().facts.size)
        assertThrows(SecurityException::class.java) { core.reconfirmRecoveredOwnerFacts(review,decision) }
        val remaining=core.recoveredOwnerFactsForReview()
        core.reconfirmRecoveredOwnerFacts(remaining,proof(remaining))
        assertTrue("an empty committed marker remains complete and durable",marker(dir).length()>40)
        assertEquals(2,ContinuumLearningCore(dir).activeOwnerFacts().size)
    }

    @Test fun noDeniedForeignOrUnboundDecisionCanReconfirm() = fixture { dir ->
        damagedFacts(dir)
        val core=recovered(dir);val review=core.recoveredOwnerFactsForReview();val allowed=proof(review)
        val forbidden=listOf(null,allowed.copy(approvedByOwner=false),allowed.copy(turnId=""),
            allowed.copy(source=OwnerConfirmationSource.MODEL_TOOL),
            allowed.copy(source=OwnerConfirmationSource.FOREIGN_CONTENT),
            allowed.copy(confirmedFactHashes=emptyMap()),allowed.copy(reviewFingerprint="stale"),
            allowed.copy(confirmedFactHashes=mapOf(review.facts.first().id to "0".repeat(64))))
        for(decision in forbidden) {
            assertThrows(SecurityException::class.java) { core.reconfirmRecoveredOwnerFacts(review,decision) }
            assertTrue(core.activeOwnerFacts().isEmpty())
        }
    }

    @Test fun pendingWriteFailureCannotPublishARecoveredPrimary() = fixture { dir ->
        val damaged=damagedFacts(dir)
        OwnerMemoryRecovery.beforeWriteForTest={ if(it.name==OwnerMemoryRecovery.MARKER_NAME) throw IOException("synthetic marker failure") }
        assertThrows(IOException::class.java) { ContinuumLearningCore.withAutomaticRecovery(dir).activeOwnerFacts() }
        assertArrayEquals(damaged,primary(dir).readBytes())
        OwnerMemoryRecovery.beforeWriteForTest=null
        assertTrue(recovered(dir).activeOwnerFacts().isEmpty())
        assertTrue(ContinuumLearningCore(dir).activeOwnerFacts().isEmpty())
    }

    @Test fun primaryPublicationFailureLeavesADurableConsentBarrier() = fixture { dir ->
        val damaged=damagedFacts(dir)
        OwnerMemoryRecovery.beforeWriteForTest={ if(it.name=="experiences-v2.bin") throw IOException("synthetic publication failure") }
        assertThrows(IOException::class.java) { ContinuumLearningCore.withAutomaticRecovery(dir).activeOwnerFacts() }
        assertArrayEquals(damaged,primary(dir).readBytes())
        assertTrue(marker(dir).length()>40)
        OwnerMemoryRecovery.beforeWriteForTest=null
        assertTrue(recovered(dir).activeOwnerFacts().isEmpty())
    }

    @Test fun failedReconfirmationDoesNotReturnSuccessOrEmitActivation() = fixture { dir ->
        damagedFacts(dir)
        val core=recovered(dir);val review=core.recoveredOwnerFactsForReview()
        val before=marker(dir).readBytes()
        val events=CopyOnWriteArrayList<ContinuumMemoryEvent>()
        val hub=ContinuumMemoryEventHub.forApp(dir)
        val subscription=hub.addListener(ContinuumMemoryEventListener { events+=it })
        try {
            OwnerMemoryRecovery.beforeWriteForTest={ if(it.name==OwnerMemoryRecovery.MARKER_NAME) throw IOException("synthetic confirmation failure") }
            assertThrows(IOException::class.java) { core.reconfirmRecoveredOwnerFacts(review,proof(review)) }
            hub.awaitIdle(2000)
            assertTrue(events.isEmpty())
            assertArrayEquals(before,marker(dir).readBytes())
            assertTrue(ContinuumLearningCore(dir).activeOwnerFacts().isEmpty())
        } finally { subscription.close() }
    }

    @Test fun absentEmptyTruncatedOrTamperedMarkerNeverMeansUnrestricted() = fixture { dir ->
        damagedFacts(dir);val core=recovered(dir);val intact=marker(dir).readBytes()
        val variants=listOf(ByteArray(0),intact.copyOf(16),intact.copyOf().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() })
        for(bytes in variants) {
            marker(dir).writeBytes(bytes)
            assertThrows(Exception::class.java) { core.activeOwnerFacts() }
            assertThrows(Exception::class.java) { core.temporalMemoryView() }
            assertThrows(Exception::class.java) { core.recoveredOwnerFactsForReview() }
            marker(dir).writeBytes(intact)
            assertTrue(core.activeOwnerFacts().isEmpty())
        }
        assertTrue(marker(dir).delete())
        assertThrows(Exception::class.java) { core.activeOwnerFacts() }
        assertThrows(Exception::class.java) { ContinuumLearningCore(dir).activeOwnerFacts() }
    }

    @Test fun temporalCacheTracksPendingEvenWhenRecoveryRestoresItsExactOldLedgerBytes() = fixture { dir ->
        val core=ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr.")
        val oldBytes=primary(dir).readBytes()
        val originalAnswer=core.temporalMemoryView().answer("Wo wohne ich?",System.currentTimeMillis())
        assertNotNull("the supported question is parsed",originalAnswer)
        assertEquals(TemporalAnswerKind.ANSWER,originalAnswer!!.kind)
        core.confirmOwnerFact("Mein Hund heißt Bruno.")
        corrupt(dir)
        val automatic=recovered(dir)
        assertArrayEquals(oldBytes,primary(dir).readBytes())
        val heldAnswer=core.temporalMemoryView().answer("Wo wohne ich?",System.currentTimeMillis())
        assertNotNull("the question remains supported while consent is held",heldAnswer)
        assertEquals(TemporalAnswerKind.UNCONFIRMED_ONLY,heldAnswer!!.kind)
        assertTrue("no recovered fact is cited as confirmed",heldAnswer.citedIds.isEmpty())
        val review=automatic.recoveredOwnerFactsForReview()
        automatic.reconfirmRecoveredOwnerFacts(review,proof(review))
        val confirmedAnswer=core.temporalMemoryView().answer("Wo wohne ich?",System.currentTimeMillis())
        assertNotNull(confirmedAnswer)
        assertEquals(TemporalAnswerKind.ANSWER,confirmedAnswer!!.kind)
    }

    @Test fun changedLedgerInvalidatesAnOutstandingOwnerReview() = fixture { dir ->
        damagedFacts(dir);val core=recovered(dir);val review=core.recoveredOwnerFactsForReview()
        core.confirmOwnerFact("Meine Lieblingsfarbe ist Blau.")
        assertThrows(SecurityException::class.java) { core.reconfirmRecoveredOwnerFacts(review,proof(review)) }
        assertEquals(listOf("Meine Lieblingsfarbe ist Blau."),core.activeOwnerFacts().map { it.content })
    }

    @Test fun verifiedRevocationsCannotBeReconfirmedThroughRecovery() = fixture { dir ->
        val core=ContinuumLearningCore(dir)
        core.confirmOwnerFact("Ich wohne in Steyr.")
        core.confirmOwnerFact("Ich trinke gerne Tee.")
        val revoked=core.activeOwnerFacts().single { it.content.contains("Tee") }
        core.revokeOwnerFact(revoked.content)
        core.confirmOwnerFact("Mein Hund heißt Bruno.")
        corrupt(dir)
        val automatic=recovered(dir);val review=automatic.recoveredOwnerFactsForReview()
        assertEquals(1,review.facts.size)
        val attempted=proof(review).copy(confirmedFactHashes=mapOf(revoked.id to revoked.contentHash))
        assertThrows(SecurityException::class.java) { automatic.reconfirmRecoveredOwnerFacts(review,attempted) }
        assertEquals(LearningStatus.REVOKED,FileLearningExperienceStore(File(dir,"learning-core-v2")).all().single { it.id==revoked.id }.status)
    }

    @Test fun segmentedRecoveryUsesVerifiedSegmentsAndPreservesVersionOne() = fixture { dir ->
        val directory=File(dir,"learning-core-v2")
        val records=(0..256).map { index -> LearningExperienceFactory.create(
            LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.CONFIRMED,"Synthetic owner record $index",now=index.toLong()) }
        FileLearningExperienceStore(directory).appendMissing(records)
        val originalManifest=primary(dir).readBytes()
        val segment=File(directory,"experiences-v3").listFiles()!!.first { it.extension=="seg" }
        val removed=LearningExperienceCodec.decode(segment.readBytes())
        val damaged=segment.readBytes().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
        segment.writeBytes(damaged)
        val core=recovered(dir)
        val restored=FileLearningExperienceStore(directory).all()
        assertEquals(records.size-removed.size,restored.size)
        assertEquals(records.map { it.id }.toSet()-removed.map { it.id }.toSet(),restored.map { it.id }.toSet())
        assertTrue(restored.all { it.status==LearningStatus.OBSERVED })
        assertEquals(0x4c455833,ByteBuffer.wrap(primary(dir).readBytes()).int)
        assertEquals(1,ByteBuffer.wrap(primary(dir).readBytes(),4,4).int)
        assertTrue(File(directory,"owner-recovery").walkTopDown().filter { it.isFile && it.name=="primary.bin" }
            .any { it.readBytes().contentEquals(originalManifest) })
        assertTrue(File(directory,"owner-recovery").walkTopDown().filter { it.isFile && it.name==segment.name }
            .any { it.readBytes().contentEquals(damaged) })
        assertEquals(restored.size,core.recoveredOwnerFactsForReview().facts.size)
    }

    @Test fun unsupportedNewerLedgerVersionIsNeverDowngradedByRecovery() = fixture { dir ->
        damagedFacts(dir)
        val newer=ByteBuffer.allocate(64).putInt(0x4c455833).putInt(3).array()
        primary(dir).writeBytes(newer)
        assertThrows(Exception::class.java) { ContinuumLearningCore.withAutomaticRecovery(dir).activeOwnerFacts() }
        assertArrayEquals(newer,primary(dir).readBytes())
        assertFalse(marker(dir).exists())
    }

    @Test fun noVerifiedSourceCannotBeReplacedWithAnEmptyLedger() = fixture { dir ->
        ContinuumLearningCore(dir).confirmOwnerFact("Ich wohne in Steyr.")
        val damaged=corrupt(dir)
        assertThrows(Exception::class.java) { ContinuumLearningCore.withAutomaticRecovery(dir).activeOwnerFacts() }
        assertArrayEquals(damaged,primary(dir).readBytes())
        assertFalse(marker(dir).exists())
    }

    @Test fun markerSymlinkCannotReadOrChangeAnotherFile() = fixture { dir ->
        damagedFacts(dir);val core=recovered(dir)
        val outside=File(dir,"synthetic-unrelated.bin").also { it.writeText("must remain unchanged") }
        val before=outside.readBytes()
        assertTrue(marker(dir).delete())
        Files.createSymbolicLink(marker(dir).toPath(),outside.toPath())
        assertThrows(Exception::class.java) { core.activeOwnerFacts() }
        assertArrayEquals(before,outside.readBytes())
    }
}
