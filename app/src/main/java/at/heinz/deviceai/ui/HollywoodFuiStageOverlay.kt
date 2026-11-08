package at.heinz.deviceai.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Decorative layer owned by the canonical work-window host. */
class HollywoodFuiStageOverlay(context: Context) : View(context) {
    private val stage = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var profile = JarvisActionScenes.FuiProfile.GENERIC
    private var accent = JarvisDesign.Colors.cyan
    private var openedAt = 0L
    private var elapsedMs = 0L
    private var closingElapsedMs = -1L
    private var active = false
    private var reducedMotion = false
    private var bracketsLatched = false
    private var dataLatched = false
    private var animator: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun open(bounds: RectF, scene: JarvisActionScenes.Scene) {
        animator?.cancel()
        stage.set(bounds)
        profile = scene.fuiProfile
        accent = scene.accent
        openedAt = SystemClock.uptimeMillis()
        elapsedMs = 0L
        closingElapsedMs = -1L
        bracketsLatched = false
        dataLatched = false
        visibility = VISIBLE
        reducedMotion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()
        if (reducedMotion) {
            elapsedMs = HollywoodFuiConstruct.DATA_END_MS
            invalidate()
            return
        }
        animator = ValueAnimator.ofInt(0, HollywoodFuiConstruct.DATA_END_MS.toInt()).apply {
            duration = HollywoodFuiConstruct.DATA_END_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                elapsedMs = (it.animatedValue as Int).toLong()
                emitPhaseFeedback(elapsedMs)
                invalidate()
            }
            start()
        }
    }

    fun retarget(bounds: RectF) {
        stage.set(bounds)
        invalidate()
    }

    /**
     * Reuses this exact overlay for a stronger scene in the same turn.
     * Geometry stays in place; only the FUI grammar rematerializes.
     */
    fun morphInPlace(bounds: RectF, scene: JarvisActionScenes.Scene) {
        val profileChanged = profile != scene.fuiProfile || accent != scene.accent
        stage.set(bounds)
        if (!profileChanged) {
            invalidate()
            return
        }
        open(bounds, scene)
    }

    fun setWorkActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) invalidate()
    }

    fun close(immediate: Boolean) {
        active = false
        animator?.cancel()
        if (immediate || visibility != VISIBLE || reducedMotion) {
            visibility = GONE
            return
        }
        closingElapsedMs = 0L
        animator = ValueAnimator.ofInt(0, HollywoodFuiConstruct.CLOSE_END_MS.toInt()).apply {
            duration = HollywoodFuiConstruct.CLOSE_END_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                closingElapsedMs = (it.animatedValue as Int).toLong()
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (stage.isEmpty || visibility != VISIBLE) return
        val liveElapsed = if (active) {
            maxOf(elapsedMs, SystemClock.uptimeMillis() - openedAt)
        } else {
            elapsedMs
        }
        val frame = HollywoodFuiConstruct.frame(liveElapsed, closingElapsedMs, reducedMotion)
        if (frame.closeFactor <= 0f) return

        val scale = 0.15f + 0.85f * frame.brackets
        val cx = stage.centerX()
        val cy = stage.centerY()
        val halfWidth = stage.width() * 0.5f * scale
        val halfHeight = stage.height() * 0.5f * scale
        val constructed = RectF(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight)
        val alpha = frame.closeFactor

        canvas.save()
        canvas.clipRect(constructed)
        if (frame.grid > 0f) {
            drawGrid(canvas, constructed, frame.grid * alpha)
            drawChamferedRim(canvas, constructed, frame.grid * alpha)
            if (frame.scanEnabled) drawScan(canvas, constructed, frame.scanPhase, frame.grid * alpha)
        }
        if (frame.data > 0f) {
            drawProfile(canvas, constructed, frame.data * alpha)
        }
        canvas.restore()
        drawCornerBrackets(canvas, constructed, frame.brackets * alpha)

        if (active && isAttachedToWindow) postInvalidateDelayed(ACTIVE_FRAME_DELAY_MS)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun emitPhaseFeedback(elapsed: Long) {
        if (!bracketsLatched && elapsed >= HollywoodFuiConstruct.BRACKETS_END_MS) {
            bracketsLatched = true
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (!dataLatched && elapsed >= HollywoodFuiConstruct.GRID_END_MS) {
            dataLatched = true
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun drawGrid(canvas: Canvas, box: RectF, alpha: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(0.7f)
        paint.color = withAlpha(accent, 0.13f * alpha)
        val step = dp(24f)
        var x = box.left + step
        while (x < box.right) {
            canvas.drawLine(x, box.top, x, box.bottom, paint)
            x += step
        }
        var y = box.top + step
        while (y < box.bottom) {
            canvas.drawLine(box.left, y, box.right, y, paint)
            y += step
        }
        paint.color = withAlpha(JarvisDesign.Colors.violet, 0.10f * alpha)
        canvas.drawLine(box.centerX(), box.top, box.centerX(), box.bottom, paint)
        canvas.drawLine(box.left, box.centerY(), box.right, box.centerY(), paint)
    }

    private fun drawChamferedRim(canvas: Canvas, box: RectF, alpha: Float) {
        val cut = min(dp(14f), min(box.width(), box.height()) * 0.12f)
        path.reset()
        path.moveTo(box.left + cut, box.top)
        path.lineTo(box.right - cut, box.top)
        path.lineTo(box.right, box.top + cut)
        path.lineTo(box.right, box.bottom - cut)
        path.lineTo(box.right - cut, box.bottom)
        path.lineTo(box.left + cut, box.bottom)
        path.lineTo(box.left, box.bottom - cut)
        path.lineTo(box.left, box.top + cut)
        path.close()

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.3f)
        paint.color = withAlpha(accent, 0.70f * alpha)
        canvas.drawPath(path, paint)
        paint.strokeWidth = dp(5f)
        paint.color = withAlpha(accent, 0.08f * alpha)
        canvas.drawPath(path, paint)
    }

    private fun drawScan(canvas: Canvas, box: RectF, phase: Float, alpha: Float) {
        val y = box.top + box.height() * phase
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(5f)
        paint.color = withAlpha(accent, 0.07f * alpha)
        canvas.drawLine(box.left + dp(6f), y, box.right - dp(6f), y, paint)
        paint.strokeWidth = dp(1.2f)
        paint.color = withAlpha(JarvisDesign.Colors.glowCore, 0.75f * alpha)
        canvas.drawLine(box.left + dp(6f), y, box.right - dp(6f), y, paint)
    }

    private fun drawProfile(canvas: Canvas, box: RectF, alpha: Float) {
        val inset = dp(18f)
        val inner = RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset)
        when (profile) {
            JarvisActionScenes.FuiProfile.DOCUMENT_BLUEPRINT -> drawDocument(canvas, inner, alpha)
            JarvisActionScenes.FuiProfile.APP_CONDUIT -> drawAppConduit(canvas, inner, alpha)
            JarvisActionScenes.FuiProfile.CODE_FORGE -> drawCodeForge(canvas, inner, alpha)
            JarvisActionScenes.FuiProfile.SWARM_GRID -> drawSwarm(canvas, inner, alpha)
            JarvisActionScenes.FuiProfile.EVIDENCE_CONSTELLATION -> drawEvidence(canvas, inner, alpha)
            JarvisActionScenes.FuiProfile.CHRONO_GAUGE -> drawChrono(canvas, inner, alpha)
            JarvisActionScenes.FuiProfile.SECURITY_XRAY -> drawSecurity(canvas, inner, alpha)
            JarvisActionScenes.FuiProfile.GENERIC -> drawGeneric(canvas, inner, alpha)
        }
    }

    private fun drawDocument(canvas: Canvas, box: RectF, alpha: Float) {
        val pageWidth = min(box.width() * 0.22f, dp(92f))
        val pageHeight = min(box.height() * 0.68f, dp(126f))
        for (index in 2 downTo 0) {
            val offset = dp(10f) * index
            val page = RectF(box.left + offset, box.centerY() - pageHeight / 2f - offset * 0.4f,
                box.left + offset + pageWidth, box.centerY() + pageHeight / 2f - offset * 0.4f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = withAlpha(if (index == 0) accent else JarvisDesign.Colors.violet, (0.48f - index * 0.1f) * alpha)
            canvas.drawRoundRect(page, dp(3f), dp(3f), paint)
            repeat(5) { line ->
                val y = page.top + dp(16f + line * 15f)
                canvas.drawLine(page.left + dp(9f), y,
                    page.left + page.width() * if (line % 2 == 0) 0.78f else 0.62f, y, paint)
            }
        }
    }

    private fun drawAppConduit(canvas: Canvas, box: RectF, alpha: Float) {
        val radius = min(box.width(), box.height()) * 0.19f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = withAlpha(accent, 0.45f * alpha)
        drawHexagon(canvas, box.centerX(), box.centerY(), radius, paint)
        paint.color = withAlpha(JarvisDesign.Colors.violet, 0.28f * alpha)
        drawHexagon(canvas, box.centerX(), box.centerY(), radius * 1.35f, paint)
        repeat(6) { index ->
            val angle = index * PI / 3.0
            val x = box.centerX() + cos(angle).toFloat() * radius * 1.75f
            val y = box.centerY() + sin(angle).toFloat() * radius * 1.25f
            canvas.drawCircle(x, y, dp(4f), paint)
            canvas.drawLine(box.centerX(), box.centerY(), x, y, paint)
        }
    }

    private fun drawCodeForge(canvas: Canvas, box: RectF, alpha: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.1f)
        repeat(7) { index ->
            val y = box.top + box.height() * (index + 1f) / 9f
            val width = box.width() * if (index % 3 == 0) 0.40f else 0.29f
            paint.color = withAlpha(if (index % 4 == 0) JarvisDesign.Colors.ok else accent, 0.38f * alpha)
            canvas.drawLine(box.left, y, box.left + width, y, paint)
        }
        val graphLeft = box.left + box.width() * 0.58f
        val nodes = listOf(
            graphLeft to box.top + box.height() * 0.24f,
            box.right - dp(8f) to box.top + box.height() * 0.38f,
            graphLeft + box.width() * 0.08f to box.bottom - box.height() * 0.25f,
            box.right - box.width() * 0.18f to box.bottom - dp(8f)
        )
        paint.color = withAlpha(JarvisDesign.Colors.violet, 0.34f * alpha)
        nodes.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.first, a.second, b.first, b.second, paint) }
        paint.color = withAlpha(accent, 0.58f * alpha)
        nodes.forEach { canvas.drawCircle(it.first, it.second, dp(4f), paint) }
    }

    private fun drawSwarm(canvas: Canvas, box: RectF, alpha: Float) {
        val cardWidth = box.width() * 0.25f
        val cardHeight = box.height() * 0.22f
        val centreX = box.centerX()
        val centreY = box.centerY()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        repeat(6) { index ->
            val column = index % 3
            val row = index / 3
            val left = box.left + column * (box.width() - cardWidth) / 2f
            val top = box.top + row * (box.height() - cardHeight)
            val card = RectF(left, top, left + cardWidth, top + cardHeight)
            paint.color = withAlpha(if (index % 2 == 0) accent else JarvisDesign.Colors.violet, 0.38f * alpha)
            canvas.drawRoundRect(card, dp(3f), dp(3f), paint)
            canvas.drawLine(card.centerX(), card.centerY(), centreX, centreY, paint)
        }
        paint.color = withAlpha(JarvisDesign.Colors.glowCore, 0.72f * alpha)
        canvas.drawCircle(centreX, centreY, dp(5f), paint)
    }

    private fun drawEvidence(canvas: Canvas, box: RectF, alpha: Float) {
        val cx = box.centerX()
        val cy = box.centerY()
        val radiusX = box.width() * 0.34f
        val radiusY = box.height() * 0.31f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        val points = (0 until 7).map { index ->
            val angle = index * (2.0 * PI / 7.0)
            Pair(cx + cos(angle).toFloat() * radiusX, cy + sin(angle).toFloat() * radiusY)
        }
        paint.color = withAlpha(accent, 0.30f * alpha)
        points.forEachIndexed { index, point ->
            val next = points[(index + 2) % points.size]
            canvas.drawLine(point.first, point.second, next.first, next.second, paint)
        }
        points.forEachIndexed { index, point ->
            paint.color = withAlpha(if (index == 3) JarvisDesign.Colors.magenta else accent, 0.62f * alpha)
            canvas.drawCircle(point.first, point.second, dp(if (index == 3) 5f else 3.5f), paint)
        }
    }

    private fun drawChrono(canvas: Canvas, box: RectF, alpha: Float) {
        val size = min(box.width(), box.height()) * 0.58f
        val gauge = RectF(box.left, box.centerY() - size / 2f, box.left + size, box.centerY() + size / 2f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = withAlpha(accent, 0.48f * alpha)
        canvas.drawArc(gauge, 140f, 260f, false, paint)
        repeat(4) { index ->
            val barLeft = box.left + size + dp(18f)
            val y = box.top + box.height() * (index + 1f) / 5f
            paint.strokeWidth = dp(3f)
            paint.color = withAlpha(if (index == 3) JarvisDesign.Colors.ok else JarvisDesign.Colors.violet,
                (0.26f + index * 0.08f) * alpha)
            canvas.drawLine(barLeft, y, barLeft + box.width() * (0.16f + index * 0.08f), y, paint)
        }
    }

    private fun drawSecurity(canvas: Canvas, box: RectF, alpha: Float) {
        val phoneWidth = min(box.width() * 0.28f, dp(82f))
        val phoneHeight = min(box.height() * 0.70f, dp(150f))
        val phone = RectF(box.centerX() - phoneWidth / 2f, box.centerY() - phoneHeight / 2f,
            box.centerX() + phoneWidth / 2f, box.centerY() + phoneHeight / 2f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = withAlpha(accent, 0.52f * alpha)
        canvas.drawRoundRect(phone, dp(9f), dp(9f), paint)
        canvas.drawLine(phone.centerX(), phone.top + dp(6f), phone.centerX(), phone.bottom - dp(6f), paint)
        canvas.drawLine(phone.left + dp(6f), phone.centerY(), phone.right - dp(6f), phone.centerY(), paint)
        paint.strokeWidth = dp(3f)
        paint.color = withAlpha(JarvisDesign.Colors.violet, 0.16f * alpha)
        canvas.drawLine(box.left, phone.centerY(), box.right, phone.centerY(), paint)
    }

    private fun drawGeneric(canvas: Canvas, box: RectF, alpha: Float) {
        val size = min(box.width(), box.height()) * 0.46f
        val ring = RectF(box.centerX() - size, box.centerY() - size * 0.55f,
            box.centerX() + size, box.centerY() + size * 0.55f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.1f)
        paint.color = withAlpha(accent, 0.40f * alpha)
        canvas.drawOval(ring, paint)
        ring.inset(size * 0.18f, size * 0.10f)
        paint.color = withAlpha(JarvisDesign.Colors.violet, 0.32f * alpha)
        canvas.drawOval(ring, paint)
    }

    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, radius: Float, target: Paint) {
        path.reset()
        repeat(6) { index ->
            val angle = Math.toRadians((index * 60f - 30f).toDouble())
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, target)
    }

    private fun drawCornerBrackets(canvas: Canvas, box: RectF, alpha: Float) {
        val length = min(dp(20f), min(box.width(), box.height()) * 0.18f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.SQUARE
        paint.strokeWidth = dp(2.2f)
        paint.color = withAlpha(JarvisDesign.Colors.glowCore, 0.90f * alpha)
        canvas.drawLine(box.left, box.top, box.left + length, box.top, paint)
        canvas.drawLine(box.left, box.top, box.left, box.top + length, paint)
        canvas.drawLine(box.right - length, box.top, box.right, box.top, paint)
        canvas.drawLine(box.right, box.top, box.right, box.top + length, paint)
        canvas.drawLine(box.left, box.bottom - length, box.left, box.bottom, paint)
        canvas.drawLine(box.left, box.bottom, box.left + length, box.bottom, paint)
        canvas.drawLine(box.right - length, box.bottom, box.right, box.bottom, paint)
        canvas.drawLine(box.right, box.bottom - length, box.right, box.bottom, paint)
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (Color.alpha(color) * alpha.coerceIn(0f, 1f)).roundToInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val ACTIVE_FRAME_DELAY_MS = 33L
    }
}
