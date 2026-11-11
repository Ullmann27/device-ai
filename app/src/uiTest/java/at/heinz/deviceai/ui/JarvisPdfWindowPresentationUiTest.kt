package at.heinz.deviceai.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class JarvisPdfWindowPresentationUiTest {
    @Test fun documentAndResultEnterFromOppositeScreenEdges() {
        assertEquals(JarvisActionScenes.Origin.Edge(JarvisActionScenes.EdgeSide.LEFT),
            JarvisActionScenes.forDocument().origin)
        assertEquals(JarvisActionScenes.Origin.Edge(JarvisActionScenes.EdgeSide.RIGHT),
            JarvisActionScenes.forDocumentResult().origin)
        for ((width, height) in listOf(384f to 840f, 884f to 760f, 1280f to 800f)) {
            for (density in listOf(1f, 2.5f)) {
                val viewport = RectF(16f, 24f, 16f + width * density, 24f + height * density)
                val target = RectF(viewport.left + 12f * density, viewport.top + height * density * .58f,
                    viewport.right - 12f * density, viewport.bottom - 12f * density)
                val left = requireNotNull(JarvisWorkWindowGeometry.edgeOrigin(viewport, target, density,
                    JarvisActionScenes.EdgeSide.LEFT))
                val right = requireNotNull(JarvisWorkWindowGeometry.edgeOrigin(viewport, target, density,
                    JarvisActionScenes.EdgeSide.RIGHT))
                assertTrue("document starts completely outside", left.right < viewport.left)
                assertTrue("result starts completely outside", right.left > viewport.right)
                assertEquals(target.centerY(), left.centerY(), .001f)
                assertEquals(target.centerY(), right.centerY(), .001f)
                assertTrue("arrival retains substantial page size", left.width() > target.width() * .7f)
                assertEquals(left.width(), right.width(), .001f)
            }
        }
    }

    @Test fun offscreenDocumentTrailEntersAtVisibleEdgeAndAvoidsProtectedHead() {
        val viewport = RectF(0f, 0f, 884f, 760f)
        val target = RectF(160f, 450f, 820f, 700f)
        val head = RectF(200f, 20f, 650f, 390f)
        for (side in JarvisActionScenes.EdgeSide.values()) {
            val origin = requireNotNull(JarvisWorkWindowGeometry.edgeOrigin(viewport, target, 1f, side))
            val route = JarvisWorkWindowGeometry.route(origin, target, viewport, listOf(head), 8f)
            assertTrue(route.size >= 2)
            assertEquals(if (side == JarvisActionScenes.EdgeSide.LEFT) 0f else 884f, route.first().x, .001f)
            assertEquals(target.centerX(), route.last().x, .001f)
            assertEquals(target.centerY(), route.last().y, .001f)
            assertTrue(route.all { it.y > head.bottom })
        }
        assertNull(JarvisWorkWindowGeometry.edgeOrigin(viewport, target, Float.NaN,
            JarvisActionScenes.EdgeSide.LEFT))
        assertNull(JarvisWorkWindowGeometry.edgeOrigin(RectF(), target, 1f,
            JarvisActionScenes.EdgeSide.RIGHT))
        assertTrue(JarvisWorkWindowGeometry.route(RectF(Float.NaN, 0f, 1f, 1f), target,
            viewport, emptyList(), 8f).isEmpty())
    }

    private class Fixture : AutoCloseable {
        private val controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val activity = controller.get()
        val root = FrameLayout(activity)
        val listeners = linkedSetOf<(Long, Float) -> Boolean>()
        var inFrame = false
        val window = JarvisWorkWindow(activity, { listener ->
            listeners += listener
            AutoCloseable { listeners -= listener }
        }) {}
        init {
            root.addView(window, FrameLayout.LayoutParams(-1, -1))
            activity.setContentView(root)
            layout()
        }
        private fun layout() {
            root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 884, 760)
        }
        fun open(label: String = "Synthetischer Entwurf", body: View? = null) {
            assertTrue(window.open(body ?: TextView(activity).apply { text = label },
                RectF(900f, 450f, 1380f, 650f), RectF(160f, 420f, 820f, 700f), "ENTWURF"))
            layout()
        }
        fun frame(time: Long, scale: Float = 1f) {
            inFrame = true
            try { listeners.toList().forEach { it(time, scale) } }
            finally { inFrame = false }
        }
        override fun close() {
            window.dispose()
            shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().destroy()
        }
    }

    @Test fun presentationCallbackWaitsForArrivalAndLeavesFrameTraversal() = Fixture().use { f ->
        f.open()
        var delivered = 0
        var duringFrame = false
        assertTrue(f.window.doOnPresented { delivered++; duringFrame = f.inFrame })
        f.frame(1_000)
        f.frame(1_899)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, delivered)
        f.frame(1_900)
        assertEquals("callback leaves draw traversal", 0, delivered)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, delivered)
        assertFalse(duringFrame)
        f.frame(2_300)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, delivered)
        assertTrue(f.window.doOnPresented { delivered++ })
        assertEquals("already presented can proceed immediately", 2, delivered)
    }

    @Test fun closeBeforeQueuedDeliveryCannotSpeakForAnOldWindow() = Fixture().use { f ->
        f.open()
        var delivered = 0
        assertTrue(f.window.doOnPresented { delivered++ })
        f.frame(1_000)
        f.frame(1_900)
        f.window.close(immediate = true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, delivered)
        assertFalse(f.window.doOnPresented { delivered++ })
        f.open("Neuer Auftrag")
        f.frame(2_000, 0f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, delivered)
    }

    @Test fun meshFrameUsesTheNativeWindowTransformAndCanonicalProgress() = Fixture().use { f ->
        f.open()
        f.frame(1_000)
        f.frame(1_320)
        val panel = f.window.findViewWithTag<View>("jarvis-work-window:panel")
        val moving = requireNotNull(f.window.documentStageFrame("test-document", true))
        assertEquals("test-document", moving.windowId)
        assertEquals(884, moving.viewportWidth)
        assertEquals(760, moving.viewportHeight)
        assertEquals(panel.left + panel.translationX, moving.left, .001f)
        assertEquals(panel.top + panel.translationY, moving.top, .001f)
        assertEquals(panel.width * panel.scaleX, moving.width, .001f)
        assertEquals(panel.height * panel.scaleY, moving.height, .001f)
        assertEquals(panel.rotationY, moving.yawDegrees, .001f)
        assertEquals(panel.cameraDistance, moving.cameraDistance, .001f)
        assertTrue("right entry is still moving", moving.left > 160f && moving.yawDegrees > 0f)
        assertTrue("mesh is visible before native text", moving.opacity > 0f && panel.alpha == 0f)
        f.frame(1_900)
        val arrived = requireNotNull(f.window.documentStageFrame("test-document", true))
        assertEquals(160f, arrived.left, .001f)
        assertEquals(660f, arrived.width, .001f)
        assertEquals(0f, arrived.yawDegrees, .001f)
        f.window.close(immediate = true)
        assertNull(f.window.documentStageFrame("test-document", true))
    }

    @Test fun replacementAndDetachRevokePendingPresentationCallbacks() = Fixture().use { f ->
        f.open()
        var delivered = 0
        assertTrue(f.window.doOnPresented { delivered++ })
        assertTrue(f.window.replaceContent(TextView(f.activity).apply { text = "Neue Arbeitsfläche" }, "NEU"))
        f.frame(1_000, 0f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, delivered)
        f.window.close(immediate = true)
        f.open()
        assertTrue(f.window.doOnPresented { delivered++ })
        f.frame(2_000, 0f)
        f.root.removeView(f.window)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, delivered)
        assertFalse(f.window.isShowing)
    }

    private fun Fixture.pageBody(): Pair<FrameLayout, ImageView> {
        val page = ImageView(activity).apply {
            setImageBitmap(Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888))
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageAlpha = 197
            contentDescription = "Synthetische PDF-Seite"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val body = FrameLayout(activity).apply {
            addView(page, FrameLayout.LayoutParams(44, 58))
        }
        return body to page
    }

    @Test fun readyMeshHidesOnlyNativePixelsAndRestoresThemOnLoss() = Fixture().use { f ->
        val (body, page) = f.pageBody()
        f.open(body = body)
        f.frame(1_000)
        f.frame(1_900)
        val bounds = requireNotNull(f.window.shownBounds())
        val drawable = page.drawable
        assertFalse(f.window.setDocumentStageReady(page, false))
        assertEquals(197, page.imageAlpha)
        assertTrue(f.window.setDocumentStageReady(page, true))
        assertTrue(f.window.documentStageReady)
        assertEquals(0, page.imageAlpha)
        assertEquals(View.VISIBLE, page.visibility)
        assertEquals(1f, page.alpha, 0f)
        assertEquals("Synthetische PDF-Seite", page.contentDescription)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, page.importantForAccessibility)
        assertSame(drawable, page.drawable)
        assertEquals(bounds, f.window.shownBounds())
        assertTrue(f.window.findViewWithTag<View>("jarvis-work-window:close").isClickable)
        assertFalse(f.window.setDocumentStageReady(page, false))
        assertEquals(197, page.imageAlpha)
        page.translationY = 500f
        assertFalse("clipped native page stays visible", f.window.setDocumentStageReady(page, true))
        assertEquals(197, page.imageAlpha)
    }

    @Test fun closeReplacementAndDetachRestorePixelsBeforeContentLeaves() = Fixture().use { f ->
        val (body, page) = f.pageBody()
        f.open(body = body)
        f.frame(1_000)
        f.frame(1_900)
        assertTrue(f.window.setDocumentStageReady(page, true))
        f.window.close()
        assertEquals(197, page.imageAlpha)
        assertFalse("late ready cannot hide closing content", f.window.setDocumentStageReady(page, true))
        f.window.close(immediate = true)
        shadowOf(Looper.getMainLooper()).idle()
        val (replacement, secondPage) = f.pageBody()
        f.open(body = replacement)
        f.frame(2_000)
        f.frame(2_900)
        assertTrue(f.window.setDocumentStageReady(secondPage, true))
        assertTrue(f.window.replaceContent(TextView(f.activity).apply { text = "Anderer Inhalt" }, "NEU"))
        assertEquals(197, secondPage.imageAlpha)
        assertFalse(f.window.documentStageReady)
        f.window.close(immediate = true)
        shadowOf(Looper.getMainLooper()).idle()
        val (lastBody, lastPage) = f.pageBody()
        f.open(body = lastBody)
        f.frame(3_000)
        f.frame(3_900)
        assertTrue(f.window.setDocumentStageReady(lastPage, true))
        f.root.removeView(f.window)
        assertEquals(197, lastPage.imageAlpha)
        assertFalse(f.window.documentStageReady)
    }
}
