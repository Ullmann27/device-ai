package at.heinz.deviceai.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.util.TypedValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native, offline, accessibility-aware Continuum cockpit surfaces.
 *
 * This is a view-only adapter for existing owner-authorized actions. It does
 * not access models, store data, request permissions, execute code, or invent
 * telemetry. Unknown measurements are shown explicitly as unknown.
 */
object ContinuumBentoCockpit {
    private val WHITE = 0xFFF1F5FF.toInt()
    private val MUTED = 0xFFB1BFDC.toInt()
    private val CYAN = 0xFF00E5FF.toInt()
    private val VIOLET = 0xFFA855F7.toInt()
    private val AMBER = 0xFFFFC45C.toInt()

    private fun dp(ctx: Context, amount: Float): Int =
        (amount * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private fun text(ctx: Context, value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = true
            setPadding(0, dp(ctx, 2f), 0, dp(ctx, 2f))
        }

    fun column(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun panel(ctx: Context, eyebrow: String, title: String, description: String): LinearLayout =
        column(ctx).apply {
            background = Glass(0xFF3B82F6.toInt(), ctx.resources.displayMetrics.density, surface = false)
            setPadding(dp(ctx, 16f), dp(ctx, 18f), dp(ctx, 16f), dp(ctx, 16f))
            addView(text(ctx, eyebrow, 10.5f, CYAN, true))
            addView(text(ctx, title, 23f, WHITE, true), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 6f) })
            if (description.isNotBlank()) addView(text(ctx, description, 12.5f, MUTED),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(ctx, 7f) })
        }

    fun section(ctx: Context, label: String): TextView = text(ctx, label, 10.5f, CYAN, true).apply {
        setPadding(dp(ctx, 4f), dp(ctx, 10f), 0, dp(ctx, 7f))
    }

    fun statusPane(ctx: Context): LinearLayout = column(ctx).apply {
        background = Glass(CYAN, ctx.resources.displayMetrics.density)
        setPadding(dp(ctx, 11f), dp(ctx, 10f), dp(ctx, 11f), dp(ctx, 10f))
    }

    /** Keeps all rows visible and readable, never invents a KPI by interpreting text as a generic percentage. */
    fun updateStatus(container: LinearLayout, raw: String) {
        val ctx = container.context
        container.removeAllViews()
        val rows = formattedStatus(raw).lines().filter { it.isNotBlank() }
        if (rows.isEmpty()) {
            container.addView(text(ctx, "Noch keine überprüfbaren Statusdaten.", 12f, MUTED))
            return
        }
        val items = rows.map { line ->
            val separator = line.indexOf(':')
            column(ctx).apply {
                background = Glass(VIOLET, ctx.resources.displayMetrics.density)
                setPadding(dp(ctx, 10f), dp(ctx, 9f), dp(ctx, 10f), dp(ctx, 9f))
                if (separator in 1..45) {
                    addView(text(ctx, line.substring(0, separator).trim(), 10f, CYAN, true))
                    addView(text(ctx, line.substring(separator + 1).trim().ifBlank { "—" }, 11.5f, WHITE))
                } else {
                    addView(text(ctx, line, 11.5f, WHITE))
                }
                contentDescription = line
            }
        }
        grid(ctx, container, items, if (ctx.resources.configuration.screenWidthDp >= 600) 3 else 2)
    }

    fun action(ctx: Context, label: String, primary: Boolean = false, critical: Boolean = false, run: () -> Unit): TextView {
        val color = if (critical) 0xFFEC4899.toInt() else if (primary) CYAN else VIOLET
        return text(ctx, "◈   " + label, 11.6f, WHITE, true).apply {
            background = Glass(color, ctx.resources.displayMetrics.density)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(ctx, 66f)
            setPadding(dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f), dp(ctx, 12f))
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { run() }
        }
    }

    fun grid(ctx: Context, target: LinearLayout, items: List<View>, columns: Int) {
        val count = columns.coerceAtLeast(1)
        for (start in items.indices step count) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (index in start until (start + count).coerceAtMost(items.size)) {
                val view = items[index]
                row.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > start) marginStart = dp(ctx, 7f)
                })
            }
            val missing = count - (items.size - start).coerceAtMost(count)
            repeat(missing) { row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f)) }
            target.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 7f) })
        }
    }

    fun show(activity: Activity, body: LinearLayout): AlertDialog {
        val scroll = object : ScrollView(activity) {
            private val sciFiEffects = ContinuumSciFiEffects(resources.displayMetrics.density)
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                // Restrained terminal texture; scroll gestures and TalkBack stay untouched.
                sciFiEffects.drawScanlines(canvas, width.toFloat(), height.toFloat())
            }
        }.apply {
            isFillViewport = false
            clipToPadding = false
            addView(body)
        }
        val dialog = AlertDialog.Builder(activity).setView(scroll).create()
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.78f)
                val maximum = dp(activity, if (activity.resources.configuration.screenWidthDp >= 600) 1000f else 560f)
                val width = (activity.resources.displayMetrics.widthPixels * 0.94f).toInt().coerceAtMost(maximum)
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
        return dialog
    }

    /**
     * Display-only timestamp decoding. Never modifies the original logs or
     * model receipts. Non-matching data is preserved verbatim.
     */
    fun formattedStatus(raw: String): String {
        val pattern = Regex("""(\d{13}):(RUNNING|FINISHED|FAILED|CANCELLED)""")
        return pattern.replace(raw) { match ->
            val millis = match.groupValues[1].toLongOrNull()
            if (millis == null) match.value else {
                val date = SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(millis))
                date + " · " + match.groupValues[2]
            }
        }.replace("|", "\n")
    }

    private class Glass(private val accent: Int, private val density: Float, private val surface: Boolean = true) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val rect = RectF()
        private var fill: Shader? = null
        private var border: Shader? = null
        private var sheen: Shader? = null

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            rect.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
            val w = rect.width().coerceAtLeast(1f)
            val h = rect.height().coerceAtLeast(1f)
            fill = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(0xF10D1934.toInt(), 0xF5050B19.toInt(), 0xFB060918.toInt()), null, Shader.TileMode.CLAMP)
            border = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(CYAN, 0xFF3B82F6.toInt(), VIOLET, 0x5543557A),
                floatArrayOf(0f, 0.32f, 0.72f, 1f), Shader.TileMode.CLAMP)
            sheen = LinearGradient(rect.left, rect.top, rect.left, rect.top + h * .65f,
                intArrayOf(0x4455AFFF, 0x115AD7FF, 0x00000000), null, Shader.TileMode.CLAMP)
        }

        override fun draw(canvas: Canvas) {
            val radius = (if (surface) 18f else 24f) * density
            paint.style = Paint.Style.FILL
            paint.shader = fill
            paint.alpha = 255
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.shader = sheen
            canvas.drawRoundRect(RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f,
                rect.top + rect.height() * .65f), radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * density
            paint.shader = border
            canvas.drawRoundRect(RectF(rect.left + 1.5f, rect.top + 1.5f,
                rect.right - 1.5f, rect.bottom - 1.5f), radius, radius, paint)
            paint.strokeWidth = 1.1f * density
            paint.shader = null
            paint.color = accent
            paint.alpha = 120
            canvas.drawArc(rect.left + radius, rect.top + 1f,
                rect.right - radius, rect.top + radius * 1.2f, 193f, 145f, false, paint)
            paint.alpha = 255
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }
    }
}
