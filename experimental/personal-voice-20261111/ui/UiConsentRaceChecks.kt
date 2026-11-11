import at.heinz.deviceai.research.voice.PersonalVoiceLearningStore
import at.heinz.deviceai.research.voice.VoiceLearningConsent
import java.nio.file.Files
import javax.crypto.KeyGenerator

/** Actual store and cryptography; controls call order, not an Android event-loop test. */
object UiConsentRaceChecks {
    @JvmStatic fun main(args: Array<String>) {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val root = Files.createTempDirectory("voice-ui-consent-fixed-").toFile()
        val uiStore = PersonalVoiceLearningStore(root, key)
        val other = PersonalVoiceLearningStore(root, key)

        val oldForm = uiStore.consentForReview()
        val oldEnableClick = { uiStore.setTextTrainingConsent(true, oldForm.reviewRevision) }
        other.setConsent(VoiceLearningConsent())
        check(runCatching(oldEnableClick).isFailure)
        check(!uiStore.consent().textTraining)
        println("PASS old_queued_enable_cannot_undo_later_revocation")

        other.setConsent(VoiceLearningConsent(textTraining = false, audioRetention = true))
        val audioForm = uiStore.consentForReview()
        other.setConsent(VoiceLearningConsent(textTraining = false, audioRetention = false))
        check(runCatching { uiStore.setTextTrainingConsent(true, audioForm.reviewRevision) }.isFailure)
        check(!uiStore.consent().audioRetention && !uiStore.consent().textTraining)
        println("PASS stale_text_change_cannot_restore_revoked_audio")

        val current = uiStore.consentForReview()
        val changed = uiStore.setTextTrainingConsent(true, current.reviewRevision)
        check(changed.consent.textTraining && !changed.consent.audioRetention)
        check(changed.reviewRevision > current.reviewRevision)
        other.setConsent(VoiceLearningConsent(textTraining = true, audioRetention = true))
        val audioApproved = uiStore.consentForReview()
        val textDisabled = uiStore.setTextTrainingConsent(false, audioApproved.reviewRevision)
        check(!textDisabled.consent.textTraining && textDisabled.consent.audioRetention)
        println("PASS fresh_text_change_preserves_each_current_audio_choice")
        println("UI_CONSENT_INTERLEAVING_CHECKS 3 passed; actual Android UI NOT_RUN")
    }
}
