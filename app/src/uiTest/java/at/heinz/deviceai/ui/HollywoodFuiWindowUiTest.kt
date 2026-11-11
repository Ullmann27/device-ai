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
import org.robolectric.RobolectricTestRunner
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

    @Test fun sustainedEnergyUsesOrbBoundsAndStopsWithActualWork() = fixture { f ->
        f.overlay.setEnergySource(RectF(40f, 460f, 100f, 500f))
        f.frame(1_000L)
        fun corridorPixels(): Int {
            val image = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
            return try {
                f.overlay.draw(Canvas(image))
                var visible = 0
                for (y in 450 until 540) for (x in 105 until 350)
                    if (Color.alpha(image.getPixel(x, y)) > 10) visible++
                visible
            } finally { image.recycle() }
        }
        f.frame(6_000L)
        assertEquals(0, corridorPixels())
        f.overlay.setWorkActive(true)
        assertTrue(f.frame(6_000L))
        assertTrue("orb-to-window flow survives construction", corridorPixels() > 100)
        f.overlay.setEnergySource(RectF(40f, 560f, 100f, 600f))
        f.frame(6_000L)
        assertEquals("refit moves the energy origin", 0, corridorPixels())
        f.overlay.setEnergySource(RectF(40f, 460f, 100f, 500f))
        f.overlay.setWorkActive(false)
        assertFalse(f.frame(6_000L))
        assertEquals("result or STOP ends the flow", 0, corridorPixels())
        f.overlay.setWorkActive(true)
        f.window.close()
        f.frame(6_001L)
        assertEquals("closing cannot keep working light", 0, corridorPixels())
    }

    @Test fun gazeTracksTheActualPerspectiveOnlyOnSuppliedFrames() = fixture { f ->
        val positions = mutableListOf<Pair<Float, Float>>()
        f.window.onPresentedCenter = { x, y -> positions += x to y }
        f.frame(1_000L)
        f.frame(1_500L)
        val panel = f.panel
        val point = floatArrayOf(panel.width * .5f, panel.height * .5f)
        panel.matrix.mapPoints(point)
        val flying = positions.last()
        assertEquals(panel.left + point[0], flying.first, .001f)
        assertEquals(panel.top + point[1], flying.second, .001f)
        assertTrue(kotlin.math.abs(flying.first - f.target.centerX()) > 10f)
        val affineX = panel.left + panel.translationX + panel.width * panel.scaleX * .5f
        assertTrue("native perspective, not just affine bounds", kotlin.math.abs(affineX - flying.first) > .01f)
        val count = positions.size
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(count, positions.size)
        f.frame(1_900L)
        assertEquals(f.target.centerX(), positions.last().first, .001f)
        assertEquals(f.target.centerY(), positions.last().second, .001f)
        f.window.close(immediate = true)
        val closedCount = positions.size
        f.window.reportPresentedCenter()
        assertEquals(closedCount, positions.size)
    }

    @Test fun reducedMotionAndLiveFallbackReportTheDockedCenter() = fixture { f ->
        val positions = mutableListOf<Pair<Float, Float>>()
        f.window.onPresentedCenter = { x, y -> positions += x to y }
        f.frame(1_000L)
        f.frame(1_400L)
        f.window.cinematicEffectsEnabled = false
        assertEquals(f.target.centerX(), positions.last().first, .001f)
        assertEquals(f.target.centerY(), positions.last().second, .001f)
        f.frame(1_500L, 0f)
        assertEquals(f.target.centerX(), positions.last().first, .001f)
    }
}
