package at.heinz.deviceai.learning.lm

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tanh

/** Row-major float matrix. Rows are sequence positions, columns are features. */
class Mat(val rows: Int, val cols: Int, val data: FloatArray = FloatArray(rows * cols)) {
    init { require(rows >= 0 && cols >= 0 && data.size == rows * cols) { "shape ${rows}x$cols vs ${data.size}" } }
    operator fun get(r: Int, c: Int) = data[r * cols + c]
    operator fun set(r: Int, c: Int, v: Float) { data[r * cols + c] = v }
    fun copy() = Mat(rows, cols, data.copyOf())
}

/**
 * A value recorded on a [Tape]. Parameters carry the gradient buffer of their training worker; every other node
 * allocates its gradient on first use during the backward pass.
 */
class Node internal constructor(val value: Mat, private var gradient: Mat?) {
    val grad: Mat? get() = gradient
    internal fun g(): Mat = gradient ?: Mat(value.rows, value.cols).also { gradient = it }
}

/**
 * Reverse-mode automatic differentiation for one sequence. Every operation records its exact analytic backward
 * step; [backward] replays them in reverse order. With `training = false` nothing is recorded (inference).
 * Each operation is checked against finite differences in `LmAutodiffTest`.
 */
class Tape(val training: Boolean = true) {
    private val steps = ArrayList<() -> Unit>()

    fun param(value: Mat, grad: Mat?): Node {
        require(grad == null || grad.rows == value.rows && grad.cols == value.cols)
        return Node(value, grad)
    }
    fun constant(value: Mat): Node = Node(value, null)

    /** Records the backward step of an operation; internal so that research operations can extend the tape. */
    internal fun record(step: () -> Unit) { if (training) steps += step }

    /** Seeds d(loss)/d(loss) = 1 for a 1x1 loss node and runs every recorded step backwards. */
    fun backward(loss: Node) {
        check(training) { "inference tape has no backward pass" }
        require(loss.value.rows == 1 && loss.value.cols == 1)
        loss.g().data[0] = 1f
        for (i in steps.indices.reversed()) steps[i]()
        steps.clear()
    }

    /** Rows of [table] selected by [ids] (token embedding). */
    fun gather(table: Node, ids: IntArray): Node {
        val d = table.value.cols
        val out = Mat(ids.size, d)
        for ((t, id) in ids.withIndex()) {
            require(id in 0 until table.value.rows) { "token $id outside vocabulary" }
            System.arraycopy(table.value.data, id * d, out.data, t * d, d)
        }
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val tg = table.g().data
            for ((t, id) in ids.withIndex()) for (j in 0 until d) tg[id * d + j] += g.data[t * d + j]
        }
        return node
    }

    /** Rows [start, start + count) of [table] (learned positions). */
    fun rows(table: Node, start: Int, count: Int): Node {
        require(start >= 0 && count >= 0 && start + count <= table.value.rows)
        val d = table.value.cols
        val out = Mat(count, d, table.value.data.copyOfRange(start * d, (start + count) * d))
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val tg = table.g().data
            for (i in 0 until count * d) tg[start * d + i] += g.data[i]
        }
        return node
    }

    fun add(a: Node, b: Node): Node {
        require(a.value.rows == b.value.rows && a.value.cols == b.value.cols)
        val out = Mat(a.value.rows, a.value.cols)
        for (i in out.data.indices) out.data[i] = a.value.data[i] + b.value.data[i]
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val ag = a.g().data; val bg = b.g().data
            for (i in g.data.indices) { ag[i] += g.data[i]; bg[i] += g.data[i] }
        }
        return node
    }

    /** Adds the 1 x cols row [bias] to every row of [a]. */
    fun addRow(a: Node, bias: Node): Node {
        require(bias.value.rows == 1 && bias.value.cols == a.value.cols)
        val c = a.value.cols
        val out = Mat(a.value.rows, c)
        for (r in 0 until a.value.rows) for (j in 0 until c) out.data[r * c + j] = a.value.data[r * c + j] + bias.value.data[j]
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val ag = a.g().data; val bg = bias.g().data
            for (r in 0 until a.value.rows) for (j in 0 until c) { ag[r * c + j] += g.data[r * c + j]; bg[j] += g.data[r * c + j] }
        }
        return node
    }

    fun mul(a: Node, b: Node): Node {
        require(a.value.rows == b.value.rows && a.value.cols == b.value.cols)
        val out = Mat(a.value.rows, a.value.cols)
        for (i in out.data.indices) out.data[i] = a.value.data[i] * b.value.data[i]
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val ag = a.g().data; val bg = b.g().data
            for (i in g.data.indices) { ag[i] += g.data[i] * b.value.data[i]; bg[i] += g.data[i] * a.value.data[i] }
        }
        return node
    }

    /** a (n x k) times b (k x m). */
    fun matmul(a: Node, b: Node): Node {
        val node = Node(Kernels.mm(a.value, b.value), null)
        record {
            val g = node.grad ?: return@record
            Kernels.addMmBt(g, b.value, a.g())   // dA += G · Bᵀ
            Kernels.addAtMm(a.value, g, b.g())   // dB += Aᵀ · G
        }
        return node
    }

    /** a (n x k) times bᵀ for b (m x k): attention scores and tied output logits. */
    fun matmulT(a: Node, b: Node): Node {
        val node = Node(Kernels.mmBt(a.value, b.value), null)
        record {
            val g = node.grad ?: return@record
            Kernels.addMm(g, b.value, a.g())     // dA += G · B
            Kernels.addAtMm(g, a.value, b.g())   // dB += Gᵀ · A
        }
        return node
    }

    /** Root-mean-square normalisation of every row with a learned 1 x cols [gain]. */
    fun rmsnorm(x: Node, gain: Node, eps: Float = 1e-5f): Node {
        val n = x.value.cols
        require(gain.value.rows == 1 && gain.value.cols == n)
        val out = Mat(x.value.rows, n)
        val inv = FloatArray(x.value.rows)
        for (r in 0 until x.value.rows) {
            var ss = 0.0
            for (j in 0 until n) { val v = x.value.data[r * n + j]; ss += v * v }
            val s = (1.0 / sqrt(ss / n + eps)).toFloat()
            inv[r] = s
            for (j in 0 until n) out.data[r * n + j] = x.value.data[r * n + j] * s * gain.value.data[j]
        }
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val xg = x.g().data; val gg = gain.g().data
            for (r in 0 until x.value.rows) {
                val s = inv[r]
                var dot = 0.0
                for (j in 0 until n) dot += g.data[r * n + j] * gain.value.data[j] * x.value.data[r * n + j]
                val k = (s * s * s * dot / n).toFloat()
                for (j in 0 until n) {
                    val xv = x.value.data[r * n + j]
                    xg[r * n + j] += s * gain.value.data[j] * g.data[r * n + j] - k * xv
                    gg[j] += g.data[r * n + j] * xv * s
                }
            }
        }
        return node
    }

    fun gelu(x: Node): Node = unary(x, { v ->
        val u = GELU_C * (v + 0.044715f * v * v * v)
        0.5f * v * (1f + tanh(u))
    }) { v ->
        val u = GELU_C * (v + 0.044715f * v * v * v)
        val t = tanh(u)
        0.5f * (1f + t) + 0.5f * v * (1f - t * t) * GELU_C * (1f + 3f * 0.044715f * v * v)
    }

    fun silu(x: Node): Node = unary(x, { v -> v * sigmoid(v) }) { v -> val s = sigmoid(v); s * (1f + v * (1f - s)) }

    fun sigmoid(x: Node): Node = unary(x, { v -> sigmoid(v) }) { v -> val s = sigmoid(v); s * (1f - s) }

    private inline fun unary(x: Node, crossinline f: (Float) -> Float, crossinline df: (Float) -> Float): Node {
        val out = Mat(x.value.rows, x.value.cols)
        for (i in out.data.indices) out.data[i] = f(x.value.data[i])
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val xg = x.g().data
            for (i in g.data.indices) xg[i] += g.data[i] * df(x.value.data[i])
        }
        return node
    }

    /**
     * Row-wise softmax of [scores] · [scale] over the causal prefix: row t only sees columns 0..t (the rest is 0).
     */
    fun causalSoftmax(scores: Node, scale: Float): Node {
        val t = scores.value.rows
        require(scores.value.cols == t)
        val p = Mat(t, t)
        for (r in 0 until t) {
            var max = Float.NEGATIVE_INFINITY
            for (c in 0..r) max = maxOf(max, scores.value.data[r * t + c] * scale)
            var sum = 0.0
            for (c in 0..r) { val e = exp((scores.value.data[r * t + c] * scale - max).toDouble()); p.data[r * t + c] = e.toFloat(); sum += e }
            for (c in 0..r) p.data[r * t + c] = (p.data[r * t + c] / sum).toFloat()
        }
        val node = Node(p, null)
        record {
            val g = node.grad ?: return@record
            val sg = scores.g().data
            for (r in 0 until t) {
                var dot = 0.0
                for (c in 0..r) dot += g.data[r * t + c] * p.data[r * t + c]
                for (c in 0..r) sg[r * t + c] += scale * p.data[r * t + c] * (g.data[r * t + c] - dot.toFloat())
            }
        }
        return node
    }

    fun colSlice(x: Node, start: Int, width: Int): Node {
        val c = x.value.cols
        require(start >= 0 && width > 0 && start + width <= c)
        val out = Mat(x.value.rows, width)
        for (r in 0 until x.value.rows) System.arraycopy(x.value.data, r * c + start, out.data, r * width, width)
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val xg = x.g().data
            for (r in 0 until x.value.rows) for (j in 0 until width) xg[r * c + start + j] += g.data[r * width + j]
        }
        return node
    }

    fun colConcat(parts: List<Node>): Node {
        val rows = parts.first().value.rows
        require(parts.all { it.value.rows == rows })
        val c = parts.sumOf { it.value.cols }
        val out = Mat(rows, c)
        var offset = 0
        for (p in parts) {
            val w = p.value.cols
            for (r in 0 until rows) System.arraycopy(p.value.data, r * w, out.data, r * c + offset, w)
            offset += w
        }
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            var o = 0
            for (p in parts) {
                val w = p.value.cols
                val pg = p.g().data
                for (r in 0 until rows) for (j in 0 until w) pg[r * w + j] += g.data[r * c + o + j]
                o += w
            }
        }
        return node
    }

    /**
     * minGRU scan (Feng et al. 2024, "Were RNNs all we needed?"): h_t = (1 − z_t) ⊙ h_{t−1} + z_t ⊙ c_t, h_{−1} = 0.
     * Constant memory per step at inference; exact backpropagation through time here.
     */
    fun minGruScan(z: Node, candidate: Node): Node {
        val t = z.value.rows; val d = z.value.cols
        require(candidate.value.rows == t && candidate.value.cols == d)
        val h = Mat(t, d)
        for (r in 0 until t) for (j in 0 until d) {
            val prev = if (r == 0) 0f else h.data[(r - 1) * d + j]
            val zz = z.value.data[r * d + j]
            h.data[r * d + j] = (1f - zz) * prev + zz * candidate.value.data[r * d + j]
        }
        val node = Node(h, null)
        record {
            val g = node.grad ?: return@record
            val zg = z.g().data; val cg = candidate.g().data
            val carry = FloatArray(d)
            for (r in t - 1 downTo 0) for (j in 0 until d) {
                val i = r * d + j
                val dh = g.data[i] + carry[j]
                val prev = if (r == 0) 0f else h.data[i - d]
                val zz = z.value.data[i]
                zg[i] += dh * (candidate.value.data[i] - prev)
                cg[i] += dh * zz
                carry[j] = dh * (1f - zz)
            }
        }
        return node
    }

    /**
     * Cross-entropy of [logits] (T x V) against [targets], summed and divided by [normalizer] (default: the number of
     * counted targets, i.e. the mean). A target of −1 is ignored. Returns a 1x1 node. A batch passes its total target
     * count as normalizer so that per-sequence gradients add up to the batch mean.
     */
    fun crossEntropy(logits: Node, targets: IntArray, normalizer: Int = -1): Node {
        val t = logits.value.rows; val v = logits.value.cols
        require(targets.size == t)
        val probs = Mat(t, v)
        var total = 0.0; var count = 0
        for (r in 0 until t) {
            val y = targets[r]
            if (y < 0) continue
            require(y < v)
            total += Kernels.softmaxRow(logits.value.data, r * v, v, probs.data) - logits.value.data[r * v + y]
            count++
        }
        require(count > 0) { "no target" }
        val norm = if (normalizer > 0) normalizer else count
        val out = Mat(1, 1); out.data[0] = (total / norm).toFloat()
        val node = Node(out, null)
        record {
            val g = node.grad ?: return@record
            val scale = g.data[0] / norm
            val lg = logits.g().data
            for (r in 0 until t) {
                val y = targets[r]
                if (y < 0) continue
                for (j in 0 until v) lg[r * v + j] += scale * (probs.data[r * v + j] - if (j == y) 1f else 0f)
            }
        }
        return node
    }

    companion object {
        private val GELU_C = sqrt(2.0 / Math.PI).toFloat()
        fun sigmoid(v: Float): Float = if (v >= 0) 1f / (1f + exp(-v)) else exp(v).let { it / (1f + it) }
    }
}

/** Dense kernels; loops are ordered so that the innermost loop runs over contiguous memory. */
internal object Kernels {
    fun mm(a: Mat, b: Mat): Mat {
        require(a.cols == b.rows) { "mm ${a.rows}x${a.cols} · ${b.rows}x${b.cols}" }
        val out = Mat(a.rows, b.cols)
        addMm(a, b, out)
        return out
    }
    /** out += a · b */
    fun addMm(a: Mat, b: Mat, out: Mat) {
        require(a.cols == b.rows && out.rows == a.rows && out.cols == b.cols)
        val n = a.rows; val k = a.cols; val m = b.cols
        val ad = a.data; val bd = b.data; val od = out.data
        for (i in 0 until n) {
            val oRow = i * m
            for (p in 0 until k) {
                val av = ad[i * k + p]
                if (av == 0f) continue
                val bRow = p * m
                for (j in 0 until m) od[oRow + j] += av * bd[bRow + j]
            }
        }
    }
    /** a · bᵀ for b (m x k). */
    fun mmBt(a: Mat, b: Mat): Mat {
        require(a.cols == b.cols)
        val n = a.rows; val k = a.cols; val m = b.rows
        val out = Mat(n, m)
        val ad = a.data; val bd = b.data; val od = out.data
        for (i in 0 until n) for (j in 0 until m) {
            var s = 0f
            val ar = i * k; val br = j * k
            for (p in 0 until k) s += ad[ar + p] * bd[br + p]
            od[i * m + j] = s
        }
        return out
    }
    /** out += g · bᵀ, g (n x m), b (k x m), out (n x k). */
    fun addMmBt(g: Mat, b: Mat, out: Mat) {
        require(g.cols == b.cols && out.rows == g.rows && out.cols == b.rows)
        val n = g.rows; val m = g.cols; val k = b.rows
        val gd = g.data; val bd = b.data; val od = out.data
        for (i in 0 until n) for (p in 0 until k) {
            var s = 0f
            val gr = i * m; val br = p * m
            for (j in 0 until m) s += gd[gr + j] * bd[br + j]
            od[i * k + p] += s
        }
    }
    /** out += aᵀ · g, a (n x k), g (n x m), out (k x m). */
    fun addAtMm(a: Mat, g: Mat, out: Mat) {
        require(a.rows == g.rows && out.rows == a.cols && out.cols == g.cols)
        val n = a.rows; val k = a.cols; val m = g.cols
        val ad = a.data; val gd = g.data; val od = out.data
        for (i in 0 until n) for (p in 0 until k) {
            val av = ad[i * k + p]
            if (av == 0f) continue
            val oRow = p * m; val gRow = i * m
            for (j in 0 until m) od[oRow + j] += av * gd[gRow + j]
        }
    }

    /** Writes softmax(row) into [out] at the same offset and returns log Σ exp(row) (so NLL = result − logit). */
    fun softmaxRow(src: FloatArray, offset: Int, n: Int, out: FloatArray): Double {
        var max = Float.NEGATIVE_INFINITY
        for (j in 0 until n) max = maxOf(max, src[offset + j])
        var sum = 0.0
        for (j in 0 until n) { val e = exp((src[offset + j] - max).toDouble()); out[offset + j] = e.toFloat(); sum += e }
        for (j in 0 until n) out[offset + j] = (out[offset + j] / sum).toFloat()
        return max + ln(sum)
    }
}
