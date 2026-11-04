package android.view

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet

/** No Android lifecycle, rendering, queue or attachment is emulated. Invalidation is a no-op. */
open class View(val context: Context, attrs: AttributeSet? = null) {
    var isClickable = false
    var isFocusable = false
    var importantForAccessibility = 0
    val isAttachedToWindow = false
    val isShown = false
    val resources get() = context.resources
    val width = 0
    val height = 0
    fun invalidate() = Unit
    fun postInvalidateOnAnimation(): Unit = error("HOST_FIXTURE_FRAME_QUEUE_UNAVAILABLE")
    fun post(action: () -> Unit): Boolean = error("HOST_FIXTURE_MAIN_QUEUE_UNAVAILABLE")
    protected open fun onAttachedToWindow(): Unit = error("HOST_FIXTURE_LIFECYCLE_UNAVAILABLE")
    protected open fun onDetachedFromWindow(): Unit = error("HOST_FIXTURE_LIFECYCLE_UNAVAILABLE")
    protected open fun onDraw(canvas: Canvas): Unit = error("HOST_FIXTURE_RENDERING_UNAVAILABLE")

    companion object { const val IMPORTANT_FOR_ACCESSIBILITY_NO = 2 }
}

class ViewGroup(context: Context) : View(context)
