package at.heinz.deviceai.learning.lm

import at.heinz.deviceai.learning.lm.reference.LmBpeTokenizer as RefTokenizer
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.security.MessageDigest
import java.util.Random

object LmHexR3Benchmark {
    @Volatile private var sink: String? = null
    private data class Work(val name: String, val iterations: Int, val bytes: ByteArray)
    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + json(it.value) }
        else -> value.toString()
    }
    private fun emit(value: Any?) = println(json(value))
    private fun consume(value: String): Long {
        sink = value
        return value.length.toLong() xor (value.firstOrNull()?.code?.toLong() ?: 0) xor
            ((value.lastOrNull()?.code?.toLong() ?: 0) shl 17)
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2 && args[0] == "hex")
        val phase = args[1]
        val random = Random(37)
        val work = listOf(
            Work("hex-empty", 3000, byteArrayOf()),
            Work("hex-zero", 300, byteArrayOf(0)),
            Work("hex-ff", 300, byteArrayOf(-1)),
            Work("hex-sha256", 50, ByteArray(32).also(random::nextBytes)),
            Work("hex-all-bytes", 4, ByteArray(256) { it.toByte() }),
            Work("hex-1024", 1, ByteArray(1024).also(random::nextBytes))
        )
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported && bean.isCurrentThreadCpuTimeSupported)
        bean.isThreadAllocatedMemoryEnabled = true
        bean.isThreadCpuTimeEnabled = true
        val id = Thread.currentThread().id
        val os = ManagementFactory.getOperatingSystemMXBean()
        val limit = Runtime.getRuntime().availableProcessors()
        check(os.systemLoadAverage <= limit) { "host load ${os.systemLoadAverage} exceeds $limit" }
        emit(mapOf("type" to "meta", "phase" to phase, "group" to "hex", "java" to System.getProperty("java.version"),
            "processors" to limit, "load" to os.systemLoadAverage, "cases" to work.size, "rounds" to 9, "warmupBatches" to 4))
        for (w in work) {
            val fixture = RefTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(w.bytes))
            val before: () -> String = { RefTokenizer.hex(w.bytes) }
            val after: () -> String = { LmBpeTokenizer.hex(w.bytes) }
            for (batch in 0 until 4) {
                val order = if (batch % 2 == 0) listOf(before, after) else listOf(after, before)
                for (f in order) repeat(w.iterations) { consume(f()) }
            }
            for (round in 0 until 9) {
                val order = if (round % 2 == 0) listOf("baseline", "candidate") else listOf("candidate", "baseline")
                var expected: Long? = null
                for (side in order) {
                    val f = if (side == "baseline") before else after
                    val allocated = bean.getThreadAllocatedBytes(id)
                    val cpu = bean.currentThreadCpuTime
                    val wall = System.nanoTime()
                    var checksum = 0L
                    repeat(w.iterations) { checksum += consume(f()) }
                    val wallNs = System.nanoTime() - wall
                    val cpuNs = bean.currentThreadCpuTime - cpu
                    val bytes = bean.getThreadAllocatedBytes(id) - allocated
                    if (expected == null) expected = checksum else check(expected == checksum)
                    emit(mapOf("type" to "sample", "phase" to phase, "case" to w.name, "fixtureSha256" to fixture,
                        "round" to round, "side" to side, "iterations" to w.iterations, "wallNs" to wallNs,
                        "cpuNs" to cpuNs, "allocatedBytes" to bytes, "checksum" to checksum))
                }
            }
            check(before() == after())
        }
        emit(mapOf("type" to "complete", "phase" to phase, "cases" to work.size,
            "load" to os.systemLoadAverage, "sinkClass" to sink?.javaClass?.name))
    }
}
