package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.intelligence.maxultra.Ttt1.Outcome
import java.io.File

/**
 * Experiment A (TTT1) run, pre-registered in docs/research/TTT1-WORKING-MEMORY-PREREG-20261023.md.
 * Run: java ... at.heinz.deviceai.intelligence.maxultra.Ttt1Experiment dev|test <report.json> <commit>
 */
object Ttt1Experiment {
    const val DEV_SEED = 20261027L
    const val TEST_SEED = 20261028L
    const val DEV_PER_VARIANT = 10
    const val TEST_PER_VARIANT = 40
    val FW_SEEDS = listOf(1L, 2L, 3L, 4L, 5L)

    fun split(name: String, seed: Long, perVariant: Int, exclude: Set<String>): Pair<List<Ttt1.Dialog>, Set<String>> {
        val g = Ttt1.Generator(seed, exclude)
        val dialogs = Ttt1.VARIANTS.flatMap { v -> (0 until perVariant).map { i -> g.dialog(v, "$name-${v.lowercase()}-$i") } }
        return dialogs to g.used.toSet()
    }

    class Tally(val system: String) {
        val perVariant = linkedMapOf<String, IntArray>()
        val bytes = ArrayList<Int>()
        fun add(v: String, o: Outcome) { perVariant.getOrPut(v) { IntArray(3) }[o.ordinal]++ }
        fun total(o: Outcome) = perVariant.values.sumOf { it[o.ordinal] }
    }

    fun evaluate(memory: Ttt1.Memory, dialogs: List<Ttt1.Dialog>, nanos: MutableList<Long>? = null): Tally {
        val t = Tally(memory.name)
        for (d in dialogs) {
            val (outcomes, peak) = Ttt1.run(memory, d, nanos)
            outcomes.forEach { t.add(d.variant, it) }
            t.bytes += peak
        }
        return t
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
        require(args.size == 3 && args[0] in setOf("dev", "test")) { "usage: dev|test <report.json> <commit>" }
        val (dev, devNames) = split("dev", DEV_SEED, DEV_PER_VARIANT, emptySet())
        val (test, testNames) = split("test", TEST_SEED, TEST_PER_VARIANT, devNames)
        check(devNames.intersect(testNames).isEmpty())
        val dialogs = if (args[0] == "dev") dev else test
        val memories = { seed: Long -> listOf(Ttt1.BoundedContext(), Ttt1.EntityStore(), Ttt1.StoreWithFastWeights(seed)) }
        memories(1L).forEach { m -> repeat(3) { evaluate(m, dialogs) } }   // warm-up
        val timing = HashMap<String, MutableList<Long>>()
        val tallies = memories(1L).map { m -> evaluate(m, dialogs, timing.getOrPut(m.name) { ArrayList() }) }
        val seeds = FW_SEEDS.associateWith { evaluate(Ttt1.StoreWithFastWeights(it), dialogs) }
        println("${args[0]}: ${dialogs.size} dialogs, ${tallies[0].perVariant.values.sumOf { it.sum() }} scored turns")
        println("variant".padEnd(18) + tallies.joinToString("") { it.system.padStart(14) } + "   (richtig/falsch/unnötige Rückfrage)")
        for (v in Ttt1.VARIANTS) println(v.padEnd(18) + tallies.joinToString("") { t ->
            (t.perVariant[v] ?: IntArray(3)).joinToString("/").padStart(14) })
        for (o in Outcome.entries) println(o.name.padEnd(18) + tallies.joinToString("") { it.total(o).toString().padStart(14) })
        val stats = tallies.associate { t ->
            val n = timing.getValue(t.system).sorted()
            t.system to mapOf("medianNanosPerTurn" to n[n.size / 2], "p95NanosPerTurn" to n[(n.size * 95 + 99) / 100 - 1],
                "peakStateBytes" to t.bytes.max(), "meanPeakStateBytes" to t.bytes.average())
        }
        println("time/state: $stats")
        println("S+FW seeds: " + seeds.entries.joinToString { "seed ${it.key}: wrong ${it.value.total(Outcome.WRONG)}, correct ${it.value.total(Outcome.CORRECT)}" })
        if (args[0] == "dev") return
        val report = linkedMapOf("experiment" to "A-TTT1", "preregistration" to "docs/research/TTT1-WORKING-MEMORY-PREREG-20261023.md",
            "commit" to args[2], "jvm" to java.lang.System.getProperty("java.version"), "devSeed" to DEV_SEED, "testSeed" to TEST_SEED,
            "dialogs" to dialogs.size, "systems" to tallies.associate { t ->
                t.system to mapOf("correct" to t.total(Outcome.CORRECT), "wrong" to t.total(Outcome.WRONG),
                    "unnecessaryAsk" to t.total(Outcome.UNNECESSARY_ASK),
                    "perVariant" to t.perVariant.mapValues { (_, n) -> n.toList() }) + stats.getValue(t.system) },
            "fastWeightSeeds" to seeds.mapKeys { "seed${it.key}" }.mapValues { (_, t) ->
                mapOf("correct" to t.total(Outcome.CORRECT), "wrong" to t.total(Outcome.WRONG), "unnecessaryAsk" to t.total(Outcome.UNNECESSARY_ASK),
                    "perVariant" to t.perVariant.mapValues { (_, n) -> n.toList() }) })
        File(args[1]).writeText(json(report) + "\n")
        println("report written: ${args[1]}")
    }
}
