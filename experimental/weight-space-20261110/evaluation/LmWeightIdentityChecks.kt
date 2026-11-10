package at.heinz.deviceai.learning.lm

import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

object LmWeightIdentityChecks {
    private data class Tensor(val name: String, val rows: Int, val cols: Int, val bits: IntArray)
    private data class Snapshot(val tensors: List<Tensor>, val hash: String) {
        val count: Long get() = tensors.sumOf { it.bits.size.toLong() }
    }
    private data class Difference(val bitChanges: Long, val numericChanges: Long, val maxAbsDelta: Double)
    private var passed = 0
    private var failed = 0
    private var updates = 0

    private fun checkCase(name: String, condition: Boolean) {
        if (condition) passed++ else failed++
        println("CHECK\tname=$name\tpass=$condition")
    }

    private fun snapshot(params: LmParams): Snapshot {
        val digest = MessageDigest.getInstance("SHA-256")
        val tensors = params.config.layout().map { (name, rows, cols) ->
            val values = params.mats.getValue(name).data
            require(values.size == rows * cols && values.all { it.isFinite() })
            Tensor(name, rows, cols, IntArray(values.size) { values[it].toRawBits() })
        }
        DataOutputStream(DigestOutputStream(OutputStream.nullOutputStream(), digest)).use { out ->
            out.writeUTF("LM1_WEIGHT_BITS_V1")
            out.writeInt(tensors.size)
            for (tensor in tensors) {
                out.writeUTF(tensor.name)
                out.writeInt(tensor.rows)
                out.writeInt(tensor.cols)
                for (bits in tensor.bits) out.writeInt(bits)
            }
        }
        return Snapshot(tensors, LmBpeTokenizer.hex(digest.digest()))
    }

    private fun difference(name: String, before: Snapshot, after: Snapshot): Difference {
        require(before.tensors.size == after.tensors.size)
        var bits = 0L
        var numeric = 0L
        var maximum = 0.0
        for ((a, b) in before.tensors.zip(after.tensors)) {
            require(a.name == b.name && a.rows == b.rows && a.cols == b.cols)
            require(a.bits.size == b.bits.size)
            for (index in a.bits.indices) {
                if (a.bits[index] != b.bits[index]) bits++
                val av = Float.fromBits(a.bits[index])
                val bv = Float.fromBits(b.bits[index])
                if (av != bv) numeric++
                maximum = max(maximum, abs(av.toDouble() - bv.toDouble()))
            }
        }
        println("COMPARISON\tname=$name\tparameters=${before.count}\tbefore=${before.hash}" +
            "\tafter=${after.hash}\tbitChanges=$bits\tnumericChanges=$numeric\tmaxAbsDelta=$maximum")
        return Difference(bits, numeric, maximum)
    }

    private fun advance(trainer: LmTrainer, until: Int) {
        val before = trainer.steps
        LmTrainingFixture.advance(trainer, until)
        updates += trainer.steps - before
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        val root = Path.of(args[0])
        require(!Files.exists(root))
        Files.createDirectories(root)
        val states = Files.createDirectory(root.resolve("states"))
        val identity = LmTrainingIdentity("1".repeat(64), LmTrainingFixture.dataSha256, LmTrainingFixture.SEED)
        val spec = LmTrainingSpec(.001f, .01f, 3, 32, 1f, 1)
        val tokenizer = LmTrainingFixture.tokenizer

        LmTrainingFixture.fresh().use { trainer ->
            val before = snapshot(trainer.model.params)
            val initialState = LmTrainingState.save(states, trainer, tokenizer, identity)
            val metadataState = LmTrainingState.save(states, trainer, tokenizer, identity.copy(seed = identity.seed + 1))
            val metadataDifference = difference("metadata_only", before, snapshot(trainer.model.params))
            checkCase("legacy_metric_false_positive_metadata", initialState != metadataState)
            checkCase("direct_weights_reject_metadata_false_positive", metadataDifference.bitChanges == 0L)
            trainer.restoreOptimizerState(trainer.optimizerState().copy(steps = 8))
            val stepState = LmTrainingState.save(states, trainer, tokenizer, identity)
            val stepDifference = difference("step_only", before, snapshot(trainer.model.params))
            checkCase("legacy_metric_false_positive_step", initialState != stepState)
            checkCase("direct_weights_reject_step_false_positive", stepDifference.bitChanges == 0L)
        }

        val original = LmTrainingFixture.fresh().use { snapshot(it.model.params) }
        val full: Snapshot
        val fullState: Path
        LmTrainingFixture.fresh().use { trainer ->
            advance(trainer, 24)
            full = snapshot(trainer.model.params)
            fullState = LmTrainingState.save(states, trainer, tokenizer, identity)
        }
        val learning = difference("initial_to_step24", original, full)
        checkCase("training_changes_actual_weights", learning.numericChanges > 0L)

        val partial: Path
        val partialWeights: ByteArray
        val partialSnapshot: Snapshot
        LmTrainingFixture.fresh().use { trainer ->
            advance(trainer, 8)
            partial = LmTrainingState.save(states, trainer, tokenizer, identity)
            partialWeights = LmCheckpoint.encode(trainer.model.params, tokenizer, emptyMap())
            partialSnapshot = snapshot(trainer.model.params)
        }
        val partialHash = LmTrainingState.sha(partial)
        LmTrainingState.load(partial, partialHash, tokenizer, identity, spec).use { trainer ->
            val restored = difference("saved_to_loaded_step8", partialSnapshot, snapshot(trainer.model.params))
            checkCase("restored_weights_bit_identical", restored.bitChanges == 0L)
            checkCase("restored_step_is_eight", trainer.steps == 8)
            val optimizer = trainer.optimizerState()
            checkCase("nonzero_moments_restored", optimizer.first.values.any { a -> a.any { it != 0f } } &&
                optimizer.second.values.any { a -> a.any { it > 0f } })
            advance(trainer, 24)
            val resumed = difference("full_vs_complete_resume", full, snapshot(trainer.model.params))
            checkCase("complete_resume_weights_bit_identical", resumed.bitChanges == 0L)
            checkCase("complete_resume_state_identical", LmTrainingState.save(states, trainer, tokenizer, identity) == fullState)
        }
        LmTrainingFixture.trainer(LmCheckpoint.decode(partialWeights, tokenizer).params).use { trainer ->
            for (step in 8 until 24) {
                check(trainer.step(LmTrainingFixture.batch(step)).isFinite())
                updates++
            }
            val reset = difference("full_vs_weights_only_resume", full, snapshot(trainer.model.params))
            checkCase("weights_only_resume_changes_actual_weights", reset.numericChanges > 0L)
            checkCase("weights_only_resume_step_is_sixteen", trainer.steps == 16)
        }
        LmTrainingFixture.trainer(LmCheckpoint.decode(partialWeights, tokenizer).params).use { trainer ->
            trainer.restoreOptimizerState(trainer.optimizerState().copy(steps = 8))
            advance(trainer, 24)
            val reset = difference("full_vs_moment_only_reset", full, snapshot(trainer.model.params))
            checkCase("moment_reset_changes_actual_weights", reset.numericChanges > 0L)
            checkCase("moment_reset_step_is_twenty_four", trainer.steps == 24)
        }
        checkCase("partial_state_unchanged", LmTrainingState.sha(partial) == partialHash)
        checkCase("exact_update_budget", updates == 80)
        println("SUMMARY\tpassed=$passed\tfailed=$failed\tupdates=$updates\tparameters=${full.count}")
        check(failed == 0) { "weight identity checks failed: $failed" }
    }
}
