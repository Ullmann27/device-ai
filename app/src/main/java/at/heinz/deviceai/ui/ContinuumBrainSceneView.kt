package at.heinz.deviceai.ui

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer

/** Dedicated compact Brain destination, sharing the dashboard geometry/material and real memory event adapter. */
class ContinuumBrainSceneView(context: Context) : View(context), CockpitAnimationRenderer {
    private val painter = AndroidContinuumPainter(context.assets)
    private val brain = ContinuumBrainOrbRenderer()
    private val effects = JarvisSequenceEffects()
    private val events = BrainOrbAnimationRenderer(effects, SystemClock::uptimeMillis)
    private val power = context.getSystemService(PowerManager::class.java)
    private var foreground = false
    private var processing = false
    private var phase = 0f
    private var previousFrame = 0L
    private var generation = 0L
    private val tick = Runnable { invalidate() }

    init {
        setBackgroundColor(JarvisDesign.Colors.background)
        contentDescription = "Continuum-Gehirn. Lichtsignale stammen aus tatsächlichen Gedächtnis- und Arbeitsereignissen."
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setProcessing(active: Boolean) { processing = active; invalidate() }
    fun setForegroundActive(active: Boolean) {
        foreground = active
        previousFrame = 0L
        removeCallbacks(tick)
        if (active) invalidate() else { effects.clear(); generation++ }
    }

    override fun render(event: CockpitAnimationEvent) {
        val expected = generation
        val apply = Runnable {
            if (expected == generation && foreground && isAttachedToWindow) {
                events.render(event); invalidate()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply.run() else post(apply)
    }

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val w = width / density; val h = height / density
        if (w <= 0f || h <= 0f) return
        val thermal = if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus else null
        val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val budget = JarvisRenderQuality.resolve(foreground && isShown && windowVisibility == VISIBLE,
            power?.isPowerSaveMode == true, thermal, reduced, processing)
        val now = SystemClock.uptimeMillis()
        if (budget.animate && previousFrame > 0L) phase += ((now - previousFrame) / 1000f).coerceIn(0f, .1f) * .14f
        previousFrame = if (budget.animate) now else 0L
        painter.begin(canvas)
        canvas.save(); canvas.scale(density, density)
        JarvisShellRenderer.background(painter, w, h)
        JarvisReferenceHud.frame(painter, w, h)
        val r = ContinuumBrainOrbRenderer.radiusFor(w - 24f, h - 50f).coerceAtLeast(1f)
        val cy = (h - 2.44f * r) / 2f + r
        brain.draw(painter, w / 2f, cy, r, ContinuumBrainOrbRenderer.Signals(
            pulse = effects.orbPulseLevel(now), recall = effects.recallBrightness(now),
            hypothesis = effects.hypothesisVisible(now), phase = phase, processing = processing,
            particles = budget.particleCount, orbitSegments = budget.orbitSegments))
        painter.drawText(if (processing) "Anfrage wird verarbeitet" else "Keine laufende Anfrage",
            w / 2f, h - 18f, JarvisDesign.Type.caption, Fill.Solid(JarvisDesign.Colors.textSecondary), TextAlign.CENTER)
        canvas.restore()
        removeCallbacks(tick)
        if (budget.animate) postDelayed(tick, budget.frameDelayMillis)
    }

    override fun onDetachedFromWindow() {
        generation++; removeCallbacks(tick); previousFrame = 0L; effects.clear()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) invalidate() else { removeCallbacks(tick); previousFrame = 0L }
    }
}
