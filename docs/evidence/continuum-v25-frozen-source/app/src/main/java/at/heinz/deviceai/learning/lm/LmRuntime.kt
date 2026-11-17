package at.heinz.deviceai.learning.lm

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Random
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Local LM1 inference outside the research harness; the app is meant to call it from a background worker.
 * A model is loaded only together with the tokenizer it is bound to and only when its bytes have the expected
 * SHA-256; damaged, foreign or oversized checkpoints are rejected. Generation runs under hard limits (new tokens,
 * wall time, context, cancellation). minGRU runs step by step with one state vector per layer, using the same
 * kernels as the training forward pass; the Transformer recomputes its window for every token.
 * Everything returned is free model text ([Kind.FREE_GENERATION]): never a verified fact, never a retrieved source.
 */
class LmRuntime private constructor(val model: LmModel, val tokenizer: LmBpeTokenizer, val identity: Identity) {

    data class Identity(val checkpointSha256: String, val tokenizerSha256: String, val arch: LmArch, val parameters: Int,
                        val parameterBytes: Long, val context: Int, val meta: Map<String, String>)

    data class Request(val prompt: String, val maxNewTokens: Int = 48, val temperature: Double = 0.0, val topK: Int = 1,
                       val seed: Long = 0, val maxMillis: Long = 2_000)

    enum class Stop { EOS, MAX_TOKENS, CONTEXT, TIME, CANCELLED }

    /** Model text only; facts belong to the memory archive with their sources, not to this runtime. */
    enum class Kind { FREE_GENERATION }

    data class Result(val text: String, val newTokens: Int, val promptTokens: Int, val promptTruncated: Boolean,
                      val stop: Stop, val millis: Long, val kind: Kind = Kind.FREE_GENERATION)

    /** Continues [Request.prompt]; [cancelled] is polled before every token. */
    fun generate(request: Request, cancelled: () -> Boolean = { false }): Result {
        require(request.maxNewTokens in 1..MAX_NEW_TOKENS) { "maxNewTokens must be 1..$MAX_NEW_TOKENS" }
        require(request.temperature.isFinite() && request.temperature >= 0 && request.topK >= 1) { "invalid sampling" }
        require(request.maxMillis in 1..MAX_MILLIS) { "maxMillis must be 1..$MAX_MILLIS" }
        require(request.prompt.toByteArray(Charsets.UTF_8).size <= MAX_PROMPT_BYTES) { "prompt longer than $MAX_PROMPT_BYTES bytes" }
        val start = System.nanoTime()
        val context = model.config.context
        val encoded = tokenizer.encode(request.prompt)
        val keep = maxOf(1, context - 1 - request.maxNewTokens)
        val prompt = if (encoded.size > keep) encoded.copyOfRange(encoded.size - keep, encoded.size) else encoded
        val stepper = if (model.config.arch == LmArch.MINGRU) MinGruStepper(model.params) else null
        val window = ArrayList<Int>(context).apply { add(tokenizer.bos); prompt.forEach { add(it) } }
        val logits = FloatArray(model.config.vocab)
        val probs = FloatArray(model.config.vocab)
        stepper?.let { s -> for (id in window) s.step(id, logits) }
        val rnd = Random(request.seed)
        val produced = ByteArrayOutputStream()
        var newTokens = 0
        var stop = Stop.MAX_TOKENS
        while (newTokens < request.maxNewTokens) {
            if (cancelled()) { stop = Stop.CANCELLED; break }
            if ((System.nanoTime() - start) / 1_000_000 >= request.maxMillis) { stop = Stop.TIME; break }
            if (window.size >= context) { stop = Stop.CONTEXT; break }
            val p = if (stepper != null) {
                Kernels.softmaxRow(logits, 0, logits.size, probs); probs
            } else model.nextDistribution(window.toIntArray())
            p[tokenizer.bos] = 0f
            val next = LmEval.sample(p, request.temperature, request.topK, rnd)
            if (next == tokenizer.eos) { stop = Stop.EOS; break }
            produced.write(tokenizer.pieceBytes(next))
            window += next
            newTokens++
            stepper?.step(next, logits)
        }
        return Result(completeUtf8(produced.toByteArray()), newTokens, prompt.size, prompt.size < encoded.size, stop,
            (System.nanoTime() - start) / 1_000_000)
    }

    /** Logits after [ids], computed position by position (minGRU); lets tests compare with [LmModel.logits]. */
    internal fun stepwiseLogits(ids: IntArray): FloatArray {
        check(model.config.arch == LmArch.MINGRU)
        val stepper = MinGruStepper(model.params)
        val out = FloatArray(model.config.vocab)
        for (id in ids) stepper.step(id, out)
        return out
    }

    /**
     * minGRU forward for one position at a time. Every operation is per position except the scan, whose state is
     * kept per layer; the matrix kernels and formulas are those of [LmModel.logits], so the logits are the same.
     */
    private class MinGruStepper(params: LmParams) {
        private val c = params.config
        private val m = params.mats
        private val state = Array(c.layers) { FloatArray(c.dim) }
        private val x = Mat(1, c.dim)
        private val h = Mat(1, c.dim)
        private val gated = Mat(1, c.dim)

        fun step(id: Int, logits: FloatArray) {
            val d = c.dim
            System.arraycopy(m.getValue("tok").data, id * d, x.data, 0, d)
            for (l in 0 until c.layers) {
                rmsnorm(x, m.getValue("l$l.n1"), h)
                val z = Kernels.mm(h, m.getValue("l$l.wz"))
                val bz = m.getValue("l$l.bz").data
                for (j in 0 until d) z.data[j] = Tape.sigmoid(z.data[j] + bz[j])
                val cand = Kernels.mm(h, m.getValue("l$l.wc"))
                val g = Kernels.mm(h, m.getValue("l$l.wg"))
                val s = state[l]
                for (j in 0 until d) {
                    s[j] = (1f - z.data[j]) * s[j] + z.data[j] * cand.data[j]
                    val v = g.data[j]
                    gated.data[j] = s[j] * (v * Tape.sigmoid(v))
                }
                val mix = Kernels.mm(gated, m.getValue("l$l.wo"))
                for (j in 0 until d) x.data[j] += mix.data[j]
                rmsnorm(x, m.getValue("l$l.n2"), h)
                val a = Kernels.mm(h, m.getValue("l$l.w1"))
                val b1 = m.getValue("l$l.b1").data
                for (j in a.data.indices) a.data[j] = gelu(a.data[j] + b1[j])
                val f = Kernels.mm(a, m.getValue("l$l.w2"))
                val b2 = m.getValue("l$l.b2").data
                for (j in 0 until d) x.data[j] += f.data[j] + b2[j]
            }
            rmsnorm(x, m.getValue("nf"), h)
            System.arraycopy(Kernels.mmBt(h, m.getValue("tok")).data, 0, logits, 0, logits.size)
        }

        private fun rmsnorm(src: Mat, gain: Mat, out: Mat) {
            var ss = 0.0
            for (j in 0 until c.dim) { val v = src.data[j]; ss += v * v }
            val s = (1.0 / sqrt(ss / c.dim + 1e-5f)).toFloat()
            for (j in 0 until c.dim) out.data[j] = src.data[j] * s * gain.data[j]
        }

        private fun gelu(v: Float): Float = 0.5f * v * (1f + tanh(GELU_C * (v + 0.044715f * v * v * v)))
    }

    companion object {
        const val MAX_CHECKPOINT_BYTES = 64 shl 20
        const val MAX_NEW_TOKENS = 512
        const val MAX_PROMPT_BYTES = 8192
        const val MAX_MILLIS = 60_000L
        private val GELU_C = sqrt(2.0 / Math.PI).toFloat()

        /** Loads [checkpoint] if its SHA-256 is [expectedSha256] and it is bound to the tokenizer in [tokenizerBytes]. */
        fun load(checkpoint: ByteArray, tokenizerBytes: ByteArray, expectedSha256: String): LmRuntime {
            require(checkpoint.size in 65..MAX_CHECKPOINT_BYTES) { "checkpoint size out of bounds" }
            val sha = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(checkpoint))
            require(MessageDigest.isEqual(sha.toByteArray(), expectedSha256.lowercase().toByteArray())) { "checkpoint is not the expected one" }
            val tokenizer = LmBpeTokenizer.deserialize(tokenizerBytes)
            val restored = LmCheckpoint.decode(checkpoint, tokenizer)
            val params = restored.params
            return LmRuntime(LmModel(params), tokenizer, Identity(sha, tokenizer.sha256, params.config.arch, params.count,
                4L * params.count, params.config.context, restored.meta))
        }

        /** Loads a checkpoint from a content-addressed store, which verifies the file against its name. */
        fun load(store: LmCheckpointStore, sha256: String, tokenizerBytes: ByteArray): LmRuntime =
            load(store.get(sha256), tokenizerBytes, sha256)

        /** Decodes UTF-8, dropping an incomplete multi-byte sequence at the end (a token limit can cut a character). */
        fun completeUtf8(bytes: ByteArray): String {
            var end = bytes.size
            var i = end - 1
            var continuation = 0
            while (i >= 0 && (bytes[i].toInt() and 0xC0) == 0x80 && continuation < 3) { i--; continuation++ }
            if (i >= 0) {
                val lead = bytes[i].toInt() and 0xFF
                val length = when { lead < 0x80 -> 1; lead >= 0xF0 -> 4; lead >= 0xE0 -> 3; lead >= 0xC0 -> 2; else -> 1 }
                if (continuation + 1 < length) end = i
            }
            return bytes.copyOf(end).toString(Charsets.UTF_8)
        }
    }
}
