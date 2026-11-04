package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.ln
import kotlin.math.sqrt

object LmChannelDiagnostic {
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private val models = mapOf(
        "S0" to "70cb6ed4020042c4ced21bf0c58dab1b83ee2a51655fdbec41279cd961234435",
        "S1" to "90b58fb58e048bd4eac967f2b0b131373170201660de7a3d330b393a6c8d820f",
        "W0" to "af617cf6d223a3e53b44bc3c0578b6924e9c73d8b92f558a981af22990cad04d",
        "W1" to "0f2b5481cf60d6c6021e9e24d1e5ba70d303da274c18933de29c48a47489cf8c"
    )
    private const val tokenizerSha = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun emit(kind: String, vararg fields: Pair<String, Any>) =
        println(kind + fields.joinToString("") { "\t${it.first}=${it.second}" })

    private fun floats(values: FloatArray): String {
        val bytes = ByteBuffer.allocate(values.size * 4)
        values.forEach { bytes.putFloat(it) }
        return encoder.encodeToString(bytes.array())
    }

    private fun selected(name: String, block: String) = when (block) {
        "tok", "nf" -> name == block
        "l0", "l1" -> name.startsWith("$block.")
        else -> false
    }

    private fun trace(arm: String, probe: String, stage: String, values: Mat, factor: Int) {
        require(values.data.all { it.isFinite() })
        require(values.cols == 64 * factor)
        var squared = 0.0
        var residual = 0.0
        var maximumRange = 0.0
        var tied = 0
        for (row in 0 until values.rows) for (col in 0 until 64) {
            var sum = 0.0
            var low = Double.POSITIVE_INFINITY
            var high = Double.NEGATIVE_INFINITY
            var exact = true
            val first = values[row, col].toRawBits()
            for (replica in 0 until factor) {
                val value = values[row, col + replica * 64]
                val x = value.toDouble()
                sum += x
                squared += x * x
                low = minOf(low, x)
                high = maxOf(high, x)
                exact = exact && value.toRawBits() == first
            }
            if (exact) tied++
            val mean = sum / factor
            for (replica in 0 until factor) {
                val difference = values[row, col + replica * 64].toDouble() - mean
                residual += difference * difference
            }
            maximumRange = maxOf(maximumRange, high - low)
        }
        val rms = sqrt(squared / values.data.size)
        val spread = sqrt(residual / values.data.size)
        emit("TRACE", "arm" to arm, "probe" to probe, "stage" to stage,
            "replicas" to factor, "groups" to values.rows * 64, "exactlyTiedGroups" to tied,
            "valueRms" to rms, "spreadRms" to spread,
            "relativeSpreadRms" to if (rms == 0.0) 0.0 else spread / rms,
            "maximumReplicaRange" to maximumRange)
    }

    private fun traced(model: LmModel, ids: IntArray, arm: String, probe: String): Mat {
        val tape = Tape(training = false)
        val nodes = model.frozenNodes(tape)
        fun p(name: String) = nodes.getValue(name)
        val factor = model.config.dim / 64
        var x = tape.gather(p("tok"), ids)
        trace(arm, probe, "embedding", x.value, factor)
        for (layer in 0 until model.config.layers) {
            val h = tape.rmsnorm(x, p("l$layer.n1"))
            val z = tape.sigmoid(tape.addRow(tape.matmul(h, p("l$layer.wz")), p("l$layer.bz")))
            val state = tape.minGruScan(z, tape.matmul(h, p("l$layer.wc")))
            val mix = tape.matmul(tape.mul(state, tape.silu(tape.matmul(h, p("l$layer.wg")))), p("l$layer.wo"))
            x = tape.add(x, mix)
            trace(arm, probe, "l$layer.recurrent", x.value, factor)
            val h2 = tape.rmsnorm(x, p("l$layer.n2"))
            val f = tape.addRow(tape.matmul(tape.gelu(tape.addRow(tape.matmul(h2, p("l$layer.w1")), p("l$layer.b1"))),
                p("l$layer.w2")), p("l$layer.b2"))
            x = tape.add(x, f)
            trace(arm, probe, "l$layer.mlp", x.value, factor)
        }
        val final = tape.rmsnorm(x, p("nf"))
        trace(arm, probe, "finalNorm", final.value, factor)
        return tape.matmulT(final, p("tok")).value
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 7) { "models tokenizer probes arm receiver donor block" }
        val modelDir = File(args[0])
        val tokenFile = File(args[1])
        val probesFile = File(args[2])
        val arm = args[3]
        val receiver = args[4]
        val donor = args[5]
        val block = args[6]
        require(receiver in models && donor in models)
        require(receiver[0] == donor[0])
        require((block == "native" && receiver == donor && arm == receiver) ||
            (block in setOf("tok", "nf", "l0", "l1") && receiver != donor && arm == "${receiver}_from_${donor}_$block"))
        val tokenBytes = tokenFile.readBytes()
        require(hash(tokenBytes) == tokenizerSha)
        val receiverBytes = File(modelDir, "$receiver.lm1").readBytes()
        val donorBytes = File(modelDir, "$donor.lm1").readBytes()
        val runtime = LmRuntime.load(receiverBytes, tokenBytes, models.getValue(receiver))
        val donorRuntime = LmRuntime.load(donorBytes, tokenBytes, models.getValue(donor))
        require(runtime.model.config == donorRuntime.model.config)
        require(runtime.model.config.arch == LmArch.MINGRU && runtime.model.config.layers == 2)
        val originals = runtime.model.params.mats.mapValues { (_, m) -> floats(m.data) }
        val matrices = LinkedHashMap<String, Mat>()
        var replacements = 0
        for ((name, original) in runtime.model.params.mats) {
            matrices[name] = if (selected(name, block)) {
                replacements++
                donorRuntime.model.params.mats.getValue(name).copy()
            } else original.copy()
        }
        require(replacements == when (block) { "native" -> 0; "tok", "nf" -> 1; else -> 11 })
        val model = LmModel(LmParams(runtime.model.config, matrices))
        require(model.params.allFinite())
        val tok = runtime.tokenizer
        val rawProbes = probesFile.readBytes()
        val probes = rawProbes.toString(Charsets.UTF_8).trimEnd().lines().map { line ->
            val fields = line.split('\t')
            require(fields.size == 2)
            fields[0] to decoder.decode(fields[1]).toString(Charsets.UTF_8)
        }
        require(probes.size == 12 && probes.map { it.first }.toSet().size == 12)
        emit("START", "arm" to arm, "receiver" to receiver, "donor" to donor, "block" to block,
            "receiverSha256" to hash(receiverBytes), "donorSha256" to hash(donorBytes),
            "tokenizerSha256" to tokenizerSha, "probesSha256" to hash(rawProbes),
            "parameters" to model.params.count, "replacedTensors" to replacements, "training" to false)
        for ((id, text) in probes) {
            val ids = intArrayOf(tok.bos) + tok.encode(text)
            require(ids.size + 32 < model.config.context)
            val instrumented = traced(model, ids, arm, id)
            val tape = Tape(training = false)
            val standard = model.logits(tape, model.frozenNodes(tape), ids).value
            check(instrumented.data.contentEquals(standard.data)) { "instrumentation parity failed" }
            val logits = standard.data.copyOfRange((ids.size - 1) * tok.vocabSize, ids.size * tok.vocabSize)
            check(logits.all { it.isFinite() })
            val probs = model.nextDistribution(ids)
            check(probs.all { it.isFinite() && it >= 0f })
            val allowed = probs.indices.filter { it != tok.bos }
            val top = allowed.maxBy { probs[it] }
            val bestOther = allowed.filter { it != tok.eos }.maxBy { logits[it] }
            val rank = 1 + allowed.count { probs[it] > probs[tok.eos] }
            val entropy = probs.sumOf { if (it == 0f) 0.0 else -it.toDouble() * ln(it.toDouble()) }
            emit("PROBE", "arm" to arm, "probe" to id, "inputIds" to ids.joinToString(","),
                "topAllowedId" to top, "eosId" to tok.eos, "bosId" to tok.bos,
                "eosProbability" to probs[tok.eos], "eosRank" to rank,
                "eosLogit" to logits[tok.eos], "bestOtherLogit" to logits[bestOther],
                "eosMargin" to logits[tok.eos].toDouble() - logits[bestOther],
                "entropyNats" to entropy, "probabilitiesF32BE" to floats(probs),
                "instrumentationBitwiseParity" to true)
            if (block == "native") {
                val result = runtime.generate(LmRuntime.Request(text, maxNewTokens = 32, maxMillis = 5000))
                emit("GENERATION", "arm" to arm, "probe" to id, "tokens" to result.newTokens,
                    "stop" to result.stop, "millis" to result.millis, "truncated" to result.promptTruncated,
                    "textBase64" to encoder.encodeToString(result.text.toByteArray(Charsets.UTF_8)))
                check(!result.promptTruncated)
            }
        }
        check(runtime.model.params.mats.all { (name, m) -> floats(m.data) == originals.getValue(name) })
        check(hash(File(modelDir, "$receiver.lm1").readBytes()) == models.getValue(receiver))
        check(hash(File(modelDir, "$donor.lm1").readBytes()) == models.getValue(donor))
        check(hash(tokenFile.readBytes()) == tokenizerSha && probesFile.readBytes().contentEquals(rawProbes))
        emit("DONE", "arm" to arm, "probes" to probes.size, "inputsUnchanged" to true,
            "receiverArraysUnchanged" to true, "trainingUpdates" to 0, "serializedCheckpoints" to 0)
    }
}
