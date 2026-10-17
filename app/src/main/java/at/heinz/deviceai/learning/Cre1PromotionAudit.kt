package at.heinz.deviceai.learning

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.ln

/**
 * Fully local, pure research-metric calculator.
 *
 * Each supplied outcome must originate from the SAME frozen test question,
 * SAME eligible SWB1 candidate set and an independently established gold ID.
 * This class checks consistency and calculates metrics; it does NOT establish
 * that the test cases were actually run, independently sealed or untainted.
 * The caller must retain reproducible benchmark evidence separately.
 */
data class Cre1AuditOutcome(
    val caseId:String,
    val expectedId:String?,
    val eligibleIds:Set<String>,
    val swb1SelectedId:String?,
    val cre1SelectedId:String?
) {
    init {
        val valid=Regex("[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,79}")
        require(valid.matches(caseId))
        require(eligibleIds.size in 0..3 && eligibleIds.all(valid::matches))
        require(expectedId==null || valid.matches(expectedId))
        // Production CRE1 may PERMUTE SWB1 candidates, never abstain on its
        // behalf or introduce a previously excluded candidate.
        if(eligibleIds.isEmpty()) {
            require(swb1SelectedId==null && cre1SelectedId==null)
        } else {
            require(swb1SelectedId!=null && swb1SelectedId in eligibleIds)
            require(cre1SelectedId!=null && cre1SelectedId in eligibleIds)
        }
    }
}

data class Cre1AuditResult(
    val manifest:Cre1PromotionManifest,
    val correctedByHybrid:Int,
    val damagedByHybrid:Int,
    val qualified:Boolean
)

/**
 * Computes paired correctness, a two-sided exact McNemar/binomial p-value,
 * and the SHA-256 fingerprint of the complete, ID-only audit result ledger.
 * Gold cases must never be used for hyperparameter selection or training.
 */
object Cre1PromotionAudit {
    fun evaluate(
        modelSha256:String,
        modelSteps:Int,
        outcomes:List<Cre1AuditOutcome>,
        frozenFusionWeight:Double
    ):Cre1AuditResult {
        require(outcomes.size in 1..10_000)
        require(outcomes.map { it.caseId }.toSet().size==outcomes.size)
        require(modelSha256.matches(Regex("[a-f0-9]{64}")) && modelSteps>=0)
        require(frozenFusionWeight.isFinite() && frozenFusionWeight in 0.05..0.5)
        var baselineCorrect=0
        var hybridCorrect=0
        var baselineWrongAttribution=0
        var hybridWrongAttribution=0
        var negativeWrongAnswer=0
        var wins=0
        var losses=0
        val negatives=outcomes.count { it.expectedId==null }
        for(row in outcomes) {
            val baselineRight=row.swb1SelectedId==row.expectedId
            val hybridRight=row.cre1SelectedId==row.expectedId
            if(baselineRight)baselineCorrect++
            if(hybridRight)hybridCorrect++
            if(!baselineRight && row.swb1SelectedId!=null)baselineWrongAttribution++
            if(!hybridRight && row.cre1SelectedId!=null)hybridWrongAttribution++
            if(row.expectedId==null && row.cre1SelectedId!=null)negativeWrongAnswer++
            if(!baselineRight && hybridRight)wins++
            if(baselineRight && !hybridRight)losses++
        }
        val manifest=Cre1PromotionManifest(
            modelSha256=modelSha256,modelSteps=modelSteps,
            auditSha256=fingerprint(outcomes),sampleCount=outcomes.size,
            negativeCount=negatives,swb1Correct=baselineCorrect,
            hybridCorrect=hybridCorrect,
            baselineFalseAttributions=baselineWrongAttribution,
            hybridFalseAttributions=hybridWrongAttribution,
            negativeFalseAnswers=negativeWrongAnswer,
            pairedPValue=exactTwoSidedMcNemar(wins,losses),
            fusionWeight=frozenFusionWeight
        )
        return Cre1AuditResult(manifest,wins,losses,
            manifest.qualified(modelSha256,modelSteps))
    }

    /**
     * P(two-sided): 2 * P[Binomial(discordant, .5) <= min(wins,losses)].
     * Log-domain recurrence avoids floating-point overflow for up to 10k cases.
     */
    fun exactTwoSidedMcNemar(wins:Int,losses:Int):Double {
        require(wins>=0 && losses>=0 && wins.toLong()+losses<=10_000L)
        val n=wins+losses
        if(n==0)return 1.0
        val k=minOf(wins,losses)
        var logTerm=-n*ln(2.0)
        var maxLog=logTerm
        var scaledSum=1.0
        for(i in 1..k) {
            logTerm+=ln((n-i+1).toDouble())-ln(i.toDouble())
            if(logTerm>maxLog){
                scaledSum=scaledSum*exp(maxLog-logTerm)+1.0
                maxLog=logTerm
            } else scaledSum+=exp(logTerm-maxLog)
        }
        return (2.0*exp(maxLog)*scaledSum).coerceIn(0.0,1.0)
    }

    /**
     * The fingerprint covers complete decision records, not private memory
     * text. Length framing makes colliding text concatenations unambiguous.
     * Sorting makes it independent of case enumeration order.
     */
    fun fingerprint(outcomes:List<Cre1AuditOutcome>):String {
        val md=MessageDigest.getInstance("SHA-256")
        fun put(value:String){
            val bytes=value.toByteArray(StandardCharsets.UTF_8)
            md.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            md.update(bytes)
        }
        put("CRE1_PROMOTION_AUDIT_V1")
        for(row in outcomes.sortedBy { it.caseId }) {
            put(row.caseId)
            put(row.expectedId?:"")
            put(row.swb1SelectedId?:"")
            put(row.cre1SelectedId?:"")
            put(row.eligibleIds.sorted().joinToString("\u0000"))
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
