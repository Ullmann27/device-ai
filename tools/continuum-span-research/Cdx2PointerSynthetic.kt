package at.heinz.deviceai.learning.span

import java.io.File
import java.util.Random
import org.json.JSONArray
import org.json.JSONObject

/** Author-defined development data, never a blind benchmark. Fixed TRAIN=17 and DEV=29. */
object Cdx2PointerSynthetic {
    fun source(id: String, revision: Int, text: String) = EvidenceBoundNaturalLocationProbe.Evidence(id, revision,
        EvidenceBoundNaturalLocationProbe.digest(text), text)
    fun corpus(split: String, seed: Long, size: Int): List<Cdx2PointerCandidate.Example> {
        require((split == "TRAIN" && seed == 17L) || (split == "DEV" && seed == 29L))
        val rng = Random(seed)
        val names = if (split == "TRAIN") listOf("Na-Liva","Na-Toma","Na-Rina","Na-Keva","Na-Jaro","Na-Sina")
            else listOf("Li-Vara","Li-Miko","Li-Dena","Li-Pavo","Li-Sora","Li-Niko")
        val places = if(split == "TRAIN") listOf("Te-Burg","Te-Tal","Te-See","Te-Feld","Te-Dorf","Te-Park")
            else listOf("Te-Neuland","Te-Ufer","Te-Gipfel","Te-Hafen","Te-Wald","Te-Insel")
        val relations = listOf("ist","wohnt","lebt","steht")
        return (0 until size).map { i ->
            val a = names[rng.nextInt(names.size)]
            val b = names[(names.indexOf(a)+1+rng.nextInt(names.size-1))%names.size]
            val x = places[rng.nextInt(places.size)] + if(i%7==0) " Te-Nord" else ""
            val y = places[(places.indexOf(x.substringBefore(' '))+1)%places.size]
            val r = relations[rng.nextInt(4)]; var question = "Wo $r $a?"
            var answer: String? = x; var sourceIndex = 0
            val texts = when(i%20) {
                0 -> listOf("$a $r in $x.")
                1 -> listOf("$b $r in $y, $a $r in $x.")
                2 -> listOf("$a $r in $x und $b $r in $y.")
                3 -> listOf("$b $r in $y.","$a $r in $x.").also { sourceIndex=1 }
                4 -> listOf("$a $r in $x; $b $r in $y.")
                5 -> listOf("  $a $r in $x.  ")
                6 -> listOf("$a $r in $x.","$b $r in $y.")
                7 -> listOf("$a $r in $x, $b $r in $y.")
                8 -> listOf("$a $r in $x.").also { question="Wo $r $b?"; answer=null }
                9 -> listOf("$a $r nicht in $x.").also { answer=null }
                10 -> listOf("$a $r heute in $x.").also { answer=null }
                11 -> listOf("$a $r in $x.","$a $r in $y.").also { answer=null }
                12 -> listOf("$a $r in $x.").also { question="Was macht $a?";answer=null }
                13 -> listOf("\"$a $r in $x\".").also { answer=null }
                14 -> listOf("$a $r in $x. Widerruf.").also { answer=null }
                15 -> listOf("$a $r in $x?").also { answer=null }
                16 -> listOf("$a $r in $x und $y.").also { answer=null }
                17 -> listOf("$a $r in $x.").also { question="Wo ${relations[(relations.indexOf(r)+1)%4]} $a?";answer=null }
                18 -> listOf("$a $r in $x. Er $r in $y.").also { answer=null }
                else -> listOf("🧪; $a $r in $x.")
            }
            val ss = texts.mapIndexed { s,t -> source("$split-$i-$s",1+rng.nextInt(8),t) }
            val gold = answer?.let { v -> val s=ss[sourceIndex];val start=s.text.indexOf(v)
                check(start>=0); EvidenceBoundNaturalLocationProbe.Decision.Found(v,s.sourceId,s.revision,s.sha256,start,start+v.length) }
            Cdx2PointerCandidate.Example("$split-$i",question,ss,gold)
        }
    }
    fun json(e: Cdx2PointerCandidate.Example): JSONObject = JSONObject().put("id",e.id).put("question",e.question)
        .put("sources",JSONArray(e.sources.map { JSONObject().put("sourceId",it.sourceId).put("revision",it.revision)
            .put("text",it.text).put("sha256",it.sha256) })).put("expected",Cdx2PointerCandidate.tuple(e.expected))
    @JvmStatic fun main(args: Array<String>) {
        require(args.size==1); val dir=File(args[0]);dir.mkdirs()
        for ((split,seed,size) in listOf(Triple("TRAIN",17L,240),Triple("DEV",29L,100))) {
            val out=File(dir,"$split.json");require(!out.exists())
            out.writeText(JSONObject().put("schema","CDX2_AUTHOR_SYNTHETIC_V1").put("split",split).put("seed",seed)
                .put("blind",false).put("cases",JSONArray(corpus(split,seed,size).map(::json))).toString(2)+"\n")
        }
    }
}
