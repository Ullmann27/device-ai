package at.heinz.deviceai.learning

import java.io.File
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/** Exact production classes, fresh isolated reference weights. No checkpoint/owner reads. */
object ConditioningCounterfactualExperiment {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size in 1..2)
        val spanCheckpoint = args.getOrNull(1)?.let { File(it).readBytes() }
        val pairs = listOf(
            DecoderEvidencePair("Mama ist in Salzburg. Papa ist in Wien.", "Wo ist Mama?", "Salzburg"),
            DecoderEvidencePair("Mama ist in Wien. Papa ist in Salzburg.", "Wo ist Mama?", "Wien"),
            DecoderEvidencePair("Mama ist in Salzburg. Papa ist in Wien.", "Wo ist Papa?", "Wien"),
            DecoderEvidencePair("Anna ist in Graz. Ben ist in Linz.", "Wo ist Anna?", "Graz"),
            DecoderEvidencePair("Ben ist in Graz. Anna ist in Linz.", "Wo ist Anna?", "Linz"))
        val encoder = TrainableSubwordEncoder(73)
        val encoderBefore = encoder.save()
        val model = TinyContextDecoder(91)
        val corpus = DecoderEvidenceCorpus(pairs, DecoderSplit.TRAIN)
        val report = model.trainEvidencePairs(corpus, steps=2520, maxMillis=5000,
            conditionerFor={encoder.representation(it.conditionerText())})
        val rows = pairs.map { pair ->
            val condition = encoder.representation(pair.conditionerText())
            val p = model.nextTokenProbabilities(pair.prefix(), condition)
            val output = model.generate(pair.prefix(), 48, condition)
            val target = pair.answer.toByteArray(Charsets.UTF_8).first().toInt() and 255
            val goldSteps = pair.answer.toByteArray(Charsets.UTF_8).indices.map { i ->
                val prefix = pair.prefix() + String(pair.answer.toByteArray(Charsets.UTF_8).copyOfRange(0,i),Charsets.UTF_8)
                val targetByte = pair.answer.toByteArray(Charsets.UTF_8)[i].toInt() and 255
                -ln(model.nextTokenProbabilities(prefix,condition)[targetByte].coerceAtLeast(1e-12))
            }
            JSONObject().put("evidence",pair.evidence).put("question",pair.question).put("expected",pair.answer)
                .put("contextBytes",pair.prefix().toByteArray(Charsets.UTF_8).takeLast(TinyContextDecoder.CONTEXT))
                .put("buckets",TrainableSubwordEncoder.subwordBuckets(pair.conditionerText()).toList())
                .put("condition",condition.toList()).put("firstProbabilities",p.toList())
                .put("firstGoldProbability",p[target]).put("firstGoldNll",-ln(p[target]))
                .put("teacherForcedAnswerByteNll",goldSteps.average()).put("freeText",output.text)
                .put("freeEnded",output.ended)
        }
        val distances = (1 until pairs.size).map { i ->
            val a = encoder.representation(pairs[0].conditionerText()); val b = encoder.representation(pairs[i].conditionerText())
            val p = model.nextTokenProbabilities(pairs[0].prefix(),a); val q = model.nextTokenProbabilities(pairs[i].prefix(),b)
            val ab = TrainableSubwordEncoder.subwordBuckets(pairs[0].conditionerText()).toSet()
            val bb = TrainableSubwordEncoder.subwordBuckets(pairs[i].conditionerText()).toSet()
            JSONObject().put("pair",i).put("bucketSymmetricDifference",(ab-bb).size+(bb-ab).size)
                .put("conditionL2",sqrt(a.indices.sumOf { (a[it]-b[it])*(a[it]-b[it]) }))
                .put("firstProbabilityMaxDifference",p.indices.maxOf { abs(p[it]-q[it]) })
        }
        val controls = listOf(0 to 1, 0 to 2, 3 to 4).map { (i,j) ->
            val ci = encoder.representation(pairs[i].conditionerText())
            val cj = encoder.representation(pairs[j].conditionerText())
            val own = model.nextTokenProbabilities(pairs[i].prefix(),ci)
            val changedCondition = model.nextTokenProbabilities(pairs[i].prefix(),cj)
            val changedPrompt = model.nextTokenProbabilities(pairs[j].prefix(),ci)
            JSONObject().put("left",i).put("right",j)
                .put("sameDirectByteContext",pairs[i].prefix().toByteArray().takeLast(8)==pairs[j].prefix().toByteArray().takeLast(8))
                .put("changePromptKeepConditionMaxDifference",own.indices.maxOf { abs(own[it]-changedPrompt[it]) })
                .put("changeConditionKeepPromptMaxDifference",own.indices.maxOf { abs(own[it]-changedCondition[it]) })
        }
        // Controlled role-binding collisions: audit-only, never included in TRAIN.
        // Shared place suffixes preserve every 2..4-gram bucket while the owner changes.
        val collisions = listOf("Perg" to "Berg", "Bern" to "Kern", "Salzburg" to "Würzburg").map { (x,y) ->
            val left = DecoderEvidencePair("Anna ist in $x. Emma ist in $y.","Wo ist Anna?",x)
            val right = DecoderEvidencePair("Anna ist in $y. Emma ist in $x.","Wo ist Anna?",y)
            val lb = TrainableSubwordEncoder.subwordBuckets(left.conditionerText()).toSet()
            val rb = TrainableSubwordEncoder.subwordBuckets(right.conditionerText()).toSet()
            val lc = encoder.representation(left.conditionerText()); val rc = encoder.representation(right.conditionerText())
            val lp = model.nextTokenProbabilities(left.prefix(),lc); val rp = model.nextTokenProbabilities(right.prefix(),rc)
            check(lb == rb && lb.size < 128)
            val repeatedCondition = model.nextTokenProbabilities(right.prefix(),lc)
            check(lp.contentEquals(repeatedCondition))
            JSONObject().put("leftEvidence",left.evidence).put("rightEvidence",right.evidence).put("question",left.question)
                .put("expectedLeft",x).put("expectedRight",y).put("bucketSet",lb.sorted())
                .put("conditionLeft",lc.toList()).put("conditionRight",rc.toList())
                .put("conditionL2",sqrt(lc.indices.sumOf { (lc[it]-rc[it])*(lc[it]-rc[it]) }))
                .put("firstProbabilitiesLeft",lp.toList()).put("firstProbabilitiesRight",rp.toList())
                .put("firstProbabilityMaxDifference",lp.indices.maxOf { abs(lp[it]-rp[it]) })
                .put("changePromptKeepConditionMaxDifference",0.0)
                .put("freeLeft",model.generate(left.prefix(),48,lc).text)
                .put("freeRight",model.generate(right.prefix(),48,rc).text)
                .put("teacherForcedNllLeft",model.evaluateEvidencePairs(DecoderEvidenceCorpus(listOf(left),DecoderSplit.AUDIT)) {lc})
                .put("teacherForcedNllRight",model.evaluateEvidencePairs(DecoderEvidenceCorpus(listOf(right),DecoderSplit.AUDIT)) {rc})
                .put("spanCountercheck",if (spanCheckpoint == null) JSONObject.NULL else {
                    val lab = at.heinz.deviceai.learning.span.NaturalLocationLabComparison
                    val leftSpan = lab.compare(left.evidence,left.question,spanCheckpoint)
                    val rightSpan = lab.compare(right.evidence,right.question,spanCheckpoint)
                    fun text(d:at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbe.Decision):String? =
                        (d as? at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbe.Decision.Found)?.text
                    JSONObject().put("ruleLeft",text(leftSpan.rule)).put("ruleRight",text(rightSpan.rule))
                        .put("learnedVerifiedLeft",text(leftSpan.learned) ?: JSONObject.NULL)
                        .put("learnedVerifiedRight",text(rightSpan.learned) ?: JSONObject.NULL)
                        .put("checkpointSha256",leftSpan.modelSha256)
                })
        }
        check(encoderBefore.contentEquals(encoder.save()))
        fun hash(bytes:ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val summary = JSONObject().put("scope","fresh authored reference, not owner checkpoint; consumed diagnostic")
            .put("steps",report.steps).put("interrupted",report.interrupted)
            .put("trainTeacherForcedNllBefore",report.lossBefore).put("trainTeacherForcedNllAfter",report.lossAfter)
            .put("rows",rows).put("distancesFromFirst",distances).put("causalInterventions",controls)
            .put("roleBindingCollisionsAuditOnly",collisions)
            .put("decoderSha256",hash(model.save())).put("frozenEncoderSha256",hash(encoderBefore))
        File(args[0]).writeText(summary.toString(2))
        println(JSONObject().put("steps",report.steps).put("before",report.lossBefore).put("after",report.lossAfter)
            .put("freeCorrect",pairs.indices.count { rows[it].getString("freeText")==pairs[it].answer }).put("total",pairs.size))
    }
}
