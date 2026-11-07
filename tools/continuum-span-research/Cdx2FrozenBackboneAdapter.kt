package at.heinz.deviceai.learning.span

import java.io.File
import java.util.Random
import org.json.JSONArray
import org.json.JSONObject

/** Two trainable collision weights only. Eleven existing checkpoint weights stay bit-identical.
 * Read-only base checkpoint, isolated shadow outputs; no productive import or promotion. */
object Cdx2FrozenBackboneAdapter {
    fun train(base: DoubleArray, examples: List<Cdx2PointerCandidate.Example>, seed: Long): DoubleArray {
        require(base.size==11 && examples.size in 1..512)
        val w=base.copyOf(13); val rng=Random(seed); val order=examples.indices.toMutableList()
        val data=examples.map { Cdx2CounterfactualShadow.options(it.question,it.sources) to it.expected }
        repeat(60) {
            java.util.Collections.shuffle(order,rng)
            for(i in order) {
                val (os,gold)=data[i];val y=os.indexOfFirst{it.found==gold};require(y>=0)
                val p=Cdx2CounterfactualShadow.probabilities(w,os)
                val gradient=DoubleArray(2){j-> val k=j+11
                    os.indices.sumOf{p[it]*os[it].features[k]}-os[y].features[k] }
                for(j in 0..1)w[j+11]-=.05*gradient[j]
            }
        }
        check((0..10).all{w[it].toBits()==base[it].toBits()})
        return w
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size==6 && args[0]=="run") {"run TRAIN VAL LABEL_FREE_REQUESTS FROZEN_BASE FRESH_OUTDIR"}
        val train=Cdx2PointerCandidate.readExamples(args[1]);val validation=Cdx2PointerCandidate.readExamples(args[2])
        val requests=JSONObject(File(args[3]).readText()).getJSONArray("cases")
        val base=Cdx2PointerCandidate.load(args[4]);val out=File(args[5]);require(!out.exists()&&out.mkdirs())
        val report=JSONObject().put("promotion","FORBIDDEN_RESEARCH_ONLY").put("trainableParameters",2)
            .put("frozenParameters",11).put("epochs",60).put("rate",.05).put("trials",JSONArray())
        fun predictions(w:DoubleArray):JSONObject {
            val rows=JSONArray();for(i in 0 until requests.length())rows.put(Cdx2CounterfactualShadow.predict(w,requests.getJSONObject(i)))
            return JSONObject().put("cases",rows)
        }
        for(seed in listOf(17L,29L)) {
            val start=System.nanoTime();val w=train(base,train,seed);val ms=(System.nanoTime()-start)/1e6
            val checkpoint=JSONObject().put("schema","CDX2_FROZEN_BASE_ADAPTER_V1").put("weights",JSONArray(w.toList()))
                .put("baseSha256",EvidenceBoundNaturalLocationProbe.digest(File(args[4]).readText())).put("seed",seed)
                .put("frozenFirstEleven",true).put("promotion","FORBIDDEN_RESEARCH_ONLY")
            File(out,"checkpoint-$seed.json").writeText(checkpoint.toString(2)+"\n")
            File(out,"predictions-$seed.json").writeText(predictions(w).toString(2)+"\n")
            File(out,"ablation-predictions-$seed.json").writeText(predictions(base.copyOf(13)).toString(2)+"\n")
            File(out,"ablation-checkpoint-$seed.json").writeText(JSONObject().put("weights",JSONArray(base.copyOf(13).toList())).toString(2)+"\n")
            report.getJSONArray("trials").put(JSONObject().put("seed",seed).put("trainMillis",ms)
                .put("trainNllBefore",Cdx2CounterfactualShadow.nll(base.copyOf(13),train))
                .put("trainNllAfter",Cdx2CounterfactualShadow.nll(w,train))
                .put("validationNll",Cdx2CounterfactualShadow.nll(w,validation)))
        }
        File(out,"training-report.json").writeText(report.toString(2)+"\n")
    }
}
