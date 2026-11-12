package at.heinz.deviceai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** A private immutable output, not an ImageReader buffer lease. */
@RequiresApi(34)
internal object AndroidGpuFrameCopier {
    private val inFlight = AtomicBoolean(false)
    fun copy(image: Image, crop: Rect, executor: Executor, done: (AndroidLiveFrame?) -> Unit) {
        if (!inFlight.compareAndSet(false, true)) { image.close(); done(null); return }
        var source: HardwareBuffer? = null
        var bitmap: Bitmap? = null
        var output: HardwareBuffer? = null
        var renderer: HardwareBufferRenderer? = null
        var node: RenderNode? = null
        try {
            source = image.hardwareBuffer ?: error("CAPTURE_BUFFER_UNAVAILABLE")
            val colorSpace = ColorSpace.getFromDataSpace(image.dataSpace) ?: ColorSpace.get(ColorSpace.Named.SRGB)
            bitmap = Bitmap.wrapHardwareBuffer(source, colorSpace) ?: error("CAPTURE_BITMAP_UNAVAILABLE")
            output = HardwareBuffer.create(crop.width(), crop.height(), HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            node = RenderNode("ContinuumLiveCrop").apply { setPosition(0, 0, crop.width(), crop.height()) }
            val canvas = node.beginRecording()
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, crop, Rect(0, 0, crop.width(), crop.height()), Paint(Paint.FILTER_BITMAP_FLAG))
            node.endRecording()
            renderer = HardwareBufferRenderer(output).apply { setContentRoot(node) }
            val heldSource = source
            val heldBitmap = bitmap
            val heldOutput = output
            val heldRenderer = renderer
            val heldNode = node
            renderer.obtainRenderRequest().setColorSpace(colorSpace).draw(executor) { result ->
                var frame: AndroidLiveFrame? = null
                try {
                    // Never return a source before GPU completion.
                    val finished = result.fence.awaitForever()
                    if (finished && result.status == HardwareBufferRenderer.RenderResult.SUCCESS)
                        frame = AndroidLiveFrame(heldOutput, colorSpace)
                } finally {
                    result.fence.close()
                    heldRenderer.close()
                    heldNode.discardDisplayList()
                    heldBitmap.recycle()
                    heldSource.close()
                    image.close()
                    if (frame == null) heldOutput.close()
                    inFlight.set(false)
                    done(frame)
                }
            }
        } catch (_: Exception) {
            renderer?.close()
            node?.discardDisplayList()
            bitmap?.recycle()
            source?.close()
            output?.close()
            image.close()
            inFlight.set(false)
            done(null)
        }
    }
}
