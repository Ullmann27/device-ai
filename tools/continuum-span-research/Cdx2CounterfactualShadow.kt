package at.heinz.deviceai.learning.span

import java.io.File
import java.util.Random
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

/** Research only. Reuses frozen CDX2 positional candidates, adds two learned role-collision
 * features. This is log-linear span classification with hand-engineered grammar, not CDX1 repair.
 * Training never reads the holdout. Predict receives only id/question/sources, never expected. */
object Cdx2CounterfactualShadow {
    private val query = Regex("""(?iu)^\s*wo\s+(?:ist|wohnt|lebt|steht)\s+([\p{L}][\p{L}\p{M}-]{1,31})\s*\?\s*$""")
    fun options(question: String, sources: List<EvidenceBoundNaturalLocationProbe.Evidence>): List<Cdx2PointerCandidate.Option> {
        val original = Cdx2PointerCandidate.options(question, sources)
        val subject = query.matchEntire(question)?.groupValues?.get(1)
        val binding = subject?.let { Regex("""(?iu)(?<![\p{L}\p{M}\p{N}-])""" + Regex.escape(it) +
            """\s+(?:ist|wohnt|lebt|steht)\s+in\b""") }
        val collision = binding != null && sources.sumOf { binding.findAll(it.text).count() } > 1
        return original.map { o -> Cdx2PointerCandidate.Option(o.found, o.features.copyOf(13).also {
            it[11] = if (collision && o.found != null) 1.0 else 0.0
            it[12] = if (collision && o.found == null) 1.0 else 0.0
        }) }
    }
    // Same finite bounded softmax, generalized to the shadow's13 parameters only.
    fun probabilities(w: DoubleArray, os: List<Cdx2PointerCandidate.Option>): DoubleArray {
        require(w.size == 13 && w.all { it.isFinite() && kotlin.math.abs(it) <= 100 })
        val scores = os.map { o -> w.indices.sumOf { w[it] * o.features[it] } }
        val m = scores.max(); val p = scores.map { kotlin.math.exp(it-m) }; val z = p.sum()
        return DoubleArray(p.size) { p[it]/z }
    }
    fun train(examples: List<Cdx2PointerCandidate.Example>, seed: Long, collisionFeatures: Boolean = true): DoubleArray {
        require(examples.size in 1..512)
        val dataset = examples.map { e -> options(e.question,e.sources).map { o ->
            if(collisionFeatures)o else Cdx2PointerCandidate.Option(o.found,o.features.copyOf().also { it[11]=0.0;it[12]=0.0 })
        } to e.expected }
        val rng = Random(seed); val w = DoubleArray(13) { rng.nextGaussian()*.01 }
        if(!collisionFeatures){w[11]=0.0;w[12]=0.0}
        val order = dataset.indices.toMutableList()
        repeat(60) {
            java.util.Collections.shuffle(order,rng)
            for (i in order) {
                val (os,expected)=dataset[i]; val y=os.indexOfFirst { it.found==expected }; require(y>=0)
                val p=probabilities(w,os)
                val gradient=DoubleArray(13) { k -> os.indices.sumOf { p[it]*os[it].features[k] }-os[y].features[k] }
                for (k in w.indices) w[k]-=.05*gradient[k]
            }
        }
        return w
    }
    fun nll(w: DoubleArray, examples: List<Cdx2PointerCandidate.Example>): Double = examples.map { e ->
        val os=options(e.question,e.sources); val y=os.indexOfFirst { it.found==e.expected }; require(y>=0)
        -ln(probabilities(w,os)[y].coerceAtLeast(1e-300))
    }.average()
    fun predict(w: DoubleArray, request: JSONObject): JSONObject {
        require(!request.has("expected")) { "gold forbidden in prediction input" }
        val q=request.getString("question"); val ss=Cdx2PointerCandidate.sources(request.getJSONArray("sources"))
        val os=options(q,ss); val p=probabilities(w,os); val f=os[p.indices.maxBy { p[it] }].found
        val accepted=Cdx2PointerCandidate.verified(q,ss,f)
        val rule=EvidenceBoundNaturalLocationProbe.answer(q,ss) as? EvidenceBoundNaturalLocationProbe.Decision.Found
        return JSONObject().put("id",request.getString("id")).put("RAW",Cdx2PointerCandidate.tuple(f))
            .put("VERIFIED",Cdx2PointerCandidate.tuple(if(accepted)f else null)).put("RULE",Cdx2PointerCandidate.tuple(rule))
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size==5 && args[0]=="run") { "run TRAIN VALIDATION LABEL_FREE_REQUESTS FRESH_OUTDIR" }
        val out=File(args[4]); require(!out.exists()); require(out.mkdirs())
        val train=Cdx2PointerCandidate.readExamples(args[1]); val validation=Cdx2PointerCandidate.readExamples(args[2])
        val requests=JSONObject(File(args[3]).readText()).getJSONArray("cases")
        val report=JSONObject().put("promotion","FORBIDDEN_RESEARCH_ONLY").put("parameters",13)
            .put("selection","fixed60epochs/rate0.05/seeds17,29; no holdout tuning").put("trials",JSONArray())
        for(seed in listOf(17L,29L)) {
            val start=System.nanoTime(); val w=train(train,seed)
            val checkpoint=JSONObject().put("schema","CDX2_COUNTERFACTUAL_SHADOW_V1").put("seed",seed)
                .put("weights",JSONArray(w.toList())).put("promotion","FORBIDDEN_RESEARCH_ONLY")
            File(out,"checkpoint-$seed.json").writeText(checkpoint.toString(2)+"\n")
            val trained=(System.nanoTime()-start)/1e6
            val ablated=train(train,seed,collisionFeatures=false)
            val ablatedRows=JSONArray()
            for(i in 0 until requests.length())ablatedRows.put(predict(ablated,requests.getJSONObject(i)))
            File(out,"ablation-predictions-$seed.json").writeText(JSONObject().put("cases",ablatedRows).toString(2)+"\n")
            File(out,"ablation-checkpoint-$seed.json").writeText(JSONObject().put("weights",JSONArray(ablated.toList())).put("collisionFeatures",false).toString(2)+"\n")

            val predictions=JSONArray(); val predictStart=System.nanoTime()
            for(i in 0 until requests.length()) predictions.put(predict(w,requests.getJSONObject(i)))
            val predictMs=(System.nanoTime()-predictStart)/1e6
            File(out,"predictions-$seed.json").writeText(JSONObject().put("cases",predictions).toString(2)+"\n")
            report.getJSONArray("trials").put(JSONObject().put("seed",seed).put("trainClassifierNll",nll(w,train))
                .put("validationClassifierNll",nll(w,validation)).put("trainMillis",trained)
                .put("batchPredictMillis",predictMs).put("checkpointBytes",File(out,"checkpoint-$seed.json").length()))
        }
        File(out,"training-report.json").writeText(report.toString(2)+"\n")
    }
}
