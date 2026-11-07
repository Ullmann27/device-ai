package at.heinz.deviceai.learning.lm

import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

enum class LmArch { TRANSFORMER, MINGRU }

/**
 * Shape of a language core. [context] is the explicit, finite number of token positions the model can see,
 * BOS included.
 */
data class LmConfig(
    val arch: LmArch,
    val vocab: Int,
    val dim: Int,
    val layers: Int,
    val heads: Int = 1,
    val context: Int = 256,
    val mlp: Int = 4 * dim
) {
    init {
        require(vocab in 258..8192 && dim in 8..512 && layers in 1..12 && heads >= 1 && dim % heads == 0)
        require(context in 8..2048 && mlp in 8..4096)
    }

    /** Parameter names and shapes in checkpoint order. */
    fun layout(): List<Triple<String, Int, Int>> = buildList {
        add(Triple("tok", vocab, dim))
        if (arch == LmArch.TRANSFORMER) add(Triple("pos", context, dim))
        for (l in 0 until layers) {
            add(Triple("l$l.n1", 1, dim))
            if (arch == LmArch.TRANSFORMER) {
                for (w in listOf("wq", "wk", "wv", "wo")) add(Triple("l$l.$w", dim, dim))
            } else {
                add(Triple("l$l.wz", dim, dim)); add(Triple("l$l.bz", 1, dim))
                for (w in listOf("wc", "wg", "wo")) add(Triple("l$l.$w", dim, dim))
            }
            add(Triple("l$l.n2", 1, dim))
            add(Triple("l$l.w1", dim, mlp)); add(Triple("l$l.b1", 1, mlp))
            add(Triple("l$l.w2", mlp, dim)); add(Triple("l$l.b2", 1, dim))
        }
        add(Triple("nf", 1, dim))
    }
}

/** Named parameter matrices in [LmConfig.layout] order. */
class LmParams(val config: LmConfig, val mats: LinkedHashMap<String, Mat>) {
    init {
        val layout = config.layout()
        require(layout.size == mats.size && layout.all { (n, r, c) -> mats[n]?.let { it.rows == r && it.cols == c } == true }) {
            "parameters do not match the configuration"
        }
    }
    val count: Int get() = mats.values.sumOf { it.data.size }
    fun zeros(): LinkedHashMap<String, Mat> = LinkedHashMap(mats.mapValues { Mat(it.value.rows, it.value.cols) })
    fun allFinite(): Boolean = mats.values.all { m -> m.data.all { it.isFinite() } }

    companion object {
        fun init(config: LmConfig, seed: Long): LmParams {
            val rnd = Random(seed)
            val out = LinkedHashMap<String, Mat>()
            val residualScale = 1f / sqrt(2f * config.layers)
            for ((name, r, c) in config.layout()) {
                val m = Mat(r, c)
                val leaf = name.substringAfterLast('.')
                when {
                    leaf in setOf("n1", "n2", "nf") -> m.data.fill(1f)
                    leaf.startsWith("b") -> Unit
                    name == "tok" || name == "pos" -> for (i in m.data.indices) m.data[i] = (rnd.nextGaussian() * .02).toFloat()
                    else -> {
                        val std = 1f / sqrt(r.toFloat()) * if (leaf == "wo" || leaf == "w2") residualScale else 1f
                        for (i in m.data.indices) m.data[i] = (rnd.nextGaussian() * std).toFloat()
                    }
                }
                out[name] = m
            }
            return LmParams(config, out)
        }
    }
}

/**
 * The language core: next-token prediction over a [LmBpeTokenizer] vocabulary.
 * TRANSFORMER: pre-RMSNorm causal multi-head attention + GELU MLP, learned positions.
 * MINGRU: gated linear recurrence (minGRU) + SiLU output gate + GELU MLP; no positions, constant state per step.
 * Both tie the output projection to the token embedding.
 */
class LmModel(val params: LmParams) {
    val config: LmConfig get() = params.config

    fun logits(tape: Tape, nodes: Map<String, Node>, ids: IntArray): Node {
        val c = config
        require(ids.size in 1..c.context) { "sequence of ${ids.size} tokens exceeds context ${c.context}" }
        fun p(name: String) = nodes[name] ?: error("missing parameter $name")
        var x = tape.gather(p("tok"), ids)
        if (c.arch == LmArch.TRANSFORMER) x = tape.add(x, tape.rows(p("pos"), 0, ids.size))
        for (l in 0 until c.layers) {
            val h = tape.rmsnorm(x, p("l$l.n1"))
            val mix = if (c.arch == LmArch.TRANSFORMER) {
                val q = tape.matmul(h, p("l$l.wq")); val k = tape.matmul(h, p("l$l.wk")); val v = tape.matmul(h, p("l$l.wv"))
                val hd = c.dim / c.heads
                val scale = 1f / sqrt(hd.toFloat())
                val heads = (0 until c.heads).map { i ->
                    val qi = if (c.heads == 1) q else tape.colSlice(q, i * hd, hd)
                    val ki = if (c.heads == 1) k else tape.colSlice(k, i * hd, hd)
                    val vi = if (c.heads == 1) v else tape.colSlice(v, i * hd, hd)
                    tape.matmul(tape.causalSoftmax(tape.matmulT(qi, ki), scale), vi)
                }
                tape.matmul(if (heads.size == 1) heads[0] else tape.colConcat(heads), p("l$l.wo"))
            } else {
                val z = tape.sigmoid(tape.addRow(tape.matmul(h, p("l$l.wz")), p("l$l.bz")))
                val state = tape.minGruScan(z, tape.matmul(h, p("l$l.wc")))
                tape.matmul(tape.mul(state, tape.silu(tape.matmul(h, p("l$l.wg")))), p("l$l.wo"))
            }
            x = tape.add(x, mix)
            val h2 = tape.rmsnorm(x, p("l$l.n2"))
            val f = tape.addRow(tape.matmul(tape.gelu(tape.addRow(tape.matmul(h2, p("l$l.w1")), p("l$l.b1"))), p("l$l.w2")), p("l$l.b2"))
            x = tape.add(x, f)
        }
        return tape.matmulT(tape.rmsnorm(x, p("nf")), p("tok"))
    }

    /** Nodes for inference (no gradients). */
    fun frozenNodes(tape: Tape): Map<String, Node> = params.mats.mapValues { tape.constant(it.value) }

    /**
     * Natural-log NLL of every target given the preceding ids (targets −1 are skipped and reported as NaN).
     * ids[t] predicts targets[t].
     */
    fun nll(ids: IntArray, targets: IntArray): DoubleArray {
        require(ids.size == targets.size)
        val tape = Tape(training = false)
        val logits = logits(tape, frozenNodes(tape), ids).value
        val v = logits.cols
        val scratch = FloatArray(logits.data.size)
        return DoubleArray(ids.size) { t ->
            val y = targets[t]
            if (y < 0) Double.NaN else Kernels.softmaxRow(logits.data, t * v, v, scratch) - logits.data[t * v + y]
        }
    }

    /** Probability distribution of the next token after [ids]. */
    fun nextDistribution(ids: IntArray): FloatArray {
        val tape = Tape(training = false)
        val logits = logits(tape, frozenNodes(tape), ids).value
        val v = logits.cols
        val out = FloatArray(logits.data.size)
        Kernels.softmaxRow(logits.data, (ids.size - 1) * v, v, out)
        return out.copyOfRange((ids.size - 1) * v, ids.size * v)
    }
}

/** AdamW with decoupled weight decay on weight matrices (not on gains, biases or embeddings). */
class LmAdamW(private val params: LmParams, private val weightDecay: Float, private val beta1: Float = .9f,
              private val beta2: Float = .98f, private val eps: Float = 1e-8f) {
    private val m = params.mats.mapValues { FloatArray(it.value.data.size) }
    private val v = params.mats.mapValues { FloatArray(it.value.data.size) }
    private val decays = params.mats.keys.associateWith { n ->
        val leaf = n.substringAfterLast('.')
        n != "tok" && n != "pos" && leaf.startsWith("w")
    }
    var steps = 0
        private set

    /** One update with [grads]; returns the global gradient norm before clipping to [clip]. */
    fun step(grads: Map<String, Mat>, lr: Float, clip: Float): Double {
        var sq = 0.0
        for (g in grads.values) for (x in g.data) sq += x.toDouble() * x
        val norm = sqrt(sq)
        val scale = if (norm > clip && norm > 0) (clip / norm).toFloat() else 1f
        steps++
        val b1 = 1.0 - Math.pow(beta1.toDouble(), steps.toDouble())
        val b2 = 1.0 - Math.pow(beta2.toDouble(), steps.toDouble())
        val stepSize = (lr * sqrt(b2) / b1).toFloat()
        for ((name, p) in params.mats) {
            val g = grads.getValue(name).data
            val mm = m.getValue(name); val vv = v.getValue(name)
            val decay = decays.getValue(name)
            for (i in p.data.indices) {
                val gi = g[i] * scale
                mm[i] = beta1 * mm[i] + (1 - beta1) * gi
                vv[i] = beta2 * vv[i] + (1 - beta2) * gi * gi
                if (decay) p.data[i] -= lr * weightDecay * p.data[i]
                p.data[i] -= stepSize * mm[i] / (sqrt(vv[i]) + eps)
            }
        }
        return norm
    }
}

/**
 * Data-parallel training on the JVM: every worker thread runs whole sequences on its own tape and gradient buffers;
 * the buffers are summed before each AdamW step. Deterministic for a fixed thread count and batch order.
 */
class LmTrainer(val model: LmModel, val learningRate: Float, val weightDecay: Float, val warmup: Int, val totalSteps: Int,
                val clip: Float = 1f, threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)) : AutoCloseable {
    private val workers = threads
    private val pool: ExecutorService = Executors.newFixedThreadPool(threads) { r -> Thread(r, "lm-train").apply { isDaemon = true } }
    private val optimizer = LmAdamW(model.params, weightDecay)
    private val buffers = List(threads) { model.params.zeros() }
    val steps: Int get() = optimizer.steps

    fun rate(step: Int): Float {
        if (step < warmup) return learningRate * (step + 1) / warmup
        val progress = ((step - warmup).toDouble() / (totalSteps - warmup).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return (learningRate * (.1 + .9 * .5 * (1 + cos(PI * progress)))).toFloat()
    }

    /**
     * One optimizer step on [batch]; each sequence is BOS + tokens + EOS and is trained on every next-token target.
     * Returns the mean NLL (nats/token) of the batch before the update.
     */
    fun step(batch: List<IntArray>): Double {
        require(batch.isNotEmpty() && batch.all { it.size >= 2 })
        val totalTargets = batch.sumOf { it.size - 1 }
        for (b in buffers) for (m in b.values) m.data.fill(0f)
        val parts = (0 until workers).map { w -> batch.indices.filter { it % workers == w } }
        val losses = pool.invokeAll(parts.mapIndexed { w, indices ->
            Callable {
                var sum = 0.0
                for (i in indices) {
                    val seq = batch[i]
                    val ids = seq.copyOfRange(0, seq.size - 1)
                    val targets = seq.copyOfRange(1, seq.size)
                    val tape = Tape()
                    val nodes = model.params.mats.mapValues { (n, m) -> tape.param(m, buffers[w].getValue(n)) }
                    val loss = tape.crossEntropy(model.logits(tape, nodes, ids), targets, totalTargets)
                    sum += loss.value.data[0].toDouble() * totalTargets
                    tape.backward(loss)
                }
                sum
            }
        }).sumOf { it.get() }
        val total = buffers[0]
        for (w in 1 until workers) for ((n, m) in buffers[w]) { val t = total.getValue(n).data; for (i in t.indices) t[i] += m.data[i] }
        optimizer.step(total, rate(optimizer.steps), clip)
        check(model.params.allFinite()) { "non-finite parameter after step ${optimizer.steps}" }
        return losses / totalTargets
    }

    override fun close() { pool.shutdownNow() }
}

/** Shared evaluation and sampling over a tokenizer and a model. */
object LmEval {
    /**
     * Bits needed to code [text] as one segment that starts after BOS, counting content tokens only (EOS excluded),
     * and the UTF-8 byte count of [text]. Bits / bytes is the bits-per-byte (BPB) measure.
     */
    fun segmentBits(model: LmModel, tokenizer: LmBpeTokenizer, text: String): Pair<Double, Int> {
        val tokens = tokenizer.encode(text)
        require(tokens.size + 1 <= model.config.context) { "segment exceeds the context" }
        val ids = intArrayOf(tokenizer.bos) + tokens
        val targets = tokens + tokenizer.eos
        targets[targets.size - 1] = -1
        val nll = model.nll(ids, targets)
        var nats = 0.0
        for (t in 0 until tokens.size) nats += nll[t]
        return nats / ln(2.0) to text.toByteArray(Charsets.UTF_8).size
    }

    /** Sampled continuation (temperature, top-k) until EOS, [maxTokens] or the context limit. */
    fun generate(model: LmModel, tokenizer: LmBpeTokenizer, prompt: String, maxTokens: Int, temperature: Double,
                 topK: Int, rnd: Random): String {
        var ids = intArrayOf(tokenizer.bos) + tokenizer.encode(prompt)
        val produced = ArrayList<Int>()
        while (produced.size < maxTokens && ids.size < model.config.context) {
            val p = model.nextDistribution(ids)
            p[tokenizer.bos] = 0f
            val next = sample(p, temperature, topK, rnd)
            if (next == tokenizer.eos) break
            produced += next
            ids += next
        }
        return tokenizer.decode(produced.toIntArray())
    }

    fun sample(p: FloatArray, temperature: Double, topK: Int, rnd: Random): Int {
        val order = p.indices.sortedByDescending { p[it] }.take(topK.coerceIn(1, p.size))
        if (temperature <= 0.0) return order.first()
        val w = order.map { exp(ln(p[it].toDouble().coerceAtLeast(1e-30)) / temperature) }
        var r = rnd.nextDouble() * w.sum()
        for ((i, id) in order.withIndex()) { r -= w[i]; if (r <= 0) return id }
        return order.last()
    }
}
