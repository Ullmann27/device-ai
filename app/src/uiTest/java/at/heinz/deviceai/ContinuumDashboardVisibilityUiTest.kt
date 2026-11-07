package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.View
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.ContinuumSystemGaugesView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Real navigation; controlled telemetry executor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w412dp-h915dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumDashboardVisibilityUiTest {
    private class QueuedIo : AbstractExecutorService() {
        val pending = ArrayDeque<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { check(!stopped); pending.addLast(command) }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> = pending.toMutableList().also {
            stopped = true; pending.clear()
        }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && pending.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }

    private fun get(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun set(activity: AgentActivity, name: String, value: Any?) {
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }

    private fun call(activity: AgentActivity, name: String, vararg args: Any?): Any? =
        AgentActivity::class.java.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(activity, *args)

    private fun withPane(test: (AgentActivity, QueuedIo, ContinuumHomeView) -> Unit) {
        val activity = Robolectric.buildActivity(AgentActivity::class.java).get()
        val io = QueuedIo()
        (get(activity, "learningIoExecutor") as ExecutorService).shutdownNow()
        set(activity, "learningIoExecutor", io)
        set(activity, "bootstrapMemory", BootstrapMemoryStore(activity))
        set(activity, "foregroundLearningActive", true)
        set(activity, "voiceEnabled", false)
        val pane = call(activity, "buildConversationPane", false) as View
        val home = get(activity, "homeView") as ContinuumHomeView
        val head = get(activity, "hologramView") as ContinuumHologramView
        head.setNativeHeadEnabled(false)
        head.setAnimationsEnabled(false)
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            host.get().setContentView(pane)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(home.isShown)
            assertTrue(io.pending.isEmpty())
            test(activity, io, home)
        } finally {
            set(activity, "foregroundLearningActive", false)
            (get(activity, "mainHandler") as Handler).removeCallbacksAndMessages(null)
            (get(activity, "centerDialog") as? AlertDialog)?.dismiss()
            head.setForegroundActive(false)
            home.setForegroundActive(false)
            (get(activity, "cockpitSubscriptions") as List<*>).forEach { (it as AutoCloseable).close() }
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
            for (name in listOf("localAgentExecutor", "backlogExecutor", "learningIoExecutor", "documentIoExecutor"))
                (get(activity, name) as ExecutorService).shutdownNow()
            host.pause().stop().destroy()
        }
    }

    @Test fun hiddenCockpitDoesNotQueueTelemetryReads() = withPane { activity, io, home ->
        call(activity, "focusChatComposer")
        assertFalse(home.isShown)
        val tick = get(activity, "dashboardRefresh") as Runnable
        tick.run()
        shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.SECONDS)
        assertEquals("Hidden cockpit must not scan learning, storage or diagnostics", 0, io.pending.size)
        assertEquals(false, get(activity, "dashboardCollecting"))
    }

    @Test fun returningToCockpitRefreshesImmediatelyAndCoalesces() = withPane { activity, io, home ->
        call(activity, "focusChatComposer")
        assertFalse(home.isShown)
        call(activity, "showHomeDashboard")
        assertTrue(home.isShown)
        assertEquals("Returning home must not wait for the next five-second tick", 1, io.pending.size)
        call(activity, "refreshConversationLayout")
        (get(activity, "dashboardRefresh") as Runnable).run()
        assertEquals("One collection remains in flight", 1, io.pending.size)
    }

    @Test fun visibleSystemDialogStillRefreshesFromChat() = withPane { activity, io, home ->
        call(activity, "focusChatComposer")
        assertFalse(home.isShown)
        val gauges = ContinuumSystemGaugesView(activity, home.state.system)
        val dialog = AlertDialog.Builder(activity).setView(gauges).create()
        set(activity, "liveHardwareGauges", gauges)
        set(activity, "centerDialog", dialog)
        dialog.show()
        assertTrue(gauges.isShown)
        (get(activity, "dashboardRefresh") as Runnable).run()
        assertEquals("Visible system telemetry remains live", 1, io.pending.size)
    }

    @Test fun detachedOrPausedDashboardDoesNotQueueTelemetryReads() = withPane { activity, io, home ->
        set(activity, "foregroundLearningActive", false)
        (get(activity, "dashboardRefresh") as Runnable).run()
        assertTrue(io.pending.isEmpty())
        set(activity, "foregroundLearningActive", true)
        (home.parent as android.view.ViewGroup).removeView(home)
        call(activity, "refreshDashboard")
        assertEquals("Detached surfaces cannot consume telemetry", 0, io.pending.size)
    }
}
