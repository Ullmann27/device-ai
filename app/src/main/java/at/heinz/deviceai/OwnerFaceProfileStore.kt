package at.heinz.deviceai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted templates, excluded from backup. */
internal class OwnerFaceProfileStore(
    private val directory: File,
    private val key: () -> SecretKey,
    private val deleteKey: () -> Unit = {}
) {
    constructor(context: Context) : this(
        File(context.applicationContext.noBackupFilesDir, "owner-face"),
        ::androidKey,
        { androidKeyStore().deleteEntry(KEY_ALIAS) }
    )

    private val profileFile get() = File(directory, "profile.aes")
    private val pendingFile get() = File(directory, "profile.pending")

    internal class Prepared internal constructor(internal val file: File, internal val generation: Long)
    internal class Snapshot(val profile: OwnerFaceProfile?, val revision: Long)

    val revision: Long get() = storageGeneration

    fun loadSnapshot(): Snapshot = synchronized(storeLock) { Snapshot(load(), storageGeneration) }

    fun load(): OwnerFaceProfile? = synchronized(storeLock) {
        if (!profileFile.exists()) return null
        require(profileFile.length() in (HEADER.size + IV_BYTES + 16L)..MAX_FILE_BYTES)
        val bytes = profileFile.readBytes()
        var plaintext: ByteArray? = null
        try {
            require(bytes.size.toLong() == profileFile.length())
            require(bytes.copyOfRange(0, HEADER.size).contentEquals(HEADER))
            val iv = bytes.copyOfRange(HEADER.size, HEADER.size + IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            plaintext = cipher.doFinal(bytes, HEADER.size + IV_BYTES, bytes.size - HEADER.size - IV_BYTES)
            decode(plaintext)
        } finally {
            bytes.fill(0)
            plaintext?.fill(0)
        }
    }

    fun save(profile: OwnerFaceProfile) {
        val pending = prepare(profile)
        try { commit(pending) } finally { discard(pending) }
    }

    fun prepare(profile: OwnerFaceProfile, expectedRevision: Long? = null): Prepared = synchronized(storeLock) {
        check(expectedRevision == null || expectedRevision == storageGeneration)
        require(profile.valid())
        require(directory.isDirectory || directory.mkdirs())
        val plaintext = encode(profile)
        var encrypted: ByteArray? = null
        val temporary = File(directory, "profile.${java.util.UUID.randomUUID()}.pending")
        var prepared = false
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(AAD)
            val iv = cipher.iv
            require(iv.size == IV_BYTES)
            encrypted = cipher.doFinal(plaintext)
            require(HEADER.size + iv.size + encrypted.size <= MAX_FILE_BYTES)
            FileOutputStream(temporary).use { stream ->
                stream.write(HEADER)
                stream.write(iv)
                stream.write(encrypted)
                stream.fd.sync()
            }
            prepared = true
            Prepared(temporary, storageGeneration)
        } finally {
            plaintext.fill(0)
            encrypted?.fill(0)
            if (!prepared) temporary.delete()
        }
    }

    fun commit(prepared: Prepared) = synchronized(storeLock) {
        check(prepared.generation == storageGeneration)
        check(prepared.file.parentFile == directory)
        Files.move(prepared.file.toPath(), profileFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        storageGeneration++
    }

    fun discard(prepared: Prepared) = synchronized(storeLock) {
        check(prepared.file.parentFile == directory)
        if (prepared.file.exists() && !prepared.file.delete()) throw IOException("FACE_PROFILE_DISCARD_FAILED")
    }

    fun delete() = synchronized(storeLock) {
        storageGeneration++
        var failure: Exception? = null
        val pending = directory.listFiles().orEmpty().filter {
            it.name.startsWith("profile.") && it.name.endsWith(".pending")
        }
        for (file in listOf(profileFile, pendingFile) + pending) {
            if (file.exists() && !file.delete()) failure = IOException("FACE_PROFILE_DELETE_FAILED")
        }
        try { deleteKey() } catch (error: Exception) { failure = error }
        failure?.let { throw it }
        Unit
    }

    private fun encode(profile: OwnerFaceProfile): ByteArray {
        val features = JSONArray()
        profile.features.forEach { values ->
            features.put(JSONArray().apply { values.forEach { put(it.toDouble()) } })
        }
        return JSONObject().put("schema", 1).put("name", profile.displayName)
            .put("createdAtMillis", profile.createdAtMillis).put("modelSha256", profile.modelSha256)
            .put("features", features).toString().toByteArray(StandardCharsets.UTF_8)
            .also { require(it.size <= MAX_FILE_BYTES - 64) }
    }

    private fun decode(bytes: ByteArray): OwnerFaceProfile {
        require(bytes.size <= MAX_FILE_BYTES - 64)
        val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
        require(json.keys().asSequence().toSet() == setOf("schema", "name", "createdAtMillis", "modelSha256", "features"))
        require(json.getInt("schema") == 1)
        val vectors = json.getJSONArray("features")
        require(vectors.length() == OwnerFacePolicy.ENROLLMENT_SAMPLES)
        val features = mutableListOf<FloatArray>()
        try {
            repeat(vectors.length()) { index ->
                val vector = vectors.getJSONArray(index)
                require(vector.length() == OwnerFaceModelContract.FEATURE_SIZE)
                features += FloatArray(vector.length()) { vector.getDouble(it).toFloat() }
            }
            return OwnerFaceProfile(json.getString("name"), json.getLong("createdAtMillis"),
                json.getString("modelSha256"), features).also { require(it.valid()) }
        } catch (error: Exception) {
            features.forEach { it.fill(0f) }
            throw error
        }
    }

    private companion object {
        const val KEY_ALIAS = "continuum-owner-face-v1"
        const val IV_BYTES = 12
        const val MAX_FILE_BYTES = 65_536L
        val HEADER = "CFACE01\u0000".toByteArray(StandardCharsets.US_ASCII)
        val AAD = ("continuum-owner-face-v1|" + OwnerFaceModelContract.RECOGNIZER_SHA256)
            .toByteArray(StandardCharsets.US_ASCII)
        val storeLock = Any()
        @Volatile var storageGeneration = 0L

        fun androidKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        fun androidKey(): SecretKey {
            val store = androidKeyStore()
            if (store.containsAlias(KEY_ALIAS)) {
                return requireNotNull(store.getKey(KEY_ALIAS, null) as? SecretKey)
            }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build())
            }.generateKey()
        }
    }
}
