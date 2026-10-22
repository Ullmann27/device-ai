package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.LmBpeTokenizer
import at.heinz.deviceai.learning.lm.Mat
import at.heinz.deviceai.learning.lm.Node
import at.heinz.deviceai.learning.lm.Tape
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/** Token mixer of every layer. DELTA = gated delta-rule fast weights (NF-2), LINEAR = the same without the erase term. */
enum class Mixer { ATTENTION, MINGRU, DELTA, LINEAR }

/** Feed-forward block. RELU = ReLU with a learned threshold (bias) and an activation-L1 penalty (NF-4). */
enum class Ffn { GELU, RELU }

/**
 * Shape of an NF research core. Same skeleton as LM1 (pre-RMSNorm residual blocks, tied output embedding), so that
 * mixers can be compared with everything else held fixed. [adapterRank] > 0 adds a low-rank path W + A·B to every
 * mixer and MLP matrix (NF-1 memory level that never touches the base weights).
 */
data class NfConfig(
    val vocab: Int,
    val dim: Int,
    val layers: Int,
    val heads: Int,
    val context: Int,
    val mlp: Int,
    val mixer: Mixer,
    val decayGate: Boolean = true,
    val writeGate: Boolean = true,
    val ffn: Ffn = Ffn.GELU,
    val adapterRank: Int = 0
) {
    init {
        require(vocab in 4..8192 && dim in 4..512 && layers in 1..12 && heads >= 1 && dim % heads == 0)
        require(context in 4..4096 && mlp in 4..4096 && adapterRank in 0..64)
    }

    val recurrent: Boolean get() = mixer != Mixer.ATTENTION

    private fun mixerMatrices(): List<String> = when (mixer) {
        Mixer.ATTENTION -> listOf("wq", "wk", "wv", "wo")
        Mixer.MINGRU -> listOf("wz", "wc", "wg", "wo")
        Mixer.DELTA, Mixer.LINEAR -> listOf("wq", "wk", "wv", "wg", "wo")
    }

    /** Parameter names and shapes in checkpoint order. */
    fun layout(): List<Triple<String, Int, Int>> = buildList {
        add(Triple("tok", vocab, dim))
        if (mixer == Mixer.ATTENTION) add(Triple("pos", context, dim))
        for (l in 0 until layers) {
            add(Triple("l$l.n1", 1, dim))
            for (w in mixerMatrices()) add(Triple("l$l.$w", dim, dim))
            if (mixer == Mixer.MINGRU) add(Triple("l$l.bz", 1, dim))
            if ((mixer == Mixer.DELTA || mixer == Mixer.LINEAR) && decayGate) { add(Triple("l$l.wa", dim, heads)); add(Triple("l$l.ba", 1, heads)) }
            if ((mixer == Mixer.DELTA || mixer == Mixer.LINEAR) && writeGate) { add(Triple("l$l.wb", dim, heads)); add(Triple("l$l.bb", 1, heads)) }
            add(Triple("l$l.n2", 1, dim))
            add(Triple("l$l.w1", dim, mlp)); add(Triple("l$l.b1", 1, mlp))
            add(Triple("l$l.w2", mlp, dim)); add(Triple("l$l.b2", 1, dim))
        }
        add(Triple("nf", 1, dim))
        if (adapterRank > 0) for (l in 0 until layers) for (w in mixerMatrices() + listOf("w1", "w2")) {
            val (r, c) = if (w == "w1") dim to mlp else if (w == "w2") mlp to dim else dim to dim
            add(Triple("l$l.$w.A", r, adapterRank)); add(Triple("l$l.$w.B", adapterRank, c))
        }
    }

    fun adapterNames(): Set<String> = layout().map { it.first }.filter { it.endsWith(".A") || it.endsWith(".B") }.toSet()
}

class NfParams(val config: NfConfig, val mats: LinkedHashMap<String, Mat>) {
    init {
        val layout = config.layout()
        require(layout.size == mats.size && layout.all { (n, r, c) -> mats[n]?.let { it.rows == r && it.cols == c } == true }) {
            "parameters do not match the configuration"
        }
    }
    val count: Int get() = mats.values.sumOf { it.data.size }
    fun countWithout(names: Set<String>): Int = mats.filterKeys { it !in names }.values.sumOf { it.data.size }
    fun allFinite(): Boolean = mats.values.all { m -> m.data.all { it.isFinite() } }
    fun copy(): NfParams = NfParams(config, LinkedHashMap(mats.mapValues { it.value.copy() }))

    /** SHA-256 over every parameter except [except] (proves that a base checkpoint stayed bit-identical). */
    fun sha256(except: Set<String> = emptySet()): String {
        val md = MessageDigest.getInstance("SHA-256")
        for ((n, m) in mats) if (n !in except) {
            md.update(n.toByteArray())
            val b = java.nio.ByteBuffer.allocate(m.data.size * 4)
            for (x in m.data) b.putFloat(x)
            md.update(b.array())
        }
        return LmBpeTokenizer.hex(md.digest())
    }

    companion object {
        fun init(config: NfConfig, seed: Long): NfParams {
            val rnd = Random(seed)
            val out = LinkedHashMap<String, Mat>()
            val residualScale = 1f / sqrt(2f * config.layers)
            for ((name, r, c) in config.layout()) {
                val m = Mat(r, c)
                val leaf = name.substringAfterLast('.')
                when {
                    name.endsWith(".B") -> Unit                                     // adapters start as a no-op
                    name.endsWith(".A") -> for (i in m.data.indices) m.data[i] = (rnd.nextGaussian() / sqrt(r.toDouble())).toFloat()
                    leaf in setOf("n1", "n2", "nf") -> m.data.fill(1f)
                    leaf == "ba" -> m.data.fill(3f)                                 // decay gate starts at α ≈ 0.95
                    leaf.startsWith("b") -> Unit
                    name == "tok" || name == "pos" -> for (i in m.data.indices) m.data[i] = (rnd.nextGaussian() * .02).toFloat()
                    else -> {
                        val std = 1f / sqrt(r.toFloat()) * if (leaf == "wo" || leaf == "w2") residualScale else 1f
                        for (i in m.data.indices) m.data[i] = (rnd.nextGaussian() * std).toFloat()
                    }
                }
                out[name] = m
            }
            return NfParams(config, out)
        }
    }
}

/** Recurrent memory carried between segments: per layer the minGRU vector or the delta-rule matrices. */
class NfState(val layers: Array<FloatArray?>) {
    fun copy() = NfState(Array(layers.size) { layers[it]?.copyOf() })
    companion object {
        fun empty(config: NfConfig) = NfState(Array(config.layers) {
            when (config.mixer) {
                Mixer.MINGRU -> FloatArray(config.dim)
                Mixer.DELTA, Mixer.LINEAR -> FloatArray(config.dim / config.heads * config.dim)
                Mixer.ATTENTION -> null
            }
        })
    }
}

/** Mean |activation| > 0 statistics of RELU blocks during one forward pass (NF-4 sparsity). */
class FfnStats { var active = 0L; var total = 0L }

class NfModel(val params: NfParams) {
    val config: NfConfig get() = params.config

    /**
     * Logits for [ids]. A non-null [state] makes recurrent mixers start from it and stores their final state back
     * into it (memory across segments). [penalty] collects the RELU activation sum when [l1] > 0.
     */
    fun logits(tape: Tape, nodes: Map<String, Node>, ids: IntArray, state: NfState? = null, stats: FfnStats? = null,
               penalty: MutableList<Node>? = null, l1: Float = 0f): Node {
        val c = config
        require(ids.size in 1..c.context) { "sequence of ${ids.size} tokens exceeds context ${c.context}" }
        fun p(name: String) = nodes[name] ?: error("missing parameter $name")
        fun proj(h: Node, name: String): Node {
            val base = tape.matmul(h, p(name))
            val a = nodes["$name.A"] ?: return base
            return tape.add(base, tape.matmul(tape.matmul(h, a), p("$name.B")))
        }
        val t = ids.size
        var x = tape.gather(p("tok"), ids)
        if (c.mixer == Mixer.ATTENTION) x = tape.add(x, tape.rows(p("pos"), 0, t))
        for (l in 0 until c.layers) {
            val h = tape.rmsnorm(x, p("l$l.n1"))
            val mix = when (c.mixer) {
                Mixer.ATTENTION -> {
                    val q = proj(h, "l$l.wq"); val k = proj(h, "l$l.wk"); val v = proj(h, "l$l.wv")
                    val hd = c.dim / c.heads
                    val scale = 1f / sqrt(hd.toFloat())
                    val heads = (0 until c.heads).map { i ->
                        val qi = if (c.heads == 1) q else tape.colSlice(q, i * hd, hd)
                        val ki = if (c.heads == 1) k else tape.colSlice(k, i * hd, hd)
                        val vi = if (c.heads == 1) v else tape.colSlice(v, i * hd, hd)
                        tape.matmul(tape.causalSoftmax(tape.matmulT(qi, ki), scale), vi)
                    }
                    proj(if (heads.size == 1) heads[0] else tape.colConcat(heads), "l$l.wo")
                }
                Mixer.MINGRU -> {
                    val z = tape.sigmoid(tape.addRow(proj(h, "l$l.wz"), p("l$l.bz")))
                    val carried = state?.layers?.get(l)
                    val s = tape.minGruScanFrom(z, proj(h, "l$l.wc"), carried?.copyOf(), carried)
                    proj(tape.mul(s, tape.silu(proj(h, "l$l.wg"))), "l$l.wo")
                }
                Mixer.DELTA, Mixer.LINEAR -> {
                    val q = tape.l2NormHeads(proj(h, "l$l.wq"), c.heads)
                    val k = tape.l2NormHeads(proj(h, "l$l.wk"), c.heads)
                    val v = proj(h, "l$l.wv")
                    val alpha = if (c.decayGate) tape.sigmoid(tape.addRow(tape.matmul(h, p("l$l.wa")), p("l$l.ba")))
                        else tape.constant(Mat(t, c.heads, FloatArray(t * c.heads) { 1f }))
                    val beta = if (c.writeGate) tape.sigmoid(tape.addRow(tape.matmul(h, p("l$l.wb")), p("l$l.bb")))
                        else tape.constant(Mat(t, c.heads, FloatArray(t * c.heads) { .5f }))
                    val carried = state?.layers?.get(l)
                    val o = tape.deltaScan(q, k, v, alpha, beta, c.heads, c.mixer == Mixer.DELTA, carried?.copyOf(), carried)
                    proj(tape.mul(o, tape.silu(proj(h, "l$l.wg"))), "l$l.wo")
                }
            }
            x = tape.add(x, mix)
            val h2 = tape.rmsnorm(x, p("l$l.n2"))
            val pre = tape.addRow(proj(h2, "l$l.w1"), p("l$l.b1"))
            val act = when (c.ffn) {
                Ffn.GELU -> tape.gelu(pre)
                Ffn.RELU -> tape.relu(pre).also { a ->
                    if (stats != null) { stats.total += a.value.data.size; stats.active += a.value.data.count { it > 0f } }
                    if (penalty != null && l1 > 0f) penalty += tape.sumScaled(a, l1)
                }
            }
            x = tape.add(x, tape.addRow(proj(act, "l$l.w2"), p("l$l.b2")))
        }
        return tape.matmulT(tape.rmsnorm(x, p("nf")), p("tok"))
    }

    fun frozenNodes(tape: Tape): Map<String, Node> = params.mats.mapValues { tape.constant(it.value) }

    /** Natural-log NLL of every target (−1 → NaN), optionally continuing and updating a carried [state]. */
    fun nll(ids: IntArray, targets: IntArray, state: NfState? = null, stats: FfnStats? = null): DoubleArray {
        require(ids.size == targets.size)
        val tape = Tape(training = false)
        val logits = logits(tape, frozenNodes(tape), ids, state, stats).value
        val v = logits.cols
        return DoubleArray(ids.size) { r ->
            val y = targets[r]
            if (y < 0) Double.NaN else logSumExp(logits.data, r * v, v) - logits.data[r * v + y]
        }
    }

    companion object {
        fun logSumExp(src: FloatArray, offset: Int, n: Int): Double {
            var max = Float.NEGATIVE_INFINITY
            for (j in 0 until n) max = maxOf(max, src[offset + j])
            var sum = 0.0
            for (j in 0 until n) sum += kotlin.math.exp((src[offset + j] - max).toDouble())
            return max + ln(sum)
        }
    }
}

/** AdamW over a chosen subset of parameters (decay only on weight matrices and adapters). */
class NfAdamW(private val mats: Map<String, Mat>, private val weightDecay: Float, private val beta1: Float = .9f,
              private val beta2: Float = .98f, private val eps: Float = 1e-8f) {
    private val m = mats.mapValues { FloatArray(it.value.data.size) }
    private val v = mats.mapValues { FloatArray(it.value.data.size) }
    private val decays = mats.keys.associateWith { n ->
        val leaf = n.substringAfterLast('.')
        n != "tok" && n != "pos" && (leaf.startsWith("w") || leaf == "A" || leaf == "B")
    }
    var steps = 0
        private set

    fun step(grads: Map<String, Mat>, lr: Float, clip: Float): Double {
        var sq = 0.0
        for (n in mats.keys) for (x in grads.getValue(n).data) sq += x.toDouble() * x
        val norm = sqrt(sq)
        val scale = if (norm > clip && norm > 0) (clip / norm).toFloat() else 1f
        steps++
        val b1 = 1.0 - Math.pow(beta1.toDouble(), steps.toDouble())
        val b2 = 1.0 - Math.pow(beta2.toDouble(), steps.toDouble())
        val stepSize = (lr * sqrt(b2) / b1).toFloat()
        for ((name, p) in mats) {
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
 * Data-parallel trainer like LmTrainer for any NF network given as parameter [mats] and a [forward] that returns the
 * logits of one example (it may add activation penalties). Only [trainable] parameters receive gradients and updates;
 * all others are tape constants (frozen base). Deterministic for a fixed thread count and batch order.
 */
class NfTrainer(val mats: LinkedHashMap<String, Mat>, val learningRate: Float, val weightDecay: Float, val warmup: Int,
                val totalSteps: Int, val trainable: Set<String> = mats.keys, val clip: Float = 1f, val l1: Float = 0f,
                threads: Int = 3,
                private val forward: (Tape, Map<String, Node>, IntArray, NfState?, MutableList<Node>, Float) -> Node) : AutoCloseable {
    private val workers = threads
    private val pool: ExecutorService = Executors.newFixedThreadPool(threads) { r -> Thread(r, "nf-train").apply { isDaemon = true } }
    private val trained = mats.filterKeys { it in trainable }
    private val optimizer = NfAdamW(trained, weightDecay)
    private val buffers = List(threads) { trained.mapValues { Mat(it.value.rows, it.value.cols) } }
    val steps: Int get() = optimizer.steps

    fun rate(step: Int): Float {
        if (step < warmup) return learningRate * (step + 1) / warmup
        val progress = ((step - warmup).toDouble() / (totalSteps - warmup).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return (learningRate * (.1 + .9 * .5 * (1 + cos(PI * progress)))).toFloat()
    }

    /**
     * One step on (ids, targets) pairs; targets −1 are ignored. Returns the mean NLL (nats per counted target) before the
     * update. [states] (one per sequence, may be null) are carried memories that are read and advanced.
     */
    fun step(batch: List<Pair<IntArray, IntArray>>, states: List<NfState?>? = null, lr: Float? = null): Double {
        require(batch.isNotEmpty())
        val totalTargets = batch.sumOf { (_, t) -> t.count { it >= 0 } }
        require(totalTargets > 0)
        for (b in buffers) for (m in b.values) m.data.fill(0f)
        val parts = (0 until workers).map { w -> batch.indices.filter { it % workers == w } }
        val losses = pool.invokeAll(parts.mapIndexed { w, indices ->
            Callable {
                var sum = 0.0
                for (i in indices) {
                    val (ids, targets) = batch[i]
                    if (targets.none { it >= 0 }) continue
                    val tape = Tape()
                    val nodes = mats.mapValues { (n, m) -> buffers[w][n]?.let { g -> tape.param(m, g) } ?: tape.constant(m) }
                    val penalty = ArrayList<Node>()
                    val logits = forward(tape, nodes, ids, states?.get(i), penalty, l1 / totalTargets)
                    var loss = tape.crossEntropy(logits, targets, totalTargets)
                    sum += loss.value.data[0].toDouble() * totalTargets
                    for (pn in penalty) loss = tape.add(loss, pn)
                    tape.backward(loss)
                }
                sum
            }
        }).sumOf { it.get() }
        val total = buffers[0]
        for (w in 1 until workers) for ((n, m) in buffers[w]) { val t = total.getValue(n).data; for (i in t.indices) t[i] += m.data[i] }
        optimizer.step(total, lr ?: rate(optimizer.steps), clip)
        check(mats.values.all { m -> m.data.all { it.isFinite() } }) { "non-finite parameter after step ${optimizer.steps}" }
        return losses / totalTargets
    }

    override fun close() { pool.shutdownNow() }
}

/** Trainer of an [NfModel] (the common case). */
fun NfTrainer(model: NfModel, learningRate: Float, weightDecay: Float, warmup: Int, totalSteps: Int,
              trainable: Set<String> = model.params.mats.keys, clip: Float = 1f, l1: Float = 0f, threads: Int = 3) =
    NfTrainer(model.params.mats, learningRate, weightDecay, warmup, totalSteps, trainable, clip, l1, threads) { tape, nodes, ids, state, penalty, l1n ->
        model.logits(tape, nodes, ids, state, null, penalty, l1n)
    }

/** Binary NF checkpoint (magic "NF1C"): configuration, tokenizer hash, metadata, parameters, SHA-256 trailer. */
object NfCheckpoint {
    private const val MAGIC = 0x4E463143
    private const val VERSION = 1

    fun encode(params: NfParams, tokenizerSha: String, meta: Map<String, String>): ByteArray {
        val c = params.config
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION)
            out.writeInt(c.vocab); out.writeInt(c.dim); out.writeInt(c.layers); out.writeInt(c.heads); out.writeInt(c.context)
            out.writeInt(c.mlp); out.writeInt(c.mixer.ordinal); out.writeBoolean(c.decayGate); out.writeBoolean(c.writeGate)
            out.writeInt(c.ffn.ordinal); out.writeInt(c.adapterRank)
            out.writeUTF(tokenizerSha)
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

    data class Restored(val params: NfParams, val tokenizerSha: String, val meta: Map<String, String>)

    fun decode(bytes: ByteArray): Restored {
        require(bytes.size > 64)
        val body = bytes.copyOfRange(0, bytes.size - 32)
        require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(body), bytes.copyOfRange(body.size, bytes.size))) {
            "checkpoint checksum mismatch"
        }
        return DataInputStream(body.inputStream()).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == VERSION) { "not an NF checkpoint" }
            val config = NfConfig(input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(),
                Mixer.values()[input.readInt()], input.readBoolean(), input.readBoolean(), Ffn.values()[input.readInt()], input.readInt())
            val tokenizerSha = input.readUTF()
            val meta = LinkedHashMap<String, String>()
            repeat(input.readInt().also { require(it in 0..64) }) { meta[input.readUTF()] = input.readUTF() }
            val mats = LinkedHashMap<String, Mat>()
            for ((name, r, c) in config.layout()) {
                require(input.readUTF() == name && input.readInt() == r && input.readInt() == c) { "unexpected parameter layout at $name" }
                mats[name] = Mat(r, c, FloatArray(r * c) { input.readFloat() })
            }
            require(input.available() == 0)
            Restored(NfParams(config, mats).also { require(it.allFinite()) { "non-finite parameter" } }, tokenizerSha, meta)
        }
    }
}
