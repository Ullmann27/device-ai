package at.heinz.deviceai.intelligence.maxultra

import java.io.File

/**
 * CTX2 run (robustness of CTX1 to the owner's own fact phrasings), pre-registered in
 * docs/research/CTX2-FACT-PHRASING-PREREG-20261024.md. B0 = today's core, B1 = CTX1 exactly as measured,
 * B2 = CTX1 with the generalized registry and unknown-name rule. Same metrics and runner as CTX1.
 * Run: java ... at.heinz.deviceai.intelligence.maxultra.Ctx2Experiment train|dev|test <report.json> <commit>  |  hash
 */
object Ctx2Experiment {
    const val TRAIN_SEED = 20261105L
    const val DEV_SEED = 20261106L
    const val TEST_SEED = 20261104L
    const val TRAIN_PER_FAMILY = 15
    const val DEV_PER_FAMILY = 10
    const val TEST_PER_FAMILY = 40
    /** SHA-256 of the canonical TEST corpus, fixed in the pre-registration before the first TEST run. */
    const val TEST_SHA256 = "2a7242bf6a93a2366fb36275f431cfeeba8ff7f651c3033e8617ca73c4c9222a"

    fun corpus(split: Ctx1Corpus.Split): List<Ctx1Corpus.Dialog> = when (split) {
        Ctx1Corpus.Split.TRAIN -> Ctx2Corpus.split(split, TRAIN_SEED, TRAIN_PER_FAMILY)
        Ctx1Corpus.Split.DEV -> Ctx2Corpus.split(split, DEV_SEED, DEV_PER_FAMILY)
        Ctx1Corpus.Split.TEST -> Ctx2Corpus.split(split, TEST_SEED, TEST_PER_FAMILY)
    }

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
        val bytes = Ctx1Experiment.swb1()
        val keep = if (split == Ctx1Corpus.Split.TEST) 60 else 400
        val tallies = listOf(Ctx1Experiment.evaluate(dialogs, false, bytes, keep),
            Ctx1Experiment.evaluate(dialogs, true, bytes, keep),
            Ctx1Experiment.evaluate(dialogs, true, bytes, keep, generalized = true))
        val report = linkedMapOf<String, Any?>("experiment" to "CTX2", "split" to split.name, "commit" to args[2],
            "corpusSha256" to sha, "dialogs" to dialogs.size, "turns" to dialogs.sumOf { it.turns.size })
        tallies.forEach { report[it.system] = Ctx1Experiment.summary(it) }
        File(args[1]).writeText(Ctx1Experiment.json(report) + "\n")
        for (t in tallies) {
            println("${t.system}: " + Ctx1Experiment.Outcome.values().joinToString(" ") { "${it.name}=${t.total(it)}" } + " extra=${t.extraCitations}")
            t.perFamily.forEach { (f, v) -> println("  $f " + Ctx1Experiment.Outcome.values().filter { v[it.ordinal] > 0 }.joinToString(" ") { "${it.name}=${v[it.ordinal]}" }) }
        }
    }
}
