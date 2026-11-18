package at.heinz.deviceai.localmodel

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Real AES-GCM with an in-memory key: the secret store's logic on the JVM. The Android Keystore itself
 * ([AndroidKeystoreSecretCipher]) is not exercised by any JVM or Robolectric test.
 */
internal class SoftwareCipher : SecretCipher {
    private var key: SecretKey? = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    var destroyed = 0
    override fun encrypt(plain: ByteArray): SecretCipher.Sealed {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, checkNotNull(key), GCMParameterSpec(128, iv))
        return SecretCipher.Sealed(iv, cipher.doFinal(plain))
    }
    override fun decrypt(sealed: SecretCipher.Sealed): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, checkNotNull(key), GCMParameterSpec(128, sealed.iv))
        return cipher.doFinal(sealed.data)
    }
    override fun destroyKey() { destroyed++; key = null }
}

internal class MapStorage : SecretStorage {
    val values = linkedMapOf<String, String>()
    var failWrites = false
    override fun read(name: String): String? = values[name]
    override fun write(values: Map<String, String?>): Boolean {
        if (failWrites) return false
        values.forEach { (key, value) -> if (value == null) this.values.remove(key) else this.values[key] = value }
        return true
    }
}

/** No test may ever reach the real API: without an explicit fake endpoint a request can only hit a closed local port. */
internal val UNREACHABLE_LOCAL_ENDPOINT = ClaudeModelRuntime.Endpoint(baseUrl = "http://127.0.0.1:1", maxRetries = 0,
    connectTimeout = java.time.Duration.ofSeconds(1), readTimeout = java.time.Duration.ofSeconds(1),
    requestTimeout = java.time.Duration.ofSeconds(2))

/** A brain with in-memory stores; [endpoint] points at the local fake endpoint in tests that send requests. */
internal fun testBrain(
    settings: ClaudeSettingsStore = InMemoryClaudeSettingsStore(),
    storage: MapStorage = MapStorage(),
    endpoint: ClaudeModelRuntime.Endpoint = UNREACHABLE_LOCAL_ENDPOINT,
    ledger: ClaudeUsageLedger = ClaudeUsageLedger(InMemoryClaudeLedgerStorage())
): ClaudeBrain = ClaudeBrain(settings,
    SecretStore("claude_api_key", SoftwareCipher(), storage, ClaudeApiKey::plausible), ledger, endpoint,
    now = { 1_700_000_000_000L })
