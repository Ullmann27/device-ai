package at.heinz.deviceai.learning.lm

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * Read-only host capacity probe (EXP-05/EXP-12), intended for one checkpoint per fresh JVM.
 * The thresholds are engineering choices, not Android acceptance or language-quality evidence.
 * No checkpoint, tokenizer, training state or application setting is written.
 *
 * LmRuntime has no token callback. TTFT is therefore the elapsed time of a separate one-token
 * generate call, including decoding and minGRU's post-token step: an upper bound, not a streamed
 * timestamp. EOS with zero output has no observed TTFT. Both calls use the identical full prompt.
 * The runtime's time limit is cooperative; the caller must also impose a process timeout.
 */
object LmCapacityBenchmark {
    private const val FIRST_TOKEN_LIMIT_MS = 1_500.0
    private const val GENERATION_LIMIT_MS = 5_000.0
    private const val RSS_LIMIT_KIB = 768L * 1024
    private const val REQUEST_MAX_MILLIS = 30_000L
    private const val WARMUPS = 1
    private const val REPEATS = 3
    private const val NEW_TOKENS = 32
    private const val PROMPT = "Ein kurzer Bericht beschreibt den Ablauf einer Prüfung. " +
        "Zuerst wird die Aufgabe gelesen, danach wird das Ergebnis geprüft."

    private data class Memory(val vmHwmKiB: Long?, val vmRssKiB: Long?, val heapUsed: Long,
                              val heapCommitted: Long, val heapMax: Long)
    private data class Timed(val result: LmRuntime.Result, val elapsedMs: Double)
    private data class Loaded(val runtime: LmRuntime, val loadMs: Double, val checkpointBytes: Int,
                              val tokenizerBytes: Int)

    private fun sha(bytes: ByteArray): String =
        LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun fileSha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                if (size > 0) digest.update(buffer, 0, size)
            }
        }
        return LmBpeTokenizer.hex(digest.digest())
    }

    private fun field(value: Any?): String = (value?.toString() ?: "UNAVAILABLE")
        .replace("\t", " ").replace("\r", " ").replace("\n", " ")

    private fun report(kind: String, vararg values: Pair<String, Any?>) =
        println((listOf(kind) + values.map { "${it.first}=${field(it.second)}" }).joinToString("\t"))

    private fun boundedRead(file: File, limit: Int): ByteArray {
        require(!Files.isSymbolicLink(file.toPath()) && file.isFile) { "regular input file required: ${file.path}" }
        require(file.length() in 1..limit.toLong()) { "input size out of bounds: ${file.path}" }
        return file.inputStream().use { it.readNBytes(limit + 1) }.also {
            require(it.isNotEmpty() && it.size <= limit) { "input changed size: ${file.path}" }
        }
    }

    private fun load(checkpoint: File, tokenizer: File): Loaded {
        val start = System.nanoTime()
        val raw = boundedRead(checkpoint, LmRuntime.MAX_CHECKPOINT_BYTES)
        val tokenBytes = boundedRead(tokenizer, 100_000)
        val runtime = LmRuntime.load(raw, tokenBytes, sha(raw))
        check(runtime.model.params.allFinite()) { "non-finite loaded weights" }
        check(runtime.identity.parameters == runtime.model.config.layout().sumOf { (_, r, c) -> r * c }) {
            "incomplete parameter layout"
        }
        return Loaded(runtime, (System.nanoTime() - start) / 1_000_000.0, raw.size, tokenBytes.size)
    }

    private fun memory(): Memory {
        val status = runCatching { File("/proc/self/status").readLines() }.getOrNull().orEmpty()
        fun kib(name: String): Long? = status.firstOrNull { it.startsWith("$name:") }
            ?.trim()?.split(Regex("\\s+"))?.let { parts ->
                if (parts.size == 3 && parts[2] == "kB") parts[1].toLongOrNull() else null
            }
        val heap = Runtime.getRuntime()
        val committed = heap.totalMemory()
        return Memory(kib("VmHWM"), kib("VmRSS"), committed - heap.freeMemory(), committed, heap.maxMemory())
    }

    private fun reportMemory(phase: String): Memory = memory().also {
        report("CAPACITY_MEMORY", "phase" to phase, "vmHwmKiB" to it.vmHwmKiB, "vmRssKiB" to it.vmRssKiB,
            "heapUsedBytes" to it.heapUsed, "heapCommittedBytes" to it.heapCommitted, "heapMaxBytes" to it.heapMax)
    }

    private fun generate(runtime: LmRuntime, requested: Int): Timed {
        val start = System.nanoTime()
        val result = runtime.generate(LmRuntime.Request(PROMPT, maxNewTokens = requested,
            temperature = 0.0, topK = 1, seed = 20261104L, maxMillis = REQUEST_MAX_MILLIS))
        return Timed(result, (System.nanoTime() - start) / 1_000_000.0)
    }

    private fun record(phase: String, trial: Int, requested: Int, measured: Timed) {
        val r = measured.result
        report("CAPACITY_TRIAL", "phase" to phase, "trial" to trial, "requestedNewTokens" to requested,
            "actualNewTokens" to r.newTokens, "promptTokens" to r.promptTokens,
            "promptTruncated" to r.promptTruncated, "stop" to r.stop,
            "elapsedMs" to measured.elapsedMs, "runtimeMillis" to r.millis,
            "ttftUpperBoundMs" to if (requested == 1 && r.newTokens == 1) measured.elapsedMs else null,
            "textSha256" to sha(r.text.toByteArray(Charsets.UTF_8)))
    }

    private fun validate(result: LmRuntime.Result, requested: Int, promptTokens: Int,
                         reasons: MutableSet<String>) {
        if (result.stop == LmRuntime.Stop.TIME || result.stop == LmRuntime.Stop.CANCELLED)
            reasons += "GENERATION_${result.stop}"
        if (result.promptTruncated || result.promptTokens != promptTokens) reasons += "PROMPT_CHANGED_OR_TRUNCATED"
        if (result.stop == LmRuntime.Stop.CONTEXT) reasons += "INSUFFICIENT_CONTEXT"
        if (result.newTokens !in 0..requested) reasons += "INVALID_TOKEN_COUNT"
        if (result.stop == LmRuntime.Stop.MAX_TOKENS && result.newTokens != requested)
            reasons += "INCOMPLETE_MAX_TOKENS_RESULT"
        // Record EOS honestly; the final workload gate rejects incomplete measured outputs.
    }

    @JvmStatic
    fun main(args: Array<String>) {
        report("CAPACITY_PROTOCOL", "firstTokenLimitMs" to FIRST_TOKEN_LIMIT_MS,
            "generation32LimitMs" to GENERATION_LIMIT_MS, "vmHwmLimitKiB" to RSS_LIMIT_KIB,
            "requestMaxMillis" to REQUEST_MAX_MILLIS, "warmups" to WARMUPS, "measuredRepeats" to REPEATS,
            "ttftMethod" to "SEPARATE_ONE_TOKEN_CALL_UPPER_BOUND", "processTimeoutRequired" to true,
            "promptSha256" to sha(PROMPT.toByteArray(Charsets.UTF_8)))
        var checkpointSha: String? = null
        var parameters: Int? = null
        var loadMs: Double? = null
        val reasons = linkedSetOf<String>()
        val firstTimes = ArrayList<Double>()
        val generationTimes = ArrayList<Double>()
        var completed = 0
        var firstTokenObservations = 0
        var full32Observations = 0
        var finalMemory: Memory? = null
        var failure: String? = null
        try {
            require(args.size == 2) { "usage: LmCapacityBenchmark <checkpoint.lm1> <tokenizer.lmbp>" }
            val checkpoint = File(args[0]).absoluteFile
            val tokenizer = File(args[1]).absoluteFile
            reportMemory("before_load")
            val loaded = load(checkpoint, tokenizer)
            val runtime = loaded.runtime
            val c = runtime.model.config
            checkpointSha = runtime.identity.checkpointSha256
            parameters = runtime.identity.parameters
            loadMs = loaded.loadMs
            val promptTokens = runtime.tokenizer.encode(PROMPT).size
            report("CAPACITY_MODEL", "checkpointPath" to checkpoint.path, "checkpointSha256" to checkpointSha,
                "checkpointBytes" to loaded.checkpointBytes, "tokenizerPath" to tokenizer.path,
                "tokenizerSha256" to runtime.identity.tokenizerSha256, "tokenizerBytes" to loaded.tokenizerBytes,
                "parameters" to parameters, "parameterBytes" to runtime.identity.parameterBytes,
                "arch" to c.arch, "vocab" to c.vocab, "dim" to c.dim, "layers" to c.layers,
                "heads" to c.heads, "context" to c.context, "mlp" to c.mlp,
                "loadMs" to loadMs, "promptTokens" to promptTokens, "promptIncludesBos" to false,
                "fullLayoutLoaded" to true, "finiteWeights" to true)
            reportMemory("after_load")
            check(promptTokens + 1 + NEW_TOKENS <= c.context) { "context cannot fit the fixed prompt plus 32 tokens" }
            for (i in 0 until WARMUPS + REPEATS) {
                val phase = if (i < WARMUPS) "warmup" else "measured"
                val trial = if (i < WARMUPS) i + 1 else i - WARMUPS + 1
                val first = generate(runtime, 1)
                record(phase, trial, 1, first)
                validate(first.result, 1, promptTokens, reasons)
                reportMemory("${phase}_${trial}_first")
                val generation = generate(runtime, NEW_TOKENS)
                record(phase, trial, NEW_TOKENS, generation)
                validate(generation.result, NEW_TOKENS, promptTokens, reasons)
                reportMemory("${phase}_${trial}_generation")
                if (phase == "measured") {
                    completed++
                    firstTimes += first.elapsedMs
                    generationTimes += generation.elapsedMs
                    if (first.result.newTokens == 1) firstTokenObservations++
                    if (generation.result.newTokens == NEW_TOKENS) full32Observations++
                }
            }
            if (fileSha(checkpoint) != checkpointSha || fileSha(tokenizer) != runtime.identity.tokenizerSha256)
                reasons += "INPUT_CHANGED_DURING_RUN"
            check(runtime.model.params.allFinite()) { "non-finite weights after generation" }
        } catch (e: Exception) {
            reasons += "LOAD_OR_EXECUTION_FAILURE"
            failure = "${e.javaClass.simpleName}: ${e.message}"
        } catch (e: OutOfMemoryError) {
            reasons += "OUT_OF_MEMORY"
            failure = e.javaClass.simpleName
        } finally {
            finalMemory = reportMemory("final")
        }
        if (completed != REPEATS) reasons += "INCOMPLETE_MEASUREMENTS"
        if (firstTokenObservations != REPEATS) reasons += "NO_FIRST_TOKEN_OBSERVATION"
        if (full32Observations != REPEATS) reasons += "INCOMPLETE_32_TOKEN_WORKLOAD"
        if (firstTimes.any { !it.isFinite() || it > FIRST_TOKEN_LIMIT_MS }) reasons += "FIRST_TOKEN_LIMIT"
        if (generationTimes.any { !it.isFinite() || it > GENERATION_LIMIT_MS }) reasons += "GENERATION_32_LIMIT"
        val hwm = finalMemory?.vmHwmKiB
        if (hwm == null) reasons += "PROCESS_RSS_UNAVAILABLE" else if (hwm > RSS_LIMIT_KIB) reasons += "PROCESS_RSS_LIMIT"
        report("CAPACITY_RESULT", "pass" to reasons.isEmpty(), "reasons" to reasons.joinToString(",").ifEmpty { "NONE" },
            "checkpointSha256" to checkpointSha, "parameters" to parameters, "loadMs" to loadMs,
            "measuredRepeats" to completed, "maxFirstTokenCallMs" to firstTimes.maxOrNull(),
            "maxGeneration32CallMs" to generationTimes.maxOrNull(), "firstTokenObservations" to firstTokenObservations,
            "full32TokenObservations" to full32Observations, "vmHwmKiB" to hwm, "failure" to failure)
        if (reasons.isNotEmpty()) exitProcess(1)
    }
}
