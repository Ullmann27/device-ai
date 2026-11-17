package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.avatar.AvatarDirector
import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import org.junit.Assert.*
import org.junit.Before
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
import java.util.concurrent.TimeUnit

/** Actual Android Canvas + existing Director; synthetic TTS events, never a claim of device TTS QA. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumAvatarNativeUiTest {
    @Before fun configureVirtualVsyncClock() {
        // Automatic virtual-vsync advances time while idleFor drains recurring frames.
        // Explicit frame advancement keeps the real lifecycle assertions bounded.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }

    private fun advanceFrames(totalMillis: Long) {
        // Paused VSync is driven by clock changes, not by a queued Looper deadline.
        var remaining = totalMillis
        while (remaining > 0) {
            val step = minOf(16L, remaining)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            remaining -= step
        }
    }

    private fun attachVisible(activity: Activity, view: View, width: Int = 412, height: Int = 307) {
        activity.setContentView(view, ViewGroup.LayoutParams(width, height))
        advanceFrames(16) // Complete the actual window traversal before foreground activation.
        assertTrue("Fixture must be attached to the real Activity window", view.isAttachedToWindow)
        assertTrue("Fixture must be shown before testing foreground behavior", view.isShown)
        assertEquals(View.VISIBLE, view.windowVisibility)
        assertEquals("Window traversal must preserve the requested viewport width", width, view.width)
        assertEquals("Window traversal must preserve the requested viewport height", height, view.height)
    }

    private fun assertFrozenActiveClock(view: ContinuumHologramView, expected: ContinuumHologramView.MotionSample) {
        val pending = ContinuumHologramView::class.java.getDeclaredField("framePending").apply { isAccessible = true }
        assertTrue("Speech fixture must retain its scheduled frame", pending.getBoolean(view))
        val actual = view.sampleMotion()
        assertEquals("PCM comparison must not advance callbacks", expected.callbackCount, actual.callbackCount)
        assertEquals("PCM comparison must hold the idle phase", expected.phase, actual.phase, 0f)
    }

    private fun snapshot(view: ContinuumHologramView, name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val before = view.sampleMotion().drawCount
        view.draw(Canvas(bitmap))
        assertTrue("Pixel capture must execute the actual Canvas draw", view.sampleMotion().drawCount > before)
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
                view.setNativeHeadEnabled(true)
                attachVisible(controller.get(), view, width, height)
                view.setForegroundActive(true)
                val director = AvatarDirector(view, android.os.SystemClock::elapsedRealtime)
                director.onRuntime(AvatarRuntimeSignals(speaking = true))
                val before = view.sampleMotion()
                advanceFrames(200)
                val active = view.sampleMotion()
                assertTrue("Speech fixture requires real frame callbacks", active.callbackCount > before.callbackCount)
                assertTrue("Speech fixture requires real phase progress", active.phase > before.phase)
                assertFrozenActiveClock(view, active)
                val closed = snapshot(view, "head-${width}x$height-closed").also { frames += it }
                director.speechStarted("fixture", "Hallo Heinz")
                // Production Director and View.pose share Android elapsedRealtime; the Robolectric
                // virtual clock remains fixed while PCM is supplied, keeping idle pixels stable.
                repeat(8) { index ->
                    if (index > 0) Thread.sleep(33)
                    director.audioLevel("fixture", .8f)
                }
                assertFrozenActiveClock(view, active)
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
                assertFrozenActiveClock(view, active)
                val stopped = snapshot(view, "head-${width}x$height-stopped").also { frames += it }
                assertTrue("Interruption and stale callbacks must return to the closed mouth", closed.sameAs(stopped))
            } finally {
                view.setNativeHeadEnabled(false)
                frames.forEach { it.recycle() }
                controller.pause().stop().destroy()
            }
        }
    }

    @Test fun actualActivityStopClosesAvatarBeforeAnyAsynchronousTtsCallback() {
        // Construct the real Activity without onCreate: no model/bootstrap/background research is started.
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        val activity = controller.get()
        val events = mutableListOf<at.heinz.deviceai.avatar.AvatarEvent>()
        val field = AgentActivity::class.java.getDeclaredField("avatarDirector").apply { isAccessible = true }
        val director = field.get(activity) as AvatarDirector
        try {
            director.attach(at.heinz.deviceai.avatar.AvatarSink { events += it })
            AgentActivity::class.java.getDeclaredField("voiceUtteranceId").apply { isAccessible = true }.set(activity, "active")
            director.speechStarted("active", "Hallo")
            director.audioLevel("active", .8f)
            AgentActivity::class.java.getDeclaredMethod("stopVoiceAnimation").apply { isAccessible = true }.invoke(activity)
            val ended = events.filterIsInstance<at.heinz.deviceai.avatar.AvatarEvent.SpeechEnded>().single()
            assertEquals("active", ended.utteranceId)
            assertTrue(ended.interrupted)
            val count = events.size
            director.audioLevel("active", 1f)
            director.wordRange("active", 0, 5)
            assertEquals("Callbacks after synchronous stop must stay rejected", count, events.size)
        } finally { controller.destroy() }
    }

    @Test fun optionalHeadPersistsAndSharesOnePausedReducedMotionClock() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val view = ContinuumHologramView(controller.get())
        try {
            view.setNativeHeadEnabled(false)
            attachVisible(controller.get(), view)
            assertFalse(ContinuumHologramView(controller.get()).isNativeHeadEnabled())
            view.setNativeHeadEnabled(true)
            assertTrue(ContinuumHologramView(controller.get()).isNativeHeadEnabled())
            view.setForegroundActive(true)
            advanceFrames(200)
            assertTrue(view.sampleMotion().callbackCount > 0)
            view.setForegroundActive(false)
            val paused = view.sampleMotion().callbackCount
            advanceFrames(1_000)
            assertEquals(paused, view.sampleMotion().callbackCount)
            view.setAnimationsEnabled(false)
            view.setForegroundActive(true)
            advanceFrames(1_000)
            assertEquals(paused, view.sampleMotion().callbackCount)
            view.setAnimationsEnabled(true)
            advanceFrames(200)
            assertTrue(view.sampleMotion().callbackCount > paused)
            controller.get().setContentView(View(controller.get()))
            val detached = view.sampleMotion().callbackCount
            advanceFrames(1_000)
            assertEquals(detached, view.sampleMotion().callbackCount)
        } finally {
            view.setNativeHeadEnabled(false)
            controller.pause().stop().destroy()
        }
    }
}
