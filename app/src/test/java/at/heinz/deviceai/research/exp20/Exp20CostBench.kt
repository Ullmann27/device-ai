package at.heinz.deviceai.research.exp20

import java.lang.management.ManagementFactory
import kotlin.random.Random

/** Primitive signature: a Kotlin (DoubleArray, Double) -> Boolean lambda would box and pollute the allocation count. */
fun interface Stepper { fun step(x: DoubleArray, dt: Double): Boolean }

/**
 * EXP-20 P1 · H20b host cost (NOT a Fold7 measurement). Run: java -cp <test:main:stdlib> at.heinz.deviceai.research.exp20.Exp20CostBench
 * n = 16, 200 000 warm-up steps, then 1000 blocks of 1000 steps; per-step ns p50/p95 over blocks, allocated bytes
 * in the measured loop. CTRNN and the leaky-integrator baseline run with identical inputs and dt.
 */
object Exp20CostBench {
    private const val N = 16

    @JvmStatic fun main(args: Array<String>) {
        val r = Random(7)
        val w = Array(N) { DoubleArray(N) { r.nextDouble(-1.0, 1.0) } }
        for (row in w) { val s = row.sumOf { kotlin.math.abs(it) }; for (j in row.indices) row[j] *= 0.9 / s }
        val tau = DoubleArray(N) { 0.1 * (1 shl (it % 8)) }
        val ctrnn = BoundedCtrnn(tau, w, DoubleArray(N))
        val ema = LeakyIntegrators(tau)
        val inputs = Array(64) { DoubleArray(N) { r.nextDouble(-1.0, 1.0) } }
        val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        fun measure(name: String, stepper: Stepper): String {
            repeat(200_000) { stepper.step(inputs[it and 63], 0.05) }
            val blocks = LongArray(1000)
            val alloc0 = mx.getThreadAllocatedBytes(tid)
            for (b in 0 until 1000) {
                val t0 = System.nanoTime()
                for (k in 0 until 1000) stepper.step(inputs[k and 63], 0.05)
                blocks[b] = System.nanoTime() - t0
            }
            val allocated = mx.getThreadAllocatedBytes(tid) - alloc0
            blocks.sort()
            val p50 = blocks[499] / 1000.0; val p95 = blocks[949] / 1000.0
            return "$name p50_ns=%.1f p95_ns=%.1f allocated_bytes=%d".format(p50, p95, allocated)
        }
        println("EXP20-P1 H20b host=${System.getProperty("java.vm.name")} ${System.getProperty("java.version")} cpus=${Runtime.getRuntime().availableProcessors()} n=$N")
        println(measure("CTRNN", Stepper { x, dt -> ctrnn.step(x, dt) }))
        println(measure("EMA", Stepper { x, dt -> ema.step(x, dt) }))
        println("state_bytes_ctrnn=${8 * (4 * N + N * N)} state_bytes_ema=${8 * 2 * N} checksum=${ctrnn.state()[0] + ema.first()}")
    }
}
