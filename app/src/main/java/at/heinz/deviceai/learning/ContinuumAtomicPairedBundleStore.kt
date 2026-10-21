package at.heinz.deviceai.learning

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * An entire experimental CDX1 checkpoint and its frozen SWB1 encoder share ONE
 * atomic file. An older verified bundle is retained as an independently
 * verifiable last-known-good file. Legacy CDX/binding files are never modified.
 *
 * Checksums detect accidental corruption, not malicious modification. POSIX
 * power-loss durability beyond fd.sync depends on the device filesystem.
 */
internal class ContinuumAtomicPairedBundleStore(private val directory: File) {
    data class Bundle(
        val decoder: ByteArray,
        val encoder: ByteArray,
        val generation: Long,
        val recoveredFromBackup: Boolean
    )

    private val current = File(directory, "context-decoder-paired.bundle")
    private val previous = File(directory, "context-decoder-paired.bundle.lkg")
    private val lockFile = File(directory, "context-decoder-paired.bundle.lock")

    /** Null is only possible if no bundle or backup exists at all. */
    fun loadVerified(): Bundle? = synchronized(LOCK) {
        when {
            !current.exists() && !previous.exists() -> null
            else -> {
                val active = runCatching { readBundle(current) }.getOrNull()
                if (active != null) active
                else {
                    val backup = runCatching { readBundle(previous) }.getOrNull()
                    backup?.copy(recoveredFromBackup = true)
                        ?: throw IllegalStateException("CDX1 bundle and backup are not verifiable")
                }
            }
        }
    }

    /**
     * The expected generation prevents a second training writer from publishing
     * a stale candidate. A failed or cancelled write never changes the active
     * bundle; a fault hook exists exclusively for deterministic crash tests.
     */
    fun commit(
        decoder: ByteArray,
        encoder: ByteArray,
        expectedGeneration: Long,
        faultBeforePublish: () -> Unit = {}
    ): Bundle = synchronized(LOCK) {
        require(expectedGeneration in 0L..MAX_GENERATION)
        TinyContextDecoder.restore(decoder)
        TrainableSubwordEncoder.restore(encoder)
        require(decoder.size in 36..65536 && encoder.size in 36..65536)
        require(!Files.isSymbolicLink(directory.toPath()))
        check(directory.isDirectory || directory.mkdirs())
        check(!Files.isSymbolicLink(lockFile.toPath()))
        FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE,
            StandardOpenOption.WRITE).use { channel ->
            val lock = channel.tryLock()
                ?: throw IllegalStateException("CDX1 bundle writer already running")
            try {
                // Corrupt current state is never silently overwritten, even when
                // a valid recovery copy is available. Recovery stays read-only.
                val active = if (current.exists()) readBundle(current)
                    else if (previous.exists()) throw IllegalStateException(
                        "CDX1 active bundle is missing; recovery required")
                    else null
                val generation = active?.generation ?: 0L
                check(generation == expectedGeneration) {
                    "CDX1 weights changed during training; refuse stale commit"
                }
                check(generation < MAX_GENERATION)
                if (active != null) {
                    atomicWrite(previous, encode(active.decoder, active.encoder,
                        active.generation))
                }
                val newBytes = encode(decoder, encoder, generation + 1L)
                // Validate the serialized object before publication.
                require(readBytes(newBytes).generation == generation + 1L)
                faultBeforePublish()
                atomicWrite(current, newBytes)
                readBundle(current)
            } finally {
                lock.release()
            }
        }
    }

    private fun readBundle(file: File): Bundle {
        check(!Files.isSymbolicLink(file.toPath()) && file.isFile)
        require(file.length() in MIN_TOTAL_BYTES.toLong()..MAX_TOTAL_BYTES.toLong())
        return readBytes(file.readBytes())
    }

    private fun readBytes(bytes: ByteArray): Bundle {
        require(bytes.size in MIN_TOTAL_BYTES..MAX_TOTAL_BYTES)
        val body = bytes.copyOfRange(0, bytes.size - DIGEST_SIZE)
        val digest = bytes.copyOfRange(body.size, bytes.size)
        require(MessageDigest.isEqual(sha256(body), digest)) {
            "CDX1 bundle checksum mismatch"
        }
        val value = DataInputStream(ByteArrayInputStream(body)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION)
            val generation = input.readLong()
            require(generation in 1L..MAX_GENERATION)
            val decoderLength = input.readInt()
            require(decoderLength in 36..65536)
            val decoder = ByteArray(decoderLength)
            input.readFully(decoder)
            val encoderLength = input.readInt()
            require(encoderLength in 36..65536)
            val encoder = ByteArray(encoderLength)
            input.readFully(encoder)
            require(input.available() == 0)
            Bundle(decoder, encoder, generation, false)
        }
        TinyContextDecoder.restore(value.decoder)
        TrainableSubwordEncoder.restore(value.encoder)
        return value
    }

    private fun encode(decoder: ByteArray, encoder: ByteArray, generation: Long): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeLong(generation)
            output.writeInt(decoder.size)
            output.write(decoder)
            output.writeInt(encoder.size)
            output.write(encoder)
        }
        val bytes = body.toByteArray()
        require(bytes.size + DIGEST_SIZE <= MAX_TOTAL_BYTES)
        return bytes + sha256(bytes)
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        check(!Files.isSymbolicLink(file.toPath()))
        val temp = File.createTempFile("cdx-pair-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            // Fail closed if ATOMIC_MOVE is unavailable; a non-atomic fallback
            // would negate the consistency contract.
            Files.move(temp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    companion object {
        private val LOCK = Any()
        private const val MAGIC = 0x43445032 // CDP2
        private const val VERSION = 1
        private const val DIGEST_SIZE = 32
        private const val MAX_GENERATION = 1_000_000L
        private const val MIN_TOTAL_BYTES = 16 + 4 + 36 + 4 + 36 + DIGEST_SIZE
        private const val MAX_TOTAL_BYTES = 16 + 4 + 65536 + 4 + 65536 + DIGEST_SIZE
        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
