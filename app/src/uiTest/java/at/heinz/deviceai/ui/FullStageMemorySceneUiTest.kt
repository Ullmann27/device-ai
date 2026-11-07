package at.heinz.deviceai.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.JarvisAvatarSlot
import at.heinz.deviceai.cockpit.MEMORY_TURN
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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
class FullStageMemorySceneUiTest {
    private fun withCockpit(check: (ContinuumHomeView, ContinuumHologramView, JarvisSequenceView) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        activity.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val live = ContinuumHologramView(activity)
        val home = ContinuumHomeView(activity, live) { _, _, _ -> }
        home.configure(884f, true, windowHeightDp = 760f)
        home.setJarvisShellStage(true)
        JarvisAvatarSlot.register(JarvisHoloAvatar(live))
        activity.setContentView(home)
        try {
            repeat(3) {
                home.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
                home.layout(0, 0, 884, 760)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            }
            home.setForegroundActive(true)
            live.setForegroundActive(true)
            check(home, live, requireNotNull((home.avatarRenderer as JarvisHoloAvatar).sequence))
        } finally {
            controller.pause().stop().destroy()
            JarvisAvatarSlot.register(null)
        }
    }

    private fun frame(view: View): Bitmap {
        fun invalidate(v: View) {
            v.invalidate()
            if (v is ViewGroup) repeat(v.childCount) { invalidate(v.getChildAt(it)) }
        }
        invalidate(view)
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }
    }

    private fun recall(home: ContinuumHomeView, count: Int = 2) {
        CockpitAnimationPresenter(home).render(CockpitAnimationEvent.RecallHits(MEMORY_TURN,
            1L, "synthetic-fullstage-recall", count, if (count > 0) .9f else null))
        repeat(4) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            val sequence = (home.avatarRenderer as JarvisHoloAvatar).sequence!!
            if (sequence.effects.recallCount == count) return
        }
        fail("The actual presenter must deliver the recall")
    }

    @Test fun realRecallDrawsBeyondTheAvatarSlot() = withCockpit { home, _, sequence ->
        var now = 1_000L
        sequence.clock = { now }
        val before = frame(home)
        recall(home)
        now = 2_200L
        val flying = frame(home)
        val avatar = home.avatarSlotRect
        var outsideChanges = 0
        for (y in 0 until home.height) for (x in 0 until home.width) {
            if (!avatar.contains(x, y) && before.getPixel(x, y) != flying.getPixel(x, y)) outsideChanges++
        }
        before.recycle(); flying.recycle()
        assertTrue("A real recall must draw a document scene beyond the avatar slot", outsideChanges > 0)
    }
}
