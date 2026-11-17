package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.widget.FrameLayout
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisSequenceOutcomeUiTest {
    private val controller = Robolectric.buildActivity(Activity::class.java).setup()
    private var now = 1_000L
    private val view = JarvisSequenceView(controller.get()).apply { clock = { now } }
    private val errors = mutableListOf<Throwable>()
    private val bus = CockpitAnimationBus().apply {
        subscribe { event ->
            try { view.render(event) } catch (error: Throwable) { errors += error }
        }
    }

    @After fun release() {
        try { assertTrue("Renderer errors: $errors", errors.isEmpty()) }
        finally { view.clear(); controller.pause().stop().destroy() }
    }

    private data class Frame(val texts: List<String>, val colors: List<Int>, val spinner: Boolean)

    private fun frame(at: Long): Frame {
        val texts = mutableListOf<String>()
        val colors = mutableListOf<Int>()
        var spinner = false
        val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, values ->
            val args = values.orEmpty()
            colors += args.filterIsInstance<Fill.Solid>().map { it.argb }
            if (method.name == "drawText") texts += args[0] as String
            if (method.name == "strokeArc" && args[5] == 250f) spinner = true
            when (method.name) {
                "measureText" -> (args[0] as String).length * (args[1] as TextStyle).size * .5f
                "capHeight" -> (args[0] as TextStyle).size * .7f
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Float.TYPE -> 1f
                    else -> null
                }
            }
        } as ContinuumPainter
        view.effects.draw(painter, 412f, 600f, at)
        return Frame(texts, colors, spinner)
    }

    private fun timeline(vararg steps: ProcessStep) =
        bus.emit(CockpitAnimationEvent.ProcessTimeline("turn", now, steps.toList()))

    private fun assertNeutral(frame: Frame, at: Long) {
        assertFalse("Unverified work has no success mark", JarvisDesign.Colors.ok in frame.colors)
        assertFalse("Unverified work has no failure mark", JarvisDesign.Colors.error in frame.colors)
        assertFalse("Unverified work has no running spinner", frame.spinner)
        assertFalse("Settled work has no live animation", view.effects.isAnimating(at))
    }

    @Test fun unverifiedWorkIsNotHiddenByLaterCompletedTool() {
        timeline(ProcessStep("work", "Bearbeitung", StepState.ACTIVE))
        now = 4_000L
        timeline(ProcessStep("work", "Antwort erstellt", StepState.UNVERIFIED),
            ProcessStep("tool:read", "Datei gelesen", StepState.DONE))

        val rendered = frame(5_500L)
        assertEquals(1, view.effects.drawnCardCount)
        assertTrue("Overall work owns the label", "Antwort erstellt" in rendered.texts)
        assertFalse("A tool result is not the overall result", "Datei gelesen" in rendered.texts)
        assertNeutral(rendered, 5_500L)

        now = 6_000L
        bus.emit(CockpitAnimationEvent.TurnEnded("turn", now, true))
        frame(7_000L)
        assertEquals(0, view.effects.drawnCardCount)
        assertFalse(view.effects.isAnimating(7_000L))
    }

    @Test fun unverifiedDetailClearsAnEarlierSuccessMark() {
        val host = FrameLayout(controller.get())
        host.addView(view)
        controller.get().setContentView(host)
        controller.visible()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.isAttachedToWindow)
        timeline(ProcessStep("review", "Prüfung", StepState.ACTIVE))
        now = 4_000L
        timeline(ProcessStep("review", "Prüfung", StepState.DONE))
        assertTrue("The probe observes real success paint",
            JarvisDesign.Colors.ok in frame(5_500L).colors)

        now = 5_500L
        val surface = Bitmap.createBitmap(412, 600, Bitmap.Config.ARGB_8888)
        try { view.draw(Canvas(surface)) } finally { surface.recycle() }
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        shadowOf(view).clearWasInvalidated()

        now = 6_000L
        timeline(ProcessStep("review", "Prüfung", StepState.UNVERIFIED))
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        assertTrue("Settled detail requests a new frame", shadowOf(view).wasInvalidated())
        assertNeutral(frame(7_500L), 7_500L)
        assertEquals(1, view.effects.drawnCardCount)
    }
}
