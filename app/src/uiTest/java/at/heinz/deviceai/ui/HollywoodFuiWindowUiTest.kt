package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class HollywoodFuiWindowUiTest {
    private class Fixture {
        val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val listeners = linkedSetOf<(Long, Float) -> Boolean>()
        val overlay = HollywoodFuiStageOverlay(activity)
        val window = JarvisWorkWindow(activity, { listener ->
            listeners.add(listener)
            AutoCloseable { listeners.remove(listener) }
        }, {})
        val origin = RectF(40f, 260f, 100f, 300f)
        val target = RectF(420f, 120f, 840f, 660f)
        val protected = listOf(RectF(120f, 20f, 400f, 420f))
        val body = TextView(activity).apply { text = "Echte Daten bleiben nativ"; textSize = 20f }
        var closed = 0
        init {
            root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            root.addView(window, FrameLayout.LayoutParams(-1, -1))
            activity.setContentView(root)
            window.onCinematicFrame = overlay::present
            assertTrue(window.open(body, origin, target, "Dokument", protected, onClosed = {
                closed++
                root.removeView(overlay)
            }))
            overlay.open(target, JarvisActionScenes.forDocument(), origin, protected)
            layout()
        }
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 884, 760)
        }
        fun frame(now: Long, scale: Float = 1f): Boolean {
            val live = listeners.toList().map { it(now, scale) }.any { it }
            shadowOf(Looper.getMainLooper()).idle()
            layout()
            return live
        }
        val panel: View get() = window.findViewWithTag("jarvis-work-window:panel")
        fun dispose() { window.dispose(); controller.pause().stop().destroy() }
    }

    private fun fixture(block: (Fixture) -> Unit) {
        val f = Fixture()
        try { block(f) } finally { f.dispose() }
    }

    @Test fun contentWaitsForGlassAndOnlySuppliedFramesAdvanceConstruction() = fixture { f ->
        f.frame(1_000L)
        f.frame(1_400L)
        assertEquals(0f, f.panel.alpha, .001f)
        val scale = f.panel.scaleX
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(scale, f.panel.scaleX, .001f)
        f.frame(1_700L)
        assertTrue(f.panel.alpha in .01f.. .99f)
        f.frame(1_900L)
        assertEquals(1f, f.panel.alpha, .001f)
        assertEquals(1, f.listeners.size)
        assertTrue(f.root.indexOfChild(f.overlay) < f.root.indexOfChild(f.window))
    }

    @Test fun lateIdleCloseGetsItsFullChoreographyAndReleasesExactlyOnce() = fixture { f ->
        f.frame(1_000L)
        f.frame(1_900L)
        f.window.close()
        assertTrue(f.frame(90_000L))
        assertTrue(f.frame(90_300L))
        assertEquals(0f, f.panel.alpha, .001f)
        assertFalse(f.frame(90_420L))
        assertEquals(1, f.closed)
        assertTrue(f.listeners.isEmpty())
        assertNull(f.body.parent)
    }

    @Test fun reducedMotionAndFallbackPreserveContentWithoutTiltOrLiveFrames() = fixture { f ->
        f.overlay.setWorkActive(true)
        assertFalse(f.frame(1_000L, 0f))
        assertEquals(1f, f.panel.alpha, .001f)
        assertEquals(0f, f.panel.rotationY, .001f)
        f.overlay.cinematicEffectsEnabled = false
        f.window.cinematicEffectsEnabled = false
        assertFalse(f.frame(1_001L))
        assertNotNull(f.body.parent)
        val image = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
        try {
            f.overlay.draw(Canvas(image))
            assertTrue(IntArray(884 * 760).also { image.getPixels(it, 0, 884, 0, 0, 884, 760) }
                .all { it == Color.TRANSPARENT })
        } finally { image.recycle() }
    }

    @Test fun decorationNeverEntersProtectedAvatarAndInputBounds() = fixture { f ->
        for (now in listOf(1_000L, 1_140L, 1_320L, 1_560L, 1_760L, 1_900L)) {
            f.frame(now)
            val image = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
            try {
                f.overlay.draw(Canvas(image))
                for (y in 30 until 410 step 13) for (x in 130 until 390 step 13)
                    assertEquals(Color.TRANSPARENT, image.getPixel(x, y))
            } finally { image.recycle() }
        }
    }

    @Test fun hotBudgetStopAndDetachCannotRequestDecorativeFrames() = fixture { f ->
        f.frame(1_000L)
        f.overlay.setWorkActive(true)
        f.overlay.setRenderBudget(JarvisRenderQuality.resolve(true, false, 3, false, true))
        assertFalse(f.frame(1_900L))
        f.overlay.setRenderBudget(JarvisRenderQuality.resolve(true, false, 0, false, false))
        f.overlay.setWorkActive(false)
        assertFalse(f.frame(2_000L))
        f.window.close(immediate = true)
        assertEquals(1, f.closed)
        assertTrue(f.listeners.isEmpty())
    }
}
