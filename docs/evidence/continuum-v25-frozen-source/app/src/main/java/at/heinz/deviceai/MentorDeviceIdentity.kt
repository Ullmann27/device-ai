package at.heinz.deviceai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * An independent AndroidKeystore identity for opt-in Groq mentor pairing.
 * Private key material never leaves the AndroidKeystore.
 */
object MentorDeviceIdentity {
    private const val ALIAS = "device_ai_mentor_pairing_ec_v1"
    private const val HEADER = "device-ai-mentor-pair-v1"

    @Synchronized
    private fun keyPair(): KeyPair {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(ALIAS, null) as? java.security.PrivateKey
        val publicKey = store.getCertificate(ALIAS)?.publicKey
        if (privateKey != null && publicKey != null) return KeyPair(publicKey, privateKey)

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKeyPair()
    }

    fun publicKeyBase64(): String =
        Base64.encodeToString(keyPair().public.encoded, Base64.NO_WRAP)

    fun fingerprint(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(keyPair().public.encoded)
            .joinToString("") { "%02x".format(it) }

    fun signChallenge(issuedAt: Long, nonce: String): String {
        require(issuedAt > 0 && nonce.matches(Regex("[A-Za-z0-9_-]{32}")))
        val payload = (HEADER + "\n" + issuedAt + "\n" + nonce +
            "\n" + publicKeyBase64()).toByteArray(Charsets.UTF_8)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair().private)
        signer.update(payload)
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }
}
