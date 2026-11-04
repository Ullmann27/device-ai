package at.heinz.deviceai.learning.lm

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

data class LmTrainingIdentity(val sourceSha256: String, val dataSha256: String, val seed: Long) {
    init { require(listOf(sourceSha256, dataSha256).all { Regex("[0-9a-f]{64}").matches(it) }) }
}

data class LmTrainingSpec(val learningRate: Float, val weightDecay: Float, val warmup: Int,
                          val totalSteps: Int, val clip: Float, val workers: Int) {
    init {
        require(learningRate.isFinite() && learningRate > 0f)
        require(weightDecay.isFinite() && weightDecay >= 0f && clip.isFinite() && clip > 0f)
        require(warmup >= 0 && totalSteps > 0 && warmup <= totalSteps && workers in 1..8)
    }
    fun trainer(params: LmParams) = LmTrainer(LmModel(params), learningRate, weightDecay,
        warmup, totalSteps, clip, workers)
    companion object {
        fun from(trainer: LmTrainer) = LmTrainingSpec(trainer.learningRate, trainer.weightDecay,
            trainer.warmup, trainer.totalSteps, trainer.clip, trainer.workerCount)
    }
}

/** Research format, distinct from LM1C. */
object LmTrainingState {
    private const val MAGIC = 0x4c4d3154
    private const val VERSION = 1
    private const val MAX_PARAMETERS = 16_011_776L
    private const val MAX_BYTES = MAX_PARAMETERS * 12L + 1_048_576L
    private val HEX = Regex("[0-9a-f]{64}")

    fun sha(path: Path): String {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        val d = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val block = ByteArray(65536)
            while (true) { val n = input.read(block); if (n < 0) break; d.update(block, 0, n) }
        }
        return LmBpeTokenizer.hex(d.digest())
    }

    fun save(directory: Path, trainer: LmTrainer, tokenizer: LmBpeTokenizer,
             identity: LmTrainingIdentity): Path = synchronized(trainer) {
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        val params = trainer.model.params
        require(params.count.toLong() in 1..MAX_PARAMETERS && params.allFinite())
        require(params.config.vocab == tokenizer.vocabSize && trainer.steps <= trainer.totalSteps)
        val temp = Files.createTempFile(directory, ".lmtrain-", ".partial")
        val bodyHash = MessageDigest.getInstance("SHA-256")
        val fileHash = MessageDigest.getInstance("SHA-256")
        try {
            FileOutputStream(temp.toFile()).use { file ->
                val full = DigestOutputStream(BufferedOutputStream(file, 65536), fileHash)
                val body = DigestOutputStream(full, bodyHash)
                val out = DataOutputStream(body)
                out.writeInt(MAGIC); out.writeInt(VERSION)
                out.writeUTF(identity.sourceSha256); out.writeUTF(identity.dataSha256); out.writeLong(identity.seed)
                out.writeUTF(tokenizer.sha256)
                val c = params.config
                for (x in intArrayOf(c.arch.ordinal, c.vocab, c.dim, c.layers, c.heads, c.context, c.mlp)) out.writeInt(x)
                val spec = LmTrainingSpec.from(trainer)
                out.writeFloat(spec.learningRate); out.writeFloat(spec.weightDecay)
                out.writeInt(spec.warmup); out.writeInt(spec.totalSteps); out.writeFloat(spec.clip); out.writeInt(spec.workers)
                out.writeUTF("LM1_ADAMW_UNCORRECTED_EPS_V1")
                out.writeLong(params.count.toLong())
                for ((name, rows, cols) in c.layout()) {
                    out.writeUTF(name); out.writeInt(rows); out.writeInt(cols)
                    for (x in params.mats.getValue(name).data) out.writeFloat(x)
                }
                trainer.writeOptimizerState(out)
                out.flush(); body.on(false)
                out.write(bodyHash.digest()); out.flush(); file.fd.sync()
            }
            val hash = LmBpeTokenizer.hex(fileHash.digest())
            val target = directory.resolve("$hash.lmtrain")
            try { Files.createLink(target, temp) }
            catch (_: FileAlreadyExistsException) { require(sha(target) == hash) { "existing state is damaged" } }
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
            target
        } finally { Files.deleteIfExists(temp) }
    }

    fun load(path: Path, expectedFileHash: String, tokenizer: LmBpeTokenizer,
             identity: LmTrainingIdentity, expectedSpec: LmTrainingSpec): LmTrainer {
        require(HEX.matches(expectedFileHash))
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        require(Files.size(path) in 128..MAX_BYTES)
        val bodyHash = MessageDigest.getInstance("SHA-256")
        val fileHash = MessageDigest.getInstance("SHA-256")
        val full = DigestInputStream(BufferedInputStream(Files.newInputStream(path), 65536), fileHash)
        val body = DigestInputStream(full, bodyHash)
        DataInputStream(body).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) { "not a complete LM1 training state" }
            require(input.readUTF() == identity.sourceSha256 && input.readUTF() == identity.dataSha256 &&
                input.readLong() == identity.seed) { "training identity mismatch" }
            require(input.readUTF() == tokenizer.sha256) { "tokenizer mismatch" }
            val arch = input.readInt(); require(arch in LmArch.entries.indices)
            val c = LmConfig(LmArch.entries[arch], input.readInt(), input.readInt(), input.readInt(),
                input.readInt(), input.readInt(), input.readInt())
            require(c.vocab == tokenizer.vocabSize)
            val count = c.layout().sumOf { (_, rows, cols) -> rows.toLong() * cols }
            require(count in 1..MAX_PARAMETERS)
            val spec = LmTrainingSpec(input.readFloat(), input.readFloat(), input.readInt(), input.readInt(),
                input.readFloat(), input.readInt())
            require(spec == expectedSpec) { "trainer schedule mismatch" }
            require(input.readUTF() == "LM1_ADAMW_UNCORRECTED_EPS_V1")
            require(input.readLong() == count)
            val mats = linkedMapOf<String, Mat>()
            for ((name, rows, cols) in c.layout()) {
                require(input.readUTF() == name && input.readInt() == rows && input.readInt() == cols)
                mats[name] = Mat(rows, cols, FloatArray(rows * cols) { input.readFloat().also { require(it.isFinite()) } })
            }
            val steps = input.readInt(); require(steps in 0..spec.totalSteps)
            val decay = input.readFloat(); val b1 = input.readFloat(); val b2 = input.readFloat(); val eps = input.readFloat()
            require(decay.toRawBits() == spec.weightDecay.toRawBits() && b1 == .9f && b2 == .98f && eps == 1e-8f)
            val first = linkedMapOf<String, FloatArray>(); val second = linkedMapOf<String, FloatArray>()
            for ((name, mat) in mats) {
                require(input.readUTF() == name && input.readInt() == mat.data.size)
                first[name] = FloatArray(mat.data.size) { input.readFloat().also { require(it.isFinite()) } }
                second[name] = FloatArray(mat.data.size) { input.readFloat().also { require(it.isFinite() && it >= 0f) } }
            }
            body.on(false)
            val trailer = ByteArray(32); input.readFully(trailer)
            require(MessageDigest.isEqual(trailer, bodyHash.digest())) { "training state checksum mismatch" }
            require(input.read() == -1) { "trailing state bytes" }
            require(LmBpeTokenizer.hex(fileHash.digest()) == expectedFileHash) { "training file identity mismatch" }
            val trainer = spec.trainer(LmParams(c, mats))
            try {
                trainer.restoreOptimizerState(LmOptimizerState(steps, decay, b1, b2, eps, first, second))
                return trainer
            } catch (failure: Throwable) { trainer.close(); throw failure }
        }
    }
}
