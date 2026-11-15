package at.heinz.deviceai.chief

import android.app.ActivityManager
import android.os.PowerManager
import at.heinz.deviceai.ShizukuBridgeLifecycleTest
import at.heinz.deviceai.ToolReceiptState
import at.heinz.deviceai.cognitive.ContinuumAutonomyControl
import at.heinz.deviceai.cognitive.ContinuumAutonomyLevel
import at.heinz.deviceai.cognitive.LocalChatTranscriptStore
import at.heinz.deviceai.routines.*
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.battery
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.context
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.receipts
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.register
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.reset
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [ShizukuBridgeLifecycleTest.Server::class])
@LooperMode(LooperMode.Mode.PAUSED)
class ChiefResourceAdmissionUiTest {
    @Before fun before() { reset(); ShizukuBridgeLifecycleTest().reset() }
    @After fun after() { reset() }

    @Test fun s8d_realResourceAdmissionStopsNewToolsButDeliversFinishedReports() {
        val app = context()
        battery(app, 19)
        val memory = shadowOf(app.getSystemService(ActivityManager::class.java))
        val power = shadowOf(app.getSystemService(PowerManager::class.java))
        fun observeMemory(low: Boolean) = memory.setMemoryInfo(ActivityManager.MemoryInfo().apply {
            lowMemory = low
            availMem = if (low) 1L else 4L shl 30
            totalMem = 8L shl 30
        })
        observeMemory(false)
        power.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
        val runtime = OwnerRoutineRuntime.get(app)
        val routine = register(runtime, event = true)
        val ownerLevel = ContinuumAutonomyControl.level(app)
        fun runEvent() = runtime.runCycle(AtomicBoolean(), OwnerRoutineDraft.APP_START)

        runEvent()
        val original = receipts(app).single()
        assertEquals(ToolReceiptState.SUCCEEDED, original.state)
        assertEquals("get_battery_info", original.toolName)
        assertEquals(1, app.batteryReads.get())
        val completed = requireNotNull(runtime.book.pendingChatReports().single().message)

        observeMemory(true)
        runEvent()
        assertEquals(1, app.batteryReads.get())
        assertEquals(listOf(original.callId), receipts(app).map { it.callId })
        val lowMemory = runtime.book.pendingChatReports().single {
            it.result.contains("Arbeitsspeicher")
        }
        assertEquals(AgentPriority.URGENT, requireNotNull(lowMemory.message).priority)

        observeMemory(false)
        power.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
        runEvent()
        assertEquals(1, app.batteryReads.get())
        assertEquals(listOf(original.callId), receipts(app).map { it.callId })
        val hot = runtime.book.pendingChatReports().single { it.result.contains("Thermalgrenze") }
        assertEquals(AgentPriority.URGENT, requireNotNull(hot.message).priority)
        assertEquals(3, runtime.book.pendingChatReports().size)

        assertTrue(ContinuumAutonomyControl.setLevel(app, ContinuumAutonomyLevel.PAUSED))
        val held = FileRoutineStateStore(File(app.filesDir.canonicalFile, "routines")).load()
        runEvent()
        assertEquals(held, FileRoutineStateStore(File(app.filesDir.canonicalFile, "routines")).load())
        assertEquals(1, app.batteryReads.get())
        val transcript = LocalChatTranscriptStore(File(app.filesDir.canonicalFile, "chat-history"))
        val conversation = UUID.randomUUID().toString()
        val queued = runtime.mailbox.peek("chief")
        assertEquals(3, queued.size)
        assertTrue(queued.contains(completed))
        val delivery = runtime.delivery(transcript)
        queued.forEach { assertNotNull(delivery.deliver(it.id, conversation)) }
        assertEquals(queued.map { it.body }, transcript.load(conversation).map { it.text })
        assertTrue(runtime.book.pendingChatReports().isEmpty())
        assertTrue(runtime.mailbox.peek("chief").isEmpty())

        power.setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
        assertTrue(ContinuumAutonomyControl.setLevel(app, ownerLevel))
        runEvent()
        assertEquals(2, app.batteryReads.get())
        assertEquals(2, receipts(app).map { it.callId }.distinct().size)
        assertTrue(receipts(app).all { it.state == ToolReceiptState.SUCCEEDED })
        runtime.runCycle(AtomicBoolean(true), OwnerRoutineDraft.APP_START)
        assertEquals(2, app.batteryReads.get())
        assertTrue(runtime.stop(routine.id))
        runEvent()
        assertEquals(2, app.batteryReads.get())
        assertTrue(runtime.book.registrations().isEmpty())
        assertEquals(3, transcript.load(conversation).size)
        assertNull(runtime.lastProblem)
    }
}
