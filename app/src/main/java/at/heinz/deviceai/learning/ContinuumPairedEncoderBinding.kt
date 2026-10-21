package at.heinz.deviceai.learning

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Binds experimental CDX1 weights to the *exact* SWB1 snapshot which supplied
 * their 16-dimensional conditioning vectors. SWB1 may continue learning while
 * CDX1 uses its frozen, validated encoder until an explicit migration is audited.
 *
 * This file is never a second SWB1 model and is never eligible for chat/tool
 * promotion. An absent binding denotes an old, unverified laboratory checkpoint;
 * a present but damaged/mismatched binding fails closed.
 */
object ContinuumPairedEncoderBinding {
    enum class State {
        LEGACY_UNBOUND, PINNED_CURRENT, PINNED_PREVIOUS, PINNED_UNKNOWN, CORRUPT
    }
    data class Inspection(val state: State, val frozenEncoder: ByteArray?)

    private const val MAGIC = 0x43534231 // CSB1
    private const val MAX_ENCODER_BYTES = 49152

    fun inspect(file: File, decoderBytes: ByteArray, liveEncoder: ByteArray? = null): Inspection {
        if (!file.exists()) return Inspection(State.LEGACY_UNBOUND, null)
        return try {
            require(file.isFile && file.length() in 72L..65536L)
            val (storedDigest, encoderBytes) = LearningSnapshot.decode(file.readBytes()) { input ->
                require(input.readInt() == MAGIC) { "Unknown CDX1 binding" }
                val digest = ByteArray(32)
                input.readFully(digest)
                val length = input.readInt()
                require(length in 36..MAX_ENCODER_BYTES) { "Invalid SWB1 length" }
                val encoder = ByteArray(length)
                input.readFully(encoder)
                digest to encoder
            }
            require(MessageDigest.isEqual(storedDigest, sha256(decoderBytes))) {
                "CDX1 checkpoint does not match SWB1 binding"
            }
            // Reject a checksummed but structurally invalid encoder snapshot.
            TrainableSubwordEncoder.restore(encoderBytes)
            val state = when {
                liveEncoder == null -> State.PINNED_UNKNOWN
                encoderBytes.contentEquals(liveEncoder) -> State.PINNED_CURRENT
                else -> State.PINNED_PREVIOUS
            }
            Inspection(state, encoderBytes)
        } catch (_: Exception) {
            Inspection(State.CORRUPT, null)
        }
    }

    fun write(file: File, decoderBytes: ByteArray, encoderBytes: ByteArray) {
        require(encoderBytes.size in 36..MAX_ENCODER_BYTES)
        TrainableSubwordEncoder.restore(encoderBytes)
        val bytes = LearningSnapshot.encode { output ->
            output.writeInt(MAGIC)
            output.write(sha256(decoderBytes))
            output.writeInt(encoderBytes.size)
            output.write(encoderBytes)
        }
        val parent = requireNotNull(file.absoluteFile.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create CDX1 binding directory" }
        val temp = File.createTempFile("cdx1-swb1-", ".tmp", parent)
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            try {
                Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
