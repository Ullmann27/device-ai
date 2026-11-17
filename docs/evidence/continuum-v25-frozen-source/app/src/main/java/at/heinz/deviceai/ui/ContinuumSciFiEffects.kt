package at.heinz.deviceai.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.max

/**
 * Cheap, native Continuum cinematic treatment for the existing Android View UI.
 *
 * No offscreen backdrop blur: blurring the already rendered dashboard destroys
 * legibility and usually incurs another full-screen compositing pass.
 *
 * The ambient glows are drawn BEHIND content and the very faint scanlines are
 * drawn AFTER content without installing any touch-intercepting overlay.
 * Only visually meaningful ornamentation lives here; no simulated telemetry.
 */
internal class ContinuumSciFiEffects(private val density: Float) {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val scanline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x09010712 // ~3.5% black: preserves sharp, readable type
        style = Paint.Style.STROKE
        strokeWidth = max(0.65f, density * 0.55f)
    }
    private var cachedWidth = -1f
    private var cachedHeight = -1f
    private var cyanGlow: RadialGradient? = null
    private var violetGlow: RadialGradient? = null

    fun drawAmbient(canvas: Canvas, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        if (width != cachedWidth || height != cachedHeight) {
            cachedWidth = width
            cachedHeight = height
            val radius = max(width * .66f, density * 140f)
            cyanGlow = RadialGradient(
                width * .43f, minOf(height * .22f, density * 235f), radius,
                intArrayOf(0x2842CAFF, 0x121D7BC5, 0x00000000),
                floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP
            )
            violetGlow = RadialGradient(
                width * .88f, minOf(height * .40f, density * 430f), radius * .9f,
                intArrayOf(0x245F36D9, 0x125728A2, 0x00000000),
                floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP
            )
        }

        glow.shader = cyanGlow
        canvas.drawRect(0f, 0f, width, height, glow)
        glow.shader = violetGlow
        canvas.drawRect(0f, 0f, width, height, glow)
        glow.shader = null
    }

    fun drawScanlines(canvas: Canvas, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        // Screenspace density-independent pitch: predictable on phone and Fold.
        // The effect is static, produces no invalidation loop, and catches no input.
        val step = max(4f, 6f * density)
        var y = step * .5f
        while (y < height) {
            canvas.drawLine(0f, y, width, y, scanline)
            y += step
        }
    }
}
