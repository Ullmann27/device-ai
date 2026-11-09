package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.GazeTarget

/** Adapts the native panel's projected centre, not its final layout bounds, to the existing gaze contract. */
internal object JarvisWindowGaze {
    fun fromProjection(x: Float, y: Float, headX: Float, headY: Float,
                       viewportWidth: Float, viewportHeight: Float): GazeTarget {
        require(x.isFinite() && y.isFinite() && headX.isFinite() && headY.isFinite())
        require(viewportWidth.isFinite() && viewportWidth > 0f &&
            viewportHeight.isFinite() && viewportHeight > 0f)
        return GazeTarget(((x - headX) / viewportWidth * 2f).coerceIn(-.7f, .7f),
            ((headY - y) / viewportHeight).coerceIn(-.4f, .4f))
    }
}
