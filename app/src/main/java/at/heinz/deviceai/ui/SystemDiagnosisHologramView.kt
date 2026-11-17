package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import at.heinz.deviceai.systemintelligence.SystemDiagnosis
import kotlin.math.min

/** Data surface inside the existing Cinema. */
class SystemDiagnosisHologramView(context: Context, private val sample: SystemDiagnosis?) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private val cyan = JarvisDesign.Colors.cyan
    private val violet = JarvisDesign.Colors.violet
    private val path = Path()
    init {
        tag = "system-diagnosis-hologram"
        contentDescription = sample?.report() ?: "Systemdiagnose. Noch keine Messung."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            resolveSize((280 * density * resources.configuration.fontScale.coerceAtLeast(1f)).toInt(), heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = min(width / 360f, height / 280f)
        if (unit <= 0f) return
        canvas.save()
        canvas.translate((width - 360f * unit) / 2, 0f)
        canvas.scale(unit, unit)
        paint.style = Paint.Style.FILL
        paint.color = 0xB5091827.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, 360f, 274f), 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .7f
        paint.color = 0x3048E5FF
        for (x in 14..350 step 24) canvas.drawLine(x.toFloat(), 12f, x.toFloat(), 264f, paint)
        for (y in 16..264 step 24) canvas.drawLine(10f, y.toFloat(), 350f, y.toFloat(), paint)
        paint.color = cyan
        canvas.drawOval(RectF(104f, 118f, 256f, 155f), paint)
        paint.color = violet
        canvas.drawOval(RectF(114f, 124f, 246f, 149f), paint)
        polygon(canvas, floatArrayOf(149f, 27f, 208f, 39f, 208f, 134f, 149f, 121f), 0xC6153850.toInt(), cyan)
        polygon(canvas, floatArrayOf(208f, 39f, 218f, 32f, 218f, 127f, 208f, 134f), 0xD4211D45.toInt(), violet)
        polygon(canvas, floatArrayOf(149f, 27f, 159f, 20f, 218f, 32f, 208f, 39f), 0xB03E5075.toInt(), cyan)
        paint.color = cyan; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        for (y in 49..108 step 12) canvas.drawLine(158f, y.toFloat(), 198f, y + 9f, paint)
        canvas.drawLine(157f, 31f, 167f, 33f, paint)
        label(canvas, "LOKALE", 18f, 38f, 12f, cyan)
        label(canvas, "SYSTEMDATEN", 18f, 55f, 12f, cyan)
        label(canvas, if (sample == null) "NICHT GEMESSEN" else "MESSUNG ERFASST", 18f, 79f, 9f, 0xFFB8D3E5.toInt())
        label(canvas, "THERMIK", 244f, 44f, 10f, violet)
        label(canvas, sample?.thermalStatus?.let { "STUFE $it" } ?: "UNBEKANNT", 244f, 61f, 12f, cyan)
        paint.style = Paint.Style.STROKE; paint.color = cyan
        canvas.drawLine(90f, 95f, 149f, 90f, paint)
        canvas.drawLine(218f, 79f, 300f, 94f, paint)
        val tiles = listOf(
            "PLATZ FREI" to SystemDiagnosis.bytes(sample?.storageAvailableBytes),
            "CONTINUUM PSS" to SystemDiagnosis.bytes(sample?.ownPssBytes),
            "RAM VERFÜGBAR" to SystemDiagnosis.bytes(sample?.ramAvailableBytes),
            "AKKU" to (sample?.batteryPercent?.let { "$it %" } ?: "nicht verfügbar")
        )
        tiles.forEachIndexed { index, (title, value) ->
            val x = 10f + (index % 2) * 174f
            val y = 164f + (index / 2) * 49f
            paint.style = Paint.Style.FILL; paint.color = 0xE00B2334.toInt()
            canvas.drawRoundRect(RectF(x, y, x + 166f, y + 42f), 7f, 7f, paint)
            paint.style = Paint.Style.STROKE; paint.color = if (index % 2 == 0) cyan else violet
            canvas.drawRoundRect(RectF(x, y, x + 166f, y + 42f), 7f, 7f, paint)
            label(canvas, title, x + 9, y + 13, 9f, 0xFFA5BBD2.toInt())
            label(canvas, value, x + 9, y + 32, 13f, 0xFFEAF8FF.toInt())
        }
        canvas.restore()
    }
    private fun polygon(canvas: Canvas, points: FloatArray, fill: Int, rim: Int) {
        path.reset(); path.moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        path.close(); paint.style = Paint.Style.FILL; paint.color = fill; canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE; paint.color = rim; paint.strokeWidth = 1.2f; canvas.drawPath(path, paint)
    }
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        canvas.drawText(text, x, y, paint)
    }
}
