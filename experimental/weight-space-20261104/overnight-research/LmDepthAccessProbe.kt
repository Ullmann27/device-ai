package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.abs
import kotlin.math.sqrt

object LmDepthAccessProbe {
    private const val smallSha = "70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435"
    private const val wideSha = "af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d"
    private const val tokenSha = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"

    private fun hex(value: ByteArray) = value.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sha(value: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(value))
    private fun emit(kind: String, vararg values: Pair<String, Any>) =
        println(kind + values.joinToString("") { "\t${it.first}=${it.second}" })

    private fun identity(params: LmParams): String {
        val hash = MessageDigest.getInstance("SHA-256")
        for ((name, matrix) in params.mats) {
            hash.update("$name:${matrix.rows}:${matrix.cols}\n".toByteArray())
            val buffer = ByteBuffer.allocate(matrix.data.size * 4)
            matrix.data.forEach { buffer.putFloat(it) }
            hash.update(buffer.array())
        }
        return hex(hash.digest())
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "models tokenizer probes" }
        val directory = File(args[0])
        val tokenFile = File(args[1])
        val probeFile = File(args[2])
        val tokenBytes = tokenFile.readBytes()
        require(sha(tokenBytes) == tokenSha)
        val smallBytes = File(directory, "S0.lm1").readBytes()
        val wideBytes = File(directory, "W0.lm1").readBytes()
        val small = LmRuntime.load(smallBytes, tokenBytes, smallSha)
        val wide = LmRuntime.load(wideBytes, tokenBytes, wideSha)
        val params = LmWeightSurgery.deepenMinGru(wide.model.params, 1, 20261104L)
        val model = LmModel(params)
        require(params.count == 9712128 && params.allFinite())
        val initial = identity(params)
        val parent = identity(wide.model.params)
        val probeBytes = probeFile.readBytes()
        val probes = probeBytes.toString(Charsets.UTF_8).trimEnd().lines().map { line ->
            val fields = line.split('\t')
            require(fields.size == 2)
            fields[0] to Base64.getDecoder().decode(fields[1]).toString(Charsets.UTF_8)
        }
        require(probes.size == 12 && probes.take(4).all { it.first.startsWith("general-") })
        val tok = small.tokenizer
        emit("START", "parameters" to params.count, "initialParameterIdentity" to initial,
            "wideCheckpointSha256" to wideSha, "smallCheckpointSha256" to smallSha,
            "tokenizerSha256" to tokenSha, "probesSha256" to sha(probeBytes), "optimizerConstructed" to false)
        val references = LinkedHashMap<String, FloatArray>()
        for ((id, text) in probes) {
            val ids = intArrayOf(tok.bos) + tok.encode(text)
            val wideProb = wide.model.nextDistribution(ids)
            val smallProb = small.model.nextDistribution(ids)
            val original = model.nextDistribution(ids)
            val difference = wideProb.indices.maxOf { abs(wideProb[it].toDouble() - smallProb[it]) }
            val same = wideProb.contentEquals(original)
            emit("BASE_PARITY", "probe" to id, "maximumProbabilityDelta" to difference,
                "deepWideBitwiseParity" to same, "pass" to (same && difference <= 1e-5))
            check(same && difference <= 1e-5) { "baseline gate failed" }
            references[id] = wideProb
        }
        val wo = params.mats.getValue("l2.wo")
        val w2 = params.mats.getValue("l2.w2")
        check(wo.data.all { it == 0f } && w2.data.all { it == 0f })
        var gradientsCompleted = 0
        try {
            for (j in 0 until 512) wo[j, j] = 0.0001f
            for (row in 0 until 2048) w2[row, row % 512] = 0.000025f
            emit("OPENED", "changedCoordinates" to 2560, "woDiagonal" to 0.0001f,
                "w2MappedEntry" to 0.000025f, "openedParameterIdentity" to identity(params))
            for ((id, text) in probes) {
                val probabilities = model.nextDistribution(intArrayOf(tok.bos) + tok.encode(text))
                val reference = references.getValue(id)
                val difference = reference.indices.maxOf { abs(reference[it].toDouble() - probabilities[it]) }
                emit("OPEN_PARITY", "probe" to id, "maximumProbabilityDelta" to difference, "pass" to (difference <= 1e-5))
                check(difference <= 1e-5) { "opened-path parity gate failed" }
            }
            for ((id, text) in probes.take(4)) {
                val sequence = intArrayOf(tok.bos) + tok.encode(text) + intArrayOf(tok.eos)
                val ids = sequence.copyOfRange(0, sequence.size - 1)
                val targets = sequence.copyOfRange(1, sequence.size)
                val gradients = params.zeros()
                val tape = Tape(training = true)
                val nodes = params.mats.mapValues { (name, matrix) -> tape.param(matrix, gradients.getValue(name)) }
                val loss = tape.crossEntropy(model.logits(tape, nodes, ids), targets)
                check(loss.value.data[0].isFinite())
                tape.backward(loss)
                var total = 0
                for ((name, g) in gradients) {
                    check(g.data.all { it.isFinite() })
                    val nonzero = g.data.count { it != 0f }
                    total += nonzero
                    emit("GRADIENT", "probe" to id, "tensor" to name, "parameters" to g.data.size,
                        "nonzeroGradients" to nonzero, "gradientL2" to sqrt(g.data.sumOf { it.toDouble() * it }),
                        "minimumNonzeroAbs" to g.data.filter { it != 0f }.minOfOrNull { abs(it) }.let { it ?: 0f },
                        "maximumAbs" to g.data.maxOf { abs(it) }, "allFinite" to true)
                }
                emit("LOSS", "probe" to id, "targetCount" to targets.size, "meanLoss" to loss.value.data[0],
                    "nonzeroGradientCoordinates" to total, "allParametersReached" to (total == 9712128))
                check(total == 9712128) { "not every coordinate received a gradient" }
                gradientsCompleted++
            }
        } finally {
            wo.data.fill(0f)
            w2.data.fill(0f)
            val restored = identity(params) == initial && identity(wide.model.params) == parent
            emit("RESTORED", "pass" to restored, "initialParameterIdentity" to initial,
                "finalParameterIdentity" to identity(params), "completedLossProbes" to gradientsCompleted)
            check(restored) { "private perturbation was not restored" }
        }
        check(sha(File(directory, "S0.lm1").readBytes()) == smallSha && sha(File(directory, "W0.lm1").readBytes()) == wideSha)
        check(tokenFile.readBytes().contentEquals(tokenBytes) && probeFile.readBytes().contentEquals(probeBytes))
        emit("DONE", "lossProbes" to gradientsCompleted, "inputsUnchanged" to true,
            "optimizerUpdates" to 0, "serializedCheckpoints" to 0, "modelPromotion" to false)
    }
}
