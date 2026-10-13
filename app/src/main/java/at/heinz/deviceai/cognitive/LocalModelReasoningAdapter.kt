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
    }
) : ContinuumReasoningAdapter {

    override fun execute(request: ReasoningPassRequest): ReasoningPassResult {
        require(request.prompt.length <= MAX_RUNTIME_PROMPT) {
            "Reasoning prompt exceeds local runtime limit; no silent clipping."
        }
        val modelId = checkNotNull(modelIdProvider()) { "No local model loaded; no cloud fallback." }
        val started = nowMillis()
        awaitReady(request.deadlineMillis)

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
            }
            latch.countDown()
        }

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

        val remaining = request.deadlineMillis - nowMillis()
        val finished = try {
            remaining > 0 && latch.await(remaining, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            runtime.cancel()
            Thread.currentThread().interrupt()
            throw CancellationException("Reasoning interrupted.")
        }
        if (!finished) {
            runtime.cancel()
            throw IllegalStateException("Reasoning pass deadline expired.")
        }
        synchronized(lock) {
            failure?.let {
                runtime.cancel()
                throw it
            }
            check(toolEvents == 0)
        }
        val output = synchronized(lock) { text.toString() }
        return ReasoningPassResult(
            modelId = modelId,
            text = output,
            latencyMillis = (nowMillis() - started).coerceAtLeast(0)
        )
    }

    private fun awaitReady(deadlineMillis: Long) {
        while (runtime.state != LocalModelState.READY) {
            check(runtime.state != LocalModelState.EMPTY && runtime.state != LocalModelState.FAILED) {
                "Local model is unavailable."
            }
            check(nowMillis() < deadlineMillis) { "Local model not ready before deadline." }
            try {
                Thread.sleep(5)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw CancellationException("Interrupted while waiting for model.")
            }
        }
    }

    private companion object {
        const val MAX_RUNTIME_PROMPT = 16_000
    }
}
