package at.heinz.deviceai.learning.lm

import at.heinz.deviceai.learning.lm.reference.LmBpeTokenizer as RefTokenizer
import java.lang.management.ManagementFactory
import com.sun.management.ThreadMXBean
import kotlin.math.max

object LmTuningR3Benchmark {
    @Volatile private var sink: Any? = null
    private data class Work(val name: String, val iterations: Int, val fixture: String,
        val before: (Int) -> Long, val after: (Int) -> Long, val verify: () -> Unit)
    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    private fun json(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + json(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
        else -> value.toString()
    }
    private fun emit(value: Any?) = println(json(value))
    private fun consume(a: FloatArray): Long { sink = a; return a.size.toLong() xor a.first().toRawBits().toLong() xor (a.last().toRawBits().toLong() shl 17) }
    private fun consume(a: DoubleArray): Long { sink = a; return a.size.toLong() xor a.first().toRawBits() xor a.last().toRawBits() }
    private fun consume(a: IntArray): Long { sink = a; return a.size.toLong() xor (a.firstOrNull()?.toLong() ?: 0) xor ((a.lastOrNull()?.toLong() ?: 0) shl 17) }

    private fun inference(kind: String): List<Work> = buildList {
        for (arch in LmArch.values()) for ((vocab, length) in listOf(258 to 1, 258 to 16, 2048 to 1, 2048 to 64)) {
            val (model, ref) = R3Fixtures.models(arch, vocab)
            val ids = IntArray(length) { (it * 67 + 3) % vocab }
            val targets = IntArray(length) { (it * 31 + 11) % vocab }
            val iterations = if (length == 1) 64 else if (length == 16) 16 else 4
            val before: (Int) -> Long = if (kind == "nll") ({ consume(ref.nll(ids, targets)) }) else ({ consume(ref.nextDistribution(ids)) })
            val after: (Int) -> Long = if (kind == "nll") ({ consume(model.nll(ids, targets)) }) else ({ consume(model.nextDistribution(ids)) })
            val verify = {
                if (kind == "nll") check(ref.nll(ids, targets).map { it.toRawBits() } == model.nll(ids, targets).map { it.toRawBits() })
                else check(ref.nextDistribution(ids).map { it.toRawBits() } == model.nextDistribution(ids).map { it.toRawBits() })
            }
            add(Work("$kind-${arch.name}-v$vocab-t$length", iterations, R3Fixtures.hash(ids.joinToString() + targets.joinToString()), before, after, verify))
        }
    }
    private fun tokenization(): List<Work> = buildList {
        val fixed = listOf("" to 2000, "a" to 2000, "Hallo Heinz, der Kern prüft." to 1000,
            "Grüße 👨‍👩‍👧‍👧 中文 e\u0301 123! ".repeat(16) to 100)
        for (learned in listOf(false, true)) for (case in 0..4) {
            val tok = if (learned) LmBpeTokenizer.train(R3Fixtures.corpus, 384) else LmBpeTokenizer.bytes()
            val ref = RefTokenizer.deserialize(tok.serialize())
            val iterations = if (case == 4) 100 else fixed[case].second
            val texts = if (case == 4) List(iterations * 13) { i ->
                "Heinz prüft " + String(CharArray(6) { p -> ('a'.code + (i / Math.pow(26.0, p.toDouble()).toInt()) % 26).toChar() })
            } else listOf(fixed[case].first)
            val name = "token-${if (learned) "merged" else "bytes"}-${if (case == 4) "unique-word" else listOf("empty", "single", "sentence", "unicode")[case]}"
            val before: (Int) -> Long = { i -> consume(ref.encode(texts[i % texts.size])) }
            val after: (Int) -> Long = { i -> consume(tok.encode(texts[i % texts.size])) }
            val verify = {
                for (text in texts) check(ref.encode(text).contentEquals(tok.encode(text)))
                check(ref.serialize().contentEquals(tok.serialize()))
            }
            add(Work(name, iterations, R3Fixtures.hash(texts.joinToString("\u0000")), before, after, verify))
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2 && args[0] in listOf("all", "nll", "distribution", "tokenizer"))
        val group = args[0]; val phase = args[1]
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported && bean.isCurrentThreadCpuTimeSupported)
        bean.isThreadAllocatedMemoryEnabled = true; bean.isThreadCpuTimeEnabled = true
        val id = Thread.currentThread().id
        val os = ManagementFactory.getOperatingSystemMXBean()
        val work = buildList {
            if (group == "all" || group == "nll") addAll(inference("nll"))
            if (group == "all" || group == "distribution") addAll(inference("distribution"))
            if (group == "all" || group == "tokenizer") addAll(tokenization())
        }
        val limit = Runtime.getRuntime().availableProcessors()
        check(os.systemLoadAverage <= limit) { "host load ${os.systemLoadAverage} exceeds $limit" }
        emit(mapOf("type" to "meta", "phase" to phase, "group" to group, "java" to System.getProperty("java.version"),
            "processors" to limit, "load" to os.systemLoadAverage, "cases" to work.size, "rounds" to 9, "warmupBatches" to 4))
        for (w in work) {
            for (batch in 0 until 4) {
                val order = if (batch % 2 == 0) listOf(w.before, w.after) else listOf(w.after, w.before)
                for (f in order) repeat(w.iterations) { f(batch * w.iterations + it) }
            }
            for (round in 0 until 9) {
                val order = if (round % 2 == 0) listOf("baseline", "candidate") else listOf("candidate", "baseline")
                var expected: Long? = null
                for (side in order) {
                    val f = if (side == "baseline") w.before else w.after
                    val allocated = bean.getThreadAllocatedBytes(id); val cpu = bean.currentThreadCpuTime; val wall = System.nanoTime()
                    var checksum = 0L
                    repeat(w.iterations) { checksum += f((round + 4) * w.iterations + it) }
                    val wallNs = System.nanoTime() - wall; val cpuNs = bean.currentThreadCpuTime - cpu
                    val bytes = bean.getThreadAllocatedBytes(id) - allocated
                    if (expected == null) expected = checksum else check(expected == checksum)
                    emit(mapOf("type" to "sample", "phase" to phase, "case" to w.name, "fixtureSha256" to w.fixture,
                        "round" to round, "side" to side, "iterations" to w.iterations, "wallNs" to wallNs,
                        "cpuNs" to cpuNs, "allocatedBytes" to bytes, "checksum" to checksum))
                }
            }
            w.verify()
        }
        emit(mapOf("type" to "complete", "phase" to phase, "cases" to work.size, "load" to os.systemLoadAverage, "sinkClass" to sink?.javaClass?.name))
    }
}
