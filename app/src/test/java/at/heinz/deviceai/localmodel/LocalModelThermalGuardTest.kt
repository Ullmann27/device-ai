package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #569: the owner's Fold7 got very hot during several 47–63 s Gemma answers. Load admission refused only the LOAD
 * at SEVERE; every generation started and kept running regardless of Android's thermal status. These tests run
 * against the production factory [LocalModelThermalGuard.wrap] used by [LocalModelHost]. No temperature is measured
 * or invented here: the status is Android's PowerManager level (0..6), simulated by the test.
 */
class LocalModelThermalGuardTest {
    private class NativeStandIn : LocalModelRuntime {
        override var state = LocalModelState.READY
        override val loadedModel: ImportedLocalModel? = null
        val started = mutableListOf<String>()
        var cancels = 0
        private var active: LocalInferenceCallback? = null
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) {}
        override fun unload(callback: (Result<Unit>) -> Unit) {}
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            check(state == LocalModelState.READY) { "A local model is not ready." } // like LiteRtLmLocalModelRuntime
            started += prompt; state = LocalModelState.GENERATING; active = callback
        }
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) = stream("thinking:$prompt", callback)
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) = stream("tools", callback)
        override fun cancel() { cancels++; finish(LocalInferenceEvent.Cancelled) }
        fun complete() = finish(LocalInferenceEvent.Completed(LocalInferenceMetrics(1, 1, 1, null, null, null, 1)))
        private fun finish(event: LocalInferenceEvent) { val a = active ?: return; active = null; state = LocalModelState.READY; a.onEvent(event) }
    }

    private var thermal: Int? = 0

    @Test fun generationDoesNotStartWhileAndroidReportsSevereHeat() {
        val native = NativeStandIn()
        val runtime = LocalModelThermalGuard.wrap(native) { thermal }
        thermal = 3
        val events = mutableListOf<LocalInferenceEvent>()
        runtime.stream("Erkläre Quantenphysik ausführlich.") { events += it }
        runtime.streamThinking("Plane den Tag.") { events += it }
        runtime.respondToTools(listOf(LocalToolResponse("get_battery_info", "{}"))) { events += it }
        assertEquals("the native model is never started while SEVERE", emptyList<String>(), native.started)
        assertEquals(List(3) { LocalInferenceEvent.Failed(LocalModelThermalGuard.PAUSED_THERMAL) }, events)
    }

    @Test fun runningGenerationStopsWhenAndroidReportsSevereHeat() {
        val native = NativeStandIn()
        val runtime = LocalModelThermalGuard.wrap(native) { thermal }
        val events = mutableListOf<LocalInferenceEvent>()
        runtime.stream("Lange Antwort") { events += it }
        assertEquals(listOf("Lange Antwort"), native.started)
        thermal = 2
        LocalModelThermalGuard.onThermalStatus(runtime, 2)
        assertEquals("MODERATE does not stop the answer", 0, native.cancels)
        thermal = 3
        LocalModelThermalGuard.onThermalStatus(runtime, 3)
        assertEquals("SEVERE stops the native generation once", 1, native.cancels)
        assertEquals("an honest thermal reason, not an unexplained cancel",
            listOf(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL)), events)
        LocalModelThermalGuard.onThermalStatus(runtime, 4)
        assertEquals("no second stop, no retry, no hidden restart", 1, native.cancels)
        assertEquals(listOf("Lange Antwort"), native.started)
    }

    @Test fun coolDeviceAndUnknownStatusKeepTheNormalPath() {
        val native = NativeStandIn()
        val runtime = LocalModelThermalGuard.wrap(native) { thermal }
        val events = mutableListOf<LocalInferenceEvent>()
        thermal = 2
        runtime.stream("eins") { events += it }
        native.complete()
        thermal = null // API < 29: no status, previous behaviour (never treated as a measured "cool")
        runtime.stream("zwei") { events += it }
        native.complete()
        assertEquals(listOf("eins", "zwei"), native.started)
        assertTrue(events.all { it is LocalInferenceEvent.Completed })
        LocalModelThermalGuard.onThermalStatus(runtime, 3)
        assertEquals("nothing running, nothing to stop", 0, native.cancels)
        thermal = 3
        runtime.stream("drei") { events += it }
        assertEquals("after heat the owner must ask again; nothing is queued", listOf("eins", "zwei"), native.started)
    }

    /** Gitar review on #565: a second caller rejected by the busy runtime must not take over the running answer. */
    @Test fun rejectedSecondStartKeepsTheHeatReasonForTheRunningAnswer() {
        val native = NativeStandIn()
        val runtime = LocalModelThermalGuard.wrap(native) { thermal }
        val first = mutableListOf<LocalInferenceEvent>()
        val second = mutableListOf<LocalInferenceEvent>()
        runtime.stream("Antwort A") { first += it }
        val rejected = runCatching { runtime.stream("Bot-Auftrag B") { second += it } }
        assertTrue("the busy runtime rejects B", rejected.isFailure)
        LocalModelThermalGuard.onThermalStatus(runtime, 3)
        assertEquals(1, native.cancels)
        assertEquals("A's owner sees the heat reason", listOf(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL)), first)
        assertEquals(emptyList<LocalInferenceEvent>(), second)
        thermal = 0
        runtime.stream("Antwort C") { }
        LocalModelThermalGuard.onThermalStatus(runtime, 3)
        assertEquals("the next answer is guarded as well", 2, native.cancels)
    }
}
