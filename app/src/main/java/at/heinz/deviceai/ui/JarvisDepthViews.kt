package at.heinz.deviceai.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Real Android 3-D tilt + depth blur for panels from [JarvisMotion.Depth] (#479 logic, unchanged):
 * View.cameraDistance, rotationX/Y, scale, translation and – on API 31+ – a RenderEffect blur growing with depth.
 * Call [apply] from the host's frame clock (e.g. the shell's invalidate loop); [reset] clears everything.
 */
object JarvisDepthViews {
    fun apply(view: View, layer: JarvisMotion.Depth.Layer, screenWidthPx: Float, screenHeightPx: Float, timeMs: Long,
              animatorScale: Float = 1f) {
        val d = view.resources.displayMetrics.density
        val cx = (view.left + view.right) / 2f; val cy = (view.top + view.bottom) / 2f
        val s = JarvisMotion.Depth.state(layer, cx, cy, screenWidthPx, screenHeightPx, timeMs, animatorScale)
        view.cameraDistance = JarvisMotion.Depth.CAMERA_DISTANCE_DP * d
        view.rotationX = s.rotationX; view.rotationY = s.rotationY
        view.scaleX = s.scale; view.scaleY = s.scale
        view.translationX = s.dx * d; view.translationY = s.dy * d
        if (Build.VERSION.SDK_INT >= 31) {
            val r = s.blurDp * d
            view.setRenderEffect(if (r > .5f) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) else null)
        }
    }

    fun reset(view: View) {
        view.rotationX = 0f; view.rotationY = 0f; view.scaleX = 1f; view.scaleY = 1f
        view.translationX = 0f; view.translationY = 0f
        if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null)
    }
}
