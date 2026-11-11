package at.heinz.deviceai.research.voice

import java.nio.file.Files
import javax.crypto.KeyGenerator

/** Deterministic reproduction of the pre-fix UI read/copy/write callback. */
object UiConsentRaceReproduction {
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty())
        val key = KeyGenerator.getInstance("AES").also { it.init(256) }.generateKey()
        val store = PersonalVoiceLearningStore(Files.createTempDirectory("voice-consent-race-").toFile(), key)
        store.setConsent(VoiceLearningConsent(false, false))
        // Old UI accepts a click now but its executor has not yet run the mutation.
        val queuedOldEnable = {
            val current = store.consent()
            store.setConsent(current.copy(textTraining = true))
        }
        store.setConsent(VoiceLearningConsent(false, false)) // a newer explicit revocation
        queuedOldEnable()
        println("CASE\tid=old_queued_enable_after_newer_revoke\ttextTraining=${store.consent().textTraining}\texpected=false")

        store.setConsent(VoiceLearningConsent(false, true))
        val oldUiRead = store.consent()
        store.setConsent(VoiceLearningConsent(false, false)) // independent audio revocation
        store.setConsent(oldUiRead.copy(textTraining = true))
        println("CASE\tid=stale_copy_restores_audio\taudioRetention=${store.consent().audioRetention}\texpected=false")
        println("SCOPE\tactualStore=true\tAndroidUiRuntime=false\tprivateData=false\tweightUpdates=0")
    }
}
