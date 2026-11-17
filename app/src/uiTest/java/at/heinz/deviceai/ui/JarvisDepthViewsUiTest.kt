package at.heinz.deviceai.ui

import android.app.Activity
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // only SDK 28 is available offline; API 31 blur branch not host-tested
class JarvisDepthViewsUiTest {
    @Test fun farPanelsTiltShrinkAndResetIsFlat() {
        val v = View(Robolectric.buildActivity(Activity::class.java).setup().get())
        v.layout(0, 0, 200, 100)
        JarvisDepthViews.apply(v, JarvisMotion.Depth.Layer.FAR, 1000f, 800f, 3000L)
        assertTrue(v.scaleX < 1f && v.rotationY != 0f && v.cameraDistance > 0f)
        JarvisDepthViews.reset(v)
        assertEquals(1f, v.scaleX, 0f); assertEquals(0f, v.rotationY, 0f)
    }
}
