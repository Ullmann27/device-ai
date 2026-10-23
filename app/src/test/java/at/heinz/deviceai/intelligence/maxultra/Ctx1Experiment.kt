package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.learning.ContinuumMemoryDialogue
import at.heinz.deviceai.learning.LearningExperienceFactory
import at.heinz.deviceai.learning.LearningExperienceType
import at.heinz.deviceai.learning.LearningProvenance
import at.heinz.deviceai.learning.LearningStatus
import at.heinz.deviceai.learning.LearningTrustClass
import java.io.File
import java.nio.file.Files
import java.util.Base64

/**
 * CTX1 run (S1/S2), pre-registered in docs/research/CTX1-DIALOGUE-CONTEXT-PREREG-20261024.md.
 * B0 = today's dialogue core (ContinuumMemoryDialogue.reply with the real SWB1 fixture over confirmed owner facts);
 * B1 = Ctx1DialogueContext in front of the same, unchanged core.
 * Run: java ... at.heinz.deviceai.intelligence.maxultra.Ctx1Experiment train|dev|test <report.json> <commit>  |  hash
 */
object Ctx1Experiment {
    const val TRAIN_SEED = 20261101L
    const val DEV_SEED = 20261102L
    const val TEST_SEED = 20261103L
    const val TRAIN_PER_FAMILY = 15
    const val DEV_PER_FAMILY = 10
    const val TEST_PER_FAMILY = 40
    /** SHA-256 of the canonical TEST corpus, fixed in the pre-registration before the first TEST run. */
    const val TEST_SHA256 = "6f2514f3842d9e929770a262ae4475c1822cc4a3e72c9a639d257672d23d97f0"

    enum class Outcome { CORRECT, WRONG, MISSED, UNNEEDED_CLARIFY, CLARIFIED, ABSTAINED }

    fun corpus(split: Ctx1Corpus.Split): List<Ctx1Corpus.Dialog> = when (split) {
        Ctx1Corpus.Split.TRAIN -> Ctx1Corpus.split(split, TRAIN_SEED, TRAIN_PER_FAMILY)
        Ctx1Corpus.Split.DEV -> Ctx1Corpus.split(split, DEV_SEED, DEV_PER_FAMILY)
        Ctx1Corpus.Split.TEST -> Ctx1Corpus.split(split, TEST_SEED, TEST_PER_FAMILY)
    }

    fun swb1(): ByteArray = Base64.getDecoder().decode(
        Ctx1Experiment::class.java.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())

    /** A first citation outside the gold set, or any citation where nothing or a question back is right, is WRONG. */
    fun classify(gold: Ctx1Corpus.Gold, cited: List<String>, clarified: Boolean): Outcome = when (gold) {
        is Ctx1Corpus.Memory -> when {
            clarified -> Outcome.UNNEEDED_CLARIFY
            cited.isEmpty() -> Outcome.MISSED
            cited.first() in gold.facts -> Outcome.CORRECT
            else -> Outcome.WRONG
        }
        Ctx1Corpus.Clarify -> if (clarified) Outcome.CLARIFIED else if (cited.isEmpty()) Outcome.ABSTAINED else Outcome.WRONG
        Ctx1Corpus.Unknown -> if (cited.isEmpty()) Outcome.ABSTAINED else Outcome.WRONG
    }

    class Tally(val system: String) {
        val perFamily = linkedMapOf<String, IntArray>()
        var extraCitations = 0
        val turnNanos = ArrayList<Long>()
        val routineNanos = ArrayList<Long>()
        var maxStateBytes = 0L
        var maxStateEntries = 0
        val failures = ArrayList<String>()
        fun add(family: String, o: Outcome) { perFamily.getOrPut(family) { IntArray(Outcome.values().size) }[o.ordinal]++ }
        fun total(o: Outcome) = perFamily.values.sumOf { it[o.ordinal] }
    }

    private fun experiences(d: Ctx1Corpus.Dialog) = d.facts.map {
        LearningExperienceFactory.create(LearningExperienceType.USER_FACT, LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, it, now = 1)
    }

    fun evaluate(dialogs: List<Ctx1Corpus.Dialog>, withContext: Boolean, bytes: ByteArray, keepFailures: Int = 0): Tally {
        val tally = Tally(if (withContext) "B1" else "B0")
        val dir = Files.createTempDirectory("ctx1-").toFile()
        try {
            for (d in dialogs) {
                val exps = experiences(d)
                val text = exps.associate { it.id to it.content }
                val core = ContinuumMemoryDialogue(dir)
                core.clear()
                val ctx = if (withContext) Ctx1DialogueContext(d.facts) else null
                d.turns.forEachIndexed { i, t ->
                    val started = System.nanoTime()
                    var cited = emptyList<String>()
                    var clarified = false
                    var asked = t.text
                    if (ctx == null) {
                        cited = core.reply(bytes, exps, t.text, i + 1L).memoryIds.map { text.getValue(it) }
                    } else {
                        val decision = ctx.decide(t.text)
                        tally.routineNanos += System.nanoTime() - started
                        when (decision) {
                            is Ctx1DialogueContext.Ask -> {
                                asked = decision.query
                                val pool = decision.scope?.let { scope -> exps.filter { it.content in scope } } ?: exps
                                cited = core.reply(bytes, pool, decision.query.take(512), i + 1L).memoryIds.map { text.getValue(it) }
                                ctx.observe(cited)
                            }
                            Ctx1DialogueContext.Clarify -> clarified = true
                            Ctx1DialogueContext.Unknown -> Unit
                        }
                        tally.maxStateEntries = maxOf(tally.maxStateEntries, ctx.stateSize())
                    }
                    tally.turnNanos += System.nanoTime() - started
                    val o = classify(t.gold, cited, clarified)
                    tally.add(d.family, o)
                    if (t.gold is Ctx1Corpus.Memory) tally.extraCitations += cited.drop(1).count { it !in (t.gold as Ctx1Corpus.Memory).facts }
                    if (o == Outcome.WRONG || o == Outcome.MISSED || o == Outcome.UNNEEDED_CLARIFY) {
                        if (tally.failures.size < keepFailures)
                            tally.failures += "${d.id} #${i + 1} $o: \"${t.text}\" -> \"$asked\" cited=${cited.firstOrNull()} gold=${t.gold}"
                    }
                }
                tally.maxStateBytes = maxOf(tally.maxStateBytes, File(dir, "dialogue-v1.bin").takeIf { it.exists() }?.length() ?: 0L)
            }
        } finally { dir.deleteRecursively() }
        return tally
    }

    private fun percentile(xs: List<Long>, p: Double): Double =
        if (xs.isEmpty()) 0.0 else xs.sorted()[((xs.size - 1) * p).toInt()] / 1e6

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> json(value.toString())
    }

    fun summary(t: Tally): Map<String, Any?> = linkedMapOf(
        "system" to t.system,
        "totals" to Outcome.values().associate { it.name to t.total(it) },
        "perFamily" to t.perFamily.mapValues { (_, v) -> Outcome.values().associate { it.name to v[it.ordinal] } },
        "extraCitations" to t.extraCitations,
        "turnMillisMedian" to percentile(t.turnNanos, 0.5), "turnMillisP95" to percentile(t.turnNanos, 0.95),
        "routineMillisMedian" to percentile(t.routineNanos, 0.5), "routineMillisP95" to percentile(t.routineNanos, 0.95),
        "maxHistoryBytes" to t.maxStateBytes, "maxRoutineStateEntries" to t.maxStateEntries,
        "failures" to t.failures)

    @JvmStatic fun main(args: Array<String>) {
        if (args.firstOrNull() == "hash") {
            println(Ctx1Corpus.sha256(Ctx1Corpus.canonical(corpus(Ctx1Corpus.Split.TEST))))
            return
        }
        require(args.size == 3 && args[0] in setOf("train", "dev", "test")) { "usage: train|dev|test <report.json> <commit> | hash" }
        val split = Ctx1Corpus.Split.valueOf(args[0].uppercase())
        val dialogs = corpus(split)
        val sha = Ctx1Corpus.sha256(Ctx1Corpus.canonical(dialogs))
        if (split == Ctx1Corpus.Split.TEST) require(sha == TEST_SHA256) { "TEST corpus does not match the sealed hash" }
        val bytes = swb1()
        val keep = if (split == Ctx1Corpus.Split.TEST) 60 else 400
        val b0 = evaluate(dialogs, false, bytes, keep)
        val b1 = evaluate(dialogs, true, bytes, keep)
        val report = linkedMapOf<String, Any?>(
            "experiment" to "CTX1", "split" to split.name, "commit" to args[2], "corpusSha256" to sha,
            "dialogs" to dialogs.size, "turns" to dialogs.sumOf { it.turns.size },
            "B0" to summary(b0), "B1" to summary(b1))
        File(args[1]).writeText(json(report) + "\n")
        for (t in listOf(b0, b1)) {
            println("${t.system}: " + Outcome.values().joinToString(" ") { "${it.name}=${t.total(it)}" } +
                " extra=${t.extraCitations} medianMs=${"%.3f".format(percentile(t.turnNanos, 0.5))}")
            t.perFamily.forEach { (f, v) -> println("  $f " + Outcome.values().filter { v[it.ordinal] > 0 }.joinToString(" ") { "${it.name}=${v[it.ordinal]}" }) }
        }
    }
}
