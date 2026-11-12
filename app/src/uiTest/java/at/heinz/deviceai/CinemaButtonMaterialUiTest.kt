package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.ui.ContinuumHomeView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Native controls and production drawing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CinemaButtonMaterialUiTest {
    private fun field(value: Any, name: String): Any? {
        value.javaClass.declaredFields.firstOrNull { it.name == name }?.let {
            return it.apply { isAccessible = true }.get(value)
        }
        val getter = "get" + name.replaceFirstChar { it.uppercaseChar() }
        return value.javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }
            ?.invoke(value)
    }

    private fun children(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()

    private fun sequence(label: String, width: Int, height: Int) {
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
            val activity = controller.get()
            val root = activity.window.decorView
            val home = field(activity, "homeView") as ContinuumHomeView
            val avatar = field(activity, "hologramView")
            val frames = CockpitHostFramePump { root }
            pump = frames
            home.setForegroundActive(true)
            fun advance(ms: Long) {
                var left = ms
                while (left > 0) {
                    val step = minOf(16L, left)
                    shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                    frames.drawRequestedFrame()
                    left -= step
                }
            }
            fun capture(phase: String) {
                val path = System.getProperty("continuum.ui.evidenceDir") ?: return
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(bitmap))
                    val dir = File(path).apply { mkdirs() }
                    File(dir, "$label-$phase.png").outputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    File(dir, "$label-$phase.json").writeText(JSONObject()
                        .put("source_commit", System.getenv("CONTINUUM_CAPTURE_SOURCE_COMMIT"))
                        .put("backend", "Robolectric API28 native Canvas; 2D fallback")
                        .put("input", "Real decor DOWN/CANCEL/UP on bottom:device")
                        .put("pressed_key", field(home.jarvisShell, "pressedControlKey") ?: JSONObject.NULL)
                        .put("model_inference", "NOT_RUN").put("Fold7", "NOT_RUN").toString(2))
                } finally { bitmap.recycle() }
            }
            advance(1800)
            val button = children(home).single { it.tag == "jarvis-bottom:device" && it.isShown }
            JarvisHomeTestRoutes.scrollIntoView(button)
            advance(32)
            val bounds = JarvisHomeTestRoutes.visibleBounds(button)
            val rootPosition = IntArray(2).also(root::getLocationOnScreen)
            var downAt = SystemClock.uptimeMillis()
            fun touch(action: Int) {
                if (action == MotionEvent.ACTION_DOWN) downAt = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action,
                    bounds.exactCenterX() - rootPosition[0], bounds.exactCenterY() - rootPosition[1], 0)
                try { assertTrue(root.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            capture("idle")
            touch(MotionEvent.ACTION_DOWN)
            advance(160)
            capture("held-160ms")
            val heldKey = field(home.jarvisShell, "pressedControlKey")
            assertEquals("Press never changes hit bounds", bounds, JarvisHomeTestRoutes.visibleBounds(button))
            touch(MotionEvent.ACTION_CANCEL)
            advance(64)
            capture("cancelled")
            val cancelledKey = field(home.jarvisShell, "pressedControlKey")
            assertFalse(children(root).any { it.tag == "jarvis-work-window" && it.isShown })
            touch(MotionEvent.ACTION_DOWN)
            advance(160)
            home.setForegroundActive(false)
            assertNull("Pause clears material pressure", field(home.jarvisShell, "pressedControlKey"))
            assertFalse("Pause cancels native pressure", button.isPressed)
            home.setForegroundActive(true)
            advance(64)
            assertNull("Resume keeps pressure clear", field(home.jarvisShell, "pressedControlKey"))
            touch(MotionEvent.ACTION_CANCEL)
            assertFalse(children(root).any { it.tag == "jarvis-work-window" && it.isShown })
            touch(MotionEvent.ACTION_DOWN)
            advance(160)
            touch(MotionEvent.ACTION_UP)
            advance(32)
            val releasedKey = field(home.jarvisShell, "pressedControlKey")
            advance(640)
            capture("opening-672ms")
            advance(550)
            capture("opened")
            val window = children(root).single { it.tag == "jarvis-work-window" && it.isShown }
            val content = activity.findViewById<View>(android.R.id.content)
            val contentPosition = IntArray(2).also(content::getLocationOnScreen)
            val expectedOrigin = RectF(bounds).apply {
                offset(-contentPosition[0].toFloat(), -contentPosition[1].toFloat())
            }
            val origin = field(window, "origin") as RectF
            assertEquals("Clicked control owns origin X", expectedOrigin.centerX(), origin.centerX(), 1.5f)
            assertEquals("Clicked control owns origin Y", expectedOrigin.centerY(), origin.centerY(), 1.5f)
            assertSame(avatar, field(activity, "hologramView"))
            assertEquals("Dock press reaches drawn material", "bottom:device", heldKey)
            assertNull("Cancel clears material pressure", cancelledKey)
            assertNull("Release clears material pressure", releasedKey)
        } finally {
            pump?.close()
            controller.close()
        }
    }

    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi")
    fun innerDockPress() = sequence("button-inner", 884, 760)

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun coverDockPress() = sequence("button-cover", 412, 915)
}
