package at.heinz.deviceai.cognitive

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/** One bounded diagnostic follow-up to the noisy minimal-control wall times. */
object ContextCompilerControlCheck {
    private const val WARMUP_PAIRS = 10_000
    private const val ROUNDS = 20
    private const val BATCH = 2_000
    @Volatile private var sink: CompiledAiContext? = null

    private data class Measurement(val wallNs: Double, val cpuNs: Double, val allocatedBytes: Double)

    private fun measure(bean: ThreadMXBean, operation: () -> CompiledAiContext): Measurement {
        val thread = Thread.currentThread().id
        val allocatedBefore = bean.getThreadAllocatedBytes(thread)
        val cpuBefore = bean.currentThreadCpuTime
        val started = System.nanoTime()
        repeat(BATCH) { sink = operation() }
        val wall = System.nanoTime() - started
        val cpu = bean.currentThreadCpuTime - cpuBefore
        val bytes = bean.getThreadAllocatedBytes(thread) - allocatedBefore
        return Measurement(wall.toDouble() / BATCH, cpu.toDouble() / BATCH, bytes.toDouble() / BATCH)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty())
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
            ?: error("Host counters unavailable")
        check(bean.isThreadAllocatedMemorySupported && bean.isCurrentThreadCpuTimeSupported)
        bean.isThreadAllocatedMemoryEnabled = true
        bean.isThreadCpuTimeEnabled = true
        // Identical fixture and policy to benchmarkCases().minimal-control.
        val input = ContextIntegrityFixture.input("Wo ist Maren?")
        val policy = AiContextPolicy(includeToolCatalog = false)
        val baseline = BaselineAiContextCompiler(policy, nowMillis = { 100_000L })
        val candidate = AiContextCompiler(policy, nowMillis = { 100_000L })
        val old = { baseline.compile(input) }
        val new = { candidate.compile(input) }
        val expected = old()
        check(expected == new())
        check(expected.text.toByteArray(Charsets.UTF_8).contentEquals(new().text.toByteArray(Charsets.UTF_8)))
        repeat(WARMUP_PAIRS) { iteration ->
            if (iteration % 2 == 0) { sink = old(); sink = new() }
            else { sink = new(); sink = old() }
        }
        val samples = mutableListOf<String>()
        repeat(ROUNDS) { round ->
            val a: Measurement
            val b: Measurement
            if (round % 2 == 0) { a = measure(bean, old); b = measure(bean, new) }
            else { b = measure(bean, new); a = measure(bean, old) }
            check(sink == expected)
            samples += """{"round":$round,"baselineFirst":${round % 2 == 0},"baselineWallNs":${a.wallNs},"candidateWallNs":${b.wallNs},"baselineCpuNs":${a.cpuNs},"candidateCpuNs":${b.cpuNs},"baselineAllocatedBytes":${a.allocatedBytes},"candidateAllocatedBytes":${b.allocatedBytes}}"""
        }
        check(old() == expected && new() == expected)
        println("""{"workload":"minimal-control","warmupPairs":$WARMUP_PAIRS,"rounds":$ROUNDS,"batch":$BATCH,"outputBytes":${expected.text.toByteArray(Charsets.UTF_8).size},"parity":"PASS","samples":[${samples.joinToString(",")}]}""")
    }
}
