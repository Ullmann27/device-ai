package at.heinz.deviceai.learning

import at.heinz.deviceai.learning.span.*
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.sqrt

/** Fresh, synthetic counterfactuals; no checkpoint load/save or productive training. */
object CdxConditioningAudit {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2 && !File(args[1]).exists())
        val corpus = JSONObject(File(args[0]).readText()).getJSONArray("cases")
        val short = (0 until corpus.length()).map { corpus.getJSONObject(it) }.filter { it.getString("kind") == "short" }
        val rows = JSONArray()
        for (seed in listOf(113,227,409)) {
            val group = short.filter { it.getInt("seed") == seed }
            for (i in group.indices step 2) {
                val a = group[i]; val b = group[i+1]
                val nameA = a.getString("question").removePrefix("Wo ist ").removeSuffix("?")
                val nameB = b.getString("question").removePrefix("Wo ist ").removeSuffix("?")
                val cityA = a.getString("expected"); val cityB = b.getString("expected")
                val left = DecoderEvidencePair("$nameA ist in $cityA. $nameB ist in $cityB.", a.getString("question"), cityA)
                val right = DecoderEvidencePair("$nameA ist in $cityB. $nameB ist in $cityA.", a.getString("question"), cityB)
                val encoder = TrainableSubwordEncoder(seed.toLong()); val before = encoder.save()
                val x = encoder.representation(left.conditionerText()); val y = encoder.representation(right.conditionerText())
                val decoder = TinyContextDecoder(seed.toLong())
                val px = decoder.nextTokenProbabilities(left.prefix(), x)
                val py = decoder.nextTokenProbabilities(right.prefix(), y)
                fun ordered(pair: DecoderEvidencePair): EntityRelationLocationShadowAdapter.Encoding {
                    val e = EvidenceBoundNaturalLocationProbe.Evidence("frozen-audit", 1,
                        EvidenceBoundNaturalLocationProbe.digest(pair.evidence), pair.evidence)
                    return requireNotNull(EntityRelationLocationShadowAdapter.encode(pair.question,
                        LocationEvidenceSnapshot.freeze(listOf(e)), enabled = true))
                }
                val ox = ordered(left); val oy = ordered(right)
                check(before.contentEquals(encoder.save()))
                rows.put(JSONObject().put("id", a.getString("id") + "/" + b.getString("id")).put("seed", seed)
                    .put("sameBucketSet", TrainableSubwordEncoder.subwordBuckets(left.conditionerText()).toSet() ==
                        TrainableSubwordEncoder.subwordBuckets(right.conditionerText()).toSet())
                    .put("sameLast8Bytes", left.prefix().toByteArray().takeLast(8) == right.prefix().toByteArray().takeLast(8))
                    .put("conditionL2", sqrt(x.indices.sumOf { (x[it]-y[it])*(x[it]-y[it]) }))
                    .put("untrainedNextTokenMaxDifference", px.indices.maxOf { abs(px[it]-py[it]) })
                    .put("orderedBindingsCorrect", ox.location.text == cityA && oy.location.text == cityB)
                    .put("orderedRepresentationsDiffer", ox.orderedSha256 != oy.orderedSha256))
            }
        }
        val values = (0 until rows.length()).map { rows.getJSONObject(it) }
        val report = JSONObject().put("status", "RESEARCH_SHADOW").put("encoderSnapshotsUnchanged", true)
            .put("decoder", "NEW_UNTRAINED_SEEDED_CONTROL_NOT_DEVICE_CHECKPOINT")
            .put("corpusSha256", EvidenceBoundNaturalLocationProbe.digest(File(args[0]).readText()))
            .put("rows", rows).put("pairs", values.size)
            .put("identicalBucketSets", values.count { it.getBoolean("sameBucketSet") })
            .put("structuredCorrectPairs", values.count { it.getBoolean("orderedBindingsCorrect") })
        File(args[1]).writeText(report.toString(2)+"\n")
        println("pairs=${values.size}; identicalBucketSets=${report.getInt("identicalBucketSets")}; structuredCorrect=${report.getInt("structuredCorrectPairs")}")
    }
}
