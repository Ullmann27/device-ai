package at.heinz.deviceai.learning.span

import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Case
import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Kind
import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Split
import java.security.MessageDigest
import java.util.Random

/**
 * Reproducible measurement run (JVM). Prints dataset hashes, baselines and trained CSX1 variants.
 * Thresholds are chosen on VALIDATION only; TEST numbers are reported once per variant.
 * Run: java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment [quick]
 */
object ContinuumSpanAbstentionExperiment {
    private fun shuffled(cases: List<Case>, seed: Long) = cases.shuffled(Random(seed))
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Trainable examples only (single-token gold); diagnostic multiword cases are never trained. */
    fun trainable(cases: List<Case>) = cases.filter { it.kind != Kind.UNIT_MULTIWORD }.map { it.example }

    /** Original PR61-style recipe: mostly positives, few negatives (ratio 1 negative : 4 positives). */
    fun originalRecipe(train: List<Case>): List<Case> {
        val pos = train.filter { it.answerable }
        val neg = train.filter { !it.answerable }.take(pos.size / 4)
        return pos + neg
    }

    data class Trained(val model: ContinuumResidualSpanCore, val threshold: Double, val steps: Int, val lossBefore: Double, val lossAfter: Double)

    fun train(cases: List<Case>, validation: List<Case>, seed: Long, epochs: Int, make: () -> ContinuumResidualSpanCore): Trained {
        val model = make()
        val examples = trainable(shuffled(cases, seed))
        val s = model.trainSession(examples, epochs = epochs, maxMillis = 60_000)
        check(!s.interrupted) { "training interrupted; raise maxMillis or lower epochs" }
        // threshold on the best candidate probability, chosen on VALIDATION only (maximize correct - unsupported)
        var bestT = 0.0; var bestScore = Int.MIN_VALUE
        for (t in (0..19).map { it * 0.05 }) {
            val m = ContinuumSpanAbstentionBench.metrics(validation) { predict(model, it, t) }
            val score = m.correct + m.correctAbstain - 2 * m.unsupported - m.wrongSpan
            if (score > bestScore) { bestScore = score; bestT = t }
        }
        return Trained(model, bestT, s.steps, s.lossBefore, s.lossAfter)
    }

    fun predict(model: ContinuumResidualSpanCore, c: Case, threshold: Double = 0.0): String? {
        val p = model.predict(c.example.evidence, c.example.question)
        return if (p.answer != null && p.confidence >= threshold) p.answer else null
    }

    @JvmStatic fun main(args: Array<String>) {
        val quick = args.contains("quick")
        val train = ContinuumSpanAbstentionBench.generate(Split.TRAIN)
        val validation = ContinuumSpanAbstentionBench.generate(Split.VALIDATION)
        val test = ContinuumSpanAbstentionBench.generate(Split.TEST)
        println("DATASET train=${train.size} (answerable ${train.count { it.answerable }}) validation=${validation.size} test=${test.size}" +
            " (unseen forms ${test.count { it.unseenForm }})")
        println("SHA256 train=" + ContinuumSpanAbstentionBench.sha256(train))
        println("SHA256 validation=" + ContinuumSpanAbstentionBench.sha256(validation))
        println("SHA256 test=" + ContinuumSpanAbstentionBench.sha256(test))
        // separation proof: no entity id and no answer value is shared between splits
        fun ids(c: List<Case>) = c.flatMap { Regex("[A-Z]{2}\\d{2}").findAll(it.example.evidence + " " + it.example.question).map { m -> m.value } }.toSet()
        fun values(c: List<Case>) = c.mapNotNull { it.example.answer }.toSet()
        check((ids(train) intersect ids(test)).isEmpty() && (ids(validation) intersect ids(test)).isEmpty())
        check((values(train) intersect values(test)).isEmpty() && (values(validation) intersect values(test)).isEmpty())
        println("SEPARATION entities and answer values disjoint across splits: OK")

        print(ContinuumSpanAbstentionBench.report("B0 untrained CSX1 (TEST)", test) { predict(ContinuumResidualSpanCore(77), it) })
        print(ContinuumSpanAbstentionBench.report("R1 surface heuristic (TEST)", test, ContinuumSpanAbstentionBench::surfaceBaseline))
        val lexicon = ContinuumSpanAbstentionBench.LexiconBaseline(train)
        print(ContinuumSpanAbstentionBench.report("R2 train-derived lexicon rule (TEST)", test, lexicon::predict))

        val seeds = if (quick) listOf(1L) else listOf(1L, 2L, 3L)
        val epochs = if (quick) 10 else 30
        val variants = linkedMapOf<String, Pair<List<Case>, () -> ContinuumResidualSpanCore>>(
            "V1 PR61 core, original recipe (few negatives)" to (originalRecipe(train) to { ContinuumResidualSpanCore(77) }),
            "V2 PR61 core, hard-negative curriculum" to (train to { ContinuumResidualSpanCore(77) })
        )
        for ((name, spec) in variants) {
            for (seed in seeds) {
                val t = train(spec.first, validation, seed, epochs, spec.second)
                val tag = "$name seed=$seed steps=${t.steps} loss ${"%.4f".format(t.lossBefore)}->${"%.4f".format(t.lossAfter)} " +
                    "checkpoint=${sha(t.model.save()).take(16)}"
                print(ContinuumSpanAbstentionBench.report("$tag argmax (TEST)", test) { predict(t.model, it) })
                print(ContinuumSpanAbstentionBench.report("$tag threshold=${"%.2f".format(t.threshold)} from VALIDATION (TEST)", test) {
                    predict(t.model, it, t.threshold) })
            }
        }
    }
}
