package at.heinz.deviceai.learning.lm

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

/** One native AdamW update. Real-mode execution requires the separately registered protocol. */
object LmTrainingResourceProbe {
    private const val CHECKPOINT_SHA = "93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312"
    private const val TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private const val LEARNING_RATE = 0.0001f
    private const val WEIGHT_DECAY = 0.01f
    private const val CLIP = 1f
    private const val WARMUP = 1
    private const val TOTAL_STEPS = 1
    private const val WORKERS = 1

    private data class Delta(val count: Long, val bitsChanged: Long, val numericChanged: Long,
                             val maxAbs: Double, val l2: Double)
    private data class Result(val before: String, val after: String, val loss: Double,
                              val postLoss: Double, val delta: Delta)

    @JvmStatic fun main(args: Array<String>) {
        require(args.isNotEmpty()) {
            "Usage: --real16m CHECKPOINT TOKENIZER | --self-test NEW_EVIDENCE_DIRECTORY"
        }
        when (args[0]) {
            "--real16m" -> {
                require(args.size == 3)
                require(Runtime.getRuntime().maxMemory() <= 3L * 1024 * 1024 * 1024) { "heap exceeds 3 GiB" }
                require(Runtime.getRuntime().availableProcessors() == 1) { "use -XX:ActiveProcessorCount=1" }
                run(Path.of(args[1]), Path.of(args[2]), CHECKPOINT_SHA, TOKENIZER_SHA, true, "")
                row("", "status", "PASS_ONE_NATIVE_UPDATE")
            }
            "--self-test" -> {
                require(args.size == 2)
                selfTest(Path.of(args[1]))
            }
            else -> error("unknown mode")
        }
    }

    private fun row(prefix: String, key: String, value: Any) {
        println(prefix + key + "\t" + value.toString().replace('\t', ' ').replace('\n', ' ').replace('\r', ' '))
        System.out.flush()
    }

    private fun sha(path: Path): String {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "not a regular non-symlink input: $path" }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(bytes)
                if (n < 0) break
                digest.update(bytes, 0, n)
            }
        }
        return LmBpeTokenizer.hex(digest.digest())
    }

    /** Canonical order: modified-UTF name, big-endian rows/cols and raw IEEE754 Float bits. */
    private fun fingerprint(params: LmParams): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DataOutputStream(BufferedOutputStream(DigestOutputStream(OutputStream.nullOutputStream(), digest), 64 * 1024)).use { out ->
            for ((name, rows, cols) in params.config.layout()) {
                val mat = params.mats.getValue(name)
                out.writeUTF(name); out.writeInt(rows); out.writeInt(cols)
                for (value in mat.data) out.writeInt(value.toRawBits())
            }
        }
        return LmBpeTokenizer.hex(digest.digest())
    }

    /** Compare against the read-only checkpoint without a second full parameter allocation. */
    private fun deltaFromCheckpoint(path: Path, params: LmParams, tokenizer: LmBpeTokenizer): Delta {
        var count = 0L
        var bitsChanged = 0L
        var numericChanged = 0L
        var maxAbs = 0.0
        var sumSquares = 0.0
        DataInputStream(BufferedInputStream(Files.newInputStream(path), 64 * 1024)).use { input ->
            require(input.readInt() == 0x4C4D3143 && input.readInt() == 1)
            val c = params.config
            for (expected in intArrayOf(c.arch.ordinal, c.vocab, c.dim, c.layers, c.heads, c.context, c.mlp)) {
                require(input.readInt() == expected)
            }
            require(input.readUTF() == tokenizer.sha256)
            val metaCount = input.readInt()
            require(metaCount in 0..64)
            repeat(metaCount) { input.readUTF(); input.readUTF() }
            for ((name, rows, cols) in c.layout()) {
                require(input.readUTF() == name && input.readInt() == rows && input.readInt() == cols)
                for (updated in params.mats.getValue(name).data) {
                    val originalBits = input.readInt()
                    val original = Float.fromBits(originalBits)
                    check(original.isFinite() && updated.isFinite())
                    count++
                    if (originalBits != updated.toRawBits()) bitsChanged++
                    if (original != updated) numericChanged++
                    val d = updated.toDouble() - original.toDouble()
                    maxAbs = maxOf(maxAbs, abs(d))
                    sumSquares += d * d
                }
            }
            input.readFully(ByteArray(32))
            require(input.read() == -1)
        }
        check(sumSquares.isFinite())
        return Delta(count, bitsChanged, numericChanged, maxAbs, sqrt(sumSquares))
    }

    private fun meanNll(model: LmModel, seq: IntArray): Double {
        val values = model.nll(seq.copyOfRange(0, seq.size - 1), seq.copyOfRange(1, seq.size))
        check(values.size == 7 && values.all { it.isFinite() }) { "non-finite inference NLL" }
        return values.average().also { check(it.isFinite()) }
    }

    private fun run(checkpoint: Path, tokenizerPath: Path, expectedCheckpoint: String,
                    expectedTokenizer: String, real: Boolean, prefix: String): Result {
        val started = System.nanoTime()
        val checkpointBefore = sha(checkpoint)
        val tokenizerBefore = sha(tokenizerPath)
        require(checkpointBefore == expectedCheckpoint) { "checkpoint SHA mismatch; no trainer constructed" }
        require(tokenizerBefore == expectedTokenizer) { "tokenizer SHA mismatch; no trainer constructed" }
        row(prefix, "format", "LM1_TRAINING_RESOURCE_R2_V1")
        row(prefix, "status", "RUNNING")
        row(prefix, "mode", if (real) "ACTUAL_16011776" else "TINY_SELF_TEST")
        row(prefix, "checkpoint_sha256_before", checkpointBefore)
        row(prefix, "tokenizer_sha256_before", tokenizerBefore)
        try {
            val tokenizer = LmBpeTokenizer.deserialize(Files.readAllBytes(tokenizerPath))
            val params = LmCheckpoint.decode(Files.readAllBytes(checkpoint), tokenizer).params
            require(params.config.vocab == tokenizer.vocabSize)
            if (real) {
                require(params.count == 16_011_776 && params.mats.size == 57)
                require(params.config.arch == LmArch.MINGRU && params.config.dim == 512 && params.config.layers == 5)
            }
            check(params.allFinite())
            val seq = intArrayOf(tokenizer.bos, 112, 114, 111, 98, 101, 33, tokenizer.eos)
            require(seq.size == 8 && seq.all { it in 0 until tokenizer.vocabSize })
            val model = LmModel(params)
            val before = fingerprint(params)
            val preLoss = meanNll(model, seq)
            row(prefix, "configuration", params.config)
            row(prefix, "parameters", params.count)
            row(prefix, "tensors", params.mats.size)
            row(prefix, "tokens", seq.joinToString(","))
            row(prefix, "targets", seq.size - 1)
            row(prefix, "batch_size", 1)
            row(prefix, "workers", WORKERS)
            row(prefix, "optimizer", "FRESH_NATIVE_ADAMW_NO_RESTORED_STATE")
            row(prefix, "learning_rate", LEARNING_RATE)
            row(prefix, "weight_decay", WEIGHT_DECAY)
            row(prefix, "beta1_source_default", 0.9f)
            row(prefix, "beta2_source_default", 0.98f)
            row(prefix, "epsilon_source_default", 1e-8f)
            row(prefix, "clip", CLIP)
            row(prefix, "warmup", WARMUP)
            row(prefix, "total_steps", TOTAL_STEPS)
            row(prefix, "weights_sha256_before", before)
            row(prefix, "weights_finite_before", true)
            row(prefix, "nll_before_nats_per_target", preLoss)
            val nativeLoss: Double
            LmTrainer(model, LEARNING_RATE, WEIGHT_DECAY, WARMUP, TOTAL_STEPS, CLIP, WORKERS).use { trainer ->
                check(trainer.steps == 0)
                val rate = trainer.rate(trainer.steps)
                check(rate.isFinite() && rate > 0f && rate == LEARNING_RATE)
                row(prefix, "trainer_steps_before", trainer.steps)
                row(prefix, "actual_step_learning_rate", rate)
                val stepStarted = System.nanoTime()
                nativeLoss = trainer.step(listOf(seq))
                row(prefix, "native_step_elapsed_ms", (System.nanoTime() - stepStarted) / 1_000_000.0)
                row(prefix, "trainer_steps_after", trainer.steps)
                row(prefix, "native_step_nll_before_update", nativeLoss)
                row(prefix, "native_step_loss_finite", nativeLoss.isFinite())
                check(trainer.steps == 1 && nativeLoss.isFinite())
                check(abs(nativeLoss - preLoss) <= 1e-5 * maxOf(1.0, abs(preLoss))) { "pre-update NLL disagreement" }
            }
            check(params.allFinite())
            val after = fingerprint(params)
            val delta = deltaFromCheckpoint(checkpoint, params, tokenizer)
            check(delta.count == params.count.toLong() && delta.numericChanged > 0L && before != after)
            val postLoss = meanNll(model, seq)
            row(prefix, "weights_finite_after", true)
            row(prefix, "weights_sha256_after", after)
            row(prefix, "parameters_compared", delta.count)
            row(prefix, "parameters_bits_changed", delta.bitsChanged)
            row(prefix, "parameters_numeric_changed", delta.numericChanged)
            row(prefix, "weight_delta_max_abs", delta.maxAbs)
            row(prefix, "weight_delta_l2", delta.l2)
            row(prefix, "nll_after_nats_per_target", postLoss)
            row(prefix, "gradient_finiteness", "UNMEASURED_NATIVE_TRAINER_DOES_NOT_EXPOSE_GRADIENTS")
            row(prefix, "gradient_norm", "UNMEASURED_NATIVE_TRAINER_DISCARDS_OPTIMIZER_RETURN_VALUE")
            row(prefix, "optimizer_moment_finiteness", "UNMEASURED_PRIVATE_NATIVE_STATE")
            row(prefix, "memory_measurement", "EXTERNAL_PROCESS_MEASUREMENT_ONLY")
            row(prefix, "trained_checkpoint_saved", false)
            row(prefix, "quality_or_promotion_claim", false)
            return Result(before, after, nativeLoss, postLoss, delta)
        } catch (failure: Throwable) {
            row(prefix, "status", "FAIL")
            row(prefix, "failure", failure.javaClass.name + ": " + failure.message)
            throw failure
        } finally {
            val checkpointAfter = sha(checkpoint)
            val tokenizerAfter = sha(tokenizerPath)
            row(prefix, "checkpoint_sha256_after", checkpointAfter)
            row(prefix, "tokenizer_sha256_after", tokenizerAfter)
            row(prefix, "inputs_unchanged", checkpointAfter == checkpointBefore && tokenizerAfter == tokenizerBefore)
            row(prefix, "probe_elapsed_ms", (System.nanoTime() - started) / 1_000_000.0)
            check(checkpointAfter == checkpointBefore && tokenizerAfter == tokenizerBefore) { "input changed during probe" }
        }
    }

    private fun selfTest(directory: Path) {
        require(!Files.exists(directory)) { "self-test evidence directory must be new" }
        Files.createDirectories(directory)
        val tokenizer = LmBpeTokenizer.bytes()
        val params = LmParams.init(LmConfig(LmArch.MINGRU, tokenizer.vocabSize, 8, 1, context = 8), 20261104L)
        val checkpoint = directory.resolve("tiny-input.lm1")
        val tokenizerPath = directory.resolve("tiny-tokenizer.lmbp")
        Files.write(checkpoint, LmCheckpoint.encode(params, tokenizer, mapOf("purpose" to "tiny-resource-probe-self-test")), StandardOpenOption.CREATE_NEW)
        Files.write(tokenizerPath, tokenizer.serialize(), StandardOpenOption.CREATE_NEW)
        val checkpointSha = sha(checkpoint)
        val tokenizerSha = sha(tokenizerPath)
        val first = run(checkpoint, tokenizerPath, checkpointSha, tokenizerSha, false, "tiny1.")
        val second = run(checkpoint, tokenizerPath, checkpointSha, tokenizerSha, false, "tiny2.")
        check(first == second) { "fixed tiny one-step runs are not deterministic" }
        check(runCatching { run(checkpoint, tokenizerPath, "0".repeat(64), tokenizerSha, false, "rejected.") }.exceptionOrNull() is IllegalArgumentException)
        row("", "tiny_determinism", "PASS")
        row("", "wrong_input_sha_rejected_before_trainer", "PASS")
        row("", "real16m_training_executed", false)
        row("", "status", "PASS_TINY_ONLY")
    }
}
