package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real adapter/coordinator boundaries; scripted runtime events are not a model-quality benchmark. */
class LocalReasoningBoundaryRegressionTest {
    private fun request(deadline: Long = 2_000) = ReasoningPassRequest(
        "boundary", ReasoningPassRole.CANDIDATE, "Review this source", 1_000, deadline
    )

    @Test fun expiredReadyRequestNeverStartsInference() {
        val runtime = Runtime { it.onEvent(LocalInferenceEvent.TextDelta("answer")); it.onEvent(completed()) }
        val adapter = LocalModelReasoningAdapter(runtime, { 2_000 }, { "model@1" })
        assertThrows(IllegalStateException::class.java) { adapter.execute(request()) }
        assertEquals(0, runtime.starts)
    }

    @Test fun interruptedReadyThreadNeverStartsInference() {
        val runtime = Runtime { it.onEvent(LocalInferenceEvent.TextDelta("answer")); it.onEvent(completed()) }
        val adapter = LocalModelReasoningAdapter(runtime, { 1_000 }, { "model@1" })
        try {
            Thread.currentThread().interrupt()
            assertThrows(java.util.concurrent.CancellationException::class.java) { adapter.execute(request()) }
            assertEquals(0, runtime.starts)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }

    @Test fun changedModelIdentityInvalidatesOutput() {
        var model = "model@1"
        val runtime = Runtime { callback ->
            model = "other-model@2"
            callback.onEvent(LocalInferenceEvent.TextDelta("answer"))
            callback.onEvent(completed())
        }
        val adapter = LocalModelReasoningAdapter(runtime, { 1_000 }, { model })
        assertThrows(IllegalStateException::class.java) { adapter.execute(request()) }
        assertTrue(runtime.cancelled)
    }

    @Test fun toolsAfterCompletionStillInvalidatePass() {
        val runtime = Runtime { callback ->
            callback.onEvent(LocalInferenceEvent.TextDelta("answer"))
            callback.onEvent(completed())
            callback.onEvent(LocalInferenceEvent.ToolProposalRejected("delete_file", "late tool"))
        }
        val adapter = LocalModelReasoningAdapter(runtime, { 1_000 }, { "model@1" })
        assertThrows(SecurityException::class.java) { adapter.execute(request()) }
        assertTrue(runtime.cancelled)
    }

    @Test fun stopReleasesDevelopmentWorkerWithoutRuntimeTerminalEvent() {
        val started = CountDownLatch(1)
        val runtime = Runtime { started.countDown() } // Intentionally never emits Completed/Cancelled.
        val adapter = LocalModelReasoningAdapter(runtime, modelIdProvider = { "model@1" })
        val coordinator = DevelopmentCoordinator(adapter, runtime::cancel)
        val executor = Executors.newSingleThreadExecutor()
        val job = executor.submit<DevelopmentOutcome> {
            coordinator.run(DevelopmentTask("stop", "Review increment", listOf(
                SourceSnapshot.of("Counter.kt", "fun increment(n: Int) = n - 1")
            ), DevelopmentLimits(maxDurationMillis = 3_000)))
        }
        try {
            assertTrue(started.await(1, TimeUnit.SECONDS))
            coordinator.cancel()
            val outcome = job.get(500, TimeUnit.MILLISECONDS)
            assertEquals(DevelopmentJobState.CANCELLED, outcome.state)
            assertNull(outcome.proposal)
            assertEquals(1, runtime.starts)
            assertTrue(runtime.cancelled)
        } finally {
            job.cancel(true)
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS))
        }
    }

    @Test fun unchangedModelProducesDataOnlyResult() {
        val runtime = Runtime { callback ->
            callback.onEvent(LocalInferenceEvent.TextDelta("answer"))
            callback.onEvent(completed())
        }
        val result = LocalModelReasoningAdapter(runtime, { 1_000 }, { "model@1" }).execute(request())
        assertEquals("answer", result.text)
        assertEquals("model@1", result.modelId)
        assertEquals(1, runtime.starts)
        assertFalse(runtime.cancelled)
    }

    private fun completed() = LocalInferenceEvent.Completed(LocalInferenceMetrics(0, 0, 1, null, null, null, 0))

    private class Runtime(val behavior: (LocalInferenceCallback) -> Unit) : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel: ImportedLocalModel? = null
        @Volatile var starts = 0
        @Volatile var cancelled = false
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("unused")
        override fun unload(callback: (Result<Unit>) -> Unit) = error("unused")
        override fun stream(prompt: String, callback: LocalInferenceCallback) = error("No chat/tool channel allowed")
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
            starts++
            behavior(callback)
        }
        override fun cancel() { cancelled = true }
    }
}
