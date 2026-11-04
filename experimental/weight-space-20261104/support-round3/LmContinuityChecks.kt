package at.heinz.deviceai.learning.lm

import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object LmContinuityChecks {
    private var count = 0
    private fun ok(name: String, condition: Boolean) {
        check(condition) { "failed: $name" }; count++
        println("CASE\t$name\tPASS")
    }
    private fun reject(name: String, action: () -> Unit) = ok(name, runCatching(action).isFailure)

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        val root = Path.of(args[0]); require(!Files.exists(root)); Files.createDirectories(root)
        val dir = Files.createDirectory(root.resolve("states"))
        val id = LmTrainingIdentity("1".repeat(64), LmTrainingFixture.dataSha256, LmTrainingFixture.SEED)
        val spec = LmTrainingSpec(.001f, .01f, 3, 32, 1f, 1)
        val tok = LmTrainingFixture.tokenizer
        val full: Path
        LmTrainingFixture.fresh().use { trainer ->
            LmTrainingFixture.advance(trainer, 24)
            full = LmTrainingState.save(dir, trainer, tok, id)
            println("REFERENCE\tweights=${LmTrainingFixture.weightHash(trainer.model.params)}\tstate=${full.fileName}")
        }
        val partial: Path
        val partialWeights: ByteArray
        LmTrainingFixture.fresh().use { trainer ->
            LmTrainingFixture.advance(trainer, 8)
            partial = LmTrainingState.save(dir, trainer, tok, id)
            val before = Files.getLastModifiedTime(partial)
            ok("idempotent_publication", partial == LmTrainingState.save(dir, trainer, tok, id) &&
                before == Files.getLastModifiedTime(partial))
            partialWeights = LmCheckpoint.encode(trainer.model.params, tok, emptyMap())
        }
        val hash = LmTrainingState.sha(partial)
        fun load(path: Path = partial, identity: LmTrainingIdentity = id, config: LmTrainingSpec = spec,
                 expected: String = LmTrainingState.sha(path), tokenizer: LmBpeTokenizer = tok) =
            LmTrainingState.load(path, expected, tokenizer, identity, config)
        load().use { resumed ->
            ok("restored_optimizer_step", resumed.steps == 8)
            ok("restored_scheduler_rate", resumed.rate(resumed.steps).toRawBits() ==
                LmTrainingFixture.fresh().use { it.rate(8).toRawBits() })
            ok("exact_checkpoint_round_trip", LmTrainingState.save(dir, resumed, tok, id) == partial)
            val state = resumed.optimizerState()
            ok("moments_present", state.first.values.any { a -> a.any { it != 0f } } &&
                state.second.values.any { a -> a.any { it > 0f } })
            reject("live_optimizer_restore_rejected") { resumed.restoreOptimizerState(state) }
            LmTrainingFixture.advance(resumed, 24)
            ok("uninterrupted_equals_full_resume", LmTrainingState.save(dir, resumed, tok, id) == full)
        }
        LmTrainingFixture.trainer(LmCheckpoint.decode(partialWeights, tok).params).use { reset ->
            for (step in 8 until 24) reset.step(LmTrainingFixture.batch(step))
            ok("weights_only_restart_diverges", LmTrainingState.save(dir, reset, tok, id) != full)
        }
        LmTrainingFixture.trainer(LmCheckpoint.decode(partialWeights, tok).params).use { reset ->
            val zero = reset.optimizerState().copy(steps = 8)
            reset.restoreOptimizerState(zero)
            LmTrainingFixture.advance(reset, 24)
            ok("moment_reset_alone_diverges", LmTrainingState.save(dir, reset, tok, id) != full)
        }
        LmTrainingFixture.fresh().use { fresh ->
            val state = fresh.optimizerState()
            val broken = state.second.mapValues { it.value.copyOf() }
            broken.getValue("nf")[7] = Float.NaN
            reject("invalid_moment_restore_rejected") { fresh.restoreOptimizerState(state.copy(second = broken)) }
            val after = fresh.optimizerState()
            ok("invalid_restore_preserves_optimizer", after.steps == 0 &&
                after.first.values.all { a -> a.all { it == 0f } } && after.second.values.all { a -> a.all { it == 0f } })
        }
        reject("wrong_source") { load(identity = id.copy(sourceSha256 = "2".repeat(64))).close() }
        reject("wrong_data") { load(identity = id.copy(dataSha256 = "3".repeat(64))).close() }
        reject("wrong_seed") { load(identity = id.copy(seed = id.seed + 1)).close() }
        reject("wrong_file_hash") { load(expected = "0".repeat(64)).close() }
        reject("wrong_warmup") { load(config = spec.copy(warmup = 4)).close() }
        reject("wrong_worker_count") { load(config = spec.copy(workers = 2)).close() }
        reject("wrong_learning_rate") { load(config = spec.copy(learningRate = .002f)).close() }
        val merged = ByteBuffer.allocate(16).putInt(0x4c4d4250).putInt(1).putInt(97).putInt(98).array()
        reject("wrong_tokenizer") { load(tokenizer = LmBpeTokenizer.deserialize(merged)).close() }
        val raw = Files.readAllBytes(partial)
        fun damaged(name: String, bytes: ByteArray): Path = root.resolve(name).also { Files.write(it, bytes) }
        val flipped = raw.copyOf(); flipped[flipped.size / 2] = (flipped[flipped.size / 2].toInt() xor 1).toByte()
        reject("body_checksum_corruption") { load(damaged("flipped", flipped)).close() }
        reject("truncated_state") { load(damaged("truncated", raw.copyOf(raw.size - 9))).close() }
        reject("trailing_bytes") { load(damaged("trailing", raw + byteArrayOf(1))).close() }
        reject("weight_only_format") { load(damaged("weights.lm1", partialWeights)).close() }
        val negative = raw.copyOf()
        ByteBuffer.wrap(negative).putInt(negative.size - 36, (-1f).toRawBits())
        MessageDigest.getInstance("SHA-256").digest(negative.copyOfRange(0, negative.size - 32))
            .copyInto(negative, negative.size - 32)
        reject("rehashed_negative_variance") { load(damaged("negative_variance", negative)).close() }
        val link = root.resolve("linked-state"); Files.createSymbolicLink(link, partial)
        reject("symlink_input") { load(link, expected = hash).close() }
        val linkedDir = root.resolve("linked-directory"); Files.createSymbolicLink(linkedDir, dir)
        load().use { trainer -> reject("symlink_output_directory") { LmTrainingState.save(linkedDir, trainer, tok, id) } }
        val badDir = Files.createDirectory(root.resolve("occupied"))
        val occupied = badDir.resolve(partial.fileName); Files.write(occupied, byteArrayOf(1, 2, 3))
        load().use { trainer -> reject("existing_damaged_target") { LmTrainingState.save(badDir, trainer, tok, id) } }
        ok("damaged_target_never_overwritten", Files.readAllBytes(occupied).contentEquals(byteArrayOf(1, 2, 3)))
        ok("original_checkpoint_unchanged", LmTrainingState.sha(partial) == hash)
        println("SUMMARY\tpassed=$count\tfailed=0\tfull=${full.fileName}\tpartial=${partial.fileName}")
    }
}
