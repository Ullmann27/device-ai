package at.heinz.deviceai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import org.robolectric.Shadows.shadowOf

/** Consumes real host View invalidations. */
internal class CockpitHostFramePump(private val rootProvider: () -> View) : AutoCloseable {
    private var surface: Bitmap? = null

    fun drawRequestedFrame() {
        val root = rootProvider()
        if (!root.isAttachedToWindow || !root.isShown || root.width <= 0 || root.height <= 0) return
        val views = descendants(root)
        if (views.none { it.isShown && shadowOf(it).wasInvalidated() }) return
        views.forEach { shadowOf(it).clearWasInvalidated() }
        val bitmap = surface?.takeIf { it.width == root.width && it.height == root.height }
            ?: Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also {
                surface?.recycle()
                surface = it
            }
        root.draw(Canvas(bitmap))
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    override fun close() {
        surface?.recycle()
        surface = null
    }
}
