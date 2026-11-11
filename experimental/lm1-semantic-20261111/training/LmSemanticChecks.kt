package at.heinz.deviceai.learning.lm

import kotlin.math.abs

object LmSemanticChecks {
    private fun objective(values: FloatArray, gradient: FloatArray?): Float {
        val tape = Tape()
        val logits = tape.param(Mat(4, 3, values), gradient?.let { Mat(4, 3, it) })
        val label = tape.crossEntropy(logits, intArrayOf(-1, 1, -1, -1), 1)
        val eos = tape.crossEntropy(logits, intArrayOf(-1, -1, 2, -1), 4)
        val selected = tape.rows(logits, 3, 1)
        val kl = tape.softTargetKl(selected, Mat(1, 3, floatArrayOf(.2f, .5f, .3f)), 1)
        val loss = tape.add(tape.add(label, eos), kl)
        if (gradient != null) tape.backward(loss)
        return loss.value.data.single()
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty())
        val values = FloatArray(12) { (it - 6) * .13f }
        val grad = FloatArray(12)
        objective(values, grad)
        var worst = 0f
        for (i in values.indices) {
            val plus = values.copyOf(); plus[i] += .001f
            val minus = values.copyOf(); minus[i] -= .001f
            val numerical = (objective(plus, null) - objective(minus, null)) / .002f
            val error = abs(numerical - grad[i]); worst = maxOf(worst, error)
            check(error <= .0003f + .003f * abs(numerical)) { "gradient mismatch at $i" }
        }
        check(grad.take(3).all { it == 0f })
        for (r in 0 until 4) check(abs(grad.slice(r * 3 until (r + 1) * 3).sum()) < .00001f)
        val expectedEosScale = grad[6] / grad[3]
        check(abs(expectedEosScale - .25f) < .00001f)
        println("CHECKS\tfiniteDifferences=12\tmaskedPrefix=PASS\trowGradientSums=PASS\teosWeight=PASS\tworstError=$worst\tmodelUpdates=0")
    }
}
