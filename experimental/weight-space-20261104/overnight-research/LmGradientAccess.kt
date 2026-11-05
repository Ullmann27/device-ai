package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.abs
import kotlin.math.sqrt

object LmGradientAccess {
    private const val smallSha = "70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435"
    private const val wideSha = "af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d"
    private const val tokenizerSha = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sha(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun emit(kind: String, vararg fields: Pair<String, Any>) =
        println(kind + fields.joinToString("") { "\t${it.first}=${it.second}" })

    private fun identity(params: LmParams): String {
        val hash = MessageDigest.getInstance("SHA-256")
        for ((name, value) in params.mats) {
            hash.update("$name:${value.rows}:${value.cols}\n".toByteArray())
            val buffer = ByteBuffer.allocate(value.data.size * 4)
            value.data.forEach { buffer.putFloat(it) }
            hash.update(buffer.array())
        }
        return hex(hash.digest())
    }

    private fun clone(params: LmParams) = LmParams(params.config,
        LinkedHashMap(params.mats.mapValues { (_, matrix) -> matrix.copy() }))

    data class Gradient(val values: Map<String, Mat>, val loss: Float, val nll: Double)

    private fun meanNll(model: LmModel, ids: IntArray, targets: IntArray): Double {
        val values = model.nll(ids, targets)
        check(values.all { it.isFinite() })
        return values.average()
    }

    private fun gradient(model: LmModel, ids: IntArray, targets: IntArray): Gradient {
        val gradients = model.params.zeros()
        val tape = Tape(training = true)
        val nodes = model.params.mats.mapValues { (name, matrix) -> tape.param(matrix, gradients.getValue(name)) }
        val loss = tape.crossEntropy(model.logits(tape, nodes, ids), targets)
        check(loss.value.data[0].isFinite())
        tape.backward(loss)
        check(gradients.values.all { m -> m.data.all { it.isFinite() } })
        val nll = meanNll(model, ids, targets)
        check(abs(loss.value.data[0].toDouble() - nll) <= 1e-5)
        return Gradient(gradients, loss.value.data[0], nll)
    }

    private fun length(values: FloatArray): Double = sqrt(values.sumOf { it.toDouble() * it })

    private fun finiteDifference(profile: String, probe: String, model: LmModel, gradients: Map<String, Mat>,
                                 tensor: String, ids: IntArray, targets: IntArray) {
        val parameter = model.params.mats.getValue(tensor).data
        val g = gradients.getValue(tensor).data
        val norm = length(g)
        check(norm.isFinite() && norm > 0)
        val original = parameter.copyOf()
        val epsilon = 0.001f
        val plus = FloatArray(parameter.size) { (original[it] + epsilon * g[it] / norm).toFloat() }
        val minus = FloatArray(parameter.size) { (original[it] - epsilon * g[it] / norm).toFloat() }
        val analytic = g.indices.sumOf { g[it].toDouble() * (plus[it].toDouble() - minus[it]) / (2.0 * epsilon) }
        val (positive, negative) = try {
            plus.copyInto(parameter)
            val positive = meanNll(model, ids, targets)
            minus.copyInto(parameter)
            val negative = meanNll(model, ids, targets)
            positive to negative
        } finally {
            original.copyInto(parameter)
        }
        check(parameter.contentEquals(original))
        val numerical = (positive - negative) / (2.0 * epsilon)
        val error = abs(numerical - analytic)
        val limit = 0.0005 + 0.02 * abs(analytic)
        emit("DIRECTION", "profile" to profile, "probe" to probe, "tensor" to tensor,
            "epsilon" to epsilon, "gradientL2" to norm, "analyticDerivative" to analytic,
            "positiveNll" to positive, "negativeNll" to negative,
            "numericalDerivative" to numerical, "absoluteError" to error, "limit" to limit,
            "restored" to true, "pass" to (error <= limit))
        check(error <= limit) { "directional derivative gate failed" }
    }

    private fun wideningChain(probe: String, wide: Map<String, Mat>, small: Map<String, Mat>) {
        var errorSquared = 0.0
        var referenceSquared = 0.0
        var maximum = 0.0
        var coordinates = 0
        for ((name, reference) in small) {
            val expanded = wide.getValue(name)
            var tensorMaximum = 0.0
            for (row in 0 until reference.rows) for (col in 0 until reference.cols) {
                var sum = 0.0
                if (name == "tok" || reference.rows == 1) {
                    for (copy in 0 until 8) sum += expanded[row, col + copy * reference.cols]
                    if (name == "nf") sum /= 8
                } else {
                    for (a in 0 until 8) for (b in 0 until 8)
                        sum += expanded[row + a * reference.rows, col + b * reference.cols]
                    sum /= 8
                }
                val expected = reference[row, col].toDouble()
                val difference = sum - expected
                errorSquared += difference * difference
                referenceSquared += expected * expected
                tensorMaximum = maxOf(tensorMaximum, abs(difference))
                coordinates++
            }
            maximum = maxOf(maximum, tensorMaximum)
            emit("CHAIN_TENSOR", "profile" to "W0", "probe" to probe, "tensor" to name,
                "coordinates" to reference.data.size, "maximumAbsoluteError" to tensorMaximum)
        }
        val relative = sqrt(errorSquared / referenceSquared)
        val pass = maximum <= 2e-4 && relative <= 2e-4 && coordinates == 132160
        emit("CHAIN", "profile" to "W0", "probe" to probe, "coordinates" to coordinates,
            "maximumAbsoluteError" to maximum, "relativeL2Error" to relative, "pass" to pass)
        check(pass) { "widening chain-rule gate failed" }
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4) { "models tokenizer probes profile" }
        val directory = File(args[0])
        val tokenFile = File(args[1])
        val probeFile = File(args[2])
        val profile = args[3]
        require(profile in setOf("S0", "W0", "D0"))
        val tokenBytes = tokenFile.readBytes()
        require(sha(tokenBytes) == tokenizerSha)
        val smallBytes = File(directory, "S0.lm1").readBytes()
        val wideBytes = File(directory, "W0.lm1").readBytes()
        val small = LmRuntime.load(smallBytes, tokenBytes, smallSha)
        val wide = LmRuntime.load(wideBytes, tokenBytes, wideSha)
        val params = when (profile) {
            "S0" -> clone(small.model.params)
            "W0" -> clone(wide.model.params)
            else -> LmWeightSurgery.deepenMinGru(wide.model.params, 1, 20261104L)
        }
        val expectedCount = when (profile) { "S0" -> 132160; "W0" -> 6562304; else -> 9712128 }
        require(params.count == expectedCount && params.allFinite())
        val originalIdentity = identity(params)
        val smallIdentity = identity(small.model.params)
        val wideIdentity = identity(wide.model.params)
        val model = LmModel(params)
        val tok = small.tokenizer
        val probeBytes = probeFile.readBytes()
        val probes = probeBytes.toString(Charsets.UTF_8).trimEnd().lines().map { line ->
            val fields = line.split('\t')
            require(fields.size == 2)
            fields[0] to Base64.getDecoder().decode(fields[1]).toString(Charsets.UTF_8)
        }
        require(probes.size == 12 && probes.take(4).all { it.first.startsWith("general-") })
        emit("START", "profile" to profile, "parameters" to params.count, "parameterBytes" to 4L * params.count,
            "initialParameterIdentity" to originalIdentity, "smallCheckpointSha256" to smallSha,
            "wideCheckpointSha256" to wideSha, "tokenizerSha256" to sha(tokenBytes),
            "probesSha256" to sha(probeBytes), "optimizerConstructed" to false)
        for ((probe, text) in probes) {
            val ids = intArrayOf(tok.bos) + tok.encode(text)
            val a = small.model.nextDistribution(ids)
            val b = model.nextDistribution(ids)
            val difference = a.indices.maxOf { abs(a[it].toDouble() - b[it]) }
            val deepParity = profile != "D0" || b.contentEquals(wide.model.nextDistribution(ids))
            emit("INITIAL_PARITY", "profile" to profile, "probe" to probe,
                "maximumProbabilityDelta" to difference, "deepWideBitwiseParity" to deepParity,
                "pass" to (difference <= 1e-5 && deepParity))
            check(difference <= 1e-5 && deepParity) { "initial parity gate failed" }
        }
        var directionChecks = 0
        for ((probe, text) in probes.take(4)) {
            val sequence = intArrayOf(tok.bos) + tok.encode(text) + intArrayOf(tok.eos)
            val ids = sequence.copyOfRange(0, sequence.size - 1)
            val targets = sequence.copyOfRange(1, sequence.size)
            val result = gradient(model, ids, targets)
            var squared = 0.0
            var nonzero = 0
            for ((name, g) in result.values) {
                val gn = length(g.data)
                val pn = length(params.mats.getValue(name).data)
                val count = g.data.count { it != 0f }
                squared += gn * gn
                nonzero += count
                emit("GRADIENT", "profile" to profile, "probe" to probe, "tensor" to name,
                    "parameters" to g.data.size, "parameterL2" to pn, "gradientL2" to gn,
                    "gradientMaxAbs" to g.data.maxOf { abs(it) }, "nonzeroGradients" to count,
                    "relativeGradientL2" to if (pn > 0) gn / pn else "UNDEFINED_ZERO_PARAMETER_NORM",
                    "allFinite" to true)
            }
            emit("LOSS", "profile" to profile, "probe" to probe, "inputIds" to ids.joinToString(","),
                "targets" to targets.joinToString(","), "targetCount" to targets.size, "lossF32" to result.loss,
                "meanNll" to result.nll, "totalGradientL2" to sqrt(squared), "nonzeroGradients" to nonzero)
            if (profile == "W0") wideningChain(probe, result.values, gradient(small.model, ids, targets).values)
            if (profile == "D0") {
                val reference = gradient(wide.model, ids, targets)
                val equal = reference.values.all { (name, g) -> g.data.contentEquals(result.values.getValue(name).data) }
                val inactive = listOf("n1", "wz", "bz", "wc", "wg", "n2", "w1", "b1")
                val active = listOf("wo", "w2", "b2")
                val zero = inactive.all { name -> result.values.getValue("l2.$name").data.all { it == 0f } }
                val nonzeroPaths = active.all { name -> result.values.getValue("l2.$name").data.any { it != 0f } }
                val inactiveCount = inactive.sumOf { result.values.getValue("l2.$it").data.size }
                emit("DEPTH_ACCESS", "profile" to profile, "probe" to probe,
                    "commonGradientBitwiseParity" to equal, "inactiveUpstreamParameters" to inactiveCount,
                    "inactiveUpstreamAllZero" to zero, "allThreeOutputPathsNonzero" to nonzeroPaths,
                    "newLayerParameters" to params.mats.filterKeys { it.startsWith("l2.") }.values.sumOf { it.data.size })
                check(equal && zero && nonzeroPaths && inactiveCount == 1838592) { "identity-layer gradient gate failed" }
            }
            val directions = if (profile == "D0") listOf("nf", "l2.wo", "l2.w2") else listOf("nf")
            for (tensor in directions) {
                finiteDifference(profile, probe, model, result.values, tensor, ids, targets)
                directionChecks++
            }
        }
        check(identity(params) == originalIdentity && identity(small.model.params) == smallIdentity && identity(wide.model.params) == wideIdentity)
        check(sha(File(directory, "S0.lm1").readBytes()) == smallSha && sha(File(directory, "W0.lm1").readBytes()) == wideSha)
        check(tokenFile.readBytes().contentEquals(tokenBytes) && probeFile.readBytes().contentEquals(probeBytes))
        emit("DONE", "profile" to profile, "lossProbes" to 4, "directionChecks" to directionChecks,
            "allArraysRestored" to true, "inputsUnchanged" to true, "optimizerUpdates" to 0,
            "serializedCheckpoints" to 0, "finalParameterIdentity" to identity(params))
    }
}
