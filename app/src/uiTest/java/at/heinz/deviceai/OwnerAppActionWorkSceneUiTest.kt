package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real Activity; controlled device boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerAppActionWorkSceneUiTest {
    private class Port : DeviceAppActionPort {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        var failure: String? = null
        override fun execute(call: ToolCallRequest, callback: (Result<String>) -> Unit) {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            callback(failure?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(
                """{"schema":"CONTINUUM_DEVICE_ACTION_V1","action":"LAUNCH_APP","package":"org.example.signal","status":"VERIFIED_FOREGROUND","windowRevision":7}"""))
        }
        override fun cancelPending() { release.countDown() }
    }

    private var pump: CockpitHostFramePump? = null
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }
    private fun get(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(owner, value)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 16L)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            pump?.drawRequestedFrame()
            left -= step
        }
    }
    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 884, 760)
    }
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < end) { advance(16); Thread.sleep(5) }
        assertTrue("Expected real callback", condition())
    }
    private fun withActivity(body: (AgentActivity, Port, ActivityController<AgentActivity>) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        LocalDialogueRetention.context.clear()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val info = ApplicationInfo().apply { packageName = "org.example.signal"; nonLocalizedLabel = "Signal" }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = info.packageName; applicationInfo = info
        })
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = info.packageName; name = "Main"; applicationInfo = info }
                nonLocalizedLabel = "Signal"
            })
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        val port = Port()
        try {
            controller.setup().visible()
            val a = controller.get()
            val root = a.window.decorView
            pump = CockpitHostFramePump { root }
            measure(root); advance(32)
            controller.windowFocusChanged(true)
            set(a, "executor", DeviceToolExecutor(a, { false }, { _, callback ->
                callback(Result.failure(IllegalStateException("No bridge in this fixture")))
            }, port))
            (get(a, "hologramView") as ContinuumHologramView).setAnimationsEnabled(true)
            measure(root); advance(80); measure(root)
            body(a, port, controller)
        } finally {
            port.release.countDown()
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            pump?.close(); pump = null
            runCatching { controller.close() }
            LocalDialogueRetention.context.clear()
        }
    }
    private fun submit(a: AgentActivity, request: String, chat: Boolean = true) {
        if (chat) {
            val target = a.window.decorView.findViewWithTag<View>("jarvis-tile:chat")
            assertNotNull(target)
            assertTrue(target.performClick())
        }
        (get(a, "input") as EditText).setText(request)
        assertTrue((get(a, "sendButton") as Button).performClick())
    }
    private fun approve(): AlertDialog {
        await { ShadowAlertDialog.getLatestAlertDialog()?.let {
            it.isShowing && shadowOf(it).message.toString().contains("org.example.signal")
        } == true }
        return ShadowAlertDialog.getLatestAlertDialog().also {
            it.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
    }
    private fun pose(a: AgentActivity) = (get(get(a, "hologramView")!!, "avatarPose") as AvatarPoseReducer)
        .pose(System.currentTimeMillis())

    @Test fun chatInputOpensRealWorkWindowHoldsGazeAndStopsProgressAtResult() = withActivity { a, port, _ ->
        submit(a, "Jarvis kannst du mir bitte Signal öffnen?")
        assertEquals(0, port.calls.get())
        approve()
        assertTrue(port.entered.await(5, TimeUnit.SECONDS))
        advance(80); measure(a.window.decorView); advance(3_200)
        val views = descendants(a.window.decorView)
        assertTrue(views.any { it.tag == "jarvis-work-window" && it.isShown })
        assertTrue(views.filterIsInstance<ProgressBar>().any { it.isShown && it.isIndeterminate })
        assertTrue("Actual tool gaze outlives opening glance", pose(a).reading)
        assertEquals(ContinuumWorkArea.START, get(a, "activeWorkArea"))
        port.release.countDown()
        await { get(a, "busy") == false }
        assertFalse(pose(a).reading)
        assertTrue(descendants(a.window.decorView).filterIsInstance<ProgressBar>()
            .filter { it.tag?.toString()?.startsWith("jarvis-tool-progress:") == true }.none { it.isIndeterminate })
        advance(2_200)
        assertEquals(ContinuumWorkArea.CHAT, get(a, "activeWorkArea"))
        assertTrue(descendants(a.window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("neuen aktiven Fenster") })
    }

    @Test fun stopBeforePostedBackendStartCannotReopenScene() = withActivity { a, port, _ ->
        submit(a, "Öffne Signal", chat = false)
        approve()
        assertTrue(port.entered.await(5, TimeUnit.SECONDS))
        (get(a, "sendButton") as Button).performClick()
        advance(3_000)
        assertFalse(pose(a).reading)
        assertFalse(descendants(a.window.decorView).any { it.tag == "jarvis-work-window" && it.isShown })
        assertNull(get(a, "activeOwnerAppFlow"))
    }

    @Test fun foregroundLaunchKeepsActualResultForReturn() = withActivity { a, port, controller ->
        submit(a, "Mach mir Signal auf", chat = false)
        approve()
        assertTrue(port.entered.await(5, TimeUnit.SECONDS))
        advance(80)
        controller.pause()
        port.release.countDown()
        await { get(a, "pendingAppActionReply") != null }
        assertFalse(descendants(a.window.decorView).any { it.tag == "jarvis-work-window" && it.isShown })
        controller.resume()
        advance(80)
        assertNull(get(a, "pendingAppActionReply"))
        assertTrue(descendants(a.window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("neuen aktiven Fenster") })
    }

    @Test fun missingAppAndPermissionNeverBecomeSuccessText() = withActivity { a, port, _ ->
        submit(a, "Öffne Nichtinstalliert", chat = false)
        await { get(a, "busy") == false }
        assertEquals(0, port.calls.get())
        port.failure = "ACCESSIBILITY_OBSERVATION_NOT_GRANTED"
        submit(a, "Öffne Signal", chat = false)
        approve()
        port.release.countDown()
        await { get(a, "busy") == false }
        assertFalse(descendants(a.window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("wurde in einem neuen aktiven Fenster beobachtet") })
    }
}
