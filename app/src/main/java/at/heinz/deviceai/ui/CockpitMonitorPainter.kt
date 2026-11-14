package at.heinz.deviceai.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import at.heinz.deviceai.avatar3d.CockpitStageFrame

/** Opaque pixels, shared perspective, no tint. */
internal class CockpitMonitorPainter {
    data class Pointer(val x: Float, val y: Float, val progress: Float)
    private val matrix = Matrix()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    private val destination = RectF()

    fun draw(canvas: Canvas, frame: CockpitStageFrame, bitmap: Bitmap?, status: String, live: Boolean,
             nativeFrameReady: Boolean, density: Float, pointer: Pointer? = null) {
        if (!frame.valid()) return
        val width = frame.monitorWidth
        val height = frame.monitorHeight
        val corners = frame.quad(0f, 0f, 1f, 1f)
        matrix.setPolyToPoly(floatArrayOf(0f, 0f, width, 0f, width, height, 0f, height), 0, corners, 0, 4)
        val save = canvas.save()
        canvas.concat(matrix)
        if (!nativeFrameReady) {
            fill.color = 0xff101b2b.toInt()
            canvas.drawRoundRect(0f, 0f, width, height, 8f * density, 8f * density, fill)
            fill.color = 0xff36546a.toInt()
            fill.style = Paint.Style.STROKE; fill.strokeWidth = density
            canvas.drawRoundRect(0f, 0f, width, height, 8f * density, 8f * density, fill)
            fill.style = Paint.Style.FILL
        }
        val left = width * CockpitStageFrame.CONTENT_LEFT
        val top = height * CockpitStageFrame.CONTENT_TOP
        val right = width * CockpitStageFrame.CONTENT_RIGHT
        val bottom = height * CockpitStageFrame.CONTENT_BOTTOM
        fill.color = Color.BLACK
        canvas.drawRect(left, top, right, bottom, fill)
        val source = bitmap?.takeUnless { it.isRecycled }
        if (source != null) {
            val scale = minOf((right - left) / source.width, (bottom - top) / source.height)
            val w = source.width * scale; val h = source.height * scale
            destination.set((left + right - w) * .5f, (top + bottom - h) * .5f,
                (left + right + w) * .5f, (top + bottom + h) * .5f)
            canvas.save()
            canvas.clipRect(left, top, right, bottom)
            image.alpha = 255
            canvas.drawBitmap(source, null, destination, image)
            pointer?.let {
                val x = destination.left + it.x * destination.width()
                val y = destination.top + it.y * destination.height()
                fill.color = 0xff56e4ef.toInt()
                fill.alpha = ((1f - it.progress) * 220f).toInt().coerceIn(0, 255)
                fill.style = Paint.Style.STROKE
                fill.strokeWidth = 1.4f * density
                canvas.drawCircle(x, y, (4f + 12f * it.progress) * density, fill)
                fill.style = Paint.Style.FILL
                fill.alpha = 255
            }
            canvas.restore()
        } else {
            label.color = 0xffa7b7c8.toInt()
            label.textAlign = Paint.Align.CENTER
            label.textSize = minOf(15f * density, (right - left) / 24f)
            canvas.drawText("Dein App-Bild erscheint hier", width * .5f, (top + bottom) * .5f, label)
            label.textSize = minOf(12f * density, (right - left) / 34f)
            canvas.drawText("Nur echte, freigegebene Bilddaten", width * .5f,
                (top + bottom) * .5f + 20f * density, label)
        }
        fill.color = if (live && source != null) 0xff35d9a6.toInt() else 0xffe3a951.toInt()
        val headerY = height * .075f
        canvas.drawCircle(left + 5f * density, headerY - 3f * density, 3f * density, fill)
        label.color = 0xffd7eaf5.toInt()
        label.textAlign = Paint.Align.LEFT
        label.textSize = minOf(12.5f * density, height * .055f)
        val visibleStatus = if (live && source == null) "Bild wird übernommen" else status
        val room = right - left - 24f * density
        val text = android.text.TextUtils.ellipsize(visibleStatus, android.text.TextPaint(label), room,
            android.text.TextUtils.TruncateAt.END).toString()
        canvas.drawText(text, left + 16f * density, headerY, label)
        canvas.restoreToCount(save)
    }
}
