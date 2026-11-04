package at.heinz.deviceai.learning.lm

import java.io.File
import kotlin.math.*

/** No optimizer update. Mask/normalization and selected finite differences on native model gradients. */
object GradientPreflight {
    private const val ABS_TOL = 3e-5
    private const val REL_TOL = .003
    private fun tolerance(reference: Double) = ABS_TOL + REL_TOL * abs(reference)

    /** Independent Float64 forward adapted from the pinned #514 research oracle; no native kernels or Tape. */
    private fun oracle(model: LmModel, ids: IntArray, targets: IntArray,
                       changed: String = "", index: Int = -1, delta: Double = 0.0): Double {
        val cfg = model.config; check(cfg.arch == LmArch.MINGRU)
        val d = cfg.dim; val n = ids.size
        check(targets.size == n && targets.any { it >= 0 })
        val parameters = model.params.mats.mapValues { (name, p) ->
            DoubleArray(p.data.size) { i -> p.data[i].toDouble() + if (name == changed && i == index) delta else 0.0 }
        }
        fun p(name: String) = parameters.getValue(name)
        fun mm(a: DoubleArray, w: DoubleArray, k: Int, cols: Int): DoubleArray =
            DoubleArray(n * cols) { ij ->
                val r = ij / cols; val c = ij % cols
                var sum = 0.0
                for (j in 0 until k) sum += a[r * k + j] * w[j * cols + c]
                sum
            }
        fun norm(a: DoubleArray, gain: DoubleArray): DoubleArray {
            val out = DoubleArray(a.size)
            for (r in 0 until n) {
                var square = 0.0
                for (j in 0 until d) square += a[r * d + j] * a[r * d + j]
                val inverse = 1.0 / sqrt(square / d + 1e-5f.toDouble())
                for (j in 0 until d) out[r * d + j] = a[r * d + j] * inverse * gain[j]
            }
            return out
        }
        val emb = p("tok")
        var x = DoubleArray(n * d) { i -> emb[ids[i / d] * d + i % d] }
        for (layer in 0 until cfg.layers) {
            val prefix = "l$layer."
            val h = norm(x, p(prefix + "n1"))
            val z = mm(h, p(prefix + "wz"), d, d)
            val candidate = mm(h, p(prefix + "wc"), d, d)
            val state = DoubleArray(n * d)
            for (t in 0 until n) for (j in 0 until d) {
                val i = t * d + j
                val gate = 1.0 / (1.0 + exp(-(z[i] + p(prefix + "bz")[j])))
                state[i] = (1.0 - gate) * (if (t == 0) 0.0 else state[i - d]) + gate * candidate[i]
            }
            val gate = mm(h, p(prefix + "wg"), d, d)
            val mixed = mm(DoubleArray(n * d) { i -> state[i] * gate[i] / (1.0 + exp(-gate[i])) }, p(prefix + "wo"), d, d)
            x = DoubleArray(n * d) { i -> x[i] + mixed[i] }
            val ff = mm(norm(x, p(prefix + "n2")), p(prefix + "w1"), d, cfg.mlp)
            val geluC = sqrt(2.0 / Math.PI).toFloat().toDouble()
            for (i in ff.indices) {
                val v = ff[i] + p(prefix + "b1")[i % cfg.mlp]
                ff[i] = .5 * v * (1.0 + tanh(geluC * (v + .044715f.toDouble() * v * v * v)))
            }
            val residual = mm(ff, p(prefix + "w2"), cfg.mlp, d)
            x = DoubleArray(n * d) { i -> x[i] + residual[i] + p(prefix + "b2")[i % d] }
        }
        x = norm(x, p("nf"))
        var loss = 0.0
        for (t in 0 until n) {
            if (targets[t] < 0) continue
            val logits = DoubleArray(cfg.vocab) { v -> (0 until d).sumOf { j -> x[t * d + j] * emb[v * d + j] } }
            val maximum = logits.max()
            loss += maximum + ln(logits.sumOf { exp(it - maximum) }) - logits[targets[t]]
        }
        return loss / targets.count { it >= 0 }
    }
    private fun closedFormMask(): Map<String, Any> {
        val logits = Mat(3, 4, floatArrayOf(-.4f, .1f, .3f, -.2f, .7f, -.3f, .5f, .2f, -.1f, .6f, -.7f, .4f))
        val targets = intArrayOf(-1, 2, 1); val buffer = Mat(3, 4)
        val tape = Tape(); val node = tape.param(logits, buffer)
        val loss = tape.crossEntropy(node, targets, 2); tape.backward(loss)
        var expectedLoss = 0.0; var maxError = 0.0; var wrongNormalizerError = 0.0
        for (r in 0 until 3) {
            val values = (0 until 4).map { logits.data[r * 4 + it].toDouble() }
            val mx = values.max(); val denominator = values.sumOf { exp(it - mx) }
            for (j in 0 until 4) {
                val expected = if (targets[r] < 0) 0.0 else (exp(values[j] - mx) / denominator - if (j == targets[r]) 1.0 else 0.0) / 2.0
                maxError = max(maxError, abs(expected - buffer.data[r * 4 + j]))
                if (targets[r] < 0) check(buffer.data[r * 4 + j] == 0f)
                wrongNormalizerError = max(wrongNormalizerError, abs(expected - expected * 2.0 / 3.0))
            }
            if (targets[r] >= 0) expectedLoss += (mx + ln(denominator) - values[targets[r]]) / 2.0
        }
        check(maxError <= 1e-7 && abs(expectedLoss - loss.value.data[0]) <= 2e-7)
        check(wrongNormalizerError > .001) { "negative normalization control is not discriminating" }
        return mapOf("passed" to true, "maxGradientError" to maxError, "lossError" to abs(expectedLoss - loss.value.data[0]),
            "inactiveRowsExactlyZero" to true, "wrongNormalizerError" to wrongNormalizerError)
    }
    private fun fullTextParity(runtime: LmRuntime, data: List<ObjectiveStudy.Row>): Map<String, Any> {
        val rows = listOf(data.first { it.template == "owner_late" && it.answer == "A" },
                          data.first { it.template == "owner_early" && it.answer == "D" })
        val samples = rows.map { ObjectiveStudy.sample(runtime, it, "full_text") }
        val actual = ObjectiveStudy.gradients(runtime.model, samples)
        val expected = runtime.model.params.mats.mapValues { DoubleArray(it.value.data.size) }
        var expectedLoss = 0.0; var nll = 0.0
        for (s in samples) {
            check(s.targets.all { it >= 0 })
            val g = runtime.model.params.zeros(); val tape = Tape()
            val nodes = runtime.model.params.mats.mapValues { (name, p) -> tape.param(p, g.getValue(name)) }
            val loss = tape.crossEntropy(runtime.model.logits(tape, nodes, s.ids), s.targets)
            tape.backward(loss)
            val weight = s.active.toDouble() / actual.active
            expectedLoss += loss.value.data[0] * weight
            nll += runtime.model.nll(s.ids, s.targets).sum() / actual.active
            for ((name, gradient) in g) for (i in gradient.data.indices) expected.getValue(name)[i] += gradient.data[i] * weight
        }
        var maxError = 0.0
        for ((name, gradient) in actual.values) for (i in gradient.data.indices)
            maxError = max(maxError, abs(gradient.data[i] - expected.getValue(name)[i]))
        check(maxError <= 2e-5 && abs(actual.loss - expectedLoss) <= 2e-5 && abs(actual.loss - nll) <= 2e-5)
        var rateError = 0.0
        LmTrainer(runtime.model, .001f, .01f, 16, ObjectiveStudy.STEPS, threads = 1).use { native ->
            for (step in 0 until ObjectiveStudy.STEPS) {
                rateError = max(rateError, abs(native.rate(step).toDouble() - ObjectiveStudy.rate(step)))
                check(native.rate(step).toRawBits() == ObjectiveStudy.rate(step).toRawBits())
            }
            check(native.steps == 0)
        }
        return mapOf("passed" to true, "parametersChecked" to runtime.model.params.count, "nativeOptimizerSteps" to 0,
            "maxGradientError" to maxError, "weightedNativeLossError" to abs(actual.loss - expectedLoss),
            "nativeNllError" to abs(actual.loss - nll), "rateComparisons" to ObjectiveStudy.STEPS, "maxRateError" to rateError)
    }
    private fun audit(model: LmModel, sample: ObjectiveStudy.Sample, scope: String,
                      coordinates: List<Pair<String, Int>>): Map<String, Any> {
        val before = ObjectiveStudy.weightsSha(model)
        val gradient = ObjectiveStudy.gradients(model, listOf(sample))
        val floatNll = model.nll(sample.ids, sample.targets).filter { it.isFinite() }.average()
        val doubleNll = oracle(model, sample.ids, sample.targets)
        check(abs(floatNll - doubleNll) <= 2e-5 && abs(gradient.loss - doubleNll) <= 2e-5)
        var maxError = 0.0; var maxStepDifference = 0.0; var failures = 0
        for ((name, index) in coordinates) {
            val analytic = gradient.values.getValue(name).data[index].toDouble()
            fun difference(step: Double) = (oracle(model, sample.ids, sample.targets, name, index, step) -
                    oracle(model, sample.ids, sample.targets, name, index, -step)) / (2 * step)
            val coarse = difference(1e-5); val reference = difference(5e-6)
            check(analytic.isFinite() && coarse.isFinite() && reference.isFinite())
            val error = abs(analytic - reference); val allowed = tolerance(reference)
            val passed = error <= allowed
            maxError = max(maxError, error); maxStepDifference = max(maxStepDifference, abs(coarse - reference))
            if (!passed) failures++
            ObjectiveStudy.emit("gradient_coordinate", "scope" to scope, "tensor" to name, "index" to index,
                "analytic" to analytic, "doubleAt1eMinus5" to coarse, "doubleAt5eMinus6" to reference,
                "absoluteError" to error, "tolerance" to allowed, "passed" to passed)
        }
        check(ObjectiveStudy.weightsSha(model) == before)
        val summary = mapOf("scope" to scope, "coordinates" to coordinates.size, "activeTargets" to sample.active,
            "nativeNll" to floatNll, "independentDoubleNll" to doubleNll, "maxAbsoluteError" to maxError,
            "maxDoubleStepDifference" to maxStepDifference, "failures" to failures, "weightsUnchanged" to true)
        ObjectiveStudy.emit("gradient_audit", "summary" to summary)
        check(failures == 0) { "predeclared derivative tolerance failed: $scope" }
        return summary
    }
    fun run(runtime: LmRuntime, data: List<ObjectiveStudy.Row>, trainFile: File, checkpoint: File) {
        check(data.size == 128)
        val started = System.nanoTime(); val original = ObjectiveStudy.weightsSha(runtime.model)
        val lengths = data.map { ObjectiveStudy.sample(runtime, it, "full_text").ids.size }
        for (row in data) for (answer in listOf("A", "B", "C", "D")) {
            val full = ObjectiveStudy.sample(runtime, row.copy(answer = answer), "full_text")
            val masked = ObjectiveStudy.sample(runtime, row.copy(answer = answer), "answer_eos")
            check(full.ids.contentEquals(masked.ids) && masked.active == 2)
            check(masked.targets.take(masked.answerIndex).all { it == -1 })
        }
        val mask = closedFormMask(); val full = fullTextParity(runtime, data)
        val tiny = LmModel(LmParams.init(LmConfig(LmArch.MINGRU, 258, 8, 2, context = 8, mlp = 12), 202611043L))
        val ids = intArrayOf(4, 7, 4, 12, 7, 4)
        val coordinates = tiny.params.mats.flatMap { (name, p) ->
            val indices = if (name == "tok") listOf(1, 4, 5, 7, 12).flatMap { token -> listOf(0, 4, 7).map { token * 8 + it } }
                          else listOf(0, p.data.size / 2, p.data.lastIndex).distinct()
            indices.map { name to it }
        }
        val tinyFull = audit(tiny, ObjectiveStudy.Sample(ids, intArrayOf(7, 4, 12, 7, 5, 1), 4), "tiny_full", coordinates)
        val tinyMasked = audit(tiny, ObjectiveStudy.Sample(ids, intArrayOf(-1, -1, -1, -1, 5, 1), 4), "tiny_masked", coordinates)
        val actualRow = data.first { it.template == "owner_late" && it.answer == "A" }
        val actual = ObjectiveStudy.sample(runtime, actualRow, "answer_eos")
        val actualCoordinates = mutableListOf("tok" to (actual.ids[1] * 64),
            "tok" to (actual.targets[actual.answerIndex] * 64 + 17), "tok" to (runtime.tokenizer.eos * 64 + 31),
            "nf" to 0, "nf" to 63)
        for (layer in 0..1) for ((leaf, index) in listOf("wc" to 17, "wz" to 63, "wg" to 127, "wo" to 256, "n1" to 0, "bz" to 31))
            actualCoordinates += "l$layer.$leaf" to index
        actualCoordinates += listOf("l0.w1" to 17, "l1.w2" to 17)
        val real = audit(runtime.model, actual, "original_checkpoint_masked", actualCoordinates.distinct())
        check(ObjectiveStudy.weightsSha(runtime.model) == original)
        check(ObjectiveStudy.sha(checkpoint.readBytes()) == ObjectiveStudy.ORIGINAL)
        ObjectiveStudy.emit("preflight_result", "passed" to true, "parameters" to runtime.model.params.count,
            "checkpointSha256" to ObjectiveStudy.ORIGINAL, "tokenizerSha256" to runtime.tokenizer.sha256,
            "trainSha256" to ObjectiveStudy.sha(trainFile.readBytes()), "rows" to data.size,
            "prefixStable" to true, "eachAnswerExactlyOneToken" to true, "promptTruncation" to false,
            "minFullSequencePositions" to lengths.min(), "maxFullSequencePositions" to lengths.max(),
            "fullTargetsPerEpoch" to lengths.sum(), "answerEosTargetsPerEpoch" to 256,
            "closedFormMask" to mask, "fullTextNativeParity" to full,
            "derivativeAudits" to listOf(tinyFull, tinyMasked, real), "optimizerUpdates" to 0,
            "originalWeightsUnchanged" to true, "millis" to (System.nanoTime() - started) / 1_000_000)
    }
}
