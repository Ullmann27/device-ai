package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.Lct1.Answer
import at.heinz.deviceai.intelligence.maxultra.Lct1.Kind
import at.heinz.deviceai.intelligence.maxultra.Lct1.KnowledgeBase
import at.heinz.deviceai.intelligence.maxultra.Lct1.Polarity
import at.heinz.deviceai.intelligence.maxultra.Lct1.Query
import at.heinz.deviceai.intelligence.maxultra.Lct1.QuestionType
import at.heinz.deviceai.intelligence.maxultra.Lct1.Relation
import at.heinz.deviceai.intelligence.maxultra.Lct1.SourceStatus
import at.heinz.deviceai.intelligence.maxultra.Lct1.Type
import at.heinz.deviceai.intelligence.maxultra.Lct1Experiment.Outcome
import java.io.File
import java.time.LocalDate
import java.util.Random

/**
 * Experiment C (WSC1) procedure, pre-registered in docs/research/WSC1-CONSOLIDATION-PREREG-20261023.md:
 * world -> wake stream (candidates) -> sleep (gate, receipts, gate self-test) -> events -> holdout against fresh L+V.
 * Run: java ... at.heinz.deviceai.intelligence.maxultra.Wsc1Experiment <report.json> <commit>
 */
object Wsc1Experiment {
    const val WORLD_SEED = 20261025L
    const val STREAM_SEED = 20261026L
    val CEO_CHANGE: LocalDate = LocalDate.of(2026, 12, 1)
    val HOLDOUT_FIRST: LocalDate = LocalDate.of(2026, 10, 24)
    const val HOLDOUT_DAYS = 129 // 24.10.2026 .. 01.03.2027
    const val REVOKED_SOURCE = "src-netz-2"
    val TYPES = listOf(QuestionType.REGION_OF_DEVICE, QuestionType.VOLTAGE_AT_BUILDING, QuestionType.CEO_FOR_DEVICE,
        QuestionType.CAPITAL_FOR_BUILDING, QuestionType.CHECK_VOLTAGE_AT_BUILDING)
    val EVENTS = listOf("CEO_CHANGE", "CONTRADICTION", "NEGATION", "REMOVED_LINK", "NAMESAKE")

    data class Config(val chains: Int = 300, val seen: Int = 200, val wakeRepeats: Int = 3, val holdoutPerChain: Int = 4,
                      val eventChains: Int = 20, val extraDistractors: Int = 400, val selfTest: Int = 20)

    class Chain(val device: String, val building: String, val city: String, val company: String, val voltage: Long)
    class World(val builder: Lct1Experiment.Builder, val chains: List<Chain>, val kb0: KnowledgeBase)

    fun world(cfg: Config): World {
        val b = Lct1Experiment.Builder(Lct1Experiment.Names(WORLD_SEED xor 0x5EEDL, emptySet()), Random(WORLD_SEED), "wsc")
        val cases = (0 until cfg.chains).map { b.case(Lct1Experiment.Variant.HOP3_VOLTAGE, it, "wsc-$it") }
        b.distractors(Lct1Experiment.DISTRACTOR_CHAINS)
        val chains = cases.map { c ->
            val fs = c.factIds.map(b.facts::getValue)
            val dev = fs.first { it.relation == Relation.IN_BUILDING }
            val city = fs.first { it.relation == Relation.IN_CITY && it.subject == dev.objectId }
            val region = fs.first { it.relation == Relation.IN_REGION && it.subject == city.objectId }
            val volt = fs.first { it.relation == Relation.GRID_VOLTAGE && it.subject == region.objectId }
            val op = fs.first { it.relation == Relation.OPERATED_BY && it.subject == dev.objectId }
            Chain(dev.subject, dev.objectId!!, city.objectId!!, op.objectId!!, volt.quantity!!)
        }
        return World(b, chains, b.knowledgeBase())
    }

    private fun question(w: World, c: Chain, type: QuestionType, asOf: LocalDate, rnd: Random): Query {
        val q = Query(type, w.builder.name(if (type.pattern.first().from == Type.DEVICE) c.device else c.building), asOf)
        if (!type.check) return q
        val others = Lct1Experiment.VOLTAGES.filter { it != c.voltage }
        return q.copy(claimedQuantity = if (rnd.nextBoolean()) c.voltage else others[rnd.nextInt(others.size)])
    }

    /** Wake stream (each seen chain: one question type, asked `wakeRepeats` times) and holdout stream. */
    fun streams(w: World, cfg: Config): Pair<List<Query>, List<Query>> {
        val rnd = Random(STREAM_SEED)
        val wakeType = List(cfg.seen) { TYPES[rnd.nextInt(TYPES.size)] }
        val wake = (0 until cfg.seen).flatMap { i ->
            List(cfg.wakeRepeats) { question(w, w.chains[i], wakeType[i], Lct1Experiment.AS_OF.plusDays(rnd.nextInt(9).toLong()), rnd) }
        }
        val h = Random(STREAM_SEED + 1)
        val holdout = (0 until cfg.chains).flatMap { i ->
            List(cfg.holdoutPerChain) { k ->
                val type = if (i < cfg.seen && k < 2) wakeType[i] else TYPES[h.nextInt(TYPES.size)]
                question(w, w.chains[i], type, HOLDOUT_FIRST.plusDays(h.nextInt(HOLDOUT_DAYS).toLong()), h)
            }
        }
        return wake to holdout
    }

    /** The fixed event list on disjoint random chains, the revoked source and the unrelated new facts. */
    fun applyEvents(w: World, cfg: Config): Pair<Map<String, List<Int>>, Map<String, SourceStatus>> {
        val b = w.builder
        val order = (0 until cfg.chains).shuffled(Random(WORLD_SEED + 1))
        val affected = EVENTS.withIndex().associate { (k, e) -> e to order.subList(k * cfg.eventChains, (k + 1) * cfg.eventChains) }
        fun factOf(subject: String, r: Relation) = b.facts.values.first { it.subject == subject && it.relation == r && it.polarity == Polarity.ASSERTED }
        for (i in affected.getValue("CEO_CHANGE")) {
            val c = w.chains[i]
            b.restate(factOf(c.company, Relation.CEO), to = CEO_CHANGE)
            b.fact(c.company, Relation.CEO, b.entity(Type.PERSON), from = CEO_CHANGE)
        }
        for (i in affected.getValue("CONTRADICTION")) b.fact(w.chains[i].city, Relation.IN_REGION, b.entity(Type.REGION))
        for (i in affected.getValue("NEGATION")) w.chains[i].let { b.fact(it.device, Relation.IN_BUILDING, it.building, polarity = Polarity.NEGATED) }
        for (i in affected.getValue("REMOVED_LINK")) b.remove(factOf(w.chains[i].building, Relation.OPERATED_BY))
        for (i in affected.getValue("NAMESAKE"))
            b.fact(b.entity(Type.DEVICE, b.name(w.chains[i].device)), Relation.IN_BUILDING, b.entity(Type.BUILDING))
        b.distractors(cfg.extraDistractors)
        return affected to (Lct1Experiment.REGISTER + (REVOKED_SOURCE to SourceStatus.REVOKED))
    }

    fun outcome(gold: Answer, a: Answer): Outcome = when {
        gold.kind == Kind.ABSTAIN -> if (a.kind == Kind.ABSTAIN) Outcome.CORRECT else Outcome.WRONG
        a.kind == Kind.ABSTAIN -> Outcome.MISSED
        Wsc1.same(a, gold) -> Outcome.CORRECT
        else -> Outcome.WRONG
    }

    class SystemResult(val name: String, val outcomes: List<Outcome>, val hits: Int, val nanos: LongArray)
    class Run(val results: List<SystemResult>, val repeat: Set<Int>, val gold: List<Answer>, val gate: Map<String, Int>,
              val selfTest: Map<String, Int>, val sleepMillis: Long, val habits: Int, val receiptsMatch: Boolean,
              val uninformative: Int, val wakeCandidates: Int, val factsBefore: Int, val factsAfter: Int,
              val affected: Map<String, List<Int>>)

    fun run(cfg: Config = Config()): Run {
        val w = world(cfg)
        val (wake, holdout) = streams(w, cfg)
        val s0 = Wsc1.Snapshot(w.kb0)
        val full = Wsc1.Full(s0)
        val cache = Wsc1.Cache(s0)
        val wsc1 = Wsc1.Consolidated(s0)
        val naive = Wsc1.Naive(s0)
        val candidates = ArrayList<Pair<Query, Answer>>()
        for (q in wake) {
            val a = s0.full.answer(q)
            cache.answer(q)
            if (a.kind != Kind.ABSTAIN) { candidates += q to a; naive.learn(Wsc1.habitOf(q, a, s0)) }
        }
        val started = System.nanoTime()
        val gate = Wsc1.Gate(wsc1, Random(STREAM_SEED + 2))
        for ((q, a) in candidates) gate.offer(q, a, s0)
        val sleepMillis = (System.nanoTime() - started) / 1_000_000

        // gate self-test on an empty target: unverified (falsified) claims and deliberately flawed habits
        val selfTest = linkedMapOf<String, Int>()
        val selfGate = Wsc1.Gate(Wsc1.Consolidated(s0), Random(STREAM_SEED + 3))
        val rnd = Random(STREAM_SEED + 4)
        val distinct = candidates.distinctBy { it.first.type.pattern to Wsc1.key(it.first.mention) }
        fun record(kind: String, verdict: String) = selfTest.merge("$kind -> $verdict", 1, Int::plus)
        for ((q, a) in distinct.take(cfg.selfTest)) for ((kind, m) in Lct1Experiment.mutations(q, a, s0.kb, rnd))
            if (kind in setOf("VALUE", "FOREIGN_FACT", "DROP_STEP")) record("UNVERIFIED_$kind", selfGate.offer(m.first, m.second, s0))
        distinct.filter { (q, a) -> Wsc1.habitOf(q, a, s0).window.let { it.from != null || it.until != null } }.take(cfg.selfTest)
            .forEach { (q, a) -> record("UNBOUNDED_WINDOW", selfGate.offer(q, a, s0) { it.copy(window = Wsc1.Window(null, null)) }) }
        distinct.take(cfg.selfTest).forEach { (q, a) ->
            record("MISSING_DEPENDENCY", selfGate.offer(q, a, s0) { h -> h.copy(groups = h.groups.entries.drop(1).associate { it.key to it.value }) })
        }

        val factsBefore = w.kb0.facts.size
        val (affected, registry) = applyEvents(w, cfg)
        val s1 = Wsc1.Snapshot(KnowledgeBase(w.builder.entities.toList(), w.builder.facts.values.toList(), registry))
        val systems = listOf(full, cache, wsc1, naive)
        systems.forEach { it.switchTo(s1) }
        val gold = holdout.map { s1.full.answer(it) }
        val wakeKeys = wake.map { it.type.pattern to Wsc1.key(it.mention) }.toSet()
        val repeat = holdout.indices.filter { (holdout[it].type.pattern to Wsc1.key(holdout[it].mention)) in wakeKeys }.toSet()
        val results = systems.map { sys ->
            val before = sys.hits
            val nanos = LongArray(holdout.size)
            val outcomes = holdout.mapIndexed { i, q ->
                val t = System.nanoTime()
                val a = sys.answer(q)
                nanos[i] = System.nanoTime() - t
                outcome(gold[i], a)
            }
            SystemResult(sys.name, outcomes, sys.hits - before, nanos)
        }
        val receiptsMatch = wsc1.all().map { it.receipt }.sorted() == gate.receipts.sorted()
        return Run(results, repeat, gold, gate.verdicts, selfTest, sleepMillis, wsc1.size, receiptsMatch,
            gate.uninformativeCounterfactuals, candidates.size, factsBefore, s1.kb.facts.size, affected)
    }

    private fun micros(n: LongArray, idx: Collection<Int>): Map<String, Double> {
        val v = idx.map { n[it] }.sorted()
        return mapOf("medianMicros" to v[v.size / 2] / 1000.0, "p95Micros" to v[(v.size * 95 + 99) / 100 - 1] / 1000.0)
    }

    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> json(value.toString())
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2) { "usage: <report.json> <commit>" }
        val warm = run()                       // pass 1: warm-up with fresh instances, results must match pass 2
        val r = run()
        check(warm.results.map { it.outcomes } == r.results.map { it.outcomes } && warm.gate == r.gate) { "runs differ" }
        val all = r.gold.indices.toList()
        val holdoutGoldAbstain = r.gold.count { it.kind == Kind.ABSTAIN }
        println("wake candidates ${r.wakeCandidates}, habits ${r.habits}, gate ${r.gate}, sleep ${r.sleepMillis} ms, " +
            "uninformative counterfactuals ${r.uninformative}, receipts recomputable ${r.receiptsMatch}")
        println("gate self-test ${r.selfTest}")
        println("facts ${r.factsBefore} -> ${r.factsAfter}; holdout ${all.size} (gold abstain $holdoutGoldAbstain), previously solved ${r.repeat.size}")
        val systems = linkedMapOf<String, Any>()
        for (s in r.results) {
            val count = { o: Outcome, idx: Collection<Int> -> idx.count { s.outcomes[it] == o } }
            val entry = linkedMapOf("correct" to count(Outcome.CORRECT, all), "wrong" to count(Outcome.WRONG, all),
                "missed" to count(Outcome.MISSED, all), "hits" to s.hits, "hitRate" to s.hits.toDouble() / all.size,
                "wrongOnPreviouslySolved" to count(Outcome.WRONG, r.repeat),
                "latencyAll" to micros(s.nanos, all), "latencyPreviouslySolved" to micros(s.nanos, r.repeat))
            systems[s.name] = entry
            println("${s.name}: $entry")
        }
        val report = linkedMapOf("experiment" to "C-WSC1", "preregistration" to "docs/research/WSC1-CONSOLIDATION-PREREG-20261023.md",
            "commit" to args[1], "jvm" to java.lang.System.getProperty("java.version"),
            "worldSeed" to WORLD_SEED, "streamSeed" to STREAM_SEED, "wakeCandidates" to r.wakeCandidates, "habits" to r.habits,
            "gate" to r.gate, "gateSelfTest" to r.selfTest, "sleepMillis" to r.sleepMillis, "receiptsRecomputable" to r.receiptsMatch,
            "uninformativeCounterfactuals" to r.uninformative, "factsBefore" to r.factsBefore, "factsAfter" to r.factsAfter,
            "events" to (r.affected.mapValues { it.value.size as Any } + ("REVOKED_SOURCE" to REVOKED_SOURCE)),
            "holdout" to all.size, "holdoutGoldAbstain" to holdoutGoldAbstain, "previouslySolved" to r.repeat.size,
            "systems" to systems, "runsIdentical" to true)
        File(args[0]).writeText(json(report) + "\n")
        println("report written: ${args[0]}")
    }
}
