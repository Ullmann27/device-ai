package at.heinz.deviceai.learning

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Fixed research protocol using the existing app's authored pair curriculum.
 * No owner data or active checkpoints are opened. Outputs/checkpoints go only
 * into a NEW experiment directory. Independent labels are read after training.
 */
object Cdx1GenerationProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "Usage: NEW_OUTPUT_DIR SWB1_BASE64 INDEPENDENT_FIXTURE_JSON" }
        val out = File(args[0]); require(!out.exists()); check(out.mkdirs())
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val swbBytes = Base64.getDecoder().decode(File(args[1]).readText().trim())
        val encoder = TrainableSubwordEncoder.restore(swbBytes)
        fun pair(e: String, q: String, a: String) = DecoderEvidencePair(e, q, a)
        val train = DecoderEvidenceCorpus(listOf(
            pair("Mira wohnt in Graz.", "Wo wohnt Mira?", "Mira wohnt in Graz."),
            pair("Lea wohnt in Linz.", "Wo wohnt Lea?", "Lea wohnt in Linz."),
            pair("Nora arbeitet als Ärztin.", "Was arbeitet Nora?", "Nora ist Ärztin."),
            pair("Paul trägt einen Helm.", "Was trägt Paul?", "Paul trägt einen Helm.")
        ), DecoderSplit.TRAIN)
        val dev = DecoderEvidenceCorpus(listOf(
            pair("Tim wohnt in Wien.", "Wo wohnt Tim?", "Tim wohnt in Wien."),
            pair("Eva ist Köchin.", "Was arbeitet Eva?", "Eva ist Köchin.")
        ), DecoderSplit.AUDIT)
        require(train.pairs.none { t -> dev.pairs.any { it.conditionerText() == t.conditionerText() } })
        val condition: (DecoderEvidencePair) -> DoubleArray = { encoder.representation(it.conditionerText()) }
        val model = TinyContextDecoder(91)
        val initial = model.save()
        val report = JSONObject().put("schema", "CDX1_FIXED_GENERATION_PROBE_V1")
            .put("sourceCommit", System.getenv("CONTINUUM_PROBE_SOURCE_SHA") ?: "UNPINNED")
            .put("encoderSha256", hash(swbBytes)).put("parameterCount", model.parameterCount)
            .put("contextBytes", TinyContextDecoder.CONTEXT).put("byteBudget", 48)
            .put("data", "Existing app synthetic pair TRAIN and consumed legacy DEV; independent TEST not used for training")
            .put("status", "TRAINING_FIXED_PROTOCOL")
        val stages = JSONArray(); report.put("stages", stages)
        fun save() = File(out, "report.json").writeText(report.toString(2) + "\n")
        fun observe(m: TinyContextDecoder, e: String, q: String, expected: Set<String>): JSONObject {
            val before = m.save()
            val g = m.generate(decoderEvidencePrefix(e, q), 48, encoder.representation(e + "\n" + q))
            check(before.contentEquals(m.save()))
            val stop = try { g.javaClass.getMethod("getStopReason").invoke(g).toString() }
                catch (_: NoSuchMethodException) { "LEGACY_NO_REASON" }
            return JSONObject().put("evidence", e).put("question", q).put("text", g.text)
                .put("bytes", g.bytesGenerated).put("ended", g.ended).put("stopReason", stop)
                .put("acceptedCorrect", g.ended && g.text.trim() in expected)
        }
        for (target in listOf(0, 120, 600, 6000)) {
            if (target > model.updates) {
                val result = model.trainEvidencePairs(train, steps = target - model.updates,
                    conditionerFor = condition, maxMillis = 5000)
                if (result.interrupted || model.updates != target) {
                    report.put("status", "BUDGET_INCOMPLETE"); save()
                    error("Fixed training budget interrupted; never claim completed steps")
                }
            }
            File(out, "stage-$target.cdx.base64").writeText(Base64.getEncoder().encodeToString(model.save()) + "\n")
            val row = JSONObject().put("updates", model.updates).put("sha256", hash(model.save()))
                .put("trainLoss", model.evaluateEvidencePairs(train, condition))
                .put("devLoss", model.evaluateEvidencePairs(dev, condition))
            for ((name, corpus) in listOf("train" to train, "dev" to dev)) {
                val responses = JSONArray()
                corpus.pairs.forEach { responses.put(observe(model, it.evidence, it.question, setOf(it.answer))) }
                row.put(name, responses)
            }
            stages.put(row); save()
        }
        // Freeze final weights before the evaluator's labels are opened.
        val frozen = model.save()
        report.put("finalSha256", hash(frozen))
        val fixtureBytes = File(args[2]).readBytes()
        report.put("independentFixtureSha256", hash(fixtureBytes))
        val probes = JSONObject(fixtureBytes.toString(Charsets.UTF_8)).getJSONArray("probes")
        val test = JSONArray()
        for (i in 0 until probes.length()) {
            val p = probes.getJSONObject(i)
            val accepted = p.getJSONArray("acceptedAnswers")
            val answers = (0 until accepted.length()).map { accepted.getString(it) }.toSet()
            val r = observe(model, p.getString("evidence"), p.getString("question"), answers)
            r.put("id", p.getString("id")).put("reference", p.getString("reference"))
                .put("strictCorrect", r.getBoolean("ended") && r.getString("text").trim() == p.getString("reference"))
                .put("supported", p.getBoolean("supported"))
            test.put(r)
        }
        check(frozen.contentEquals(model.save()))
        check(hash(swbBytes) == report.getString("encoderSha256"))
        check(!initial.contentEquals(frozen))
        report.put("independentTest", test).put("status", "MEASURED_RESEARCH_ONLY")
        report.put("ownerWeightsModified", false).put("productionPromotion", false)
        save(); println(report.toString(2))
    }
}
