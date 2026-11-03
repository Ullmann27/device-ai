package at.heinz.deviceai.learning.span

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Label-free research instrumentation of the unchanged CDX2 selector and verifier.
 * No training, extra semantic rule, ranker, or productive caller is introduced. */
object GeneralizationProbe {
    fun run(request: JSONObject, weights: DoubleArray): JSONObject {
        require(request.keySet() == setOf("id", "question", "sources")) { "label-free request required" }
        val q = request.getString("question")
        val sources = Cdx2PointerCandidate.sources(request.getJSONArray("sources"))
        val options = if (weights.size == 11) Cdx2PointerCandidate.options(q, sources)
            else Cdx2CounterfactualShadow.options(q, sources)
        val probabilities = if (weights.size == 11) Cdx2PointerCandidate.probabilities(weights, options)
            else Cdx2CounterfactualShadow.probabilities(weights, options)
        val found = options[probabilities.indices.maxBy { probabilities[it] }].found
        val accepted = Cdx2PointerCandidate.verified(q, sources, found)
        val rule = EvidenceBoundNaturalLocationProbe.answer(q, sources)
        val row = JSONObject().put("id", request.getString("id"))
            .put("RAW", Cdx2PointerCandidate.tuple(found))
            .put("VERIFIED", Cdx2PointerCandidate.tuple(if (accepted) found else null))
            .put("RULE", Cdx2PointerCandidate.tuple(rule as? EvidenceBoundNaturalLocationProbe.Decision.Found))
            .put("ruleReason", (rule as? EvidenceBoundNaturalLocationProbe.Decision.Abstained)?.reason ?: "FOUND")
            .put("inputValid", Cdx2PointerCandidate.valid(q, sources))
        val traces = JSONArray()
        options.forEachIndexed { index, option -> traces.put(JSONObject()
            .put("tuple", Cdx2PointerCandidate.tuple(option.found))
            .put("features", JSONArray(option.features.toList())).put("probability", probabilities[index])) }
        return row.put("options", traces)
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4) { "BASE_CHECKPOINT CURRENT_CHECKPOINT LABEL_FREE_REQUESTS FRESH_OUTDIR" }
        val base = Cdx2PointerCandidate.load(args[0])
        val currentJson = JSONObject(File(args[1]).readText())
        require(currentJson.getString("schema") == "CDX2_FROZEN_BASE_ADAPTER_V1")
        require(currentJson.getBoolean("frozenFirstEleven"))
        val stored = currentJson.getJSONArray("weights")
        require(stored.length() == 13)
        val current = DoubleArray(13) { stored.getDouble(it) }
        require(current.all { it.isFinite() && kotlin.math.abs(it) <= 100 })
        require((0..10).all { current[it].toBits() == base[it].toBits() })
        val requests = JSONObject(File(args[2]).readText()).getJSONArray("cases")
        require(requests.length() in 1..256)
        val out = File(args[3]); require(!out.exists() && out.mkdirs())
        val arms = listOf("base11" to base, "current13" to current)
        // Equal warmup workload; reverse arm order on alternating repetitions.
        repeat(5) { repeat ->
            for ((_, weights) in if (repeat % 2 == 0) arms else arms.reversed())
                for (i in 0 until requests.length()) run(requests.getJSONObject(i), weights)
        }
        val timings = arms.associate { it.first to mutableListOf<Double>() }
        val predictions = arms.associate { it.first to JSONArray() }
        repeat(5) { repeat ->
            for ((name, weights) in if (repeat % 2 == 0) arms else arms.reversed()) {
                for (i in 0 until requests.length()) {
                    val start = System.nanoTime()
                    val row = run(requests.getJSONObject(i), weights)
                    timings.getValue(name) += (System.nanoTime() - start) / 1e6
                    if (repeat == 0) predictions.getValue(name).put(row)
                    else check(row.toString() == predictions.getValue(name).getJSONObject(i).toString()) {
                        "nondeterministic prediction ${row.getString("id")}" }
                }
            }
        }
        val measurements = JSONObject()
        for ((name, _) in arms) {
            File(out, "$name-predictions.json").writeText(JSONObject().put("cases", predictions.getValue(name)).toString(2) + "\n")
            val t = timings.getValue(name).sorted()
            measurements.put(name, JSONObject().put("samples", t.size).put("medianMillis", t[t.size / 2])
                .put("p95Millis", t[kotlin.math.ceil(t.size * .95).toInt() - 1])
                .put("totalMillis", t.sum()).put("samplesMillis", JSONArray(t)))
        }
        File(out, "timings.json").writeText(JSONObject().put("warmupPasses", 5).put("measuredPasses", 5)
            .put("scope", "host JVM; full instrumented pipeline including trace allocation; not Android or model-only inference")
            .put("measurements", measurements).toString(2) + "\n")
        println("CDX2_GENERALIZATION_COMPLETE cases=${requests.length()} arms=2 predictionRepeats=5")
    }
}
