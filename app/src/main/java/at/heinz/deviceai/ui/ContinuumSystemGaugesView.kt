package at.heinz.deviceai.ui

import android.animation.ValueAnimator
import android.os.PowerManager
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
    private var telemetry: ContinuumDashboardState.SystemStatus
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val ring = RectF()
    private var columns = 4
    private var rowHeight = 0f
    private val gradients = arrayOfNulls<SweepGradient>(5)
    private val boldFace = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)

    private val values: List<Triple<String, Int?, Int>>
        get() = listOf(
            Triple("App-CPU", telemetry.appCpuPercent, 0xFF00E5FF.toInt()),
            Triple("Akku", telemetry.batteryPercent, 0xFF3BE7C3.toInt()),
            Triple("RAM", telemetry.memoryPercent, 0xFFEC4899.toInt()),
            Triple("Speicher", telemetry.storagePercent, 0xFF3B82F6.toInt()),
            Triple("GPU", null, 0xFFA855F7.toInt())
        )

    private var foregroundActive = false
    private var transition: ValueAnimator? = null
    private val displayed = FloatArray(5) { values[it].second?.coerceIn(0, 100)?.toFloat() ?: Float.NaN }
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun setForegroundActive(active: Boolean) {
        foregroundActive = active
        if (!active) settle()
    }

    private fun motionAllowed() = foregroundActive && isAttachedToWindow && isShown &&
        windowVisibility == VISIBLE && ValueAnimator.areAnimatorsEnabled() && !powerManager.isPowerSaveMode

    private fun settle() {
        transition?.cancel()
        transition = null
        values.forEachIndexed { i, entry -> displayed[i] = entry.second?.coerceIn(0, 100)?.toFloat() ?: Float.NaN }
        invalidate()
    }

    override fun onDetachedFromWindow() { settle(); super.onDetachedFromWindow() }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (!isVisible) settle()
    }

    /** The open system panel reflects the same 5-second sampler as the dashboard. */
    fun updateTelemetry(next: ContinuumDashboardState.SystemStatus) {
        if (telemetry == next) return
        transition?.cancel()
        transition = null
        val from = displayed.copyOf()
        telemetry = next
        contentDescription = describeTelemetry()
        val target = FloatArray(5) { values[it].second?.coerceIn(0, 100)?.toFloat() ?: Float.NaN }
        // Missing/new measurements appear immediately. Never invent a zero baseline.
        for (i in displayed.indices) if (!from[i].isFinite() || !target[i].isFinite()) {
            from[i] = target[i]
            displayed[i] = target[i]
        }
        if (!motionAllowed() || from.indices.none { from[it].isFinite() && from[it] != target[it] }) {
            settle()
            return
        }
        transition = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            addUpdateListener {
                if (!motionAllowed()) { settle(); return@addUpdateListener }
                val t = it.animatedValue as Float
                for (i in displayed.indices) displayed[i] = from[i] + (target[i] - from[i]) * t
                if (t >= 1f) transition = null
                invalidate()
            }
            start()
        }
    }

    private fun describeTelemetry(): String = values.joinToString(". ") { (name, measured, _) ->
        "$name: " + (measured?.let { "${it.coerceIn(0, 100)} Prozent" } ?: "nicht messbar")
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
        contentDescription = describeTelemetry()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val font = resources.configuration.fontScale.coerceAtLeast(1f)
        columns = ContinuumGridPolicy.gaugeColumns(width / density, font)
        val needed = (110f * font * density * ((values.size + columns - 1) / columns)).toInt()
        val height = View.resolveSize(needed, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rowHeight = h.toFloat() / ((values.size + columns - 1) / columns)
        val cell = w.toFloat() / columns
        for (i in values.indices) {
            val cx = cell * (i % columns + .5f)
            val cy = rowHeight * (i / columns + .43f)
            val accent = values[i].third
            gradients[i] = SweepGradient(cx, cy, intArrayOf(accent, Color.WHITE, accent), null)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val cell = width.toFloat() / columns
        val stroke = 5.2f * density
        val radius = min(cell * 0.30f, rowHeight * 0.27f)
        val labelSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        val valueSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)

        values.forEachIndexed { i, entry ->
            val (name, nullablePercent, accent) = entry
            val percent = nullablePercent?.coerceIn(0, 100)
            val cx = cell * (i % columns + .5f)
            val cy = rowHeight * (i / columns + .43f)
            ring.set(cx - radius, cy - radius, cx + radius, cy + radius)

            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = stroke
            paint.color = 0xFF1E2942.toInt()
            canvas.drawArc(ring, -90f, 360f, false, paint)

            val animatedPercent = displayed[i]
            if (percent != null && animatedPercent.isFinite() && animatedPercent > 0f) {
                // Glow is behind the sharp colored arc, not a global blur of text.
                paint.strokeWidth = stroke * 1.8f
                paint.color = (accent and 0x00FFFFFF) or (0x3A shl 24)
                canvas.drawArc(ring, -90f, animatedPercent * 3.6f, false, paint)

                paint.strokeWidth = stroke
                paint.shader = gradients[i]
                canvas.drawArc(ring, -90f, animatedPercent * 3.6f, false, paint)
                paint.shader = null
            }

            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = boldFace
            paint.textSize = valueSize
            paint.color = 0xFFF1F5FF.toInt()
            val centerY = cy - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(percent?.let { "$it%" } ?: "—", cx, centerY, paint)

            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = labelSize
            paint.color = 0xFFB1BFDC.toInt()
            canvas.drawText(name, cx, cy + radius + labelSize * 1.45f, paint)
        }
    }
}
