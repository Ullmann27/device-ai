package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.View
import android.util.TypedValue
import kotlin.math.min

/**
 * Compact four-gauge Android system-telemetry strip.
 *
 * Only real samples from ContinuumDashboardState are displayed. GPU usage
 * is explicitly unknown until an independently verified Android sampler exists.
 * This class neither polls sensors nor schedules background work.
 */
class ContinuumSystemGaugesView(
    context: Context,
    private val telemetry: ContinuumDashboardState.SystemStatus
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val ring = RectF()

    private val values: List<Triple<String, Int?, Int>> = listOf(
        Triple("App-CPU", telemetry.appCpuPercent, 0xFF00E5FF.toInt()),
        Triple("GPU", null, 0xFFA855F7.toInt()),
        Triple("RAM", telemetry.memoryPercent, 0xFFEC4899.toInt()),
        Triple("Speicher", telemetry.storagePercent, 0xFF3B82F6.toInt())
    )

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        contentDescription = values.joinToString(". ") { (name, measured, _) ->
            "$name: " + (measured?.let { "${it.coerceIn(0, 100)} Prozent" } ?: "nicht messbar")
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val needed = (130f * density).toInt()
        val height = View.resolveSize(needed, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val cell = width.toFloat() / values.size
        val stroke = 5.2f * density
        val radius = min(cell * 0.35f, height * 0.26f)
        val cy = (height * .44f).coerceAtLeast(radius + stroke)
        val labelSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        val valueSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, resources.displayMetrics)

        values.forEachIndexed { i, entry ->
            val (name, nullablePercent, accent) = entry
            val percent = nullablePercent?.coerceIn(0, 100)
            val cx = cell * (i + .5f)
            ring.set(cx - radius, cy - radius, cx + radius, cy + radius)

            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = stroke
            paint.color = 0xFF1E2942.toInt()
            canvas.drawArc(ring, -90f, 360f, false, paint)

            if (percent != null && percent > 0) {
                // Glow is behind the sharp colored arc, not a global blur of text.
                paint.strokeWidth = stroke * 1.8f
                paint.color = (accent and 0x00FFFFFF) or (0x3A shl 24)
                canvas.drawArc(ring, -90f, percent * 3.6f, false, paint)

                paint.strokeWidth = stroke
                paint.shader = SweepGradient(cx, cy,
                    intArrayOf(accent, Color.WHITE, accent), null)
                canvas.drawArc(ring, -90f, percent * 3.6f, false, paint)
                paint.shader = null
            }

            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            paint.textSize = valueSize
            paint.color = 0xFFF1F5FF.toInt()
            val centerY = cy - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(percent?.let { "$it%" } ?: "—", cx, centerY, paint)

            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = labelSize
            paint.color = 0xFFB1BFDC.toInt()
            canvas.drawText(name, cx, cy + radius + 21f * density, paint)
        }
    }
}
