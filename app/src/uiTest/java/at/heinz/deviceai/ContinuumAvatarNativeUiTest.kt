package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/** Actual Android Canvas + existing Director; synthetic TTS events, never a claim of device TTS QA. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContinuumAvatarNativeUiTest {
    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun snapshot(view: View, name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        System.getProperty("continuum.ui.evidenceDir")?.let { path ->
            val out = java.io.File(path).apply { mkdirs() }
            java.io.File(out, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        return bitmap
    }

    @Test fun phoneAndFoldNativeHeadFollowsSpeechAndStopsOnInterruption() {
        for ((width, height) in listOf(412 to 307, 410 to 291)) {
            val controller = Robolectric.buildActivity(Activity::class.java).setup()
            val view = ContinuumHologramView(controller.get())
            val frames = mutableListOf<Bitmap>()
            try {
                controller.get().setContentView(view)
                layout(view, width, height)
                view.setNativeHeadEnabled(true)
                view.setForegroundActive(true)
                val director = AvatarDirector(view)
                director.onRuntime(AvatarRuntimeSignals(speaking = true))
                val closed = snapshot(view, "head-${width}x$height-closed").also { frames += it }
                director.speechStarted("fixture", "Hallo Heinz")
                repeat(8) { director.audioLevel("fixture", .8f) }
                val speaking = snapshot(view, "head-${width}x$height-speaking").also { frames += it }
                assertFalse("Existing speech envelope must visibly open the mouth", closed.sameAs(speaking))
                var mouthPixels = 0
                for (y in height * 60 / 100 until height * 74 / 100)
                    for (x in width * 40 / 100 until width * 60 / 100)
                        if (closed.getPixel(x, y) != speaking.getPixel(x, y)) mouthPixels++
                assertTrue("Speech must change mouth pixels, not just another region ($mouthPixels)", mouthPixels > 30)
                director.speechEnded("fixture", true)
                director.audioLevel("fixture", 1f)
                director.wordRange("fixture", 0, 5)
                val stopped = snapshot(view, "head-${width}x$height-stopped").also { frames += it }
                assertTrue("Interruption and stale callbacks must return to the closed mouth", closed.sameAs(stopped))
            } finally {
                view.setNativeHeadEnabled(false)
                frames.forEach { it.recycle() }
                controller.pause().stop().destroy()
            }
        }
    }

    @Test fun optionalHeadPersistsAndSharesOnePausedReducedMotionClock() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumHologramView(controller.get())
        try {
            controller.get().setContentView(view)
            layout(view, 412, 307)
            view.setNativeHeadEnabled(false)
            assertFalse(ContinuumHologramView(controller.get()).isNativeHeadEnabled())
            view.setNativeHeadEnabled(true)
            assertTrue(ContinuumHologramView(controller.get()).isNativeHeadEnabled())
            view.setForegroundActive(true)
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            assertTrue(view.sampleMotion().callbackCount > 0)
            view.setForegroundActive(false)
            val paused = view.sampleMotion().callbackCount
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            assertEquals(paused, view.sampleMotion().callbackCount)
            view.setAnimationsEnabled(false)
            view.setForegroundActive(true)
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            assertEquals(paused, view.sampleMotion().callbackCount)
            view.setAnimationsEnabled(true)
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            assertTrue(view.sampleMotion().callbackCount > paused)
            controller.get().setContentView(View(controller.get()))
            val detached = view.sampleMotion().callbackCount
            shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            assertEquals(detached, view.sampleMotion().callbackCount)
        } finally {
            view.setNativeHeadEnabled(false)
            controller.pause().stop().destroy()
        }
    }
}
