package at.heinz.deviceai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Transparent, locally rendered 3D-style AI core based on the Device AI
 * presentation reference. No remote textures, WebView or battery-intensive loop.
 *
 * All paint effects are decorative. They never indicate an inference or
 * device-health measurement.
 */
class ContinuumHologramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val frame = RectF()
    private val cyan = Color.rgb(39, 222, 255)
    private val blue = Color.rgb(56, 115, 255)
    private val violet = Color.rgb(184, 94, 255)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = "Dekorative holografische Darstellung des Device AI Kerns"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (340f * resources.displayMetrics.density).toInt()
        val desiredHeight = (234f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / 340f, height / 234f)
        if (scale <= 0f) return
        val save = canvas.save()
        canvas.translate((width - 340f * scale) / 2f, (height - 234f * scale) / 2f)
        canvas.scale(scale, scale)

        // Distant nebula and mountains remain behind a fully transparent view.
        brush.reset()
        brush.isAntiAlias = true
        brush.shader = RadialGradient(
            171f, 113f, 171f,
            intArrayOf(0x50296CFA.toInt(), 0x2023429F, 0x0000081C),
            floatArrayOf(0f, .43f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawOval(5f, 3f, 336f, 219f, brush)
        brush.shader = null
        for (index in 0 until 52) {
            val x = ((index * 83 + 29) % 324 + 8).toFloat()
            val y = ((index * 41 + 7) % 150 + 9).toFloat()
            val alpha = if (index % 7 == 0) 156 else 70
            brush.color = Color.argb(alpha, 140, 190, 255)
            brush.style = Paint.Style.FILL
            canvas.drawCircle(x, y, if (index % 9 == 0) 1.4f else .55f, brush)
        }

        // Luminous concentric platform (a base, not a fake model-status meter).
        brush.style = Paint.Style.FILL
        brush.shader = RadialGradient(171f, 207f, 116f,
            intArrayOf(0x80237BFF.toInt(), 0x25133772, Color.TRANSPARENT),
            floatArrayOf(0f, .63f, 1f), Shader.TileMode.CLAMP)
        canvas.drawOval(48f, 175f, 293f, 233f, brush)
        brush.shader = null
        for (index in 0..3) {
            brush.color = when (index) {
                0 -> Color.argb(240, 69, 214, 255)
                1 -> Color.argb(180, 81, 132, 255)
                2 -> Color.argb(90, 169, 98, 255)
                else -> Color.argb(56, 35, 149, 255)
            }
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = if (index == 0) 2f else 1.2f
            frame.set(49f + index * 11f, 191f + index * 4f, 291f - index * 11f, 228f - index * 3f)
            canvas.drawOval(frame, brush)
        }

        // Cyan depth halo makes the silhouette readable over any dark card.
        brush.style = Paint.Style.FILL
        brush.shader = RadialGradient(171f, 112f, 103f,
            intArrayOf(0x9230AEFF.toInt(), 0x56354DF0, 0x000B1945),
            floatArrayOf(.32f, .76f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(171f, 112f, 104f, brush)

        // Glass sphere: translucent surface and high contrast rim.
        brush.shader = RadialGradient(137f, 79f, 155f,
            intArrayOf(0xC462CBFF.toInt(), 0xD212307C.toInt(),
                0xEC0C1840.toInt(), 0xE92358B4.toInt()),
            floatArrayOf(0f, .38f, .76f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(171f, 111f, 80f, brush)
        brush.shader = null
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = 3.3f
        brush.color = cyan
        brush.setShadowLayer(12f, 0f, 0f, blue)
        canvas.drawCircle(171f, 111f, 80f, brush)
        brush.clearShadowLayer()
        brush.color = Color.argb(185, 208, 155, 255)
        brush.strokeWidth = 1.3f
        canvas.drawArc(RectF(93f, 32f, 249f, 191f), 187f, 185f, false, brush)

        // Long, tilted orbital rings, drawn over and behind the glass.
        drawOrbit(canvas, -23f, 129f, 22f, cyan, 2.8f)
        drawOrbit(canvas, 24f, 132f, -12f, violet, 2.7f)
        drawOrbit(canvas, -23f, 133f, 159f, Color.argb(230, 226, 121, 255), 2.3f)

        // Fine spherical meridians and interconnected neural points.
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = .8f
        brush.color = Color.argb(73, 137, 199, 255)
        for (i in 0..3) {
            val inset = 17f + i * 12f
            canvas.drawOval(RectF(90f + inset, 32f, 252f - inset, 192f), brush)
        }
        val constellation = Path().apply {
            moveTo(130f, 81f)
            lineTo(162f, 62f)
            lineTo(194f, 99f)
            lineTo(216f, 87f)
            moveTo(123f, 123f)
            lineTo(157f, 146f)
            lineTo(201f, 130f)
            lineTo(215f, 159f)
        }
        brush.color = Color.argb(122, 108, 204, 255)
        canvas.drawPath(constellation, brush)

        // Bright central intelligence spark and a few specular reflections.
        brush.style = Paint.Style.FILL
        brush.shader = RadialGradient(169f, 114f, 56f,
            intArrayOf(Color.WHITE, 0xDC3FDAFF.toInt(), 0x50366FFF, Color.TRANSPARENT),
            floatArrayOf(0f, .10f, .47f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(169f, 114f, 56f, brush)
        brush.shader = null
        brush.color = Color.argb(220, 210, 248, 255)
        brush.strokeWidth = 1.5f
        canvas.drawRect(168.3f, 91f, 169.7f, 137f, brush)
        canvas.drawRect(148f, 113.3f, 190f, 114.7f, brush)
        brush.shader = RadialGradient(129f, 62f, 47f,
            intArrayOf(0x8CFFFFFF.toInt(), 0x2038C8FF, Color.TRANSPARENT),
            floatArrayOf(0f, .58f, 1f), Shader.TileMode.CLAMP)
        canvas.drawOval(111f, 43f, 172f, 93f, brush)
        brush.shader = null

        canvas.restoreToCount(save)
    }

    private fun drawOrbit(
        canvas: Canvas,
        angle: Float,
        centerY: Float,
        startAngle: Float,
        color: Int,
        width: Float
    ) {
        val saved = canvas.save()
        canvas.rotate(angle, 171f, centerY)
        frame.set(41f, centerY - 38f, 301f, centerY + 38f)
        brush.shader = null
        brush.color = color
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = width
        brush.setShadowLayer(9f, 0f, 0f, color)
        canvas.drawArc(frame, startAngle, 272f, false, brush)
        brush.clearShadowLayer()
        brush.strokeWidth = .8f
        brush.color = Color.argb(170, 215, 241, 255)
        canvas.drawArc(frame, startAngle + 14f, 225f, false, brush)
        canvas.restoreToCount(saved)
    }
}
