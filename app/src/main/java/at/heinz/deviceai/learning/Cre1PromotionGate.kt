package at.heinz.deviceai.learning

import java.io.File
import java.util.Locale
import java.util.Properties

/**
 * CRE1 is allowed to reorder ONLY the already-eligible SWB1 top candidates.
 * No ungrounded fact, new evidence ID, tool authorization or source trust can
 * be created here. Model training steps are NOT evidence of retrieval quality.
 *
 * The local promotion manifest is a benchmark report, not a cryptographic
 * attestation. Its provenance and sealed test split must be reviewed separately.
 */
data class Cre1PromotionManifest(
    val modelSha256:String,
    val modelSteps:Int,
    val auditSha256:String,
    val sampleCount:Int,
    val negativeCount:Int,
    val swb1Correct:Int,
    val hybridCorrect:Int,
    val baselineFalseAttributions:Int,
    val hybridFalseAttributions:Int,
    val negativeFalseAnswers:Int,
    val pairedPValue:Double,
    val fusionWeight:Double
) {
    fun qualified(actualSha256:String,actualSteps:Int):Boolean {
        val hash=Regex("[a-f0-9]{64}")
        return hash.matches(modelSha256) && modelSha256==actualSha256 &&
            hash.matches(auditSha256) && auditSha256!="0".repeat(64) &&
            modelSteps==actualSteps && modelSteps>=25 &&
            sampleCount>=200 && negativeCount>=50 &&
            swb1Correct in 0..sampleCount && hybridCorrect in 0..sampleCount &&
            hybridCorrect-swb1Correct>=kotlin.math.ceil(sampleCount*0.05).toInt() &&
            baselineFalseAttributions in 0..sampleCount &&
            hybridFalseAttributions==0 && negativeFalseAnswers==0 &&
            pairedPValue.isFinite() && pairedPValue in 0.0..0.05 &&
            fusionWeight.isFinite() && fusionWeight in 0.05..0.5
    }
}

object Cre1PromotionGate {
    private val numbers=Regex("""\d+(?:[.,]\d+)*""")
    private val words=Regex("""[\p{L}\p{N}]+""")
    private val negations=setOf("nicht","kein","keine","keinen","keinem",
        "keiner","keines","nie","niemals","ohne")
    private val questionWords=setOf("wo","was","wer","wann","wie","warum","welche",
        "welcher","welches","hat","haben","ist","sind","kann","mit","ich",
        "gibt","denn","bitte","über","und","für","der","die","das")

    /** Loading never changes model or user memory. Fail closed for all errors. */
    fun readQualified(file:File,sha256:String,steps:Int):Cre1PromotionManifest? {
        if(!file.isFile || file.length() !in 40..2048)return null
        return runCatching {
            val props=Properties()
            file.inputStream().buffered().use { props.load(it) }
            val keys=setOf("schema","modelSha256","modelSteps","auditSha256",
                "sampleCount","negativeCount","swb1Correct","hybridCorrect",
                "baselineFalseAttributions","hybridFalseAttributions",
                "negativeFalseAnswers","pairedPValue","fusionWeight")
            require(props.stringPropertyNames()==keys && props.getProperty("schema")=="1")
            val result=Cre1PromotionManifest(
                props.getProperty("modelSha256"),
                props.getProperty("modelSteps").toInt(),
                props.getProperty("auditSha256"),
                props.getProperty("sampleCount").toInt(),
                props.getProperty("negativeCount").toInt(),
                props.getProperty("swb1Correct").toInt(),
                props.getProperty("hybridCorrect").toInt(),
                props.getProperty("baselineFalseAttributions").toInt(),
                props.getProperty("hybridFalseAttributions").toInt(),
                props.getProperty("negativeFalseAnswers").toInt(),
                props.getProperty("pairedPValue").toDouble(),
                props.getProperty("fusionWeight").toDouble()
            )
            result.takeIf { it.qualified(sha256,steps) }
        }.getOrNull()
    }

    /**
     * Candidate-set lock: only a permutation of the original grounded list
     * can be returned. Scores are clamped to a declared cosine interval and
     * missing/nonfinite scores force SWB1 selection.
     */
    fun rerank(
        query:String,
        swb1:List<ContinuumMemoryHit>,
        semanticById:Map<String,Double>,
        manifest:Cre1PromotionManifest,
        modelSha256:String,
        modelSteps:Int
    ):List<ContinuumMemoryHit> {
        if(!manifest.qualified(modelSha256,modelSteps) ||
            swb1.size !in 2..3 || swb1.map { it.id }.distinct().size!=swb1.size ||
            semanticById.keys!=swb1.map { it.id }.toSet() ||
            semanticById.values.any { !it.isFinite() || it !in -1.0..1.0 } ||
            swb1.any { !it.score.isFinite() || it.score !in 0.0..1.0 })
            return swb1
        val alpha=manifest.fusionWeight
        val mixed=swb1.map { hit ->
            hit to ((1.0-alpha)*hit.score+alpha*((semanticById.getValue(hit.id)+1.0)/2.0))
        }.sortedWith(compareByDescending<Pair<ContinuumMemoryHit,Double>>{it.second}
            .thenBy { it.first.id })
        val winner=mixed.first().first
        val original=swb1.first()
        if(winner.id==original.id)return swb1
        if(!preservesHardAnchors(query,original.text,winner.text))return swb1
        // Do not change ordering without a meaningful, numeric advantage.
        val topScore=mixed.first().second
        val originalScore=mixed.first { it.first.id==original.id }.second
        if(topScore-originalScore<0.02)return swb1
        return mixed.map { it.first }
    }

    internal fun preservesHardAnchors(query:String,top:String,candidate:String):Boolean {
        val lowerTop=top.lowercase(Locale.ROOT)
        val lowerCandidate=candidate.lowercase(Locale.ROOT)
        // Even without a literal number in the question, changing a number
        // compared with SWB1 Top-1 may change the meaning of the answer.
        if(numbers.findAll(lowerTop).map { it.value }.toSet()!=
            numbers.findAll(lowerCandidate).map { it.value }.toSet())return false
        fun neg(text:String)=words.findAll(text).map { it.value }
            .filter { it in negations }.toSet()
        if(neg(lowerTop)!=neg(lowerCandidate))return false
        // Conservatively freeze named entities AND German capitalized nouns.
        // A question about Leo's address must not silently switch Wien to
        // Graz just because both candidates mention the same owner name.
        fun titled(text:String)=words.findAll(text).map { it.value }
            .filter { it.length>=3 && it.first().isUpperCase() &&
                it.lowercase(Locale.ROOT) !in questionWords }.map {
                it.lowercase(Locale.ROOT)
            }.toSet()
        if(titled(top)!=titled(candidate))return false
        val topWords=words.findAll(lowerTop).map { it.value }.toSet()
        val candidateWords=words.findAll(lowerCandidate).map { it.value }.toSet()
        // Candidate must preserve specific query terms that SWB1 grounded.
        for(token in words.findAll(query).map { it.value.lowercase(Locale.ROOT) }) {
            if(token in topWords && token !in candidateWords)return false
        }
        // Names/title-cased entities in the question cannot be swapped; a
        // conservative veto is preferable to a wrong owner attribution.
        val named=words.findAll(query).map { it.value }.filter {
            it.length>=3 && it.first().isUpperCase() &&
                it.lowercase(Locale.ROOT) !in questionWords
        }
        for(name in named)if(name.lowercase(Locale.ROOT) in topWords &&
            name.lowercase(Locale.ROOT) !in candidateWords)return false
        return true
    }
}
