package at.heinz.deviceai.learning.span

import java.io.File
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

object Cdx2PointerDevHarness {
    private fun source(text: String) = EvidenceBoundNaturalLocationProbe.Evidence("DEV-copy", 7,
        EvidenceBoundNaturalLocationProbe.digest(text), text)
    @JvmStatic fun main(args: Array<String>) {
        if(args.firstOrNull()=="evaluate") {
            require(args.size==4);evaluate(args[1],args[2],args[3]);return
        }
        require(args.size==1) { "CHECKPOINT.json | evaluate CHECKPOINT.json CORPUS.json OUT.json" }
        val weights=if(args[0]=="red")DoubleArray(11) else Cdx2PointerCandidate.load(args[0])
        // Catches a selector that emits a fixed value or never copies changed evidence.
        val a = Cdx2PointerCandidate.raw(weights, "Wo ist Na-Liva?", listOf(source("Na-Liva ist in Te-Burg.")))
        val b = Cdx2PointerCandidate.raw(weights, "Wo ist Na-Liva?", listOf(source("Na-Liva ist in Te-Tal.")))
        check(a?.text == "Te-Burg" && b?.text == "Te-Tal" && a.text != b.text) {
            "RED_COPY_AND_CHANGED_VALUE: expected Te-Burg then Te-Tal; got ${a?.text} then ${b?.text}"
        }
        var checks=1
        fun must(label:String, value:Boolean) {check(value){label};checks++}
        val s=source("🧪; Na-Liva ist in Te-Burg.")
        val found=Cdx2PointerCandidate.raw(weights,"Wo ist Na-Liva?",listOf(s))!!
        must("UTF16 exact offsets", found.text=="Te-Burg" && found.start==19 && found.endExclusive==26)
        must("identity intact",found.sourceId=="DEV-copy" && found.revision==7 && found.sha256==s.sha256)
        must("verifier accepts real tuple",Cdx2PointerCandidate.verified("Wo ist Na-Liva?",listOf(s),found))
        must("snapshot immutable",s.text=="🧪; Na-Liva ist in Te-Burg." && s.revision==7)
        must("tampered hash raw abstention",Cdx2PointerCandidate.raw(weights,"Wo ist Na-Liva?",listOf(s.copy(text=s.text.replace("Burg","Dorf"))))==null)
        must("source revision invalid",Cdx2PointerCandidate.raw(weights,"Wo ist Na-Liva?",listOf(s.copy(revision=0)))==null)
        must("duplicate source id invalid",Cdx2PointerCandidate.raw(weights,"Wo ist Na-Liva?",listOf(s,s))==null)
        must("stale revision rejects",!Cdx2PointerCandidate.verified("Wo ist Na-Liva?",listOf(s.copy(revision=8)),found))
        must("offset mutation rejects",!Cdx2PointerCandidate.verified("Wo ist Na-Liva?",listOf(s),found.copy(start=18)))
        must("false source rejects",!Cdx2PointerCandidate.verified("Wo ist Na-Liva?",listOf(s),found.copy(sourceId="DEV-other")))
        val t=source("Na-Liva ist in Te-Burg, Li-Vara ist in Te-Tal.")
        val u=source("Na-Liva ist in Te-Tal, Li-Vara ist in Te-Burg.")
        must("swap role binding",Cdx2PointerCandidate.raw(weights,"Wo ist Li-Vara?",listOf(t))?.text=="Te-Tal" &&
            Cdx2PointerCandidate.raw(weights,"Wo ist Li-Vara?",listOf(u))?.text=="Te-Burg")
        val negatives=listOf("Na-Liva ist nicht in Te-Burg.","Na-Liva ist heute in Te-Burg.",
            "Na-Liva ist in Te-Burg und Te-Tal.","Na-Liva ist in Te-Burg. Widerruf.","Na-Liva ist in Te-Burg?",
            "\"Na-Liva ist in Te-Burg\".","Na-Liva ist in Te-Burg. Er ist in Te-Tal.")
        for(text in negatives)must("negative/time/quotation control: $text",Cdx2PointerCandidate.raw(weights,"Wo ist Na-Liva?",listOf(source(text)))==null)
        must("unknown entity",Cdx2PointerCandidate.raw(weights,"Wo ist Li-Vara?",listOf(source("Na-Liva ist in Te-Burg.")))==null)
        must("unknown relation",Cdx2PointerCandidate.raw(weights,"Wo arbeitet Na-Liva?",listOf(source("Na-Liva ist in Te-Burg.")))==null)
        val sample=Cdx2PointerSynthetic.corpus("TRAIN",17,20).first()
        val options=Cdx2PointerCandidate.options(sample.question,sample.sources)
        must("wrong model width rejected",runCatching {Cdx2PointerCandidate.probabilities(DoubleArray(10),options)}.isFailure)
        must("nonfinite model rejected",runCatching {Cdx2PointerCandidate.probabilities(DoubleArray(11){Double.NaN},options)}.isFailure)
        must("overflowing model rejected",runCatching {Cdx2PointerCandidate.probabilities(DoubleArray(11){Double.MAX_VALUE},options)}.isFailure)
        val w=DoubleArray(11){(it-5)*.03};val (loss,g)=Cdx2PointerCandidate.lossGradient(w,options,sample.expected)
        must("finite loss",loss.isFinite())
        for(k in w.indices){val plus=w.copyOf();val minus=w.copyOf();plus[k]+=1e-5;minus[k]-=1e-5
            val numeric=(Cdx2PointerCandidate.lossGradient(plus,options,sample.expected).first-
                Cdx2PointerCandidate.lossGradient(minus,options,sample.expected).first)/2e-5
            must("gradient finite difference $k",abs(numeric-g[k])<1e-7)}
        val train=Cdx2PointerSynthetic.corpus("TRAIN",17,240)
        must("training lowers classifier NLL",Cdx2PointerCandidate.nll(weights,train)<Cdx2PointerCandidate.nll(DoubleArray(11),train)*.2)
        val trained=Cdx2PointerCandidate.train(train,60,.15)
        must("deterministic SGD",trained.contentEquals(Cdx2PointerCandidate.train(train,60,.15)))
        println("PASS $checks behavioral/gradient/immutability controls; classifier NLL, no byte generation")
    }
    private fun evaluate(checkpoint:String,corpus:String,out:String) {
        require(!File(out).exists());val w=Cdx2PointerCandidate.load(checkpoint);val data=Cdx2PointerCandidate.readExamples(corpus)
        var rawOk=0;var verifiedOk=0;var ruleOk=0;var missing=0;var rawAbstain=0;var unsafe=0;var falseSource=0
        val rows=JSONArray();val latencies=mutableListOf<Double>()
        for(e in data){val start=System.nanoTime();val raw=Cdx2PointerCandidate.raw(w,e.question,e.sources)
            val ms=(System.nanoTime()-start)/1_000_000.0;latencies+=ms
            val accepted=Cdx2PointerCandidate.verified(e.question,e.sources,raw);val verified=if(accepted)raw else null
            val rule=EvidenceBoundNaturalLocationProbe.answer(e.question,e.sources) as? EvidenceBoundNaturalLocationProbe.Decision.Found
            if(raw==e.expected)rawOk++;if(verified==e.expected)verifiedOk++;if(rule==e.expected)ruleOk++
            if(e.expected==null){missing++;if(raw==null)rawAbstain++ else unsafe++}
            if(raw!=null && (e.expected==null || raw.sourceId!=e.expected.sourceId || raw.revision!=e.expected.revision || raw.sha256!=e.expected.sha256))falseSource++
            rows.put(JSONObject().put("id",e.id).put("question",e.question).put("expected",Cdx2PointerCandidate.tuple(e.expected))
                .put("RAW",Cdx2PointerCandidate.tuple(raw)).put("VERIFIED",Cdx2PointerCandidate.tuple(verified)).put("RULE",Cdx2PointerCandidate.tuple(rule))
                .put("rawExactTuple",raw==e.expected).put("verifierAccepted",accepted).put("rawMillis",ms)
                .put("classifierNll",Cdx2PointerCandidate.lossGradient(w,Cdx2PointerCandidate.options(e.question,e.sources),e.expected).first))
        }
        latencies.sort();File(out).writeText(JSONObject().put("schema","CDX2_DEV_EVALUATION_V1").put("items",data.size)
            .put("rawExactTuple",rawOk).put("verifiedExactTuple",verifiedOk).put("ruleExactTuple",ruleOk)
            .put("supportedItems",data.size-missing).put("missingItems",missing).put("correctRawAbstentions",rawAbstain)
            .put("unsupportedRawPositive",unsafe).put("falseSourceAssignments",falseSource).put("classifierNll",Cdx2PointerCandidate.nll(w,data))
            .put("rawP50Millis",latencies[latencies.size/2]).put("rawP95Millis",latencies[(latencies.size*.95).toInt().coerceAtMost(latencies.lastIndex)])
            .put("parameterCount",11).put("checkpointBytes",File(checkpoint).length()).put("cases",rows).toString(2)+"\n")
        println("DEV evaluate: raw=$rawOk/${data.size} verified=$verifiedOk rule=$ruleOk abstain=$rawAbstain/$missing unsafe=$unsafe falseSource=$falseSource")
    }
}
