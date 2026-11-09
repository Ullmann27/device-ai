package at.heinz.deviceai.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.min

/**
 * GPU-only prism catchlight on existing chamfered window geometry.
 * A stroke shader, not a backdrop refraction or a filter over native text.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class AgslHologramEdgePainter {
    private val path = Path()
    private val shader = RuntimeShader(SOURCE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        this.shader = this@AgslHologramEdgePainter.shader
    }
    private var failed = false

    /** Returns false when the safe Canvas fallback must remain authoritative. */
    fun draw(canvas: Canvas, bounds: RectF, tiltDegrees: Float, pulse: Float,
             density: Float): Boolean {
        if (failed || !canvas.isHardwareAccelerated || !tiltDegrees.isFinite() ||
            !pulse.isFinite() || !density.isFinite() || density <= 0f ||
            bounds.width() < 24f * density || bounds.height() < 24f * density) return false
        return try {
            val chamfer = min(14f * density, min(bounds.width(), bounds.height()) * .12f)
            path.reset()
            path.moveTo(bounds.left + chamfer, bounds.top)
            path.lineTo(bounds.right - chamfer, bounds.top)
            path.lineTo(bounds.right, bounds.top + chamfer)
            path.lineTo(bounds.right, bounds.bottom - chamfer)
            path.lineTo(bounds.right - chamfer, bounds.bottom)
            path.lineTo(bounds.left + chamfer, bounds.bottom)
            path.lineTo(bounds.left, bounds.bottom - chamfer)
            path.lineTo(bounds.left, bounds.top + chamfer)
            path.close()
            shader.setFloatUniform("origin", bounds.left, bounds.top)
            shader.setFloatUniform("extent", bounds.width(), bounds.height())
            shader.setFloatUniform("tilt", Math.toRadians(tiltDegrees.toDouble()).toFloat())
            shader.setFloatUniform("pulse", pulse.coerceIn(0f, 1f))
            paint.strokeWidth = 1.15f * density
            canvas.drawPath(path, paint)
            true
        } catch (_: RuntimeException) {
            failed = true
            false
        }
    }

    private companion object {
        const val SOURCE = """
            uniform float2 origin;
            uniform float2 extent;
            uniform float tilt;
            uniform float pulse;
            half4 main(float2 position) {
                float2 uv = (position - origin) / max(extent, float2(1.0, 1.0));
                float incoming = clamp(1.0 - uv.x, 0.0, 1.0);
                float grazing = smoothstep(0.10, 0.92, incoming);
                float cutlight = smoothstep(0.44, 0.52, uv.x + uv.y * 0.24 + sin(tilt) * 0.12);
                cutlight *= 1.0 - smoothstep(0.52, 0.68, uv.x + uv.y * 0.24 + sin(tilt) * 0.12);
                half3 cyan = half3(0.34, 0.82, 1.0);
                half3 violet = half3(0.61, 0.37, 0.96);
                half3 colour = mix(violet, cyan, half(grazing));
                float alpha = clamp(0.11 + 0.17 * cutlight + pulse * 0.10, 0.0, 0.36);
                return half4(colour, half(alpha));
            }
        """
    }
}
