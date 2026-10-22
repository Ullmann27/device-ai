package at.heinz.deviceai.learning.synapse

import at.heinz.deviceai.learning.synapse.SynapseV2Bench.Case
import at.heinz.deviceai.learning.synapse.SynapseV2Bench.Split
import at.heinz.deviceai.learning.synapse.SynapseV2Reasoner.Binding
import at.heinz.deviceai.learning.synapse.SynapseV2Reasoner.Config
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Reproducible SYNAPSE v2 study (JVM). Selection uses VALIDATION only; the frozen TEST is evaluated by `final`, once,
 * after the variants and thresholds were fixed.
 * Run: java -cp synapse.jar at.heinz.deviceai.learning.synapse.SynapseV2Experiment [stats|select|final] [quick]
 */
object SynapseV2Experiment {
    /** A predictor returns an answer string or null (abstain). */
    fun interface Predictor { fun predict(c: Case): String? }

    data class Tally(var correct: Int = 0, var wrong: Int = 0, var unjustified: Int = 0,
                     var abstained: Int = 0, var unsupported: Int = 0) {
        val answerable get() = correct + wrong + unjustified
        val unanswerable get() = abstained + unsupported
        val total get() = answerable + unanswerable
        val answered get() = correct + wrong + unsupported
        fun add(c: Case, p: String?) {
            if (c.answer != null) when { p == c.answer -> correct++; p == null -> unjustified++; else -> wrong++ }
            else if (p == null) abstained++ else unsupported++
        }
        fun line(): String = "correct $correct/$answerable${ci(correct, answerable)}, wrong $wrong, unjustified abstain $unjustified | " +
            "abstained $abstained/$unanswerable, unsupported $unsupported${ci(unsupported, unanswerable)} | " +
            "coverage ${pct(answered, total)}, errors ${wrong + unsupported}/$total"
    }

    /** Wilson 95% interval as text. */
    fun ci(k: Int, n: Int): String {
        if (n == 0) return ""
        val z = 1.96; val p = k.toDouble() / n; val d = 1 + z * z / n
        val centre = (p + z * z / (2 * n)) / d
        val half = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / d
        return " [" + "%.0f".format(100 * maxOf(0.0, centre - half)) + "–" + "%.0f".format(100 * minOf(1.0, centre + half)) + "%]"
    }
    private fun pct(k: Int, n: Int) = if (n == 0) "-" else "%.0f%%".format(100.0 * k / n)

    fun report(name: String, cases: List<Case>, predictor: Predictor, breakdown: Boolean = true): String = buildString {
        val all = Tally(); val byCategory = sortedMapOf<String, Tally>(); val byForm = sortedMapOf<String, Tally>()
        val byOverlap = sortedMapOf<String, Tally>()
        val start = System.nanoTime()
        for (c in cases) {
            val p = predictor.predict(c)
            all.add(c, p)
            byCategory.getOrPut(c.category.name) { Tally() }.add(c, p)
            byForm.getOrPut(c.form.name) { Tally() }.add(c, p)
            byOverlap.getOrPut(c.overlap.substringBefore('|').let { if (c.overlap.contains('|')) c.overlap else it }) { Tally() }.add(c, p)
        }
        val ms = (System.nanoTime() - start) / 1e6 / cases.size
        appendLine("== $name  (${"%.3f".format(ms)} ms/question, JVM x86_64, not ARM64)")
        appendLine("  ALL                  " + all.line())
        if (breakdown) {
            for ((k, t) in byCategory) appendLine("  " + k.padEnd(21) + t.line())
            for ((k, t) in byForm) appendLine("  form " + k.padEnd(16) + t.line())
            for ((k, t) in byOverlap) appendLine("  tag " + k.take(30).padEnd(31) + t.line())
        }
    }

    fun examples(cases: List<Case>) = cases.map { SynapseV2Reasoner.Example(it.evidence, it.question, it.answer, it.bridge) }

    /** Abstention threshold on VALIDATION: maximize correct − 2·(wrong + unsupported). */
    fun chooseThreshold(model: SynapseV2Reasoner, validation: List<Case>): Double {
        val answers = validation.map { c -> c to model.answer(c.evidence, c.question) }
        var best = 0.0; var bestScore = Int.MIN_VALUE
        for (t in (0..19).map { it * 0.05 }) {
            val tally = Tally()
            for ((c, a) in answers) tally.add(c, if (a.answer != null && a.confidence >= t) a.answer else null)
            val score = tally.correct - 2 * (tally.wrong + tally.unsupported)
            if (score > bestScore) { bestScore = score; best = t }
        }
        return best
    }

    data class Variant(val name: String, val config: Config, val trainWith: Config = config)

    val VARIANTS = listOf(
        Variant("A1 bilinear bag (no binding)", Config(binding = Binding.BAG)),
        Variant("A2 HRR role binding", Config(binding = Binding.HRR)),
        Variant("A3 A2 + Sinkhorn competition", Config(binding = Binding.HRR, sinkhorn = true), trainWith = Config(binding = Binding.HRR)),
        Variant("A4 A2 + learned proof-gated two-hop", Config(binding = Binding.HRR, twoHop = true)),
        Variant("A4b A1 bag + two-hop (binding ablation)", Config(binding = Binding.BAG, twoHop = true)),
        Variant("S scalar features only (two-hop)", Config(binding = Binding.HRR, twoHop = true, scalarOnly = true))
    )

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    class Trained(val model: SynapseV2Reasoner, val threshold: Double, val session: SynapseV2Reasoner.Session)

    fun train(v: Variant, train: List<Case>, validation: List<Case>, seed: Long, epochs: Int): Trained {
        val learner = SynapseV2Reasoner(v.trainWith, seed)
        val session = learner.train(examples(train), epochs, seed = seed)
        val model = if (v.trainWith == v.config) learner else SynapseV2Reasoner(v.config, seed).also { it.loadParameters(learner.parameters()) }
        return Trained(model, chooseThreshold(model, validation), session)
    }

    private fun parametersSha(m: SynapseV2Reasoner): String {
        val p = m.parameters(); val bytes = java.nio.ByteBuffer.allocate(p.size * 8); p.forEach { bytes.putDouble(it) }
        return sha(bytes.array()).take(16)
    }

    fun baselines(cases: List<Case>, splitName: String) {
        print(report("A0 v1 proof core (PR #71, rules only) ($splitName)", cases, Predictor { c -> ContinuumSynapseZeroShadow.deliberate(c.evidence, c.question).answer }))
        print(report("A0b surface heuristic: last content token of the entity sentence ($splitName)", cases, Predictor(::surface)))
        for (seed in listOf(1L, 2L, 3L)) {
            val untrained = SynapseV2Reasoner(Config(binding = Binding.HRR, twoHop = true), seed)
            print(report("U untrained A4 architecture seed=$seed argmax ($splitName)", cases, Predictor { c -> untrained.answer(c.evidence, c.question).answer }, breakdown = false))
        }
    }

    fun surface(c: Case): String? {
        val tokens = SynapseV2Reasoner.tokenize(c.evidence)
        val entity = SynapseV2Reasoner.tokenize(c.question).filter { it.isId }.map { it.text }.distinct().singleOrNull() ?: return null
        val sentence = tokens.firstOrNull { it.text == entity }?.sentence ?: return null
        val q = SynapseV2Reasoner.tokenize(c.question).map { it.lower }.toSet()
        return tokens.filter { it.sentence == sentence && it.text != entity && it.lower !in SynapseV2Reasoner.STOP &&
            it.lower !in SynapseV2Reasoner.CUES && it.lower != "sondern" && it.lower !in q && it.text.length >= 2 }.lastOrNull()?.text
    }

    @JvmStatic fun main(args: Array<String>) {
        val quick = args.contains("quick")
        val mode = args.firstOrNull { it in setOf("stats", "select", "final", "dump") } ?: "stats"
        if (mode == "dump") {
            // human review of generated material (VALIDATION, or TRAIN with "train"); TEST is not printed case by case
            val cases = SynapseV2Bench.generate(if (args.contains("train")) Split.TRAIN else Split.VALIDATION)
            for ((category, group) in cases.groupBy { it.category }) for (c in group.take(3))
                println("$category ${c.relation} ${c.form} ${c.overlap} | ${c.evidence} | ${c.question} -> ${c.answer ?: "ABSTAIN"}" +
                    (c.bridge?.let { " via $it" } ?: ""))
            return
        }
        val train = SynapseV2Bench.generate(Split.TRAIN)
        val validation = SynapseV2Bench.generate(Split.VALIDATION)
        println("DATASET train=${train.size} validation=${validation.size} trainSha=${SynapseV2Bench.sha256(train)} " +
            "validationSha=${SynapseV2Bench.sha256(validation)}")
        if (mode == "stats") {
            val test = SynapseV2Bench.generate(Split.TEST)
            println("TEST n=${test.size} sha=${SynapseV2Bench.sha256(test)} pinned=${SynapseV2Bench.TEST_SHA256}")
            for (split in listOf(train, validation, test)) println("CATEGORIES ${split.first().split}: " +
                split.groupingBy { it.category }.eachCount().entries.joinToString { "${it.key}=${it.value}" })
            println("FORMS TEST: " + test.groupingBy { it.form }.eachCount())
            println("ENTITY overlap train∩test=${(SynapseV2Bench.entities(train) intersect SynapseV2Bench.entities(test)).size} " +
                "validation∩test=${(SynapseV2Bench.entities(validation) intersect SynapseV2Bench.entities(test)).size} " +
                "train∩validation=${(SynapseV2Bench.entities(train) intersect SynapseV2Bench.entities(validation)).size}")
            println("VALUE overlap train∩test=${(SynapseV2Bench.answers(train) intersect SynapseV2Bench.answers(test)).size} " +
                "validation∩test=${(SynapseV2Bench.answers(validation) intersect SynapseV2Bench.answers(test)).size} " +
                "train∩validation=${(SynapseV2Bench.answers(train) intersect SynapseV2Bench.answers(validation)).size}")
            val trainShapes = SynapseV2Bench.shapes(train)
            val leaked = SynapseV2Bench.heldOutTemplates(Split.TEST).map(SynapseV2Bench::templateShape).filter { it in trainShapes }
            println("HELD-OUT TEST templates found in TRAIN: ${leaked.size} $leaked")
            println("WORD-TRIGRAM overlap of evidence with TRAIN: validation=${"%.2f".format(SynapseV2Bench.trigramOverlap(train, validation))} " +
                "test=${"%.2f".format(SynapseV2Bench.trigramOverlap(train, test))}")
            val probe = SynapseV2Reasoner(Config(twoHop = true))
            println("PARAMETERS A4 = ${probe.parameterCount}")
            return
        }
        val seeds = if (quick) listOf(1L) else listOf(1L, 2L, 3L)
        val epochs = if (quick) 8 else 25
        val evaluation = if (mode == "final") SynapseV2Bench.generate(Split.TEST).also {
            check(SynapseV2Bench.sha256(it) == SynapseV2Bench.TEST_SHA256) { "TEST changed: ${SynapseV2Bench.sha256(it)}" }
        } else validation
        val splitName = if (mode == "final") "TEST" else "VALIDATION"
        baselines(evaluation, splitName)
        for (v in VARIANTS) for (seed in seeds) {
            val t = train(v, train, validation, seed, epochs)
            val tag = "${v.name} seed=$seed steps=${t.session.steps} skipped=${t.session.skipped} loss ${"%.3f".format(t.session.lossFirstEpoch)}->" +
                "${"%.3f".format(t.session.lossLastEpoch)} params=${t.model.parameterCount} sha=${parametersSha(t.model)}"
            print(report("$tag argmax ($splitName)", evaluation, Predictor { c -> t.model.answer(c.evidence, c.question).answer }, breakdown = seed == seeds.first()))
            print(report("$tag threshold=${"%.2f".format(t.threshold)} from VALIDATION ($splitName)", evaluation,
                Predictor { c -> t.model.answer(c.evidence, c.question, t.threshold).answer }, breakdown = seed == seeds.first()))
        }
    }
}
