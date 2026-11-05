package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisWorkWindowUiTest {
    private class Frames {
        val listeners = linkedSetOf<(Long, Float) -> Boolean>()
        var requests = 0
        val source: ((Long, Float) -> Boolean) -> AutoCloseable = { listener ->
            listeners.add(listener)
            AutoCloseable { listeners.remove(listener) }
        }
        fun at(time: Long, scale: Float = 1f): Boolean =
            listeners.toList().map { it(time, scale) }.any { it }
    }

    private fun implementation(name: String): Class<*> = try {
        Class.forName("at.heinz.deviceai.ui.$name")
    } catch (missing: ClassNotFoundException) {
        throw AssertionError("Missing in-cockpit $name implementation", missing)
    }

    private inner class Fixture {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val frames = Frames()
        val root = FrameLayout(activity)
        var cockpitClicks = 0
        val cockpit = View(activity).apply { setOnClickListener { cockpitClicks++ } }
        val window = implementation("JarvisWorkWindow")
            .getConstructor(Context::class.java, Function1::class.java, Function0::class.java)
            .newInstance(activity, frames.source, { frames.requests++ }) as FrameLayout
        val origin = RectF(80f, 530f, 220f, 600f)
        val target = RectF(240f, 440f, 824f, 700f)
        val protectedBounds = listOf(RectF(250f, 30f, 820f, 420f))
        var opened = 0
        var closed = 0
        init {
            root.addView(cockpit, FrameLayout.LayoutParams(-1, -1))
            root.addView(window, FrameLayout.LayoutParams(-1, -1))
            activity.setContentView(root)
            controller.visible()
            layout()
        }
        fun body(value: String = "Echte Statusdaten") = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            repeat(24) { addView(TextView(activity).apply {
                text = "$value $it"
                minHeight = 36
            }) }
        }
        fun open(body: View): Boolean = window.javaClass.getMethod("open", View::class.java,
            RectF::class.java, RectF::class.java, String::class.java, List::class.java,
            Function0::class.java, Function0::class.java, Int::class.javaPrimitiveType)
            .invoke(window, body, origin, target, "Systemstatus", protectedBounds,
                { opened++ }, { closed++ }, at.heinz.deviceai.ui.JarvisDesign.Colors.cyan) as Boolean
        fun close(immediate: Boolean = false) {
            window.javaClass.getMethod("close", Boolean::class.javaPrimitiveType).invoke(window, immediate)
        }
        fun showing() = window.javaClass.getMethod("isShowing").invoke(window) as Boolean
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 884, 760)
        }
        fun frame(time: Long, scale: Float = 1f): Boolean {
            val active = frames.at(time, scale)
            shadowOf(Looper.getMainLooper()).idle()
            layout()
            return active
        }
        fun tap(x: Float, y: Float) {
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 1, action, x, y, 0)
                root.dispatchTouchEvent(event)
                event.recycle()
            }
            shadowOf(Looper.getMainLooper()).idle()
        }
        fun dispose() {
            window.javaClass.getMethod("dispose").invoke(window)
            controller.pause().stop().destroy()
        }
    }

    private fun withWindow(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.dispose() }
    }

    @Test fun suppliedFramesOpenExistingBodyAndKeepCloseOutsideScroll() = withWindow { f ->
        val body = f.body()
        assertTrue(f.open(body))
        assertEquals(1, f.frames.listeners.size)
        assertTrue(f.frames.requests > 0)
        assertTrue(f.frame(1_000))
        assertTrue(f.frame(1_250))
        val panel = f.window.findViewWithTag<View>("jarvis-work-window:panel")
        assertTrue(panel.scaleX > f.origin.width() / f.target.width())
        assertTrue(panel.scaleX < 1f)
        assertFalse(f.frame(2_500))
        assertEquals(1, f.opened)
        assertTrue(f.showing())
        assertEquals(1f, panel.scaleX, .001f)
        val scroll = f.window.findViewWithTag<ScrollView>("jarvis-work-window:scroll")
        assertSame(body, scroll.getChildAt(0))
        val close = f.window.findViewWithTag<TextView>("jarvis-work-window:close")
        assertEquals("SCHLIESSEN", close.text.toString())
        assertTrue(close.height >= 48)
        assertNotSame(scroll, close.parent)
        val headerTop = close.top
        scroll.scrollTo(0, 300)
        assertEquals(headerTop, close.top)
        close.performClick()
        assertTrue(f.showing())
        assertTrue(f.frame(2_600))
        assertFalse(f.frame(4_000))
        assertFalse(f.showing())
        assertNull(body.parent)
        assertEquals(1, f.closed)
        assertEquals(0, f.frames.listeners.size)
    }

    @Test fun reducedMotionSettlesBothDirectionsOnNextSuppliedFrame() = withWindow { f ->
        assertTrue(f.open(f.body()))
        assertFalse(f.frame(40, 0f))
        assertEquals(1, f.opened)
        assertTrue(f.showing())
        f.close()
        assertFalse(f.frame(41, 0f))
        assertFalse(f.showing())
        assertEquals(1, f.closed)
        assertTrue(f.frames.listeners.isEmpty())
    }

    @Test fun immediateCloseReleasesBodyAndCallbackExactlyOnce() = withWindow { f ->
        val body = f.body()
        assertTrue(f.open(body))
        f.frame(1_000)
        val stale = f.frames.listeners.single()
        f.close(immediate = true)
        f.close(immediate = true)
        assertFalse(f.showing())
        assertNull(body.parent)
        assertEquals(1, f.closed)
        assertEquals(0, f.opened)
        assertFalse(stale(9_000, 1f))
        assertTrue(f.frames.listeners.isEmpty())
    }

    @Test fun outsideAndProtectedTouchesReachCockpit() = withWindow { f ->
        assertTrue(f.open(f.body()))
        f.frame(1_000, 0f)
        f.tap(20f, 730f)
        f.tap(500f, 250f)
        assertEquals(2, f.cockpitClicks)
        assertTrue(f.showing())
    }

    @Test fun protectedAreaRemainsTransparentDuringOpening() = withWindow { f ->
        f.origin.set(20f, 50f, 180f, 100f)
        assertTrue(f.open(f.body()))
        f.frame(1_000)
        for (time in listOf(1_080L, 1_180L, 1_360L, 1_650L)) {
            f.frame(time)
            val bitmap = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
            try {
                f.window.draw(Canvas(bitmap))
                for (y in 40 until 410 step 11) for (x in 260 until 810 step 11) {
                    assertEquals("protected pixel $x,$y at $time", Color.TRANSPARENT, bitmap.getPixel(x, y))
                }
            } finally { bitmap.recycle() }
        }
    }

    @Test fun detachingReleasesFrameSubscription() = withWindow { f ->
        val body = f.body()
        assertTrue(f.open(body))
        f.frame(1_000)
        f.root.removeView(f.window)
        assertTrue(f.frames.listeners.isEmpty())
        assertNull(body.parent)
        assertFalse(f.showing())
        assertEquals(1, f.closed)
    }

    @Test fun geometryFindsPhoneAndFoldSpaceWithoutCoveringHeadOrDialog() {
        val geometry = implementation("JarvisWorkWindowGeometry")
        val instance = geometry.getField("INSTANCE").get(null)
        val destination = geometry.getMethod("destination", RectF::class.java, List::class.java,
            Float::class.javaPrimitiveType)
        for ((viewport, protectedBounds) in listOf(
            RectF(0f, 0f, 884f, 710f) to listOf(RectF(210f, 70f, 850f, 420f)),
            RectF(0f, 0f, 412f, 853f) to listOf(RectF(8f, 74f, 404f, 493f), RectF(8f, 503f, 404f, 633f))
        )) {
            val result = destination.invoke(instance, viewport, protectedBounds, 1f) as RectF?
            assertNotNull(result)
            result!!
            assertTrue(viewport.contains(result))
            assertTrue(result.width() >= 200f)
            assertTrue(result.height() >= 160f)
            protectedBounds.forEach { assertFalse(RectF.intersects(it, result)) }
        }
        val blocked = RectF(0f, 0f, 412f, 853f)
        assertNull(destination.invoke(instance, blocked, listOf(blocked), 1f))
    }
}
