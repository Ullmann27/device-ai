package at.heinz.deviceai.learning.lm

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestOutputStream
import java.security.MessageDigest

/** No optimizer update is called. */
object LmLargeStateProbe {
    private const val MODEL_SHA = "93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312"
    private const val TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private data class Saved(val path: Path, val weights: String)
    private fun row(name: String, value: Any) { println("$name\t$value"); System.out.flush() }
    private fun fingerprint(params: LmParams): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DataOutputStream(BufferedOutputStream(DigestOutputStream(OutputStream.nullOutputStream(), digest), 65536)).use { out ->
            for ((name, rows, cols) in params.config.layout()) {
                out.writeUTF(name); out.writeInt(rows); out.writeInt(cols)
                for (x in params.mats.getValue(name).data) out.writeInt(x.toRawBits())
            }
        }
        return LmBpeTokenizer.hex(digest.digest())
    }
    private fun initial(model: Path, directory: Path, tok: LmBpeTokenizer,
                        id: LmTrainingIdentity, spec: LmTrainingSpec): Saved {
        val params = LmCheckpoint.decode(Files.readAllBytes(model), tok).params
        require(params.count == 16_011_776 && params.mats.size == 57)
        require(params.config.arch == LmArch.MINGRU && params.config.dim == 512 && params.config.layers == 5)
        val weights = fingerprint(params)
        row("parameters", params.count); row("tensors", params.mats.size); row("weights_before", weights)
        return spec.trainer(params).use { trainer ->
            check(trainer.steps == 0)
            val saved = LmTrainingState.save(directory, trainer, tok, id)
            check(trainer.steps == 0)
            Saved(saved, weights)
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4)
        require(Runtime.getRuntime().availableProcessors() == 1)
        require(Runtime.getRuntime().maxMemory() <= 1024L * 1024 * 1024)
        val model = Path.of(args[0]); val tokenizer = Path.of(args[1]); val out = Path.of(args[2])
        require(LmTrainingState.sha(model) == MODEL_SHA && LmTrainingState.sha(tokenizer) == TOKENIZER_SHA)
        require(!Files.exists(out)); Files.createDirectory(out)
        val tok = LmBpeTokenizer.deserialize(Files.readAllBytes(tokenizer))
        val noData = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256")
            .digest("LM1_STATE_ONLY_V1_NO_TRAINING_DATA".toByteArray(Charsets.UTF_8)))
        val id = LmTrainingIdentity(args[3], noData, 0L)
        val spec = LmTrainingSpec(.0001f, .01f, 1, 1, 1f, 1)
        row("format", "LM1_16M_STATE_ONLY_V1")
        row("optimizer_state_origin", "FRESH_ZERO_MOMENTS_NOT_HISTORICAL_RESUME")
        row("data", "NONE"); row("source_identity", args[3])
        row("tokenizer_bos", tok.bos); row("tokenizer_eos", tok.eos)
        val saved = initial(model, out, tok, id, spec)
        val hash = LmTrainingState.sha(saved.path)
        row("state_file_sha256", hash); row("state_file_bytes", Files.size(saved.path))
        LmTrainingState.load(saved.path, hash, tok, id, spec).use { restored ->
            require(restored.steps == 0 && restored.model.params.count == 16_011_776)
            require(restored.model.params.allFinite())
            val after = fingerprint(restored.model.params)
            require(after == saved.weights)
            val moments = restored.optimizerState()
            require(moments.steps == 0)
            require(moments.first.values.all { a -> a.all { it == 0f } } &&
                moments.second.values.all { a -> a.all { it == 0f } })
            row("moment_values_checked", moments.first.values.sumOf { it.size.toLong() } +
                moments.second.values.sumOf { it.size.toLong() })
            row("moment_values_zero", true)
            val again = LmTrainingState.save(out, restored, tok, id)
            require(again == saved.path && LmTrainingState.sha(again) == hash)
            row("full_state_roundtrip_byte_identical", true); row("weights_after", after)
            row("optimizer_steps_after", restored.steps)
        }
        require(LmTrainingState.sha(model) == MODEL_SHA && LmTrainingState.sha(tokenizer) == TOKENIZER_SHA)
        row("original_model_sha256_after", MODEL_SHA); row("original_tokenizer_sha256_after", TOKENIZER_SHA)
        row("original_inputs_unchanged", true)
        row("optimizer_updates", 0); row("inference_calls", 0)
        row("status", "PASS_16M_STATE_ONLY")
    }
}
