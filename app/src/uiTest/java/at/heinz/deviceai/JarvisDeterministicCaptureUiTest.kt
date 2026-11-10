package at.heinz.deviceai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ScrollView
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisShellLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Deterministic capture of the real Activity and Android native Canvas on a host.
 * Animation duration scale is zero BEFORE Activity startup: TimeAnimator must
 * never enter Robolectric's idle loop. This is an explicit still-frame fixture,
 * not the normal runtime policy and not a Filament/device acceptance test.
 * Storage is isolated by Robolectric. No fabricated brain, agent or model data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w1536dp-h1024dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisDeterministicCaptureUiTest {
    private fun field(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun durationScale(): Float = ValueAnimator::class.java
        .getDeclaredMethod("getDurationScale").invoke(null) as Float

    private fun setDurationScale(value: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .invoke(null, value)
    }

    private fun capture(label: String, width: Int, height: Int, fontScale: Float = 1f,
                        captureScrollDetails: Boolean = false, captureLiveTab: Boolean = false,
                        captureNavigationCycle: Boolean = false) {
        val app = RuntimeEnvironment.getApplication()
        val resolver = app.contentResolver
        val previousScale = durationScale()
        val previousSetting = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        val previousFont = RuntimeEnvironment.getFontScale()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            RuntimeEnvironment.setFontScale(fontScale)
            setDurationScale(0f)
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            assertFalse("Still capture must disable animation before the first window attachment",
                ValueAnimator.areAnimatorsEnabled())
            app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
                .putBoolean("voice_enabled", false).commit()
            app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
                .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
            app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
                .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
            app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit()
                .clear().putBoolean("jarvis-shell", true).commit()

            println("CAPTURE_PHASE activity.setup; native Canvas, explicitly selected 2D head")
            controller.setup().visible().windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            val home = field(activity, "homeView") as ContinuumHomeView
            val avatar = field(activity, "hologramView") as ContinuumHologramView
            avatar.setAnimationsEnabled(false)
            home.setForegroundActive(false)
            home.jarvisShell.ambientMotionEnabled = false
            fun measure() {
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
                shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
            }
            println("CAPTURE_PHASE layout")
            measure()
            measure()
            assertEquals(View.VISIBLE, root.windowVisibility)
            assertTrue(home.jarvisShellStage)
            assertEquals(View.VISIBLE, home.jarvisShell.visibility)
            assertEquals(0, home.indexOfChild(home.jarvisShell))
            assertTrue(home.avatarSlotShown)
            assertTrue(home.avatarRenderer is JarvisHoloAvatar)
            assertSame("The rendered signal surface is the Activity's actual head", avatar,
                (home.avatarRenderer as JarvisHoloAvatar).hologram)
            assertSame(home, avatar.parent)
            assertEquals(View.VISIBLE, avatar.visibility)
            assertTrue("Actual slot has nonempty geometry", !home.avatarSlotRect.isEmpty)
            assertEquals("The still fixture must not dolly or tilt the hit targets", 1f, home.scaleX, 0f)
            assertEquals(1f, home.scaleY, 0f)
            assertEquals(0f, home.rotationX, 0f)
            assertEquals(0f, home.rotationY, 0f)

            val viewport = field(activity, "dashboardSection") as ScrollView
            val density = home.resources.displayMetrics.density
            val shell = requireNotNull(home.jarvisShell.currentShell())
            val status = requireNotNull(shell[JarvisShellLayout.Slot.STATUS])
            val detailSlots = listOf(JarvisShellLayout.Slot.ROW_AGENTS, JarvisShellLayout.Slot.ROW_TASKS,
                JarvisShellLayout.Slot.ROW_MEMORY, JarvisShellLayout.Slot.ROW_MODEL)
            fun slotBounds() = JSONObject().also { result ->
                for (slot in listOf(JarvisShellLayout.Slot.TILES, JarvisShellLayout.Slot.STATUS) + detailSlots) {
                    val box = shell[slot] ?: continue
                    result.put(slot.name, JSONObject().put("left", box.left.toDouble()).put("top", box.top.toDouble())
                        .put("right", box.right.toDouble()).put("bottom", box.bottom.toDouble()))
                }
            }
            fun contentRect(box: JarvisShellLayout.Box) = Rect((box.left * density).toInt(),
                (box.top * density).toInt(), (box.right * density).toInt(), (box.bottom * density).toInt()).also {
                viewport.offsetDescendantRectToMyCoords(home, it)
            }
            fun render(frameLabel: String, state: String) {
                println("CAPTURE_PHASE draw $state")
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    root.draw(Canvas(bitmap))
                    assertTrue("Native Canvas capture must contain rendered content",
                        (0 until width step 64).flatMap { x -> (0 until height step 64).map { y -> bitmap.getPixel(x, y) } }
                            .toSet().size > 8)
                    System.getProperty("continuum.ui.evidenceDir")?.let { directory ->
                        val dir = File(directory).apply { mkdirs() }
                        val image = File(dir, "$frameLabel.png")
                        image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        fun bounds(view: View) = JSONObject().put("left", view.left).put("top", view.top)
                            .put("right", view.right).put("bottom", view.bottom)
                        val metadata = JSONObject()
                            .put("schema_version", 1)
                            .put("fidelity", "android_activity_host_native_canvas_api28_explicit_2d_head")
                            .put("source_commit", System.getenv("CONTINUUM_CAPTURE_SOURCE_COMMIT") ?: JSONObject.NULL)
                            .put("capture_state", state)
                            .put("external_navigation_visible", (field(activity, "bottomWorkAreaBar") as? View)?.isShown == true)
                            .put("pixel_width", width).put("pixel_height", height)
                            .put("viewport_width_dp", (width / density).toDouble())
                            .put("viewport_height_dp", (height / density).toDouble())
                            .put("density", density.toDouble()).put("font_scale", fontScale.toDouble())
                            .put("has_feature_tiles", shell.hasFeatureTiles)
                            .put("live_panel_expanded", home.findViewWithTag<View>("jarvis-live-panel")?.isShown == true)
                            .put("animation_duration_scale", 0)
                            .put("head_mode", "existing_2d_renderer_explicitly_selected")
                            .put("native_filament_verified", false).put("physical_device_verified", false)
                            .put("data_policy", "isolated empty test storage; real application startup; no invented status data")
                            .put("bounds_coordinate_space", "each View relative to its real parent; no synthetic composition")
                            .put("home_bounds", bounds(home)).put("avatar_bounds", bounds(avatar))
                            .put("shell_bounds", bounds(home.jarvisShell))
                            .put("scroll_viewport_bounds", bounds(viewport))
                            .put("scroll_x_px", viewport.scrollX).put("scroll_y_px", viewport.scrollY)
                            .put("scroll_y_dp", (viewport.scrollY / density).toDouble())
                            .put("scroll_content_height_px", viewport.getChildAt(0).height)
                            .put("slot_bounds_coordinate_space", "actual shell-local dp")
                            .put("slot_bounds_dp", slotBounds())
                            .put("png_sha256", MessageDigest.getInstance("SHA-256").digest(image.readBytes())
                                .joinToString("") { "%02x".format(it) })
                        File(dir, "$frameLabel.json").writeText(metadata.toString(2))
                        println("CAPTURE_IMAGE ${image.absolutePath}")
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            render(label, "home")
            if (captureNavigationCycle) {
                val bar = field(activity, "bottomWorkAreaBar") as View
                val pane = (bar.parent as android.view.ViewGroup).getChildAt(0)
                fun margin() = (pane.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin
                assertEquals("Cockpit dock owns Home navigation", View.GONE, bar.visibility)
                assertEquals("Home reclaims the external reservation", 0, margin())
                assertEquals(height.toFloat(), home.jarvisShell.viewportHeightDp, .01f)
                JarvisHomeTestRoutes.touch(root, JarvisHomeTestRoutes.destination(root, "chat"))
                measure()
                assertEquals(ContinuumWorkArea.CHAT, field(activity, "activeWorkArea"))
                assertEquals("Chat retains navigation", View.VISIBLE, bar.visibility)
                assertTrue("Chat reserves its visible navigation", margin() > 0)
                JarvisHomeTestRoutes.touch(root, bar.findViewWithTag(ContinuumWorkArea.START))
                measure()
                assertEquals(View.GONE, bar.visibility)
                assertEquals(0, margin())
                assertSame("Navigation retains the avatar", avatar, field(activity, "hologramView"))
                render("$label-return", "home_after_chat")
            }
            if (captureLiveTab) {
                val tab = requireNotNull(home.findViewWithTag<View>("jarvis-live-tab:system"))
                JarvisHomeTestRoutes.touch(root, tab)
                shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
                render("$label-live-system", "live_system")
                val panel = requireNotNull(home.findViewWithTag<View>("jarvis-live-panel"))
                assertTrue("The capture shows the real expanded drawer", panel.isShown && panel.alpha == 1f)
                JarvisHomeTestRoutes.touch(root, tab)
                shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
            }
            if (captureScrollDetails && shell.hasFeatureTiles) {
                assertEquals("The fixed Home rail is removed", null, shell[JarvisShellLayout.Slot.NAV_RAIL])
                assertTrue("Cards precede their status strip",
                    requireNotNull(shell[JarvisShellLayout.Slot.TILES]).bottom <= status.top)
                detailSlots.forEach { slot ->
                    assertTrue("$slot follows the complete home", requireNotNull(shell[slot]).top > status.bottom)
                }
                val firstDetail = detailSlots.map { requireNotNull(shell[it]) }.minBy { it.top }
                viewport.scrollTo(0, (contentRect(firstDetail).top - 12f * density).toInt().coerceAtLeast(0))
                shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
                assertTrue("Real dashboard scrolling must reveal the diagnostics", viewport.scrollY > 0)
                detailSlots.forEach { slot ->
                    val bounds = contentRect(requireNotNull(shell[slot]))
                    assertTrue("$slot is visible in the scrolled dashboard",
                        bounds.top >= viewport.scrollY && bounds.bottom <= viewport.scrollY + viewport.height)
                }
                render("$label-scrolled-diagnostics", "scrolled_diagnostics")
            }
        } finally {
            println("CAPTURE_PHASE cleanup")
            runCatching { controller.pause() }
            runCatching { controller.stop() }
            runCatching { controller.destroy() }
            Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousSetting)
            setDurationScale(previousScale)
            RuntimeEnvironment.setFontScale(previousFont)
        }
        assertEquals("Activity cleanup unregisters its renderer", null, JarvisAvatarSlot.renderer)
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun compactDockOwnsNavigation() = capture("activity-single-dock-412x915", 412, 915,
        captureNavigationCycle = true)

    @Test fun referenceRasterActivityCanvas() = capture("activity-raster-1536x1024", 1536, 1024)
    @Test @Config(qualifiers = "w390dp-h844dp-mdpi")
    fun compact390() = capture("activity-compact-390x844", 390, 844)
    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun compact412() = capture("activity-compact-412x915", 412, 915)
    @Test @Config(qualifiers = "w780dp-h360dp-land-mdpi")
    fun mediumShortLandscape() = capture("activity-medium-780x360", 780, 360)
    @Test @Config(qualifiers = "w700dp-h720dp-mdpi")
    fun mediumMultiwindow() = capture("activity-medium-700x720", 700, 720)
    @Test @Config(qualifiers = "w832dp-h748dp-land-mdpi")
    fun mediumNearSquare() = capture("activity-medium-832x748", 832, 748)
    @Test @Config(qualifiers = "w692dp-h768dp-port-xhdpi")
    fun ownerFoldAspect() = capture("activity-fold-1384x1536", 1384, 1536,
        captureScrollDetails = true, captureLiveTab = true)
    @Test @Config(qualifiers = "w864dp-h1536dp-port-mdpi")
    fun referenceTallAspect() = capture("activity-reference-864x1536", 864, 1536)
    @Test @Config(qualifiers = "w960dp-h640dp-land-mdpi")
    fun expandedCanonical() = capture("activity-expanded-960x640", 960, 640)
    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun expandedTablet() = capture("activity-expanded-1280x800", 1280, 800)
    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun compactFont150() = capture("activity-compact-412x915-font150", 412, 915, 1.5f)
    @Test @Config(qualifiers = "w412dp-h915dp-mdpi")
    fun compactFont200() = capture("activity-compact-412x915-font200", 412, 915, 2f)
    @Test @Config(qualifiers = "w832dp-h748dp-land-mdpi")
    fun mediumFont150() = capture("activity-medium-832x748-font150", 832, 748, 1.5f)
    @Test @Config(qualifiers = "w832dp-h748dp-land-mdpi")
    fun mediumFont200() = capture("activity-medium-832x748-font200", 832, 748, 2f)
    @Test @Config(qualifiers = "w960dp-h640dp-land-mdpi")
    fun expandedFont150() = capture("activity-expanded-960x640-font150", 960, 640, 1.5f)
    @Test @Config(qualifiers = "w960dp-h640dp-land-mdpi")
    fun expandedFont200() = capture("activity-expanded-960x640-font200", 960, 640, 2f)
}
