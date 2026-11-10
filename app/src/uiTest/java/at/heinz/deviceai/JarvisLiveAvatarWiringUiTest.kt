package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.JarvisCockpitGrid
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import at.heinz.deviceai.ui.JarvisSequenceView
import at.heinz.deviceai.ui.JarvisMenuNavigator
import at.heinz.deviceai.ui.JarvisWorkWindow
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
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowDisplayManager
import java.util.concurrent.TimeUnit

/** Functional View/Activity wiring; LEGACY graphics deliberately makes no pixel, native Canvas or GPU claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisLiveAvatarWiringUiTest {
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    private fun measure(root: View, width: Int, height: Int) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
    }

    private fun resizeWindow(activity: AgentActivity, width: Int, height: Int) {
        ShadowDisplayManager.changeDisplay(0, "w${width}dp-h${height}dp-mdpi")
        activity.window.setLayout(width, height)
        // A width-class change may replace uiWindowRoot. Re-read the actual root after each posted reflow.
        repeat(3) {
            measure(activity.window.decorView, width, height)
            measure(field(activity, "uiWindowRoot") as View, width, height)
        }
    }

    private fun visibleRails(activity: AgentActivity, home: ContinuumHomeView): List<View> =
        listOfNotNull(field(home, "shellRailScroll") as? View, field(activity, "conversationRail") as? View)
            .filter { it.isShown }

    private fun withShell(width: Int = 884, height: Int = 760,
        check: (AgentActivity, ContinuumHomeView, ContinuumHologramView) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        val animatorScale = Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit().putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().putBoolean("jarvis-shell", true).commit()
        // Host Canvas regression; Filament/GPU validation belongs to the device test.
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            measure(root, width, height); measure(root, width, height)
            check(activity, field(activity, "homeView") as ContinuumHomeView,
                field(activity, "hologramView") as ContinuumHologramView)
        } finally {
            runCatching { controller.pause(); controller.stop(); controller.destroy() }
            JarvisAvatarSlot.register(null)
            Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, animatorScale)
            app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun shellUsesTheOneSurfaceReceivingActivitySignals() = withShell { activity, home, live ->
        val docked = home.avatarRenderer as JarvisHoloAvatar
        assertSame("Docking must not replace the surface that receives speech, state and lifecycle", live, docked.hologram)
        assertSame(home, live.parent)
        assertTrue("The signal-bearing surface must actually be visible", live.isShown)
        assertEquals(1, descendants(activity.window.decorView).count { it is ContinuumHologramView })
    }

    @Test fun actualDirectorSpeechReachesTheVisibleHead() = withShell { activity, home, _ ->
        val director = field(activity, "avatarDirector") as AvatarDirector
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        director.speechStarted("fixture", "Hallo")
        director.audioLevel("fixture", .8f)
        shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.MILLISECONDS)
        val visible = (home.avatarRenderer as JarvisHoloAvatar).hologram!!
        val pose = (field(visible, "avatarPose") as AvatarPoseReducer).pose(android.os.SystemClock.elapsedRealtime())
        assertEquals("The live TTS state must reach the visible head", AvatarState.SPEAKING, pose.state)
        assertTrue("Measured audio must open the visible mouth", pose.jawOpen > 0f)
        director.speechEnded("fixture", true)
        val stopped = (field(visible, "avatarPose") as AvatarPoseReducer).pose(android.os.SystemClock.elapsedRealtime())
        assertEquals(0f, stopped.jawOpen, 0f)
    }

    @Test fun foldSlotContainsTheSeparateHeadAndOrbAreas() = withShell { activity, home, live ->
        val slot = home.avatarSlotRect
        val root = activity.window.decorView
        val homeOrigin = IntArray(2).also { home.getLocationInWindow(it) }
        val rootOrigin = IntArray(2).also { root.getLocationInWindow(it) }
        val railInset = homeOrigin[0] - rootOrigin[0]
        assertEquals("Dashboard owns its rail; Activity must not reserve a second rail", 0, railInset)
        assertEquals("The dashboard must fill the available pane without an inset-scrollbar gutter", root.width, home.width)
        // Check against the current ten-card reference grid, not the retired ref5 offsets.
        val grid = JarvisCockpitGrid.create(home.width.toFloat(), 760f)
        val headStage = grid[JarvisCockpitGrid.Area.HEAD]
        val orbStage = grid[JarvisCockpitGrid.Area.ORB]
        assertEquals(headStage.left.toInt(), slot.left)
        assertEquals("The orb keeps its own dedicated right boundary", orbStage.right.toInt(), slot.right)
        assertEquals(slot.left, live.left)
        assertEquals(slot.right, live.right)
        assertTrue(live.width > 0 && live.height > 0)
        val head = field(live, "cockpitHeadBounds") as android.graphics.Rect
        val orb = field(live, "cockpitOrbBounds") as android.graphics.Rect
        assertEquals("No obsolete owner-card offset may shrink the new head viewport", 0, head.left)
        assertEquals(headStage.right.toInt() - slot.left, head.right)
        assertEquals(orbStage.left.toInt() - slot.left, orb.left)
        assertTrue("Head and orb have separate, positive viewports", head.width() > 0 && head.right < orb.left)
        assertEquals("The orb keeps its full dedicated width", live.width, orb.right)
        assertEquals(JarvisCockpitGrid.HEAD_ANCHOR_X, field(live, "headAnchorX") as Float, .001f)
        assertEquals(JarvisCockpitGrid.HEAD_CLOSE_UP, field(live, "headCloseUp") as Float, .001f)
    }

    @Test fun resizingTheSlotPreservesTheSurfaceAndCurrentSpeech() = withShell { activity, home, live ->
        val director = field(activity, "avatarDirector") as AvatarDirector
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        director.speechStarted("resize", "Hallo")
        director.audioLevel("resize", .7f)
        val docked = home.avatarRenderer as JarvisHoloAvatar
        val before = docked.hologram
        home.configure(760f, true, windowHeightDp = 884f)
        measure(home, 760, 884); measure(home, 760, 884)
        assertSame("A bounds change must not create a new renderer", before, docked.hologram)
        assertSame(live, docked.hologram)
        assertEquals(AvatarState.SPEAKING,
            (field(live, "avatarPose") as AvatarPoseReducer).pose(android.os.SystemClock.elapsedRealtime()).state)
    }

    @Test fun heightOnlyWindowResizeUpdatesTheViewportWithoutReplacingLiveOwners() = withShell { activity, home, live ->
        val director = field(activity, "avatarDirector") as AvatarDirector
        val composer = field(activity, "input") as EditText
        composer.setText("Unfertiger Entwurf bleibt erhalten")
        composer.setSelection(4, 12)
        director.onRuntime(AvatarRuntimeSignals(speaking = true))
        director.speechStarted("height-only", "Hallo")
        assertEquals(760f, home.jarvisShell.viewportHeightDp, .01f)

        resizeWindow(activity, 884, 420)

        assertSame("Height-only layout must keep the existing Home", home, field(activity, "homeView"))
        assertSame("The signal-bearing hologram must retain its identity", live, field(activity, "hologramView"))
        assertSame("Height-only resize must not replace the editor", composer, field(activity, "input"))
        assertSame("An active speech director must retain ownership", director, field(activity, "avatarDirector"))
        assertSame(live, (home.avatarRenderer as JarvisHoloAvatar).hologram)
        assertEquals("The stored viewport must follow the real window height", 420f,
            (field(home, "configuredHeight") as Number).toFloat(), .01f)
        assertEquals(420f, home.jarvisShell.viewportHeightDp, .01f)
        assertEquals("Unfertiger Entwurf bleibt erhalten", composer.text.toString())
        assertEquals(4, composer.selectionStart)
        assertEquals(12, composer.selectionEnd)
        assertEquals(AvatarState.SPEAKING,
            (field(live, "avatarPose") as AvatarPoseReducer).pose(android.os.SystemClock.elapsedRealtime()).state)
        director.speechEnded("height-only", true)
    }

    @Test
    @Config(sdk = [28], qualifiers = "w780dp-h360dp-land-mdpi")
    fun shortMediumWindowOwnsOneSidebarAndNoPhoneBottomNavigation() = withShell(780, 360) { activity, home, _ ->
        assertEquals(780, home.width)
        assertEquals(360f, home.jarvisShell.viewportHeightDp, .01f)
        assertEquals("The reference home has no duplicated fixed sidebar", 0, visibleRails(activity, home).size)
        assertFalse((field(home, "shellRailScroll") as View).isShown)
        assertFalse("Phone navigation must not consume medium-window height",
            (field(activity, "bottomWorkAreaBar") as? View)?.isShown == true)
    }

    @Test
    @Config(sdk = [28], qualifiers = "w592dp-h760dp-mdpi")
    fun crossingThe600DpBoundaryReassignsNavigationEvenForASmallWidthDelta() = withShell(592, 760) { activity, home, live ->
        val director = field(activity, "avatarDirector") as AvatarDirector
        assertFalse("Compact Home uses its Cinema dock", (field(activity, "bottomWorkAreaBar") as View).isShown)
        assertTrue("Compact starts without a desktop sidebar", visibleRails(activity, home).isEmpty())

        resizeWindow(activity, 606, 760)

        val resized = field(activity, "homeView") as ContinuumHomeView
        assertEquals(606, resized.width)
        assertEquals("Crossing 600 dp must reflow without reinstating the removed home sidebar",
            0, visibleRails(activity, resized).size)
        assertFalse((field(resized, "shellRailScroll") as View).isShown)
        assertFalse((field(activity, "bottomWorkAreaBar") as? View)?.isShown == true)
        assertSame("Reflow retains the real surface", live, field(activity, "hologramView"))
        assertSame(director, field(activity, "avatarDirector"))
        assertSame(live, (resized.avatarRenderer as JarvisHoloAvatar).hologram)
    }

    @Test fun semanticMenuDestinationsOpenTheirNamedScreensWithoutStartingATurn() = withShell { activity, _, _ ->
        val navigator = (field(activity, "menuNavigator\$delegate") as Lazy<*>).value as JarvisMenuNavigator
        val root = activity.window.decorView
        val width = root.width
        val height = root.height
        val frames = CockpitHostFramePump { activity.window.decorView }
        val initialRequest = field(activity, "activeLocalRequest")
        assertEquals(false, initialRequest)
        val initialTurn = field(activity, "activeChiefTurn")
        fun windows() = descendants(root).filterIsInstance<JarvisWorkWindow>()
        fun settle() {
            repeat(2) {
                measure(root, width, height)
                frames.drawRequestedFrame()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
        fun unchangedTurn(target: String) {
            assertEquals("$target must not start a local request", initialRequest, field(activity, "activeLocalRequest"))
            assertSame("$target must not start a chief turn", initialTurn, field(activity, "activeChiefTurn"))
        }
        try {
            listOf("logic" to "Logik und Planung", "tasks" to "Entwicklungsaufgaben", "web" to "Web und Quellen").forEach { (target, title) ->
                assertTrue("The previous destination must be removed", windows().isEmpty())
                val previousDialog = ShadowAlertDialog.getLatestAlertDialog()
                navigator.navigate(target)
                settle()
                unchangedTurn(target)
                if (target == "tasks") {
                    assertTrue("Tasks retain their dedicated dialog", windows().isEmpty())
                    val dialog = ShadowAlertDialog.getLatestAlertDialog()
                    assertNotNull("Tasks must open their real dialog", dialog)
                    assertNotSame("Tasks must open a new dialog", previousDialog, dialog)
                    assertTrue(dialog.isShowing)
                    val texts = descendants(dialog.window!!.decorView).filterIsInstance<TextView>()
                        .filter { it.isShown }.map { it.text.toString() }
                    assertTrue("Tasks must show their own title: $texts", title in texts)
                    val close = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    assertEquals("Schließen", close.text.toString())
                    assertTrue(close.isShown && close.isEnabled)
                    assertTrue("The dialog close button must be wired", close.performClick())
                    settle()
                    assertFalse("The task dialog must close", dialog.isShowing)
                } else {
                    assertSame("$target must use the cockpit window", previousDialog, ShadowAlertDialog.getLatestAlertDialog())
                    val shown = windows()
                    assertEquals("$target must open exactly one work window", 1, shown.size)
                    val window = shown.single()
                    assertTrue(window.isAttachedToWindow && window.isShown && window.isShowing)
                    val panel = window.findViewWithTag<View>("jarvis-work-window:panel")
                    assertTrue("$target must have a laid-out panel", panel.isShown && panel.width > 0 && panel.height > 0)
                    assertEquals("$target must finish opening visibly", 1f, panel.alpha, .001f)
                    val texts = descendants(panel).filterIsInstance<TextView>()
                        .filter { it.isShown }.map { it.text.toString() }
                    assertTrue("$target must show its own title: $texts", title in texts)
                    val close = window.findViewWithTag<View>("jarvis-work-window:close")
                    assertTrue(close.isShown && close.isEnabled)
                    assertTrue("$target close button must be wired", close.performClick())
                    settle()
                    assertFalse("$target must finish closing", window.isShowing)
                    assertFalse("$target must detach its window", window.isAttachedToWindow)
                    assertTrue("$target must remove its window", windows().isEmpty())
                }
                unchangedTurn("Closing $target")
            }
        } finally {
            frames.close()
        }
    }

    @Test fun dockingAndDetachingDoNotRewriteTheSaved3dChoice() {
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val prefs = activity.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("native-head", true).putBoolean("native-head-3d", true).commit()
        val renderer = JarvisHoloAvatar()
        val host = FrameLayout(activity)
        renderer.attach(host, android.graphics.Rect(0, 0, 600, 500))
        assertTrue("Docking is layout, not a request to switch off 3D", prefs.getBoolean("native-head-3d", false))
        assertTrue(renderer.hologram!!.isNative3dEnabled())
        renderer.detach()
        assertTrue("Detach must retain the owner's appearance preference", prefs.getBoolean("native-head-3d", false))
    }

    @Test fun separateRegionsPositionTheNativeViewportAndMemoryTargetOnTheSameGrid() {
        val controller = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val live = ContinuumHologramView(controller.get())
        try {
            live.setNativeHeadEnabled(true)
            live.setGeometry(ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT)
            // Exercise real TextureView layout without starting Android Filament JNI on a host.
            val texture = TextureView(controller.get())
            live.addView(texture, FrameLayout.LayoutParams(-1, -1))
            ContinuumHologramView::class.java.getDeclaredField("nativeTexture")
                .apply { isAccessible = true }.set(live, texture)
            val sequence = JarvisSequenceView(controller.get())
            live.setCockpitSequence(sequence)
            live.setCockpitRegions(android.graphics.Rect(10, 20, 310, 420), android.graphics.Rect(330, 20, 570, 420))
            measure(live, 600, 450)
            assertEquals(listOf(10, 20, 310, 420), listOf(texture.left, texture.top, texture.right, texture.bottom))
            assertEquals(.75f, sequence.effects.orbTargetX, .0001f)
            assertEquals(188f / 450f, sequence.effects.orbTargetY, .0001f)
            assertEquals(600, sequence.width)
            assertEquals(450, sequence.height)
        } finally { live.setNativeHeadEnabled(false); controller.pause().stop().destroy() }
    }
}
