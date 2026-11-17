package at.heinz.deviceai.routines

import android.app.ActivityManager
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.content.ComponentName
import android.os.Looper
import android.os.PowerManager
import at.heinz.deviceai.CheckedShellCallDispatcher
import at.heinz.deviceai.IDeviceShell
import at.heinz.deviceai.ShellExecutionResult
import at.heinz.deviceai.ShellExecutionWire
import at.heinz.deviceai.ShellOutcome
import at.heinz.deviceai.ShizukuBridge
import at.heinz.deviceai.ShizukuBridgeLifecycleTest
import at.heinz.deviceai.ToolReceiptState
import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.chief.RoutineDispatcher
import at.heinz.deviceai.chief.WorkerBrief
import at.heinz.deviceai.cognitive.ContinuumAutonomyControl
import at.heinz.deviceai.cognitive.ContinuumAutonomyLevel
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.ObservedContext
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.battery
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.context
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.field
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.idle
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.receipts
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.register
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.reset
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.until
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowActivityManager
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [OwnerRoutineRuntimeUiTest.Memory::class, ShizukuBridgeLifecycleTest.Server::class])
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerRoutineRuntimeUiTest {
    @Implements(ActivityManager::class)
    class Memory : ShadowActivityManager() {
        @Implementation override fun getMemoryInfo(outInfo: ActivityManager.MemoryInfo) {
            if (holdTool.get() && Thread.currentThread().name == "device-ai-tool") {
                entered.countDown()
                while (release.count != 0L) {
                    try { release.await(5, TimeUnit.SECONDS) }
                    catch (_: InterruptedException) { }
                }
            }
            super.getMemoryInfo(outInfo)
        }
        companion object {
            val holdTool = AtomicBoolean()
            var entered = CountDownLatch(1)
            var release = CountDownLatch(1)
        }
    }

    @Before fun before() {
        reset()
        Memory.holdTool.set(false); Memory.entered = CountDownLatch(1); Memory.release = CountDownLatch(1)
        ShizukuBridgeLifecycleTest().reset()
    }
    @After fun after() { Memory.release.countDown(); reset() }

    @Test fun realJobReadsLowBatteryAndKeepsPersistedDueTime() {
        val app = context(); battery(app, 19)
        val runtime = OwnerRoutineRuntime.get(app)
        register(runtime, OwnerRoutineTarget.LOW_BATTERY)
        assertEquals(OwnerRoutineSchedule.SCHEDULED, runtime.restoreSchedule())
        val job = app.getSystemService(JobScheduler::class.java).getPendingJob(OwnerRoutineScheduler.JOB_ID)!!
        assertEquals(ComponentName(app, OwnerRoutineJobService::class.java), job.service)
        assertTrue(job.isPersisted); assertTrue(job.isPeriodic)
        assertFalse(job.isRequireBatteryNotLow); assertFalse(job.isRequireCharging)
        assertFalse(job.isRequireStorageNotLow); assertFalse(job.isRequireDeviceIdle)
        val controller = Robolectric.buildService(OwnerRoutineJobService::class.java).create()
        val service = controller.get()
        try {
            assertTrue(service.onStartJob(Shadow.newInstanceOf(JobParameters::class.java)))
            until("Actual Android job completes") { shadowOf(service).isJobFinished }
            assertFalse(shadowOf(service).isRescheduleNeeded)
            assertEquals(1, app.batteryReads.get())
            val receipt = receipts(app).single()
            assertEquals("get_battery_info", receipt.toolName)
            assertEquals(ToolReceiptState.SUCCEEDED, receipt.state)
            assertTrue(runtime.book.pendingChatReports().single().result.contains("Aktueller Akkustand: 19 %"))
            val persisted = FileRoutineStateStore(File(app.filesDir.canonicalFile, "routines")).load()
            assertTrue(persisted.entries.single().nextAtMillis > System.currentTimeMillis())
            until("Job clears its worker") { field(service, "worker") == null }
            assertTrue(service.onStartJob(Shadow.newInstanceOf(JobParameters::class.java)))
            until("Second job finishes without replay") { field(service, "worker") == null }
            assertEquals(1, app.batteryReads.get())
            assertEquals(listOf(receipt.callId), receipts(app).map { it.callId })
        } finally { controller.destroy() }
    }

    @Test fun onStopJobRevokesWaitingGenerationBeforeAnyDeviceRead() {
        val app = context(); battery(app, 18)
        val runtime = OwnerRoutineRuntime.get(app); register(runtime)
        val store = FileRoutineStateStore(File(app.filesDir.canonicalFile, "routines"))
        val before = store.load()
        val controller = Robolectric.buildService(OwnerRoutineJobService::class.java).create()
        val service = controller.get()
        val params = Shadow.newInstanceOf(JobParameters::class.java)
        try {
            synchronized(runtime.book) {
                assertTrue(service.onStartJob(params))
                until("Job actually waits on the shared book") {
                    (field(service, "worker") as? Thread)?.state == Thread.State.BLOCKED
                }
                assertTrue(service.onStopJob(params))
            }
            until("Stopped job leaves") { field(service, "worker") == null }
            assertEquals(0, app.batteryReads.get()); assertTrue(receipts(app).isEmpty())
            assertEquals(before, store.load())
            assertEquals(before.entries, runtime.book.registrations())
            assertNull(runtime.lastProblem)
            assertFalse(shadowOf(service).isJobFinished)
            assertTrue(service.onStartJob(Shadow.newInstanceOf(JobParameters::class.java)))
            until("Fresh Android job may execute") { field(service, "worker") == null }
            assertEquals(1, app.batteryReads.get())
            assertEquals(ToolReceiptState.SUCCEEDED, receipts(app).single().state)
        } finally { controller.destroy() }
    }

    @Test fun onStopJobDuringStartedClaimPreservesDurableState() = stopDuringStartedClaim(false)

    @Test fun onDestroyDuringStartedClaimPreservesDurableState() = stopDuringStartedClaim(true)

    private fun stopDuringStartedClaim(destroy: Boolean) {
        val app = context(); battery(app, 18)
        val runtime = OwnerRoutineRuntime.get(app); register(runtime)
        val store = FileRoutineStateStore(File(app.filesDir.canonicalFile, "routines"))
        val before = store.load()
        val storeLock = FileRoutineStateStore::class.java.getDeclaredField("LOCK").apply {
            isAccessible = true
        }.get(null)
        val controller = Robolectric.buildService(OwnerRoutineJobService::class.java).create()
        val service = controller.get()
        val params = Shadow.newInstanceOf(JobParameters::class.java)
        try {
            synchronized(storeLock) {
                assertTrue(service.onStartJob(params))
                until("Claim already entered the real store") {
                    val thread = field(service, "worker") as? Thread
                    thread?.state == Thread.State.BLOCKED && thread.stackTrace.any {
                        it.className == RoutineBook::class.java.name && it.methodName.startsWith("claimDue")
                    }
                }
                if (destroy) controller.destroy() else assertTrue(service.onStopJob(params))
                assertTrue((field(service, "cancellation") as AtomicBoolean).get())
                assertFalse("Durable dispatch is not interrupted", (field(service, "worker") as Thread).isInterrupted)
            }
            until("Cancelled claim is released") { field(service, "worker") == null }
            assertEquals(0, app.batteryReads.get())
            assertTrue(receipts(app).isEmpty())
            assertEquals(before.entries, store.load().entries)
            assertEquals(before.entries, runtime.book.registrations())
            assertTrue(runtime.book.pendingChatReports().isEmpty())
            assertNull(runtime.lastProblem)
            assertFalse(shadowOf(service).isJobFinished)
            val nextController = Robolectric.buildService(OwnerRoutineJobService::class.java).create()
            val next = nextController.get()
            try {
                assertTrue(next.onStartJob(Shadow.newInstanceOf(JobParameters::class.java)))
                until("Fresh job after cancelled persistence") { field(next, "worker") == null }
                assertEquals(1, app.batteryReads.get())
                assertEquals(ToolReceiptState.SUCCEEDED, receipts(app).single().state)
            } finally { nextController.destroy() }
        } finally { if (!destroy) controller.destroy() }
    }

    @Test fun ownerStopDuringAndroidAdmissionPreventsTheActualBatteryRead() {
        val app = context(); battery(app, 19)
        val runtime = OwnerRoutineRuntime.get(app); val routine = register(runtime)
        Memory.holdTool.set(true)
        val worker = Thread { runtime.runCycle(AtomicBoolean()) }
        worker.start()
        try {
            assertTrue(Memory.entered.await(5, TimeUnit.SECONDS))
            runtime.signalStop(routine.id)
            Memory.release.countDown()
            worker.join(10_000); assertFalse(worker.isAlive)
            assertTrue(runtime.stop(routine.id))
            assertEquals(0, app.batteryReads.get())
            assertTrue(receipts(app).none { it.state == ToolReceiptState.SUCCEEDED })
            assertTrue(runtime.book.registrations().isEmpty())
            assertTrue(runtime.book.pendingChatReports().isEmpty())
        } finally { Memory.release.countDown(); worker.join(10_000) }
    }

    @Test fun notStopRecheckedAfterResourceObservationBlocksToolStart() {
        val app = context(); battery(app, 19)
        val runtime = OwnerRoutineRuntime.get(app); register(runtime)
        Memory.holdTool.set(true)
        val worker = Thread { runtime.runCycle(AtomicBoolean()) }
        worker.start()
        try {
            assertTrue(Memory.entered.await(5, TimeUnit.SECONDS))
            assertTrue(ContinuumAutonomyControl.setLevel(app, ContinuumAutonomyLevel.PAUSED))
            Memory.release.countDown()
            worker.join(10_000); assertFalse(worker.isAlive)
            assertEquals(0, app.batteryReads.get())
            assertTrue(receipts(app).none { it.state == ToolReceiptState.SUCCEEDED })
            assertEquals(OwnerRoutineSchedule.PAUSED, runtime.restoreSchedule())
            assertNull(app.getSystemService(JobScheduler::class.java).getPendingJob(OwnerRoutineScheduler.JOB_ID))
        } finally { Memory.release.countDown(); worker.join(10_000) }
    }

    @Test fun realAndroidLowMemoryBlocksBeforeToolReceipt() {
        val app = context(); battery(app, 19)
        shadowOf(app.getSystemService(ActivityManager::class.java)).setMemoryInfo(ActivityManager.MemoryInfo().apply {
            lowMemory = true; availMem = 1
        })
        val runtime = OwnerRoutineRuntime.get(app); register(runtime)
        runtime.runCycle(AtomicBoolean())
        assertEquals(0, app.batteryReads.get()); assertTrue(receipts(app).isEmpty())
        assertTrue(runtime.book.pendingChatReports().single().result.contains("Arbeitsspeicher"))
        assertEquals(AgentPriority.URGENT, runtime.mailbox.peek("chief").single().priority)
    }

    @Test fun deniedShizukuIsReportedWithoutRequestingNewPermission() {
        val app = context()
        ShizukuBridgeLifecycleTest.Server.granted = false
        val runtime = OwnerRoutineRuntime.get(app); register(runtime, OwnerRoutineTarget.DOWNLOADS)
        runtime.runCycle(AtomicBoolean())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, ShizukuBridgeLifecycleTest.Server.permissionRequests)
        assertTrue(ShizukuBridgeLifecycleTest.Server.connections.isEmpty())
        assertTrue(receipts(app).isEmpty())
        assertTrue(runtime.book.pendingChatReports().single().result.contains("Shizuku-Freigabe"))
    }

    @Test @Config(sdk = [35]) fun actualThermalAdmissionBlocksBeforeWorkerTools() {
        val app = context(); battery(app, 19)
        shadowOf(app.getSystemService(PowerManager::class.java)).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
        val runtime = OwnerRoutineRuntime.get(app); register(runtime)
        runtime.runCycle(AtomicBoolean())
        assertEquals(0, app.batteryReads.get()); assertTrue(receipts(app).isEmpty())
        assertTrue(runtime.book.pendingChatReports().single().result.contains("Thermalgrenze"))
        assertEquals(AgentPriority.URGENT, runtime.mailbox.peek("chief").single().priority)
    }

    @Test fun firstPausedProcessRestoresFinishedReportWithoutWorkerReplay() {
        val app = context(); battery(app, 19)
        val before = OwnerRoutineRuntime.get(app); register(before)
        before.runCycle(AtomicBoolean())
        val message = before.mailbox.peek("chief").single()
        val receipt = receipts(app).single()
        assertEquals(1, app.batteryReads.get())
        assertTrue(ContinuumAutonomyControl.setLevel(app, ContinuumAutonomyLevel.PAUSED))
        reset()
        val reopened = OwnerRoutineRuntime.get(app)
        assertTrue(reopened.mailbox.peek("chief").isEmpty())
        reopened.ownerAppEntered(OwnerRoutineRuntime.markOwnerAppEntry())
        idle(reopened)
        assertEquals(listOf(message), reopened.mailbox.peek("chief"))
        assertEquals(listOf(receipt.callId), receipts(app).map { it.callId })
        assertEquals(1, app.batteryReads.get())
        assertNull(app.getSystemService(JobScheduler::class.java).getPendingJob(OwnerRoutineScheduler.JOB_ID))
    }

    @Test fun delayedShellDispatchCannotStartAfterRoutineRevocation() {
        val app = context()
        val runtime = OwnerRoutineRuntime.get(app); val routine = register(runtime, OwnerRoutineTarget.DOWNLOADS)
        val bridge = field(runtime, "bridge") as ShizukuBridge
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val completed = CountDownLatch(1)
        val calls = AtomicInteger()
        val dispatcher = CheckedShellCallDispatcher { task -> Thread {
            entered.countDown()
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); task.run() }
            finally { completed.countDown() }
        } }
        ShizukuBridge::class.java.getDeclaredField("checkedCalls").apply { isAccessible = true }.set(bridge, dispatcher)
        bridge.startGrantedOnly()
        ShizukuBridgeLifecycleTest.Server.connections.single().onServiceConnected(ComponentName("test", "test.Shell"),
            object : IDeviceShell.Stub() {
                override fun execute(command: String): String {
                    calls.incrementAndGet()
                    return ShellExecutionWire.encodeResult(ShellExecutionResult(ShellOutcome.COMPLETED, 0, "document.pdf"))
                }
                override fun destroy() { }
            })
        assertTrue(bridge.isReady())
        val worker = Thread { runtime.runCycle(AtomicBoolean()) }
        worker.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            runtime.signalStop(routine.id)
            assertTrue(runtime.stop(routine.id))
            release.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            worker.join(10_000); assertFalse(worker.isAlive)
            assertEquals("No Binder transaction after revocation", 0, calls.get())
            assertTrue(receipts(app).none { it.state == ToolReceiptState.SUCCEEDED })
            assertTrue(runtime.book.registrations().isEmpty())
        } finally { release.countDown(); worker.join(10_000) }
    }

    @Test fun persistentFailureAfterPartialAdmissionCancelsTheFinishedJobGeneration() {
        val app = context()
        val runtime = OwnerRoutineRuntime.get(app)
        register(runtime, OwnerRoutineTarget.DOWNLOADS); register(runtime, OwnerRoutineTarget.DEVICE)
        val bridge = field(runtime, "bridge") as ShizukuBridge
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val completed = CountDownLatch(1)
        val calls = AtomicInteger()
        ShizukuBridge::class.java.getDeclaredField("checkedCalls").apply { isAccessible = true }.set(bridge,
            CheckedShellCallDispatcher { task -> Thread {
                entered.countDown()
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); task.run() }
                finally { completed.countDown() }
            } })
        bridge.startGrantedOnly()
        ShizukuBridgeLifecycleTest.Server.connections.single().onServiceConnected(ComponentName("test", "test.Shell"),
            object : IDeviceShell.Stub() {
                override fun execute(command: String): String {
                    calls.incrementAndGet()
                    return ShellExecutionWire.encodeResult(ShellExecutionResult(ShellOutcome.COMPLETED, 0, "document.pdf"))
                }
                override fun destroy() { }
            })
        val dispatcher = field(runtime, "dispatcher") as RoutineDispatcher
        val admission = RoutineDispatcher::class.java.getDeclaredField("onAdmission").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = admission.get(dispatcher) as (WorkerBrief) -> Unit
        val admissions = AtomicInteger()
        val saved = AtomicReference<ByteArray?>()
        val faultPresent = AtomicBoolean()
        val generation = AtomicReference<AtomicBoolean?>()
        val store = File(app.filesDir.canonicalFile, "routines/owner-routines-v1.bin")
        admission.set(dispatcher, { brief: WorkerBrief ->
            original(brief)
            @Suppress("UNCHECKED_CAST") val current = field(runtime, "admitting") as ThreadLocal<AtomicBoolean>
            generation.set(current.get())
            if (admissions.incrementAndGet() == 2) {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                saved.set(store.readBytes())
                store.writeText("Controlled persistent I/O corruption")
                faultPresent.set(true)
            }
        })
        val controller = Robolectric.buildService(OwnerRoutineJobService::class.java).create()
        val service = controller.get()
        try {
            assertTrue(service.onStartJob(Shadow.newInstanceOf(JobParameters::class.java)))
            until("Actual JobService reports its storage failure") { shadowOf(service).isJobFinished }
            assertEquals(2, admissions.get()); assertNotNull(saved.get())
            assertTrue("Partially admitted generation is revoked", requireNotNull(generation.get()).get())
            assertNotNull(runtime.lastProblem)
            store.writeBytes(requireNotNull(saved.get()))
            faultPresent.set(false)
            release.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertTrue((field(runtime, "pool") as ChiefWorkerPool).awaitIdle(10_000))
            assertEquals(0, calls.get())
            assertTrue(receipts(app).none { it.state == ToolReceiptState.SUCCEEDED })
        } finally {
            if (faultPresent.get()) saved.get()?.let(store::writeBytes)
            release.countDown(); controller.destroy()
        }
    }

    @Test fun trustedFilesParentAliasWorksButInnerRoutineLinkIsRejected() {
        val original = context()
        val parent = File(original.cacheDir, "routine-paths").apply { mkdirs() }
        val actual = File(parent, "actual").apply { mkdirs() }
        val alias = File(parent, "alias").toPath()
        Files.createSymbolicLink(alias, actual.toPath())
        val app = ObservedContext(original, alias.toFile())
        val runtime = OwnerRoutineRuntime.get(app)
        register(runtime, event = true)
        assertEquals(1, FileRoutineStateStore(File(actual, "routines")).load().entries.size)
        battery(app, 19)
        runtime.runCycle(AtomicBoolean(), OwnerRoutineDraft.APP_START)
        val message = requireNotNull(runtime.book.pendingChatReports().single().message)
        val transcript = at.heinz.deviceai.cognitive.LocalChatTranscriptStore(File(app.filesDir.canonicalFile, "chat-history"))
        val conversation = java.util.UUID.randomUUID().toString()
        assertNotNull(runtime.delivery(transcript).deliver(message.id, conversation))
        assertEquals(listOf(message.body), transcript.load(conversation).map { it.text })
        assertTrue(File(actual, "chat-history/$conversation.jsonl").isFile)
        reset()
        val untrusted = File(parent, "untrusted").apply { mkdirs() }
        val outside = File(parent, "outside").apply { mkdirs() }
        Files.createSymbolicLink(File(untrusted, "routines").toPath(), outside.toPath())
        assertThrows(Exception::class.java) { OwnerRoutineRuntime.get(ObservedContext(original, untrusted)) }
        assertTrue(outside.listFiles()!!.isEmpty())
    }
}
