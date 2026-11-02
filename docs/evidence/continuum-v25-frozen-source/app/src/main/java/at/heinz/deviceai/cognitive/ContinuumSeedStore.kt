package at.heinz.deviceai.cognitive

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class ContinuumSeedLoadSource { PRIMARY, LAST_KNOWN_GOOD }

data class ContinuumSeedLoadResult(
    val seed: ContinuumSeed,
    val source: ContinuumSeedLoadSource
)

class FileContinuumSeedStore(
    private val directory: File,
    private val signer: IdentitySigner
) {
    private val primary = File(directory, PRIMARY_FILE)
    private val backup = File(directory, BACKUP_FILE)

    @Synchronized
    fun save(seed: ContinuumSeed) {
        val encoded = ContinuumSeedCodec.encodeEnvelope(ContinuumSeedCodec.seal(seed, signer))
        require(encoded.size <= MAX_SEED_FILE_BYTES)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create Continuum seed directory.")
        }

        if (primary.isFile) {
            val current = runCatching { readValidated(primary) }.getOrNull()
            if (current != null) {
                writeAtomically(backup, primary.readBytes())
            }
        }
        writeAtomically(primary, encoded)
    }

    @Synchronized
    fun load(): ContinuumSeedLoadResult? {
        if (primary.isFile) {
            runCatching { readValidated(primary) }.getOrNull()?.let {
                return ContinuumSeedLoadResult(it, ContinuumSeedLoadSource.PRIMARY)
            }
        }
        if (backup.isFile) {
            runCatching { readValidated(backup) }.getOrNull()?.let {
                return ContinuumSeedLoadResult(it, ContinuumSeedLoadSource.LAST_KNOWN_GOOD)
            }
        }
        return null
    }

    @Synchronized
    fun requireLoad(): ContinuumSeedLoadResult =
        load() ?: error("No valid Continuum seed is available.")

    @Synchronized
    fun hasStoredState(): Boolean = primary.isFile || backup.isFile

    private fun readValidated(file: File): ContinuumSeed {
        require(file.length() in 1..MAX_SEED_FILE_BYTES.toLong()) { "Invalid Continuum seed file size." }
        val envelope = ContinuumSeedCodec.decodeEnvelope(file.readBytes())
        return ContinuumSeedCodec.open(envelope, signer)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        require(bytes.size in 1..MAX_SEED_FILE_BYTES)
        val temp = File.createTempFile("continuum-seed-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    companion object {
        private const val PRIMARY_FILE = "continuum-seed.bin"
        private const val BACKUP_FILE = "continuum-seed.lkg"
        private const val MAX_SEED_FILE_BYTES = 72 * 1024
    }
}
