package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
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
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Real views, scripted lifecycle input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CinemaNativeSequenceUiTest {
    private fun field(name: String) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }

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
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        var pump: CockpitHostFramePump? = null
        val hashes = mutableSetOf<String>()
        try {
            controller.setup().visible().windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            val home = field("homeView").get(activity) as ContinuumHomeView
            val head = field("hologramView").get(activity) as ContinuumHologramView
            val triggers = field("cockpitTriggers").get(activity) as CockpitAnimationTriggers
            val framePump = CockpitHostFramePump { root }
            pump = framePump
            head.setAnimationsEnabled(true)
            head.setForegroundActive(true)
            home.setForegroundActive(true)
            fun measure() {
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
            }
            fun advance(millis: Long) {
                var remaining = millis
                while (remaining > 0) {
                    val step = minOf(16L, remaining)
                    shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
                    measure()
                    framePump.drawRequestedFrame()
                    remaining -= step
                }
            }
            fun capture(phase: String) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(bitmap))
                    val bytes = java.io.ByteArrayOutputStream().use {
                        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        it.toByteArray()
                    }
                    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                        .joinToString("") { "%02x".format(it) }
                    hashes += hash
                    System.getProperty("continuum.ui.evidenceDir")?.let { path ->
                        val dir = File(path).apply { mkdirs() }
                        File(dir, "$label-$phase.png").writeBytes(bytes)
                        File(dir, "$label-$phase.json").writeText(JSONObject()
                            .put("source_commit", System.getenv("CONTINUUM_CAPTURE_SOURCE_COMMIT"))
                            .put("phase", phase).put("sha256", hash)
                            .put("width", width).put("height", height)
                            .put("backend", "Robolectric API28 native Canvas")
                            .put("inputs", "Scripted command and reasoning lifecycle; no model inference")
                            .put("avatar", "Existing 2D fallback")
                            .put("Filament", "NOT_RUN").put("Fold7", "NOT_RUN").toString(2))
                    }
                } finally { bitmap.recycle() }
            }
            fun start(turn: String) {
                field("busy").set(activity, true)
                field("activeLocalRequest").set(activity, true)
                triggers.commandAcknowledged(turn, "Prüfauftrag: Arbeitsszene darstellen")
                AgentActivity::class.java.getDeclaredMethod("presentLocalReasoningWorkScene",
                    String::class.java, String::class.java).apply { isAccessible = true }
                    .invoke(activity, turn, "Prüfauftrag: Arbeitsszene darstellen")
            }
            fun end(turn: String, success: Boolean) {
                AgentActivity::class.java.getDeclaredMethod("finishLocalReasoningWorkScene",
                    String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
                    .invoke(activity, turn, false)
                triggers.turnEnded(turn, success)
                field("busy").set(activity, false)
                field("activeLocalRequest").set(activity, false)
            }
            fun visibleWindows(view: View): Int =
                (if (view.tag == "jarvis-work-window" && view.isShown) 1 else 0) +
                    if (view is ViewGroup) (0 until view.childCount).sumOf { visibleWindows(view.getChildAt(it)) } else 0
            measure()
            advance(1800)
            capture("idle")
            start("cinema-native-success")
            var last = 0L
            for (time in listOf(140L, 280L, 650L, 1180L, 2100L)) {
                advance(time - last)
                capture("work-${time}ms")
                last = time
            }
            assertEquals("One active work window", 1, visibleWindows(root))
            end("cinema-native-success", true)
            advance(650)
            capture("return-650ms")
            advance(1750)
            capture("returned")
            assertEquals(0, visibleWindows(root))
            start("cinema-native-abort")
            advance(280)
            end("cinema-native-abort", false)
            advance(2400)
            capture("aborted-return")
            assertEquals(0, visibleWindows(root))
            assertSame(head, field("hologramView").get(activity))
            assertTrue("Distinct sampled frames", hashes.size >= 6)
        } finally {
            pump?.close()
            controller.close()
        }
    }

    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi")
    fun innerSequence() = sequence("native-inner", 884, 760)

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun coverSequence() = sequence("native-cover", 412, 915)
}
