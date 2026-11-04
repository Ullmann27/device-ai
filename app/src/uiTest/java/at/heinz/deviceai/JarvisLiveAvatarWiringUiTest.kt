package at.heinz.deviceai

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.TextureView
import android.widget.FrameLayout
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.avatar.AvatarState
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.ui.ContinuumHomeView
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import at.heinz.deviceai.ui.JarvisSequenceView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/** Functional View/Activity wiring; LEGACY graphics deliberately makes no pixel, native Canvas or GPU claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
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

    private fun withShell(check: (AgentActivity, ContinuumHomeView, ContinuumHologramView) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit().putBoolean("introduced", true).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().putBoolean("jarvis-shell", true).commit()
        // Host Canvas regression; Filament/GPU validation belongs to the device test.
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            measure(root, 884, 760); measure(root, 884, 760)
            check(activity, field(activity, "homeView") as ContinuumHomeView,
                field(activity, "hologramView") as ContinuumHologramView)
        } finally {
            runCatching { controller.pause(); controller.stop(); controller.destroy() }
            JarvisAvatarSlot.register(null)
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
        val pose = (field(visible, "avatarPose") as AvatarPoseReducer).pose(System.currentTimeMillis())
        assertEquals("The live TTS state must reach the visible head", AvatarState.SPEAKING, pose.state)
        assertTrue("Measured audio must open the visible mouth", pose.jawOpen > 0f)
        director.speechEnded("fixture", true)
        val stopped = (field(visible, "avatarPose") as AvatarPoseReducer).pose(System.currentTimeMillis())
        assertEquals(0f, stopped.jawOpen, 0f)
    }

    @Test fun foldSlotContainsTheSeparateHeadAndOrbAreas() = withShell { activity, home, live ->
        val slot = home.avatarSlotRect
        val root = activity.window.decorView
        val homeOrigin = IntArray(2).also { home.getLocationInWindow(it) }
        val rootOrigin = IntArray(2).also { root.getLocationInWindow(it) }
        val railInset = homeOrigin[0] - rootOrigin[0]
        assertTrue("The fixture must exercise the already occupied external rail", railInset > 0)
        // Independently read ref5 bounds use the whole window, before the external rail takes its space.
        assertEquals((root.width * .157f).toInt(), railInset + slot.left)
        assertEquals("The orb must not be squeezed into the HEAD-only 54.7% boundary",
            (root.width * .819f).toInt(), railInset + slot.right)
        assertEquals(slot.left, live.left)
        assertEquals(slot.right, live.right)
        assertTrue(live.width > 0 && live.height > 0)
        val head = field(live, "cockpitHeadBounds") as android.graphics.Rect
        val orb = field(live, "cockpitOrbBounds") as android.graphics.Rect
        assertTrue("The face starts to the right of ref5's user card, not behind its labels",
            railInset + live.left + head.left > (root.width * .306f).toInt())
        assertTrue("Head and orb have separate, positive viewports", head.width() > 0 && head.right < orb.left)
        assertEquals("The orb keeps its full dedicated width", live.width, orb.right)
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
            (field(live, "avatarPose") as AvatarPoseReducer).pose(System.currentTimeMillis()).state)
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
