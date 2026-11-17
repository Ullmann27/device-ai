package at.heinz.deviceai.learning

import at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbe
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * CDX1 root-cause experiment (FABLE51 GENESIS): does the free byte generator fail because of
 * TRAINING, because of the CONDITIONER, or because of its ARCHITECTURE?
 *
 * Fresh throwaway TinyContextDecoder instances only (never a checkpoint). Frozen synthetic
 * corpus: train names/locations vs. HELD-OUT names and HELD-OUT locations, role-swap sentences
 * (Anna ist in Perg, Emma ist in Berg), and unanswerable questions (person not in evidence).
 *
 * Conditions (same decoder, same budget, same decoding):
 *  SWB1_BAG       fresh TrainableSubwordEncoder bag over evidence+question (legacy CDX1 path)
 *  ORACLE_ANSWER  16-dim code that identifies the ANSWER STRING perfectly (upper bound for any
 *                 conditioner that compresses the decision into 16 numbers; zero binding loss)
 *  ORACLE_SPELL   16-dim hash bag of the answer's own character trigrams (can the decoder act
 *                 as a spelling decoder and emit answer strings it never saw?)
 *  RULE_CORE      EvidenceBoundNaturalLocationProbe (evidence-bound span copy; zero training)
 *
 * Metrics are reported separately: exact free generation, abstention on unanswerable items,
 * and – for SWB1_BAG – whether swapped role bindings yield distinguishable first-byte
 * distributions. Usage: Cdx1OracleBoundExperiment <out.json> [updatesPerCondition=60000]
 */
object Cdx1OracleBoundExperiment {
    private const val ABSTAIN = "unbekannt"
    private val trainNames = listOf("Anna", "Emma", "Mira", "Lea", "Nora", "Paul", "Jonas", "Felix",
        "Lukas", "Maria", "Sofia", "David", "Tobias", "Hanna", "Laura", "Moritz")
    private val heldOutNames = listOf("Ylva", "Quirin", "Zoran", "Ivana", "Bastian", "Greta", "Oskar", "Xenia")
    private val trainLocations = listOf("Perg", "Berg", "Wien", "Wein", "Graz", "Gras", "Linz", "Steyr",
        "Krems", "Wels", "Leoben", "Villach")
    private val heldOutLocations = listOf("Bad Ischl", "Tulln", "Amstetten", "Hallein", "Bregenz", "Melk")

    data class Item(val pair: DecoderEvidencePair, val kind: String)

    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** Deterministic corpus; every item is a single-line evidence, a "Wo ist X?" question and a short answer. */
    private fun corpus(names: List<String>, locations: List<String>, seed: Int, swaps: Boolean, size: Int): List<Item> {
        val rnd = java.util.Random(seed.toLong())
        val items = ArrayList<Item>()
        while (items.size < size) {
            val a = names[rnd.nextInt(names.size)]
            var b = names[rnd.nextInt(names.size)]; while (b == a) b = names[rnd.nextInt(names.size)]
            val la = locations[rnd.nextInt(locations.size)]
            var lb = locations[rnd.nextInt(locations.size)]; while (lb == la) lb = locations[rnd.nextInt(locations.size)]
            val roll = rnd.nextInt(4)
            when {
                swaps && roll == 0 -> { // role swap pair: same bag of words, opposite bindings
                    items += Item(DecoderEvidencePair("$a ist in $la, $b ist in $lb.", "Wo ist $a?", la), "SWAP")
                    items += Item(DecoderEvidencePair("$a ist in $lb, $b ist in $la.", "Wo ist $a?", lb), "SWAP")
                }
                roll == 1 -> items += Item(DecoderEvidencePair("$a ist in $la, $b ist in $lb.", "Wo ist $b?", lb), "TWO")
                roll == 2 -> items += Item(DecoderEvidencePair("$a ist in $la.", "Wo ist $b?", ABSTAIN), "UNANSWERABLE")
                else -> items += Item(DecoderEvidencePair("$a ist in $la.", "Wo ist $a?", la), "ONE")
            }
        }
        return items.take(size)
    }

    private fun oracleAnswer(answer: String): DoubleArray {
        val h = sha("answer:$answer")
        return DoubleArray(16) { i -> if ((h[i / 4].digitToInt(16) shr (i % 4)) and 1 == 1) 1.0 else -1.0 }
    }

    private fun oracleSpelling(answer: String): DoubleArray {
        val v = DoubleArray(16)
        val s = " $answer "
        for (i in 0 until s.length - 2) {
            val g = s.substring(i, i + 3)
            val h = sha(g)
            val bucket = h[0].digitToInt(16)
            v[bucket] += if (h[1].digitToInt(16) < 8) 1.0 else -1.0
        }
        return DoubleArray(16) { kotlin.math.tanh(v[it]) }
    }

    private class Decoder(val label: String, val conditioner: (DecoderEvidencePair) -> DoubleArray) {
        val model = TinyContextDecoder(4711L)
        fun train(items: List<Item>, updates: Int): Pair<Double, Double> {
            val corpus = DecoderEvidenceCorpus(items.map { it.pair }, DecoderSplit.TRAIN)
            var before = Double.NaN; var after = Double.NaN; var done = 0
            while (done < updates) {
                val r = model.trainEvidencePairs(corpus, steps = minOf(20000, updates - done), learningRate = .035,
                    conditionerFor = conditioner, maxMillis = 5000)
                if (before.isNaN()) before = r.lossBefore
                after = r.lossAfter
                done += r.steps.coerceAtLeast(1)
            }
            return before to after
        }
        fun exact(items: List<Item>): Pair<Int, JSONArray> {
            var ok = 0; val samples = JSONArray()
            items.forEachIndexed { i, item ->
                val g = model.generate(item.pair.prefix(), 48, conditioner(item.pair)).text.trim()
                if (g == item.pair.answer) ok++
                if (i < 6) samples.put(JSONObject().put("kind", item.kind).put("question", item.pair.question)
                    .put("expected", item.pair.answer).put("generated", g))
            }
            return ok to samples
        }
        fun firstByteDistinct(swaps: List<Item>): Int {
            var distinct = 0
            for (i in swaps.indices step 2) {
                if (i + 1 >= swaps.size) break
                val a = model.nextTokenProbabilities(swaps[i].pair.prefix(), conditioner(swaps[i].pair))
                val b = model.nextTokenProbabilities(swaps[i + 1].pair.prefix(), conditioner(swaps[i + 1].pair))
                if (a.indices.maxByOrNull { a[it] } != b.indices.maxByOrNull { b[it] }) distinct++
            }
            return distinct
        }
    }

    private fun ruleCore(items: List<Item>): JSONObject {
        var exact = 0; var abstainOk = 0; var abstainTotal = 0; var falseAnswer = 0
        for (item in items) {
            val ev = EvidenceBoundNaturalLocationProbe.Evidence("e1", 1, sha(item.pair.evidence), item.pair.evidence)
            val d = EvidenceBoundNaturalLocationProbe.answer(item.pair.question, listOf(ev))
            if (item.pair.answer == ABSTAIN) {
                abstainTotal++
                if (d is EvidenceBoundNaturalLocationProbe.Decision.Abstained) abstainOk++ else falseAnswer++
            } else if (d is EvidenceBoundNaturalLocationProbe.Decision.Found && d.text == item.pair.answer) exact++
        }
        val answerable = items.count { it.pair.answer != ABSTAIN }
        return JSONObject().put("condition", "RULE_CORE").put("trainingUpdates", 0)
            .put("exactAnswerable", "$exact/$answerable").put("abstention", "$abstainOk/$abstainTotal")
            .put("falseAnswersOnUnanswerable", falseAnswer)
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.isNotEmpty()) { "usage: Cdx1OracleBoundExperiment <out.json> [updatesPerCondition]" }
        val out = File(args[0]); require(!out.exists()) { "refusing to overwrite ${out.path}" }
        val updates = args.getOrNull(1)?.toInt() ?: 60000
        val train = corpus(trainNames, trainLocations, seed = 1, swaps = true, size = 64)
        val testSeen = corpus(trainNames, trainLocations, seed = 2, swaps = true, size = 40)
        val testUnseenNames = corpus(heldOutNames, trainLocations, seed = 3, swaps = true, size = 40)
        val testUnseenLocations = corpus(heldOutNames, heldOutLocations, seed = 4, swaps = true, size = 40)
        val encoder = TrainableSubwordEncoder(73L)
        val report = JSONObject().put("schema", "CDX1_ORACLE_BOUND_V1").put("updatesPerCondition", updates)
            .put("decoderParameters", TinyContextDecoder(4711L).parameterCount)
            .put("trainItems", train.size).put("trainAnswers", train.map { it.pair.answer }.distinct().size)
            .put("checkpointsTouched", false)
        val conditions = JSONArray()
        val decoders = listOf(
            Decoder("SWB1_BAG") { encoder.representation(it.conditionerText()) },
            Decoder("ORACLE_ANSWER") { oracleAnswer(it.answer) },
            Decoder("ORACLE_SPELL") { oracleSpelling(it.answer) }
        )
        for (d in decoders) {
            val started = System.nanoTime()
            val (before, after) = d.train(train, updates)
            val entry = JSONObject().put("condition", d.label).put("lossBefore", before).put("lossAfter", after)
                .put("updates", d.model.updates).put("trainMillis", (System.nanoTime() - started) / 1_000_000)
            for ((name, set) in listOf("train" to train, "testSeen" to testSeen,
                "testUnseenNames" to testUnseenNames, "testUnseenLocations" to testUnseenLocations)) {
                val answerable = set.filter { it.pair.answer != ABSTAIN }
                val unanswerable = set.filter { it.pair.answer == ABSTAIN }
                val (ok, samples) = d.exact(answerable)
                val (abst, _) = d.exact(unanswerable)
                val distinctAnswers = answerable.map { d.model.generate(it.pair.prefix(), 48, d.conditioner(it.pair)).text.trim() }
                    .distinct().size
                entry.put(name, JSONObject().put("exactAnswerable", "$ok/${answerable.size}")
                    .put("abstention", "$abst/${unanswerable.size}").put("distinctGenerations", distinctAnswers)
                    .put("swapPairsDistinguished", "${d.firstByteDistinct(set.filter { it.kind == "SWAP" })}/${set.count { it.kind == "SWAP" } / 2}")
                    .put("samples", samples))
            }
            conditions.put(entry)
        }
        for ((name, set) in listOf("train" to train, "testSeen" to testSeen,
            "testUnseenNames" to testUnseenNames, "testUnseenLocations" to testUnseenLocations)) {
            conditions.put(ruleCore(set).put("set", name))
        }
        report.put("conditions", conditions)
        out.writeText(report.toString(2))
        println(report.toString(2))
    }
}
