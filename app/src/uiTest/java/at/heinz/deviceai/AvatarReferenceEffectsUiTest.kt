package at.heinz.deviceai

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.FrameLayout
import at.heinz.deviceai.avatar.JarvisHoloAvatar
import at.heinz.deviceai.cockpit.BOOT_TURN
import at.heinz.deviceai.cockpit.MEMORY_TURN
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystem
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Host Canvas; synthetic event inputs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], instrumentedPackages = ["at.heinz.deviceai.ContinuumHologramView"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AvatarReferenceEffectsUiTest {
    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)
    private fun now() = ShadowSystem.currentTimeMillis()
    private fun entry(at: Long = now()) = CockpitAnimationEvent.CockpitEntered(BOOT_TURN, at, "fixture-session")

    private fun withSurface(check: (ContinuumHologramView, JarvisHoloAvatar, Bitmap) -> Unit) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val context = controller.get()
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        context.getSharedPreferences("continuum-avatar-appearance", 0).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        val host = FrameLayout(context)
        val live = ContinuumHologramView(context)
        host.addView(live, FrameLayout.LayoutParams(640, 480))
        context.setContentView(host)
        val adapter = JarvisHoloAvatar(live)
        adapter.attach(host, Rect(0, 0, 640, 480))
        live.addView(object : View(context) {
            override fun onDraw(canvas: Canvas) { canvas.drawColor(Color.rgb(20, 30, 50)) }
        }.apply { setWillNotDraw(false) }, FrameLayout.LayoutParams(640, 480))
        live.setForegroundActive(true)
        advance(40)
        live.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY))
        live.layout(0, 0, 640, 480)
        val clean = capture(live)
        try {
            assertEquals(Color.rgb(20, 30, 50), clean.getPixel(320, 200))
            assertTrue(live.isNativeHeadEnabled())
            check(live, adapter, clean)
        } finally {
            clean.recycle()
            adapter.detach()
            controller.pause().stop().destroy()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        }
    }

    private fun capture(live: ContinuumHologramView) =
        Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).also { live.draw(Canvas(it)) }

    private fun differs(live: ContinuumHologramView, clean: Bitmap): Boolean {
        val actual = capture(live)
        return try { !clean.sameAs(actual) } finally { actual.recycle() }
    }

    @Test fun cockpitEntryDrawsAboveTheExistingChildComposition() = withSurface { live, adapter, clean ->
        adapter.onAnimationEvent(entry())
        advance(350)
        assertTrue("The actual cockpit event must reach the visible head layer", differs(live, clean))
        advance(2_000)
        assertFalse("Icons expire without a second animation loop", differs(live, clean))
    }

    @Test fun learnedDocumentUsesTheSameVisibleHeadRoute() = withSurface { live, adapter, clean ->
        adapter.onAnimationEvent(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, now(), "fixture-doc", "text", 1))
        advance(350)
        assertTrue("Confirmed document intake must reach the head layer", differs(live, clean))
    }

    @Test fun finishingUnrelatedWorkDoesNotCancelADocumentArrival() = withSurface { live, adapter, clean ->
        live.setTaskEffectScope("fixture-work")
        adapter.onAnimationEvent(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, now(), "fixture-doc", "text", 1))
        advance(350)
        assertTrue(differs(live, clean))
        live.setTaskEffectScope(null)
        assertTrue("Document arrivals outlive unrelated work scopes", differs(live, clean))
        advance(2_000)
        assertFalse(differs(live, clean))
    }

    @Test fun repeatedEventCannotExtendTheEffect() = withSurface { live, adapter, clean ->
        val event = entry()
        adapter.onAnimationEvent(event)
        advance(350)
        assertTrue(differs(live, clean))
        advance(250)
        adapter.onAnimationEvent(event)
        advance(1_400)
        assertFalse("A replay cannot restart the icon sequence", differs(live, clean))
    }

    @Test fun staleFutureAndEmptyDocumentEventsStayInvisible() = withSurface { live, adapter, clean ->
        adapter.onAnimationEvent(entry(now() - 751))
        adapter.onAnimationEvent(entry(now() + 1))
        adapter.onAnimationEvent(CockpitAnimationEvent.DocumentLearned(MEMORY_TURN, now(), "empty", "text", 0))
        advance(350)
        assertFalse(differs(live, clean))
    }

    @Test fun pauseAndReducedMotionDropQueuedEffects() = withSurface { live, adapter, clean ->
        val event = entry()
        adapter.onAnimationEvent(event)
        advance(350)
        assertTrue(differs(live, clean))
        live.setForegroundActive(false)
        live.setForegroundActive(true)
        adapter.onAnimationEvent(event)
        advance(100)
        assertFalse("A pause clears and rejects old arrivals", differs(live, clean))
        live.setAnimationsEnabled(false)
        adapter.onAnimationEvent(entry())
        live.setAnimationsEnabled(true)
        advance(350)
        assertFalse("Reduced motion cannot queue a later burst", differs(live, clean))
    }
}
