package at.heinz.deviceai.learning.lm

import kotlin.math.abs
import kotlin.math.max

object LmDistillChecks {
    private fun calculate(values: Mat, q: Mat, divisor: Int): Pair<Float, FloatArray> {
        val tape = Tape()
        val grad = Mat(values.rows, values.cols)
        val loss = tape.softTargetKl(tape.param(values, grad), q, divisor)
        tape.backward(loss)
        return loss.value.data[0] to grad.data
    }
    private fun close(a: Float, b: Float, absTol: Float = 2e-6f) =
        abs(a - b) <= absTol
    private fun checkNamed(name: String, condition: Boolean) {
        check(condition) { name }
        println("CHECK\tname=$name\tpassed=true")
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty())
        val logits = Mat(3, 4, floatArrayOf(-.7f, .2f, 1.1f, -.3f, .9f, -.5f, .3f, 1.3f, -.2f, .8f, -.6f, .5f))
        val teacher = Mat(3, 4, floatArrayOf(.1f, .2f, .3f, .4f, .4f, .1f, .2f, .3f, .25f, .25f, .125f, .375f))
        val baseline = calculate(logits, teacher, 3)
        val epsilon = 1e-3f
        var maximumError = 0f
        for (i in logits.data.indices) {
            val a = logits.copy(); val b = logits.copy()
            a.data[i] += epsilon; b.data[i] -= epsilon
            val numerical = (calculate(a, teacher, 3).first - calculate(b, teacher, 3).first) / (2 * epsilon)
            maximumError = max(maximumError, abs(numerical - baseline.second[i]))
            check(abs(numerical - baseline.second[i]) <= 2e-4f + 3e-3f * abs(numerical))
        }
        println("CHECK\tname=finite_difference\tpassed=true\tmaxAbsoluteError=$maximumError\tabsTolerance=2.0E-4\trelTolerance=0.003\tepsilon=$epsilon")
        val p = Mat(3, 4)
        for (r in 0 until 3) Kernels.softmaxRow(logits.data, r * 4, 4, p.data)
        val identical = calculate(logits, p, 3)
        checkNamed("identical_zero_kl", abs(identical.first) < 2e-6f && identical.second.all { abs(it) < 2e-6f })
        val shifted = logits.copy().also { for (i in it.data.indices) it.data[i] += 7f }
        val shiftResult = calculate(shifted, teacher, 3)
        checkNamed("logit_shift_invariance", close(shiftResult.first, baseline.first) && shiftResult.second.zip(baseline.second).all { close(it.first, it.second) })
        val labels = intArrayOf(2, 0, 3)
        val oneHot = Mat(3, 4).also { for (r in 0 until 3) it[r, labels[r]] = 1f }
        val soft = calculate(logits, oneHot, 3)
        val tape = Tape(); val hardGrad = Mat(3, 4)
        val hard = tape.crossEntropy(tape.param(logits, hardGrad), labels, 3)
        tape.backward(hard)
        checkNamed("one_hot_equivalence", close(soft.first, hard.value.data[0]) && soft.second.zip(hardGrad.data).all { close(it.first, it.second) })
        val mixedTape = Tape(); val mixedGrad = Mat(3, 4)
        val shared = mixedTape.param(logits, mixedGrad)
        val mixed = mixedTape.add(mixedTape.softTargetKl(shared, teacher, 3), mixedTape.crossEntropy(shared, labels, 3))
        mixedTape.backward(mixed)
        checkNamed("mixed_objective_adds_gradients", baseline.second.indices.all { close(mixedGrad.data[it], baseline.second[it] + hardGrad.data[it]) })
        val accumulatedTape = Tape(); val accumulatedGrad = Mat(3, 4, FloatArray(12) { .125f })
        val accumulated = accumulatedTape.softTargetKl(accumulatedTape.param(logits, accumulatedGrad), teacher, 3)
        accumulatedTape.backward(accumulated)
        checkNamed("external_gradient_accumulates", baseline.second.indices.all { close(accumulatedGrad.data[it], baseline.second[it] + .125f) })
        val doubledTape = Tape(); val doubledGrad = Mat(3, 4)
        val once = doubledTape.softTargetKl(doubledTape.param(logits, doubledGrad), teacher, 3)
        doubledTape.backward(doubledTape.add(once, once))
        checkNamed("upstream_scale_applied", baseline.second.indices.all { close(doubledGrad.data[it], baseline.second[it] * 2) })
        val duplicate = calculate(Mat(6, 4, logits.data + logits.data), Mat(6, 4, teacher.data + teacher.data), 6)
        checkNamed("duplicate_mean_invariance", close(duplicate.first, baseline.first) && baseline.second.indices.all { close(duplicate.second[it] + duplicate.second[it + 12], baseline.second[it]) })
        checkNamed("normalizer_scaling", calculate(logits, teacher, 6).let { close(it.first * 2, baseline.first) && it.second.zip(baseline.second).all { pair -> close(pair.first * 2, pair.second) } })
        checkNamed("teacher_not_mutated", teacher.data.contentEquals(floatArrayOf(.1f, .2f, .3f, .4f, .4f, .1f, .2f, .3f, .25f, .25f, .125f, .375f)))
        val invalid = teacher.copy().also { it.data[0] = -1f }
        checkNamed("negative_teacher_rejected", runCatching { calculate(logits, invalid, 3) }.exceptionOrNull() is IllegalArgumentException)
        val badMass = teacher.copy().also { it.data[0] = 1f }
        checkNamed("teacher_mass_rejected", runCatching { calculate(logits, badMass, 3) }.exceptionOrNull() is IllegalArgumentException)
        checkNamed("zero_normalizer_rejected", runCatching { calculate(logits, teacher, 0) }.exceptionOrNull() is IllegalArgumentException)
        val badLogits = logits.copy().also { it.data[0] = Float.NaN }
        checkNamed("nonfinite_logits_rejected", runCatching { calculate(badLogits, teacher, 3) }.exceptionOrNull() is IllegalArgumentException)
        val extreme = calculate(Mat(1, 4, floatArrayOf(1000f, -1000f, 999f, -999f)), Mat(1, 4, floatArrayOf(.25f, .25f, .25f, .25f)), 1)
        checkNamed("extreme_logits_finite", extreme.first.isFinite() && extreme.second.all { it.isFinite() })
        println("RESULT\tchecks=15\tmodelUpdates=0\tpassed=true")
    }
}
