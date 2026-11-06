package at.heinz.deviceai.avatar3d

/** Cached geometry; linear screen-space travel. */
class AvatarCameraFraming(
    private val focus: AvatarGlbContract.Focus,
    private val aspect: Double,
    anchorX: Double,
    closeUp: Double,
    private val keepOut: AvatarFraming.KeepOut? = null
) {
    val rest = AvatarFraming.window(focus, aspect, anchorX, closeUp, keepOut)
    private val top = rest.cameraCenterY + rest.halfHeight
    private val inverseWidth = 1.0 / (2.0 * rest.halfHeight * aspect)
    private val inverseHeight = 1.0 / (2.0 * rest.halfHeight)
    private val enabled = safe(0.0)
    private val leftShift = if (enabled) boundedShift(-1.0) else 0.0
    private val rightShift = if (enabled) boundedShift(1.0) else 0.0

    fun sample(progress: Float, direction: Float): AvatarFraming.Window {
        require(progress.isFinite() && progress in 0f..1f)
        require(direction.isFinite() && direction in -1f..1f)
        if (progress == 0f || !enabled) return rest
        val zoom = 1.0 - PULLBACK * progress
        val shift = if (direction < 0f) leftShift * -direction else rightShift * direction
        val anchor = rest.anchorX + shift * progress
        val vertical = rest.halfHeight / zoom
        val horizontal = vertical * aspect
        return AvatarFraming.Window(-2.0 * horizontal * anchor, 2.0 * horizontal * (1.0 - anchor),
            vertical, top - vertical, rest.headScale * zoom, anchor)
    }

    private fun boundedShift(sign: Double): Double {
        if (safe(sign * MAX_PAN)) return sign * MAX_PAN
        var low = 0.0
        var high = MAX_PAN
        repeat(16) {
            val middle = (low + high) / 2.0
            if (safe(sign * middle)) low = middle else high = middle
        }
        return sign * low
    }

    private fun safe(shift: Double): Boolean {
        val zoom = 1.0 - PULLBACK
        val anchor = rest.anchorX + shift
        if (rest.anchorX - focus.reachLeft * inverseWidth < -EPSILON ||
            rest.anchorX + focus.reachRight * inverseWidth > 1.0 + EPSILON ||
            anchor - focus.reachLeft * inverseWidth * zoom < -EPSILON ||
            anchor + focus.reachRight * inverseWidth * zoom > 1.0 + EPSILON) return false
        val area = keepOut ?: return true
        val face = focus.face ?: return true
        for (i in face.left.indices) {
            val x0 = rest.anchorX + face.left[i] * inverseWidth
            val x1 = anchor + face.left[i] * inverseWidth * zoom
            val y0 = (top - (face.bottom + i * face.step)) * inverseHeight
            val y1 = y0 * zoom
            if (y0 > area.top && x0 < area.right - EPSILON) return false
            if (y1 > area.top && x1 < area.right - EPSILON) return false
            if ((y0 > area.top) != (y1 > area.top)) {
                // Check the intervening corner crossing.
                val t = (area.top - y0) / (y1 - y0)
                if (x0 + (x1 - x0) * t < area.right - EPSILON) return false
            }
        }
        return true
    }

    companion object {
        const val PULLBACK = .08
        const val MAX_PAN = .025
        private const val EPSILON = 1e-10
    }
}
