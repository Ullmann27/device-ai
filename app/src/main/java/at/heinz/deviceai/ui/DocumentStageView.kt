package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.avatar3d.DocumentStageFrame

/** Output of the existing Filament engine. */
class DocumentStageView(
    context: Context,
    private val hologram: ContinuumHologramView,
    private val windowId: String
) : TextureView(context), TextureView.SurfaceTextureListener {
    var onReadyChanged: ((Boolean) -> Unit)? = null
    private var ownedSurface: Surface? = null
    private var frame: DocumentStageFrame? = null
    private var page: Bitmap? = null
    private var closed = false
    private var ready = false
    private var bufferWidth = 0
    private var bufferHeight = 0

    init {
        isOpaque = false
        alpha = 0f
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    fun present(value: DocumentStageFrame, bitmap: Bitmap?) {
        if (closed || value.windowId != windowId || !value.valid()) return
        frame = value
        page = bitmap?.takeUnless { it.isRecycled }
        resizeBuffer()
        hologram.presentDocumentStage(value, page, bufferWidth, bufferHeight)
        alpha = if (ready) value.opacity else 0f
    }

    private fun resizeBuffer() {
        val current = frame ?: return
        val size = current.bufferSize()
        if (size.first == bufferWidth && size.second == bufferHeight) return
        surfaceTexture?.setDefaultBufferSize(size.first, size.second)
        bufferWidth = size.first
        bufferHeight = size.second
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (closed) return
        bufferWidth = 0
        bufferHeight = 0
        resizeBuffer()
        val surface = Surface(texture)
        ownedSurface = surface
        hologram.attachDocumentStage(windowId, surface) { drawn ->
            if (!closed) {
                if (ready != drawn) { ready = drawn; onReadyChanged?.invoke(drawn) }
                alpha = if (drawn) frame?.opacity ?: 0f else 0f
            }
        }
        frame?.let { hologram.presentDocumentStage(it, page, bufferWidth, bufferHeight) }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        bufferWidth = 0
        resizeBuffer()
        frame?.let { hologram.presentDocumentStage(it, page, bufferWidth, bufferHeight) }
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        releaseSurface()
        return true
    }

    private fun releaseSurface() {
        hologram.detachDocumentStage(windowId)
        ownedSurface?.release()
        ownedSurface = null
        ready = false
        alpha = 0f
        onReadyChanged?.invoke(false)
    }

    fun close() {
        if (closed) return
        closed = true
        releaseSurface()
        frame = null
        page = null
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }
}
