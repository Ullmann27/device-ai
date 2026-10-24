package at.heinz.deviceai

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import at.heinz.deviceai.ui.AndroidContinuumPainter
import at.heinz.deviceai.ui.ContinuumHologramRenderer
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Hardware accelerated living Continuum core. Drawing is done by [ContinuumHologramRenderer] (shared with the JVM
 * preview); this view keeps the real state, speech energy, reduced-motion and power-save policies.
 */
class ContinuumHologramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    enum class State { READY, LISTENING, THINKING, SPEAKING, RESEARCH, LEARNING }

    private val renderer = ContinuumHologramRenderer()
    private val painter = AndroidContinuumPainter(context.assets)
    private val audio = HologramAudioEnvelope()
    // State order equals ContinuumHologramRenderer.Mode order (READY … LEARNING).
    private val stateWeights = FloatArray(State.entries.size).apply { this[0] = 1f }
    private val frameWeights = FloatArray(State.entries.size)
    private val renderFrame = ContinuumHologramRenderer.Frame(.4f, .18f, 0f, frameWeights)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var currentState = State.READY
    @Volatile private var pendingAudioLevel = 0f
    @Volatile private var pendingAudioMs = 0L
    private var consumedAudioMs = 0L
    private var animationsEnabled = true
    // A visible window is not proof that its Activity is still foregrounded.
    // The owner Activity explicitly pauses/resumes this one animation loop.
    private var foregroundActive = false
    private var motionAllowed = false
    private var aggregatedVisible = false
    private var framePending = false
    private var observersRegistered = false
    private var lastFrameMs = 0L
    private var phase = .4f
    private var energy = .18f
    private var audioEnergy = 0f

    private val drawFrame = Runnable {
        framePending = false
        if (canAnimate()) {
            invalidate()
            scheduleFrame()
        }
    }

    /** Owns the single visible-view frame callback, even before the first draw. */
    private fun scheduleFrame() {
        if (canAnimate() && !framePending) {
            framePending = true
            postOnAnimationDelayed(drawFrame, if (currentState == State.READY) 16L else 8L)
        }
    }
    private val motionObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) = updateMotionPolicy()
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateMotionPolicy()
    }

    init {
        // State has an accessible text label beside the hero; avoid a duplicate announcement.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    /** Call on the UI thread. State changes crossfade over the existing orb. */
    fun setState(state: State) {
        if (state == currentState) return
        currentState = state
        pendingAudioLevel = 0f
        pendingAudioMs = 0L
        consumedAudioMs = 0L
        audio.reset()
        audioEnergy = 0f
        if (!canAnimate()) settleState()
        invalidate()
    }

    /** Safe from a TTS callback thread. Supply actual PCM RMS in the range 0..1. */
    fun setAudioLevel(level: Float) {
        if (currentState != State.SPEAKING) return
        pendingAudioLevel = if (level.isFinite()) level.coerceIn(0f, 1f) else 0f
        pendingAudioMs = SystemClock.uptimeMillis()
    }

    /** Explicit Activity lifecycle. Keeps phase continuity without background callbacks. */
    fun setForegroundActive(active: Boolean) {
        if (foregroundActive == active) return
        foregroundActive = active
        updateMotionPolicy()
    }

    /** Explicit reduced motion preference; system animator scale is also respected. */
    fun setAnimationsEnabled(enabled: Boolean) {
        animationsEnabled = enabled
        updateMotionPolicy()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!observersRegistered) {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver)
            context.registerReceiver(powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            observersRegistered = true
        }
        aggregatedVisible = isShown
        updateMotionPolicy()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(drawFrame)
        framePending = false
        lastFrameMs = 0L
        audio.reset()
        if (observersRegistered) {
            context.contentResolver.unregisterContentObserver(motionObserver)
            context.unregisterReceiver(powerReceiver)
            observersRegistered = false
        }
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        aggregatedVisible = isVisible
        updateMotionPolicy()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateMotionPolicy()
    }

    private fun canAnimate() = foregroundActive && motionAllowed && isAttachedToWindow && aggregatedVisible && windowVisibility == VISIBLE

    private fun updateMotionPolicy() {
        motionAllowed = animationsEnabled && ValueAnimator.areAnimatorsEnabled() && !powerManager.isPowerSaveMode
        removeCallbacks(drawFrame)
        framePending = false
        lastFrameMs = 0L
        if (!canAnimate()) {
            settleState()
            audio.reset()
            audioEnergy = 0f
            pendingAudioMs = 0L
            consumedAudioMs = 0L
        }
        invalidate()
        scheduleFrame()
    }

    private fun settleState() {
        stateWeights.fill(0f)
        stateWeights[currentState.ordinal] = 1f
        energy = stateEnergy(currentState)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((412f * resources.displayMetrics.density).toInt(), widthMeasureSpec),
            resolveSize((307f * resources.displayMetrics.density).toInt(), heightMeasureSpec)
        )
    }

    /** Phone or Fold composition of the reference images (see ContinuumHologramRenderer.Geometry). */
    fun setGeometry(geometry: ContinuumHologramRenderer.Geometry) {
        renderer.geometry = geometry
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        if (width <= 0 || height <= 0) return
        advanceFrame()
        renderer.layout(width / density, height / density)
        painter.begin(canvas)
        val saved = canvas.save()
        canvas.scale(density, density)
        frameWeights.indices.forEach { frameWeights[it] = stateWeights[it] }
        renderFrame.phase = phase
        renderFrame.energy = energy
        renderFrame.audio = audioEnergy
        renderer.draw(painter, renderFrame)
        canvas.restoreToCount(saved)
        scheduleFrame()
    }

    private fun advanceFrame() {
        if (!canAnimate()) return
        val now = SystemClock.uptimeMillis()
        val delta = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceIn(0f, .1f)
        lastFrameMs = now
        val blend = 1f - exp(-delta * 7f)
        for (i in stateWeights.indices) {
            stateWeights[i] += ((if (i == currentState.ordinal) 1f else 0f) - stateWeights[i]) * blend
        }
        val sampleTime = pendingAudioMs
        if (sampleTime > consumedAudioMs) {
            audio.submit(pendingAudioLevel, sampleTime)
            consumedAudioMs = sampleTime
        }
        audioEnergy = if (currentState == State.SPEAKING) sqrt(audio.valueAt(now)) else 0f
        val targetEnergy = if (currentState == State.SPEAKING) .12f + audioEnergy * .88f else stateEnergy(currentState)
        energy += (targetEnergy - energy) * (1f - exp(-delta * 9f))
        phase = (phase + delta * (.28f + energy * .72f)) % 1000f
    }

    private fun stateEnergy(state: State) = when (state) {
        State.READY -> .18f
        State.LISTENING -> .42f
        State.THINKING -> .78f
        State.SPEAKING -> .12f
        State.RESEARCH -> .62f
        State.LEARNING -> .52f
    }

}
