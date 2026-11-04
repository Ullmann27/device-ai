package at.heinz.deviceai.ui

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.view.View

/**
 * Applies [JarvisMenuMotion.Camera] (continuous dolly-in + float) to the cockpit view: RenderThread transforms only
 * (scale/translation/rotation), no redraw. Off for "Animationen entfernen" and energy saving; paused while
 * Muster A runs ([pause]/[restart]) so the scene change owns the transform.
 */
class JarvisCameraDrift(private val target: View) {
    private val density = target.resources.displayMetrics.density
    private val power = target.context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var originMs = 0L
    private var paused = false
    private val animator = TimeAnimator().apply {
        setTimeListener { _, total, _ -> apply(total) }
    }
    private val attach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = start()
        override fun onViewDetachedFromWindow(v: View) = stop()
    }

    init {
        target.cameraDistance = JarvisMotion.Depth.CAMERA_DISTANCE_DP * density
        target.addOnAttachStateChangeListener(attach)
        if (target.isAttachedToWindow) start()
    }

    private fun allowed() = ValueAnimator.areAnimatorsEnabled() && !power.isPowerSaveMode

    fun start() { if (!animator.isStarted && allowed()) { originMs = -1L; animator.start() } }

    fun stop() { animator.cancel(); reset() }

    fun pause() { paused = true }

    /** After a scene change: drift starts again at scale 1,00 (no jump). */
    fun restart() { paused = false; originMs = -1L }

    private fun apply(totalMs: Long) {
        if (paused) return
        if (!allowed()) { reset(); return }
        if (originMs < 0) originMs = totalMs
        val c = JarvisMenuMotion.Camera.at(totalMs - originMs)
        target.pivotX = target.width / 2f; target.pivotY = target.height / 2f
        target.scaleX = c.scale; target.scaleY = c.scale
        target.translationX = c.dxDp * density; target.translationY = c.dyDp * density
        target.rotationX = c.rotationX; target.rotationY = c.rotationY
    }

    private fun reset() {
        target.scaleX = 1f; target.scaleY = 1f; target.translationX = 0f; target.translationY = 0f
        target.rotationX = 0f; target.rotationY = 0f
    }

    fun close() { target.removeOnAttachStateChangeListener(attach); stop() }
}
