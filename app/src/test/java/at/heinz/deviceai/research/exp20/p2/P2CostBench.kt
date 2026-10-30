package at.heinz.deviceai.research.exp20.p2

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.random.Random

/**
 * EXP-20 P2 · Part K full cost (preregistered section 8). Host JVM only, NOT a Fold7 measurement.
 *   run <dataDir> <runIndex> <outFile>    one fresh JVM per run; arm order shuffled with Random(runIndex)
 *   aggregate <outFile> <runFiles>...
 * FULL = encode + state update + readout + choose() incl. classifier, on every TRAIN turn (sessions reset per dialog).
 * STATE = decay + injection only, on pre-encoded inputs. ns/turn = block mean over 1000 turns; p50/p95/p99 over 200 blocks.
 */
object P2CostBench {
    private const val WARMUP = 200_000
    private const val BLOCKS = 200
    private const val BLOCK = 1000

    private class SparseKernel(theta: Double) : TemporalKernel {
        private var s = P2Numerics.Sparse(theta); private val th = theta
        override val dim = P2Config.CHANNELS
        override fun reset() { s = P2Numerics.Sparse(th) }
        override fun step(u: DoubleArray, dt: Double): Boolean { if (!dt.isFinite() || dt <= 0.0) return false; s.step(u, dt); return true }
        override fun state() = s.state()
    }

    private class Expm1Kernel : TemporalKernel {
        private var e = P2Numerics.ExpEuler(Double.POSITIVE_INFINITY, true)
        override val dim = P2Config.CHANNELS
        override fun reset() { e = P2Numerics.ExpEuler(Double.POSITIVE_INFINITY, true) }
        override fun step(u: DoubleArray, dt: Double): Boolean { if (!dt.isFinite() || dt <= 0.0) return false; e.step(u, dt); return true }
        override fun state() = e.state()
    }

    fun interface Stepper { fun turn(k: Int): Int }

    /** Primitive payload bytes of state + configuration + readout (no object headers, no JVM overhead). */
    private val payload = mapOf(
        "A" to 0, "A0" to 8 * (10 + 9 + 9),
        "B" to 8 * (16 + 16) + 8 * (16 + 16) + 8 * (26 + 25 + 25),
        "C" to 8 * (16 + 256 + 16 + 16 + 16) + 8 * (16 + 16) + 8 * (26 + 25 + 25),
        "C_SPARSE_1e-2" to 8 * (16 + 256 + 16 * 3) + 8 * (16 + 16) + 8 * (26 + 25 + 25),
        "C_EXPM1" to 8 * (16 + 256 + 16 * 3) + 8 * (16 + 16) + 8 * (26 + 25 + 25))

    @JvmStatic fun main(args: Array<String>) {
        when (args[0]) {
            "run" -> run(File(args[1]), args[2].toInt(), File(args[3]))
            "aggregate" -> aggregate(File(args[1]), args.drop(2).map(::File))
        }
    }

    private fun run(dataDir: File, runIndex: Int, out: File) {
        val train = P2Tsv.read(File(dataDir, "train.tsv"))
        data class T(val text: String, val t: Double, val first: Boolean, val u: DoubleArray)
        val turns = train.flatMap { d -> d.turns.mapIndexed { i, t -> T(t.text, t.tSec, i == 0, P2Config.channels(P2Encoder.encode(t.text))) } }
        val n = turns.size
        fun readout(arm: ArmId, lambda: Double): LogisticReadout { val (zs, y) = P2Eval.trainingSet(arm, train); return LogisticReadout.fit(zs, y, lambda) }
        val rA0 = readout(ArmId.A0, 1.0); val rB = readout(ArmId.B, 0.001); val rC = readout(ArmId.C, 0.001)
        check(rB.sha256() == "e20f32aa9c2a459a2a9dca4342544af7e04418ee7f9af0901afa3b70ca4a9c8f" && rC.sha256() == "a6ed4695bbeb41df14b39dacf01c9f6fdb43b7d5c54c20ac5b3285c02e44ef26") { "readouts differ from freeze" }
        var sink = 0
        fun full(kernel: (() -> TemporalKernel)?, r: LogisticReadout?, thr: Double): Stepper {
            val track = kernel?.let { TemporalTrack(it()) }
            var prevT = 0.0
            return Stepper { k ->
                val tt = turns[k % n]
                if (tt.first) { track?.reset(); prevT = tt.t }
                val x = P2Encoder.encode(tt.text)
                var unresolved = false
                if (r != null) {
                    val z = if (track == null) x + P2Features.gap(tt.t - prevT)
                        else { track.observe(tt.t, P2Config.channels(x)); x + P2Features.gap(track.lastDt) + track.state() }
                    unresolved = r.p(z) >= thr
                }
                prevT = tt.t
                RouterBridge.action(tt.text, unresolved).ordinal
            }
        }
        fun state(kernel: () -> TemporalKernel): Stepper {
            val track = TemporalTrack(kernel())
            return Stepper { k -> val tt = turns[k % n]; if (tt.first) track.reset(); track.observe(tt.t, tt.u).ordinal }
        }
        val benches = linkedMapOf<String, Stepper>(
            "FULL A" to full(null, null, 0.5), "FULL A0" to full(null, rA0, 0.4),
            "FULL B" to full({ LeakyKernel() }, rB, 0.4), "FULL C" to full({ CtrnnKernel() }, rC, 0.5),
            "FULL C_SPARSE_1e-2" to full({ SparseKernel(1e-2) }, rC, 0.5), "FULL C_EXPM1" to full({ Expm1Kernel() }, rC, 0.5),
            "STATE B" to state { LeakyKernel() }, "STATE C" to state { CtrnnKernel() },
            "STATE C_SPARSE_1e-2" to state { SparseKernel(1e-2) }, "STATE C_EXPM1" to state { Expm1Kernel() })
        val order = benches.keys.shuffled(Random(runIndex.toLong()))
        val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val tid = Thread.currentThread().id
        val sb = StringBuilder("# run=$runIndex order=$order jvm=${System.getProperty("java.vm.name")} ${System.getProperty("java.version")} cpus=${Runtime.getRuntime().availableProcessors()} loadavg=${File("/proc/loadavg").readText().trim()}\n")
        for (name in order) {
            val s = benches.getValue(name)
            for (k in 0 until WARMUP) sink += s.turn(k)
            val blocks = DoubleArray(BLOCKS)
            val a0 = mx.getThreadAllocatedBytes(tid)
            var k = WARMUP
            for (b in 0 until BLOCKS) { val t0 = System.nanoTime(); repeat(BLOCK) { sink += s.turn(k++) }; blocks[b] = (System.nanoTime() - t0).toDouble() / BLOCK }
            val alloc = (mx.getThreadAllocatedBytes(tid) - a0).toDouble() / (BLOCKS * BLOCK)
            blocks.sort()
            sb.append("%s\tp50=%.1f\tp95=%.1f\tp99=%.1f\talloc_per_turn=%.1f\n".format(name, blocks[BLOCKS / 2], blocks[(BLOCKS * 95) / 100], blocks[(BLOCKS * 99) / 100], alloc))
        }
        // heap ESTIMATE per kernel+track instance (VM-dependent; includes headers and array overheads)
        val rt = Runtime.getRuntime()
        for ((label, mk) in listOf<Pair<String, () -> Any>>("B" to { TemporalTrack(LeakyKernel()) }, "C" to { TemporalTrack(CtrnnKernel()) },
                "C_SPARSE_1e-2" to { TemporalTrack(SparseKernel(1e-2)) })) {
            repeat(3) { System.gc(); Thread.sleep(50) }
            val before = rt.totalMemory() - rt.freeMemory()
            val keep = List(10_000) { mk() }
            repeat(3) { System.gc(); Thread.sleep(50) }
            val after = rt.totalMemory() - rt.freeMemory()
            sb.append("HEAP_ESTIMATE $label bytes_per_instance=%.0f (n=%d)\n".format((after - before) / 10_000.0, keep.size))
        }
        for ((k, v) in payload) sb.append("PAYLOAD $k bytes=$v\n")
        sb.append("VmRSS ${File("/proc/self/status").readLines().first { it.startsWith("VmRSS") }.substringAfter(':').trim()}\n")
        sb.append("sink=$sink\n")
        out.parentFile?.mkdirs(); out.writeText(sb.toString()); print(sb)
    }

    private fun aggregate(out: File, files: List<File>) {
        val rows = files.flatMap { f -> f.readLines().filter { it.startsWith("FULL") || it.startsWith("STATE") }.map { l ->
            val c = l.split('\t'); c[0] to c.drop(1).associate { kv -> kv.substringBefore('=') to kv.substringAfter('=').toDouble() } } }
        val sb = StringBuilder("EXP-20 P2 Part K aggregate over ${files.size} JVM processes (ns per turn, block means; median and max across runs)\n")
        for ((name, rs) in rows.groupBy({ it.first }, { it.second }).toSortedMap()) {
            fun med(k: String) = rs.map { it.getValue(k) }.sorted().let { it[it.size / 2] }
            fun max(k: String) = rs.maxOf { it.getValue(k) }
            sb.append("%-22s p50 med=%8.1f  p95 med=%8.1f  p99 med=%8.1f max=%8.1f  alloc/turn med=%7.1f  runs=%d\n".format(name, med("p50"), med("p95"), med("p99"), max("p99"), med("alloc_per_turn"), rs.size))
        }
        out.writeText(sb.toString()); print(sb)
    }
}
