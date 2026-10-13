package at.heinz.deviceai.continuum

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

data class ContinuumObjectId(val value: String) {
    init { require(value.matches(Regex("[a-f0-9]{64}"))) }

    companion object {
        fun fromContent(content: ByteArray) = ContinuumObjectId(sha256Hex(content))
    }
}

enum class ContinuumObjectType {
    IDENTITY_SNAPSHOT, MEMORY_BLOCK, EXPERIENCE_BLOCK, KNOWLEDGE_SEGMENT,
    RESEARCH_DOSSIER, SKILL_PACKAGE, MODEL_REPUTATION, TASK_CHECKPOINT, MIGRATION_RECORD
}

data class ContinuumObjectMetadata(
    val id: ContinuumObjectId,
    val type: ContinuumObjectType,
    val createdAtMillis: Long,
    val contentSizeBytes: Int
) {
    init {
        require(createdAtMillis >= 0)
        require(contentSizeBytes >= 0)
    }
}

data class ContinuumStoredObject(val metadata: ContinuumObjectMetadata, val content: ByteArray)

data class ContinuumStateRoot(
    val formatVersion: StateFormatVersion,
    val objectIds: List<ContinuumObjectId>,
    val contentHash: String
) {
    init {
        require(objectIds.size <= MAX_ROOT_OBJECTS)
        require(objectIds.distinct().size == objectIds.size)
        require(objectIds == objectIds.sortedBy { it.value }) { "State-root objects must be canonically ordered." }
        require(contentHash == computeHash(formatVersion, objectIds))
    }

    companion object {
        const val MAX_ROOT_OBJECTS = 4_096

        fun create(formatVersion: StateFormatVersion, objectIds: Collection<ContinuumObjectId>) =
            objectIds.distinct().sortedBy { it.value }.let { sorted ->
                require(sorted.size == objectIds.size)
                ContinuumStateRoot(formatVersion, sorted, computeHash(formatVersion, sorted))
            }

        private fun computeHash(version: StateFormatVersion, ids: List<ContinuumObjectId>): String {
            val canonical = buildString {
                append("CONTINUUM_STATE_ROOT\n")
                append(version.value).append('\n')
                ids.sortedBy { it.value }.forEach { append(it.value).append('\n') }
            }
            return sha256Hex(canonical.toByteArray(Charsets.UTF_8))
        }
    }
}

interface ContinuumObjectStore {
    val maxObjectBytes: Int
    val maxStoreBytes: Long
    fun put(type: ContinuumObjectType, content: ByteArray, createdAtMillis: Long): ContinuumObjectMetadata
    fun get(id: ContinuumObjectId): ContinuumStoredObject?
    fun verify(id: ContinuumObjectId): Boolean
}

/**
 * Local app-private storage adapter. Remote/replica adapters must be explicitly owner-configured.
 */
class FileContinuumObjectStore(
    private val directory: File,
    override val maxObjectBytes: Int = DEFAULT_MAX_OBJECT_BYTES,
    override val maxStoreBytes: Long = DEFAULT_MAX_STORE_BYTES
) : ContinuumObjectStore {
    init {
        require(maxObjectBytes in 1..DEFAULT_MAX_OBJECT_BYTES)
        require(maxStoreBytes >= maxObjectBytes && maxStoreBytes <= DEFAULT_MAX_STORE_BYTES)
        require(!Files.isSymbolicLink(directory.toPath())) { "Object store directory cannot be a symlink." }
    }

    @Synchronized
    override fun put(
        type: ContinuumObjectType,
        content: ByteArray,
        createdAtMillis: Long
    ): ContinuumObjectMetadata {
        val stableContent = content.copyOf()
        require(stableContent.size <= maxObjectBytes) { "Continuum object exceeds its size limit." }
        require(createdAtMillis >= 0)
        val id = ContinuumObjectId.fromContent(stableContent)
        val metadata = ContinuumObjectMetadata(id, type, createdAtMillis, stableContent.size)
        val target = objectFile(id)
        if (target.exists()) {
            val existing = get(id) ?: error("Existing Continuum object could not be verified.")
            require(existing.metadata == metadata && existing.content.contentEquals(stableContent)) {
                "Content-addressed objects are immutable."
            }
            return existing.metadata
        }

        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create Continuum object store.")
        val entries = directory.listFiles() ?: throw IOException("Cannot inspect Continuum object store.")
        require(entries.all { it.isFile && !Files.isSymbolicLink(it.toPath()) }) {
            "Continuum object store contains an unexpected entry."
        }
        val usedBytes = entries.sumOf { it.length() }
        val envelope = encode(metadata, stableContent)
        require(usedBytes + envelope.size <= maxStoreBytes) { "Continuum object store is full." }
        val temporary = File.createTempFile("continuum-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(envelope)
                output.fd.sync()
            }
            moveAtomically(temporary, target)
        } finally {
            temporary.delete()
        }
        return metadata
    }

    @Synchronized
    override fun get(id: ContinuumObjectId): ContinuumStoredObject? {
        val file = objectFile(id)
        require(!Files.isSymbolicLink(file.toPath())) { "Continuum object path cannot be a symlink." }
        if (!file.isFile) return null
        require(file.length() in 1..(maxObjectBytes + MAX_ENVELOPE_OVERHEAD).toLong()) {
            "Continuum object exceeds its storage limit."
        }
        val input = DataInputStream(ByteArrayInputStream(file.readBytes()))
        require(input.readUTF() == MAGIC && input.readInt() == ENVELOPE_VERSION) {
            "Unsupported Continuum object envelope."
        }
        val type = ContinuumObjectType.values().getOrNull(input.readInt())
            ?: error("Unknown Continuum object type.")
        val created = input.readLong()
        val size = input.readInt()
        require(size in 0..maxObjectBytes && size <= input.available() - CHECKSUM_BYTES) {
            "Invalid Continuum object content size."
        }
        val content = ByteArray(size)
        input.readFully(content)
        val checksum = ByteArray(CHECKSUM_BYTES)
        input.readFully(checksum)
        val metadata = ContinuumObjectMetadata(id, type, created, size)
        require(input.available() == 0 && MessageDigest.isEqual(checksum, integrityDigest(metadata, content))) {
            "Continuum object checksum mismatch."
        }
        require(ContinuumObjectId.fromContent(content) == id) { "Continuum object content hash mismatch." }
        return ContinuumStoredObject(metadata, content)
    }

    override fun verify(id: ContinuumObjectId): Boolean =
        runCatching { get(id) != null }.getOrDefault(false)

    private fun objectFile(id: ContinuumObjectId): File {
        val root = directory.canonicalFile
        val file = File(root, "${id.value}.cobj")
        require(file.canonicalFile.parentFile == root) { "Invalid Continuum object path." }
        return file
    }

    private fun encode(metadata: ContinuumObjectMetadata, content: ByteArray): ByteArray =
        ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(ENVELOPE_VERSION)
                out.writeInt(metadata.type.ordinal)
                out.writeLong(metadata.createdAtMillis)
                out.writeInt(content.size)
                out.write(content)
                out.write(integrityDigest(metadata, content))
            }
        }.toByteArray()

    private fun integrityDigest(metadata: ContinuumObjectMetadata, content: ByteArray): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(metadata.type.ordinal)
                out.writeLong(metadata.createdAtMillis)
                out.writeInt(metadata.contentSizeBytes)
                out.write(content)
            }
        }.toByteArray().let(::sha256)

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath())
        }
    }

    companion object {
        const val DEFAULT_MAX_OBJECT_BYTES = 4 * 1024 * 1024
        const val DEFAULT_MAX_STORE_BYTES = 64L * 1024 * 1024
        private const val MAGIC = "DEVICE_AI_CONTINUUM_OBJECT"
        private const val ENVELOPE_VERSION = 1
        private const val CHECKSUM_BYTES = 32
        private const val MAX_ENVELOPE_OVERHEAD = 128
    }
}

private fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)

private fun sha256Hex(bytes: ByteArray): String =
    sha256(bytes).joinToString("") { "%02x".format(it) }
