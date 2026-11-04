package at.heinz.deviceai.avatar

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import at.heinz.deviceai.ui.AndroidContinuumPainter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real Android Canvas renders of the holographic bust on the app background for visual comparison with the owner
 * references. Synthetic AvatarContract poses; not Filament/GPU or physical-device evidence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumAvatarLookPreviewUiTest {
    private fun render(name: String, w: Int, h: Int, pose: AvatarPose, seconds: Float, wake: Float = 1f): Bitmap {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = 2f
        val bitmap = Bitmap.createBitmap((w * density).toInt(), (h * density).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xff050817.toInt())
        val painter = AndroidContinuumPainter(activity.assets)
        painter.begin(canvas)
        canvas.scale(density, density)
        ContinuumAvatarRenderer().draw(painter, w.toFloat(), h.toFloat(), pose, seconds, true, wake)
        System.getProperty("continuum.ui.evidenceDir")?.let { path ->
            val out = java.io.File(path).apply { mkdirs() }
            java.io.File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bitmap
    }

    private fun pose(jaw: Float = 0f, energy: Float = 0f, gx: Float = 0f, gy: Float = 0f, viseme: Viseme = Viseme.SIL) =
        AvatarPose(AvatarState.IDLE, AvatarExpression.NEUTRAL, gx, gy, jaw, viseme, if (viseme == Viseme.SIL) 0f else 1f, energy)

    @Test fun holographicBustRendersIdleSpeakingGazeAndWake() {
        val idle = render("look-280x360-idle", 280, 360, pose(), 1.2f)
        val speak = render("look-280x360-speaking-aa", 280, 360, pose(jaw = .8f, energy = .8f, viseme = Viseme.AA), 1.2f)
        render("look-280x360-speaking-o", 280, 360, pose(jaw = .5f, energy = .6f, viseme = Viseme.O), 2.0f)
        val gaze = render("look-280x360-gaze-right", 280, 360, pose(gx = .8f, gy = .2f), 1.2f)
        val asleep = render("look-280x360-wake-closed", 280, 360, pose(), 1.2f, wake = 0f)
        render("look-412x307-idle", 412, 307, pose(), 3.4f)
        assertFalse("speech must change the face", idle.sameAs(speak))
        assertFalse("gaze must turn head/irises", idle.sameAs(gaze))
        assertFalse("closed eyes before wake", idle.sameAs(asleep))
        var bright = 0
        for (y in 0 until idle.height step 4) for (x in 0 until idle.width step 4) {
            val c = idle.getPixel(x, y)
            if (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255) > 300) bright++
        }
        assertTrue("luminous pixels $bright", bright > 200)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JarvisAvatarSlotUiTest {
    @Test fun attachForwardsAndDetachRemoves() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = android.widget.FrameLayout(activity); activity.setContentView(host)
        val slot = JarvisAvatarSlot()
        slot.attach(host, at.heinz.deviceai.ui.JarvisShellLayout.Box(0f, 0f, 300f, 300f))
        assertTrue(slot.isAttached); assertTrue(host.childCount == 1)
        slot.onAvatarState(AvatarEvent.SpeechStarted("u", "Hallo", 1))
        slot.onAnimationEvent(at.heinz.deviceai.cockpit.CockpitAnimationEvent.CommandFlyIn("t", 2, "Hallo"))
        assertTrue(slot.sequence!!.effects.isAnimating(slot.sequence!!.clock()))
        slot.detach()
        assertFalse(slot.isAttached); assertTrue(host.childCount == 0)
    }
}
