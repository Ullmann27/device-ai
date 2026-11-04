package at.heinz.deviceai.avatar

import android.app.Activity
import android.graphics.Rect
import android.widget.FrameLayout
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JarvisHoloAvatarUiTest {
    @Test fun attachPlacesOneStageInTheSlotRectAndDetachRemovesIt() {
        val host = FrameLayout(Robolectric.buildActivity(Activity::class.java).setup().get())
        val avatar = JarvisHoloAvatar()
        avatar.attach(host, Rect(40, 100, 640, 700))
        assertEquals(1, host.childCount)
        val stage = host.getChildAt(0)
        assertEquals(40f, stage.x, 0f); assertEquals(100f, stage.y, 0f)
        assertEquals(600, stage.layoutParams.width); assertEquals(600, stage.layoutParams.height)
        avatar.onAvatarState(AvatarState.SPEAKING)
        avatar.onAnimationEvent(CockpitAnimationEvent.TurnEnded("t", 0L, true))
        assertTrue(avatar.isAttached)
        avatar.attach(host, Rect(0, 0, 100, 100))
        assertEquals("re-attach must not stack stages", 1, host.childCount)
        avatar.detach()
        assertEquals(0, host.childCount); assertFalse(avatar.isAttached)
    }
}
