package at.heinz.deviceai.ui

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Android glass backend for [JarvisShellRenderer] (kept out of the JVM preview path).
 * - Background: API 33+ ONE AGSL shader (shared ref5 palette, faint cyan grid, lower-edge haze,
 *   procedural bokeh and dust – one particle per cell, so the cost is fixed per pixel). API 31–32: the portable
 *   painter background, recorded once per size and replayed (cached layer).
 * - Glass: API 31+ backdrop blur (RenderEffect on a RenderNode copy of the background, clipped per panel);
 *   API 33+ rim glow as AGSL SDF shader.
 * - Depth: real 3D tilt via android.graphics.Camera (cameraDistance, rotationX/Y) on every hardware canvas;
 *   depth-of-field blur for far layers via RenderEffect (API 31+).
 * - Bloom: [JarvisBloomPass] (API 31+).
 * Below API 31 or on a software canvas every hook returns false → portable fallback in JarvisNeon.
 */
class JarvisGlassRenderer(private val painter: AndroidContinuumPainter) : JarvisShellRenderer.GlassBackend {
    private lateinit var canvas: Canvas
    private var density = 1f
    private var widthPx = 0
    private var heightPx = 0
    private var hardware = false
    private var blurCapable = false
    private val bloomPass = JarvisBloomPass(painter)
    private val agsl: Any? = if (Build.VERSION.SDK_INT >= 33) Agsl() else null
    private var bgNode: Any? = null
    private var blurNode: Any? = null
    private var cachedW = -1
    private var cachedH = -1
    private val clipPath = Path()
    private val camera = Camera()
    private val tilt = Matrix()
    private val depthMatrix = Matrix()
    private val depthInverse = Matrix()
    private val depthPool = ArrayList<Any>()
    private var depthUsed = 0
    private var depthOpen: Any? = null

    fun begin(target: Canvas, density: Float, widthPx: Int, heightPx: Int) {
        canvas = target; this.density = density; this.widthPx = widthPx; this.heightPx = heightPx
        hardware = target.isHardwareAccelerated
        blurCapable = Build.VERSION.SDK_INT >= 31 && hardware
        bloomPass.beginFrame(); depthUsed = 0
    }

    override fun drawBackground(width: Float, height: Float, timeMs: Long): Boolean =
        if (blurCapable && Build.VERSION.SDK_INT >= 31) background(width, height, timeMs) else false

    @RequiresApi(31)
    private fun background(w: Float, h: Float, timeMs: Long): Boolean {
        val wp = widthPx; val hp = heightPx
        val bg = (bgNode ?: RenderNode("jarvisBackground").also { bgNode = it }) as RenderNode
        val shader = agsl as? Agsl
        val sizeChanged = wp != cachedW || hp != cachedH
        if (shader != null && Build.VERSION.SDK_INT >= 33) {
            bg.setPosition(0, 0, wp, hp)
            shader.drawBackground(bg.beginRecording(), wp.toFloat(), hp.toFloat(), density, timeMs)
            bg.endRecording()
        } else if (sizeChanged) {
            bg.setPosition(0, 0, wp, hp)
            val rc = bg.beginRecording()
            rc.scale(density, density)
            painter.begin(rc)
            JarvisShellRenderer.background(painter, w, h)
            bg.endRecording()
            painter.begin(canvas)
        }
        if (sizeChanged) {
            val blur = (blurNode ?: RenderNode("jarvisBackdropBlur").also { blurNode = it }) as RenderNode
            blur.setPosition(0, 0, wp, hp)
            val r = JarvisDesign.Metrics.backdropBlur * density
            blur.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
            blur.beginRecording().drawRenderNode(bg); blur.endRecording()
            cachedW = wp; cachedH = hp
        }
        canvas.save(); canvas.scale(1f / density, 1f / density); canvas.drawRenderNode(bg); canvas.restore()
        return true
    }

    override fun drawPanelGlass(box: JarvisShellLayout.Box, radius: Float, glow: Float): Boolean =
        if (blurCapable && Build.VERSION.SDK_INT >= 31) panelGlass(box, radius, glow) else false

    @RequiresApi(31)
    @Suppress("DEPRECATION") // Canvas.getMatrix: backdrop must be drawn in screen space under the panel clip
    private fun panelGlass(box: JarvisShellLayout.Box, radius: Float, glow: Float): Boolean {
        val blur = blurNode as? RenderNode ?: return false
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(box.left, box.top, box.right, box.bottom, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.getMatrix(depthMatrix)
        if (depthMatrix.invert(depthInverse)) canvas.concat(depthInverse) // backdrop stays in screen space
        canvas.drawRenderNode(blur)
        canvas.restore()
        val shader = agsl as? Agsl ?: return false
        if (Build.VERSION.SDK_INT >= 33) shader.drawRim(canvas, box, radius, .35f + .65f * glow)
        return true
    }

    override fun beginDepth(box: JarvisShellLayout.Box, depth: JarvisMotion.Depth.State): Boolean {
        if (!hardware) return false
        val needBlur = depth.blurDp > 0f && blurCapable
        val cx = (box.left + box.right) / 2f; val cy = (box.top + box.bottom) / 2f
        camera.save()
        camera.setLocation(0f, 0f, -JarvisMotion.Depth.CAMERA_DISTANCE_DP / 72f) // Camera units: 1 inch = 72 px
        camera.rotateX(depth.rotationX); camera.rotateY(depth.rotationY)
        camera.getMatrix(tilt)
        camera.restore()
        tilt.preTranslate(-cx, -cy); tilt.postTranslate(cx, cy)
        if (needBlur && Build.VERSION.SDK_INT >= 31) return beginBlurredDepth(depth.blurDp)
        canvas.save(); canvas.concat(tilt)
        depthOpen = canvas
        return true
    }

    @RequiresApi(31)
    @Suppress("DEPRECATION")
    private fun beginBlurredDepth(blurDp: Float): Boolean {
        val node = (if (depthUsed < depthPool.size) depthPool[depthUsed] else RenderNode("jarvisDepth").also { depthPool += it }) as RenderNode
        depthUsed++
        canvas.getMatrix(depthMatrix)
        if (!depthMatrix.invert(depthInverse)) return false
        node.setPosition(0, 0, widthPx, heightPx)
        val r = blurDp * density
        node.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL))
        val rc = node.beginRecording()
        rc.concat(depthMatrix); rc.concat(tilt)
        painter.begin(rc); bloomTarget = rc
        depthOpen = node
        return true
    }

    private var bloomTarget: Canvas? = null

    override fun endDepth() {
        val open = depthOpen ?: return
        depthOpen = null
        if (open === canvas) { canvas.restore(); return }
        if (Build.VERSION.SDK_INT >= 31) endBlurredDepth(open)
    }

    @RequiresApi(31)
    private fun endBlurredDepth(open: Any) {
        val node = open as RenderNode
        node.endRecording()
        bloomTarget = null
        painter.begin(canvas)
        canvas.save(); canvas.concat(depthInverse); canvas.drawRenderNode(node); canvas.restore()
    }

    override fun bloom(radiusDp: Float, intensity: Float, emissive: (ContinuumPainter) -> Unit): Boolean {
        val target = bloomTarget ?: canvas
        val ok = bloomPass.draw(target, density, widthPx, heightPx, radiusDp, intensity, emissive)
        if (bloomTarget != null) painter.begin(target)
        return ok
    }

    @RequiresApi(33)
    private class Agsl {
        private val bg = RuntimeShader(BACKGROUND)
        private val rim = RuntimeShader(RIM)
        private val bgPaint = Paint().apply { shader = bg }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rim }

        fun drawBackground(c: Canvas, w: Float, h: Float, density: Float, timeMs: Long) {
            bg.setFloatUniform("size", w, h); bg.setFloatUniform("density", density)
            bg.setFloatUniform("time", (timeMs % 600_000L) / 1000f)
            colorUniform("topColor", JarvisDesign.Colors.background)
            colorUniform("bottomColor", JarvisDesign.Colors.backgroundBottom)
            colorUniform("hazeColor", JarvisDesign.Colors.backgroundHaze)
            colorUniform("cyanColor", JarvisDesign.Colors.cyan)
            colorUniform("violetColor", JarvisDesign.Colors.violet)
            colorUniform("dustColor", JarvisDesign.Colors.dust)
            c.drawRect(0f, 0f, w, h, bgPaint)
        }

        private fun colorUniform(name: String, color: Int) = bg.setFloatUniform(name,
            Argb.red(color) / 255f, Argb.green(color) / 255f, Argb.blue(color) / 255f)

        fun drawRim(c: Canvas, b: JarvisShellLayout.Box, radius: Float, intensity: Float) {
            val spread = 10f
            rim.setFloatUniform("rect", b.left, b.top, b.right, b.bottom)
            rim.setFloatUniform("radius", radius); rim.setFloatUniform("spread", spread / 2.5f)
            rim.setFloatUniform("intensity", intensity)
            c.drawRoundRect(b.left - spread, b.top - spread, b.right + spread, b.bottom + spread, radius + spread, radius + spread, rimPaint)
        }
    }

    companion object {
        /**
         * Pixel coordinates. Shared ref5 palette, faint cyan grid (48 dp), lower-edge haze,
         * bokeh (cells of 120 dp, ≤1 disc each, ≤5 % per ref5) and dust (cells of 35 dp ≈ 1 per 1200 dp², r 0.65 dp,
         * colour #0F93C4 per ref5). Bokeh cell size and drift speed GESCHÄTZT.
         */
        const val BACKGROUND = """
uniform float2 size; uniform float density; uniform float time;
uniform float3 topColor; uniform float3 bottomColor; uniform float3 hazeColor;
uniform float3 cyanColor; uniform float3 violetColor; uniform float3 dustColor;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float particle(float2 p, float cell, float radius, float seed) {
  float2 id = floor(p / cell);
  float2 f = p - id * cell;
  float h = hash(id + seed);
  float2 c = float2(hash(id + seed + 1.3), hash(id + seed + 7.1)) * cell;
  float d = distance(f, c);
  float r = radius * (0.4 + 0.6 * h);
  return h > 0.55 ? smoothstep(r, 0.0, d) * (h - 0.55) * 2.2 : 0.0;
}
half4 main(float2 p) {
  float2 uv = p / size;
  half3 c = half3(mix(topColor, bottomColor, uv.y));
  float stepPx = 48.0 * density;
  float2 g = abs(fract(p / stepPx - 0.5) - 0.5) * stepPx;
  float d = min(g.x, g.y);
  float soft = 1.6 * density; float core = 0.45 * density;
  c += half3(cyanColor) * half(exp(-d * d / (2.0 * soft * soft)) * 0.004 + exp(-d * d / (2.0 * core * core)) * 0.008);
  float r = distance(p, float2(size.x * 0.45, size.y * 0.92)) / (max(size.x, size.y) * 0.70);
  c = mix(c, half3(hazeColor), half(0.65 * max(0.0, 1.0 - r)));
  float bokeh = particle(p, 120.0 * density, 7.0 * density, 3.0);
  float bokehV = particle(p + 60.0 * density, 120.0 * density, 6.0 * density, 9.0);
  c += half3(cyanColor) * half(bokeh * 0.05) + half3(violetColor) * half(bokehV * 0.05);
  float2 dp = p + float2(0.0, time * 6.0 * density);
  c += half3(dustColor) * half(particle(dp, 35.0 * density, 0.65 * density, 17.0) * 2.2);
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
