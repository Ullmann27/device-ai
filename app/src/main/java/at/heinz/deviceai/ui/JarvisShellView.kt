package at.heinz.deviceai.ui

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import at.heinz.deviceai.cockpit.WindowProjectedMotion

/**
 * Drop-in View that lays out [JarvisShellLayout] for its own size and draws it with [JarvisShellRenderer]
 * plus native glass ([JarvisGlassRenderer]: backdrop blur API 31+, AGSL background/rim API 33+).
 * Jarvis Kern: put content into [content] / [tiles], call [playEntrance] on a command event.
 */
class JarvisShellView(context: Context) : View(context) {
    private val painter = AndroidContinuumPainter(context.assets)
    private val textPainter = JarvisTextScalePainter(painter)
    private val glass = JarvisGlassRenderer(painter)
    /** The separate foreground layer follows this layer's invalidation without owning an animation clock. */
    var onFrameDrawn: (() -> Unit)? = null
    internal var motionBudgetProvider: (() -> JarvisRenderQuality.Budget)? = null
    private val motionFrames = linkedSetOf<(Long, Float) -> Boolean>()
    private val spatialPresence = SpatialPresenceAndLightBridge(context) { invalidate() }
    internal val spatialTiltX: Float get() = spatialPresence.tiltX
    internal val spatialTiltY: Float get() = spatialPresence.tiltY

    /** Shares the existing draw clock. */
    fun observeMotionFrames(listener: (Long, Float) -> Boolean): AutoCloseable {
        motionFrames.add(listener)
        invalidate()
        return AutoCloseable { motionFrames.remove(listener) }
    }
    var selectedDockTarget: String = "home"
        set(value) { field = value; invalidate() }
    var content: Map<JarvisShellLayout.Slot, JarvisShellRenderer.SlotContent> = emptyMap()
        set(value) { field = value; invalidate() }
    /** Presentation model of the action tiles (label/icon/target, optional display text from Kern). */
    var tiles: List<JarvisTile> = JarvisTiles.reference
        set(value) { field = value; invalidate() }
    internal var controlPress: JarvisShellRenderer.ControlPress? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    val pressedControlKey: String? get() = controlPress?.key
    val pressedTarget: String? get() = controlPress?.target
    private fun materialPressed() = controlPress?.key?.let {
        it.startsWith("tile:") || it.startsWith("bottom:")
    } == true
    var foldRail: JarvisShellLayout.Rail = JarvisShellLayout.Rail.LABELLED
        set(value) { field = value; invalidate() }
    private var foregroundCinema = JarvisShellRenderer.CinemaFrame()
    private var startMs = -1L
    private var workStartMs = -1L
    private var exitMs = -1L
    private var cinemaTurnId = ""
    private var workClear = false
    private var resultForward = false
    private var workStageTurnId = ""
    private var fromWorkingScene = false
    private var interruptedAfterMs = JarvisCinemaEntrance.WORKING_SETTLED_MS
    private var returnResultElapsedMs = -1L
    private var pendingWorkStageTurnId: String? = null
    private var pendingWorkResultTurnId: String? = null
    private var pendingResultAdvance = false
    var onProjectedMotion: ((WindowProjectedMotion) -> Unit)? = null
    private val projectedMotion = WindowProjectedMotionSampler()
    private var layoutFont = 0f
    private var layoutRail: JarvisShellLayout.Rail? = null
    private var layoutInset = Float.NaN
    private var layoutViewport = Float.NaN
    private var layoutCompact = false
    var compactPresentation = false
        set(value) {
            if (field == value) return
            field = value
            shell = null
            invalidate()
        }
    /** Stable visible height, independent of this View's larger scrolling content height. */
    var viewportHeightDp = 0f
        set(value) {
            require(value.isFinite() && value >= 0f)
            if (field == value) return
            field = value
            invalidate()
        }
    /** Width already occupied by the Activity's functional rail, in window dp. */
    var contentRailInsetDp = 0f
        set(value) {
            require(value.isFinite() && value >= 0f)
            if (field == value) return
            field = value
            invalidate()
        }

    /** Optional decorative refresh. Native head/orb own their frame clock; static HUD needs no continuous redraw. */
    var ambientMotionEnabled = true
        set(value) { field = value; refreshMotion() }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private fun motionAllowed(scale: Float) = isAttachedToWindow && scale > 0f && isShown &&
        windowVisibility == VISIBLE && hasWindowFocus() && !powerManager.isPowerSaveMode &&
        motionBudgetProvider?.invoke()?.animate != false
    private fun ambientMotion(scale: Float) = ambientMotionEnabled && motionAllowed(scale)
    private val ambientFrame = Runnable {
        if (motionAllowed(animatorScale())) invalidate()
    }
    private val motionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshMotion()
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshMotion()
    }
    private var motionObserverRegistered = false
    private var powerReceiverRegistered = false

    private fun refreshMotion() {
        refreshSpatialPresence()
        removeCallbacks(ambientFrame)
        lastFrameStartNanos = 0L
        invalidate()
    }

    private fun refreshSpatialPresence() {
        val enabled = JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_01_Z_GLASS)
        val capable = motionBudgetProvider?.invoke()?.animate == true
        if (enabled && capable && workspaceFootprint != JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE &&
            motionAllowed(animatorScale())) spatialPresence.start() else spatialPresence.stop()
    }

    internal fun refreshMotionPolicy() = refreshMotion()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!motionObserverRegistered) runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
            motionObserverRegistered = true
        }.onFailure { Log.w("JarvisMotion", "Motion observer unavailable", it) }
        if (!powerReceiverRegistered) runCatching {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(powerReceiver, filter)
            powerReceiverRegistered = true
        }.onFailure { Log.w("JarvisMotion", "Power observer unavailable", it) }
        refreshMotion()
    }

    override fun onDetachedFromWindow() {
        spatialPresence.stop()
        removeCallbacks(ambientFrame)
        if (motionObserverRegistered) {
            runCatching { context.contentResolver.unregisterContentObserver(motionObserver) }
            motionObserverRegistered = false
        }
        if (powerReceiverRegistered) {
            runCatching { context.unregisterReceiver(powerReceiver) }
            powerReceiverRegistered = false
        }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isAttachedToWindow) refreshMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) refreshMotion()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) refreshMotion()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (isAttachedToWindow) refreshMotion()
    }

    /** Latest shell geometry (dp) – lets Kern position its own child views on the same slots. */
    var shell: JarvisShellLayout.Shell? = null
        private set

    /** Airbus deck: tiles/secondary and mission rows move on separate bounded tracks. */
    var deckClearance = 0f
        private set
    var missionClearance = 0f
        private set
    private var workspaceFootprint = JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE
    private var deckFrameMs = -1L

    fun setWorkspaceFootprint(footprint: JarvisActionScenes.WorkspaceFootprint) {
        if (workspaceFootprint == footprint) return
        workspaceFootprint = footprint
        deckFrameMs = -1L
        refreshSpatialPresence()
        invalidate()
    }

    fun setDeckCleared(cleared: Boolean) = setWorkspaceFootprint(
        if (cleared) JarvisActionScenes.WorkspaceFootprint.FULL_MISSION_BAY
        else JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE)

    /**
     * Geometry for the size the view has now (dp), from the same cache onDraw uses – so a work window can be planned
     * after a layout even before the next draw. Null while the view is too small for the shell.
     */
    fun currentShell(): JarvisShellLayout.Shell? {
        val d = resources.displayMetrics.density
        val w = width / d; val h = height / d
        if (w < 240f || h < 160f) return null
        val font = resources.configuration.fontScale
        val viewport = viewportHeightDp.takeIf { it >= 160f } ?: h
        return shell?.takeIf { it.width == w && layoutViewport == viewport && layoutFont == font && layoutRail == foldRail && layoutInset == contentRailInsetDp && layoutCompact == compactPresentation }
            ?: JarvisShellLayout.forContent(w, viewport, font, foldRail, contentRailInsetDp,
                compactPresentation = compactPresentation, workstation = true).also {
                shell = it; layoutFont = font; layoutRail = foldRail; layoutInset = contentRailInsetDp; layoutViewport = viewport; layoutCompact = compactPresentation
            }
    }

    fun bindCinemaTurn(turnId: String) {
        cinemaTurnId = turnId
    }

    fun closeProjectedMotion(turnId: String, atMillis: Long) {
        if (pendingWorkStageTurnId == turnId) {
            pendingWorkStageTurnId = null
            pendingWorkResultTurnId = null
        }
        projectedMotion.closeActive(turnId, atMillis).forEach { onProjectedMotion?.invoke(it) }
    }

    fun closeProjectedWindow(windowId: String, atMillis: Long) {
        if (cinemaTurnId.isBlank()) return
        projectedMotion.closed(cinemaTurnId, windowId, atMillis)?.let { onProjectedMotion?.invoke(it) }
    }

    fun offerProjectedMotion(
        windowId: String,
        projectedX: Float,
        projectedY: Float,
        headX: Float,
        headY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        phase: WindowProjectedMotion.Phase,
        accentArgb: Int?,
        reducedMotion: Boolean,
        moving: Boolean,
        atMillis: Long = SystemClock.uptimeMillis(),
        turnId: String = cinemaTurnId,
    ) {
        if (turnId.isBlank()) return
        projectedMotion.consider(
            turnId = turnId,
            atMillis = atMillis,
            windowId = windowId,
            projectedX = projectedX,
            projectedY = projectedY,
            headX = headX,
            headY = headY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            phase = phase,
            accentArgb = accentArgb,
            reducedMotion = reducedMotion,
            moving = moving,
            windowPresent = true,
        )?.let { onProjectedMotion?.invoke(it) }
    }

    fun playEntrance() {
        pendingWorkStageTurnId = null
        pendingWorkResultTurnId = null
        pendingResultAdvance = false
        returnResultElapsedMs = -1L
        workClear = false
        resultForward = false
        workStageTurnId = ""
        fromWorkingScene = false
        interruptedAfterMs = JarvisCinemaEntrance.WORKING_SETTLED_MS
        val now = SystemClock.uptimeMillis()
        startMs = now
        workStartMs = now
        exitMs = -1L
        postInvalidateOnAnimation()
    }

    fun playWorkStage() {
        val now = SystemClock.uptimeMillis()
        if (workClear && startMs >= 0L) {
            if (exitMs >= 0L) {
                if (workStageTurnId != cinemaTurnId && pendingWorkStageTurnId != cinemaTurnId) {
                    pendingWorkStageTurnId = cinemaTurnId
                    pendingWorkResultTurnId = null
                }
                postInvalidateOnAnimation()
                return
            }
            val layout = currentShell()
            val count = layout?.let { it.boxes.size + it.tileBoxes().size } ?: 3
            when (JarvisCinemaEntrance.workOrderReplay(
                workStageTurnId == cinemaTurnId, now - startMs, count, animatorScale())) {
                JarvisCinemaEntrance.WorkOrderReplay.IGNORE -> {
                    postInvalidateOnAnimation()
                    return
                }
                JarvisCinemaEntrance.WorkOrderReplay.CONTINUE_FLIGHT -> {
                    workStageTurnId = cinemaTurnId
                    pendingResultAdvance = false
                    postInvalidateOnAnimation()
                    return
                }
                JarvisCinemaEntrance.WorkOrderReplay.RESTART_FROM_WORKING ->
                    fromWorkingScene = true
            }
        }
        beginWorkStage(cinemaTurnId, now)
    }

    private fun beginWorkStage(turnId: String, now: Long) {
        pendingWorkStageTurnId = null
        pendingWorkResultTurnId = null
        pendingResultAdvance = false
        returnResultElapsedMs = -1L
        workClear = true
        resultForward = false
        workStageTurnId = turnId
        startMs = now
        workStartMs = now
        exitMs = -1L
        postInvalidateOnAnimation()
    }

    private fun completeReturn(now: Long) {
        val next = pendingWorkStageTurnId
        val nextResult = next != null && pendingWorkResultTurnId == next
        pendingWorkStageTurnId = null
        pendingWorkResultTurnId = null
        val previous = workStageTurnId.ifBlank { cinemaTurnId }
        if (previous.isNotBlank()) closeProjectedMotion(previous, now)
        workClear = false
        resultForward = false
        pendingResultAdvance = false
        workStageTurnId = ""
        fromWorkingScene = false
        interruptedAfterMs = JarvisCinemaEntrance.WORKING_SETTLED_MS
        returnResultElapsedMs = -1L
        startMs = -1L
        workStartMs = -1L
        exitMs = -1L
        if (next != null) {
            cinemaTurnId = next
            beginWorkStage(next, now)
            pendingResultAdvance = nextResult
        }
    }

    fun playWorkWindowEntrance() {
        if (exitMs >= 0L) {
            if (pendingWorkStageTurnId != null && pendingWorkStageTurnId == cinemaTurnId) {
                pendingWorkResultTurnId = cinemaTurnId
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        val layout = currentShell()
        val count = layout?.let { it.boxes.size + it.tileBoxes().size } ?: 3
        if (workClear && startMs >= 0L && now - startMs <
            JarvisCinemaEntrance.workOrderDurationMs(count, animatorScale())) {
            pendingResultAdvance = true
            postInvalidateOnAnimation()
            return
        }
        resultForward = workClear
        workStartMs = now
        exitMs = -1L
        postInvalidateOnAnimation()
    }

    fun playExit() {
        pendingResultAdvance = false
        val now = SystemClock.uptimeMillis()
        if (exitMs >= 0L) {
            postInvalidateOnAnimation()
            return
        }
        interruptedAfterMs = if (workClear && startMs >= 0L) {
            (now - startMs).coerceAtLeast(0L)
        } else {
            JarvisCinemaEntrance.WORKING_SETTLED_MS
        }
        returnResultElapsedMs = if (resultForward && workStartMs >= 0L)
            (now - workStartMs).coerceAtLeast(0L) else -1L
        exitMs = now
        resultForward = false
        postInvalidateOnAnimation()
    }

    private fun animatorScale(): Float =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

    override fun onDraw(canvas: Canvas) {
        val drawStartedNanos = if (frameLogging) System.nanoTime() else 0L
        val d = resources.displayMetrics.density
        val w = width / d; val h = height / d
        if (w < 240f || h < 160f) return
        val font = resources.configuration.fontScale
        val s = currentShell() ?: return
        val scale = animatorScale()
        val now = SystemClock.uptimeMillis()
        var exiting = exitMs >= 0L
        var elapsed = when {
            exiting -> now - exitMs
            startMs < 0 -> Long.MAX_VALUE / 4
            else -> now - startMs
        }
        if (exiting && workClear) {
            val tearDown = JarvisCinemaEntrance.returnDurationMs(
                s.boxes.size + s.tileBoxes().size, interruptedAfterMs, animatorScale = scale)
            if (scale <= 0f || elapsed >= tearDown) {
                completeReturn(now)
                exiting = false
                elapsed = if (workClear) 0L else Long.MAX_VALUE / 4
            }
        }
        if (pendingResultAdvance && workClear && !exiting && elapsed >=
            JarvisCinemaEntrance.workOrderDurationMs(s.boxes.size + s.tileBoxes().size, scale)) {
            pendingResultAdvance = false
            resultForward = true
            workStartMs = now
        }
        val workElapsed = when {
            exiting -> now - exitMs
            workStartMs >= 0L -> now - workStartMs
            else -> elapsed
        }
        painter.begin(canvas); glass.begin(canvas, d, width, height)
        textPainter.fontScale = font
        canvas.save(); canvas.scale(d, d)
        val sceneTime = if (ambientMotion(scale)) now else 0L
        val materialTime = if (motionAllowed(scale)) now else 0L
        val tileTarget = if (workspaceFootprint == JarvisActionScenes.WorkspaceFootprint.CONVERSATION_IDLE) 0f else 1f
        val missionTarget = if (workspaceFootprint == JarvisActionScenes.WorkspaceFootprint.FULL_MISSION_BAY) 1f else 0f
        val deckMoving = kotlin.math.abs(deckClearance - tileTarget) > .0001f ||
            kotlin.math.abs(missionClearance - missionTarget) > .0001f
        if (deckMoving) {
            val frame = if (deckFrameMs < 0L) 0L else now - deckFrameMs
            val elapsed = (frame / scale.coerceAtLeast(.05f)).toLong()
            val animate = motionAllowed(scale)
            deckClearance = JarvisDeckClearance.step(deckClearance, tileTarget, elapsed, animate)
            missionClearance = JarvisDeckClearance.step(missionClearance, missionTarget, elapsed, animate)
        }
        deckFrameMs = now
        val luxuryDepth = JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_13_LUXURY)
        val wings = JarvisCinematicSettings.experimentEnabled(context,
            JarvisCinematicSettings.Experiment.EXP_05_WINGS)
        val depthBudget = motionBudgetProvider?.invoke()
        foregroundCinema = JarvisShellRenderer.CinemaFrame(elapsed, scale, workClear, exiting,
            interruptedAfterMs, fromWorkingScene, depthBudget, sceneTime, returnResultElapsedMs, workElapsed, resultForward)
        JarvisShellRenderer.draw(textPainter, s, content, elapsed, scale, tiles, glass, sceneTime, deferForeground = true,
            deckClearance = deckClearance, missionClearance = missionClearance,
            luxuryDepth = luxuryDepth, depthBudget = depthBudget, enableWings = wings,
            exiting = exiting, workElapsedMs = workElapsed,
            clearIdle = workClear, resultForward = resultForward,
            fromWorkingScene = fromWorkingScene, interruptedAfterMs = interruptedAfterMs,
            returnResultElapsedMs = returnResultElapsedMs, controlPress = controlPress, materialTimeMs = materialTime, selectedDockTarget = selectedDockTarget)
        publishWorkWindowMotion(s, elapsed, workElapsed, exiting, scale, now, depthBudget)
        canvas.restore()
        var workWindowMoving = false
        val effectiveScale = if (motionAllowed(scale)) scale else 0f
        motionFrames.toList().forEach { listener ->
            if (listener(now, effectiveScale)) workWindowMoving = true
        }
        onFrameDrawn?.invoke()
        val count = s.boxes.size + s.tileBoxes().size
        val total = maxOf(
            JarvisMotion.groupDurationMs(JarvisMotion.Presets.panelLeft, count, animatorScale = scale),
            when {
                workClear && exiting -> JarvisCinemaEntrance.returnDurationMs(
                    count, interruptedAfterMs, animatorScale = scale)
                workClear && !exiting -> JarvisCinemaEntrance.workOrderDurationMs(count, animatorScale = scale)
                else -> JarvisCinemaEntrance.ensembleDurationMs(count, animatorScale = scale, exiting = exiting)
            })
        // Invalidate only for an explicit entrance/decorative mode. Status updates invalidate through state setters.
        removeCallbacks(ambientFrame)
        val deckStillMoving = kotlin.math.abs(deckClearance - tileTarget) > .0001f ||
            kotlin.math.abs(missionClearance - missionTarget) > .0001f
        val frameDelay = motionBudgetProvider?.invoke()?.frameDelayMillis ?: 16L
        val cinemaRunning = if (exiting) exitMs >= 0 && elapsed < total
            else (startMs >= 0 || workStartMs >= 0) && elapsed < total
        if (motionAllowed(scale) && (workWindowMoving || deckStillMoving || cinemaRunning || materialPressed())) {
            if (frameDelay <= 16L) postInvalidateOnAnimation() else postDelayed(ambientFrame, frameDelay)
        } else if (ambientMotion(scale)) postDelayed(ambientFrame, maxOf(66L, frameDelay))
        if (exiting && (elapsed >= total || !motionAllowed(scale))) {
            // A completed fly-out must not leave the input, dock and status permanently hidden.
            // Reduced motion, no focus or power save can skip the rendered CLOSED phase.
            // Close any remaining projected windows before restoring the settled home.
            completeReturn(now)
            postInvalidateOnAnimation()
        }
        if (frameLogging) recordFrame(drawStartedNanos, System.nanoTime())
    }

    /** Same geometry and text metrics, rendered above the transparent native avatar but below its hit targets. */
    fun drawForeground(canvas: Canvas) {
        val s = shell ?: return
        val d = resources.displayMetrics.density
        painter.begin(canvas)
        glass.begin(canvas, d, width, height)
        textPainter.fontScale = resources.configuration.fontScale
        val save = canvas.save()
        canvas.scale(d, d)
        JarvisShellRenderer.drawForeground(textPainter, s, content, deckClearance, glass, foregroundCinema)
        canvas.restoreToCount(save)
    }

    /** Observes draws without scheduling frames. */
    var frameLogging = false
        set(value) {
            if (field == value) return
            field = value
            lastFrameStartNanos = 0L
            if (value) frameSample = FrameSample()
        }

    data class FrameSample(
        val draws: Long = 0L,
        val intervals: Long = 0L,
        val intervalNanos: Long = 0L,
        val maxIntervalNanos: Long = 0L,
        val drawNanos: Long = 0L
    ) {
        val averageIntervalMs: Double? get() = if (intervals > 0) intervalNanos / intervals / 1e6 else null
        val averageDrawMs: Double? get() = if (draws > 0) drawNanos / draws / 1e6 else null
    }

    private var lastFrameStartNanos = 0L
    private var frameSample = FrameSample()
    fun sampleFrames(): FrameSample = frameSample

    private fun recordFrame(start: Long, end: Long) {
        val interval = if (lastFrameStartNanos > 0L) (start - lastFrameStartNanos).coerceAtLeast(0L) else 0L
        val before = frameSample
        frameSample = FrameSample(before.draws + 1, before.intervals + if (lastFrameStartNanos > 0L) 1 else 0,
            before.intervalNanos + interval, maxOf(before.maxIntervalNanos, interval),
            before.drawNanos + (end - start).coerceAtLeast(0L))
        lastFrameStartNanos = start
        if (frameSample.draws % 120L == 0L) {
            Log.d("JarvisFrames", "draws=%d avg=%.2fms max=%.2fms onDraw=%.2fms".format(
                java.util.Locale.ROOT, frameSample.draws, frameSample.averageIntervalMs ?: 0.0,
                frameSample.maxIntervalNanos / 1e6, frameSample.averageDrawMs ?: 0.0))
        }
    }

    private fun publishWorkWindowMotion(
        s: JarvisShellLayout.Shell,
        elapsedMs: Long,
        workElapsedMs: Long,
        exiting: Boolean,
        animatorScale: Float,
        now: Long,
        budget: JarvisRenderQuality.Budget?,
    ) {
        if (cinemaTurnId.isBlank() || onProjectedMotion == null) return
        if (!exiting && startMs < 0L && workStartMs < 0L) return
        val head = s.headStage ?: s.headBounds
        val headX = head?.centerX ?: s.width / 2f
        val headY = head?.centerY ?: s.height * .28f
        val cx = s.width / 2f
        val reduced = animatorScale <= 0f || budget?.animate == false
        for (slot in listOf(
            JarvisShellLayout.Slot.WORK_DOCUMENT,
            JarvisShellLayout.Slot.WORK_PROCESS,
            JarvisShellLayout.Slot.WORK_DRAFT,
        )) {
            val box = s[slot] ?: continue
            val windowId = WindowProjectedMotionSampler.windowIdOf(slot) ?: continue
            val rank = JarvisCinemaEntrance.rankOf(slot, 0)
            val side = if (box.centerX >= cx) 1f else -1f
            val cinema = when {
                exiting && workClear ->
                    JarvisCinemaEntrance.returnPose(
                        slot, rank, elapsedMs, interruptedAfterMs, side, budget, animatorScale, fromWorkingScene,
                        returnResultElapsedMs)
                !exiting && workClear && resultForward && slot == JarvisShellLayout.Slot.WORK_DRAFT ->
                    JarvisCinemaEntrance.resultAdvance(workElapsedMs, budget, animatorScale)
                !exiting && workClear && resultForward ->
                    JarvisCinemaEntrance.settledPose(
                        JarvisCinemaEntrance.Kind.WORK_WINDOW, rank,
                        side = side, budget = budget, animatorScale = animatorScale)
                !exiting && workClear ->
                    JarvisCinemaEntrance.workOrderPose(
                        slot, rank, elapsedMs, side, budget, animatorScale,
                        fromWorkingScene = fromWorkingScene)
                else -> JarvisCinemaEntrance.pose(
                    JarvisCinemaEntrance.Kind.WORK_WINDOW, rank, workElapsedMs, exiting,
                    side = side, budget = budget, animatorScale = animatorScale)
            }
            val f = if (workClear) JarvisMotion.REST else JarvisMotion.frame(
                JarvisMotion.panelSpecFor(box.centerX, cx),
                elapsedMs - JarvisMotion.staggerStart(rank, animatorScale = animatorScale),
                animatorScale,
            )
            val phase = when {
                exiting && (cinema.hidden || cinema.travel <= 0.02f) ->
                    WindowProjectedMotion.Phase.CLOSED
                exiting -> WindowProjectedMotion.Phase.CLOSING
                cinema.settled -> WindowProjectedMotion.Phase.SETTLED
                cinema.hidden -> continue
                else -> WindowProjectedMotion.Phase.FLYING
            }
            offerProjectedMotion(
                windowId = windowId,
                projectedX = box.centerX + f.dx + cinema.dx,
                projectedY = box.centerY + f.dy + cinema.dy,
                headX = headX,
                headY = headY,
                viewportWidth = s.width,
                viewportHeight = s.height,
                phase = phase,
                accentArgb = null,
                reducedMotion = reduced,
                moving = !cinema.settled && !cinema.hidden,
                atMillis = now,
                turnId = if (exiting && workClear) workStageTurnId else cinemaTurnId,
            )
        }
    }
}
