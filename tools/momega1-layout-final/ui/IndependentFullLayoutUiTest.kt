package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual complete Activity/View tree under native host Canvas. Synthetic empty
 * owner storage and explicit presentation text; no device, model or Filament proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IndependentFullLayoutUiTest {
    private fun field(a: AgentActivity, name: String) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun call(a: AgentActivity, name: String) = AgentActivity::class.java
        .getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)
    private fun message(a: AgentActivity, name: String, text: String) = AgentActivity::class.java
        .getDeclaredMethod(name, String::class.java).apply { isAccessible = true }.invoke(a, text)
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun snapshot(a: AgentActivity, label: String, width: Int, height: Int) {
        val root = a.window.decorView
        fun exact(n: Int) = View.MeasureSpec.makeMeasureSpec(n, View.MeasureSpec.EXACTLY)
        root.measure(exact(width), exact(height)); root.layout(0, 0, width, height)
        val out = File(System.getProperty("continuum.layout.evidenceDir")
            ?: error("Explicit isolated evidence directory required")).apply { mkdirs() }
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(image))
            File(out, "$label.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
        File(out, "$label.views.txt").writeText(views(root).joinToString("\n") { view ->
            val at = IntArray(2); view.getLocationOnScreen(at)
            "${view.javaClass.simpleName} shown=${view.isShown} visibility=${view.visibility} x=${at[0]} y=${at[1]} w=${view.width} h=${view.height} text=" +
                (if (view is TextView) view.text.toString().replace('\n', ' ').take(160) else view.contentDescription.orEmpty())
        })
        println("LAYOUT_CAPTURE $label ${width}x$height nativeCanvas window=${root.windowVisibility}")
    }
    private fun capture(label: String, width: Int, height: Int) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit().putBoolean("introduced", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            val a = controller.get()
            val head = field(a, "hologramView") as ContinuumHologramView
            assertEquals("A hidden host window is not a valid screenshot", View.VISIBLE, a.window.decorView.windowVisibility)
            assertTrue("Actual default orb must be visible in home", head.isShown)
            shadowOf(Looper.getMainLooper()).idleFor(80, TimeUnit.MILLISECONDS)
            snapshot(a, "$label-home-orb-host", width, height)
            call(a, "focusChatComposer")
            message(a, "addUserMessage", "Prüfe bitte die Darstellung dieser Ansicht.")
            message(a, "addAssistantMessage", "Synthetischer Layout-Prüftext: Stimme, Kamera und Modell werden hier nicht ausgeführt.")
            shadowOf(Looper.getMainLooper()).idleFor(80, TimeUnit.MILLISECONDS)
            snapshot(a, "$label-chat-orb-host", width, height)
            assertTrue("One actual avatar remains visible in chat", head.isShown)
            assertTrue("Composer remains visible", (field(a, "input") as View).isShown)
            assertSame(head, field(a, "hologramView"))
        } finally {
            runCatching { controller.pause() }; runCatching { controller.stop() }; runCatching { controller.destroy() }
        }
    }
    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun phoneHomeAndChat() = capture("phone-412x915", 412, 915)
    @Test @Config(qualifiers = "w840dp-h649dp-mdpi-land")
    fun foldLandscapeHomeAndChat() = capture("fold-landscape-840x649", 840, 649)
    @Test @Config(qualifiers = "w840dp-h1050dp-mdpi-port")
    fun foldPortraitHomeAndChat() = capture("fold-portrait-840x1050", 840, 1050)
}
