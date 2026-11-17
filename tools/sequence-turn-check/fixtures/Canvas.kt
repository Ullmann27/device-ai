package android.graphics

/** Rendering is deliberately unavailable. No host result may be reported as an Android frame. */
class Canvas {
    fun save(): Int = error("HOST_FIXTURE_RENDERING_UNAVAILABLE")
    fun scale(x: Float, y: Float): Unit = error("HOST_FIXTURE_RENDERING_UNAVAILABLE")
    fun restoreToCount(count: Int): Unit = error("HOST_FIXTURE_RENDERING_UNAVAILABLE")
}

class Rect
