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
        var source: HardwareBuffer? = null
        val input: Pair<HardwareBuffer, ColorSpace>
        try {
            source = image.hardwareBuffer ?: error("CAPTURE_BUFFER_UNAVAILABLE")
            val color = observedColorSpace(image.dataSpace) ?: error("CAPTURE_COLOR_UNVERIFIED")
            input = source to color
        } catch (_: Exception) {
            source?.close()
            image.close()
            done(null)
            return
        }
        copy(input.first, input.second, crop, executor, { input.first.close(); image.close() }, done)
    }

    fun copy(frame: AndroidLiveFrame, executor: Executor, done: (AndroidLiveFrame?) -> Unit) =
        copy(frame.hardwareBuffer, frame.colorSpace, Rect(0, 0, frame.width, frame.height), executor, frame::close, done)

    internal fun observedColorSpace(dataSpace: Int): ColorSpace? = ColorSpace.getFromDataSpace(dataSpace)
        ?.takeIf { it.model == ColorSpace.Model.RGB }

    private fun copy(source: HardwareBuffer, colorSpace: ColorSpace, crop: Rect, executor: Executor,
                     releaseInput: () -> Unit, done: (AndroidLiveFrame?) -> Unit) {
        if (!inFlight.compareAndSet(false, true)) { releaseInput(); done(null); return }
        val completed = AtomicBoolean(false)
        var bitmap: Bitmap? = null
        var output: HardwareBuffer? = null
        var renderer: HardwareBufferRenderer? = null
        var node: RenderNode? = null
        fun complete(frame: AndroidLiveFrame?) {
            if (!completed.compareAndSet(false, true)) return
            try {
                runCatching { renderer?.close() }
                runCatching { node?.discardDisplayList() }
                runCatching { bitmap?.recycle() }
                runCatching { releaseInput() }
                if (frame == null) runCatching { output?.close() }
            } finally { inFlight.set(false) }
            done(frame)
        }
        try {
            check(colorSpace.model == ColorSpace.Model.RGB && crop.width() > 0 && crop.height() > 0 &&
                Rect(0, 0, source.width, source.height).contains(crop)) { "CAPTURE_INPUT_UNVERIFIED" }
            val outputColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            bitmap = Bitmap.wrapHardwareBuffer(source, colorSpace) ?: error("CAPTURE_BITMAP_UNAVAILABLE")
            output = HardwareBuffer.create(crop.width(), crop.height(), HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
            node = RenderNode("ContinuumLiveCrop").apply { setPosition(0, 0, crop.width(), crop.height()) }
            val canvas = node.beginRecording()
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, crop, Rect(0, 0, crop.width(), crop.height()), Paint(Paint.FILTER_BITMAP_FLAG))
            node.endRecording()
            renderer = HardwareBufferRenderer(output).apply { setContentRoot(node) }
            val heldOutput = output
            renderer.obtainRenderRequest().setColorSpace(outputColorSpace).draw(executor) { result ->
                var frame: AndroidLiveFrame? = null
                try {
                    // Never return a source before GPU completion.
                    val finished = result.fence.awaitForever()
                    if (finished && result.status == HardwareBufferRenderer.RenderResult.SUCCESS)
                        frame = AndroidLiveFrame(heldOutput, outputColorSpace)
                } finally {
                    result.fence.close()
                    complete(frame)
                }
            }
        } catch (_: Exception) {
            complete(null)
        }
    }
}
