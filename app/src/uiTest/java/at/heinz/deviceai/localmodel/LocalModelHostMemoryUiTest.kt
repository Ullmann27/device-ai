package at.heinz.deviceai.localmodel

import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import at.heinz.deviceai.ContinuumUiCopy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class LocalModelHostMemoryUiTest {
    private val app: Context get() = RuntimeEnvironment.getApplication()
    private val power: PowerManager get() = app.getSystemService(PowerManager::class.java)

    private class NativePort : LocalModelRuntime {
        override var state = LocalModelState.READY
        override val loadedModel = ImportedLocalModel("memory-gate-fixture", "1", "a".repeat(64),
            3_650_000_000L, LocalModelCapabilities(true, false, false, false, true, 32_768, null,
                setOf(LocalModelBackend.CPU)))
        val starts = mutableListOf<String>()
        var loads = 0
        var unloads = 0
        var cancels = 0
        private var active: LocalInferenceCallback? = null
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) { loads++ }
        override fun unload(callback: (Result<Unit>) -> Unit) { unloads++ }
        override fun stream(prompt: String, callback: LocalInferenceCallback) = begin("stream:$prompt", callback)
        override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) = begin("text-only:$prompt", callback)
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) = begin("thinking:$prompt", callback)
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            begin("tools:" + responses.single().toolName + ":" + responses.single().response, callback)
        private fun begin(label: String, callback: LocalInferenceCallback) {
            check(state == LocalModelState.READY)
            starts += label
            state = LocalModelState.GENERATING
            active = callback
        }
        override fun cancel() {
            cancels++
            finish(LocalInferenceEvent.Cancelled)
        }
        fun complete() = finish(LocalInferenceEvent.Completed(LocalInferenceMetrics(1, 1, 1, null, null, null, 1)))
        private fun finish(event: LocalInferenceEvent) {
            val callback = active ?: return
            active = null
            state = LocalModelState.READY
            callback.onEvent(event)
        }
    }

    @Before fun before() {
        memory(false)
        if (Build.VERSION.SDK_INT >= 29) {
            shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun memory(low: Boolean, available: Long = 4L shl 30) {
        shadowOf(app.getSystemService(ActivityManager::class.java)).setMemoryInfo(
            ActivityManager.MemoryInfo().apply {
                totalMem = 12L shl 30
                availMem = available
                lowMemory = low
            })
    }

    private fun runtime(native: NativePort, context: Context = app): LocalModelRuntime {
        val factory = LocalModelHost::class.java.getDeclaredMethod("thermallyGuarded",
            Context::class.java, LocalModelRuntime::class.java, PowerManager::class.java)
        factory.isAccessible = true
        return factory.invoke(LocalModelHost, context, native, power) as LocalModelRuntime
    }

    private fun start(runtime: LocalModelRuntime, kind: Int, events: MutableList<LocalInferenceEvent>) {
        val callback = LocalInferenceCallback { events += it }
        when (kind) {
            0 -> runtime.stream("owner-question", callback)
            1 -> runtime.streamThinking("worker-question", callback)
            2 -> runtime.respondToTools(listOf(LocalToolResponse("get_battery_info", "{}")), callback)
            3 -> runtime.streamTextOnly("text-question", callback)
            else -> error("Unknown entry point")
        }
    }

    @Test fun loadedChatRechecksLowMemoryBeforeNativeDelegation() = changedMemory(0)
    @Test fun loadedThinkingRechecksLowMemoryBeforeNativeDelegation() = changedMemory(1)
    @Test fun loadedToolContinuationRechecksLowMemoryBeforeNativeDelegation() = changedMemory(2)
    @Test fun loadedTextOnlyRechecksLowMemoryBeforeNativeDelegation() = changedMemory(3)

    private fun changedMemory(kind: Int) {
        val native = NativePort()
        val model = native.loadedModel
        val runtime = runtime(native)
        val events = mutableListOf<LocalInferenceEvent>()
        start(runtime, kind, events)
        native.complete()
        val admitted = native.starts.single()
        assertEquals(listOf("stream:owner-question", "thinking:worker-question",
            "tools:get_battery_info:{}", "text-only:text-question")[kind], admitted)
        assertTrue(events.single() is LocalInferenceEvent.Completed)
        events.clear()
        memory(true, 256L shl 20)
        start(runtime, kind, events)
        assertEquals(listOf(admitted), native.starts)
        assertEquals(listOf(LocalInferenceEvent.Failed("LOCAL_MODEL_PAUSED_MEMORY")), events)
        assertEquals("Keine neue Modellantwort gestartet: Android meldet zu wenig Arbeitsspeicher. " +
            "Schließe andere Apps und frage erneut.", ContinuumUiCopy.modelFailure("LOCAL_MODEL_PAUSED_MEMORY"))
        assertEquals(LocalModelState.READY, runtime.state)
        assertSame(model, runtime.loadedModel)
        assertEquals(0, native.loads)
        assertEquals(0, native.unloads)
        assertEquals(0, native.cancels)
        events.clear()
        memory(false, 256L shl 20)
        assertEquals(1, native.starts.size)
        start(runtime, kind, events)
        native.complete()
        assertEquals(listOf(admitted, admitted), native.starts)
        assertTrue(events.single() is LocalInferenceEvent.Completed)
        assertSame(model, runtime.loadedModel)
    }

    @Test fun unavailableOrUnreadableMemoryBlocksEveryEntryWithoutUnloading() {
        for (unreadable in listOf(false, true)) {
            val context = object : ContextWrapper(app) {
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? {
                    if (name == Context.ACTIVITY_SERVICE) {
                        if (unreadable) throw IllegalStateException("Controlled unavailable memory observation")
                        return null
                    }
                    return super.getSystemService(name)
                }
            }
            val native = NativePort()
            val runtime = runtime(native, context)
            val events = mutableListOf<LocalInferenceEvent>()
            repeat(4) { start(runtime, it, events) }
            assertTrue(native.starts.isEmpty())
            assertEquals(List(4) { LocalInferenceEvent.Failed("LOCAL_MODEL_MEMORY_NO_MEASUREMENT") }, events)
            assertEquals("Keine neue Modellantwort gestartet: Die Arbeitsspeichermessung ist nicht verfügbar. " +
                "Versuche es später erneut.", ContinuumUiCopy.modelFailure("LOCAL_MODEL_MEMORY_NO_MEASUREMENT"))
            assertEquals(LocalModelState.READY, runtime.state)
            assertSame(native.loadedModel, runtime.loadedModel)
            assertEquals(0, native.loads)
            assertEquals(0, native.unloads)
            assertEquals(0, native.cancels)
        }
    }

    @Test @Config(sdk = [28]) fun unavailableThermalStatusStillChecksActualMemory() {
        repeat(4) { changedMemory(it) }
    }

    @Test fun thermalAdmissionAndRunningCancellationRemainEffective() {
        val native = NativePort()
        val runtime = runtime(native)
        val events = mutableListOf<LocalInferenceEvent>()
        shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
        shadowOf(Looper.getMainLooper()).idle()
        repeat(4) { start(runtime, it, events) }
        assertTrue(native.starts.isEmpty())
        assertEquals(List(4) { LocalInferenceEvent.Failed("LOCAL_MODEL_PAUSED_THERMAL") }, events)
        shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
        shadowOf(Looper.getMainLooper()).idle()
        events.clear()
        start(runtime, 1, events)
        memory(true)
        shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
        // Existing listener entry.
        LocalModelThermalGuard.onThermalStatus(runtime, PowerManager.THERMAL_STATUS_SEVERE)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, native.cancels)
        assertEquals(listOf(LocalInferenceEvent.Failed("LOCAL_MODEL_STOPPED_THERMAL")), events)
        assertEquals(1, native.starts.size)
        shadowOf(power).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
        shadowOf(Looper.getMainLooper()).idle()
        events.clear()
        start(runtime, 1, events)
        assertEquals(listOf(LocalInferenceEvent.Failed("LOCAL_MODEL_PAUSED_MEMORY")), events)
        assertEquals(1, native.starts.size)
        memory(false)
        events.clear()
        start(runtime, 1, events)
        native.complete()
        assertEquals(2, native.starts.size)
        assertEquals(1, native.cancels)
        assertTrue(events.single() is LocalInferenceEvent.Completed)
    }

    @Test fun ownerCancellationStillReachesAnAlreadyRunningNativeGeneration() {
        val native = NativePort()
        val runtime = runtime(native)
        val events = mutableListOf<LocalInferenceEvent>()
        start(runtime, 0, events)
        memory(true)
        runtime.cancel()
        assertEquals(1, native.cancels)
        assertEquals(listOf(LocalInferenceEvent.Cancelled), events)
        events.clear()
        start(runtime, 0, events)
        assertEquals(listOf(LocalInferenceEvent.Failed("LOCAL_MODEL_PAUSED_MEMORY")), events)
        assertEquals(1, native.starts.size)
        assertEquals(1, native.cancels)
    }

    @Test fun rejectedConcurrentCallerCannotReplaceTheRunningNativeOwner() {
        val native = NativePort()
        val runtime = runtime(native)
        val first = mutableListOf<LocalInferenceEvent>()
        val second = mutableListOf<LocalInferenceEvent>()
        start(runtime, 1, first)
        memory(true)
        assertThrows(IllegalStateException::class.java) { start(runtime, 0, second) }
        assertTrue(second.isEmpty())
        assertEquals(1, native.starts.size)
        assertEquals(0, native.cancels)
        native.complete()
        assertTrue(first.single() is LocalInferenceEvent.Completed)
        assertEquals(LocalModelState.READY, runtime.state)
    }
}
