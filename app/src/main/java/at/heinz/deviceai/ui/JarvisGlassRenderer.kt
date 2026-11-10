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

    override fun drawFluidEdge(
        box: JarvisShellLayout.Box, radius: Float, phase: Float, energy: Float, timeMs: Long,
    ): Boolean {
        if (!hardware || Build.VERSION.SDK_INT < 33) return false
        if (!box.left.isFinite() || !box.top.isFinite() || !box.right.isFinite() ||
            !box.bottom.isFinite() || box.width <= 0f || box.height <= 0f ||
            !radius.isFinite() || !phase.isFinite() || !energy.isFinite()) return false
        if (energy <= 0f) return true
        val shader = agsl as? Agsl ?: return false
        return shader.drawFluidEdge(bloomTarget ?: canvas, box,
            radius.coerceIn(0f, minOf(box.width, box.height) / 2f),
            phase.coerceIn(0f, 1f), energy.coerceIn(0f, 1f), timeMs)
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
        private var fluidUnavailable = false
        private var fluid: RuntimeShader? = null
        private val fluidPaint = Paint()

        fun drawFluidEdge(c: Canvas, b: JarvisShellLayout.Box, radius: Float,
                          phase: Float, energy: Float, timeMs: Long): Boolean {
            if (fluidUnavailable) return false
            return try {
                val effect = fluid ?: RuntimeShader(FLUID_EDGE).also {
                    fluid = it
                    fluidPaint.shader = it
                }
                effect.setFloatUniform("rect", b.left, b.top, b.right, b.bottom)
                effect.setFloatUniform("radius", radius)
                effect.setFloatUniform("phase", phase)
                effect.setFloatUniform("energy", energy)
                effect.setFloatUniform("time", (timeMs.coerceAtLeast(0L) % 600_000L) / 1000f)
                val spread = FLUID_SPREAD_DP
                val innerX = minOf(b.width / 2f, radius + spread)
                val innerY = minOf(b.height / 2f, radius + spread)
                val left = b.left - spread
                val top = b.top - spread
                val right = b.right + spread
                val bottom = b.bottom + spread
                // Disjoint strips avoid interior shading.
                c.drawRect(left, top, b.left + innerX, bottom, fluidPaint)
                c.drawRect(b.right - innerX, top, right, bottom, fluidPaint)
                if (b.left + innerX < b.right - innerX) {
                    c.drawRect(b.left + innerX, top, b.right - innerX, b.top + innerY, fluidPaint)
                    c.drawRect(b.left + innerX, b.bottom - innerY, b.right - innerX, bottom, fluidPaint)
                }
                true
            } catch (_: IllegalArgumentException) {
                fluidUnavailable = true
                false
            } catch (_: IllegalStateException) {
                fluidUnavailable = true
                false
            }
        }


        fun drawBackground(c: Canvas, w: Float, h: Float, density: Float, timeMs: Long) {
            bg.setFloatUniform("size", w, h); bg.setFloatUniform("density", density)
            bg.setFloatUniform("time", (timeMs % 600_000L) / 1000f)
            colorUniform("topColor", JarvisDesign.Colors.background)
            colorUniform("bottomColor", JarvisDesign.Colors.backgroundBottom)
            colorUniform("hazeColor", JarvisDesign.Colors.backgroundHaze)
            colorUniform("cyanColor", JarvisDesign.Colors.cyan)
            colorUniform("violetColor", JarvisDesign.Colors.violet)
            colorUniform("dustColor", JarvisDesign.Colors.dust)
            colorUniform("amberColor", JarvisDesign.Colors.warn)
            c.drawRect(0f, 0f, w, h, bgPaint)
        }

        private fun colorUniform(name: String, color: Int) = bg.setFloatUniform(name,
            Argb.red(color) / 255f, Argb.green(color) / 255f, Argb.blue(color) / 255f)

        fun drawRim(c: Canvas, b: JarvisShellLayout.Box, radius: Float, intensity: Float) {
            val spread = 12f
            rim.setFloatUniform("rect", b.left, b.top, b.right, b.bottom)
            rim.setFloatUniform("radius", radius); rim.setFloatUniform("spread", spread / 2.5f)
            rim.setFloatUniform("intensity", intensity)
            c.drawRoundRect(b.left - spread, b.top - spread, b.right + spread, b.bottom + spread, radius + spread, radius + spread, rimPaint)
        }
    }

    companion object {
        const val FLUID_SPREAD_DP = 10f

        // Local dp; premultiplied edge light.
        const val FLUID_EDGE = """
uniform float4 rect;
uniform float radius;
uniform float phase;
uniform float energy;
uniform float time;
float sdFluidRound(float2 p, float2 b, float r) {
  float2 q = abs(p) - b + r;
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
half4 main(float2 p) {
  float2 center = (rect.xy + rect.zw) * 0.5;
  float2 halfSize = (rect.zw - rect.xy) * 0.5;
  float2 local = p - rect.xy;
  float2 uv = local / max(rect.zw - rect.xy, float2(1.0));
  float d = sdFluidRound(p - center, halfSize, radius);
  float band = 1.0 - smoothstep(8.8, 10.0, abs(d));
  float flow = local.y * 0.071 + local.x * 0.039 - time * 2.2 - phase * 2.5;
  float drift = sin(flow + 0.65 * sin(local.x * 0.045 - time * 1.1));
  float lobe = 0.5 + 0.5 * sin(flow * 0.79 + sin(local.y * 0.043 + phase * 2.0));
  float width = 2.0 + 3.8 * lobe * lobe;
  float meniscus = d - drift * 1.8;
  float body = 1.0 - smoothstep(width * 0.48, width + 1.4, abs(meniscus));
  float lip = 1.0 - smoothstep(0.25, 1.3, abs(meniscus + width * 0.55));
  float wake = (1.0 - smoothstep(0.0, 8.6, abs(meniscus))) * (0.35 + 0.65 * lobe);
  float fleck = pow(0.5 + 0.5 * sin(flow * 2.3 + drift), 6.0);
  float a = clamp(energy * band * (body * 0.48 + lip * 0.26 + wake * 0.12), 0.0, 0.86);
  half3 cyan = half3(0.08, 0.92, 1.0);
  half3 violet = half3(0.67, 0.28, 1.0);
  half3 color = mix(cyan, violet, half(0.28 + 0.24 * sin(uv.x * 4.0 + drift * 0.6)));
  color = mix(color, half3(0.88, 0.98, 1.0), half(lip * (0.25 + fleck * 0.35)));
  return half4(color * a, a);
}"""

        /**
         * Pixel coordinates. Shared ref5 palette, faint cyan grid (48 dp), lower-edge haze,
         * bokeh (cells of 120 dp, ≤1 disc each, ≤5 % per ref5) and dust (cells of 35 dp ≈ 1 per 1200 dp², r 0.65 dp,
         * colour #0F93C4 per ref5). Bokeh cell size and drift speed GESCHÄTZT.
         */
        const val BACKGROUND = """
uniform float2 size; uniform float density; uniform float time;
uniform float3 topColor; uniform float3 bottomColor; uniform float3 hazeColor;
uniform float3 cyanColor; uniform float3 violetColor; uniform float3 dustColor;
uniform float3 amberColor;
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
  c = mix(c, half3(hazeColor), half(0.72 * max(0.0, 1.0 - r)));
  float2 n1 = uv - float2(0.34, 0.22); c += half3(violetColor) * half(exp(-dot(n1, n1) * 7.0) * 0.16);
  float2 n2 = uv - float2(0.72, 0.28); c += half3(cyanColor) * half(exp(-dot(n2, n2) * 8.0) * 0.15);
  float2 n3 = uv - float2(0.50, 0.82); c += half3(cyanColor) * half(exp(-dot(n3, n3) * 6.0) * 0.10);
  float2 n4 = uv - float2(0.10, 0.70); c += half3(violetColor) * half(exp(-dot(n4, n4) * 9.0) * 0.08);
  float2 n5 = uv - float2(0.82, 0.16); c += half3(amberColor) * half(exp(-dot(n5, n5) * 14.0) * 0.09);
  float2 n6 = uv - float2(0.62, 0.36); c += half3(amberColor) * half(exp(-dot(n6, n6) * 22.0) * 0.05);
  float farB = particle(p, 180.0 * density, 28.0 * density, 1.0);
  float farA = particle(p + 140.0 * density, 220.0 * density, 22.0 * density, 11.0);
  c += half3(cyanColor) * half(farB * 0.055) + half3(amberColor) * half(farA * 0.05);
  float bokeh = particle(p, 120.0 * density, 7.0 * density, 3.0);
  float bokehV = particle(p + 60.0 * density, 120.0 * density, 6.0 * density, 9.0);
  c += half3(cyanColor) * half(bokeh * 0.06) + half3(violetColor) * half(bokehV * 0.05);
  float2 dp = p + float2(0.0, time * 6.0 * density);
  c += half3(dustColor) * half(particle(dp, 35.0 * density, 0.65 * density, 17.0) * 2.2);
  return half4(c, 1.0);
}"""

        /**
         * Rounded-rect SDF rim with volumetric glass: outer glow plus a short inner
         * sheen that fades away from the edge (does not fill the panel). Cyan-to-violet
         * with a warm amber mid accent matching ContinuumTheme.AMBER (255,196,92).
         * Premultiplied. Local (dp) coordinates.
         */
        const val RIM = """
uniform float4 rect; uniform float radius; uniform float spread; uniform float intensity;
float sdRound(float2 p, float2 b, float r) { float2 q = abs(p) - b + r; return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r; }
half4 main(float2 p) {
  float2 c = (rect.xy + rect.zw) * 0.5; float2 h = (rect.zw - rect.xy) * 0.5;
  float d = sdRound(p - c, h, radius);
  float t = clamp((p.x - rect.x) / max(rect.z - rect.x, 1.0), 0.0, 1.0);
  half3 col = mix(half3(0.00, 0.90, 1.00), half3(0.83, 0.24, 1.00), half(t));
  col = mix(col, half3(1.00, 0.77, 0.36), half(0.20 * (1.0 - abs(t * 2.0 - 1.0))));
  if (d < 0.0) {
    float inner = exp(d / (spread * 0.55)) * intensity * 0.28;
    float top = clamp(((c.y - p.y) / max(h.y, 1.0)) * 0.5 + 0.5, 0.0, 1.0);
    half3 sheen = mix(col, half3(0.85, 0.95, 1.00), half(0.45 * top));
    return half4(sheen * inner, inner);
  }
  float a = exp(-d / spread) * intensity * 0.78;
  return half4(col * a, a);
}"""
    }
}
