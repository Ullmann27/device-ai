package at.heinz.deviceai.intelligence.ewm1

import at.heinz.deviceai.intelligence.maxultra.Ctx1Experiment
import at.heinz.deviceai.learning.*
import java.io.File
import java.net.URLEncoder
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject

/**
 * EWM2 runner: K0 = today's chat path (B0 memory, then the archive answer policy when the turn is not grounded),
 * K1 = Ewm1Brain. TEST5 runs once after the code freeze (sealed hash, never overwrites a report).
 * Mode DERIVE writes the H7 derivative (turns without source gold) for the frozen EWM1 comparison.
 */
object Ewm2Experiment {
    const val TEST5_SHA256 = "06bc2184d1e92f59d9d05c406df732e39ea8490800d6777d7421b2dd79776839"
    private fun strings(a: JSONArray) = List(a.length()) { a.getString(it) }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 2) { "usage: <DEV|TEST5|DERIVE> <corpus.json> [<out.json> <frozen-commit>]" }
        val raw = File(args[1]).readBytes(); val sha = ContinuumMemoryDialogue.hash(raw)
        val corpus = JSONArray(raw.toString(Charsets.UTF_8))
        if (args[0] == "DERIVE") { File(args[2]).writeText(derive(corpus).toString(1) + "\n"); return }
        if (args[0] == "TEST5") {
            require(sha == TEST5_SHA256) { "TEST5 corpus does not match the sealed hash" }
            require(args.size == 4 && Regex("[a-f0-9]{40}").matches(args[3]) && !File(args[2]).exists())
        }
        val verbose = args[0] == "DEV" && System.getenv("EWM1_VERBOSE") == "1"
        val bytes = Ctx1Experiment.swb1()
        val counts = linkedMapOf("K0" to linkedMapOf<String, Int>(), "K1" to linkedMapOf())
        val nanos = ArrayList<Long>()
        val dir = Files.createTempDirectory("ewm2-").toFile()
        try {
            for (d in 0 until corpus.length()) {
                val dialog = corpus.getJSONObject(d)
                val facts = strings(dialog.getJSONArray("facts"))
                val exps = facts.mapIndexed { i, text -> LearningExperienceFactory.create(LearningExperienceType.USER_FACT,
                    LearningProvenance.USER_CONFIRMED, LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, text, now = i + 1L) }
                val byId = exps.associate { it.id to it.content }
                val archiveDir = File(dir, "a$d").apply { mkdirs() }
                val archive = ContinuumKnowledgeArchive(archiveDir)
                val src = dialog.optJSONArray("sources") ?: JSONArray()
                val records = (0 until src.length()).map { i -> val s = src.getJSONObject(i)
                    archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT, s.getString("title"),
                        "https://de.wikipedia.org/wiki/" + URLEncoder.encode(s.getString("title").replace(' ', '_'), "UTF-8"),
                        "Synthetisch · EWM2-Test", s.getString("text"), collectedAt = 1_760_000_000_000L) }
                val titleById = records.associate { "SOURCE:" + it.id to "SOURCE:" + it.title }
                val core = ContinuumMemoryDialogue(File(dir, "m$d").apply { mkdirs() }); core.clear()
                val brain = Ewm1Brain(exps.map { it.id to it.content }, Ewm1SourceIndex(records)) { q, subset ->
                    ContinuumMemoryDialogue.rank(bytes, q.take(1000), exps.filter { it.id in subset }).map { it.id } }
                val turns = dialog.getJSONArray("turns")
                for (i in 0 until turns.length()) {
                    val t = turns.getJSONObject(i); val input = t.getString("text")
                    val expected = t.getString("expected"); val kind = t.optString("kind")
                    val gold = strings(t.getJSONArray("gold")).toSet()
                    // K0: today's AgentActivity order
                    val turn = core.reply(bytes, exps, input, i + 1L)
                    val k0 = if (turn.grounded) turn.memoryIds.map { byId.getValue(it) }
                        else if (input.length in 6..240 && (input.contains("?") || input.startsWith("Was ist ", true) || input.startsWith("Erkläre ", true)))
                            listOfNotNull(runCatching { ContinuumArchiveAnswerPolicy.select(input, archive.search(input, limit = 20)) }.getOrNull()
                                ?.let { "SOURCE:" + it.source.title })
                        else emptyList()
                    val start = System.nanoTime(); val ans = brain.ask(input); nanos += System.nanoTime() - start
                    val k1 = ans.cited.map { c -> if (c.startsWith("SOURCE:")) titleById.getValue(c) else byId.getValue(c) }
                    for ((sys, cited, clarified) in listOf(Triple("K0", k0, false), Triple("K1", k1, ans.decision == "CLARIFY"))) {
                        val o = Ewm1Experiment.outcome(expected, gold, cited, clarified)
                        counts.getValue(sys).merge(o, 1, Int::plus); counts.getValue(sys).merge("kind:$kind:$o", 1, Int::plus)
                    }
                    if (verbose) println("[${dialog.getString("id")}#$i $kind] $input -> ${ans.route}/${ans.decision}/${ans.reason} $k1 (K0 $k0)")
                }
            }
        } finally { dir.deleteRecursively() }
        val sorted = nanos.sorted()
        val result = JSONObject().put("experiment", "EWM2").put("split", args[0]).put("corpusSha256", sha)
            .put("commit", if (args.size == 4) args[3] else JSONObject.NULL).put("systems", JSONObject(counts as Map<*, *>))
            .put("k1MillisMedian", sorted[sorted.size / 2] / 1e6).put("k1MillisP95", sorted[(sorted.size * 95) / 100] / 1e6)
        if (args.size >= 3) { val out = File(args[2]); check(out.createNewFile()); out.writeText(result.toString(2) + "\n") }
        println(result.toString())
    }

    /** H7 derivative: same dialogs, only the turns without source gold, in order. */
    fun derive(corpus: JSONArray): JSONArray = JSONArray().apply {
        for (d in 0 until corpus.length()) {
            val dialog = corpus.getJSONObject(d); val turns = dialog.getJSONArray("turns")
            val kept = JSONArray()
            for (i in 0 until turns.length()) { val t = turns.getJSONObject(i)
                if (strings(t.getJSONArray("gold")).none { it.startsWith("SOURCE:") }) kept.put(t) }
            put(JSONObject().put("id", dialog.getString("id")).put("facts", dialog.getJSONArray("facts")).put("turns", kept))
        }
    }
}
