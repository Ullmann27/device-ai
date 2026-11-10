package at.heinz.deviceai.cognitive

import java.lang.management.ManagementFactory
import java.security.MessageDigest

/** Scripted orchestration trial; no inference. */
object CognitiveBudgetExperiment {
    @JvmStatic fun main(args: Array<String>) {
        val revision = args.first()
        if (args.getOrNull(1) == "modes") {
            modes(revision)
            return
        }
        val cpu = ManagementFactory.getThreadMXBean()
        val decision = CognitiveBudgetController.decide(CognitiveBudgetSignals(thermalStatus = 0), CognitiveBudgetMode.DEEP)
        var fixedCalls = 0
        var adaptiveCalls = 0
        var sameResults = 0
        var nonCycleMatches = 0
        println("case\tarm\tcalls\tcompleted\tprompt_bytes\toutput_bytes\twall_ns\tthread_cpu_ns\tstop\tcandidate_sha256")
        for (case in 0 until 64) {
            val results = mutableMapOf<Boolean, CognitiveBudgetResearchResult>()
            for (adaptive in if (case % 2 == 0) listOf(false, true) else listOf(true, false)) {
                var call = 0
                val adapter = ContinuumReasoningAdapter {
                    val index = call++
                    val text = when (case % 4) {
                        0 -> "fixture-$case-settled"
                        1 -> if (index == 0) "fixture-$case-draft" else "fixture-$case-settled"
                        2 -> "fixture-$case-step-$index"
                        else -> "fixture-$case-cycle-${index % 2}"
                    }
                    ReasoningPassResult("SCRIPTED_NO_MODEL", text, 0)
                }
                val runner = CognitiveBudgetResearchRunner(adapter, revision,
                    CognitiveBudgetResearchConfig(enabled = true, adaptiveStop = adaptive))
                val wall = System.nanoTime()
                val beforeCpu = if (cpu.isCurrentThreadCpuTimeSupported) cpu.currentThreadCpuTime else -1L
                val result = runner.run("fixture-$case", "Synthetic fixture $case", "No facts or tool authority", decision)
                val usedCpu = if (beforeCpu >= 0) cpu.currentThreadCpuTime - beforeCpu else -1L
                val usedWall = System.nanoTime() - wall
                val receipt = result.receipt
                check(receipt.attemptedCalls == call && call <= 4)
                check(receipt.completedCalls == call)
                val hash = MessageDigest.getInstance("SHA-256").digest(requireNotNull(result.candidate).toByteArray())
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                println(listOf(case, if (adaptive) "adaptive" else "fixed", call, receipt.completedCalls,
                    receipt.totalPromptBytes, receipt.totalOutputBytes, usedWall, usedCpu, receipt.stop, hash).joinToString("\t"))
                results[adaptive] = result
                if (adaptive) adaptiveCalls += call else fixedCalls += call
            }
            if (results[true]!!.candidate == results[false]!!.candidate) {
                sameResults++
                if (case % 4 != 3) nonCycleMatches++
            }
        }
        check(fixedCalls == 256 && adaptiveCalls == 192)
        check(sameResults == 48 && nonCycleMatches == 48)
        System.err.println("CONTROLLED_ONLY fixed_calls=$fixedCalls adaptive_calls=$adaptiveCalls identical_candidates=$sameResults/64 non_cycle_identical=$nonCycleMatches/48 cycle_differences=16/16")
    }
    private fun modes(revision: String) {
        println("fixture\tmode\tallowed_calls\tattempted_calls\tcompleted_calls\tstop\tprompt_bytes\toutput_bytes")
        val modes = CognitiveBudgetMode.entries
        for (mode in modes) {
            val decision = CognitiveBudgetController.decide(CognitiveBudgetSignals(thermalStatus = 0), mode)
            val runner = CognitiveBudgetResearchRunner(ContinuumReasoningAdapter {
                ReasoningPassResult("SCRIPTED_NO_MODEL", "unchanged-fixture", 0)
            }, revision, CognitiveBudgetResearchConfig(enabled = true))
            val result = runner.run("mode-${mode.name}", "Explicit synthetic mode fixture", "No event authority", decision)
            val r = result.receipt
            println(listOf("NEW_TASK", mode, decision.maxLocalCalls, r.attemptedCalls, r.completedCalls,
                r.stop, r.totalPromptBytes, r.totalOutputBytes).joinToString("\t"))
        }
    }

}
