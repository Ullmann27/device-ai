package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalInferenceCallback
import at.heinz.deviceai.localmodel.LocalInferenceEvent
import at.heinz.deviceai.localmodel.LocalInferenceMetrics
import at.heinz.deviceai.localmodel.LocalModelRuntime
import at.heinz.deviceai.localmodel.LocalModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelReasoningAdapterTest {
    private fun request(prompt: String = "hello", deadlineIn: Long = 5_000) = ReasoningPassRequest(
        taskId = "t1",
        role = ReasoningPassRole.CANDIDATE,
        prompt = prompt,
        maxOutputChars = 1_000,
        deadlineMillis = System.currentTimeMillis() + deadlineIn
    )

    private fun adapter(rt: FakeRuntime) = LocalModelReasoningAdapter(rt, modelIdProvider = { "fake@1" })

    @Test
    fun returnsTextFromRuntime() {
        val rt = FakeRuntime { cb ->
            cb.onEvent(LocalInferenceEvent.TextDelta("answer"))
            cb.onEvent(completed())
        }
        val result = adapter(rt).execute(request())
        assertEquals("answer", result.text)
        assertEquals("fake@1", result.modelId)
        assertTrue(rt.thinkingUsed)
        assertFalse(rt.agentConversationUsed)
    }

    @Test
    fun toolProposalFailsClosedAndCancels() {
        val rt = FakeRuntime { cb ->
            cb.onEvent(LocalInferenceEvent.ToolProposalRejected("delete_file", "x"))
        }
        assertThrows(SecurityException::class.java) { adapter(rt).execute(request()) }
        assertTrue(rt.cancelled)
    }

    @Test
    fun outputOverflowIsRejected() {
        val rt = FakeRuntime { cb -> cb.onEvent(LocalInferenceEvent.TextDelta("x".repeat(1_001))) }
        assertThrows(IllegalArgumentException::class.java) { adapter(rt).execute(request()) }
        assertTrue(rt.cancelled)
    }

    @Test
    fun oversizedPromptIsNotClipped() {
        val rt = FakeRuntime { }
        assertThrows(IllegalArgumentException::class.java) {
            adapter(rt).execute(request("p".repeat(16_001)))
        }
        assertFalse(rt.thinkingUsed)
    }

    @Test
    fun deadlineExpiryCancelsRuntime() {
        val rt = FakeRuntime { }
        assertThrows(IllegalStateException::class.java) {
            adapter(rt).execute(request(deadlineIn = 60))
        }
        assertTrue(rt.cancelled)
    }

    @Test
    fun missingModelFailsWithoutFallback() {
        val rt = FakeRuntime { }
        assertThrows(IllegalStateException::class.java) {
            LocalModelReasoningAdapter(rt, modelIdProvider = { null }).execute(request())
        }
        assertFalse(rt.thinkingUsed)
    }

    private fun completed() = LocalInferenceEvent.Completed(
        LocalInferenceMetrics(0, 0, 1, null, null, null, 0)
    )

    private class FakeRuntime(private val behavior: (LocalInferenceCallback) -> Unit) : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel: ImportedLocalModel? = null
        var cancelled = false
        var thinkingUsed = false
        var agentConversationUsed = false
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("unused")
        override fun unload(callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            agentConversationUsed = true
            error("Reasoning workers must not use the agent conversation.")
        }
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) {
            thinkingUsed = true
            behavior(callback)
        }
        override fun cancel() { cancelled = true }
    }
}
