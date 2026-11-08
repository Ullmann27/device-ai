package at.heinz.deviceai.cognitive

import java.lang.management.ManagementFactory
import kotlin.math.abs

object ReasoningPilot {
    private data class Scenario(val name: String, val ids: List<String>, val priors: List<Float>)
    private val scenarios = listOf(
        Scenario("DEV-equal4", listOf("config", "data", "lifecycle", "ui"), List(4) { 1f }),
        Scenario("HOLDOUT-unequal4", listOf("alpha", "beta", "gamma", "delta"), listOf(2f, 3f, 4f, 5f)),
        Scenario("HOLDOUT-equal6", List(6) { "cause-$it" }, List(6) { 3f })
    )

    private fun <T> permutations(values: List<T>): List<List<T>> =
        if (values.isEmpty()) listOf(emptyList())
        else values.flatMap { value -> permutations(values - value).map { listOf(value) + it } }

    private fun workspace(scenario: Scenario, order: List<String>): HypothesisWorkspace =
        HypothesisWorkspace().also { workspace ->
            admitPilot(workspace, order.map { id ->
                val index = scenario.ids.indexOf(id)
                HypothesisWorkspace.Hypothesis(
                    id, HypothesisWorkspace.Bucket.entries[index], "Synthetic cause $id", scenario.priors[index]
                )
            })
        }

    private fun probes(scenario: Scenario): List<HypothesisWorkspace.Probe> {
        val bits = (0..1).map { bit ->
            HypothesisWorkspace.Probe("bit-$bit", 1f, 0f,
                scenario.ids.mapIndexed { index, id -> id to (index and (1 shl bit) != 0) }.toMap())
        }
        val singletons = scenario.ids.map { target ->
            HypothesisWorkspace.Probe("single-$target", 1f, 0f, scenario.ids.associateWith { it == target })
        }
        return bits + singletons
    }

    private fun diagnose(scenario: Scenario, order: List<String>, truth: String): Pair<Int, Int> {
        val workspace = workspace(scenario, order)
        var remaining = scenario.ids.toSet()
        val available = probes(scenario).toMutableList()
        repeat(2) {
            val probe = workspace.selectNextProbe(available) ?: return@repeat
            available.remove(probe)
            val observation = probe.expectedByHypothesis.getValue(truth)
            workspace.observe(probe, observation, "Synthetic fixture observation: ${probe.id}")
            remaining = remaining.filter { probe.expectedByHypothesis[it] == observation }.toSet()
        }
        // Independent consistency check, not max(weight), is the answer oracle.
        return when {
            remaining.size != 1 -> 0 to 1
            remaining.single() == truth -> 1 to 0
            else -> error("False diagnosis")
        }
    }

    private fun dependencyControls(): Int {
        var checked = 0
        for (mask in 0..7) {
            val diagnosed = mask and 1 != 0
            val allowed = mask and 2 != 0
            val verified = mask and 4 != 0
            val jtms = LightweightJtmsEngine()
            jtms.addRule(LightweightJtmsEngine.Rule("plan", setOf("diagnosed")))
            jtms.addRule(LightweightJtmsEngine.Rule("work", setOf("plan", "allowed")))
            jtms.addRule(LightweightJtmsEngine.Rule("success", setOf("work", "verified"), setOf("revoked")))
            jtms.setFact("diagnosed", diagnosed)
            jtms.setFact("allowed", allowed)
            jtms.setFact("verified", verified)
            check((jtms.state("success") == LightweightJtmsEngine.TruthState.VALID_DERIVED) ==
                (diagnosed && allowed && verified))
            jtms.setFact("revoked", true)
            check(jtms.state("success") == LightweightJtmsEngine.TruthState.BLOCKED_BY_EXCEPTION)
            jtms.setFact("revoked", false)
            jtms.setFact("diagnosed", false)
            check(jtms.state("success") != LightweightJtmsEngine.TruthState.VALID_DERIVED)
            checked += 3
        }
        val reality = mapOf("permission" to "denied", "receipt" to "missing")
        val overlay = CounterfactualWorldOverlay(reality, "pilot")
        overlay.assume("permission", "granted")
        overlay.derive("receipt", "hypothetical")
        check(reality == mapOf("permission" to "denied", "receipt" to "missing"))
        check(overlay.scope == CounterfactualWorldOverlay.Scope.COUNTERFACTUAL_SANDBOX)
        return checked + 2
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty() || args.toList() == listOf("--assert"))
        val bean = ManagementFactory.getThreadMXBean()
        val cpuAvailable = bean.isCurrentThreadCpuTimeSupported && bean.isThreadCpuTimeEnabled
        repeat(50) { workspace(scenarios.first(), scenarios.first().ids).selectNextProbe(probes(scenarios.first())) }
        val start = System.nanoTime()
        val cpuStart = if (cpuAvailable) bean.currentThreadCpuTime else 0
        var failedPriors = 0
        println("scenario\tpermutations\tcorrect_priors\tmax_error\tfirst_probe_variants\tdiagnoses\tabstentions")
        for (scenario in scenarios) {
            var correct = 0
            var maxError = 0.0
            var diagnoses = 0
            var abstentions = 0
            val firstProbes = mutableSetOf<String?>()
            val orders = permutations(scenario.ids)
            for (order in orders) {
                val workspace = workspace(scenario, order)
                val expectedSum = scenario.priors.sumOf { it.toDouble() }
                val error = workspace.snapshot().maxOf { hypothesis ->
                    abs(hypothesis.weight.toDouble() -
                        scenario.priors[scenario.ids.indexOf(hypothesis.id)].toDouble() / expectedSum)
                }
                maxError = maxOf(error, maxError)
                if (error <= 1e-6) correct++ else failedPriors++
                if (scenario.ids.size == 4) {
                    firstProbes += workspace.selectNextProbe(probes(scenario))?.id
                    scenario.ids.forEach { truth ->
                        val result = diagnose(scenario, order, truth)
                        diagnoses += result.first
                        abstentions += result.second
                    }
                }
                val useless = HypothesisWorkspace.Probe("no-info", 1f, 0f, scenario.ids.associateWith { true })
                check(workspace.selectNextProbe(listOf(useless)) == null)
            }
            println("${scenario.name}\t${orders.size}\t$correct\t$maxError\t${firstProbes.size}\t$diagnoses\t$abstentions")
        }
        val controls = dependencyControls()
        val cpu = if (cpuAvailable) bean.currentThreadCpuTime - cpuStart else -1
        println("METRICS wall_ns=${System.nanoTime() - start} cpu_ns=$cpu controls=$controls energy_joules=UNAVAILABLE")
        if (args.isNotEmpty()) check(failedPriors == 0) { "$failedPriors prior invariance failures" }
    }
}
