package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.View
import at.heinz.deviceai.cockpit.CockpitAnimationBus
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.MEMORY_TURN
import at.heinz.deviceai.ui.ContinuumBrainSceneView
import at.heinz.deviceai.ui.JarvisSequenceEffects
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

/** Actual dialog; synthetic memory events. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumBrainHypothesisUiTest {
    private data class Fixture(val activity: AgentActivity, val bus: CockpitAnimationBus)
    private data class Brain(val dialog: AlertDialog, val root: View,
                             val scene: ContinuumBrainSceneView, val effects: JarvisSequenceEffects)

    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)

    private fun layout(root: View, width: Int, height: Int) {
        assertTrue(width > 0 && height > 0)
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        root.dispatchWindowFocusChanged(true)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun open(f: Fixture): Brain {
        AgentActivity::class.java.getDeclaredMethod("navigateDashboardTargetNow", String::class.java)
            .apply { isAccessible = true }.invoke(f.activity, "brain")
        // Deliver actual dialog traversal.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        val dialog = field(f.activity, "centerDialog") as AlertDialog
        val scene = field(f.activity, "brainScene") as ContinuumBrainSceneView
        val window = requireNotNull(dialog.window)
        val root = window.decorView
        layout(root, window.attributes.width, window.attributes.height)
        assertTrue("Activity opens the actual Brain dialog", dialog.isShowing)
        assertTrue("Brain surface is attached and shown", scene.isAttachedToWindow && scene.isShown)
        assertTrue("Dialog lays out its Brain surface", scene.width > 0 && scene.height > 0)
        return Brain(dialog, root, scene, field(scene, "effects") as JarvisSequenceEffects)
    }

    private fun frame(brain: Brain): Bitmap {
        brain.root.viewTreeObserver.dispatchOnPreDraw()
        return Bitmap.createBitmap(brain.root.width, brain.root.height, Bitmap.Config.ARGB_8888)
            .also { brain.root.draw(Canvas(it)) }
    }

    private fun event(status: String) = CockpitAnimationEvent.MemoryStored(
        MEMORY_TURN, System.currentTimeMillis(), "synthetic-brain-entry", status)

    private fun emit(f: Fixture, status: String) {
        f.bus.emit(event(status))
        idle()
    }

    private fun withActivity(check: (Fixture) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            controller.windowFocusChanged(true)
            val activity = controller.get()
            layout(activity.window.decorView, 884, 760)
            check(Fixture(activity, field(activity, "cockpitBus") as CockpitAnimationBus))
        } finally {
            (field(controller.get(), "centerDialog") as? AlertDialog)?.dismiss()
            idle()
            controller.pause().stop().destroy()
        }
    }

    @Test fun actualBrainDialogShowsHypothesisWithoutInventingConfirmation() = withActivity { f ->
        val brain = open(f)
        val now = SystemClock.uptimeMillis()
        val rest = frame(brain)
        try {
            for (status in listOf("UNCONFIRMED", "REVOKED")) {
                emit(f, status)
                assertFalse(brain.effects.hypothesisVisible(now))
                assertEquals(0f, brain.effects.orbPulseLevel(now), 0f)
                val unchanged = frame(brain)
                try { assertTrue("$status must not invent a visual memory signal", rest.sameAs(unchanged)) }
                finally { unchanged.recycle() }
            }
            emit(f, "HYPOTHETICAL")
            assertTrue("The Activity subscription admits the real event", brain.effects.hypothesisVisible(now))
            assertEquals("A hypothesis is not confirmation", 0f, brain.effects.orbPulseLevel(now), 0f)
            val hypothesis = frame(brain)
            try {
                assertEquals("Frames use identical production time", now, SystemClock.uptimeMillis())
                assertFalse("Hypothesis must change the native dialog pixels", rest.sameAs(hypothesis))
            } finally { hypothesis.recycle() }
        } finally { rest.recycle() }
    }

    @Test fun closingBrainDialogRetiresQueuedHypothesesAndReopensCalm() = withActivity { f ->
        val old = open(f)
        emit(f, "HYPOTHETICAL")
        assertTrue(old.effects.hypothesisVisible(SystemClock.uptimeMillis()))
        Thread { f.bus.emit(event("HYPOTHETICAL")) }.apply { start(); join() }
        assertTrue(old.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick())
        idle()
        assertFalse("The real close button dismisses", old.dialog.isShowing)
        assertNull(field(f.activity, "brainScene"))
        assertFalse("Close clears active and queued effects", old.effects.isAnimating(SystemClock.uptimeMillis()))

        val fresh = open(f)
        assertNotSame(old.scene, fresh.scene)
        assertFalse("Reopen cannot replay old memory", fresh.effects.isAnimating(SystemClock.uptimeMillis()))
        val now = SystemClock.uptimeMillis()
        val rest = frame(fresh)
        try {
            emit(f, "CONFIRMED")
            assertFalse("A fresh confirmation is not a hypothesis", fresh.effects.hypothesisVisible(now))
            assertTrue("Fresh events still reach the dialog", fresh.effects.orbPulseLevel(now) > 0f)
            assertFalse("The old surface remains retired", old.effects.isAnimating(now))
            val confirmed = frame(fresh)
            try {
                assertEquals("Control frames share production time", now, SystemClock.uptimeMillis())
                assertFalse("Confirmed memory has its own native signal", rest.sameAs(confirmed))
            } finally { confirmed.recycle() }
        } finally { rest.recycle() }
    }
}
