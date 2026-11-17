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

interface AgentCheckpointStore {
    fun save(checkpoint: AgentCheckpoint)
    fun load(): AgentCheckpoint?
    fun clear()
}

class FileAgentCheckpointStore(
    private val directory: File,
    private val fileName: String = "agent-checkpoint.bin"
) : AgentCheckpointStore {
    init {
        require(fileName.matches(Regex("[A-Za-z0-9._-]{1,96}")))
    }

    @Synchronized
    override fun save(checkpoint: AgentCheckpoint) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create agent checkpoint directory.")
        }
        val payload = AgentCheckpointCodec.encode(checkpoint)
        val envelope = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC)
                out.writeInt(SCHEMA_VERSION)
                out.writeInt(payload.size)
                out.write(payload)
                out.write(sha256(payload))
            }
        }.toByteArray()
        require(envelope.size <= MAX_ENVELOPE_BYTES)

        val temporary = File.createTempFile("agent-checkpoint-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(envelope)
                output.fd.sync()
            }
            moveAtomically(temporary, checkpointFile())
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    override fun load(): AgentCheckpoint? {
        val file = checkpointFile()
        if (!file.isFile) return null
        require(file.length() in 1..MAX_ENVELOPE_BYTES.toLong()) {
            "Agent checkpoint file exceeds the allowed size."
        }
        return decodeEnvelope(file.readBytes())
    }

    @Synchronized
    override fun clear() {
        val file = checkpointFile()
        if (file.exists() && !file.delete()) {
            throw IOException("Cannot delete agent checkpoint.")
        }
    }

    private fun checkpointFile() = File(directory, fileName)

    private fun decodeEnvelope(bytes: ByteArray): AgentCheckpoint {
        return try {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            require(input.readUTF() == MAGIC) { "Invalid agent checkpoint magic." }
            require(input.readInt() == SCHEMA_VERSION) { "Unsupported agent checkpoint store schema." }
            val payloadSize = input.readInt()
            require(payloadSize in 1..AgentCheckpointCodec.maxEncodedBytes()) {
                "Invalid agent checkpoint payload size."
            }
            require(payloadSize <= input.available() - CHECKSUM_BYTES) {
                "Truncated agent checkpoint payload."
            }
            val payload = ByteArray(payloadSize)
            input.readFully(payload)
            val checksum = ByteArray(CHECKSUM_BYTES)
            input.readFully(checksum)
            require(input.available() == 0) { "Unexpected trailing checkpoint data." }
            require(MessageDigest.isEqual(checksum, sha256(payload))) {
                "Agent checkpoint checksum mismatch."
            }
            AgentCheckpointCodec.decode(payload)
        } catch (e: IOException) {
            throw IllegalArgumentException("Corrupted agent checkpoint.", e)
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val MAGIC = "DEVICE_AI_AGENT_CHECKPOINT_STORE"
        private const val SCHEMA_VERSION = 1
        private const val CHECKSUM_BYTES = 32
        private const val MAX_ENVELOPE_BYTES = 96 * 1024
    }
}

private fun sha256(bytes: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(bytes)
