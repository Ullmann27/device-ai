package at.heinz.deviceai.localmodel

/** These are application callback boundaries, not native tokenizer/prefill measurements. */
enum class LocalInferenceKind { CHAT, ISOLATED_THINKING, TOOL_CONTINUATION }
enum class LocalInferencePrefill { UNKNOWN_NOT_EXPOSED }
enum class LocalInferenceHeapScope { JVM_HEAP_ONLY }
enum class LocalInferenceTerminal { NONE, COMPLETED, CANCELLED, FAILED }
enum class LocalInferenceCancelOrigin { CALLER, UNLOAD, OUTPUT_LIMIT, TOOL_BATCH_REJECTED }

enum class LocalInferenceStage {
    INVOCATION_ENTER,
    CONVERSATION_SELECTION_ENTER, CONVERSATION_SELECTION_EXIT,
    PREVIOUS_CONVERSATION_RELEASE_ENTER, PREVIOUS_CONVERSATION_RELEASE_EXIT,
    CONVERSATION_CREATE_ENTER, CONVERSATION_CREATE_EXIT,
    INPUT_TOKEN_COUNT_ENTER, INPUT_TOKEN_COUNT_EXIT,
    NATIVE_DISPATCH_ENTER, NATIVE_DISPATCH_RETURN, NATIVE_DISPATCH_THROW,
    FIRST_NATIVE_TEXT_CALLBACK,
    NATIVE_TERMINAL_CALLBACK_ENTER, NATIVE_TERMINAL_CALLBACK_EXIT,
    CANCEL_REQUEST, CANCEL_CALL_EXIT,
    RUNTIME_TERMINAL_DELIVERY_ENTER, RUNTIME_TERMINAL_DELIVERY_EXIT,
    INVOCATION_THROW
}

data class LocalInferenceJvmHeap(val usedBytes: Long, val maxBytes: Long)

/** No request, response, path, model identifier or exception text is retained. */
data class LocalInferenceProfile(
    val id: Long,
    val kind: LocalInferenceKind,
    val invocationStartedNanos: Long,
    val stagesNanos: Map<LocalInferenceStage, Long>,
    val nativeTextChunks: Int,
    val nativeTextCharacters: Int,
    val cancellationRequests: Int,
    val firstCancelOrigin: LocalInferenceCancelOrigin?,
    val nativeTerminal: LocalInferenceTerminal,
    val runtimeTerminal: LocalInferenceTerminal,
    val invocationJvmHeap: LocalInferenceJvmHeap?,
    val terminalJvmHeap: LocalInferenceJvmHeap?,
    val terminalHeapStage: LocalInferenceStage?,
    val clockAnomaly: Boolean,
    val prefill: LocalInferencePrefill = LocalInferencePrefill.UNKNOWN_NOT_EXPOSED,
    val heapScope: LocalInferenceHeapScope = LocalInferenceHeapScope.JVM_HEAP_ONLY
) {
    /** Fixed labels, enum values and numbers only; safe for the existing status-copy UI. */
    fun safeSummary(): String = buildString {
        append("INFERENCE_PROFILE #").append(id).append(' ').append(kind.name)
        append("\nprefill=").append(prefill.name)
        append("\nheapScope=").append(heapScope.name)
        append("\nclock=").append(if (clockAnomaly) "ANOMALY_OBSERVED" else "MONOTONIC_OFFSETS")
        append("\nnativeTerminal=").append(nativeTerminal.name)
        append(" runtimeTerminal=").append(runtimeTerminal.name)
        append("\ntextCallbackChunks=").append(nativeTextChunks)
        append(" textCharacters=").append(nativeTextCharacters)
        append("\ncancelRequests=").append(cancellationRequests)
        append(" firstCancelOrigin=").append(firstCancelOrigin?.name ?: "NONE")
        append("\ninvocationHeapUsedBytes=").append(invocationJvmHeap?.usedBytes ?: -1)
        append(" maxBytes=").append(invocationJvmHeap?.maxBytes ?: -1)
        append("\nterminalHeapUsedBytes=").append(terminalJvmHeap?.usedBytes ?: -1)
        append(" maxBytes=").append(terminalJvmHeap?.maxBytes ?: -1)
        append(" sampleStage=").append(terminalHeapStage?.name ?: "NONE")
        LocalInferenceStage.values().forEach { stage ->
            stagesNanos[stage]?.let {
                append('\n').append(stage.name).append('=').append(it / 1_000_000L).append(" ms")
            }
        }
    }.take(2_500)
}

/** At most eight recent process-local records; never persisted or uploaded. */
internal class LocalInferenceProfiles(
    private val nowNanos: () -> Long = System::nanoTime,
    private val heap: () -> LocalInferenceJvmHeap = {
        val runtime = Runtime.getRuntime()
        LocalInferenceJvmHeap(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory())
    },
    private val capacity: Int = 8
) {
    private val lock = Any()
    private val records = ArrayDeque<LocalInferenceProfileRecorder>()
    private var nextId = 1L

    init { require(capacity in 1..8) }

    fun begin(kind: LocalInferenceKind): LocalInferenceProfileRecorder {
        // Suppliers run before entering a profiler monitor.
        val started = nowNanos()
        val initialHeap = sampleJvmHeap(heap)
        return synchronized(lock) {
            LocalInferenceProfileRecorder(nextId++, kind, started, initialHeap, nowNanos, heap)
                .also {
                    records.addLast(it)
                    while (records.size > capacity) records.removeFirst()
                }
        }
    }

    fun snapshots(): List<LocalInferenceProfile> {
        val current = synchronized(lock) { records.toList() }
        return current.map { it.snapshot() }
    }
}

internal class LocalInferenceProfileRecorder(
    private val id: Long,
    private val kind: LocalInferenceKind,
    private val startedNanos: Long,
    private val initialHeap: LocalInferenceJvmHeap?,
    private val nowNanos: () -> Long,
    private val heap: () -> LocalInferenceJvmHeap
) {
    private val lock = Any()
    private val stages = linkedMapOf(LocalInferenceStage.INVOCATION_ENTER to 0L)
    private var nativeTextChunks = 0
    private var nativeTextCharacters = 0
    private var cancellationRequests = 0
    private var firstCancelOrigin: LocalInferenceCancelOrigin? = null
    private var nativeTerminal = LocalInferenceTerminal.NONE
    private var runtimeTerminal = LocalInferenceTerminal.NONE
    private var terminalHeap: LocalInferenceJvmHeap? = null
    private var terminalHeapStage: LocalInferenceStage? = null
    private var clockAnomaly = false

    fun mark(stage: LocalInferenceStage) {
        val at = nowNanos()
        synchronized(lock) { markAt(stage, at) }
    }

    /** No profiler monitor is held while the measured JNI or callback action executes. */
    fun <T> measure(enter: LocalInferenceStage, exit: LocalInferenceStage, action: () -> T): T {
        mark(enter)
        try { return action() }
        finally { mark(exit) }
    }

    fun nativeText(characters: Int) {
        if (characters <= 0) return
        val at = nowNanos()
        synchronized(lock) {
            markAt(LocalInferenceStage.FIRST_NATIVE_TEXT_CALLBACK, at)
            nativeTextChunks = saturatingAdd(nativeTextChunks, 1)
            nativeTextCharacters = saturatingAdd(nativeTextCharacters, characters)
        }
    }

    fun <T> cancel(origin: LocalInferenceCancelOrigin, action: () -> T): T {
        val at = nowNanos()
        synchronized(lock) {
            markAt(LocalInferenceStage.CANCEL_REQUEST, at)
            cancellationRequests = saturatingAdd(cancellationRequests, 1)
            if (firstCancelOrigin == null) firstCancelOrigin = origin
        }
        try { return action() }
        finally { mark(LocalInferenceStage.CANCEL_CALL_EXIT) }
    }

    fun nativeTerminal(value: LocalInferenceTerminal) {
        terminal(value, LocalInferenceStage.NATIVE_TERMINAL_CALLBACK_ENTER, native = true)
    }

    fun runtimeTerminal(value: LocalInferenceTerminal) {
        terminal(value, LocalInferenceStage.RUNTIME_TERMINAL_DELIVERY_ENTER, native = false)
    }

    private fun terminal(value: LocalInferenceTerminal, stage: LocalInferenceStage, native: Boolean) {
        val at = nowNanos()
        val sampledHeap = sampleJvmHeap(heap)
        synchronized(lock) {
            markAt(stage, at)
            if (native && nativeTerminal == LocalInferenceTerminal.NONE) nativeTerminal = value
            if (!native && runtimeTerminal == LocalInferenceTerminal.NONE) runtimeTerminal = value
            if (terminalHeapStage == null) {
                terminalHeapStage = stage
                terminalHeap = sampledHeap
            }
        }
    }

    fun snapshot(): LocalInferenceProfile = synchronized(lock) {
        LocalInferenceProfile(id, kind, startedNanos, stages.toMap(),
            nativeTextChunks, nativeTextCharacters, cancellationRequests, firstCancelOrigin,
            nativeTerminal, runtimeTerminal, initialHeap, terminalHeap, terminalHeapStage, clockAnomaly)
    }

    private fun markAt(stage: LocalInferenceStage, at: Long) {
        val elapsed = at - startedNanos
        if (elapsed < 0) clockAnomaly = true
        else if (stage !in stages) stages[stage] = elapsed
    }
}

private fun sampleJvmHeap(supplier: () -> LocalInferenceJvmHeap): LocalInferenceJvmHeap? =
    runCatching(supplier).getOrNull()?.takeIf { it.usedBytes >= 0 && it.maxBytes >= 0 }

private fun saturatingAdd(value: Int, increment: Int): Int =
    (value.toLong() + increment).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
