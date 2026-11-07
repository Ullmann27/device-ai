package at.heinz.deviceai.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Looper
import android.view.View
import at.heinz.deviceai.AgentActivity
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.BOOT_TURN
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.Cue
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.cockpit.TimelineStep
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AvatarEntranceUiTest {
    private fun configure() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
    }

    private fun advance(milliseconds: Long) {
        var left = milliseconds
        while (left > 0L) {
            val step = minOf(16L, left)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            left -= step
        }
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 884, 760)
    }

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun frame(live: ContinuumHologramView): Bitmap {
        live.viewTreeObserver.dispatchOnPreDraw()
        return Bitmap.createBitmap(live.width, live.height, Bitmap.Config.ARGB_8888).also { live.draw(Canvas(it)) }
    }

    private fun crop(frame: Bitmap, bounds: RectF) = Bitmap.createBitmap(frame, bounds.left.toInt(),
        bounds.top.toInt(), bounds.width().toInt(), bounds.height().toInt())

    private fun capture(bitmap: Bitmap, name: String) {
        System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
            java.io.File(dir).apply { mkdirs() }.resolve("$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }

    private fun boot(home: ContinuumHomeView) {
        CockpitAnimationPresenter(home).render(CockpitAnimationEvent.BootStarted(BOOT_TURN, 0L, "Bereit"))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun withCockpit(check: (ContinuumHomeView, ContinuumHologramView) -> Unit) {
        configure()
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val live = ContinuumHologramView(activity)
        val home = ContinuumHomeView(activity, live) { _, _, _ -> }
        home.configure(884f, true, windowHeightDp = 760f)
        home.setJarvisShellStage(true)
        JarvisAvatarSlot.register(JarvisHoloAvatar(live))
        activity.setContentView(home)
        try {
            repeat(3) { measure(home); advance(16L) }
            controller.windowFocusChanged(true)
            live.dispatchWindowFocusChanged(true)
            home.setForegroundActive(true)
            live.setForegroundActive(true)
            assertTrue(home.ownsAvatarSlot && live.isShown && live.hasWindowFocus())
            check(home, live)
        } finally {
            controller.pause().stop().destroy()
            JarvisAvatarSlot.register(null)
        }
    }

    @Test fun realPresenterMovesOnlyTheHeadAndFinishesTheBoot() = withCockpit { home, live ->
        val before = frame(live)
        val head = RectF(field(live, "headDrawBounds") as RectF)
        val orb = RectF(field(live, "orbDrawBounds") as RectF)
        val originalRenderer = field(live, "renderer") as ContinuumHologramRenderer
        val geometry = originalRenderer.geometry
        val state = field(live, "renderFrame") as ContinuumHologramRenderer.Frame
        val signals = listOf(state.phase, state.energy, state.audio)
        val weights = state.weights.copyOf()
        boot(home)
        val arriving = frame(live)
        capture(before, "entrance-rest")
        capture(arriving, "entrance-depth")
        val beforeHead = crop(before, head)
        val arrivingHead = crop(arriving, head)
        try {
            assertFalse("The real cue must visibly bring the head from depth", beforeHead.sameAs(arrivingHead))
            assertEquals("The orb keeps its drawing region", orb, field(live, "orbDrawBounds"))
            assertSame(originalRenderer, field(live, "renderer"))
            assertEquals(geometry, originalRenderer.geometry)
            assertEquals("Same clock and original orb signals", signals, listOf(state.phase, state.energy, state.audio))
            assertArrayEquals(weights, state.weights, 0f)
            assertTrue("The head starts far away", live.entranceScale() < .2f)
            assertEquals("The whole hologram stays unscaled", 1f, live.scaleX, 0f)
            advance(2_000L)
            assertTrue("The arriving head reaches its foreground pose", live.entranceScale() > 1f)
            frame(live).also { capture(it, "entrance-foreground"); it.recycle() }
            advance(1_000L)
            assertTrue("The head holds through the greeting", live.entranceScale() > 1f)
            advance(640L)
            assertEquals("The finite entrance restores normal framing", 1f, live.entranceScale(), 0f)
            frame(live).also { capture(it, "entrance-settled"); it.recycle() }
            advance(1_000L)
            assertEquals("No entrance replay", 1f, live.entranceScale(), 0f)
        } finally { listOf(before, arriving, beforeHead, arrivingHead).forEach(Bitmap::recycle) }
    }

    @Test fun shrinkCueReturnsTheHeadWithoutChangingTheWorkCamera() = withCockpit { home, live ->
        boot(home)
        live.setWorkWindowCamera(true, .5f)
        advance(1_200L)
        assertTrue(live.entranceScale() > 1f)
        assertEquals(1f, live.cameraProgress(), .00001f)
        assertTrue(home.avatarRenderer!!.onCockpitCue(TimelineStep(0L, 600L, Cue.AVATAR_SHRINK,
            CockpitAnimationEvent.BootStarted(BOOT_TURN, 0L, "Bereit"))))
        advance(640L)
        assertEquals("The SHRINK cue takes effect before the fallback deadline", 1f, live.entranceScale(), 0f)
        assertEquals("The accepted work camera stays active", 1f, live.cameraProgress(), .00001f)
    }

    @Test fun lifecycleResetsImmediatelyWithoutReplayingTheEntrance() = withCockpit { home, live ->
        val boundaries = listOf<Pair<() -> Unit, () -> Unit>>(
            ({ home.setForegroundActive(false); live.setForegroundActive(false) }) to
                ({ home.setForegroundActive(true); live.setForegroundActive(true) }),
            ({ live.setAnimationsEnabled(false) }) to ({ live.setAnimationsEnabled(true) }),
            ({ live.visibility = View.INVISIBLE }) to ({ live.visibility = View.VISIBLE }),
            ({ live.dispatchWindowFocusChanged(false) }) to ({ live.dispatchWindowFocusChanged(true) })
        )
        for ((leave, resume) in boundaries) {
            boot(home)
            assertTrue(live.entranceScale() < .2f)
            leave()
            assertEquals("The boundary resets before another frame", 1f, live.entranceScale(), 0f)
            resume()
            advance(4_000L)
            assertEquals("A delayed shrink cannot resurrect the entrance", 1f, live.entranceScale(), 0f)
        }
        boot(home)
        home.removeView(live)
        assertEquals("Detaching releases the entrance", 1f, live.entranceScale(), 0f)
    }

    @Test fun actualOwnerStopImmediatelyCancelsTheEntranceAndItsDelayedCue() {
        configure()
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible(); advance(32L); controller.windowFocusChanged(true)
            val activity = controller.get()
            val home = field(activity, "homeView") as ContinuumHomeView
            val live = field(activity, "hologramView") as ContinuumHologramView
            home.setForegroundActive(true); live.setForegroundActive(true)
            measure(activity.window.decorView); live.dispatchWindowFocusChanged(true)
            frame(live).recycle()
            boot(home)
            assertTrue("The actual Activity receives the boot cue", live.entranceScale() < .2f)
            AgentActivity::class.java.getDeclaredMethod("cancelLocalRequest")
                .apply { isAccessible = true }.invoke(activity)
            assertEquals("STOP resets before its queued callbacks", 1f, live.entranceScale(), 0f)
            advance(4_000L)
            assertEquals("No old cue can restart after STOP", 1f, live.entranceScale(), 0f)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun actualActivityStartupDeliversEntranceAfterFirstLayout() {
        configure()
        at.heinz.deviceai.cockpit.resetBootForTest()
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible(); advance(32L); controller.windowFocusChanged(true)
            val live = field(controller.get(), "hologramView") as ContinuumHologramView
            measure(controller.get().window.decorView)
            controller.get().window.decorView.dispatchWindowFocusChanged(true)
            advance(64L)
            frame(live).recycle()
            assertTrue("The real first boot reaches the head: ${live.motionDiagnostics()}", live.entranceScale() < .8f)
            advance(2_000L)
            assertTrue(live.entranceScale() > 1f)
            advance(1_600L)
            assertEquals(1f, live.entranceScale(), 0f)
        } finally { controller.pause().stop().destroy() }
    }
}
