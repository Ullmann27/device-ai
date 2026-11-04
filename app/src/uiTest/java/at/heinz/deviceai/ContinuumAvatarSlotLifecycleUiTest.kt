package at.heinz.deviceai

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.ui.ContinuumHomeView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumAvatarSlotLifecycleUiTest {
    @Test fun detachedHomeCannotConsumeTheNextOwnersRegistration() = withActivity { activity ->
        val previous = home(activity)
        JarvisAvatarSlot.register(JarvisHoloAvatar(previous.hologram))
        activity.setContentView(previous)
        measure(previous)
        assertFalse(previous.avatarSlotRect.isEmpty)

        val owner = FrameLayout(activity)
        val live = surface(activity)
        owner.addView(live)
        activity.setContentView(owner)
        assertFalse(previous.isAttachedToWindow)
        val renderer = JarvisHoloAvatar(live)
        renderer.attach(owner, Rect(0, 0, 400, 500))
        JarvisAvatarSlot.register(renderer)

        shadowOf(Looper.getMainLooper()).idle()

        assertNull(previous.avatarRenderer)
        assertSame(previous, previous.hologram.parent)
        assertSame(owner, live.parent)
        assertSame(live, renderer.hologram)
        assertTrue(renderer.isAttached)
    }

    @Test fun attachedHomeWaitsForItsOwnLiveSurfaceAndRedocksAfterReattachment() = withActivity { activity ->
        val home = home(activity)
        activity.setContentView(home)
        measure(home)
        shadowOf(Looper.getMainLooper()).idle()

        val owner = FrameLayout(activity)
        val foreign = surface(activity)
        owner.addView(foreign)
        val otherRenderer = JarvisHoloAvatar(foreign)
        otherRenderer.attach(owner, Rect(0, 0, 400, 500))
        JarvisAvatarSlot.register(otherRenderer)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(home.avatarRenderer)
        assertTrue(home.avatarSlotShown)
        assertSame(owner, foreign.parent)
        assertSame(foreign, otherRenderer.hologram)
        assertTrue(otherRenderer.isAttached)

        otherRenderer.detach()
        val ownRenderer = JarvisHoloAvatar(home.hologram)
        JarvisAvatarSlot.register(ownRenderer)
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(ownRenderer, home.avatarRenderer)
        assertSame(home.hologram, ownRenderer.hologram)

        activity.setContentView(FrameLayout(activity))
        assertFalse(ownRenderer.isAttached)
        assertNull(home.avatarRenderer)
        activity.setContentView(home)
        measure(home)
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(ownRenderer, home.avatarRenderer)
        assertTrue(ownRenderer.isAttached)
        assertSame(home, home.hologram.parent)
    }

    @Test fun directAttachmentStillRejectsAnotherSurfacesParent() = withActivity { activity ->
        val owner = FrameLayout(activity)
        val live = surface(activity)
        owner.addView(live)
        val renderer = JarvisHoloAvatar(live)
        assertThrows(IllegalArgumentException::class.java) {
            renderer.attach(FrameLayout(activity), Rect(0, 0, 400, 500))
        }
        assertSame(owner, live.parent)
        assertFalse(renderer.isAttached)
    }

    private fun surface(activity: Activity) = ContinuumHologramView(activity).apply {
        setNativeHeadEnabled(false)
        setAnimationsEnabled(false)
        setForegroundActive(false)
    }

    private fun home(activity: Activity) = ContinuumHomeView(activity, surface(activity)) { _, _, _ -> }.apply {
        configure(884f, true, windowHeightDp = 760f)
        setJarvisShellStage(true)
        jarvisShell.ambientMotionEnabled = false
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 884, 760)
    }

    private fun withActivity(check: (Activity) -> Unit) {
        JarvisAvatarSlot.register(null)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val preferences = controller.get().getSharedPreferences("continuum-cockpit-presentation", 0)
        val hadShell = preferences.contains("jarvis-shell")
        val previousShell = preferences.getBoolean("jarvis-shell", false)
        val appearance = controller.get().getSharedPreferences("continuum-avatar-appearance", 0)
        val previousAppearance = listOf("native-head", "native-head-3d").associateWith { key ->
            if (appearance.contains(key)) appearance.getBoolean(key, false) else null
        }
        try {
            check(controller.get())
        } finally {
            val renderer = JarvisAvatarSlot.renderer
            JarvisAvatarSlot.register(null)
            renderer?.detach()
            controller.pause().stop().destroy()
            shadowOf(Looper.getMainLooper()).idle()
            preferences.edit().apply {
                if (hadShell) putBoolean("jarvis-shell", previousShell) else remove("jarvis-shell")
            }.commit()
            appearance.edit().apply {
                previousAppearance.forEach { (key, value) ->
                    if (value == null) remove(key) else putBoolean(key, value)
                }
            }.commit()
        }
    }
}
