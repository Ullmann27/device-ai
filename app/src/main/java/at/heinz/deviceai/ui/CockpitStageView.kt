package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.avatar3d.CockpitStageFrame

/** Surface on the existing avatar clock. */
internal class CockpitStageView(context: Context, private val hologram: ContinuumHologramView) :
    TextureView(context), TextureView.SurfaceTextureListener {
    private var ownedSurface: Surface? = null
    private var frame: CockpitStageFrame? = null
    private var bufferWidth = 0
    private var bufferHeight = 0
    var onReadyChanged: ((Boolean) -> Unit)? = null

    init {
        isOpaque = false
        alpha = 0f
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    fun present(value: CockpitStageFrame) {
        if (!value.valid()) return
        frame = value
        val size = value.bufferSize()
        if (size.first != bufferWidth || size.second != bufferHeight) {
            surfaceTexture?.setDefaultBufferSize(size.first, size.second)
            bufferWidth = size.first
            bufferHeight = size.second
        }
        hologram.presentCockpitStage(value, bufferWidth, bufferHeight)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        ownedSurface = Surface(texture).also { surface ->
            hologram.attachCockpitStage(surface) { ready ->
                alpha = if (ready) 1f else 0f
                onReadyChanged?.invoke(ready)
            }
        }
        bufferWidth = 0
        bufferHeight = 0
        frame?.let(::present)
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        bufferWidth = 0
        bufferHeight = 0
        frame?.let(::present)
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }
    private fun release() {
        hologram.detachCockpitStage()
        ownedSurface?.release()
        ownedSurface = null
        alpha = 0f
        onReadyChanged?.invoke(false)
    }
    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
