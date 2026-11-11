import at.heinz.deviceai.research.voice.*
import java.nio.file.Files
import javax.crypto.KeyGenerator

object IndependentStoreReview {
    @JvmStatic fun main(args: Array<String>) {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val directory = Files.createTempDirectory("voice-independent-review-").toFile()
        val first = PersonalVoiceLearningStore(directory, key)
        val second = PersonalVoiceLearningStore(directory, key)
        fun example(number: Int) = PersonalVoiceExample(
            "synthetic-session", "Synthetische Korrektur $number", null, false,
            "Prüfe synthetischen Zweig $number", true, listOf("Testkontext"),
            listOf("task-$number"), "Prüfergebnis", listOf("Kein Merge"),
            "synthetic-independent-review", true, true, true
        )
        first.setConsent(VoiceLearningConsent(textTraining = true))
        second.setConsent(VoiceLearningConsent(textTraining = true))
        val one = example(1); val two = example(2)
        val oldApproval = VoiceTrainingApproval(one.digest, true)
        check(first.stage(one, oldApproval) == VoiceStageResult.STORED)
        check(first.stage(two, VoiceTrainingApproval(two.digest, true)) == VoiceStageResult.STORED)
        first.setConsent(VoiceLearningConsent())
        println("consent_revoked_on_first=" + !first.consent().textTraining)
        println("first_batch_after_revoke=" + first.reviewedBatch(2).size)
        println("second_batch_after_revoke=" + second.reviewedBatch(2).size)
        val three = example(3)
        println("second_stage_after_revoke=" + second.stage(three, VoiceTrainingApproval(three.digest, true)))
        second.delete(one.digest)
        println("replayed_approval_after_delete=" + second.stage(one, oldApproval))
        for (i in 4..PersonalVoiceLearningStore.MAX_RECORDS) {
            val e = example(i)
            check(second.stage(e, VoiceTrainingApproval(e.digest, true)) == VoiceStageResult.STORED)
        }
        println("records_at_capacity=" + second.ids().size)
        val revised = example(999)
        println("replace_at_capacity=" + second.replace(one.digest, revised, VoiceTrainingApproval(revised.digest, true)))
        println("original_survives_capacity_failure=" + (one.digest in second.ids()))
    }
}
