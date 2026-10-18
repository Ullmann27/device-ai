package at.heinz.deviceai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import kotlin.math.min

/**
 * Decorative, GPU-friendly isometric core artwork.
 * No animation, network, assets, accessibility traps or continuously running tasks.
 * Deliberately separate from inference and research state.
 */
class ContinuumCoreArtView(context: Context) : View(context) {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun stroke(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, size: Float) {
        brush.reset()
        brush.isAntiAlias = true
        brush.color = color
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = size
        brush.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(x1, y1, x2, y2, brush)
    }

    private fun polygon(canvas: Canvas, colorA: Int, colorB: Int, vararg xy: Float) {
        val shape = Path().apply {
            moveTo(xy[0], xy[1])
            var i = 2
            while (i < xy.size) {
                lineTo(xy[i], xy[i + 1])
                i += 2
            }
            close()
        }
        brush.reset()
        brush.isAntiAlias = true
        brush.style = Paint.Style.FILL
        brush.shader = LinearGradient(25f, 25f, 140f, 140f, colorA, colorB, Shader.TileMode.CLAMP)
        canvas.drawPath(shape, brush)
        brush.shader = null
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        brush.reset()
        brush.isAntiAlias = true
        brush.style = Paint.Style.FILL
        brush.color = color
        canvas.drawCircle(x, y, radius, brush)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val scale = min(width.toFloat() / 160f, height.toFloat() / 160f)
        canvas.save()
        canvas.translate((width - 160f * scale) / 2f, (height - 160f * scale) / 2f)
        canvas.scale(scale, scale)

        // Two orbital traces establish depth without motion or GPU-heavy blur.
        brush.reset()
        brush.isAntiAlias = true
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = 1.2f
        brush.color = Color.argb(125, 52, 193, 252)
        canvas.drawOval(8f, 27f, 153f, 132f, brush)
        brush.color = Color.argb(65, 92, 124, 250)
        canvas.drawOval(23f, 9f, 135f, 150f, brush)
        stroke(canvas, 10f, 122f, 43f, 122f, Color.argb(115, 76, 181, 250), 1.6f)
        stroke(canvas, 120f, 33f, 153f, 33f, Color.argb(115, 76, 181, 250), 1.6f)
        dot(canvas, 10f, 122f, 3f, Color.rgb(58, 222, 252))
        dot(canvas, 153f, 33f, 3f, Color.rgb(102, 139, 255))

        // Three extruded, independently shaded layers: top, front and right.
        for (layer in 0..2) {
            val top = 41f + layer * 26f
            polygon(canvas, Color.rgb(16, 92, 175), Color.rgb(13, 41, 101),
                26f, top + 18f, 79f, top + 46f, 79f, top + 59f, 26f, top + 31f)
            polygon(canvas, Color.rgb(31, 130, 245), Color.rgb(28, 59, 165),
                79f, top + 46f, 132f, top + 18f, 132f, top + 31f, 79f, top + 59f)
            polygon(canvas, Color.rgb(97, 226, 255), Color.rgb(64, 103, 250),
                26f, top + 18f, 79f, top - 9f, 132f, top + 18f, 79f, top + 46f)
            stroke(canvas, 28f, top + 18f, 79f, top + 44f, Color.rgb(155, 247, 255), 1.4f)
            stroke(canvas, 79f, top + 44f, 130f, top + 18f, Color.rgb(175, 215, 255), 1.4f)
        }

        dot(canvas, 145f, 101f, 2.4f, Color.rgb(52, 218, 249))
        dot(canvas, 20f, 45f, 2.2f, Color.rgb(98, 151, 255))
        canvas.restore()
    }
}
