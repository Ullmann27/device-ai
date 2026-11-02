package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

/** These adapters simulate host model engines; neither loads native weights. */
class LocalToolContinuationTest {
    private class HostTextAdapter : LocalModelRuntime {
        override val state = LocalModelState.READY
        override val loadedModel: ImportedLocalModel? = null
        var calls = 0
        var prompt = ""
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) = error("HOST_ONLY")
        override fun unload(callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
        override fun cancel() {}
        override fun stream(prompt: String, callback: LocalInferenceCallback) { calls++;this.prompt=prompt }
    }

    @Test fun defaultAdapterRejectsWholeUtf8OverflowBeforeCallingAnEngine() {
        val adapter=HostTextAdapter()
        var failure: LocalInferenceEvent?=null
        adapter.respondToTools(listOf(LocalToolResponse("get_device_info","界".repeat(8_000)))) { failure=it }
        assertEquals("An oversized encoded observation must not reach an engine",0,adapter.calls)
        assertTrue(failure is LocalInferenceEvent.Failed)
    }

    @Test fun defaultAdapterRejectsAnOversizedBatchBeforeCallingAnEngine() {
        val adapter=HostTextAdapter()
        var failure: LocalInferenceEvent?=null
        adapter.respondToTools(List(5) {LocalToolResponse("get_device_info","small")}) { failure=it }
        assertEquals(0,adapter.calls)
        assertTrue(failure is LocalInferenceEvent.Failed)
    }

    @Test fun defaultAdapterKeepsLateNegationInACompleteSmallResponse() {
        val adapter=HostTextAdapter()
        val text="x".repeat(6_000)+" — Korrektur: NICHT ausführen. Μην προχωρήσεις."
        adapter.respondToTools(listOf(LocalToolResponse("get_device_info",text))) {}
        assertEquals(1,adapter.calls)
        assertTrue(adapter.prompt.endsWith(text))
    }

    @Test fun serializedObservationKeepsCompleteLateMultilingualCorrection() {
        val text="x".repeat(6_000)+" Korrektur: NICHT freigegeben. Μην προχωρήσεις."
        val encoded=LocalToolContinuation.encode("get_device_info","SUCCEEDED",text)
        assertNotNull(encoded)
        assertEquals(text,JSONObject(encoded!!.response).getString("output"))
    }

    @Test fun escapedOutputIsRefusedWholeWithoutAConstructorException() {
        assertNull(LocalToolContinuation.encode("get_device_info","SUCCEEDED","\"".repeat(6_000)))
        assertNull(LocalToolContinuation.encode("get_device_info","SUCCEEDED","\u0000".repeat(2_000)))
        assertNotNull(LocalToolContinuation.encode("get_device_info","SUCCEEDED","\"".repeat(100)))
    }

    @Test fun cumulativeNativeContextAndOutputReserveAreMandatory() {
        assertFalse(LocalToolContinuation.fitsContext(8_000,15_900,16_000,1_024))
        assertFalse(LocalToolContinuation.fitsContext(100,null,16_000,1_024))
        assertFalse(LocalToolContinuation.fitsContext(100,0,0,1_024))
        assertFalse(LocalToolContinuation.fitsContext(16_001,0,32_768,1_024))
        assertTrue(LocalToolContinuation.fitsContext(100,100,16_000,1_024))
    }
}
