package at.heinz.deviceai.learning.lm

import java.lang.management.ManagementFactory
import java.util.Random

/** Host-only, preregistered round-one/candidate comparison with identical inputs and RNG seeds. */
object LmSamplingRound2Benchmark {
    @Volatile private var sink = 0L

    private data class Scenario(val size: Int, val kind: String, val temperature: Double, val topK: Int) {
        val id = "$size-$kind-$temperature-$topK"
    }

    @JvmStatic fun main(args: Array<String>) {
        val phase = args.single()
        require(phase == "before" || phase == "after")
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported && bean.isCurrentThreadCpuTimeSupported)
        bean.isThreadAllocatedMemoryEnabled = true
        bean.isThreadCpuTimeEnabled = true
        println("{\"phase\":\"$phase\",\"java\":\"${System.getProperty("java.runtime.version")}\",\"vm\":\"${System.getProperty("java.vm.name")}\",\"arch\":\"${System.getProperty("os.arch")}\",\"processors\":${Runtime.getRuntime().availableProcessors()},\"load\":${ManagementFactory.getOperatingSystemMXBean().systemLoadAverage}}")
        val scenarios = buildList {
            for (size in intArrayOf(258, 512, 2048, 8192)) {
                for (kind in listOf("random", "ties")) add(Scenario(size, kind, 0.0, 1))
            }
            add(Scenario(512, "random", 1.0, 50))
            add(Scenario(512, "random", 1.0, 1))
            add(Scenario(512, "random", 1.0, 512))
            add(Scenario(512, "ties", 1.0, 50))
            add(Scenario(2048, "random", .8, 40))
            add(Scenario(2048, "random", 1.0, 2048))
            add(Scenario(8192, "random", 1.0, 50))
            add(Scenario(8192, "random", 1.0, 8192))
            add(Scenario(512, "random", Double.NaN, 50))
            add(Scenario(512, "special", 1.0, 50))
        }
        for (scenario in scenarios) measure(bean, phase, scenario)
        println("{\"phase\":\"$phase\",\"checksum\":$sink}")
    }

    private fun measure(bean: com.sun.management.ThreadMXBean, phase: String, scenario: Scenario) {
        val random = Random(731L + scenario.size)
        val inputs = Array(32) {
            val p = FloatArray(scenario.size) { if (scenario.kind == "ties") 1f else random.nextFloat() }
            val total = p.sum()
            for (i in p.indices) p[i] /= total
            if (scenario.kind == "special") {
                p[0] = Float.NaN; p[1] = Float.POSITIVE_INFINITY; p[2] = -0.0f
                p[3] = 0f; p[4] = Float.NEGATIVE_INFINITY; p[5] = -1f
            }
            p
        }
        val operations = when (scenario.size) { 8192 -> 128; 2048 -> 384; else -> 1024 }
        repeat(4) {
            runBatch(inputs, scenario, operations, true)
            runBatch(inputs, scenario, operations, false)
        }
        repeat(9) { repetition ->
            for (reference in if (repetition % 2 == 0) listOf(true, false) else listOf(false, true)) {
                val thread = Thread.currentThread().id
                val bytes = bean.getThreadAllocatedBytes(thread)
                val cpu = bean.currentThreadCpuTime
                val start = System.nanoTime()
                val checksum = runBatch(inputs, scenario, operations, reference)
                val elapsed = System.nanoTime() - start
                val cpuElapsed = bean.currentThreadCpuTime - cpu
                val allocated = bean.getThreadAllocatedBytes(thread) - bytes
                val implementation = if (reference) "round_one_reference" else "production"
                println("{\"phase\":\"$phase\",\"scenario\":\"${scenario.id}\",\"vocab\":${scenario.size},\"kind\":\"${scenario.kind}\",\"temperature\":\"${scenario.temperature}\",\"topK\":${scenario.topK},\"repetition\":$repetition,\"implementation\":\"$implementation\",\"operations\":$operations,\"wallNs\":$elapsed,\"cpuNs\":$cpuElapsed,\"allocatedBytes\":$allocated,\"checksum\":$checksum}")
            }
        }
    }

    private fun runBatch(inputs: Array<FloatArray>, scenario: Scenario, operations: Int, reference: Boolean): Long {
        val random = Random(97)
        var checksum = 0L
        for (i in 0 until operations) {
            val p = inputs[i % inputs.size]
            val token = if (reference) RoundOneSamplingReference.sample(p, scenario.temperature, scenario.topK, random)
                else LmEval.sample(p, scenario.temperature, scenario.topK, random)
            checksum = checksum * 31 + token
        }
        checksum = checksum xor random.nextLong()
        sink = checksum
        return checksum
    }
}
