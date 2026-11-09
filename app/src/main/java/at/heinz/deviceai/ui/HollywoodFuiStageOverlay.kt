package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import at.heinz.deviceai.cockpit.StepState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Decorative layer owned by the canonical work-window host. Follows the single timing law in [HollywoodFuiConstruct]
 * (anchor → brackets → scan raster → glass layers → data), tilts the whole construct in perspective while it flies in
 * or folds away and pays for motes/bloom/frame rate only what the cockpit's [JarvisRenderQuality.Budget] allows.
 */
class HollywoodFuiStageOverlay(context: Context) : View(context) {
    private val stage = RectF()
    private val source = RectF()
    private var protectedBounds = emptyList<RectF>()
    internal var requestFrame: () -> Unit = {}
    internal var cinematicEffectsEnabled = true
        set(value) {
            field = value
            invalidate()
            requestFrame()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val painter = AndroidContinuumPainter(context.assets)
    private val camera = Camera()
    private val cameraMatrix = Matrix()
    private var cinematics = HollywoodFuiCinematics.Painterly(JarvisDesign.Colors.cyan)
    private var budget = HollywoodFuiCinematics.Budget.FULL
    private var originX = Float.NaN
    private var originY = Float.NaN
    private var moteSeed = 0
    private var anchorLatched = false
    private var profile = JarvisActionScenes.FuiProfile.GENERIC
    private var accent = JarvisDesign.Colors.cyan
    private var elapsedMs = 0L
    private var openness = 0f
    private var closingElapsedMs = -1L
    private var active = false
    private var parallaxX = 0f
    private var parallaxY = 0f
    private var reducedMotion = false
    private var bracketsLatched = false
    private var dataLatched = false
    private var telemetryText: String? = null
    private var liveData: HollywoodFuiLiveData? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    /** Origin and destination use the native window's exact overlay-pixel geometry. */
    fun open(bounds: RectF, scene: JarvisActionScenes.Scene, origin: RectF,
             protected: List<RectF> = emptyList()) {
        stage.set(bounds)
        source.set(origin)
        protectedBounds = protected.map(::RectF)
        profile = scene.fuiProfile
        if (accent != scene.accent) cinematics = HollywoodFuiCinematics.Painterly(scene.accent)
        accent = scene.accent
        originX = origin.centerX()
        originY = origin.centerY()
        moteSeed = scene.fuiProfile.ordinal
        elapsedMs = 0L
        openness = 0f
        closingElapsedMs = -1L
        anchorLatched = false
        bracketsLatched = false
        dataLatched = false
        liveData = null
        visibility = VISIBLE
        invalidate()
    }

    fun retarget(bounds: RectF, origin: RectF = source, protected: List<RectF> = protectedBounds) {
        stage.set(bounds)
        source.set(origin)
        originX = source.centerX()
        originY = source.centerY()
        protectedBounds = protected.map(::RectF)
        invalidate()
    }

    /**
     * Reuses this exact overlay for a stronger scene in the same turn.
     * Geometry and clock stay in place; only the profile changes.
     */
    fun morphInPlace(bounds: RectF, scene: JarvisActionScenes.Scene) {
        val profileChanged = profile != scene.fuiProfile || accent != scene.accent
        stage.set(bounds)
        if (!profileChanged) {
            invalidate()
            return
        }
        profile = scene.fuiProfile
        if (accent != scene.accent) cinematics = HollywoodFuiCinematics.Painterly(scene.accent)
        accent = scene.accent
        invalidate()
    }

    /** Decorative glass only; native text, focus and hitboxes never shift. */
    fun setParallax(x: Float, y: Float) {
        parallaxX = if (x.isFinite()) x.coerceIn(-1f, 1f) else 0f
        parallaxY = if (y.isFinite()) y.coerceIn(-1f, 1f) else 0f
    }

    fun setWorkActive(value: Boolean) {
        if (active == value) return
        active = value
        invalidate()
        requestFrame()
    }

    /** Couples motes, bloom and the live frame cadence to the cockpit's current render budget. */
    fun setRenderBudget(value: JarvisRenderQuality.Budget) {
        val next = HollywoodFuiCinematics.Budget.from(value)
        if (budget == next) return
        budget = next
        invalidate()
        requestFrame()
    }

    /** Current frame cadence of the live (working) overlay in ms; 16 at full budget, longer when hot or saving power. */
    fun liveFrameDelayMs(): Long = maxOf(budget.frameDelayMs.coerceAtLeast(MIN_FRAME_DELAY_MS),
        if (hasWindowFocus()) MIN_FRAME_DELAY_MS else 50L)

    fun setTelemetryText(value: String?) {
        val cleaned = value?.filterNot(Char::isISOControl)?.trim()?.take(64)?.ifEmpty { null }
        if (telemetryText == cleaned) return
        telemetryText = cleaned
        invalidate()
    }

    fun setLiveData(value: HollywoodFuiLiveData?) {
        if (liveData == value) return
        liveData = value
        invalidate()
    }

    fun close(immediate: Boolean) {
        active = false
        if (immediate) visibility = GONE
    }

    /** The native window supplies the only clock, including close and Android animator scale. */
    internal fun present(elapsed: Long, closing: Long, reduced: Boolean, open: Float): Boolean {
        elapsedMs = elapsed
        closingElapsedMs = closing
        reducedMotion = reduced || !budget.animate
        openness = open
        if (!cinematicEffectsEnabled) return false
        if (!reducedMotion && closing < 0L) emitPhaseFeedback(elapsed)
        invalidate()
        return budget.needsLiveFrames(active, reducedMotion,
            isAttachedToWindow && isShown && windowVisibility == VISIBLE,
            cinematicEffectsEnabled, closing >= 0L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!cinematicEffectsEnabled || stage.isEmpty || visibility != VISIBLE) return
        val frame = HollywoodFuiConstruct.frame(elapsedMs, closingElapsedMs, reducedMotion)
        if (frame.closeFactor <= 0f) return

        val cx = stage.centerX()
        val constructed = stage
        val density = resources.displayMetrics.density
        val side = if (originX.isNaN() || abs(originX - cx) < dp(8f)) 0f else if (originX < cx) -1f else 1f
        val bloom = budget.bloom && !reducedMotion

        // Whole construct in perspective: swings in around its vertical axis from the anchor side, folds away again.
        canvas.save()
        protectedBounds.forEach { canvas.clipOutRect(it) }
        painter.begin(canvas)
        canvas.save()
        canvas.scale(density, density)
        val boxDp = HollywoodFuiCinematics.Box(stage.left / density, stage.top / density,
            stage.right / density, stage.bottom / density)
        // The source is in screen space, never rotated with the arriving glass.
        cinematics.drawAnchor(painter, originX / density, originY / density, boxDp, frame, bloom)
        canvas.restore()
        canvas.translate((source.left - stage.left) * (1f - openness), (source.top - stage.top) * (1f - openness))
        canvas.scale((source.width() + (stage.width() - source.width()) * openness) / stage.width(),
            (source.height() + (stage.height() - source.height()) * openness) / stage.height(), stage.left, stage.top)
        val tilt = HollywoodFuiConstruct.orientedTilt(frame.tiltDegrees, side, reducedMotion)
        if (tilt != 0f) {
            camera.save()
            camera.setLocation(0f, 0f, -JarvisMotion.Depth.CAMERA_DISTANCE_DP / 72f)
            camera.rotateY(tilt)
            camera.getMatrix(cameraMatrix)
            camera.restore()
            cameraMatrix.preTranslate(-cx, -stage.centerY())
            cameraMatrix.postTranslate(cx, stage.centerY())
            canvas.concat(cameraMatrix)
        }

        // Painter layers in dp: anchor + beam, glass depth sheets, grid/scan, rim energy and motes.
        canvas.save()
        canvas.scale(density, density)
        if (!reducedMotion && active) canvas.translate(parallaxX * 5f, parallaxY * 3.5f)
        val radiusDp = 6f
        cinematics.drawGlassLayers(painter, boxDp, frame, radiusDp, budget.glassLayers)
        canvas.restore()

        // Profile data stays on the proven Canvas path, scaled with the close choreography (data leaves first).
        canvas.save()
        canvas.clipRect(constructed)
        canvas.save()
        canvas.scale(density, density)
        val raster = HollywoodFuiWorkActivity.rasterOpacity(frame.grid, frame.data,
            active, !reducedMotion && budget.animate)
        cinematics.drawGridAndScan(painter, boxDp,
            frame.copy(scanEnabled = frame.scanEnabled && !reducedMotion), bloom,
            innerRasterAlpha = raster)
        canvas.restore()
        // The native accessible body takes over. Never keep a second text plane behind its labels.
        val dataAlpha = frame.data * (1f - frame.data) * frame.closeData
        if (dataAlpha > 0f) drawProfile(canvas, constructed, dataAlpha)
        canvas.restore()

        canvas.save()
        canvas.scale(density, density)
        cinematics.drawMotesAndEnergy(painter, boxDp, frame, elapsedMs,
            if (!reducedMotion && (active || !frame.settled)) budget.motes else 0,
            moteSeed, working = active && !reducedMotion, bloom = bloom, radius = radiusDp)
        cinematics.drawBrackets(painter, boxDp, frame.brackets * frame.closeFrame, bloom)
        canvas.restore()

        if (dataAlpha > 0f) drawTelemetry(canvas, constructed, dataAlpha)
        canvas.restore()

    }

    private fun emitPhaseFeedback(elapsed: Long) {
        if (!anchorLatched && elapsed >= HollywoodFuiConstruct.ANCHOR_END_MS) {
            anchorLatched = true
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (!bracketsLatched && elapsed >= HollywoodFuiConstruct.BRACKETS_END_MS) {
            bracketsLatched = true
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (!dataLatched && elapsed >= HollywoodFuiConstruct.GRID_END_MS) {
            dataLatched = true
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
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
        val data = liveData as? HollywoodFuiLiveData.Document
        val pages = (data?.pageCount ?: 1).coerceIn(1, 4)
        val pageWidth = min(box.width() * 0.24f, dp(96f))
        val pageHeight = min(box.height() * 0.68f, dp(132f))
        for (index in pages - 1 downTo 0) {
            val offset = dp(10f) * index
            val page = RectF(
                box.left + offset,
                box.centerY() - pageHeight / 2f - offset * .35f,
                box.left + offset + pageWidth,
                box.centerY() + pageHeight / 2f - offset * .35f
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = withAlpha(
                if (index == 0) accent else JarvisDesign.Colors.violet,
                (0.55f - index * .09f) * alpha
            )
            canvas.drawRoundRect(page, dp(3f), dp(3f), paint)
        }
        data ?: return
        val textLeft = box.left + pageWidth + dp(24f)
        drawSmallLabel(canvas, data.documentName, textLeft, box.top + dp(20f), alpha, accent)
        data.question?.let {
            drawWrappedLines(canvas, listOf(it), textLeft, box.top + dp(44f), box.right, alpha)
        }
        data.entriesAdded?.let {
            drawSmallLabel(canvas, "$it bestätigte Einträge", textLeft, box.bottom - dp(18f), alpha, JarvisDesign.Colors.ok)
        }
    }

    private fun drawAppConduit(canvas: Canvas, box: RectF, alpha: Float) {
        val radius = min(box.width(), box.height()) * .19f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = withAlpha(accent, .48f * alpha)
        drawHexagon(canvas, box.centerX(), box.centerY(), radius, paint)
        paint.color = withAlpha(JarvisDesign.Colors.violet, .30f * alpha)
        drawHexagon(canvas, box.centerX(), box.centerY(), radius * 1.35f, paint)

        val data = liveData as? HollywoodFuiLiveData.App ?: return
        drawSmallLabel(canvas, data.provider, box.left, box.top + dp(18f), alpha, accent)
        drawSmallLabel(canvas, data.accountLabel, box.left, box.top + dp(38f), alpha, JarvisDesign.Colors.text)
    }

    private fun drawCodeForge(canvas: Canvas, box: RectF, alpha: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = withAlpha(accent, .42f * alpha)
        canvas.drawRoundRect(box, dp(4f), dp(4f), paint)
        val timeline = liveData as? HollywoodFuiLiveData.Timeline
        drawTimelineLines(canvas, timeline?.lines.orEmpty(), box, alpha, prefixAgents = false)
    }

    private fun drawSwarm(canvas: Canvas, box: RectF, alpha: Float) {
        val lines = (liveData as? HollywoodFuiLiveData.Timeline)?.lines.orEmpty().take(6)
        if (lines.isEmpty()) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = withAlpha(accent, .28f * alpha)
            canvas.drawCircle(box.centerX(), box.centerY(), dp(7f), paint)
            return
        }
        val cardWidth = box.width() * .28f
        val cardHeight = box.height() * .30f
        val centreX = box.centerX()
        val centreY = box.centerY()
        lines.forEachIndexed { index, line ->
            val column = index % 3
            val row = index / 3
            val left = box.left + column * (box.width() - cardWidth) / 2f
            val top = box.top + row * (box.height() - cardHeight)
            val card = RectF(left, top, left + cardWidth, top + cardHeight)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = withAlpha(stateColor(line.state), .52f * alpha)
            canvas.drawRoundRect(card, dp(4f), dp(4f), paint)
            canvas.drawLine(card.centerX(), card.centerY(), centreX, centreY, paint)
            drawSmallLabel(
                canvas,
                "SCHRITT %02d".format(Locale.ROOT, index + 1),
                card.left + dp(6f),
                card.top + dp(15f),
                alpha,
                stateColor(line.state)
            )
            drawSmallLabel(canvas, line.label, card.left + dp(6f), card.top + dp(31f), alpha, JarvisDesign.Colors.text)
        }
        paint.color = withAlpha(JarvisDesign.Colors.glowCore, .72f * alpha)
        canvas.drawCircle(centreX, centreY, dp(5f), paint)
    }

    private fun drawEvidence(canvas: Canvas, box: RectF, alpha: Float) {
        val evidence = liveData as? HollywoodFuiLiveData.Evidence
        val timeline = liveData as? HollywoodFuiLiveData.Timeline
        val count = evidence?.count?.coerceIn(0, 8) ?: 0
        if (evidence != null && count > 0) {
            val cx = box.centerX()
            val cy = box.centerY()
            val radiusX = box.width() * .34f
            val radiusY = box.height() * .31f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            val points = (0 until count).map { index ->
                val angle = index * (2.0 * PI / count.toDouble())
                Pair(cx + cos(angle).toFloat() * radiusX, cy + sin(angle).toFloat() * radiusY)
            }
            paint.color = withAlpha(accent, .34f * alpha)
            points.forEachIndexed { index, point ->
                if (points.size > 1) {
                    val next = points[(index + 1) % points.size]
                    canvas.drawLine(point.first, point.second, next.first, next.second, paint)
                }
                canvas.drawCircle(point.first, point.second, dp(4f), paint)
            }
            drawSmallLabel(canvas, evidence.sourceLabel, box.left, box.top + dp(18f), alpha, accent)
            evidence.topScore?.let {
                drawSmallLabel(canvas, "TOP ${formatNumber(it.toDouble())}", box.left, box.top + dp(38f), alpha, JarvisDesign.Colors.ok)
            }
        } else {
            drawTimelineLines(canvas, timeline?.lines.orEmpty(), box, alpha, prefixAgents = false)
        }
    }

    private fun drawChrono(canvas: Canvas, box: RectF, alpha: Float) {
        val data = liveData as? HollywoodFuiLiveData.Calculation
        val size = min(box.width(), box.height()) * .52f
        val gauge = RectF(box.left, box.centerY() - size / 2f, box.left + size, box.centerY() + size / 2f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.color = withAlpha(accent, .52f * alpha)
        canvas.drawArc(gauge, 140f, 260f, false, paint)
        data ?: return

        drawSmallLabel(canvas, data.expression, box.left, box.top + dp(18f), alpha, accent)
        drawSmallLabel(
            canvas,
            formatNumber(data.result) + (data.unit?.let { " $it" } ?: ""),
            gauge.centerX() - dp(18f),
            gauge.centerY() + dp(5f),
            alpha,
            JarvisDesign.Colors.ok
        )

        val maxAbs = data.steps.maxOfOrNull { kotlin.math.abs(it.value) }?.takeIf { it > 0.0 } ?: 1.0
        val timelineElapsed = elapsedMs
        data.steps.take(4).forEachIndexed { index, step ->
            val reveal = ((timelineElapsed - HollywoodFuiConstruct.DATA_END_MS - index * 80L) / 180f).coerceIn(0f, 1f)
            val barLeft = box.left + size + dp(16f)
            val y = box.top + box.height() * (index + 1f) / 5f
            val fraction = (kotlin.math.abs(step.value) / maxAbs).toFloat().coerceIn(0f, 1f) * reveal
            paint.strokeWidth = dp(3f)
            paint.color = withAlpha(JarvisDesign.Colors.violet, (.30f + index * .08f) * alpha)
            canvas.drawLine(barLeft, y, barLeft + box.width() * .28f * fraction, y, paint)
            drawSmallLabel(canvas, step.label, barLeft, y - dp(5f), alpha, JarvisDesign.Colors.text)
        }
    }

    private fun drawSecurity(canvas: Canvas, box: RectF, alpha: Float) {
        val phoneWidth = min(box.width() * .28f, dp(82f))
        val phoneHeight = min(box.height() * .70f, dp(150f))
        val phone = RectF(
            box.centerX() - phoneWidth / 2f,
            box.centerY() - phoneHeight / 2f,
            box.centerX() + phoneWidth / 2f,
            box.centerY() + phoneHeight / 2f
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = withAlpha(accent, .52f * alpha)
        canvas.drawRoundRect(phone, dp(9f), dp(9f), paint)
        canvas.drawLine(phone.centerX(), phone.top + dp(6f), phone.centerX(), phone.bottom - dp(6f), paint)
        canvas.drawLine(phone.left + dp(6f), phone.centerY(), phone.right - dp(6f), phone.centerY(), paint)

        val data = liveData as? HollywoodFuiLiveData.Security ?: return
        drawSmallLabel(canvas, data.sourceLabel, box.left, box.top + dp(18f), alpha, accent)
        val measurement = listOfNotNull(
            data.measured?.let { "IST ${formatNumber(it)}" },
            data.threshold?.let { "LIMIT ${formatNumber(it)}" }
        ).joinToString(" · ")
        if (measurement.isNotEmpty()) {
            drawSmallLabel(canvas, measurement, box.left, box.top + dp(38f), alpha, JarvisDesign.Colors.text)
        }
    }

    private fun drawGeneric(canvas: Canvas, box: RectF, alpha: Float) {
        val size = min(box.width(), box.height()) * .46f
        val ring = RectF(
            box.centerX() - size, box.centerY() - size * .55f,
            box.centerX() + size, box.centerY() + size * .55f
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.1f)
        paint.color = withAlpha(accent, .40f * alpha)
        canvas.drawOval(ring, paint)
        drawTimelineLines(
            canvas,
            (liveData as? HollywoodFuiLiveData.Timeline)?.lines.orEmpty(),
            box,
            alpha,
            prefixAgents = false
        )
    }

    private fun drawTimelineLines(
        canvas: Canvas,
        lines: List<HollywoodFuiLiveData.Timeline.Line>,
        box: RectF,
        alpha: Float,
        prefixAgents: Boolean
    ) {
        lines.take(8).forEachIndexed { index, line ->
            val y = box.top + dp(18f + index * 18f)
            if (y > box.bottom) return@forEachIndexed
            val prefix = if (prefixAgents) "SCHRITT %02d · ".format(Locale.ROOT, index + 1) else ""
            drawSmallLabel(canvas, prefix + line.label, box.left + dp(6f), y, alpha, stateColor(line.state))
        }
    }

    private fun drawWrappedLines(
        canvas: Canvas,
        lines: List<String>,
        left: Float,
        top: Float,
        right: Float,
        alpha: Float
    ) {
        var y = top
        lines.flatMap { line ->
            val words = line.split(Regex("\\s+"))
            val result = mutableListOf<String>()
            var current = ""
            words.forEach { word ->
                val next = if (current.isEmpty()) word else "$current $word"
                paint.textSize = dp(10f)
                if (paint.measureText(next) <= right - left || current.isEmpty()) current = next
                else { result += current; current = word }
            }
            if (current.isNotEmpty()) result += current
            result
        }.take(4).forEach { line ->
            drawSmallLabel(canvas, line, left, y, alpha, JarvisDesign.Colors.text)
            y += dp(16f)
        }
    }

    private fun drawSmallLabel(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        alpha: Float,
        color: Int
    ) {
        if (text.isBlank()) return
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = android.graphics.Typeface.MONOSPACE
        paint.textSize = dp(10f)
        paint.color = withAlpha(color, .88f * alpha)
        canvas.drawText(text.take(72), x, y, paint)
    }

    private fun stateColor(state: StepState): Int = when (state) {
        StepState.DONE -> JarvisDesign.Colors.ok
        StepState.ACTIVE -> accent
        StepState.WAITING -> JarvisDesign.Colors.violet
        StepState.BLOCKED, StepState.FAILED -> JarvisDesign.Colors.magenta
    }

    private fun formatNumber(value: Double): String =
        String.format(Locale.GERMANY, if (kotlin.math.abs(value) >= 100.0) "%.0f" else "%.2f", value)

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

    private fun drawTelemetry(canvas: Canvas, box: RectF, alpha: Float) {
        val text = telemetryText ?: return
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = dp(10f)
        paint.typeface = android.graphics.Typeface.MONOSPACE
        paint.color = withAlpha(JarvisDesign.Colors.glowCore, 0.88f * alpha)
        canvas.drawText(text, box.right - dp(10f), box.top + dp(16f), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (Color.alpha(color) * alpha.coerceIn(0f, 1f)).roundToInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        /** Legacy cadence kept for callers; live cadence now follows [liveFrameDelayMs]. */
        const val ACTIVE_FRAME_DELAY_MS = 33L
        const val MIN_FRAME_DELAY_MS = 16L
    }
}
