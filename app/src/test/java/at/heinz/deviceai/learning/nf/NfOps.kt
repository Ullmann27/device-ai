package at.heinz.deviceai.learning.nf

import at.heinz.deviceai.learning.lm.Mat
import at.heinz.deviceai.learning.lm.Node
import at.heinz.deviceai.learning.lm.Tape
import kotlin.math.sqrt

/*
 * Research operations of the Continuum Neural Frontier Lab (NF). They extend the LM1 tape with exact analytic backward
 * steps; every one is checked against finite differences in NfCoreTest. Test sources only: nothing here ships in the app.
 */

/** Unit length per head: every dh-wide slice of every row of [x] (T x H·dh) is divided by its L2 norm. */
fun Tape.l2NormHeads(x: Node, heads: Int, eps: Float = 1e-6f): Node {
    val t = x.value.rows; val d = x.value.cols
    require(heads >= 1 && d % heads == 0)
    val hd = d / heads
    val out = Mat(t, d)
    val inv = FloatArray(t * heads)
    for (r in 0 until t) for (h in 0 until heads) {
        val o = r * d + h * hd
        var s = 0.0
        for (j in 0 until hd) { val v = x.value.data[o + j]; s += v * v }
        val n = (1.0 / sqrt(s + eps)).toFloat()
        inv[r * heads + h] = n
        for (j in 0 until hd) out.data[o + j] = x.value.data[o + j] * n
    }
    val node = Node(out, null)
    record {
        val g = node.grad ?: return@record
        val xg = x.g().data
        for (r in 0 until t) for (h in 0 until heads) {
            val o = r * d + h * hd
            val n = inv[r * heads + h]
            var dot = 0.0
            for (j in 0 until hd) dot += g.data[o + j] * out.data[o + j]
            for (j in 0 until hd) xg[o + j] += n * (g.data[o + j] - out.data[o + j] * dot.toFloat())
        }
    }
    return node
}

/**
 * Gated delta-rule fast weights (the recurrence of Gated DeltaNet, Yang et al. 2024/2025), one dh x dh state per head:
 *   u_t = S_{t-1}ᵀ k_t,   S_t = α_t S_{t-1} + β_t k_t (v_t − α_t u_t)ᵀ,   o_t = S_tᵀ q_t
 * which equals α_t (I − β_t k_t k_tᵀ) S_{t-1} + β_t k_t v_tᵀ: the old value stored under k_t is partly erased before
 * the new one is written. With [delta] = false the erase term is dropped (gated linear attention, S_t = α S + β k vᵀ).
 * q, k, v are T x (H·dh); alpha and beta are T x H. [initial] (H·dh·dh, not differentiated) carries memory from an
 * earlier segment; [finalState] receives S_{T-1}. Exact backpropagation through time.
 */
fun Tape.deltaScan(q: Node, k: Node, v: Node, alpha: Node, beta: Node, heads: Int, delta: Boolean = true,
                   initial: FloatArray? = null, finalState: FloatArray? = null): Node {
    val t = q.value.rows; val d = q.value.cols
    require(heads >= 1 && d % heads == 0)
    require(k.value.rows == t && v.value.rows == t && k.value.cols == d && v.value.cols == d)
    require(alpha.value.rows == t && beta.value.rows == t && alpha.value.cols == heads && beta.value.cols == heads)
    val hd = d / heads; val hh = hd * hd; val stride = heads * hh
    require(initial == null || initial.size == stride)
    require(finalState == null || finalState.size == stride)
    val states = FloatArray((t + 1) * stride)          // states[0] = initial, states[r + 1] = S_r
    if (initial != null) System.arraycopy(initial, 0, states, 0, stride)
    val qd = q.value.data; val kd = k.value.data; val vd = v.value.data
    val out = Mat(t, d)
    val u = FloatArray(hd)
    for (r in 0 until t) for (h in 0 until heads) {
        val prev = r * stride + h * hh; val cur = prev + stride
        val o = r * d + h * hd
        val a = alpha.value.data[r * heads + h]; val b = beta.value.data[r * heads + h]
        if (delta) {
            u.fill(0f)
            for (i in 0 until hd) { val ki = kd[o + i]; val row = prev + i * hd; for (j in 0 until hd) u[j] += ki * states[row + j] }
        }
        for (i in 0 until hd) {
            val bk = b * kd[o + i]
            val pr = prev + i * hd; val cr = cur + i * hd
            for (j in 0 until hd) states[cr + j] = a * states[pr + j] + bk * (vd[o + j] - if (delta) a * u[j] else 0f)
        }
        for (i in 0 until hd) {
            val qi = qd[o + i]
            val cr = cur + i * hd
            for (j in 0 until hd) out.data[o + j] += states[cr + j] * qi
        }
    }
    if (finalState != null) System.arraycopy(states, t * stride, finalState, 0, stride)
    val node = Node(out, null)
    record {
        val g = node.grad ?: return@record
        val qg = q.g().data; val kg = k.g().data; val vg = v.g().data
        val ag = alpha.g().data; val bg = beta.g().data
        val carry = FloatArray(stride)                // dLoss/dS_r for the step being processed
        val gk = FloatArray(hd); val w = FloatArray(hd); val du = FloatArray(hd)
        for (r in t - 1 downTo 0) for (h in 0 until heads) {
            val prev = r * stride + h * hh; val cur = prev + stride; val gh = h * hh
            val o = r * d + h * hd
            val a = alpha.value.data[r * heads + h]; val b = beta.value.data[r * heads + h]
            for (i in 0 until hd) {                    // o = Sᵀq: dq = S dO, dS += q dOᵀ
                val qi = qd[o + i]
                var s = 0f
                for (j in 0 until hd) { val dOj = g.data[o + j]; s += states[cur + i * hd + j] * dOj; carry[gh + i * hd + j] += qi * dOj }
                qg[o + i] += s
            }
            if (delta) {
                u.fill(0f)
                for (i in 0 until hd) { val ki = kd[o + i]; for (j in 0 until hd) u[j] += ki * states[prev + i * hd + j] }
            }
            gk.fill(0f)
            for (i in 0 until hd) { val ki = kd[o + i]; for (j in 0 until hd) gk[j] += carry[gh + i * hd + j] * ki }
            for (j in 0 until hd) w[j] = vd[o + j] - if (delta) a * u[j] else 0f
            var dB = 0.0; var dA = 0.0
            for (j in 0 until hd) dB += gk[j] * w[j]
            for (i in 0 until hh) dA += carry[gh + i] * states[prev + i]
            if (delta) for (j in 0 until hd) dA -= b * gk[j] * u[j]
            for (j in 0 until hd) vg[o + j] += b * gk[j]
            for (i in 0 until hd) {
                var s = 0f
                for (j in 0 until hd) s += carry[gh + i * hd + j] * w[j]
                kg[o + i] += b * s
            }
            if (delta) {
                for (j in 0 until hd) du[j] = -a * b * gk[j]
                for (i in 0 until hd) {
                    var s = 0f
                    for (j in 0 until hd) s += states[prev + i * hd + j] * du[j]
                    kg[o + i] += s
                }
            }
            ag[r * heads + h] += dA.toFloat(); bg[r * heads + h] += dB.toFloat()
            for (i in 0 until hd) {
                val ki = kd[o + i]
                for (j in 0 until hd) {
                    val c = gh + i * hd + j
                    carry[c] = a * carry[c] + if (delta) ki * du[j] else 0f
                }
            }
        }
    }
    return node
}

/**
 * Exact inverse of one delta-rule write (NF-2 rollback): given S_t and the write's k (unit length), v, α > 0 and
 * β < 1, returns S_{t-1} = (1/α) (I + β/(1−β) k kᵀ) (S_t − β k vᵀ) for one head. Only k, v, α, β must be journaled,
 * never a copy of the state.
 */
fun undoDeltaWrite(state: FloatArray, k: FloatArray, v: FloatArray, alpha: Float, beta: Float): FloatArray {
    val hd = k.size
    require(state.size == hd * hd && v.size == hd && alpha > 0f && beta < 1f)
    val a = FloatArray(hd * hd) { state[it] - beta * k[it / hd] * v[it % hd] }
    val ktA = FloatArray(hd)
    for (i in 0 until hd) for (j in 0 until hd) ktA[j] += k[i] * a[i * hd + j]
    val c = beta / (1f - beta)
    return FloatArray(hd * hd) { (a[it] + c * k[it / hd] * ktA[it % hd]) / alpha }
}

/** One delta-rule write on a single head state (the forward step of [deltaScan], used by memories outside the tape). */
fun deltaWrite(state: FloatArray, k: FloatArray, v: FloatArray, alpha: Float, beta: Float) {
    val hd = k.size
    val u = FloatArray(hd)
    for (i in 0 until hd) for (j in 0 until hd) u[j] += k[i] * state[i * hd + j]
    for (i in 0 until hd) for (j in 0 until hd) state[i * hd + j] = alpha * state[i * hd + j] + beta * k[i] * (v[j] - alpha * u[j])
}

/** minGRU scan that starts from a carried state h_{-1} = [initial] (1 x d, not differentiated). */
fun Tape.minGruScanFrom(z: Node, candidate: Node, initial: FloatArray?, finalState: FloatArray? = null): Node {
    val t = z.value.rows; val d = z.value.cols
    require(candidate.value.rows == t && candidate.value.cols == d)
    require(initial == null || initial.size == d)
    val h = Mat(t, d)
    for (r in 0 until t) for (j in 0 until d) {
        val prev = if (r == 0) initial?.get(j) ?: 0f else h.data[(r - 1) * d + j]
        val zz = z.value.data[r * d + j]
        h.data[r * d + j] = (1f - zz) * prev + zz * candidate.value.data[r * d + j]
    }
    if (finalState != null) System.arraycopy(h.data, (t - 1) * d, finalState, 0, d)
    val node = Node(h, null)
    record {
        val g = node.grad ?: return@record
        val zg = z.g().data; val cg = candidate.g().data
        val carry = FloatArray(d)
        for (r in t - 1 downTo 0) for (j in 0 until d) {
            val i = r * d + j
            val dh = g.data[i] + carry[j]
            val prev = if (r == 0) initial?.get(j) ?: 0f else h.data[i - d]
            val zz = z.value.data[i]
            zg[i] += dh * (candidate.value.data[i] - prev)
            cg[i] += dh * zz
            carry[j] = dh * (1f - zz)
        }
    }
    return node
}

/** Rows of [x] at [idx] (indices may repeat or be omitted); backward scatters the gradient back. */
fun Tape.pickRows(x: Node, idx: IntArray): Node {
    val c = x.value.cols
    val out = Mat(idx.size, c)
    for ((r, i) in idx.withIndex()) {
        require(i in 0 until x.value.rows)
        System.arraycopy(x.value.data, i * c, out.data, r * c, c)
    }
    val node = Node(out, null)
    record {
        val g = node.grad ?: return@record
        val xg = x.g().data
        for ((r, i) in idx.withIndex()) for (j in 0 until c) xg[i * c + j] += g.data[r * c + j]
    }
    return node
}

/** max(0, x). Exact zeros let [at.heinz.deviceai.learning.lm.Kernels] skip whole rows of the next weight matrix. */
fun Tape.relu(x: Node): Node {
    val out = Mat(x.value.rows, x.value.cols)
    for (i in out.data.indices) out.data[i] = if (x.value.data[i] > 0f) x.value.data[i] else 0f
    val node = Node(out, null)
    record {
        val g = node.grad ?: return@record
        val xg = x.g().data
        for (i in g.data.indices) if (x.value.data[i] > 0f) xg[i] += g.data[i]
    }
    return node
}

/** Sum of all entries times [scale], as a 1x1 node (activation penalties). */
fun Tape.sumScaled(x: Node, scale: Float): Node {
    var s = 0.0
    for (v in x.value.data) s += v
    val out = Mat(1, 1, floatArrayOf((s * scale).toFloat()))
    val node = Node(out, null)
    record {
        val g = node.grad ?: return@record
        val xg = x.g().data
        val d = g.data[0] * scale
        for (i in xg.indices) xg[i] += d
    }
    return node
}

/** Rows of [a] followed by the rows of [b] (same width). */
fun Tape.rowConcat(a: Node, b: Node): Node {
    val c = a.value.cols
    require(b.value.cols == c)
    val out = Mat(a.value.rows + b.value.rows, c)
    System.arraycopy(a.value.data, 0, out.data, 0, a.value.data.size)
    System.arraycopy(b.value.data, 0, out.data, a.value.data.size, b.value.data.size)
    val node = Node(out, null)
    record {
        val g = node.grad ?: return@record
        val ag = a.g().data; val bg = b.g().data
        for (i in ag.indices) ag[i] += g.data[i]
        for (i in bg.indices) bg[i] += g.data[a.value.data.size + i]
    }
    return node
}
