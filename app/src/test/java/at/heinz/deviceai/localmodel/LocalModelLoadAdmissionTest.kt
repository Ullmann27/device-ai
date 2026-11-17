package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5 milestone 4 – ComputeFabric/ConstrainedResourceFabric <-> LiteRT-LM model load.
 * The only production load path (AgentActivity.loadModel, also used by the
 * automatic Gemma restore on app open) started a multi-GB native engine without
 * any RAM or thermal admission. These tests drive the real gate in front of a
 * recording LocalModelRuntime.
 */
class LocalModelLoadAdmissionTest {
    private val gib = 1024L * 1024 * 1024
    private val gemma = ImportedLocalModel("gemma-4-e4b-it", "1", "a".repeat(64), 3_650_000_000L,
        LocalModelCapabilities(true, false, false, false, true, 32_768, null, setOf(LocalModelBackend.CPU)))

    private class RecordingRuntime : LocalModelRuntime {
        val loads = mutableListOf<String>()
        override val state = LocalModelState.EMPTY
        override val loadedModel: ImportedLocalModel? = null
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) { loads += "$id@$version" }
        override fun unload(callback: (Result<Unit>) -> Unit) = Unit
        override fun stream(prompt: String, callback: LocalInferenceCallback) = Unit
        override fun cancel() = Unit
    }

    private fun load(snapshot: LocalModelResourceSnapshot?): Pair<RecordingRuntime, Result<ImportedLocalModel>?> {
        val runtime = RecordingRuntime()
        var refused: Result<ImportedLocalModel>? = null
        LocalModelLoadAdmission.loadIfAdmitted(runtime, gemma, snapshot, onRefused = { refused = it })
        return runtime to refused
    }

    @Test fun severeThermalStateRefusesNativeLoad() {
        val (runtime, result) = load(LocalModelResourceSnapshot(12 * gib, 6 * gib, false, 3))
        assertTrue(runtime.loads.isEmpty())
        assertEquals("LOCAL_MODEL_LOAD_REFUSED_THERMAL", result?.exceptionOrNull()?.message)
    }

    @Test fun androidLowMemoryStateRefusesNativeLoad() {
        val (runtime, result) = load(LocalModelResourceSnapshot(12 * gib, 300L * 1024 * 1024, true, 0))
        assertTrue(runtime.loads.isEmpty())
        assertEquals("LOCAL_MODEL_LOAD_REFUSED_MEMORY", result?.exceptionOrNull()?.message)
    }

    @Test fun modelThatCannotStayResidentInPhysicalRamIsRefused() {
        // 3.65 GB weights on a 4 GiB phone: > 3/4 of physical RAM, can never be resident with OS headroom.
        val (runtime, result) = load(LocalModelResourceSnapshot(4 * gib, 2 * gib, false, 0))
        assertTrue(runtime.loads.isEmpty())
        assertEquals("LOCAL_MODEL_LOAD_REFUSED_MEMORY", result?.exceptionOrNull()?.message)
    }

    @Test fun missingTelemetryFailsClosedInsteadOfGuessing() {
        val (runtime, result) = load(null)
        assertTrue(runtime.loads.isEmpty())
        assertEquals("LOCAL_MODEL_LOAD_NO_MEASUREMENT", result?.exceptionOrNull()?.message)
    }

    @Test fun healthyFold7ClassDeviceStillLoadsTheOriginalGemma() {
        // Must not lock the owner out: 12 GiB device, moderate thermal, normal memory state.
        val (runtime, result) = load(LocalModelResourceSnapshot(12 * gib, 4 * gib, false, 2))
        assertEquals(listOf("gemma-4-e4b-it@1"), runtime.loads)
        assertEquals(null, result)
    }

    @Test fun unknownThermalOnOldAndroidDoesNotBlockButMemoryStillChecked() {
        val (ok, okResult) = load(LocalModelResourceSnapshot(12 * gib, 4 * gib, false, null))
        assertEquals(1, ok.loads.size); assertEquals(null, okResult)
        val (low, lowResult) = load(LocalModelResourceSnapshot(12 * gib, 4 * gib, true, null))
        assertTrue(low.loads.isEmpty()); assertNotNull(lowResult); assertFalse(lowResult!!.isSuccess)
    }
}
