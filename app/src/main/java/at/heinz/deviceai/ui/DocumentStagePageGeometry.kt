package at.heinz.deviceai.ui

import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView

/** Native bitmap footprint, panel-relative. */
internal object DocumentStagePageGeometry {
    fun fittedRect(image: ImageView, panel: ViewGroup, out: RectF): Boolean {
        out.setEmpty()
        if (image.width <= 0 || image.height <= 0 || panel.width <= 0 || panel.height <= 0 ||
            image.scaleType != ImageView.ScaleType.FIT_CENTER) return false
        val drawable = image.drawable as? BitmapDrawable ?: return false
        if (drawable.bitmap.isRecycled || drawable.bounds.isEmpty) return false
        out.set(drawable.bounds)
        image.imageMatrix.mapRect(out)
        out.offset(image.paddingLeft.toFloat(), image.paddingTop.toFloat())
        var current: View = image
        while (current !== panel) {
            if (current.visibility != View.VISIBLE || current.rotation != 0f || current.rotationX != 0f ||
                current.rotationY != 0f || current.scaleX <= 0f || current.scaleY <= 0f) return false
            current.matrix.mapRect(out)
            val parent = current.parent as? ViewGroup ?: return false
            out.offset(current.left.toFloat() - parent.scrollX, current.top.toFloat() - parent.scrollY)
            if (parent.clipChildren && !inside(out, 0f, 0f, parent.width.toFloat(), parent.height.toFloat())) return false
            if (parent.clipToPadding && !inside(out, parent.paddingLeft.toFloat(), parent.paddingTop.toFloat(),
                    (parent.width - parent.paddingRight).toFloat(), (parent.height - parent.paddingBottom).toFloat())) return false
            current = parent
        }
        if (!inside(out, 0f, 0f, panel.width.toFloat(), panel.height.toFloat())) return false
        out.set((out.left / panel.width).coerceIn(0f, 1f), (out.top / panel.height).coerceIn(0f, 1f),
            (out.right / panel.width).coerceIn(0f, 1f), (out.bottom / panel.height).coerceIn(0f, 1f))
        return out.width() > 0f && out.height() > 0f
    }

    private fun inside(rect: RectF, left: Float, top: Float, right: Float, bottom: Float): Boolean =
        JarvisWorkWindowGeometry.valid(rect) && rect.left >= left - .01f && rect.top >= top - .01f &&
            rect.right <= right + .01f && rect.bottom <= bottom + .01f
}
