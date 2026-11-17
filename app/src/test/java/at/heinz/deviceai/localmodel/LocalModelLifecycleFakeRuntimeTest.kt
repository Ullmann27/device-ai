package at.heinz.deviceai.localmodel

import at.heinz.deviceai.LocalFirstPolicy
import at.heinz.deviceai.LocalModelUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelLifecycleFakeRuntimeTest {
    @Test
    fun fakeRuntimeExposesLoadGenerateCancelAndUnloadStates() {
        val runtime = FakeRuntime()
        val lifecycleStates = mutableListOf<LocalModelState>()
        runtime.load("test-model", "1.0.0") { result ->
            assertTrue(result.isSuccess)
            lifecycleStates += runtime.state
        }
        assertEquals(listOf(LocalModelState.LOADING, LocalModelState.READY), runtime.transitions.take(2))
        assertEquals(LocalModelState.READY, lifecycleStates.single())
        assertEquals(LocalModelUiState.READY, LocalFirstPolicy.modelUiState(runtime.state, true, false))

        val events = mutableListOf<LocalInferenceEvent>()
        runtime.holdGeneration = true
        runtime.stream("test prompt") { events += it }
        assertEquals(LocalModelState.GENERATING, runtime.state)
        assertEquals(
            LocalModelUiState.GENERATING,
            LocalFirstPolicy.modelUiState(runtime.state, true, false)
        )

        runtime.cancel()
        assertEquals(LocalInferenceEvent.Cancelled, events.single())
        assertEquals(LocalModelState.READY, runtime.state)
        assertEquals(LocalModelUiState.OFFLINE, LocalFirstPolicy.modelUiState(runtime.state, true, true))

        runtime.unload()
        assertEquals(LocalModelState.EMPTY, runtime.state)
        assertNull(runtime.loadedModel)
    }

    private class FakeRuntime : LocalModelRuntime {
        override var state = LocalModelState.EMPTY
            private set
        override var loadedModel: ImportedLocalModel? = null
            private set
        val transitions = mutableListOf<LocalModelState>()
        var holdGeneration = false
        private var pendingCallback: LocalInferenceCallback? = null

        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) {
            state = LocalModelState.LOADING
            transitions += state
            loadedModel = ImportedLocalModel(
                id,
                version,
                "0".repeat(64),
                1,
                LocalModelCapabilities(
                    text = true,
                    vision = false,
                    audio = false,
                    video = false,
                    functionCalling = true,
                    maxContextTokens = 1_024,
                    minimumRuntimeVersion = null,
                    supportedBackends = setOf(LocalModelBackend.CPU)
                )
            )
            state = LocalModelState.READY
            transitions += state
            callback(Result.success(checkNotNull(loadedModel)))
        }

        override fun unload(callback: (Result<Unit>) -> Unit) {
            state = LocalModelState.UNLOADING
            transitions += state
            loadedModel = null
            state = LocalModelState.EMPTY
            transitions += state
            callback(Result.success(Unit))
        }

        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            state = LocalModelState.GENERATING
            if (holdGeneration) pendingCallback = callback
            else {
                state = LocalModelState.READY
                callback.onEvent(LocalInferenceEvent.Completed(LocalInferenceMetrics(1, 1, 1, null, null, 0, 1)))
            }
        }

        override fun cancel() {
            pendingCallback?.onEvent(LocalInferenceEvent.Cancelled)
            pendingCallback = null
            state = LocalModelState.READY
        }
    }
}
