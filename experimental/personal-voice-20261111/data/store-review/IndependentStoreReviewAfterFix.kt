import at.heinz.deviceai.research.voice.*
import java.nio.file.Files
import javax.crypto.KeyGenerator

object IndependentStoreReviewAfterFix {
    @JvmStatic fun main(args: Array<String>) {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val directory = Files.createTempDirectory("voice-independent-fixed-").toFile()
        val first = PersonalVoiceLearningStore(directory, key)
        val second = PersonalVoiceLearningStore(directory, key)
        fun example(number: Int) = PersonalVoiceExample(
            "synthetic-session", "Synthetische Korrektur $number", null, false,
            "Prüfe synthetischen Zweig $number", true, listOf("Testkontext"),
            listOf("task-$number"), "Prüfergebnis", listOf("Kein Merge"),
            "synthetic-independent-review", true, true, true
        )
        fun grant(store: PersonalVoiceLearningStore, e: PersonalVoiceExample) =
            store.approveForTraining(e, true, store.reviewRevision())
        first.setConsent(VoiceLearningConsent(textTraining = true))
        val one = example(1); val two = example(2); val three = example(3)
        check(first.stage(one, grant(first, one)) == VoiceStageResult.STORED)
        check(first.stage(two, grant(first, two)) == VoiceStageResult.STORED)
        val queued = grant(second, three)
        first.setConsent(VoiceLearningConsent())
        check(first.reviewedBatch(2).isEmpty() && second.reviewedBatch(2).isEmpty())
        check(!second.consent().textTraining)
        check(second.stage(three, queued) == VoiceStageResult.CONSENT_REQUIRED)
        println("PASS consent_revocation_shared_across_instances")
        second.setConsent(VoiceLearningConsent(textTraining = true))
        val oldApproval = grant(second, one)
        check(second.delete(one.digest))
        check(second.stage(one, oldApproval) == VoiceStageResult.REVIEW_REQUIRED)
        check(one.digest !in second.ids())
        check(second.stage(one, grant(second, one)) == VoiceStageResult.STORED)
        println("PASS deleted_record_rejects_previous_approval")
        check(second.stage(three, grant(second, three)) == VoiceStageResult.STORED)
        for (i in 4..PersonalVoiceLearningStore.MAX_RECORDS) {
            val e = example(i)
            check(second.stage(e, grant(second, e)) == VoiceStageResult.STORED)
        }
        check(second.ids().size == PersonalVoiceLearningStore.MAX_RECORDS)
        val revised = example(999)
        check(second.replace(one.digest, revised, grant(second, revised)) == VoiceStageResult.STORED)
        check(second.ids().size == PersonalVoiceLearningStore.MAX_RECORDS)
        check(one.digest !in second.ids() && revised.digest in second.ids())
        println("PASS replacement_at_capacity_preserves_total_256")
        val snapshot = first.inspectForReview(two.digest)
        second.setConsent(VoiceLearningConsent(textTraining = true))
        check(runCatching { first.approveForTraining(snapshot.example, true, snapshot.reviewRevision) }.isFailure)
        println("PASS old_review_form_cannot_issue_new_approval")
        println("INDEPENDENT_STORE_FIX_CHECKS 4 passed")
    }
}
