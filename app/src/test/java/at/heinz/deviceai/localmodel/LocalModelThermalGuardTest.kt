package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * #569: the owner's Fold7 got very hot during several 47–63 s Gemma answers. Load admission refused only the LOAD
 * at SEVERE; every generation started and kept running regardless of Android's thermal status. These tests run
 * against the production factory [LocalModelThermalGuard.wrap] used by [LocalModelHost]. No temperature is measured
 * or invented here: the status is Android's PowerManager level (0..6), simulated by the test.
 */
class LocalModelThermalGuardTest {
    private class NativeStandIn : LocalModelRuntime {
        var beforeAdmission: () -> Unit = {}
        var afterAdmission: () -> Unit = {}
        var afterCancel: () -> Unit = {}
        var cancelCompletes = true
        var lastCallback: LocalInferenceCallback? = null
        override var state = LocalModelState.READY
        override val loadedModel: ImportedLocalModel? = null
        val started = mutableListOf<String>()
        var cancels = 0
        private var active: LocalInferenceCallback? = null
        override fun load(id: String, version: String, callback: (Result<ImportedLocalModel>) -> Unit) {}
        override fun unload(callback: (Result<Unit>) -> Unit) {}
        override fun stream(prompt: String, callback: LocalInferenceCallback) {
            check(state == LocalModelState.READY) { "A local model is not ready." } // like LiteRtLmLocalModelRuntime
            beforeAdmission()
            started += prompt; state = LocalModelState.GENERATING; active = callback
            lastCallback = callback
            afterAdmission()
        }
        override fun streamThinking(prompt: String, callback: LocalInferenceCallback) = stream("thinking:$prompt", callback)
        override fun respondToTools(responses: List<LocalToolResponse>, callback: LocalInferenceCallback) = stream("tools", callback)
        override fun cancel() {
            cancels++
            if (cancelCompletes) finish(LocalInferenceEvent.Cancelled)
            afterCancel()
        }
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
    private fun start(runtime: LocalModelRuntime, kind: Int, events: MutableList<LocalInferenceEvent>) {
        val callback = LocalInferenceCallback { events += it }
        when (kind) {
            0 -> runtime.stream("chat", callback)
            1 -> runtime.streamThinking("reasoning", callback)
            else -> runtime.respondToTools(listOf(LocalToolResponse("get_battery_info", "{}")), callback)
        }
    }

    @Test fun severeCallbackDuringSamplingRejectsStaleCoolReadingForEveryEntryPoint() {
        repeat(3) { kind ->
            val native = NativeStandIn()
            lateinit var runtime: LocalModelRuntime
            runtime = LocalModelThermalGuard.wrap(native) {
                val sampled = 0
                LocalModelThermalGuard.onThermalStatus(runtime, 3)
                sampled
            }
            val events = mutableListOf<LocalInferenceEvent>()
            start(runtime, kind, events)
            assertEquals(emptyList<String>(), native.started)
            assertEquals(0, native.cancels)
            assertEquals(listOf(LocalInferenceEvent.Failed(LocalModelThermalGuard.PAUSED_THERMAL)), events)
        }
    }

    @Test fun heatBeforeNativeAdmissionStopsOnceAfterStartForEveryEntryPoint() {
        repeat(3) { kind ->
            val native = NativeStandIn()
            val runtime = LocalModelThermalGuard.wrap(native) { thermal }
            native.beforeAdmission = {
                thermal = 3
                LocalModelThermalGuard.onThermalStatus(runtime, 3)
                LocalModelThermalGuard.onThermalStatus(runtime, 4)
                assertEquals(0, native.cancels)
            }
            thermal = 0
            val events = mutableListOf<LocalInferenceEvent>()
            start(runtime, kind, events)
            assertEquals(1, native.started.size)
            assertEquals(1, native.cancels)
            assertEquals(LocalModelState.READY, native.state)
            assertEquals(listOf(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL)), events)
            native.lastCallback!!.onEvent(LocalInferenceEvent.Completed(
                LocalInferenceMetrics(1, 1, 1, null, null, null, 1)))
            native.lastCallback!!.onEvent(LocalInferenceEvent.TextDelta("late"))
            LocalModelThermalGuard.onThermalStatus(runtime, 4)
            assertEquals(1, events.size)
            assertEquals(1, native.cancels)
            native.beforeAdmission = {}
            thermal = 0
            start(runtime, kind, events)
            native.complete()
            assertEquals(2, native.started.size)
            assertEquals(1, native.cancels)
            assertTrue(events.last() is LocalInferenceEvent.Completed)
        }
    }

    @Test fun admittedBlockingStartCanBeStoppedWithoutHoldingGuardLock() {
        val native = NativeStandIn()
        val runtime = LocalModelThermalGuard.wrap(native) { thermal }
        val admitted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val startError = AtomicReference<Throwable?>()
        val stopError = AtomicReference<Throwable?>()
        val events = mutableListOf<LocalInferenceEvent>()
        native.afterAdmission = {
            admitted.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val starter = Thread({
            try { runtime.stream("blocked start") { events += it } }
            catch (error: Throwable) { startError.set(error) }
        }, "thermal-start-test")
        val stopper = Thread({
            try {
                LocalModelThermalGuard.onThermalStatus(runtime, 3)
                LocalModelThermalGuard.onThermalStatus(runtime, 4)
            } catch (error: Throwable) { stopError.set(error) }
            finally { stopped.countDown() }
        }, "thermal-stop-test")
        try {
            starter.start()
            assertTrue(admitted.await(5, TimeUnit.SECONDS))
            stopper.start()
            assertTrue("stop must not wait for start to return", stopped.await(5, TimeUnit.SECONDS))
            assertEquals(1, native.cancels)
            assertEquals(LocalModelState.READY, native.state)
            assertEquals(listOf(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL)), events)
        } finally {
            release.countDown()
            starter.join(5_000)
            stopper.join(5_000)
        }
        assertTrue(!starter.isAlive && !stopper.isAlive)
        assertEquals(null, startError.get())
        assertEquals(null, stopError.get())
        assertEquals(1, native.cancels)
        assertEquals(1, events.size)
    }

    @Test fun rejectedNativeStartReleasesOwnershipForCoolOwnerRetry() {
        val native = NativeStandIn()
        val runtime = LocalModelThermalGuard.wrap(native) { thermal }
        native.beforeAdmission = { error("native admission rejected") }
        val events = mutableListOf<LocalInferenceEvent>()
        assertTrue(runCatching { runtime.stream("rejected") { events += it } }.isFailure)
        assertEquals(emptyList<String>(), native.started)
        LocalModelThermalGuard.onThermalStatus(runtime, 3)
        assertEquals(0, native.cancels)
        native.beforeAdmission = {}
        runtime.stream("owner retry") { events += it }
        native.complete()
        assertEquals(listOf("owner retry"), native.started)
        assertEquals(1, events.size)
        assertTrue(events.single() is LocalInferenceEvent.Completed)
    }

    @Test fun thermalCancellationKeepsOwnershipUntilNativeCancelReturns() {
        val native = NativeStandIn()
        val runtime = LocalModelThermalGuard.wrap(native) { thermal }
        val cancelled = CountDownLatch(1)
        val release = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val events = mutableListOf<LocalInferenceEvent>()
        runtime.stream("first") { events += it }
        native.afterCancel = {
            cancelled.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val stopper = Thread({
            try { LocalModelThermalGuard.onThermalStatus(runtime, 3) }
            catch (failure: Throwable) { error.set(failure) }
        }, "thermal-owner-test")
        try {
            stopper.start()
            assertTrue(cancelled.await(5, TimeUnit.SECONDS))
            assertEquals(LocalModelState.READY, native.state)
            assertTrue(runCatching { runtime.stream("too early") { events += it } }.isFailure)
            assertEquals(listOf("first"), native.started)
        } finally {
            release.countDown()
            stopper.join(5_000)
        }
        assertTrue(!stopper.isAlive)
        assertEquals(null, error.get())
        native.afterCancel = {}
        runtime.stream("cool owner retry") { events += it }
        native.complete()
        assertEquals(listOf("first", "cool owner retry"), native.started)
        assertEquals(1, native.cancels)
        assertEquals(2, events.size)
        assertEquals(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL), events.first())
        assertTrue(events.last() is LocalInferenceEvent.Completed)
    }

    @Test fun latchedHeatRejectsSuccessWhenNativeCompletionWinsCancellation() {
        val native = NativeStandIn().apply { cancelCompletes = false }
        val runtime = LocalModelThermalGuard.wrap(native) { thermal }
        val events = mutableListOf<LocalInferenceEvent>()
        runtime.stream("first") { events += it }
        LocalModelThermalGuard.onThermalStatus(runtime, 3)
        native.lastCallback!!.onEvent(LocalInferenceEvent.TextDelta("late"))
        LocalModelThermalGuard.onThermalStatus(runtime, 4)
        assertEquals(1, native.cancels)
        assertTrue(events.isEmpty())
        native.complete()
        assertEquals(listOf(LocalInferenceEvent.Failed(LocalModelThermalGuard.STOPPED_THERMAL)), events)
        runtime.stream("cool owner retry") { events += it }
        native.complete()
        assertTrue(events.last() is LocalInferenceEvent.Completed)
        assertEquals(1, native.cancels)
    }

}
