package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.IdentitySigner
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class SignedOwnerCorrectionJournalCoverageTest {
    private val signer=object:IdentitySigner {
        override fun sign(payload:ByteArray):ByteArray=Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec("journal-coverage-test-only".toByteArray(),"HmacSHA256"))
            doFinal(payload)
        }
        override fun verify(payload:ByteArray,signature:ByteArray)=
            MessageDigest.isEqual(sign(payload),signature)
    }

    @Test fun completelyMissingSignedFactsAreReported() = fixture { journal,file,facts ->
        val before=file.readBytes()
        val audit=journal.audit(emptyList())
        assertEquals(CorrectionAuditStatus.UNSIGNED_LEDGER_CHANGE,audit.status)
        assertEquals(2,audit.signedMutations)
        assertEquals(facts.map { it.id }.sorted(),audit.unsignedRecordIds)
        assertArrayEquals(before,file.readBytes())
    }

    @Test fun partiallyMissingSignedFactIsReported() = fixture { journal,_,facts ->
        val audit=journal.audit(facts.drop(1))
        assertEquals(CorrectionAuditStatus.UNSIGNED_LEDGER_CHANGE,audit.status)
        assertEquals(2,audit.signedMutations)
        assertEquals(listOf(facts.first().id),audit.unsignedRecordIds)
    }

    @Test fun missingSignedRevocationIsReported() = fixture { journal,_,facts ->
        val revoked=facts.first().copy(status=LearningStatus.REVOKED,updatedAt=3_000L)
        val after=listOf(revoked,facts.last())
        assertEquals(1,journal.recordDiff(facts.associateBy { it.id },after.associateBy { it.id },3_000L))
        assertEquals(CorrectionAuditStatus.VERIFIED,journal.audit(after).status)
        val audit=journal.audit(listOf(facts.last()))
        assertEquals(CorrectionAuditStatus.UNSIGNED_LEDGER_CHANGE,audit.status)
        assertEquals(3,audit.signedMutations)
        assertEquals(listOf(revoked.id),audit.unsignedRecordIds)
    }

    @Test fun missingAndChangedFactsAreReportedTogether() = fixture { journal,_,facts ->
        val changed=facts.last().copy(status=LearningStatus.REVOKED,updatedAt=3_000L)
        val audit=journal.audit(listOf(changed))
        assertEquals(CorrectionAuditStatus.UNSIGNED_LEDGER_CHANGE,audit.status)
        assertEquals(facts.map { it.id }.sorted(),audit.unsignedRecordIds)
        assertEquals(2,audit.unsignedRecordIds.distinct().size)
    }

    @Test fun completeSignedInventoryRemainsVerified() = fixture { journal,file,facts ->
        val before=file.readBytes()
        val audit=journal.audit(facts.reversed())
        assertEquals(CorrectionAuditStatus.VERIFIED,audit.status)
        assertEquals(2,audit.signedMutations)
        assertTrue(audit.unsignedRecordIds.isEmpty())
        assertArrayEquals(before,file.readBytes())
    }

    @Test fun unsignedObservedOwnerFactIsAllowed() = fixture { journal,_,facts ->
        val observed=fact("Vielleicht steht das gelbe Testobjekt in Raum C.",3_000L)
            .copy(status=LearningStatus.OBSERVED)
        val audit=journal.audit(facts+observed)
        assertEquals(CorrectionAuditStatus.VERIFIED,audit.status)
        assertEquals(2,audit.signedMutations)
        assertTrue(audit.unsignedRecordIds.isEmpty())
    }

    @Test fun unrelatedSourceTextDoesNotRequireOwnerSignature() = fixture { journal,_,facts ->
        val audit=journal.audit(facts+source())
        assertEquals(CorrectionAuditStatus.VERIFIED,audit.status)
        assertEquals(2,audit.signedMutations)
        assertTrue(audit.unsignedRecordIds.isEmpty())
    }

    @Test fun unrelatedSourceCannotCoverMissingOwnerFact() = fixture { journal,_,facts ->
        val audit=journal.audit(facts.drop(1)+source())
        assertEquals(CorrectionAuditStatus.UNSIGNED_LEDGER_CHANGE,audit.status)
        assertEquals(listOf(facts.first().id),audit.unsignedRecordIds)
    }

    @Test fun brokenSignatureChainTakesPriorityOverMissingFacts() = fixture { journal,file,_ ->
        file.writeText(file.readText().replaceFirst("NEW_EVIDENCE","CONSOLIDATION"))
        val before=file.readBytes()
        val audit=journal.audit(emptyList())
        assertEquals(CorrectionAuditStatus.TAMPERED,audit.status)
        assertEquals(0,audit.signedMutations)
        assertTrue(audit.unsignedRecordIds.isEmpty())
        assertArrayEquals(before,file.readBytes())
    }

    private fun fact(content:String,at:Long)=LearningExperienceFactory.create(
        LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,
        LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,content,now=at
    )

    private fun source()=LearningExperienceFactory.create(
        LearningExperienceType.SOURCE_TEXT,LearningProvenance.PUBLIC_WEB_SOURCE,
        LearningTrustClass.SOURCE_OBSERVATION,LearningStatus.OBSERVED,
        "Synthetischer fremder Quellenhinweis ohne Besitzerbestätigung.",
        sourceRef="https://example.org/journal-coverage",now=3_000L
    )

    private fun fixture(action:(SignedOwnerCorrectionJournal,File,List<LearningExperience>)->Unit) {
        val directory=Files.createTempDirectory("journal-coverage-").toFile()
        try {
            val journal=SignedOwnerCorrectionJournal(directory,signer)
            val facts=listOf(fact("Das graue Testobjekt steht in Raum A.",1_000L),
                fact("Das violette Testobjekt steht in Raum B.",2_000L))
            assertEquals(2,journal.recordDiff(emptyMap(),facts.associateBy { it.id },2_000L))
            action(journal,File(directory,SignedOwnerCorrectionJournal.FILE_NAME),facts)
        } finally { directory.deleteRecursively() }
    }
}
