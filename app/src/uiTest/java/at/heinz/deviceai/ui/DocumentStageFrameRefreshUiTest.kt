package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.avatar3d.DocumentStageFrame
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Synthetic acknowledgement; no GPU. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DocumentStageFrameRefreshUiTest {
    @Test fun validMotionKeepsReadinessButClippingAndNewBitmapRevokeIt() {
        val context: Context = RuntimeEnvironment.getApplication()
        val hologram = ContinuumHologramView(context).apply {
            setForegroundActive(false)
            setAnimationsEnabled(false)
        }
        val surfaceTexture = SurfaceTexture(0)
        val surface = Surface(surfaceTexture)
        val page = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val replacement = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val ready = mutableListOf<Boolean>()
        val frame = DocumentStageFrame("event-page", 884, 760, 160f, 420f, 660f, 280f,
            0f, 0f, 1f, false, pageLeft = .1f, pageTop = .2f, pageWidth = .2f, pageHeight = .5f,
            pageRectValid = true)
        try {
            hologram.attachDocumentStage(frame.windowId, surface) { ready += it }
            val binding = requireNotNull(ContinuumHologramView::class.java.getDeclaredField("documentStageBinding")
                .apply { isAccessible = true }.get(hologram))
            val report = binding.javaClass.getDeclaredMethod("report", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
            hologram.presentDocumentStage(frame, page, 884, 760)
            report.invoke(binding, true)
            hologram.presentDocumentStage(frame.copy(), page, 884, 760)
            val scrolled = frame.copy(pageTop = .15f)
            hologram.presentDocumentStage(scrolled, page, 884, 760)
            assertEquals("valid flight stays visible", listOf(true), ready)
            val stored = binding.javaClass.getDeclaredField("frame").apply { isAccessible = true }.get(binding)
            assertEquals(scrolled, stored)
            hologram.presentDocumentStage(scrolled.copy(pageRectValid = false), page, 884, 760)
            assertEquals(listOf(true, false), ready)
            hologram.presentDocumentStage(scrolled, page, 884, 760)
            assertEquals("recovery awaits a rendered frame", listOf(true, false), ready)
            report.invoke(binding, true)
            hologram.presentDocumentStage(scrolled, replacement, 884, 760)
            assertEquals(listOf(true, false, true, false), ready)
            report.invoke(binding, true)
            hologram.detachDocumentStage(frame.windowId)
            assertEquals(listOf(true, false, true, false, true, false), ready)
        } finally {
            hologram.detachDocumentStage(frame.windowId)
            surface.release()
            surfaceTexture.release()
            page.recycle()
            replacement.recycle()
        }
    }
}
