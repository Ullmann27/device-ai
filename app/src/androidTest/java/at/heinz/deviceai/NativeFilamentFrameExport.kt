package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.Image
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale

/** Native pixels, no UI fabrication. */
internal class NativeFilamentFrameExport(context: Context) {
    private val directory = File(checkNotNull(context.getExternalFilesDir(null)), "filament-review")
    private val rows = mutableListOf<String>()
    var count = 0
        private set

    init {
        check(directory.mkdirs() || directory.isDirectory)
        check(directory.listFiles()?.isEmpty() == true) { "VISUAL_DIRECTORY_NOT_EMPTY" }
        val sample = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            sample.copyPixelsFromBuffer(ByteBuffer.wrap(byteArrayOf(-1, 0, 0, -1)))
            check(sample.getPixel(0, 0) == Color.RED) { "RGBA_BITMAP_CHANNEL_ORDER" }
        } finally { sample.recycle() }
    }

    fun capture(image: Image) {
        if (count >= FRAME_COUNT) return
        check(image.cropRect == Rect(0, 0, image.width, image.height)) { "OUTPUT_CROP_CHANGED" }
        val plane = image.planes.single()
        check(plane.pixelStride == 4 && plane.rowStride >= image.width * 4) { "OUTPUT_PIXEL_LAYOUT" }
        val source = plane.buffer.duplicate()
        val start = source.position()
        val row = ByteArray(image.width * 4)
        check(source.limit() >= start + (image.height - 1) * plane.rowStride + row.size)
        val packed = ByteBuffer.allocate(row.size * image.height)
        repeat(image.height) { y ->
            source.position(start + y * plane.rowStride)
            source.get(row)
            packed.put(row)
        }
        packed.flip()
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val name = String.format(Locale.ROOT, "frame-%03d.png", count)
        val file = File(directory, name)
        try {
            bitmap.copyPixelsFromBuffer(packed)
            check(file.createNewFile()) { "DUPLICATE_VISUAL_FRAME" }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
        rows += listOf(name, image.timestamp, SystemClock.elapsedRealtimeNanos(),
            image.width, image.height, digest).joinToString("\t")
        count++
    }

    fun finish() {
        check(count == FRAME_COUNT) { "INCOMPLETE_VISUAL_SEQUENCE" }
        File(directory, "frames.tsv").writeText(
            "file\timageTimestampNanos\telapsedRealtimeNanos\twidth\theight\tsha256\n" +
                rows.joinToString("\n", postfix = "\n"))
    }

    companion object {
        const val FRAME_COUNT = 24
    }
}
