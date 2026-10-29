package at.heinz.deviceai.intelligence.ewm1

import at.heinz.deviceai.cognitive.LocalDialogueQualityGate
import at.heinz.deviceai.intelligence.maxultra.Ctx1Experiment
import at.heinz.deviceai.learning.*
import java.io.File
import java.nio.file.Files
import org.json.JSONArray
import org.json.JSONObject

/**
 * EWM1 runner. DEV4 and TEST3 are development sets; TEST4 runs once after the code freeze
 * (sealed SHA-256, refuses to overwrite a report). B0 is the unchanged ContinuumMemoryDialogue.
 */
object Ewm1Experiment {
    const val TEST4_SHA256 = "fbe484ac0cc03bedb952d101479d04a7497279e4a4c537c20f733f751c828bfa"
    private fun strings(a: JSONArray) = List(a.length()) { a.getString(it) }

    data class Summary(val counts: Map<String, Map<String, Int>>, val extra: Map<String, Any>)

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 2) { "usage: <DEV|TEST3|TEST4> <corpus.json> [<new-report.json> <frozen-commit>]" }
        val mode = args[0]
        val raw = File(args[1]).readBytes()
        require(raw.size <= 256 * 1024)
        val sha = ContinuumMemoryDialogue.hash(raw)
        if (mode == "TEST4") {
            require(sha == TEST4_SHA256) { "TEST4 corpus does not match the sealed hash" }
            require(args.size == 4 && Regex("[a-f0-9]{40}").matches(args[3])) { "TEST4 needs a report path and the frozen commit" }
            require(!File(args[2]).exists()) { "Do not overwrite a research report" }
        }
        val result = run(JSONArray(raw.toString(Charsets.UTF_8)), verbose = mode != "TEST4" && System.getenv("EWM1_VERBOSE") == "1")
        val report = JSONObject()
            .put("experiment", "EWM1").put("split", mode).put("corpusSha256", sha)
            .put("commit", if (args.size == 4) args[3] else JSONObject.NULL)
            .put("systems", JSONObject(result.counts)).put("extra", JSONObject(result.extra))
        if (args.size >= 3) { val out = File(args[2]); check(out.createNewFile()) { "report exists" }; out.writeText(report.toString(2) + "\n") }
        println(JSONObject(result.counts).toString())
        println(JSONObject(result.extra.filterKeys { it != "turns" }).toString())
    }

    fun run(corpus: JSONArray, verbose: Boolean = false): Summary {
        val bytes = Ctx1Experiment.swb1()
        val counts = linkedMapOf("B0" to linkedMapOf<String, Int>(), "EWM" to linkedMapOf())
        fun add(sys: String, k: String) { counts.getValue(sys).merge(k, 1, Int::plus) }
        val ewmNanos = ArrayList<Long>(); val b0Nanos = ArrayList<Long>()
        val claim = linkedMapOf<String, Int>()
        fun addClaim(k: String) { claim.merge(k, 1, Int::plus) }
        val turnsOut = JSONArray()
        val dir = Files.createTempDirectory("ewm1-").toFile()
        try {
            for (d in 0 until corpus.length()) {
                val dialog = corpus.getJSONObject(d)
                val facts = strings(dialog.getJSONArray("facts"))
                val exps = facts.mapIndexed { i, text ->
                    LearningExperienceFactory.create(LearningExperienceType.USER_FACT, LearningProvenance.USER_CONFIRMED,
                        LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, text, now = i + 1L)
                }
                val byId = exps.associate { it.id to it.content }
                val core = ContinuumMemoryDialogue(dir); core.clear()
                val workspace = Ewm1Workspace(exps.map { it.id to it.content }) { q, subset ->
                    ContinuumMemoryDialogue.rank(bytes, q.take(1000), exps.filter { it.id in subset }).map { it.id }
                }
                val turns = dialog.getJSONArray("turns")
                for (i in 0 until turns.length()) {
                    val t = turns.getJSONObject(i)
                    val input = t.getString("text"); val expected = t.getString("expected")
                    val kind = t.optString("kind", "")
                    val gold = strings(t.getJSONArray("gold")).toSet()
                    val outdated = if (t.has("outdated")) strings(t.getJSONArray("outdated")).toSet() else emptySet()
                    require(facts.containsAll(gold) && facts.containsAll(outdated))

                    var start = System.nanoTime()
                    val b0Turn = core.reply(bytes, exps, input, i + 1L)
                    b0Nanos += System.nanoTime() - start
                    val b0Cited = b0Turn.memoryIds.map { byId.getValue(it) }

                    start = System.nanoTime()
                    val pack = workspace.decide(input)
                    ewmNanos += System.nanoTime() - start
                    val ewmCited = pack.cited.map { byId.getValue(it) }
                    val ewmOutdated = pack.outdatedFacts.map { byId.getValue(it) }.toSet()

                    val row = JSONObject().put("dialog", dialog.getString("id")).put("turn", i).put("input", input)
                        .put("expected", expected).put("kind", kind).put("gold", JSONArray(gold.toList()))
                    for ((sys, cited, clarified, marked) in listOf(
                        Quad("B0", b0Cited, false, emptySet<String>()), Quad("EWM", ewmCited, pack.decision == "CLARIFY", ewmOutdated))) {
                        val outcome = outcome(expected, gold, cited, clarified)
                        add(sys, outcome)
                        add(sys, "kind:$kind:$outcome")
                        if (kind == "HOP2" || kind == "MULTI") add(sys, if (cited.containsAll(gold)) "COVERED" else "NOT_COVERED")
                        if (kind == "UPDATE") add(sys, if (cited.firstOrNull() in gold &&
                                outdated.all { it !in cited || it in marked }) "CURRENT_OK" else "CURRENT_FAIL")
                        row.put(sys, JSONObject().put("cited", JSONArray(cited)).put("outcome", outcome))
                    }
                    row.put("ewmDecision", pack.decision).put("ewmReason", pack.reason).put("ewmFocus", JSONArray(pack.focus))
                    if (verbose && (row.getJSONObject("EWM").getString("outcome") !in setOf("CORRECT", "ABSTAINED", "CLARIFIED")))
                        println("[${dialog.getString("id")}#$i $kind] $input -> ${pack.decision}/${pack.reason} cited=$ewmCited focus=${pack.focus} (B0 ${row.getJSONObject("B0").getString("outcome")})")
                    turnsOut.put(row)

                    if (t.has("answers")) {
                        val ans = t.getJSONObject("answers")
                        for ((label, text) in listOf("faithful" to ans.getString("faithful"), "unfaithful" to ans.getString("unfaithful"))) {
                            val ewm = Ewm1ClaimCheck.check(text, gold.toList())
                            val g0 = !LocalDialogueQualityGate.evaluate(input, text).success
                            val n0 = Ewm1ClaimCheck.naive(text, gold.toList())
                            addClaim("EWM:$label:" + if (ewm.flagged) "FLAGGED" else "PASSED")
                            addClaim("G0:$label:" + if (g0) "FLAGGED" else "PASSED")
                            addClaim("N0:$label:" + if (n0) "FLAGGED" else "PASSED")
                            if (verbose && (ewm.flagged == (label == "faithful")))
                                println("  claim[$label] ${if (ewm.flagged) "FLAGGED" else "PASSED"} ${ewm.unsupported} :: $text")
                        }
                    }
                }
            }
        } finally { dir.deleteRecursively() }
        fun pct(n: List<Long>, q: Double) = n.sorted()[((n.size - 1) * q).toInt()] / 1e6
        val extra = linkedMapOf<String, Any>(
            "ewmMillisMedian" to pct(ewmNanos, 0.5), "ewmMillisP95" to pct(ewmNanos, 0.95),
            "b0MillisMedian" to pct(b0Nanos, 0.5), "b0MillisP95" to pct(b0Nanos, 0.95),
            "claims" to claim, "turns" to turnsOut)
        return Summary(counts, extra)
    }

    private data class Quad(val a: String, val b: List<String>, val c: Boolean, val d: Set<String>)

    fun outcome(expected: String, gold: Set<String>, cited: List<String>, clarified: Boolean): String = when (expected) {
        "QUERY" -> when { clarified -> "UNNEEDED_CLARIFY"; cited.isEmpty() -> "MISSED"; cited.first() in gold -> "CORRECT"; else -> "WRONG" }
        "CLARIFY" -> when { clarified -> "CLARIFIED"; cited.isEmpty() -> "MISSED_CLARIFY"; else -> "WRONG" }
        else -> when { clarified -> "UNNEEDED_CLARIFY"; cited.isEmpty() -> "ABSTAINED"; else -> "WRONG" }
    }
}
