package at.heinz.deviceai.learning

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

/** Caller-side research transform. Parameters fit only the authored TRAIN inputs,
 * never DEV/TEST answers, and do not mutate or retrain the frozen SWB1 encoder.
 */
object Cdx1ResearchConditioning {
    fun transform(arm: String, encoder: TrainableSubwordEncoder,
                  train: List<DecoderEvidencePair>): (DecoderEvidencePair) -> DoubleArray {
        val mean = DoubleArray(16)
        train.forEach { p -> encoder.representation(p.conditionerText()).forEachIndexed { i, x -> mean[i] += x / train.size } }
        require(arm in listOf("raw", "unit_l2", "train_mean_centered_x32"))
        return { p ->
            val c = encoder.representation(p.conditionerText())
            when (arm) {
                "raw" -> c
                "unit_l2" -> {
                    val norm = sqrt(c.sumOf { it * it }).coerceAtLeast(1e-12)
                    c.map { (it / norm).coerceIn(-1.0, 1.0) }.toDoubleArray()
                }
                else -> c.mapIndexed { i, x -> ((x - mean[i]) * 32).coerceIn(-1.0, 1.0) }.toDoubleArray()
            }
        }
    }
    fun pairs(array: JSONArray) = (0 until array.length()).map { i ->
        val p = array.getJSONObject(i)
        DecoderEvidencePair(p.getString("evidence"), p.getString("question"), p.getString("answer"))
    }
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Fixed matched-budget comparison. All outputs/weights are retained, including
 * failed controls. This does not attach an alternative conditioner to the app.
 */
object Cdx1ConditionerRepairProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4) { "SWB1_BASE64 PROTOCOL_JSON NEW_OUTPUT_DIR SOURCE_SHA" }
        require(args[3].matches(Regex("[a-f0-9]{40}")))
        val out = File(args[2]); require(!out.exists()); check(out.mkdirs())
        val swb = Base64.getDecoder().decode(File(args[0]).readText().trim())
        val encoder = TrainableSubwordEncoder.restore(swb)
        val protocolBytes = File(args[1]).readBytes()
        val protocol = JSONObject(protocolBytes.toString(Charsets.UTF_8))
        val train = Cdx1ResearchConditioning.pairs(protocol.getJSONArray("train"))
        val trainCorpus = DecoderEvidenceCorpus(train, DecoderSplit.TRAIN)
        val dev = Cdx1ResearchConditioning.pairs(protocol.getJSONArray("dev"))
        val arms = protocol.getJSONArray("arms")
        val stages = protocol.getJSONArray("stages")
        val report = JSONObject().put("schema", "CDX1_CONDITIONER_REPAIR_RESULT_V1")
            .put("sourceCommit", args[3]).put("protocolSha256", Cdx1ResearchConditioning.hash(protocolBytes))
            .put("encoderSha256", Cdx1ResearchConditioning.hash(swb)).put("arms", JSONArray())
            .put("ownerWeightsModified", false).put("productionPromotion", false)
            .put("status", "FIXED_PROTOCOL_RUNNING")
        fun save() = File(out, "report.json").writeText(report.toString(2) + "\n")
        for (a in 0 until arms.length()) {
            val arm = arms.getString(a)
            val condition = Cdx1ResearchConditioning.transform(arm, encoder, train)
            val model = TinyContextDecoder(protocol.getLong("seed"))
            val armOut = File(out, arm); check(armOut.mkdir())
            val armRow = JSONObject().put("name", arm).put("stages", JSONArray())
            report.getJSONArray("arms").put(armRow)
            for (s in 0 until stages.length()) {
                val target = stages.getInt(s)
                val start = System.nanoTime()
                while (model.updates < target) {
                    val remaining = (target - model.updates).coerceAtMost(20000)
                    val r = model.trainEvidencePairs(trainCorpus, steps = remaining,
                        learningRate = protocol.getDouble("learningRate"), conditionerFor = condition, maxMillis = 5000)
                    if (r.interrupted || r.steps != remaining) {
                        report.put("status", "BUDGET_INCOMPLETE"); save(); error("Fixed update budget interrupted")
                    }
                }
                val snapshot = model.save()
                check(TinyContextDecoder.restore(snapshot).save().contentEquals(snapshot))
                File(armOut, "stage-$target.cdx.base64").writeText(Base64.getEncoder().encodeToString(snapshot) + "\n")
                val row = JSONObject().put("updates", target).put("sha256", Cdx1ResearchConditioning.hash(snapshot))
                    .put("trainingMillis", (System.nanoTime() - start) / 1e6)
                    .put("trainLoss", model.evaluateEvidencePairs(trainCorpus, condition))
                for ((name, pairs) in listOf("train" to train, "dev" to dev)) {
                    val outputs = JSONArray()
                    pairs.forEachIndexed { i, p ->
                        val g = model.generate(p.prefix(), protocol.getInt("byteBudget"), condition(p))
                        val input = protocol.getJSONArray(name).getJSONObject(i)
                        outputs.put(JSONObject().put("id", input.getString("id"))
                            .put("stratum", input.optString("stratum", "train"))
                            .put("evidence", p.evidence).put("question", p.question).put("expected", p.answer)
                            .put("text", g.text).put("ended", g.ended).put("bytes", g.bytesGenerated)
                            .put("exactCorrect", g.ended && g.text.trim() == p.answer))
                    }
                    row.put(name, outputs)
                }
                row.put("devLoss", model.evaluateEvidencePairs(DecoderEvidenceCorpus(dev, DecoderSplit.AUDIT), condition))
                check(model.save().contentEquals(snapshot))
                armRow.getJSONArray("stages").put(row); save()
                println("$arm/$target: train=" + row.getJSONArray("train").let { x -> (0 until x.length()).count { x.getJSONObject(it).getBoolean("exactCorrect") } } + "/4 dev=" + row.getJSONArray("dev").let { x -> (0 until x.length()).count { x.getJSONObject(it).getBoolean("exactCorrect") } } + "/${dev.size}")
            }
        }
        report.put("status", "MEASURED_HOST_RESEARCH_ONLY"); save()
    }
}

/** Behavioral RED/GREEN check: the original collapsed snapshot fails3/4; the
 * proposed repair must actually generate the4 authored answers through EOS.
 */
object Cdx1GenerationQualityRegression {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 4) { "SWB1_BASE64 PROTOCOL_JSON CDX1_BASE64 ARM" }
        val swb = Base64.getDecoder().decode(File(args[0]).readText().trim())
        val modelBytes = Base64.getDecoder().decode(File(args[2]).readText().trim())
        val encoder = TrainableSubwordEncoder.restore(swb)
        val protocol = JSONObject(File(args[1]).readText())
        val pairs = Cdx1ResearchConditioning.pairs(protocol.getJSONArray("train"))
        val condition = Cdx1ResearchConditioning.transform(args[3], encoder, pairs)
        val model = TinyContextDecoder.restore(modelBytes)
        val wrong = pairs.mapNotNull { p ->
            val g = model.generate(p.prefix(), protocol.getInt("byteBudget"), condition(p))
            if (g.ended && g.text.trim() == p.answer) null else "${p.question}: expected=${p.answer}, actual=${g.text}, EOS=${g.ended}"
        }
        check(model.save().contentEquals(modelBytes))
        check(wrong.isEmpty()) { "Free generation regression (${wrong.size}/${pairs.size} wrong):\n" + wrong.joinToString("\n") }
        println("PASS:4/4 authored answers freely generated with EOS; model unchanged")
    }
}
