package at.heinz.deviceai.routines

import android.os.Looper
import at.heinz.deviceai.ShizukuBridgeLifecycleTest
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.battery
import at.heinz.deviceai.routines.OwnerRoutineAndroidSupport.context
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
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ShizukuBridgeLifecycleTest.Server::class])
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerRoutineArrivalUiTest {
    @Before fun before() { reset(); ShizukuBridgeLifecycleTest().reset() }
    @After fun after() { reset() }

    @Test fun completedInfoReportReachesAttachedOwnerWithoutUrgency() {
        val app = context(); battery(app, 67)
        val runtime = OwnerRoutineRuntime.get(app)
        val observed = mutableListOf<AgentMessage>()
        var mainThread = false
        runtime.attachChief {
            mainThread = Looper.myLooper() == Looper.getMainLooper()
            observed += runtime.mailbox.peek("chief")
        }
        register(runtime, OwnerRoutineTarget.BATTERY)
        runtime.runCycle(AtomicBoolean())
        shadowOf(Looper.getMainLooper()).idle()
        val saved = runtime.book.pendingChatReports().single()
        assertEquals(listOf(saved.message), observed)
        assertEquals(AgentPriority.INFO, observed.single().priority)
        assertTrue(mainThread)
        assertEquals(1, app.batteryReads.get())
    }

    @Test fun detachedOwnerReceivesNoLateCallback() {
        val app = context(); battery(app, 67)
        val runtime = OwnerRoutineRuntime.get(app)
        var calls = 0
        val listener: () -> Unit = { calls++ }
        runtime.attachChief(listener)
        register(runtime, OwnerRoutineTarget.BATTERY)
        runtime.runCycle(AtomicBoolean())
        runtime.detachChief(listener)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, calls)
        assertEquals(AgentPriority.INFO, runtime.mailbox.peek("chief").single().priority)
        assertEquals(1, runtime.book.pendingChatReports().size)
    }
}
