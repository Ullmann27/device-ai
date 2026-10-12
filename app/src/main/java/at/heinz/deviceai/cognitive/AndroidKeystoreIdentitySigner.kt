package at.heinz.deviceai.cognitive

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class AndroidKeystoreIdentitySigner(
    private val alias: String = "device-ai-identity-signing-key"
) : IdentitySigner {
    init {
        require(alias.matches(Regex("[A-Za-z0-9._-]{1,128}")))
        ensureKey()
    }

    @Synchronized
    override fun sign(payload: ByteArray): ByteArray {
        val keyStore = keyStore()
        val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey
            ?: error("Android Keystore identity key is unavailable.")
        return Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(payload)
            sign()
        }
    }

    @Synchronized
    override fun verify(payload: ByteArray, signature: ByteArray): Boolean = runCatching {
        val publicKey = keyStore().getCertificate(alias)?.publicKey ?: return false
        Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initVerify(publicKey)
            update(payload)
            verify(signature)
        }
    }.getOrDefault(false)

    private fun ensureKey() {
        val keyStore = keyStore()
        if (keyStore.containsAlias(alias)) {
            require(keyStore.getCertificate(alias)?.publicKey?.algorithm == "EC") {
                "Existing Android Keystore alias is not an EC signing key."
            }
            return
        }
        KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generateKeyPair()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
