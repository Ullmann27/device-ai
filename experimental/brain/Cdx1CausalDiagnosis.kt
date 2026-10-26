package at.heinz.deviceai.learning

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/** Read-only checkpoint diagnosis plus finite differences on NEW throwaway models.
 * Reflection is deliberately contained in this research harness, never the app.
 */
object Cdx1CausalDiagnosis {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4) { "SWB1_BASE64 CDX1_BASE64 NEW_REPORT_JSON SOURCE_SHA" }
        val out = File(args[2]); require(!out.exists())
        fun decode(path: String) = Base64.getDecoder().decode(File(path).readText().trim())
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val swb = decode(args[0]); val cdx = decode(args[1])
        val encoder = TrainableSubwordEncoder.restore(swb)
        val model = TinyContextDecoder.restore(cdx)
        val train = listOf(
            DecoderEvidencePair("Mira wohnt in Graz.", "Wo wohnt Mira?", "Mira wohnt in Graz."),
            DecoderEvidencePair("Lea wohnt in Linz.", "Wo wohnt Lea?", "Lea wohnt in Linz."),
            DecoderEvidencePair("Nora arbeitet als Ärztin.", "Was arbeitet Nora?", "Nora ist Ärztin."),
            DecoderEvidencePair("Paul trägt einen Helm.", "Was trägt Paul?", "Paul trägt einen Helm.")
        )
        val conditions = train.map { encoder.representation(it.conditionerText()) }
        val report = JSONObject().put("schema", "CDX1_CAUSAL_DIAGNOSIS_V1")
            .put("sourceCommit", args[3]).put("encoderSha256", hash(swb))
            .put("decoderSha256", hash(cdx)).put("updates", model.updates)
            .put("ownerWeightsModified", false).put("productionPromotion", false)
        val observations = JSONArray()
        train.forEachIndexed { i, pair ->
            val c = conditions[i]
            val p = model.nextTokenProbabilities(pair.prefix(), c)
            val zero = model.nextTokenProbabilities(pair.prefix())
            val tail = pair.prefix().toByteArray().takeLast(TinyContextDecoder.CONTEXT)
                .toByteArray().toString(Charsets.UTF_8)
            val top = JSONArray()
            p.indices.sortedByDescending { p[it] }.take(5).forEach { t ->
                top.put(JSONObject().put("token", t).put("probability", p[t]))
            }
            val ablations = JSONArray()
            listOf("correct" to c, "zero" to DoubleArray(16),
                "wrongPair" to conditions[(i + 1) % conditions.size],
                "amplified32" to c.map { (it * 32).coerceIn(-1.0, 1.0) }.toDoubleArray())
                .forEach { (label, values) ->
                    val g = model.generate(pair.prefix(), 48, values)
                    ablations.put(JSONObject().put("condition", label).put("text", g.text)
                        .put("ended", g.ended).put("bytes", g.bytesGenerated))
                }
            val answer = pair.answer.toByteArray(Charsets.UTF_8)
            var viaInference = 0.0
            for (n in 0..answer.size) {
                val prompt = (pair.prefix().toByteArray() + answer.take(n).toByteArray()).toString(Charsets.UTF_8)
                // All old corpus byte prefixes fit UTF-8 except the first byte of ä/Ä.
                // Those positions need a private forward call to avoid UTF-8 replacement.
                val full = pair.prefix().toByteArray() + answer.take(n).toByteArray()
                val context = IntArray(8) { j -> full[full.size - 8 + j].toInt() and 255 }
                val forward = model.javaClass.getDeclaredMethod("forward", IntArray::class.java, DoubleArray::class.java)
                    .also { it.isAccessible = true }.invoke(model, context, c)
                val probabilities = forward.javaClass.getDeclaredMethod("getP").also { it.isAccessible = true }
                    .invoke(forward) as DoubleArray
                val target = if (n == answer.size) 257 else answer[n].toInt() and 255
                viaInference += -ln(probabilities[target].coerceAtLeast(1e-12))
                if (full.contentEquals(prompt.toByteArray())) {
                    val publicP = model.nextTokenProbabilities(prompt, c)
                    check(publicP.contentEquals(probabilities))
                }
            }
            val apiLoss = model.evaluateEvidencePairs(DecoderEvidenceCorpus(listOf(pair), DecoderSplit.AUDIT)) { c }
            check(abs(viaInference / (answer.size + 1) - apiLoss) < 1e-12)
            observations.put(JSONObject().put("evidence", pair.evidence).put("question", pair.question)
                .put("contextTail", tail).put("conditioner", JSONArray(c.toList()))
                .put("conditionerNorm", sqrt(c.sumOf { it * it })).put("topFirstTokens", top)
                .put("conditionVsZeroMaxProbabilityDifference", p.indices.maxOf { abs(p[it] - zero[it]) })
                .put("teacherForcedLossMatchesInferenceForward", true).put("loss", apiLoss)
                .put("generations", ablations))
        }
        report.put("observations", observations)
        val distances = JSONArray()
        for (i in conditions.indices) for (j in i+1 until conditions.size) {
            val a = conditions[i]; val b = conditions[j]
            distances.put(JSONObject().put("a", i).put("b", j)
                .put("l2", sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })))
        }
        report.put("conditionDistances", distances)
        val pair = train.first(); val c = conditions.first(); val rate = .0001
        val fresh = TinyContextDecoder(91); val original = fresh.save()
        val stepped = TinyContextDecoder.restore(original)
        val result = stepped.trainEvidencePairs(DecoderEvidenceCorpus(listOf(pair), DecoderSplit.TRAIN),
            steps = 1, learningRate = rate, conditionerFor = { c }, maxMillis = 5000)
        check(result.steps == 1)
        val derivativeRows = JSONArray(); var worst = 0.0
        for (name in listOf("embedding", "hiddenWeights", "hiddenBias", "outputWeights", "outputBias")) {
            fun weights(m: TinyContextDecoder) = m.javaClass.getDeclaredField(name)
                .also { it.isAccessible = true }.get(m) as DoubleArray
            val old = weights(fresh); val after = weights(stepped)
            // Largest affected parameters test nonzero signal; include conditioner
            // columns and repeated context-byte embeddings via all largest entries.
            val indices = old.indices.sortedByDescending { abs(after[it] - old[it]) }.take(6)
            for (index in indices) {
                val epsilon = 1e-5
                val plus = TinyContextDecoder.restore(original); weights(plus)[index] += epsilon
                val minus = TinyContextDecoder.restore(original); weights(minus)[index] -= epsilon
                val target = pair.answer.toByteArray()[0].toInt() and 255
                fun nll(m: TinyContextDecoder) = -ln(m.nextTokenProbabilities(pair.prefix(), c)[target])
                val numeric = (nll(plus) - nll(minus)) / (2 * epsilon)
                val analytic = (old[index] - after[index]) / rate
                val error = abs(numeric - analytic)
                worst = maxOf(worst, error)
                derivativeRows.put(JSONObject().put("array", name).put("index", index)
                    .put("finiteDifference", numeric).put("sgdDerivative", analytic).put("absoluteError", error))
            }
        }
        check(worst < 1e-6) { "SGD derivative mismatch $worst" }
        check(model.save().contentEquals(cdx))
        report.put("finiteDifferences", derivativeRows).put("maxAbsoluteDerivativeError", worst)
            .put("status", "MEASURED_HOST_ONLY")
        out.writeText(report.toString(2) + "\n"); println(report.toString(2))
    }
}
