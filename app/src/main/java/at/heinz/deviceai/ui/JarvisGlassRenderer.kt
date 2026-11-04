package at.heinz.deviceai.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Android glass backend for [JarvisShellRenderer] (kept out of the JVM preview path).
 * - API 33+: page background = ONE AGSL shader (gradient + soft cyan grid + scanlines + violet haze), rim glow = AGSL SDF shader.
 * - API 31+: backdrop blur via RenderEffect.createBlurEffect on a RenderNode copy of the background, clipped per panel.
 * - Below 31 / software canvas: returns false → portable painter fallback (semi-opaque glass, additive rim strokes).
 */
class JarvisGlassRenderer(private val painter: AndroidContinuumPainter) : JarvisShellRenderer.GlassBackend {
    private lateinit var canvas: Canvas
    private var density = 1f
    private var native = false
    private var bgNode: RenderNode? = null
    private var blurNode: RenderNode? = null
    private val shaders: Any? = if (Build.VERSION.SDK_INT >= 33) Agsl() else null
    private val clipPath = android.graphics.Path()

    fun begin(target: Canvas, density: Float) {
        canvas = target; this.density = density
        native = Build.VERSION.SDK_INT >= 31 && target.isHardwareAccelerated
    }

    override fun drawBackground(width: Float, height: Float, timeMs: Long): Boolean {
        if (!native || Build.VERSION.SDK_INT < 31) return false
        return recordAndDrawBackground(width, height, timeMs)
    }

    @RequiresApi(31)
    private fun recordAndDrawBackground(w: Float, h: Float, timeMs: Long): Boolean {
        val wp = (w * density).toInt(); val hp = (h * density).toInt()
        val bg = bgNode ?: RenderNode("jarvisBackground").also { bgNode = it }
        bg.setPosition(0, 0, wp, hp)
        val rc = bg.beginRecording()
        val agsl = shaders as? Agsl
        if (agsl != null && Build.VERSION.SDK_INT >= 33) agsl.drawBackground(rc, wp.toFloat(), hp.toFloat(), density, timeMs)
        rc.save(); rc.scale(density, density)
        painter.begin(rc)
        if (agsl == null) JarvisShellRenderer.background(painter, w, h) else JarvisShellRenderer.bokeh(painter, w, h)
        rc.restore()
        bg.endRecording()
        val blur = blurNode ?: RenderNode("jarvisBackdropBlur").also { blurNode = it }
        blur.setPosition(0, 0, wp, hp)
        val r = JarvisDesign.Metrics.backdropBlur * density
        blur.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        blur.beginRecording().also { it.drawRenderNode(bg) }; blur.endRecording()
        canvas.save(); canvas.scale(1f / density, 1f / density); canvas.drawRenderNode(bg); canvas.restore()
        painter.begin(canvas)
        return true
    }

    override fun drawPanelGlass(box: JarvisShellLayout.Box, radius: Float, glow: Float): Boolean {
        if (!native || Build.VERSION.SDK_INT < 31) return false
        val blur = blurNode ?: return false
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(box.left, box.top, box.right, box.bottom, radius, radius, android.graphics.Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.scale(1f / density, 1f / density)
        canvas.drawRenderNode(blur)
        canvas.restore()
        val agsl = shaders as? Agsl ?: return false
        if (Build.VERSION.SDK_INT >= 33) agsl.drawRim(canvas, box, radius, .35f + .65f * glow)
        return true
    }

    @RequiresApi(33)
    private class Agsl {
        private val bg = RuntimeShader(BACKGROUND)
        private val rim = RuntimeShader(RIM)
        private val bgPaint = Paint().apply { shader = bg }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rim }

        fun drawBackground(c: Canvas, w: Float, h: Float, density: Float, timeMs: Long) {
            bg.setFloatUniform("size", w, h); bg.setFloatUniform("density", density)
            bg.setFloatUniform("time", (timeMs % 100_000L) / 1000f)
            c.drawRect(0f, 0f, w, h, bgPaint)
        }

        fun drawRim(c: Canvas, b: JarvisShellLayout.Box, radius: Float, intensity: Float) {
            val spread = 10f
            rim.setFloatUniform("rect", b.left, b.top, b.right, b.bottom)
            rim.setFloatUniform("radius", radius); rim.setFloatUniform("spread", spread / 2.5f)
            rim.setFloatUniform("intensity", intensity)
            c.drawRoundRect(b.left - spread, b.top - spread, b.right + spread, b.bottom + spread, radius + spread, radius + spread, rimPaint)
        }
    }

    companion object {
        /** Gradient #050814→#0A1128, blurred cyan grid (48 dp), fine scanlines, violet haze. Pixel coordinates. */
        const val BACKGROUND = """
uniform float2 size; uniform float density; uniform float time;
half4 main(float2 p) {
  float2 uv = p / size;
  half3 c = mix(half3(0.0196, 0.0314, 0.0784), half3(0.0392, 0.0667, 0.157), half(uv.y));
  float stepPx = 48.0 * density;
  float2 g = abs(fract(p / stepPx - 0.5) - 0.5) * stepPx;
  float d = min(g.x, g.y);
  float soft = 1.6 * density; float core = 0.45 * density;
  float line = exp(-d * d / (2.0 * soft * soft)) * 0.05 + exp(-d * d / (2.0 * core * core)) * 0.05;
  c += half3(0.0, 1.0, 1.0) * half(line);
  float r = distance(uv, float2(0.5, 0.35));
  c += half3(0.541, 0.169, 0.886) * half(0.10 * (1.0 - smoothstep(0.0, 0.75, r)));
  float scan = 0.5 + 0.5 * sin(p.y * 3.14159 / (1.5 * density) + time * 0.5);
  c *= half(1.0 - 0.03 * scan);
  return half4(c, 1.0);
}"""

        /** Outer glow of a rounded rect via SDF, cyan, premultiplied. Local (dp) coordinates. */
        const val RIM = """
uniform float4 rect; uniform float radius; uniform float spread; uniform float intensity;
float sdRound(float2 p, float2 b, float r) { float2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
half4 main(float2 p) {
  float2 c = (rect.xy + rect.zw) * 0.5; float2 h = (rect.zw - rect.xy) * 0.5;
  float d = sdRound(p - c, h, radius);
  if (d < 0.0) return half4(0.0);
  float a = exp(-d / spread) * intensity * 0.55;
  return half4(0.0, a, a, a);
}"""
    }
}
