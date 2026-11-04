package at.heinz.deviceai.ui

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * 2D UI bloom (panels, text, rims, 2D halos): the emissive drawing is recorded into a RenderNode, blurred with
 * RenderEffect and composited additively (BlendMode.PLUS) – light bleeds softly over edges.
 * The 3D head has its own Filament bloom (#478); this pass is for the 2D layer only.
 * API 31+ on a hardware canvas; otherwise [draw] returns false and callers use JarvisNeon.bloom (radial fallback).
 * Nodes are pooled per frame ([beginFrame]) so steady-state frames do not allocate.
 */
class JarvisBloomPass(private val painter: AndroidContinuumPainter) {
    private val pool = ArrayList<Any>()
    private var used = 0
    private val matrix = Matrix()
    private val inverse = Matrix()

    fun beginFrame() { used = 0 }

    fun supported(canvas: Canvas) = Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated

    /**
     * Records [emissive] in the canvas' current transform and draws it as blurred additive light.
     * [radiusDp] blur radius, [intensity] 0..1+, [density] px per dp, [widthPx]/[heightPx] node bounds (view size).
     */
    fun draw(canvas: Canvas, density: Float, widthPx: Int, heightPx: Int, radiusDp: Float, intensity: Float,
             emissive: (ContinuumPainter) -> Unit): Boolean {
        if (!supported(canvas) || intensity <= 0f || widthPx <= 0 || heightPx <= 0) return false
        return record(canvas, density, widthPx, heightPx, radiusDp, intensity, emissive)
    }

    @RequiresApi(31)
    @Suppress("DEPRECATION") // Canvas.getMatrix: the only way to replay the caller's transform into a RenderNode
    private fun record(canvas: Canvas, density: Float, w: Int, h: Int, radiusDp: Float, intensity: Float,
                       emissive: (ContinuumPainter) -> Unit): Boolean {
        val node = (if (used < pool.size) pool[used] else RenderNode("jarvisBloom").also { pool += it }) as RenderNode
        used++
        canvas.getMatrix(matrix)
        if (!matrix.invert(inverse)) return false
        node.setPosition(0, 0, w, h)
        val r = radiusDp * density
        node.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL))
        node.setUseCompositingLayer(true, Paint().apply { blendMode = BlendMode.PLUS; alpha = (255 * intensity.coerceIn(0f, 1f)).toInt() })
        val rc = node.beginRecording()
        rc.concat(matrix)
        painter.begin(rc)
        emissive(painter)
        node.endRecording()
        painter.begin(canvas)
        canvas.save(); canvas.concat(inverse); canvas.drawRenderNode(node); canvas.restore()
        return true
    }
}
