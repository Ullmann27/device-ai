package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalModelReasoningAdapter(
    private val runtime: LocalModelRuntime,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val modelIdProvider: () -> String? = {
        runtime.loadedModel?.let { "${it.id}@${it.version}" }
    },
    private val nowNanos: () -> Long = System::nanoTime,
    private val turnTrace: CognitiveTurnTrace? = null
) : ContinuumReasoningAdapter {

    @Volatile private var cancelled = false
    private var pinnedModel: String? = null

    /** Cancellation is sticky for this job; reuse for sequential passes, never for a new job. */
    override fun cancel() {
        cancelled = true
        runCatching { runtime.cancel() }
    }

    override fun execute(request: ReasoningPassRequest): ReasoningPassResult {
        require(request.prompt.length <= MAX_RUNTIME_PROMPT) {
            "Reasoning prompt exceeds local runtime limit; no silent clipping."
        }
        val started = nowMillis()
        val startNanos = nowNanos()
        val remainingMillis = request.deadlineMillis - started
        // All production callers are bounded to 60s; cap independently even on clock rollback.
        val budgetNanos = remainingMillis.coerceAtMost(MAX_PASS_MILLIS) * 1_000_000L
        val modelId = checkNotNull(modelIdProvider()) { "No local model loaded; no cloud fallback." }
        val modelFingerprint = "$modelId|${runtime.loadedModel?.sha256.orEmpty()}"

        fun gate() {
            if (cancelled || Thread.currentThread().isInterrupted) {
                throw CancellationException("Local reasoning cancelled.")
            }
            val elapsed = nowNanos() - startNanos
            check(remainingMillis > 0 && elapsed >= 0 && elapsed < budgetNanos &&
                nowMillis() < request.deadlineMillis) { "Reasoning pass deadline expired." }
            check(modelIdProvider() == modelId &&
                "$modelId|${runtime.loadedModel?.sha256.orEmpty()}" == modelFingerprint &&
                (pinnedModel == null || pinnedModel == modelFingerprint)) {
                "Local model changed during reasoning; output discarded."
            }
        }

        try {
            gate()
            while (runtime.state != LocalModelState.READY) {
                check(runtime.state != LocalModelState.EMPTY && runtime.state != LocalModelState.FAILED) {
                    "Local model is unavailable."
                }
                Thread.sleep(WAIT_SLICE_MILLIS)
                gate()
            }
            gate()
        } catch (interrupted: InterruptedException) {
            cancel()
            Thread.currentThread().interrupt()
            throw CancellationException("Interrupted while waiting for model.")
        } catch (failure: Exception) {
            cancel()
            throw failure
        }
        pinnedModel = modelFingerprint

        val latch = CountDownLatch(1)
        val lock = Any()
        val text = StringBuilder()
        var failure: Throwable? = null
        var done = false
        var toolEvents = 0

        fun end(error: Throwable?) {
            synchronized(lock) {
                if (done) return
                done = true
                failure = error
                turnTrace?.event(if (error == null) TraceStage.MODEL_COMPLETED else TraceStage.MODEL_FAILED)
            }
            latch.countDown()
        }

        try {
            gate()
            turnTrace?.event(TraceStage.MODEL_ADVISORY_INVOKED)
            runtime.loadedModel?.let { turnTrace?.reference(TraceStage.MODEL_REF, it.sha256) }
            runtime.streamThinking(request.prompt, LocalInferenceCallback { event ->
                when (event) {
                    is LocalInferenceEvent.TextDelta -> synchronized(lock) {
                        if (!done) {
                            if (text.length + event.text.length > request.maxOutputChars) {
                                end(IllegalArgumentException("Reasoning output exceeded limit."))
                            } else {
                                text.append(event.text)
                            }
                        }
                    }
                    is LocalInferenceEvent.Completed -> end(null)
                    is LocalInferenceEvent.Cancelled -> end(CancellationException("Local inference cancelled."))
                    is LocalInferenceEvent.Failed -> end(IllegalStateException(event.message))
                    is LocalInferenceEvent.ToolProposal,
                    is LocalInferenceEvent.ToolProposalRejected,
                    is LocalInferenceEvent.ToolBatchRejected -> {
                        synchronized(lock) { toolEvents++ }
                        end(SecurityException("Reasoning worker attempted a tool proposal."))
                    }
                }
            })

            // Do not depend on the native runtime emitting a terminal event after cancellation.
            while (true) {
                gate()
                if (latch.await(WAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS)) break
            }
            gate()
            synchronized(lock) {
                if (toolEvents > 0) throw SecurityException("Reasoning worker attempted a tool proposal.")
                failure?.let { throw it }
            }
            val output = synchronized(lock) { text.toString() }
            return ReasoningPassResult(
                modelId = modelId,
                text = output,
                latencyMillis = ((nowNanos() - startNanos) / 1_000_000L).coerceAtLeast(0)
            )
        } catch (interrupted: InterruptedException) {
            cancel()
            Thread.currentThread().interrupt()
            throw CancellationException("Reasoning interrupted.")
        } catch (failure: Exception) {
            cancel()
            throw failure
        } finally {
            synchronized(lock) {
                // Ignore late text/terminal callbacks after this pass has returned.
                done = true
            }
        }
    }

    private companion object {
        const val MAX_RUNTIME_PROMPT = 16_000
        const val MAX_PASS_MILLIS = 60_000L
        const val WAIT_SLICE_MILLIS = 10L
    }
}

