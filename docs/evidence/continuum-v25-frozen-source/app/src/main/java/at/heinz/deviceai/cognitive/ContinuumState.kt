package at.heinz.deviceai.cognitive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@JvmInline
value class ContinuumObjectId(val value: String) {
    init { require(value.matches(Regex("[a-f0-9]{64}"))) { "Invalid Continuum object id." } }
    override fun toString(): String = value

    companion object {
        fun of(type: ContinuumObjectType, payload: ByteArray): ContinuumObjectId {
            require(payload.isNotEmpty())
            return ContinuumObjectId(
                sha256Bytes(type.name.toByteArray(Charsets.UTF_8) + byteArrayOf(0) + payload).toHex()
            )
        }
    }
}

enum class ContinuumObjectType {
    IDENTITY_SNAPSHOT,
    MEMORY_BLOCK,
    EXPERIENCE_BLOCK,
    KNOWLEDGE_GRAPH_SEGMENT,
    RESEARCH_DOSSIER,
    SKILL_PACKAGE,
    MODEL_REPUTATION_SNAPSHOT,
    TASK_CHECKPOINT,
    MIGRATION_RECORD,
    STATE_MANIFEST,
    CACHE
}

data class ContinuumObjectMetadata(
    val id: ContinuumObjectId,
    val type: ContinuumObjectType,
    val createdAtMillis: Long,
    val payloadBytes: Int
) {
    init { require(createdAtMillis >= 0 && payloadBytes in 1..MAX_OBJECT_BYTES) }

    companion object { const val MAX_OBJECT_BYTES = 2 * 1024 * 1024 }
}

data class StoredContinuumObject(
    val metadata: ContinuumObjectMetadata,
    val payload: ByteArray
) {
    init { require(payload.size == metadata.payloadBytes) }
}

data class ContinuumStateRoot(
    val generation: Long,
    val rootObjectId: ContinuumObjectId,
    val previousRootObjectId: ContinuumObjectId?,
    val createdAtMillis: Long
) {
    init {
        require(generation >= 1 && createdAtMillis >= 0)
        require((generation == 1L) == (previousRootObjectId == null))
    }

    fun canonical(): String =
        generation.toString() + "|" + rootObjectId.value + "|" +
            (previousRootObjectId?.value ?: "") + "|" + createdAtMillis
}

interface ContinuumObjectStore {
    fun put(
        type: ContinuumObjectType,
        payload: ByteArray,
        createdAtMillis: Long = System.currentTimeMillis()
    ): ContinuumObjectMetadata

    fun get(id: ContinuumObjectId): StoredContinuumObject?
    fun contains(id: ContinuumObjectId): Boolean = get(id) != null
    fun totalStoredBytes(): Long
}

interface ContinuumReplicaStore {
    val storeId: String
    fun hasObject(id: ContinuumObjectId): Boolean
    fun readObject(id: ContinuumObjectId): ByteArray?
    fun writeObject(type: ContinuumObjectType, payload: ByteArray): ContinuumObjectId
}

class FileContinuumObjectStore(
    private val directory: File,
    private val maxObjectBytes: Int = ContinuumObjectMetadata.MAX_OBJECT_BYTES,
    private val maxStoreBytes: Long = 64L * 1024 * 1024
) : ContinuumObjectStore {
    init {
        require(maxObjectBytes in 1..ContinuumObjectMetadata.MAX_OBJECT_BYTES)
        require(maxStoreBytes in maxObjectBytes.toLong()..512L * 1024 * 1024)
    }

    @Synchronized
    override fun put(type: ContinuumObjectType, payload: ByteArray, createdAtMillis: Long): ContinuumObjectMetadata {
        require(payload.isNotEmpty() && payload.size <= maxObjectBytes)
        require(createdAtMillis >= 0)
        val id = ContinuumObjectId.of(type, payload)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create Continuum object directory.")
        val target = objectFile(id)
        if (target.exists()) {
            val existing = readValidated(target)
            require(existing.metadata.type == type && existing.payload.contentEquals(payload)) {
                "Content-addressed object collision or corruption."
            }
            return existing.metadata
        }

        val encoded = encode(type, createdAtMillis, payload)
        require(totalStoredBytes() <= maxStoreBytes - encoded.size) { "Continuum object store capacity exceeded." }
        val temp = File.createTempFile("continuum-object-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                if (!target.exists()) Files.move(temp.toPath(), target.toPath())
            }
        } finally {
            temp.delete()
        }
        return readValidated(target).metadata
    }

    @Synchronized
    override fun get(id: ContinuumObjectId): StoredContinuumObject? {
        val file = objectFile(id)
        if (!file.isFile) return null
        return readValidated(file).also {
            require(it.metadata.id == id) { "Continuum object filename/content mismatch." }
        }
    }

    @Synchronized
    override fun totalStoredBytes(): Long =
        directory.listFiles { file -> file.isFile && file.name.matches(Regex("[a-f0-9]{64}\\.obj")) }
            .orEmpty().sumOf(File::length)

    private fun objectFile(id: ContinuumObjectId): File = File(directory, id.value + ".obj")

    private fun encode(type: ContinuumObjectType, createdAtMillis: Long, payload: ByteArray): ByteArray {
        val body = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(type.ordinal)
                out.writeLong(createdAtMillis)
                out.writeInt(payload.size)
                out.write(payload)
            }
        }.toByteArray()
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(SCHEMA_VERSION)
                out.writeInt(body.size)
                out.write(body)
                out.write(sha256Bytes(body))
            }
        }.toByteArray()
    }

    private fun readValidated(file: File): StoredContinuumObject = try {
        require(file.length() in 1..(maxObjectBytes + 256).toLong()) { "Invalid Continuum object file size." }
        val input = DataInputStream(ByteArrayInputStream(file.readBytes()))
        require(input.readUTF() == MAGIC) { "Unrecognized Continuum object." }
        require(input.readInt() == SCHEMA_VERSION) { "Unsupported Continuum object schema." }
        val bodySize = input.readInt()
        require(bodySize in 1..(maxObjectBytes + 32) && bodySize <= input.available() - 32)
        val body = ByteArray(bodySize)
        input.readFully(body)
        val checksum = ByteArray(32)
        input.readFully(checksum)
        require(input.available() == 0 && MessageDigest.isEqual(checksum, sha256Bytes(body))) {
            "Continuum object integrity check failed."
        }
        val data = DataInputStream(ByteArrayInputStream(body))
        val type = ContinuumObjectType.values().getOrNull(data.readInt())
            ?: throw IllegalArgumentException("Unknown Continuum object type.")
        val createdAt = data.readLong()
        val payloadSize = data.readInt()
        require(payloadSize in 1..maxObjectBytes && payloadSize <= data.available())
        val payload = ByteArray(payloadSize)
        data.readFully(payload)
        require(data.available() == 0)
        val id = ContinuumObjectId.of(type, payload)
        StoredContinuumObject(ContinuumObjectMetadata(id, type, createdAt, payload.size), payload)
    } catch (e: IOException) {
        throw IllegalArgumentException("Corrupted Continuum object.", e)
    }

    companion object {
        private const val MAGIC = "DEVICE_AI_CONTINUUM_OBJECT"
        private const val SCHEMA_VERSION = 1
    }
}
