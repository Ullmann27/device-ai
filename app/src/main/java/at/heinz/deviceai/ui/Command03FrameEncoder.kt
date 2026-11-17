package at.heinz.deviceai.ui

import android.graphics.Bitmap
import android.os.Build
import at.heinz.deviceai.AndroidLiveFrame
import java.io.ByteArrayOutputStream

/** Transient conversion of the already authorized buffer. No capture, file or network access. */
internal object Command03FrameEncoder {
    fun encode(frame: AndroidLiveFrame): Command03EncodedFrame? {
        if (Build.VERSION.SDK_INT < 29 || frame.width <= 0 || frame.height <= 0) return null
        val wrapped = Bitmap.wrapHardwareBuffer(frame.hardwareBuffer, frame.colorSpace) ?: return null
        var software: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            software = wrapped.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            val scale = minOf(1.0, Command03EncodedFrame.MAX_EDGE.toDouble() / maxOf(frame.width, frame.height))
            val width = (frame.width * scale).toInt().coerceAtLeast(1)
            val height = (frame.height * scale).toInt().coerceAtLeast(1)
            scaled = if (software.width == width && software.height == height) software
                else Bitmap.createScaledBitmap(software, width, height, true)
            // A fixed maximum output stream prevents a high-detail image from growing a hidden queue.
            val output = object : ByteArrayOutputStream() {
                override fun write(value: Int) {
                    check(count < Command03EncodedFrame.MAX_JPEG_BYTES) { "FRAME_ENCODING_LIMIT" }
                    super.write(value)
                }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    check(length <= Command03EncodedFrame.MAX_JPEG_BYTES - count) { "FRAME_ENCODING_LIMIT" }
                    super.write(bytes, offset, length)
                }
            }
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 70, output)) return null
            return Command03EncodedFrame(width, height, output.toByteArray()).takeIf { it.valid() }
        } finally {
            if (scaled !== software) scaled?.recycle()
            software?.recycle()
            wrapped.recycle()
        }
    }
}
