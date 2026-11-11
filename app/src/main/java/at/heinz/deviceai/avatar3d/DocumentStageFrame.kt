package at.heinz.deviceai.avatar3d

/** Geometry from the physical window. */
data class DocumentStageFrame(
    val windowId: String,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val opacity: Float,
    val result: Boolean,
    val cameraDistance: Float = 8000f,
    val pageLeft: Float = 0f,
    val pageTop: Float = 0f,
    val pageWidth: Float = 0f,
    val pageHeight: Float = 0f,
    val pageRectValid: Boolean = false
) {
    fun valid(): Boolean = windowId.isNotBlank() && windowId.length <= 96 &&
        viewportWidth in 1..16384 && viewportHeight in 1..16384 &&
        listOf(left, top, width, height, yawDegrees, pitchDegrees, opacity, cameraDistance,
            pageLeft, pageTop, pageWidth, pageHeight).all(Float::isFinite) &&
        width > 0f && height > 0f && width <= viewportWidth * 4f && height <= viewportHeight * 4f &&
        kotlin.math.abs(left) <= viewportWidth * 8f && kotlin.math.abs(top) <= viewportHeight * 8f &&
        kotlin.math.abs(yawDegrees) <= 80f && kotlin.math.abs(pitchDegrees) <= 80f &&
        opacity in 0f..1f && cameraDistance >= 1f &&
        (!pageRectValid || (pageLeft in 0f..1f && pageTop in 0f..1f &&
            pageWidth > 0f && pageHeight > 0f && pageLeft + pageWidth <= 1.00001f &&
            pageTop + pageHeight <= 1.00001f))

    /** Bounded output, native controls unaffected. */
    fun bufferSize(maxEdge: Int = 1280): Pair<Int, Int> {
        require(valid() && maxEdge in 64..2048)
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(viewportWidth, viewportHeight))
        return maxOf(1, (viewportWidth * scale).toInt()) to maxOf(1, (viewportHeight * scale).toInt())
    }
}
