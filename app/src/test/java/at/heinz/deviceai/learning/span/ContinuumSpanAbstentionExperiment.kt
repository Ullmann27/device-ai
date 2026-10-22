package at.heinz.deviceai.learning.span

import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Case
import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Kind
import at.heinz.deviceai.learning.span.ContinuumSpanAbstentionBench.Split
import java.security.MessageDigest
import java.util.Random

/**
 * Reproducible measurement run (JVM). Prints dataset hashes, baselines and trained CSX1 variants.
 * Thresholds are chosen on VALIDATION only; TEST numbers are reported once per variant.
 * Run: java -cp csx1.jar at.heinz.deviceai.learning.span.ContinuumSpanAbstentionExperiment [cycle1|select|final] [quick]
 *   cycle1 (default): logged cycle-1 comparison on TEST; select: feature variants on VALIDATION only;
 *   final: the variants fixed after selection, once on the frozen TEST.
 */
object ContinuumSpanAbstentionExperiment {
    private fun shuffled(cases: List<Case>, seed: Long) = cases.shuffled(Random(seed))
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Trainable examples: multi-token golds only for models with multiTokenSpans (otherwise unrepresentable). */
    fun trainable(cases: List<Case>, multiToken: Boolean = false) =
        cases.filter { multiToken || it.kind != Kind.UNIT_MULTIWORD }.map { it.example }

    /** Original PR61-style recipe: mostly positives, few negatives (ratio 1 negative : 4 positives). */
    fun originalRecipe(train: List<Case>): List<Case> {
        val pos = train.filter { it.answerable }
        val neg = train.filter { !it.answerable }.take(pos.size / 4)
        return pos + neg
    }

    data class Trained(val model: ContinuumResidualSpanCore, val threshold: Double, val steps: Int, val lossBefore: Double, val lossAfter: Double)

    fun train(cases: List<Case>, validation: List<Case>, seed: Long, epochs: Int, make: () -> ContinuumResidualSpanCore): Trained {
        val model = make()
        val examples = trainable(shuffled(cases, seed), model.features.multiTokenSpans)
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

    val VARIANTS = linkedMapOf(
        "V2 legacy features (PR61)" to ContinuumResidualSpanCore.Features(),
        "V3 no position shortcut" to ContinuumResidualSpanCore.Features(positionFeatures = false),
        "V4 identifier placeholders" to ContinuumResidualSpanCore.Features(identifierPlaceholders = true),
        "V5 placeholders + no position" to ContinuumResidualSpanCore.Features(identifierPlaceholders = true, positionFeatures = false),
        "V6 entity proximity" to ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true),
        "V7 PR61 features + cue words excluded" to ContinuumResidualSpanCore.Features(excludeCueWords = true),
        "V8 entity proximity + cue words excluded" to ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true, excludeCueWords = true),
        "V9 V8 + multi-token spans" to ContinuumResidualSpanCore.Features(positionFeatures = false, entityProximity = true,
            excludeCueWords = true, multiTokenSpans = true)
    )

    private fun runVariants(names: List<String>, split: List<Case>, splitName: String, train: List<Case>, validation: List<Case>,
                            seeds: List<Long>, epochs: Int, errors: Boolean) {
        for (name in names) for (seed in seeds) {
            val features = VARIANTS.getValue(name)
            val t = train(train, validation, seed, epochs) { ContinuumResidualSpanCore(77, features) }
            val tag = "$name seed=$seed steps=${t.steps} loss ${"%.4f".format(t.lossBefore)}->${"%.4f".format(t.lossAfter)} " +
                "checkpoint=${sha(t.model.save()).take(16)}"
            print(ContinuumSpanAbstentionBench.report("$tag argmax ($splitName)", split) { predict(t.model, it) })
            print(ContinuumSpanAbstentionBench.report("$tag threshold=${"%.2f".format(t.threshold)} from VALIDATION ($splitName)", split) {
                predict(t.model, it, t.threshold) })
            if (errors && seed == seeds.first()) for (c in split.filter { it.unseenForm }) {
                val p = predict(t.model, c)
                if (p != c.example.answer) println("    ERR ${c.kind} ${c.relation}: '${c.example.evidence}' | '${c.example.question}' expected=${c.example.answer ?: "ABSTAIN"} got=${p ?: "ABSTAIN"}")
            }
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        val quick = args.contains("quick")
        val mode = args.firstOrNull { it in setOf("cycle1", "select", "final", "diagnose") } ?: "cycle1"
        if (mode == "diagnose") {
            // learned scalar weights (last 14 parameters) and validation negation errors; no TEST access
            val train = ContinuumSpanAbstentionBench.generate(Split.TRAIN)
            val validation = ContinuumSpanAbstentionBench.generate(Split.VALIDATION)
            val names = listOf("residual", "overlap", "entity", "negation", "contrast", "code", "capital", "slot7", "slot8", "bias",
                "null:1", "null:maxOverlap", "null:maxBinding", "null:noCandidates")
            for (v in args.filter { it.matches(Regex("V\\d")) }) {
                val name = VARIANTS.keys.first { it.startsWith("$v ") }
                val t = train(train, validation, 1L, 30) { ContinuumResidualSpanCore(77, VARIANTS.getValue(name)) }
                val w = t.model.parameters().takeLast(14)
                // the five public PR61 DEV cases: regression/sanity only, never a blind test
                val pr61 = listOf(
                    ContinuumResidualSpanCore.Example("Roboter X77 steht in Salzburg.", "Wo befindet sich X77?", "Salzburg"),
                    ContinuumResidualSpanCore.Example("Gerät K99 verwendet Modul XZ-902.", "Welches Modul verwendet K99?", "XZ-902"),
                    ContinuumResidualSpanCore.Example("Roboter T82 nutzt eine Zange.", "Welches Werkzeug nutzt T82?", "Zange"),
                    ContinuumResidualSpanCore.Example("Roboter X77 steht in Salzburg.", "Welches Werkzeug nutzt X77?", null),
                    ContinuumResidualSpanCore.Example("Gerät K99 verwendet Modul XZ-902.", "Wo steht K99?", null))
                println("PR61-DEV $name: " + pr61.joinToString { e -> (e.answer ?: "ABSTAIN") + "->" +
                    (t.model.predict(e.evidence, e.question).answer ?: "ABSTAIN") } + " | checkpointSha256=" + sha(t.model.save()))
                println("WEIGHTS $name: " + names.zip(w).joinToString { (n, x) -> n + "=" + "%.3f".format(x) })
                for (c in validation.filter { it.kind == Kind.NEGATED || it.kind == Kind.CONTRAST }) {
                    val p = t.model.predict(c.example.evidence, c.example.question)
                    if (p.answer != c.example.answer) println("  ERR ${c.kind}: '${c.example.evidence}' | '${c.example.question}' -> ${p.answer} p=${"%.2f".format(p.confidence)}")
                }
            }
            return
        }
        if (mode != "cycle1") {
            val multiword = args.contains("multiword")
            val train = ContinuumSpanAbstentionBench.generate(Split.TRAIN, bindingCurriculum = args.contains("binding"), multiword = multiword)
            val validation = ContinuumSpanAbstentionBench.generate(Split.VALIDATION, multiword = multiword)
            println("TRAIN binding curriculum=${args.contains("binding")} multiword=$multiword trainSha=${ContinuumSpanAbstentionBench.sha256(train)}")
            println("DATASET train=${train.size} validation=${validation.size} (unseen ${validation.count { it.unseenForm }}) " +
                "validationSha=${ContinuumSpanAbstentionBench.sha256(validation)}")
            val seeds = if (quick) listOf(1L) else listOf(1L, 2L, 3L)
            val chosen = args.filter { it.matches(Regex("V\\d")) }.map { a -> VARIANTS.keys.first { it.startsWith(a + " ") } }
            if (mode == "select") runVariants(chosen.ifEmpty { VARIANTS.keys.toList() }, validation, "VALIDATION", train, validation, seeds, if (quick) 10 else 30, true)
            else {
                val test = ContinuumSpanAbstentionBench.generate(Split.TEST)
                check(ContinuumSpanAbstentionBench.sha256(test) == ContinuumSpanAbstentionBench.TEST_SHA256) { "TEST changed" }
                runVariants(chosen.ifEmpty { VARIANTS.keys.toList() }, test, "TEST", train, validation, seeds, 30, false)
            }
            return
        }
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
