package at.heinz.deviceai.localmodel

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM seal of one secret. The key material is the implementation's business and never exposed. */
interface SecretCipher {
    class Sealed(val iv: ByteArray, val data: ByteArray)
    fun encrypt(plain: ByteArray): Sealed
    fun decrypt(sealed: Sealed): ByteArray
    /** Removes the key; a stored ciphertext becomes unreadable. */
    fun destroyKey() {}
}

/** Where the sealed bytes are kept (app-private preferences in production). */
interface SecretStorage {
    fun read(name: String): String?
    /** Writes all [values] together; a null value removes its entry. False when nothing was stored. */
    fun write(values: Map<String, String?>): Boolean
}

/**
 * An owner secret held in memory. [toString] is redacted on purpose so the value cannot leak through a log
 * statement, an exception message, a snapshot or a data-class dump of a holder.
 */
class OwnerSecret(private val value: String) {
    init { require(value.isNotEmpty()) }
    /** The only way to the plain value; call it at the point of use and do not keep the result. */
    fun reveal(): String = value
    override fun toString(): String = "OwnerSecret(redacted)"
    override fun equals(other: Any?): Boolean = other is OwnerSecret && other.value == value
    override fun hashCode(): Int = value.hashCode()
}

/**
 * One named secret, encrypted at rest. The plain value is returned only by [load]; it is never logged, never part
 * of [toString], and a ciphertext that cannot be decrypted (key lost, data damaged, restored on another device)
 * reads as "no secret" instead of failing.
 */
class SecretStore(
    private val name: String,
    private val cipher: SecretCipher,
    private val storage: SecretStorage,
    private val accepts: (String) -> Boolean = { true }
) {
    init { require(name.matches(Regex("[a-z][a-z0-9_]{1,40}"))) }

    @Synchronized fun save(secret: String): Boolean {
        if (!accepts(secret)) return false
        return runCatching {
            val sealed = cipher.encrypt(secret.toByteArray(Charsets.UTF_8))
            val encoder = Base64.getEncoder()
            storage.write(mapOf(ivKey to encoder.encodeToString(sealed.iv),
                dataKey to encoder.encodeToString(sealed.data)))
        }.getOrDefault(false)
    }

    @Synchronized fun load(): OwnerSecret? {
        val iv = storage.read(ivKey) ?: return null
        val data = storage.read(dataKey) ?: return null
        return runCatching {
            val decoder = Base64.getDecoder()
            String(cipher.decrypt(SecretCipher.Sealed(decoder.decode(iv), decoder.decode(data))), Charsets.UTF_8)
                .takeIf { it.isNotEmpty() && accepts(it) }?.let(::OwnerSecret)
        }.getOrNull()
    }

    @Synchronized fun isPresent(): Boolean = load() != null

    /** Removes the ciphertext and the key. True when no secret is stored afterwards. */
    @Synchronized fun clear(): Boolean {
        val removed = runCatching { storage.write(mapOf(ivKey to null, dataKey to null)) }.getOrDefault(false)
        runCatching { cipher.destroyKey() }
        return removed && storage.read(dataKey) == null
    }

    override fun toString(): String = "SecretStore($name, content=redacted)"

    private val ivKey get() = name + "_iv"
    private val dataKey get() = name + "_data"
}

/**
 * Non-exportable AES-256-GCM key in the Android Keystore, one alias per secret. The key never leaves the
 * keystore; this class only asks it to encrypt and decrypt.
 */
class AndroidKeystoreSecretCipher(private val alias: String) : SecretCipher {
    init { require(alias.matches(Regex("[a-z][a-z0-9_]{1,60}"))) }

    override fun encrypt(plain: ByteArray): SecretCipher.Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return SecretCipher.Sealed(cipher.iv, cipher.doFinal(plain))
    }

    override fun decrypt(sealed: SecretCipher.Sealed): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed.iv))
        return cipher.doFinal(sealed.data)
    }

    override fun destroyKey() {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

class PreferencesSecretStorage(context: Context, preferencesName: String) : SecretStorage {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    override fun read(name: String): String? = preferences.getString(name, null)
    override fun write(values: Map<String, String?>): Boolean = preferences.edit().apply {
        values.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
    }.commit()
}

/** The owner's own Claude API key: format check and the one store instance of the app. */
object ClaudeApiKey {
    /** Anthropic API keys start with "sk-ant-"; the rest is checked only for a plausible shape, never online. */
    private val SHAPE = Regex("sk-ant-[A-Za-z0-9_-]{8,400}")

    fun plausible(candidate: String): Boolean = SHAPE.matches(candidate)

    fun store(context: Context): SecretStore = SecretStore(
        name = "claude_api_key",
        cipher = AndroidKeystoreSecretCipher("continuum_claude_api_key_v1"),
        storage = PreferencesSecretStorage(context, "continuum.claude.secret"),
        accepts = ::plausible)

    /** For the settings screen: presence only. No part of the key is ever shown, so no screenshot can carry it. */
    fun status(present: Boolean): String =
        if (present) "Schlüssel gespeichert (verschlüsselt, wird nie angezeigt)" else "kein Schlüssel gespeichert"
}
