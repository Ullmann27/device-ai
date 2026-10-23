package at.heinz.deviceai.learning.lm

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Binary checkpoint of a language core: configuration, the SHA-256 of its tokenizer, free metadata (seed, steps,
 * corpus hash, …) and all parameters in layout order, followed by a SHA-256 of the body.
 * Restoring requires the same tokenizer; a mismatch or any damaged byte is rejected.
 */
object LmCheckpoint {
    private const val MAGIC = 0x4C4D3143 // "LM1C"
    private const val VERSION = 1

    fun encode(params: LmParams, tokenizer: LmBpeTokenizer, meta: Map<String, String>): ByteArray {
        val c = params.config
        require(c.vocab == tokenizer.vocabSize) { "model vocabulary ${c.vocab} != tokenizer ${tokenizer.vocabSize}" }
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION)
            out.writeInt(c.arch.ordinal); out.writeInt(c.vocab); out.writeInt(c.dim); out.writeInt(c.layers)
            out.writeInt(c.heads); out.writeInt(c.context); out.writeInt(c.mlp)
            out.writeUTF(tokenizer.sha256)
            out.writeInt(meta.size)
            for ((k, v) in meta.toSortedMap()) { out.writeUTF(k.take(64)); out.writeUTF(v.take(512)) }
            for ((name, m) in params.mats) {
                out.writeUTF(name); out.writeInt(m.rows); out.writeInt(m.cols)
                for (x in m.data) out.writeFloat(x)
            }
        }
        val body = buffer.toByteArray()
        return body + MessageDigest.getInstance("SHA-256").digest(body)
    }

    data class Restored(val params: LmParams, val meta: Map<String, String>)

    fun decode(bytes: ByteArray, tokenizer: LmBpeTokenizer): Restored {
        require(bytes.size > 64)
        val body = bytes.copyOfRange(0, bytes.size - 32)
        require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(body), bytes.copyOfRange(body.size, bytes.size))) {
            "checkpoint checksum mismatch"
        }
        return DataInputStream(body.inputStream()).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) { "not a language-core checkpoint" }
            val arch = LmArch.values()[input.readInt()]
            val config = LmConfig(arch, input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt())
            val tokenizerSha = input.readUTF()
            require(tokenizerSha == tokenizer.sha256) { "checkpoint belongs to another tokenizer" }
            val meta = LinkedHashMap<String, String>()
            repeat(input.readInt().also { require(it in 0..64) }) { meta[input.readUTF()] = input.readUTF() }
            val mats = LinkedHashMap<String, Mat>()
            for ((name, r, c) in config.layout()) {
                require(input.readUTF() == name && input.readInt() == r && input.readInt() == c) { "unexpected parameter layout at $name" }
                mats[name] = Mat(r, c, FloatArray(r * c) { input.readFloat() })
            }
            require(input.available() == 0)
            Restored(LmParams(config, mats).also { require(it.allFinite()) { "non-finite parameter" } }, meta)
        }
    }
}

/**
 * Content-addressed checkpoint files: `<sha256>.lm1` in their own directory (never the SWB1/CDX1 directories).
 * Files are written atomically, never overwritten or deleted by this class, and re-verified on every read.
 */
class LmCheckpointStore(private val directory: File) {
    fun put(bytes: ByteArray): String {
        val sha = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        if (!directory.isDirectory && !directory.mkdirs()) error("checkpoint directory not creatable")
        val target = File(directory, "$sha$SUFFIX")
        if (target.isFile && verifies(target, sha)) return sha
        check(!target.exists()) { "existing file $sha$SUFFIX is damaged; refusing to overwrite it" }
        val temp = File.createTempFile("lm-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath())
            }
        } finally { temp.delete() }
        return sha
    }

    fun get(sha: String): ByteArray {
        require(HASH.matches(sha))
        val file = File(directory, "$sha$SUFFIX")
        require(!Files.isSymbolicLink(file.toPath())) { "symbolic link rejected" }
        val bytes = file.readBytes()
        check(LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes)) == sha) { "checkpoint $sha is damaged" }
        return bytes
    }

    fun list(): List<String> = directory.listFiles().orEmpty().map { it.name }.filter { it.endsWith(SUFFIX) }
        .map { it.removeSuffix(SUFFIX) }.filter { HASH.matches(it) }.sorted()

    private fun verifies(file: File, sha: String) =
        runCatching { LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(file.readBytes())) == sha }.getOrDefault(false)

    companion object {
        private const val SUFFIX = ".lm1"
        private val HASH = Regex("[a-f0-9]{64}")
    }
}
