package at.heinz.deviceai.learning.lm

import java.lang.management.ManagementFactory
import java.util.Locale
import java.util.Random

/** Host-only paired microbenchmark. Each result is one complete batch, never a phone measurement. */
object LmSamplingBenchmark {
    @Volatile private var sink = 0L

    @JvmStatic fun main(args: Array<String>) {
        val phase = args.single()
        require(phase == "before" || phase == "after")
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported && bean.isCurrentThreadCpuTimeSupported)
        bean.isThreadAllocatedMemoryEnabled = true
        bean.isThreadCpuTimeEnabled = true
        println("{\"phase\":\"$phase\",\"java\":\"${System.getProperty("java.runtime.version")}\",\"vm\":\"${System.getProperty("java.vm.name")}\",\"arch\":\"${System.getProperty("os.arch")}\",\"processors\":${Runtime.getRuntime().availableProcessors()},\"load\":${ManagementFactory.getOperatingSystemMXBean().systemLoadAverage}}")
        for (size in intArrayOf(258, 512, 2048, 8192)) {
            for (kind in listOf("random", "ties")) measure(bean, phase, size, kind, 0.0, 1)
        }
        measure(bean, phase, 512, "random", 1.0, 50)
        println("{\"phase\":\"$phase\",\"checksum\":$sink}")
    }

    private fun measure(bean: com.sun.management.ThreadMXBean, phase: String, size: Int,
                        kind: String, temperature: Double, topK: Int) {
        val random = Random(731L + size)
        val inputs = Array(32) {
            val p = FloatArray(size) { if (kind == "ties") 1f else random.nextFloat() }
            val total = p.sum()
            for (i in p.indices) p[i] /= total
            p
        }
        val operations = when (size) { 8192 -> 128; 2048 -> 384; else -> 1024 }
        repeat(4) {
            runBatch(inputs, temperature, topK, operations, true)
            runBatch(inputs, temperature, topK, operations, false)
        }
        repeat(9) { repetition ->
            for (reference in if (repetition % 2 == 0) listOf(true, false) else listOf(false, true)) {
                val thread = Thread.currentThread().id
                val bytes = bean.getThreadAllocatedBytes(thread)
                val cpu = bean.currentThreadCpuTime
                val start = System.nanoTime()
                val checksum = runBatch(inputs, temperature, topK, operations, reference)
                val elapsed = System.nanoTime() - start
                val cpuElapsed = bean.currentThreadCpuTime - cpu
                val allocated = bean.getThreadAllocatedBytes(thread) - bytes
                println(String.format(Locale.ROOT,
                    "{\"phase\":\"%s\",\"vocab\":%d,\"kind\":\"%s\",\"temperature\":%.1f,\"topK\":%d,\"repetition\":%d,\"implementation\":\"%s\",\"operations\":%d,\"wallNs\":%d,\"cpuNs\":%d,\"allocatedBytes\":%d,\"checksum\":%d}",
                    phase, size, kind, temperature, topK, repetition,
                    if (reference) "sorted_reference" else "production", operations, elapsed, cpuElapsed, allocated, checksum))
            }
        }
    }

    private fun runBatch(inputs: Array<FloatArray>, temperature: Double, topK: Int, operations: Int,
                         reference: Boolean): Long {
        val random = Random(97)
        var checksum = 0L
        for (i in 0 until operations) {
            val p = inputs[i % inputs.size]
            checksum += if (reference) SortedSamplingReference.sample(p, temperature, topK, random)
                else LmEval.sample(p, temperature, topK, random)
        }
        sink = checksum
        return checksum
    }
}
