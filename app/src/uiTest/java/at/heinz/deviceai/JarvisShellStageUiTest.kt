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
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.io.File
import java.util.concurrent.TimeUnit

/** Welten-Optik's JarvisShellView (#479) embedded in the real home: bottom layer, live avatar in its AVATAR slot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisShellStageUiTest {
    @Before fun configureVirtualVsyncClock() {
        // Activity setup can start the real avatar loop before fixture code can pause it.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advanceFrames(totalMillis: Long) {
        var remaining = totalMillis
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            remaining -= step
        }
    }

    private fun field(activity: AgentActivity, name: String): Any? = AgentActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)

    private fun run(label: String, width: Int, height: Int) {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit().putBoolean("introduced", true).commit()
        // Native host Canvas capture; optional Android Filament rendering is checked on the device separately.
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("jarvis-shell", true).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            advanceFrames(16)
            controller.windowFocusChanged(true)
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
                advanceFrames(40)
            }
            measure(); measure()
            assertTrue(home.jarvisShellStage)
            assertEquals(View.VISIBLE, home.jarvisShell.visibility)
            assertEquals("shell is the bottom layer", 0, home.indexOfChild(home.jarvisShell))
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val shell = requireNotNull(home.jarvisShell.shell) { "shell drawn" }
            val d = home.resources.displayMetrics.density
            // Heinz's takeover request: retain the original live head AND orb in the ref5 composite stage.
            val box = requireNotNull(shell[JarvisShellLayout.Slot.AVATAR])
            assertEquals("ONE live stage = head plus orb", (box.left * d).toInt(), home.avatarSlotRect.left)
            assertEquals((box.top * d).toInt(), home.avatarSlotRect.top)
            assertEquals((box.right * d).toInt(), home.avatarSlotRect.right)
            assertEquals((box.bottom * d).toInt(), home.avatarSlotRect.bottom)
            assertTrue("the real head is registered by the activity",
                at.heinz.deviceai.cockpit.JarvisAvatarSlot.renderer is at.heinz.deviceai.avatar.JarvisHoloAvatar)
            assertTrue("head docked in the slot", home.avatarSlotShown && home.avatarRenderer is at.heinz.deviceai.avatar.JarvisHoloAvatar)
            assertTrue("The docked head must be the existing Activity signal surface",
                (home.avatarRenderer as at.heinz.deviceai.avatar.JarvisHoloAvatar).hologram === avatar)
            assertEquals("The signal surface must remain visible", View.VISIBLE, avatar.visibility)
            assertEquals(home, avatar.parent)
            System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
                File(dir).mkdirs()
                File(dir, "$label.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            bitmap.recycle()
        } finally {
            runCatching { controller.pause(); controller.stop(); controller.destroy() }
            assertEquals("onDestroy unregisters the head", null, at.heinz.deviceai.cockpit.JarvisAvatarSlot.renderer)
            app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test @Config(qualifiers = "w412dp-h915dp-mdpi") fun phone() = run("shell-phone-412x915", 412, 915)
    @Test @Config(qualifiers = "w884dp-h760dp-land-mdpi") fun foldOpen() = run("shell-fold7-884x760", 884, 760)
    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi") fun tablet() = run("shell-tablet-1280x800", 1280, 800)
}

/** The ONE avatar slot: a registered renderer gets attach + events, unregistering detaches it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisAvatarSlotUiTest {
    @Before fun configureVirtualVsyncClock() {
        // Activity setup can start the real avatar loop before fixture code can pause it.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advanceFrames(totalMillis: Long) {
        var remaining = totalMillis
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            remaining -= step
        }
    }

    private class Fake : at.heinz.deviceai.cockpit.JarvisAvatarRenderer {
        val calls = mutableListOf<String>(); var slot: android.graphics.Rect? = null
        override fun attach(host: android.view.ViewGroup, slot: android.graphics.Rect) { calls += "attach"; this.slot = slot }
        override fun onAvatarState(state: at.heinz.deviceai.avatar.AvatarState) { calls += "state" }
        override fun onAnimationEvent(event: at.heinz.deviceai.cockpit.CockpitAnimationEvent) { calls += "event:" + event::class.simpleName }
        override fun detach() { calls += "detach" }
    }

    @Test fun registeredRendererIsDockedFedAndDetached() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit().putBoolean("introduced", true).commit()
        // This host slot/lifecycle test uses the real Canvas head; Filament is a separate device check.
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().putBoolean("jarvis-shell", true).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            advanceFrames(16)
            controller.windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            val home = AgentActivity::class.java.getDeclaredField("homeView").apply { isAccessible = true }.get(activity) as ContinuumHomeView
            fun settle() {
                root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 884, 760); advanceFrames(100)
            }
            settle()
            val fake = Fake()
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(fake)
            settle(); settle()
            assertTrue(fake.calls.toString(), "attach" in fake.calls)
            assertTrue("slot rect is the avatar slot", fake.slot!!.width() > 0 && fake.slot == home.avatarSlotRect)
            assertTrue(home.avatarRenderer === fake)
            val bus = AgentActivity::class.java.getDeclaredField("cockpitBus").apply { isAccessible = true }.get(activity)
                as at.heinz.deviceai.cockpit.CockpitAnimationBus
            bus.emit(at.heinz.deviceai.cockpit.CockpitAnimationEvent.CommandFlyIn("t", 1L, "x"))
            settle()
            assertTrue(fake.calls.toString(), "event:CommandFlyIn" in fake.calls)
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.fanOut { it.run() }.onAvatarEvent(
                at.heinz.deviceai.avatar.AvatarEvent.StateChanged(at.heinz.deviceai.avatar.AvatarState.THINKING,
                    at.heinz.deviceai.avatar.AvatarExpression.FOCUSED, at.heinz.deviceai.avatar.GazeTarget.OWNER, 1L))
            assertTrue("state" in fake.calls)
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
            settle()
            assertEquals("detach", fake.calls.last())
            assertTrue(home.avatarSlotShown) // placeholder again
        } finally {
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
            runCatching { controller.pause(); controller.stop(); controller.destroy() }
            app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
