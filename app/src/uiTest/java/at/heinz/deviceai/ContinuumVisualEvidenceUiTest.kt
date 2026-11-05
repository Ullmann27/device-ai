package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Visual evidence of the real AgentActivity cockpit on the host (Robolectric NATIVE canvas). The live 3D head
 * cannot run here: the 2D head preview draws in the head stage instead of Filament. TTS callbacks and the
 * owner's position are scripted through the real AvatarDirector; nothing is measured on a device.
 * Writes PNGs only with -PcontinuumUiEvidenceDir=…; otherwise it only checks that the scenes render.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumVisualEvidenceUiTest {
    @Before fun configureVirtualVsyncClock() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) { val step = minOf(16L, left); shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS); left -= step }
    }

    private fun field(a: AgentActivity, name: String): Any? =
        AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun measure(root: View, w: Int, h: Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
    }

    private fun capture(view: View, label: String, w: Int, h: Int) {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            assertTrue("$label must not be blank", (0 until 200).any { bitmap.getPixel(w * it / 200, h / 3) != bitmap.getPixel(0, 0) })
            System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
                File(dir).apply { mkdirs() }.resolve("$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            println("EVIDENCE_CAPTURE $label ${w}x$h HOST_ROBOLECTRIC_NATIVE_CANVAS_2D_HEAD_NOT_FILAMENT")
        } finally { bitmap.recycle() }
    }

    /** Real DOWN (and optional UP) through the decor at the control centre; false if it is off screen. */
    private fun touch(root: View, target: View, release: Boolean): Boolean {
        val r = Rect()
        if (!target.getGlobalVisibleRect(r) || r.width() < target.width - 1 || r.height() < target.height - 1) return false
        val at = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(at, at, MotionEvent.ACTION_DOWN, r.exactCenterX(), r.exactCenterY(), 0)
        try { root.dispatchTouchEvent(down) } finally { down.recycle() }
        if (release) {
            val up = MotionEvent.obtain(at, at + 16, MotionEvent.ACTION_UP, r.exactCenterX(), r.exactCenterY(), 0)
            try { root.dispatchTouchEvent(up) } finally { up.recycle() }
        }
        return true
    }

    private fun scenes(tag: String, w: Int, h: Int, font: Float) {
        val previousFont = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(font)
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            advance(32)
            controller.windowFocusChanged(true)
            val a = controller.get()
            val root = a.window.decorView
            val head = field(a, "hologramView") as ContinuumHologramView
            val home = field(a, "homeView") as ContinuumHomeView
            val director = field(a, "avatarDirector") as AvatarDirector
            head.setAnimationsEnabled(true); head.setForegroundActive(true); home.setForegroundActive(true)
            measure(root, w, h); advance(1_200); measure(root, w, h)
            capture(root, "$tag-1-start", w, h)

            director.onRuntime(AvatarRuntimeSignals(listening = true)); head.setState(ContinuumHologramView.State.LISTENING)
            advance(900); capture(root, "$tag-2-listening", w, h)

            director.onRuntime(AvatarRuntimeSignals(busy = true)); head.setState(ContinuumHologramView.State.THINKING)
            advance(900); capture(root, "$tag-3-thinking", w, h)

            // Scripted TTS callbacks through the real Director (word ranges; orb level as the TTS signal would set it).
            director.onRuntime(AvatarRuntimeSignals(speaking = true)); head.setState(ContinuumHologramView.State.SPEAKING)
            val text = "Guten Abend Heinz, das Cockpit ist bereit."
            director.speechStarted("evidence-1", text)
            var cursor = 0
            for (word in Regex("\\S+").findAll(text).take(3)) {
                director.wordRange("evidence-1", word.range.first, word.range.last + 1)
                repeat(10) { director.audioLevel("evidence-1", 0f, measuredPcmAvailable = false); head.setAudioLevel(.55f + .3f * (it % 3) / 2f); advance(33) }
                cursor = word.range.last
            }
            assertTrue(cursor > 0)
            capture(root, "$tag-4-speaking", w, h)
            director.speechEnded("evidence-1", interrupted = false)
            director.onRuntime(AvatarRuntimeSignals()); head.setState(ContinuumHologramView.State.READY)
            advance(900)

            val memory = descendants(root).firstOrNull { it.tag == "jarvis-tile:memory" && it.isShown }
            if (memory != null && touch(root, memory, release = false)) {
                advance(120)
                capture(root, "$tag-5-tile-pressed", w, h)
                val at = SystemClock.uptimeMillis()
                val cancel = MotionEvent.obtain(at, at, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                try { root.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                advance(300)
            }
            val system = descendants(root).firstOrNull { (it.tag == "jarvis-tile:device" || it.tag == "jarvis-tile:system") && it.isShown }
            if (system != null && touch(root, system, release = true)) {
                advance(600)
                val dialog: AlertDialog? = ShadowAlertDialog.getLatestAlertDialog()
                if (dialog != null && dialog.isShowing) {
                    val decor = requireNotNull(dialog.window).decorView
                    capture(root, "$tag-6-window-behind", w, h)
                    capture(decor, "$tag-6-window-systemstatus", decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1))
                    dialog.dismiss(); advance(300)
                }
            }
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            runCatching { controller.close() }
            RuntimeEnvironment.setFontScale(previousFont)
        }
    }

    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi")
    fun fold7Inner() = scenes("fold7-inner-884x760", 884, 760, 1f)

    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi")
    fun fold7InnerLargerFont() = scenes("fold7-inner-884x760-font130", 884, 760, 1.3f)

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun fold7CoverAndPhone() = scenes("fold7-cover-phone-412x915", 412, 915, 1f)

    @Test @Config(qualifiers = "w1536dp-h1024dp-land-mdpi")
    fun referenceTablet() = scenes("reference-1536x1024", 1536, 1024, 1f)
}
