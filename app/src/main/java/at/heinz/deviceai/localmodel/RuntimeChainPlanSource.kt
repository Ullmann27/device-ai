package at.heinz.deviceai.localmodel

import at.heinz.deviceai.cognitive.OwnerChainPlanCodec
import at.heinz.deviceai.cognitive.OwnerChainPlanSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Model plans through the one runtime interface: the same [LocalModelRuntime.streamTextOnly] entry serves the local
 * LiteRT-LM model and the Claude runtime, so a plan proposal needs no knowledge of which one is loaded. One bounded,
 * tool-free generation; the text is data for [OwnerChainPlanCodec] and is never executed as it is.
 */
class RuntimeChainPlanSource(
    private val runtime: LocalModelRuntime,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val cancelled: () -> Boolean = { false }
) : OwnerChainPlanSource {
    init { require(timeoutMillis in 1..MAX_TIMEOUT_MILLIS) }

    override fun propose(prompt: String): String? {
        if (runtime.state != LocalModelState.READY || runtime.loadedModel == null || cancelled()) return null
        val text = StringBuilder()
        val finished = CountDownLatch(1)
        var complete = false
        runtime.streamTextOnly(prompt) { event ->
            when (event) {
                is LocalInferenceEvent.TextDelta -> synchronized(text) {
                    if (text.length <= OwnerChainPlanCodec.MAX_CHARS) text.append(event.text)
                }
                is LocalInferenceEvent.Completed -> { complete = true; finished.countDown() }
                is LocalInferenceEvent.Failed, LocalInferenceEvent.Cancelled -> finished.countDown()
                // A plan is text. A native tool proposal in a tool-free generation is not a plan.
                else -> Unit
            }
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!finished.await(20, TimeUnit.MILLISECONDS)) {
            if (cancelled() || System.nanoTime() >= deadline) {
                runCatching { runtime.cancel() }
                return null
            }
        }
        return synchronized(text) { text.toString() }.takeIf { complete && it.isNotBlank() }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
        const val MAX_TIMEOUT_MILLIS = 180_000L
    }
}
