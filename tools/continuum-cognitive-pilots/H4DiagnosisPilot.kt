package at.heinz.deviceai.cognitive

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.ln

object H4DiagnosisPilot {
    private data class Model(val priors: List<Float>, val probes: List<HypothesisWorkspace.Probe>) {
        val ids = priors.indices.map { "h$it" }
    }
    private data class World(val id: String, val stratum: Int, val hiddenTruth: Int, val model: Model)
    private data class Seen(val probe: HypothesisWorkspace.Probe, val value: Boolean?)

    private fun read(path: String): List<World> = File(path).readLines().drop(1).map { line ->
        val fields = line.split('\t')
        require(fields.size == 5)
        val priors = fields[3].split(',').map(String::toFloat)
        val probes = fields[4].split(';').map { encoded ->
            val parts = encoded.split(':')
            require(parts.size == 3 && parts[2].length == priors.size)
            HypothesisWorkspace.Probe(parts[0], parts[1].toFloat(), 0f,
                parts[2].mapIndexed { index, bit ->
                    require(bit == '0' || bit == '1')
                    "h$index" to (bit == '1')
                }.toMap())
        }
        World(fields[0], fields[1].toInt(), fields[2].toInt(), Model(priors, probes))
    }

    private fun possible(model: Model, seen: List<Seen>): Set<String> = model.ids.filter { id ->
        seen.count { it.value != null && it.probe.expectedByHypothesis.getValue(id) != it.value } <= 1
    }.toSet()

    private fun run(world: World, arm: String): String {
        val model = world.model
        val workspace = HypothesisWorkspace()
        val candidates = model.ids.mapIndexed { index, id ->
            HypothesisWorkspace.Hypothesis(id, HypothesisWorkspace.Bucket.entries[index], "Synthetic $id", model.priors[index])
        }
        if (arm == "sequential") candidates.forEach { check(workspace.add(it)) }
        else check(workspace.admitPriors(candidates))
        val seen = mutableListOf<Seen>()
        var cost = 0f
        val bean = ManagementFactory.getThreadMXBean()
        val cpuEnabled = bean.isCurrentThreadCpuTimeSupported && bean.isThreadCpuTimeEnabled
        val cpuStart = if (cpuEnabled) bean.currentThreadCpuTime else 0
        val started = System.nanoTime()
        repeat(3) { step ->
            val available = model.probes.filter { probe ->
                seen.none { it.probe.id == probe.id } && probe.latencyCostMs <= 9f - cost
            }
            val selected = when (arm) {
                "sequential", "batch" -> workspace.selectNextProbe(available)
                "uniform" -> available.firstOrNull()
                else -> error("Unknown arm: $arm")
            } ?: return@repeat
            val observed = when {
                step == 0 && world.stratum in setOf(1, 3) -> null
                step == 1 && world.stratum in setOf(2, 3) ->
                    !selected.expectedByHypothesis.getValue("h${world.hiddenTruth}")
                else -> selected.expectedByHypothesis.getValue("h${world.hiddenTruth}")
            }
            seen += Seen(selected, observed)
            cost += selected.latencyCostMs
            if (observed != null) workspace.observe(selected, observed, "synthetic observation ${selected.id}")
        }
        val feasible = possible(model, seen)
        val wall = System.nanoTime() - started
        val cpu = if (cpuEnabled) bean.currentThreadCpuTime - cpuStart else -1
        val gain = if (feasible.isEmpty()) 0.0 else ln(model.ids.size.toDouble() / feasible.size) / ln(2.0)
        val transcript = seen.joinToString(";") { "${it.probe.id}:${it.value?.let { v -> if (v) "1" else "0" } ?: "?"}" }
        return listOf(world.id, arm, transcript, cost, feasible.sorted().joinToString(","),
            feasible.singleOrNull() ?: "ABSTAIN", workspace.best()?.id ?: "ABSTAIN", wall, cpu, gain).joinToString("\t")
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2) { "Pass worlds.tsv and arm" }
        val worlds = read(args[0])
        repeat(4) { worlds.take(4).forEach { run(it, args[1]) } }
        println("id\tarm\ttranscript\tcost\tfeasible\tanswer\traw\twall_ns\tcpu_ns\tgain_bits")
        worlds.forEach { println(run(it, args[1])) }
    }
}
