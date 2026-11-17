package at.heinz.deviceai.learning

import kotlin.math.ln
import kotlin.math.sqrt

/** Reproducible HOST experiment, not a JUnit assertion of model intelligence.
 * Trains only authored curriculum TRAIN. The old public 25 probes are regression
 * measurements, already consumed; they are never evidence of blind generalisation.
 * Outputs synthetic probe data and checkpoint hashes only. Never opens app storage.
 */
object Cdx1CausalAblation {
    private fun json(s: String) = "\"" + s.map { c -> when(c) {
        '"' -> "\\\""; '\\' -> "\\\\"; else -> if(c.code < 32) "\\u%04x".format(c.code) else c.toString()
    } }.joinToString("") + "\""

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2) { "Cdx1CausalAblation <seed> <raw|unit|zero>" }
        val seed = args[0].toLong()
        val mode = args[1].also { require(it in setOf("raw", "unit", "zero")) }
        val encoder = TrainableSubwordEncoder()
        fun condition(text: String): DoubleArray {
            val c = encoder.representation(text)
            return when(mode) {
                "zero" -> DoubleArray(16)
                "unit" -> { val norm = sqrt(c.sumOf { it * it }); c.map { if(norm > 0) it / norm else 0.0 }.toDoubleArray() }
                else -> c
            }
        }
        val training = ContinuumCdxLargeCurriculum.trainingStage(0)
        val probes = ContinuumIntelligenceAudit.frozenProbes
        val audit = DecoderEvidenceCorpus(probes.map {
            DecoderEvidencePair(it.evidence, it.question, it.expected ?: "unbekannt")
        }, DecoderSplit.AUDIT)
        val decoder = TinyContextDecoder(seed)
        val started = System.nanoTime()
        val trained = decoder.trainEvidencePairs(training, steps=2520,
            conditionerFor={ condition(it.conditionerText()) }, maxMillis=5000)
        check(trained.steps == 2520 && !trained.interrupted) { "Incomplete experiment" }
        val checkpoint = decoder.save()
        val vectors = audit.pairs.map { condition(it.conditionerText()) }
        val distances = vectors.indices.flatMap { i -> (i+1 until vectors.size).map { j ->
            sqrt(vectors[i].indices.sumOf { k -> (vectors[i][k]-vectors[j][k]).let { it*it } })
        } }
        var firstCorrect = 0; var entropy = 0.0; var eos = 0.0
        val predictions = audit.pairs.mapIndexed { i, p ->
            val distribution = decoder.nextTokenProbabilities(p.prefix(), vectors[i])
            val best = distribution.indices.maxBy { distribution[it] }
            if(best == (p.answer.toByteArray()[0].toInt() and 255)) firstCorrect++
            entropy += -distribution.filter { it > 0 }.sumOf { it*ln(it) }
            eos += distribution[257]
            decoder.generate(p.prefix(), DecoderGenerativeGate.MAX_BYTES, vectors[i]).text
        }
        val scored = ContinuumIntelligenceAudit.score(predictions,
            ContinuumMemoryDialogueHash.hash(checkpoint), decoder.updates)
        val permuted = audit.pairs.mapIndexed { i, p ->
            decoder.generate(p.prefix(), DecoderGenerativeGate.MAX_BYTES, vectors[(i+1)%vectors.size]).text
        }
        val collision = predictions.groupingBy { it }.eachCount().values.maxOrNull()!!
        val elapsed = (System.nanoTime()-started)/1_000_000.0
        check(checkpoint.contentEquals(decoder.save())) { "Evaluation mutated decoder" }
        println("{\"context\":${TinyContextDecoder.CONTEXT},\"seed\":$seed,\"condition\":${json(mode)}," +
            "\"parameters\":${decoder.parameterCount},\"snapshotBytes\":${checkpoint.size},\"productionSnapshotCompatible\":${checkpoint.size<=65536},\"updates\":${decoder.updates},\"trainNllBefore\":${trained.lossBefore}," +
            "\"trainNllAfter\":${trained.lossAfter},\"teacherForcedProbeNll\":${decoder.evaluateEvidencePairs(audit) { condition(it.conditionerText()) }}," +
            "\"correct\":${scored.correctAnswers},\"supported\":20,\"abstentions\":${scored.validAbstentions},\"missing\":5," +
            "\"unsupported\":${scored.unsupportedClaims},\"firstByteCorrect\":$firstCorrect,\"firstByteTotal\":25," +
            "\"firstEntropyNats\":${entropy/25},\"firstEosProbability\":${eos/25}," +
            "\"conditionMeanPairDistance\":${distances.average()},\"conditionMinPairDistance\":${distances.minOrNull()}," +
            "\"outputsUnchangedByConditionPermutation\":${predictions.zip(permuted).count { it.first==it.second }}," +
            "\"largestIdenticalOutputGroup\":$collision,\"elapsedMillis\":$elapsed,\"checkpointSha256\":${json(ContinuumMemoryDialogueHash.hash(checkpoint))}," +
            "\"fixtureSha256\":${json(ContinuumIntelligenceAudit.fixtureSha256())},\"predictions\":${predictions.joinToString(",", "[", "]", transform=::json)}}")
    }

    private object ContinuumMemoryDialogueHash {
        fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
