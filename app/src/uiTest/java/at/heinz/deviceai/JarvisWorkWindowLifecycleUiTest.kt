package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.ui.JarvisWorkWindow
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
class JarvisWorkWindowLifecycleUiTest {
    private class DrawingClock(context: Context) : View(context) {
        val listeners = linkedSetOf<(Long, Float) -> Boolean>()
        var time = 0L
        var inDraw = false
        val source: ((Long, Float) -> Boolean) -> AutoCloseable = { listener ->
            listeners.add(listener)
            AutoCloseable { listeners.remove(listener) }
        }
        override fun onDraw(canvas: Canvas) {
            inDraw = true
            try { listeners.toList().forEach { it(time, 1f) } }
            finally { inDraw = false }
        }
    }

    private class Fixture {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val clock = DrawingClock(activity)
        val window = JarvisWorkWindow(activity, clock.source) {}
        init {
            root.addView(clock, FrameLayout.LayoutParams(-1, -1))
            root.addView(window, FrameLayout.LayoutParams(-1, -1))
            activity.setContentView(root)
            controller.visible()
            layout()
        }
        fun body(label: String) = TextView(activity).apply { text = label }
        fun open(body: View, closed: () -> Unit) = window.open(body,
            RectF(30f, 540f, 190f, 600f), RectF(220f, 440f, 820f, 700f),
            "Systemstatus", listOf(RectF(250f, 30f, 820f, 420f)), onClosed = closed)
        fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 884, 760)
        }
        fun draw(time: Long) {
            clock.time = time
            layout()
            val bitmap = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
            try { root.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
        }
        fun dispose() {
            window.dispose()
            shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().destroy()
        }
    }

    private fun withWindow(block: (Fixture) -> Unit) {
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.dispose() }
    }

    @Test fun finalDrawClosesLogicallyBeforeReleasingViewsAndCallingHost() = withWindow { f ->
        val body = f.body("Status ist vorhanden")
        var closed = 0
        var callbackDuringDraw = false
        var removalDuringDraw = false
        assertTrue(f.open(body) {
            closed++
            callbackDuringDraw = callbackDuringDraw || f.clock.inDraw
            if (!f.clock.inDraw) f.root.removeView(f.window)
        })
        val scroll = f.window.findViewWithTag<ScrollView>("jarvis-work-window:scroll")
        scroll.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View, child: View) = Unit
            override fun onChildViewRemoved(parent: View, child: View) {
                removalDuringDraw = removalDuringDraw || f.clock.inDraw
            }
        })
        f.draw(1_000)
        f.draw(2_000)
        f.window.close()
        f.draw(2_400)
        assertFalse(f.window.isShowing)
        assertEquals(View.GONE, f.window.visibility)
        assertTrue(f.clock.listeners.isEmpty())
        assertFalse("Draw must not change child hierarchy", removalDuringDraw)
        assertFalse("Host callback must leave draw", callbackDuringDraw)
        assertSame("Body stays until cleanup", scroll, body.parent)
        assertEquals(0, closed)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(body.parent)
        assertNull(f.window.parent)
        assertEquals(1, closed)
    }

    @Test fun reopeningBeforeCleanupCannotRemoveNewBodyOrRunStaleClose() = withWindow { f ->
        val first = f.body("Erster Status")
        val second = f.body("Neuer Status")
        var oldCloses = 0
        var newCloses = 0
        assertTrue(f.open(first) {
            oldCloses++
            if (!f.clock.inDraw) f.root.removeView(f.window)
        })
        f.draw(1_000)
        f.draw(2_000)
        f.window.close()
        f.draw(2_400)
        assertTrue(f.open(second) { newCloses++ })
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(f.window.isShowing)
        assertSame(f.root, f.window.parent)
        assertNull(first.parent)
        assertNotNull(second.parent)
        assertEquals("Superseded callback is cancelled", 0, oldCloses)
        assertEquals(1, f.clock.listeners.size)
        f.window.close(immediate = true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, newCloses)
        assertEquals(0, oldCloses)
        assertNull(second.parent)
    }

    @Test fun fractionalDestinationSettlesExactlyInsideSafePixelBounds() = withWindow { f ->
        val requested = RectF(220.35f, 440.75f, 820.9f, 700.2f)
        val keepOut = RectF(0f, 0f, 220.34f, 760f)
        assertTrue(f.window.open(f.body("Bruchteilige Zielbox"),
            RectF(30.25f, 540.3f, 190.65f, 600.4f), requested, "Systemstatus", listOf(keepOut)))
        f.draw(1_000)
        f.draw(2_000)
        val panel = f.window.findViewWithTag<View>("jarvis-work-window:panel")
        assertEquals(1f, panel.scaleX, 0f)
        assertEquals(1f, panel.scaleY, 0f)
        assertEquals(0f, panel.translationX, 0f)
        assertEquals(0f, panel.translationY, 0f)
        val actual = RectF(panel.left.toFloat(), panel.top.toFloat(), panel.right.toFloat(), panel.bottom.toFloat())
        assertTrue("Pixel bounds stay inside request", requested.contains(actual))
        assertFalse("Rounding preserves protected area", RectF.intersects(actual, keepOut))
        val close = f.window.findViewWithTag<View>("jarvis-work-window:close")
        val visible = Rect()
        assertTrue(close.getGlobalVisibleRect(visible))
        assertEquals(48, close.height)
        assertEquals("Close target stays unscaled", close.height, visible.height())
    }

    /**
     * Rotation/fold follow-up (06.11.2026), HOST: the first layout of a window can come at another size than the one it
     * was planned for (a rotation, fold or keyboard resize was still pending when the owner touched). The window may
     * not vanish without a trace: it is placed again inside the real size, clear of the protected area, with its body.
     */
    @Test fun firstLayoutAtAnotherSizePlansAgainInsteadOfVanishing() = withWindow { f ->
        val late = JarvisWorkWindow(f.activity, f.clock.source) {}
        f.root.addView(late, FrameLayout.LayoutParams(-1, -1))
        try {
            val face = RectF(250f, 30f, 820f, 420f)
            val body = f.body("Gedächtniszentrum")
            // Planned while the screen was still 760 × 884: its bottom (876) lies below the 760 px of the real layout.
            assertTrue(late.open(body, RectF(30f, 540f, 190f, 600f), RectF(60f, 554f, 700f, 876f),
                "Gedächtniszentrum", listOf(face)))
            f.layout()
            shadowOf(Looper.getMainLooper()).idleFor(32, java.util.concurrent.TimeUnit.MILLISECONDS)
            f.draw(32L)
            assertTrue("the window survives its first layout on another size", late.isShowing)
            assertTrue("with its body", generateSequence(body.parent) { it.parent }.any { it === late })
            val panel = late.javaClass.getDeclaredField("panel").apply { isAccessible = true }.get(late) as View
            val box = RectF(panel.left.toFloat(), panel.top.toFloat(), panel.right.toFloat(), panel.bottom.toFloat())
            assertTrue("inside the real size: $box", RectF(0f, 0f, 884f, 760f).contains(box))
            assertFalse("clear of the face: $box", RectF.intersects(box, face))
        } finally { late.dispose() }
    }

    /**
     * App crystal (owner brief 06.11.2026), HOST: an installed app's real icon rides the light trail inside a prism of
     * the cockpit colours (violet/magenta and cyan facets); a document page keeps its plain glass disc. Same frame,
     * same route – only the crystal flag differs.
     */
    @Test fun anInstalledAppRidesTheTrailInsideItsCrystal() {
        fun frame(crystal: Boolean): IntArray {
            val pixels = IntArray(884 * 760)
            withWindow { f ->
                val icon = android.graphics.drawable.BitmapDrawable(f.activity.resources,
                    Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF00C853.toInt()) })
                assertTrue(f.window.open(f.body("App"), RectF(30f, 540f, 190f, 600f), RectF(220f, 440f, 820f, 700f),
                    "APP · Test", listOf(RectF(250f, 30f, 820f, 420f)), emblem = icon, crystal = crystal))
                f.draw(1_000)
                f.clock.time = 1_060
                f.layout()
                val bitmap = Bitmap.createBitmap(884, 760, Bitmap.Config.ARGB_8888)
                try {
                    f.root.draw(Canvas(bitmap))
                    bitmap.getPixels(pixels, 0, 884, 0, 0, 884, 760)
                    // HOST evidence: before (glass disc) / after (crystal), same frame and route.
                    System.getProperty("continuum.ui.evidenceDir")?.let { dir ->
                        java.io.File(dir).apply { mkdirs() }.resolve(if (crystal) "crystal-after.png" else "crystal-before-disc.png")
                            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                } finally { bitmap.recycle() }
            }
            return pixels
        }
        fun r(c: Int) = (c shr 16) and 255
        fun g(c: Int) = (c shr 8) and 255
        fun b(c: Int) = c and 255
        fun green(px: IntArray) = px.count { g(it) > r(it) + 20 && g(it) > b(it) + 10 }
        val disc = frame(crystal = false)
        val crystal = frame(crystal = true)
        assertTrue("the real icon is drawn in both: ${green(disc)} / ${green(crystal)}", green(disc) > 10 && green(crystal) > 10)
        val facets = disc.indices.count { i ->
            val c = crystal[i]; val d = disc[i]
            val changed = kotlin.math.abs(r(c) - r(d)) + kotlin.math.abs(g(c) - g(d)) + kotlin.math.abs(b(c) - b(d)) > 12
            changed && b(c) > g(c) + 15 && r(c) > g(c) + 5
        }
        assertTrue("the crystal adds violet/magenta facets around the icon: $facets px", facets > 30)
    }

    /**
     * PR #572 (fold-short 840×320, 200 % font), HOST: the window title wrapped onto four lines, its header took 157 of
     * 270 px and left a 101 px scroll viewport – less than one 128 px action, which therefore could never be shown or
     * tapped whole. A long title may wrap, but never so far that the tallest control no longer fits the viewport.
     */
    @Test fun largeTypeTitleNeverStarvesTheScrollViewportOfItsTallestControl() {
        val previousFont = org.robolectric.RuntimeEnvironment.getFontScale()
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        try {
            withWindow { f ->
                val action = TextView(f.activity).apply { text = "CODING-AUFTRAG ENTWERFEN"; isClickable = true }
                val body = android.widget.LinearLayout(f.activity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    addView(TextView(f.activity).apply { text = "Entwicklungszentrum" }, -1, 120)
                    addView(action, -1, 128)
                    addView(TextView(f.activity).apply { text = "Weitere Werkzeuge" }, -1, 600)
                }
                assertTrue(f.window.open(body, RectF(30f, 540f, 190f, 600f), RectF(561f, 20f, 840f, 290f),
                    "Entwicklungszentrum"))
                f.draw(1_000)
                f.draw(2_000)
                val scroll = f.window.findViewWithTag<ScrollView>("jarvis-work-window:scroll")
                val viewport = scroll.height - scroll.paddingTop - scroll.paddingBottom
                assertTrue("scroll viewport $viewport px must hold the 128 px action", viewport >= action.height)
                scroll.scrollTo(0, action.top + (action.parent as View).top)
                f.draw(2_016)
                val visible = Rect()
                assertTrue(action.getGlobalVisibleRect(visible))
                assertEquals("the whole action is reachable: $visible", action.height, visible.height())
                val title = f.window.findViewWithTag<View>("jarvis-work-window:panel").let { panel ->
                    (panel as ViewGroup).getChildAt(0).let { header -> ((header as ViewGroup).getChildAt(0) as TextView) }
                }
                assertEquals("the full title stays the window's text", "Entwicklungszentrum", title.text.toString())
            }
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(previousFont) }
    }
}
