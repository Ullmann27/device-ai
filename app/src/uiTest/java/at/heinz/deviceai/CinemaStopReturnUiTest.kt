package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CinemaStopReturnUiTest {
    private fun field(name: String) = AgentActivity::class.java.getDeclaredField(name)
        .apply { isAccessible = true }

    private fun children(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()

    @Test fun stopCancelsImmediatelyButReturnsVisibleWindow() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit()
            .clear().putBoolean("jarvis-shell", true).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        var pump: CockpitHostFramePump? = null
        try {
            controller.setup().visible()
            shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
            controller.windowFocusChanged(true)
            val a = controller.get()
            val root = a.window.decorView
            val home = field("homeView").get(a) as ContinuumHomeView
            val avatar = field("hologramView").get(a)
            val frames = CockpitHostFramePump { root }
            pump = frames
            fun advance(ms: Long) {
                repeat((ms / 16).toInt()) {
                    shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
                    root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 884, 760)
                    frames.drawRequestedFrame()
                }
            }
            fun capture(label: String) {
                val path = System.getProperty("continuum.ui.evidenceDir") ?: return
                val bitmap = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(bitmap))
                    val dir = File(path).apply { mkdirs() }
                    File(dir, "$label.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    File(dir, "$label.json").writeText(org.json.JSONObject()
                        .put("source_commit", System.getenv("CONTINUUM_CAPTURE_SOURCE_COMMIT"))
                        .put("backend", "Robolectric API28 native Canvas, 2D fallback")
                        .put("input", "Actual decor STOP touch; scripted work lifecycle")
                        .put("model_inference", "NOT_RUN").put("Fold7", "NOT_RUN").toString(2))
                } finally { bitmap.recycle() }
            }
            home.setForegroundActive(true)
            advance(1800)
            field("busy").set(a, true)
            field("activeLocalRequest").set(a, true)
            val triggers = field("cockpitTriggers").get(a) as CockpitAnimationTriggers
            triggers.commandAcknowledged("stop-proof", "Prüfauftrag")
            AgentActivity::class.java.getDeclaredMethod("presentLocalReasoningWorkScene",
                String::class.java, String::class.java).apply { isAccessible = true }
                .invoke(a, "stop-proof", "Prüfauftrag")
            advance(1800)
            val window = children(root).single { it.tag == "jarvis-work-window" && it.isShown }
            val stop = children(window).filterIsInstance<TextView>().single { it.text == "STOP" }
            JarvisHomeTestRoutes.scrollIntoView(stop)
            advance(32)
            val bounds = JarvisHomeTestRoutes.visibleBounds(stop)
            val location = IntArray(2).also(root::getLocationOnScreen)
            val at = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(at, at + 16, action,
                    bounds.exactCenterX() - location[0], bounds.exactCenterY() - location[1], 0)
                try { assertTrue(root.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            advance(32)
            assertNull("Cancelled scene cannot accept deltas", field("localReasoningWorkScene").get(a))
            assertEquals(false, field("activeLocalRequest").get(a))
            assertTrue("Visible window must return before removal", window.isShown)
            capture("stop-32ms")
            field("busy").set(a, true)
            field("activeLocalRequest").set(a, true)
            AgentActivity::class.java.getDeclaredMethod("scheduleLocalReasoningWorkScene",
                String::class.java, String::class.java, Long::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(a, "next-request", "Nächster Prüfauftrag", 1000L)
            val next = field("localReasoningWorkScene").get(a)
            val generation = field("workPresentationGeneration").get(a)
            assertTrue(stop.callOnClick())
            assertSame("Old STOP cannot cancel next lease", next, field("localReasoningWorkScene").get(a))
            assertEquals(generation, field("workPresentationGeneration").get(a))
            assertEquals(true, field("activeLocalRequest").get(a))
            AgentActivity::class.java.getDeclaredMethod("finishLocalReasoningWorkScene",
                String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                .invoke(a, "next-request", true)
            field("busy").set(a, false)
            field("activeLocalRequest").set(a, false)
            // Scripted worker acknowledges cancellation.
            triggers.turnEnded("stop-proof", false)
            advance(320)
            capture("stop-352ms")
            advance(2080)
            assertFalse(window.isShown)
            assertFalse(children(root).any { it.tag == "jarvis-work-window" && it.isShown })
            assertSame(avatar, field("hologramView").get(a))
            capture("stop-returned")
        } finally {
            pump?.close()
            controller.close()
        }
    }
}
