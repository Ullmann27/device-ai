package at.heinz.deviceai.learning.lm

import kotlin.math.ln

fun Tape.softTargetKl(logits: Node, teacher: Mat, normalizer: Int): Node {
    val rows = logits.value.rows
    val cols = logits.value.cols
    require(teacher.rows == rows && teacher.cols == cols && normalizer > 0)
    require(logits.value.data.all { it.isFinite() })
    require(teacher.data.all { it.isFinite() && it >= 0f })
    val probabilities = FloatArray(rows * cols)
    val masses = DoubleArray(rows)
    var total = 0.0
    for (r in 0 until rows) {
        val offset = r * cols
        val mass = (0 until cols).sumOf { teacher.data[offset + it].toDouble() }
        require(kotlin.math.abs(mass - 1.0) <= 1e-5)
        masses[r] = mass
        val logZ = Kernels.softmaxRow(logits.value.data, offset, cols, probabilities)
        for (j in 0 until cols) {
            val q = teacher.data[offset + j].toDouble()
            if (q > 0.0) total += q * (ln(q) - logits.value.data[offset + j] + logZ)
        }
    }
    val output = Node(Mat(1, 1, floatArrayOf((total / normalizer).toFloat())), null)
    record {
        val upstream = output.grad ?: return@record
        val scale = upstream.data[0] / normalizer
        val gradient = logits.g().data
        for (r in 0 until rows) for (j in 0 until cols) {
            val i = r * cols + j
            gradient[i] += (scale * (masses[r] * probabilities[i] - teacher.data[i])).toFloat()
        }
    }
    return output
}
