package at.heinz.deviceai.localmodel

import at.heinz.deviceai.localmodel.LocalModelAcceleration.Notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Decision logic of the owner switch "Beschleuniger: CPU (Standard) | GPU (experimentell)". Host tests only:
 * the native LiteRT-LM GPU start itself is NOT_RUN and is represented by the attempt lambda.
 */
class LocalModelAccelerationTest {
    private val cpu = LocalModelBackend.CPU
    private val gpu = LocalModelBackend.GPU
    private val both = setOf(cpu, gpu)

    /**
     * In-memory stand-in for the durable store. [snapshot] is "what is on disk at this moment": a controller on a
     * snapshot taken inside a native call models the process being killed there and started again.
     */
    private class Store : LocalModelAccelerationStore {
        var failWrites = false
        val writes = mutableListOf<String>()
        fun snapshot() = Store().also {
            it.requestedValue = requestedValue
            it.loadPending = loadPending
            it.generationPending = generationPending
            it.proven = proven
            it.noticeValue = noticeValue
        }
        private fun <T> stored(name: String, value: T): T {
            if (failWrites) throw IllegalStateException("LOCAL_MODEL_ACCELERATOR_STATE_NOT_STORED")
            writes += "$name=$value"
            return value
        }
        private var requestedValue = LocalModelBackend.CPU
        private var loadPending = false
        private var generationPending = false
        private var proven: String? = null
        private var noticeValue: Notice? = null
        override var requested: LocalModelBackend
            get() = requestedValue
            set(value) { requestedValue = stored("requested", value) }
        override var gpuLoadPending: Boolean
            get() = loadPending
            set(value) { loadPending = stored("loadPending", value) }
        override var gpuGenerationPending: Boolean
            get() = generationPending
            set(value) { generationPending = stored("generationPending", value) }
        override var gpuProvenModel: String?
            get() = proven
            set(value) { proven = stored("proven", value) }
        override var notice: Notice?
            get() = noticeValue
            set(value) { noticeValue = stored("notice", value) }
    }

    @Test fun cpuIsTheDefaultAndGpuIsNeverTriedWithoutTheOwnerSwitch() {
        val store = Store()
        val controller = LocalModelAccelerationController(store)
        val attempts = mutableListOf<LocalModelBackend>()
        val loaded = controller.load(both) { attempts += it; "engine" }
        assertEquals(cpu, controller.requested)
        assertEquals(listOf(cpu), attempts)
        assertEquals(cpu, loaded.backend)
        assertNull(loaded.notice)
        assertNull(loaded.ownerNotice())
        assertTrue("a CPU load writes no guard marker", store.writes.isEmpty())
    }

    @Test fun requestedGpuStartsOnGpuAndTheLoadMarkerIsSetOnlyDuringTheNativeStart() {
        val store = Store()
        val controller = LocalModelAccelerationController(store)
        controller.request(gpu)
        var markerDuringStart: Boolean? = null
        val loaded = controller.load(both) { backend ->
            markerDuringStart = store.gpuLoadPending
            "engine-on-$backend"
        }
        assertEquals(true, markerDuringStart)
        assertFalse(store.gpuLoadPending)
        assertEquals(gpu, loaded.backend)
        assertEquals("engine-on-GPU", loaded.value)
        assertNull(loaded.notice)
        assertEquals(gpu, controller.requested)
    }

    @Test fun aGpuStartThatThrowsFallsBackToCpuInTheSameLoadAndSwitchesThePreferenceBack() {
        val store = Store()
        val controller = LocalModelAccelerationController(store)
        controller.request(gpu)
        val attempts = mutableListOf<LocalModelBackend>()
        val loaded = controller.load(both) { backend ->
            attempts += backend
            if (backend == gpu) throw IllegalStateException("Failed to create engine: INTERNAL: no OpenCL")
            "engine-on-$backend"
        }
        assertEquals(listOf(gpu, cpu), attempts)
        assertEquals(cpu, loaded.backend)
        assertEquals("engine-on-CPU", loaded.value)
        assertEquals(Notice.GPU_START_FAILED, loaded.notice)
        assertFalse(store.gpuLoadPending)
        assertEquals("a failing GPU start is not repeated at every launch", cpu, controller.requested)
        assertEquals(Notice.GPU_START_FAILED, controller.notice)
        val message = loaded.ownerNotice()!!
        assertTrue(message.contains("fehlgeschlagen"))
        assertTrue(message.contains("experimentell – am Gerät ungeprüft"))
        assertTrue(message.contains("Technischer Hinweis: Failed to create engine: INTERNAL: no OpenCL"))
        // The next load is a plain CPU load.
        assertEquals(listOf(cpu), mutableListOf<LocalModelBackend>().also { next -> controller.load(both) { next += it } })
    }

    @Test fun linkageErrorsOfAMissingNativeGpuLibraryAlsoFallBack() {
        val controller = LocalModelAccelerationController(Store()).apply { request(gpu) }
        val loaded = controller.load(both) { backend ->
            if (backend == gpu) throw UnsatisfiedLinkError("libOpenCL.so not found")
            "cpu"
        }
        assertEquals(cpu, loaded.backend)
        assertEquals(Notice.GPU_START_FAILED, loaded.notice)
    }

    @Test fun noCpuEngineIsCreatedWhileAFailedGpuAttemptStillOwnsNativeResources() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        val attempts = mutableListOf<LocalModelBackend>()
        val failure = IllegalStateException("GPU conversation could not be released")
        try {
            controller.load(both, fallbackAllowed = { false }) { backend ->
                attempts += backend
                throw failure
            }
            fail("the GPU failure must be reported")
        } catch (reported: IllegalStateException) {
            assertSame(failure, reported)
        }
        assertEquals(listOf(gpu), attempts)
        assertFalse("the process survived, so this is not a crash marker", store.gpuLoadPending)
        assertEquals(cpu, controller.requested)
        assertEquals(Notice.GPU_START_FAILED, controller.notice)
    }

    @Test fun whenCpuFailsAfterGpuTheCpuFailureIsReportedAndCarriesTheGpuCause() {
        val controller = LocalModelAccelerationController(Store()).apply { request(gpu) }
        try {
            controller.load(both) { backend -> throw IllegalStateException("start failed on $backend") }
            fail("both attempts failed")
        } catch (reported: IllegalStateException) {
            assertEquals("start failed on CPU", reported.message)
            assertEquals("start failed on GPU", reported.suppressed.single().message)
        }
    }

    @Test fun aProcessKilledInsideTheGpuStartRevertsToCpuAtTheNextStartAndTellsTheOwner() {
        val store = Store()
        val firstProcess = LocalModelAccelerationController(store).apply { request(gpu) }
        // What is on disk while the native GPU start is running; the process is killed in there.
        var disk: Store? = null
        firstProcess.load(both) { disk = store.snapshot(); "engine" }
        val survivingState = disk!!
        assertTrue(survivingState.gpuLoadPending)
        assertEquals(gpu, survivingState.requested)

        val nextProcess = LocalModelAccelerationController(survivingState)
        val attempts = mutableListOf<LocalModelBackend>()
        val loaded = nextProcess.load(both) { attempts += it; "engine" }
        assertEquals("the GPU is not tried again", listOf(cpu), attempts)
        assertEquals(cpu, loaded.backend)
        assertEquals(Notice.GPU_LOAD_INTERRUPTED, loaded.notice)
        assertTrue(loaded.ownerNotice()!!.contains("wieder auf CPU"))
        assertEquals(cpu, nextProcess.requested)
        assertFalse(survivingState.gpuLoadPending)
        assertEquals(Notice.GPU_LOAD_INTERRUPTED, nextProcess.notice)
        // A third start is an ordinary CPU start; the notice stays readable in the model centre.
        assertEquals(LocalModelAcceleration.Plan(cpu, null), LocalModelAccelerationController(survivingState).plan(both))
        assertEquals(Notice.GPU_LOAD_INTERRUPTED, LocalModelAccelerationController(survivingState).notice)
    }

    @Test fun aModelFileWithoutGpuBackendStaysOnCpuAndKeepsThePreference() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        val attempts = mutableListOf<LocalModelBackend>()
        val loaded = controller.load(setOf(cpu)) { attempts += it; "engine" }
        assertEquals(listOf(cpu), attempts)
        assertEquals(Notice.GPU_NOT_IN_MODEL, loaded.notice)
        assertFalse("no native GPU start, so no marker", store.gpuLoadPending)
        assertEquals("another model file may support it", gpu, controller.requested)
        // NPU-only declarations are not a GPU either.
        assertEquals(cpu, controller.plan(setOf(cpu, LocalModelBackend.NPU)).backend)
    }

    @Test fun withoutAWritableCrashGuardTheGpuIsNotTried() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        store.failWrites = true
        val attempts = mutableListOf<LocalModelBackend>()
        val loaded = controller.load(both) { attempts += it; "engine" }
        assertEquals(listOf(cpu), attempts)
        assertEquals(cpu, loaded.backend)
        assertEquals(Notice.GPU_GUARD_NOT_WRITABLE, loaded.notice)
    }

    @Test fun switchingTheAcceleratorIsAFreshAttemptAndOnlyCpuOrGpuCanBeRequested() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        store.gpuLoadPending = true
        assertEquals(cpu, controller.plan(both).backend)
        assertEquals(Notice.GPU_LOAD_INTERRUPTED, controller.notice)
        // The owner knowingly switches the GPU on again: markers and the old notice no longer apply.
        controller.request(gpu)
        assertNull(controller.notice)
        assertEquals(LocalModelAcceleration.Plan(gpu, null), controller.plan(both))
        try {
            controller.request(LocalModelBackend.NPU)
            fail("NPU is not offered")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(gpu, controller.requested)
    }

    @Test fun storedValuesAreParsedDefensively() {
        assertEquals(cpu, LocalModelAcceleration.parse(null))
        assertEquals(cpu, LocalModelAcceleration.parse(""))
        assertEquals(cpu, LocalModelAcceleration.parse("NPU"))
        assertEquals(cpu, LocalModelAcceleration.parse("unbekannt"))
        assertEquals(gpu, LocalModelAcceleration.parse("GPU"))
        assertEquals(gpu, LocalModelAcceleration.parse("gpu"))
        assertNull(LocalModelAcceleration.parseNotice(null))
        assertNull(LocalModelAcceleration.parseNotice("SOMETHING_ELSE"))
        assertEquals(Notice.GPU_START_FAILED, LocalModelAcceleration.parseNotice("GPU_START_FAILED"))
    }

    @Test fun firstGpuAnswerOfAModelFileIsMarkedUntilItCompleted() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        val sha = "a".repeat(64)
        assertFalse(controller.generationNeedsMarker(cpu, sha))
        assertFalse(controller.generationNeedsMarker(null, sha))
        assertFalse(controller.generationNeedsMarker(gpu, null))
        assertTrue(controller.generationNeedsMarker(gpu, sha))

        controller.generationStarting()
        assertTrue(store.gpuGenerationPending)
        controller.generationEnded(sha, completed = false)
        assertFalse(store.gpuGenerationPending)
        assertTrue("a failed or stopped answer proves nothing about the GPU path", controller.generationNeedsMarker(gpu, sha))

        controller.generationStarting()
        controller.generationEnded(sha, completed = true)
        assertFalse(controller.generationNeedsMarker(gpu, sha))
        assertTrue("another model file is unproven", controller.generationNeedsMarker(gpu, "b".repeat(64)))
    }

    @Test fun aProcessKilledDuringTheFirstGpuAnswerRevertsToCpuAtTheNextLoad() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        val native = FakeRuntime(gpu)
        LocalModelAccelerationGuard.wrap(native, controller).stream("a") { }
        // The answer is running on the GPU and the process is killed before any terminal event.
        val survivingState = store.snapshot()
        assertTrue(survivingState.gpuGenerationPending)

        val nextProcess = LocalModelAccelerationController(survivingState)
        val attempts = mutableListOf<LocalModelBackend>()
        val loaded = nextProcess.load(both) { attempts += it; "engine" }
        assertEquals(listOf(cpu), attempts)
        assertEquals(Notice.GPU_GENERATION_INTERRUPTED, loaded.notice)
        assertEquals(cpu, nextProcess.requested)
        assertFalse(survivingState.gpuGenerationPending)
    }

    @Test fun ownerTextsNameTheSwitchTheStateAndThatNothingWasMeasuredOnTheDevice() {
        assertEquals("BESCHLEUNIGER · GPU VERSUCHEN (EXPERIMENTELL)", LocalModelAcceleration.actionLabel(cpu))
        assertEquals("BESCHLEUNIGER · ZURÜCK AUF CPU (STANDARD)", LocalModelAcceleration.actionLabel(gpu))

        val idle = LocalModelAcceleration.statusText(cpu, null, null, null)
        assertTrue(idle.startsWith("Beschleuniger: CPU (Standard). Kein Modell geladen."))
        assertTrue(idle.contains("hat noch niemand gemessen"))

        val pending = LocalModelAcceleration.statusText(gpu, cpu, both, null)
        assertTrue(pending.contains("Beschleuniger: GPU (experimentell – am Gerät ungeprüft)."))
        assertTrue(pending.contains("rechnet auf der CPU"))
        assertTrue(pending.contains("wirkt beim nächsten Laden"))
        assertTrue(pending.contains("Die Modelldatei meldet: CPU, GPU."))

        val active = LocalModelAcceleration.statusText(gpu, gpu, both, null)
        assertTrue(active.contains("rechnet auf der GPU (experimentell – am Gerät ungeprüft)"))
        assertFalse(active.contains("wirkt beim nächsten Laden"))

        val reverted = LocalModelAcceleration.statusText(cpu, cpu, setOf(cpu), Notice.GPU_LOAD_INTERRUPTED)
        assertTrue(reverted.contains("Letzter Hinweis: GPU-Beschleuniger"))
        assertTrue(reverted.contains("Die Modelldatei meldet: CPU."))

        Notice.entries.forEach { assertTrue(it.name, it.ownerMessage.contains(LocalModelAcceleration.EXPERIMENTAL)) }
        assertTrue(LocalModelAcceleration.CONFIRM_TITLE.contains(LocalModelAcceleration.EXPERIMENTAL))
        assertTrue(LocalModelAcceleration.CONFIRM_MESSAGE.contains("noch nie gelaufen"))
        assertTrue(LocalModelAcceleration.CONFIRM_MESSAGE.contains("Hitzeschutz"))
    }

    // ---- generation guard around a runtime ----

    private class FakeRuntime(
        override var activeBackend: LocalModelBackend?,
        sha256: String? = "c".repeat(64)
    ) : LocalModelRuntime {
        override var state = LocalModelState.READY
        override val loadedModel: ImportedLocalModel? = sha256?.let {
            ImportedLocalModel("fixture", "1", it, 1, LocalModelCapabilities(true, false, false, false, true, 4_096,
                null, setOf(LocalModelBackend.CPU, LocalModelBackend.GPU)))
        }
        override val computeThreads: Int? = 2
        override val contextTokens: Int? = 4_096
        override val backendNotice: String? = "fixture notice"
        val starts = mutableListOf<String>()
        var rejectStart = false
        var active: LocalInferenceCallback? = null
        var cancels = 0
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) { error("unused") }
        override fun unload(callback: (Result<Unit>) -> Unit) { error("unused") }
        override fun stream(prompt: String, callback: LocalInferenceCallback) = begin("stream", callback)
        override fun streamTextOnly(prompt: String, callback: LocalInferenceCallback) = begin("text-only", callback)
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) = begin("thinking", callback)
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) =
            begin("tools", callback)
        private fun begin(label: String, callback: LocalInferenceCallback) {
            starts += label
            check(!rejectStart) { "A local model is not ready." }
            active = callback
        }
        override fun cancel() { cancels++ }
        fun emit(event: LocalInferenceEvent) = active!!.onEvent(event)
    }

    private val completed = LocalInferenceEvent.Completed(LocalInferenceMetrics(1, 1, 1, null, null, null, 1))

    @Test fun guardForwardsEveryEntryPointAndRuntimeFactUnchanged() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        val native = FakeRuntime(gpu)
        val runtime = LocalModelAccelerationGuard.wrap(native, controller)
        assertEquals(gpu, runtime.activeBackend)
        assertEquals(2, runtime.computeThreads)
        assertEquals(4_096, runtime.contextTokens)
        assertEquals("fixture notice", runtime.backendNotice)
        assertSame(native.loadedModel, runtime.loadedModel)

        val events = mutableListOf<LocalInferenceEvent>()
        runtime.stream("a") { events += it }
        native.emit(LocalInferenceEvent.TextDelta("Hallo"))
        assertTrue("marker stays while the first GPU answer is running", store.gpuGenerationPending)
        native.emit(completed)
        assertFalse(store.gpuGenerationPending)
        assertEquals(listOf(LocalInferenceEvent.TextDelta("Hallo"), completed), events)

        // Proven: later generations are not marked any more.
        store.writes.clear()
        runtime.streamTextOnly("b") { events += it }
        runtime.streamThinking("c") { events += it }
        runtime.respondToTools(listOf(LocalToolResponse("tool", "{}"))) { events += it }
        assertEquals(listOf("stream", "text-only", "thinking", "tools"), native.starts)
        assertTrue(store.writes.toString(), store.writes.isEmpty())
        runtime.cancel()
        assertEquals(1, native.cancels)
    }

    @Test fun guardClearsTheMarkerOnFailureCancellationAndRejectedStartWithoutProvingTheGpu() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        val native = FakeRuntime(gpu)
        val runtime = LocalModelAccelerationGuard.wrap(native, controller)
        val sha = native.loadedModel!!.sha256

        val events = mutableListOf<LocalInferenceEvent>()
        runtime.streamTextOnly("a") { events += it }
        assertTrue(store.gpuGenerationPending)
        native.emit(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL))
        assertFalse(store.gpuGenerationPending)
        assertTrue(controller.generationNeedsMarker(gpu, sha))

        runtime.streamThinking("b") { events += it }
        native.emit(LocalInferenceEvent.Cancelled)
        assertFalse(store.gpuGenerationPending)
        assertTrue(controller.generationNeedsMarker(gpu, sha))

        native.rejectStart = true
        try {
            runtime.stream("c") { events += it }
            fail("the runtime's own rejection must reach the caller")
        } catch (rejected: IllegalStateException) {
            assertEquals("A local model is not ready.", rejected.message)
        }
        assertFalse(store.gpuGenerationPending)
        assertEquals(listOf<LocalInferenceEvent>(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL),
            LocalInferenceEvent.Cancelled), events)
    }

    @Test fun cpuEnginesAndUnloadedRuntimesAreNotMarkedAtAll() {
        val store = Store()
        val controller = LocalModelAccelerationController(store)
        for (native in listOf(FakeRuntime(cpu), FakeRuntime(null), FakeRuntime(gpu, sha256 = null))) {
            val runtime = LocalModelAccelerationGuard.wrap(native, controller)
            runtime.stream("a") { }
            runtime.respondToTools(listOf(LocalToolResponse("tool", "{}"))) { }
            assertEquals(listOf("stream", "tools"), native.starts)
        }
        assertTrue(store.writes.toString(), store.writes.isEmpty())
    }

    @Test fun thermalGuardStillStopsAGpuGenerationThroughTheAccelerationGuard() {
        val store = Store()
        val controller = LocalModelAccelerationController(store).apply { request(gpu) }
        val native = FakeRuntime(gpu)
        var thermal = 0
        val runtime = LocalModelThermalGuard.wrap(LocalModelAccelerationGuard.wrap(native, controller)) { thermal }
        assertEquals(gpu, runtime.activeBackend)

        // Severe heat before the start: the native runtime is not called and no marker is written.
        thermal = LocalModelThermalGuard.SEVERE
        val refused = mutableListOf<LocalInferenceEvent>()
        runtime.stream("a") { refused += it }
        assertEquals(listOf<LocalInferenceEvent>(LocalInferenceEvent.Failed(LocalModelThermalGuard.PAUSED_THERMAL)), refused)
        assertTrue(native.starts.isEmpty())
        assertFalse(store.gpuGenerationPending)

        // Heat during a running GPU answer: cancelled once, reported as a thermal stop, marker cleared, GPU unproven.
        thermal = 0
        val events = mutableListOf<LocalInferenceEvent>()
        native.state = LocalModelState.READY
        runtime.stream("b") { events += it }
        native.state = LocalModelState.GENERATING
        assertTrue(store.gpuGenerationPending)
        LocalModelThermalGuard.onThermalStatus(runtime, LocalModelThermalGuard.SEVERE)
        assertEquals(1, native.cancels)
        native.emit(LocalInferenceEvent.Cancelled)
        assertEquals(listOf<LocalInferenceEvent>(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL)), events)
        assertFalse(store.gpuGenerationPending)
        assertTrue(controller.generationNeedsMarker(gpu, native.loadedModel!!.sha256))
    }
}
