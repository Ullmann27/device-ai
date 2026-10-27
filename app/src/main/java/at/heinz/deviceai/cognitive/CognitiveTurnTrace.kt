package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ToolExecutionStatus
import java.security.MessageDigest
import java.util.UUID

enum class TraceStage {
    MEMORY_LOOKUP, MEMORY_REF, SOURCE_REF, MODEL_REF, CALL_REF, MEMORY_EXACT,
    CTX3_SHADOW, CTX3_OFF, CRE1_SHADOW, CRE1_ACTIVE, ROUTER_ADVISORY,
    MODEL_PRIMARY_INVOKED, MODEL_ADVISORY_INVOKED, MODEL_COMPLETED, MODEL_FAILED,
    MODEL_NOT_READY, GATED_FALLBACK, LOCAL_MODEL_SYNTHESIS, TOOL_PROPOSED, TOOL_EXECUTED,
    CORRECTION_SCOPE, CLARIFICATION, TEST_DOUBLE_CALL
}
enum class TraceOutcome { MEMORY_EXACT, MODEL_INTERPRETATION, ACTION_VERIFIED, BLOCKED, FAILED, CANCELLED, CLARIFY, WORKING_CONTEXT }
enum class TraceFeedback { NONE, CONFIRMED, CORRECTED, REVOKED }
data class CognitiveTraceEvent(val stage: TraceStage, val referenceHash: String? = null,
    val toolStatus: ToolExecutionStatus? = null) {
    init { require(referenceHash == null || referenceHash.matches(Regex("[a-f0-9]{64}"))) }
}
data class CognitiveTraceSnapshot(val id: String, val sessionHash: String, val elapsedMillis: Long,
    val events: List<CognitiveTraceEvent>, val outcome: TraceOutcome, val incomplete: Boolean,
    val feedback: TraceFeedback = TraceFeedback.NONE) {
    init {
        require(id.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")))
        require(sessionHash.matches(Regex("[a-f0-9]{64}")))
        require(elapsedMillis >= 0 && events.size <= 64)
    }
    val modelInvocations: Int get() = events.count { it.stage in setOf(TraceStage.MODEL_PRIMARY_INVOKED, TraceStage.MODEL_ADVISORY_INVOKED) }
    val completedModelCalls: Int get() = events.count { it.stage == TraceStage.MODEL_COMPLETED }
    val advisoryCalls: Int get() = events.count { it.stage == TraceStage.MODEL_ADVISORY_INVOKED }
}

/** Metadata only: no prompts, replies, arbitrary labels, filenames or raw IDs.
 * ID fingerprints are owner-local lookup aids, not anonymization or factual proof.
 * Invocation != completion; model interpretation never becomes verified memory.
 */
class CognitiveTurnTrace(sessionId: String, val id: String = UUID.randomUUID().toString(),
    private val nowNanos: () -> Long = System::nanoTime) {
    private val sessionHash = fingerprint(sessionId)
    private val started = nowNanos()
    private val events = mutableListOf<CognitiveTraceEvent>()
    private var incomplete = false
    private var final: CognitiveTraceSnapshot? = null

    @Synchronized fun event(stage: TraceStage, status: ToolExecutionStatus? = null) = add(CognitiveTraceEvent(stage, toolStatus = status))
    @Synchronized fun reference(stage: TraceStage, opaqueId: String) = add(CognitiveTraceEvent(stage, fingerprint(opaqueId)))
    private fun add(event: CognitiveTraceEvent) {
        if (final != null) return
        if (events.size == 64) { incomplete = true; return }
        events += event
    }
    @Synchronized fun finish(outcome: TraceOutcome): CognitiveTraceSnapshot {
        final?.let { return it }
        return CognitiveTraceSnapshot(id, sessionHash, ((nowNanos() - started).coerceAtLeast(0) / 1_000_000),
            events.toList(), outcome, incomplete).also { final = it }
    }
    companion object {
        private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

/** Strict allowlisted TSV row; never serializes arbitrary objects or model text. */
object CognitiveTraceCodec {
    fun encode(value: CognitiveTraceSnapshot): String = listOf("1", value.id, value.sessionHash,
        value.elapsedMillis.toString(), value.outcome.name, value.incomplete.toString(), value.feedback.name,
        value.events.joinToString(";") { "${it.stage.name},${it.referenceHash ?: "-"},${it.toolStatus?.name ?: "-"}" }).joinToString("\t")
    fun decode(row: String): CognitiveTraceSnapshot {
        require(row.length <= 8192)
        val p = row.split('\t'); require(p.size == 8 && p[0] == "1")
        val elapsed = p[3].toLongOrNull(); require(elapsed != null && elapsed >= 0 && elapsed.toString() == p[3])
        require(p[5] == "true" || p[5] == "false")
        val events = if (p[7].isEmpty()) emptyList() else p[7].split(';').map { field ->
            val e = field.split(','); require(e.size == 3)
            CognitiveTraceEvent(TraceStage.valueOf(e[0]), e[1].takeUnless { it == "-" },
                e[2].takeUnless { it == "-" }?.let(ToolExecutionStatus::valueOf))
        }
        return CognitiveTraceSnapshot(p[1], p[2], elapsed, events, TraceOutcome.valueOf(p[4]), p[5].toBoolean(), TraceFeedback.valueOf(p[6]))
    }
}
