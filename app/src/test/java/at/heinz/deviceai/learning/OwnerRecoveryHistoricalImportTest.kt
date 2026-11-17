package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The Android startup preference mirror is history, never a new explicit owner decision. */
class OwnerRecoveryHistoricalImportTest {
    private fun fixture(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("owner-historical-import-").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    @Test fun stalePreferenceCannotRecreateARevokedIdentityLostWithItsSegment() = fixture { directory ->
        val stale = "Ich wohne in Steyr."
        val records = (0 until 300).map { index ->
            LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
                LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED,
                LearningStatus.CONFIRMED, "Meine synthetische Prüfkennung ist Nummer $index.", now = index.toLong())
        } + LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
            LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.CONFIRMED, stale, now = 5000L)
        val storeDirectory = File(directory, "learning-core-v2")
        val store = FileLearningExperienceStore(storeDirectory)
        assertEquals(records.size, store.appendMissing(records))
        val staleId = records.last().id
        assertEquals(1, ContinuumLearningCore(directory).revokeOwnerFact(stale))
        val segment = File(storeDirectory, "experiences-v3").listFiles()!!.single { file ->
            file.extension == "seg" && LearningExperienceCodec.decode(file.readBytes()).any {
                it.id == staleId && it.status == LearningStatus.REVOKED
            }
        }
        val destroyed = ByteArray(segment.length().toInt()) { 0x7f }
        segment.writeBytes(destroyed)

        val recovered = ContinuumLearningCore.withAutomaticRecovery(directory)
        assertTrue(recovered.activeOwnerFacts().isEmpty())
        assertFalse("the lost revocation identity is genuinely absent from the recovered ledger",
            store.all().any { it.id == staleId })
        assertTrue(recovered.recoveredOwnerFactsForReview().facts.isNotEmpty())
        assertEquals("an old preference is not a fresh owner confirmation", 0,
            recovered.syncOwnerFacts(listOf(stale)))
        assertFalse(store.all().any { it.id == staleId })
        assertTrue(ContinuumLearningCore(directory).activeOwnerFacts().isEmpty())
        assertTrue(File(storeDirectory, "owner-recovery").walkTopDown().any {
            it.isFile && it.name == segment.name && it.readBytes().contentEquals(destroyed)
        })

        assertEquals("a genuinely new explicit owner action remains available",
            OwnerFactChange.ADDED, recovered.confirmOwnerFact(stale))
        assertEquals(listOf(stale), recovered.activeOwnerFacts().map { it.content })
    }

    @Test fun completedReviewDoesNotTurnAnEmptyRecoveryMarkerIntoLegacyImportConsent() = fixture { directory ->
        val original = ContinuumLearningCore(directory)
        val historical = listOf("Ich wohne in Steyr.", "Ich trinke gerne Tee.", "Mein Hund heißt Bruno.")
        historical.forEach(original::confirmOwnerFact)
        val primary = File(directory, "learning-core-v2/experiences-v2.bin")
        primary.writeBytes(primary.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val recovered = ContinuumLearningCore.withAutomaticRecovery(directory)
        val review = recovered.recoveredOwnerFactsForReview()
        assertEquals(2, review.facts.size)
        recovered.reconfirmRecoveredOwnerFacts(review, OwnerReconfirmationProof(
            OwnerConfirmationSource.OWNER_PILL, true, "owner-explicit-review", System.currentTimeMillis(),
            review.fingerprint, review.facts.associate { it.id to it.contentHash }))
        assertTrue(recovered.recoveredOwnerFactsForReview().facts.isEmpty())
        assertTrue(File(directory, "learning-core-v2/${OwnerMemoryRecovery.MARKER_NAME}").length() > 40)
        val approved = recovered.activeOwnerFacts().map { it.id }.toSet()
        val restartedStrictReader = ContinuumLearningCore(directory)
        assertEquals("the complete empty marker retains recovery history", 0,
            restartedStrictReader.syncOwnerFacts(historical))
        assertEquals(approved, restartedStrictReader.activeOwnerFacts().map { it.id }.toSet())
        assertFalse(restartedStrictReader.activeOwnerFacts().any { it.content == historical.last() })
    }

    @Test fun ordinaryLegacyMigrationStillWorksBeforeAnyRecoveryAndKeepsRevocations() = fixture { directory ->
        val core = ContinuumLearningCore(directory)
        val historical = listOf("Ich wohne in Steyr.", "Ich trinke gerne Tee.")
        assertEquals(2, core.syncOwnerFacts(historical))
        assertEquals(historical.toSet(), core.activeOwnerFacts().map { it.content }.toSet())
        assertEquals(0, core.syncOwnerFacts(historical))
        assertEquals(1, core.revokeOwnerFact(historical.first()))
        assertEquals(0, core.syncOwnerFacts(historical))
        assertEquals(listOf(historical.last()), core.activeOwnerFacts().map { it.content })
        assertFalse(File(directory, "learning-core-v2/${OwnerMemoryRecovery.MARKER_NAME}").exists())
    }
}
