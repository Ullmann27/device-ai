package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisShellLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/** Welten-Optik's JarvisShellView (#479) embedded in the real home: bottom layer, live avatar in its AVATAR slot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JarvisShellStageUiTest {
    private fun field(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun run(label: String, width: Int, height: Int) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit().putBoolean("introduced", true).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("jarvis-shell", true).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            val home = field(activity, "homeView") as ContinuumHomeView
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            avatar.setAnimationsEnabled(false)
            home.setForegroundActive(false)
            fun measure() {
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
            }
            measure(); measure()
            assertTrue(home.jarvisShellStage)
            assertEquals(View.VISIBLE, home.jarvisShell.visibility)
            assertEquals("shell is the bottom layer", 0, home.indexOfChild(home.jarvisShell))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val shell = requireNotNull(home.jarvisShell.shell) { "shell drawn" }
            val d = home.resources.displayMetrics.density
            val box = requireNotNull(shell[JarvisShellLayout.Slot.AVATAR])
            assertEquals("live avatar sits in the AVATAR slot", (box.left * d).toInt(), avatar.left)
            assertEquals((box.top * d).toInt(), avatar.top)
            System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
                File(dir).mkdirs()
                File(dir, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        } finally {
            runCatching { controller.pause(); controller.stop(); controller.destroy() }
            app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi") fun phone() = run("shell-phone-412x915", 412, 915)
    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi") fun foldOpen() = run("shell-fold7-884x760", 884, 760)
    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi") fun tablet() = run("shell-tablet-1280x800", 1280, 800)
}
