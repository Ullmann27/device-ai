package at.heinz.deviceai.research.voice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Research-only factory. No Activity caller, capture, cloud upload, or audio writer. */
object AndroidPersonalVoiceStore {
    fun create(context: Context): PersonalVoiceLearningStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
        return PersonalVoiceLearningStore(File(context.noBackupFilesDir, "personal-voice-v1"), key)
    }
    private const val ALIAS = "continuum_personal_voice_learning_v1"
}
