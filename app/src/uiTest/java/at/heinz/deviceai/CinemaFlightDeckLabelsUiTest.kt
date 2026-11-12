package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.cockpit.CockpitAnimationTriggers
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.HollywoodFuiStageOverlay
import at.heinz.deviceai.ui.JarvisCinematicSettings
import at.heinz.deviceai.ui.JarvisShellLayout
import at.heinz.deviceai.ui.JarvisWorkWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Code54 cinema B2 on the real AgentActivity host (same scripted work scene as [CinemaNativeSequenceUiTest]):
 * the flight contact never lies over the KPI tiles of the status band, and EXP_06 switches the reading thread live
 * through JarvisWorkWindowHost. HOST evidence only: Robolectric API 28 native software canvas, 2D fallback head –
 * no device, no GPU, no Filament, no Fold7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CinemaFlightDeckLabelsUiTest {
    private val width = 884
    private val height = 760

    private fun field(name: String) = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }

    private class Scene(val activity: AgentActivity, val home: ContinuumHomeView, val advance: (Long) -> Unit)

    private fun scene(prepare: (Context) -> Unit = {}, block: (Scene) -> Unit) {
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
        prepare(app)
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        var pump: CockpitHostFramePump? = null
        try {
            controller.setup().visible()
            shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
            controller.windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            val home = field("homeView").get(activity) as ContinuumHomeView
            val head = field("hologramView").get(activity) as ContinuumHologramView
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
            val advance = { millis: Long ->
                var remaining = millis
                while (remaining > 0) {
                    val step = minOf(16L, remaining)
                    shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
                    measure()
                    framePump.drawRequestedFrame()
                    remaining -= step
                }
            }
            measure()
            advance(1800)
            block(Scene(activity, home, advance))
        } finally {
            pump?.close()
            controller.close()
        }
    }

    private fun start(activity: AgentActivity, turn: String) {
        val triggers = field("cockpitTriggers").get(activity) as CockpitAnimationTriggers
        field("busy").set(activity, true)
        field("activeLocalRequest").set(activity, true)
        triggers.commandAcknowledged(turn, "Prüfauftrag: Arbeitsszene darstellen")
        AgentActivity::class.java.getDeclaredMethod("presentLocalReasoningWorkScene",
            String::class.java, String::class.java).apply { isAccessible = true }
            .invoke(activity, turn, "Prüfauftrag: Arbeitsszene darstellen")
    }

    private fun findView(view: View, match: (View) -> Boolean): View? {
        if (match(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findView(view.getChildAt(i), match)?.let { return it }
        return null
    }

    private inline fun <reified T : View> find(view: View, noinline match: (View) -> Boolean = { it is T }): T? =
        findView(view, match) as T?

    private fun content(activity: AgentActivity) = activity.findViewById<ViewGroup>(android.R.id.content)

    /** One layer drawn alone into a bitmap of its own size; same coordinates as the content container. */
    private fun layer(view: View): IntArray {
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return try {
            view.draw(Canvas(image))
            IntArray(view.width * view.height).also { image.getPixels(it, 0, view.width, 0, 0, view.width, view.height) }
        } finally { image.recycle() }
    }

    /** Slot box of the live shell in content-container pixels. */
    private fun slotRect(scene: Scene, slot: JarvisShellLayout.Slot): RectF {
        val shell = scene.home.jarvisShell
        val box = requireNotNull(shell.currentShell()?.get(slot)) { "no $slot in the shell" }
        val density = scene.activity.resources.displayMetrics.density
        val shellAt = IntArray(2).also(shell::getLocationInWindow)
        val contentAt = IntArray(2).also(content(scene.activity)::getLocationInWindow)
        val dx = (shellAt[0] - contentAt[0]).toFloat()
        val dy = (shellAt[1] - contentAt[1]).toFloat()
        return RectF(dx + box.left * density, dy + box.top * density, dx + box.right * density, dy + box.bottom * density)
    }

    /** Accent deck light (the flight contact's line): saturated cyan, as in HollywoodFuiConnectionsUiTest. */
    private fun light(c: Int) = Color.alpha(c) > 60 && Color.blue(c) > 150 && Color.red(c) < 120

    @Test fun flightContactNeverLightsTheKpiTilesWhileTheWindowFlies() = scene { scene ->
        start(scene.activity, "cinema-b2-kpi")
        val window = requireNotNull(find<JarvisWorkWindow>(content(scene.activity)) { it.tag == "jarvis-work-window" })
        val panel = requireNotNull(window.findViewWithTag<View>("jarvis-work-window:panel"))
        val status = slotRect(scene, JarvisShellLayout.Slot.STATUS)
        assertTrue("the status band with its KPI tiles is on screen: $status",
            status.width() > 400f && status.top > 400f && status.bottom <= height + 1f)
        val contact = JarvisWorkWindow::class.java.getDeclaredField("flightContact").apply { isAccessible = true }
        var elapsed = 0L
        scene.advance(280L)
        elapsed = 280L
        var peak = 0f
        val lit = mutableListOf<String>()
        while (elapsed <= 650L) {
            peak = maxOf(peak, contact.getFloat(window))
            val pixels = layer(window)
            // The glass itself (visible from ≈640 ms) may carry accent light; only the deck around it is checked.
            val glass = RectF(android.graphics.Rect().also(panel::getHitRect))
            var count = 0
            for (y in status.top.toInt() until minOf(status.bottom.toInt(), window.height))
                for (x in status.left.toInt() until minOf(status.right.toInt(), window.width)) {
                    if (panel.alpha > 0f && glass.contains(x.toFloat(), y.toFloat())) continue
                    if (light(pixels[y * window.width + x])) count++
                }
            if (count > 0) lit += "work-${elapsed}ms: $count"
            scene.advance(16L)
            elapsed += 16L
        }
        assertTrue("the flight contact really runs in this window (peak $peak)", peak > .5f)
        assertTrue("no deck light over the KPI tiles during the flight: $lit", lit.isEmpty())
        // The host hands the window exactly the status band (and the other labelled deck rows) as deck labels.
        @Suppress("UNCHECKED_CAST")
        val labels = JarvisWorkWindow::class.java.getDeclaredField("deckLabels").apply { isAccessible = true }
            .get(window) as List<RectF>
        assertTrue("status band is a deck label: $labels", labels.any {
            kotlin.math.abs(it.left - status.left) < 1f && kotlin.math.abs(it.top - status.top) < 1f &&
                kotlin.math.abs(it.right - status.right) < 1f && kotlin.math.abs(it.bottom - status.bottom) < 1f })
        val avatar = slotRect(scene, JarvisShellLayout.Slot.AVATAR)
        assertFalse("the avatar stage is no label", labels.any { it == avatar })
    }

    @Test fun exp06SwitchesTheReadingThreadLiveThroughTheHostAndDefaultsOn() = scene { scene ->
        start(scene.activity, "cinema-b2-exp06")
        scene.advance(2100L)
        val overlay = requireNotNull(find<HollywoodFuiStageOverlay>(content(scene.activity)))
        val context = scene.activity
        assertTrue("owner never touched the switch: EXP_06 is on", JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_06_FACE_LIGHT))
        assertTrue("host maps EXP_06 ON to the reading thread", overlay.attentionLinkEnabled)
        val on = layer(overlay)

        JarvisCinematicSettings.setExperiment(context, JarvisCinematicSettings.Experiment.EXP_06_FACE_LIGHT, false)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(JarvisCinematicSettings.experimentEnabled(context, JarvisCinematicSettings.Experiment.EXP_06_FACE_LIGHT))
        assertFalse("host maps EXP_06 OFF live (preference listener)", overlay.attentionLinkEnabled)
        assertTrue("EXP_11 is a different switch and stays as it was", overlay.orbLinkEnabled)
        val off = layer(overlay)
        val removed = on.indices.count { on[it] != off[it] }
        assertTrue("OFF removes the drawn reading thread ($removed px)", removed > 30)

        JarvisCinematicSettings.setExperiment(context, JarvisCinematicSettings.Experiment.EXP_06_FACE_LIGHT, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("host maps EXP_06 ON again live", overlay.attentionLinkEnabled)
        val again = layer(overlay)
        assertEquals("ON restores exactly the same picture", 0, on.indices.count { on[it] != again[it] })
    }

    @Test fun exp06StoredOffByTheOwnerStaysOffAfterRestart() = scene(prepare = { app ->
        // A deliberate earlier owner choice under the unchanged key; the Code52 migration already happened.
        JarvisCinematicSettings.activateCinema52ByDefault(app)
        JarvisCinematicSettings.setExperiment(app, JarvisCinematicSettings.Experiment.EXP_06_FACE_LIGHT, false)
        JarvisCinematicSettings.preferences(app).edit().commit()
    }) { scene ->
        start(scene.activity, "cinema-b2-exp06-off")
        scene.advance(400L)
        val overlay = requireNotNull(find<HollywoodFuiStageOverlay>(content(scene.activity)))
        assertNotNull(overlay)
        assertFalse(JarvisCinematicSettings.experimentEnabled(scene.activity,
            JarvisCinematicSettings.Experiment.EXP_06_FACE_LIGHT))
        assertFalse("the stored OFF choice reaches the host at open", overlay.attentionLinkEnabled)
    }
}
