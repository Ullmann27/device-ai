package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi")
class DocumentStagePageGeometryUiTest {
    private class Fixture(width: Int, height: Int, bitmapWidth: Int, bitmapHeight: Int) {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        val panel = FrameLayout(context)
        val scroll = ScrollView(context)
        private val body = FrameLayout(context)
        val page = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888))
        }
        init {
            panel.addView(scroll, FrameLayout.LayoutParams(644, 220).apply { leftMargin = 8; topMargin = 52 })
            scroll.addView(body, FrameLayout.LayoutParams(644, 500))
            body.addView(page, FrameLayout.LayoutParams(width, height).apply { leftMargin = 16; topMargin = 12 })
            panel.measure(View.MeasureSpec.makeMeasureSpec(660, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY))
            panel.layout(0, 0, 660, 280)
        }
        fun rect(): RectF? = RectF().takeIf { DocumentStagePageGeometry.fittedRect(page, panel, it) }
    }

    @Test fun fullPageFitsActualNativeImageAndNestedOffsets() {
        val f = Fixture(240, 200, 300, 400)
        val rect = requireNotNull(f.rect())
        assertEquals(69f / 660f, rect.left, .00001f)
        assertEquals(64f / 280f, rect.top, .00001f)
        assertEquals(150f / 660f, rect.width(), .00001f)
        assertEquals(200f / 280f, rect.height(), .00001f)
        f.panel.rotationY = 14f
        f.panel.scaleX = .78f
        assertEquals("panel transform applied separately", rect, f.rect())
    }

    @Test fun resultSourceUsesIts44By58SlotAndScrollPosition() {
        val f = Fixture(44, 58, 300, 400)
        val rect = requireNotNull(f.rect())
        assertEquals(24.25f / 660f, rect.left, .00001f)
        assertEquals(64f / 280f, rect.top, .00001f)
        assertEquals(43.5f / 660f, rect.width(), .00001f)
        assertEquals(58f / 280f, rect.height(), .00001f)
        f.scroll.scrollTo(0, 8)
        assertEquals(56f / 280f, requireNotNull(f.rect()).top, .00001f)
    }

    @Test fun clippedOrUnrelatedPageCannotEscapeNativeContent() {
        val f = Fixture(44, 58, 300, 400)
        f.scroll.scrollTo(0, 30)
        assertNull(f.rect())
        f.scroll.scrollTo(0, 0)
        assertNotNull(f.rect())
        f.page.scaleType = ImageView.ScaleType.CENTER_CROP
        assertNull(f.rect())
        f.page.scaleType = ImageView.ScaleType.FIT_CENTER
        val other = FrameLayout(f.panel.context).apply { layout(0, 0, 660, 280) }
        assertFalse(DocumentStagePageGeometry.fittedRect(f.page, other, RectF()))
    }
}
