package at.heinz.deviceai.learning

import at.heinz.deviceai.cognitive.IdentitySigner
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LegacyJournalFixtureMain {
    @JvmStatic fun main(args: Array<String>) {
        val signer = object : IdentitySigner {
            override fun sign(payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec("canonical-memory-test-only-key".toByteArray(), "HmacSHA256"))
                doFinal(payload)
            }
            override fun verify(payload: ByteArray, signature: ByteArray) =
                MessageDigest.isEqual(sign(payload), signature)
        }
        fun fact(content: String, at: Long) = LearningExperienceFactory.create(
            LearningExperienceType.USER_FACT, LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, content, now = at)
        val first = fact("Das blaue Testobjekt steht in Halle A.", 1_000L)
        val second = fact("Das rote Testobjekt steht in Halle B.", 2_000L)
        val journal = SignedOwnerCorrectionJournal(File(args.single()), signer)
        journal.recordDiff(emptyMap(), mapOf(first.id to first), 1_000L)
        journal.recordDiff(mapOf(first.id to first), mapOf(first.id to first, second.id to second), 2_000L)
        print(File(args.single(), SignedOwnerCorrectionJournal.FILE_NAME).readText())
        println("HASHES=" + journal.entries().joinToString(",") { it.payload.hash() })
        println("AUDIT=" + journal.audit(listOf(first, second)).status)
    }
}
