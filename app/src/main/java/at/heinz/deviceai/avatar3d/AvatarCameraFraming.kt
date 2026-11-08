package at.heinz.deviceai.avatar3d

/** Cached geometry; linear screen-space travel, always bounded by full-pose clipping and dialogue keep-out. */
class AvatarCameraFraming(
    private val focus: AvatarGlbContract.Focus,
    private val aspect: Double,
    anchorX: Double,
    closeUp: Double,
    private val keepOut: AvatarFraming.KeepOut? = null,
    private val profile: Profile = Profile.LEGACY
) {
    enum class Profile(
        val pullback: Double,
        val rightWindowAnchor: Double,
        val panAwayFromWindow: Boolean
    ) {
        /** Compatibility path used by existing probes/tests. */
        LEGACY(.08, .0, false),
        /** Single app / document terminal: visibly frees the right-hand stage. */
        MEDIUM_TERMINAL(.24, .34, true),
        /** Coding / swarm / large analysis: wide mission-bay composition. */
        FULL_MISSION_BAY(.36, .28, true)
    }

    val rest = AvatarFraming.window(focus, aspect, anchorX, closeUp, keepOut)
    private val top = rest.cameraCenterY + rest.halfHeight
    private val inverseWidth = 1.0 / (2.0 * rest.halfHeight * aspect)
    private val inverseHeight = 1.0 / (2.0 * rest.halfHeight)
    private val enabled = safe(0.0)

    private val legacyLeftShift = if (enabled && profile == Profile.LEGACY) boundedShift(-LEGACY_MAX_PAN) else 0.0
    private val legacyRightShift = if (enabled && profile == Profile.LEGACY) boundedShift(LEGACY_MAX_PAN) else 0.0

    fun sample(progress: Float, direction: Float): AvatarFraming.Window {
        require(progress.isFinite() && progress in 0f..1f)
        require(direction.isFinite() && direction in -1f..1f)
        if (progress == 0f || !enabled) return rest

        val zoom = 1.0 - profile.pullback * progress
        val fullShift = if (profile.panAwayFromWindow) {
            targetShiftAway(direction)
        } else {
            if (direction < 0f) legacyLeftShift * -direction else legacyRightShift * direction
        }
        val anchor = rest.anchorX + fullShift * progress
        val vertical = rest.halfHeight / zoom
        val horizontal = vertical * aspect
        return AvatarFraming.Window(
            -2.0 * horizontal * anchor,
            2.0 * horizontal * (1.0 - anchor),
            vertical,
            top - vertical,
            rest.headScale * zoom,
            anchor
        )
    }

    private fun targetShiftAway(direction: Float): Double {
        if (direction == 0f) return 0.0
        val requestedAnchor = if (direction > 0f) {
            // Terminal right -> head moves left; never move toward the terminal.
            minOf(rest.anchorX, profile.rightWindowAnchor)
        } else {
            // Mirror only when the head is not already far enough to the right.
            maxOf(rest.anchorX, 1.0 - profile.rightWindowAnchor)
        }
        val requested = (requestedAnchor - rest.anchorX) * kotlin.math.abs(direction)
        return boundedShift(requested)
    }

    /** Binary-search the largest safe fraction of an arbitrary requested shift. */
    private fun boundedShift(requested: Double): Double {
        if (requested == 0.0 || !requested.isFinite()) return 0.0
        if (safe(requested)) return requested
        val sign = if (requested < 0.0) -1.0 else 1.0
        val magnitude = kotlin.math.abs(requested)
        var low = 0.0
        var high = magnitude
        repeat(18) {
            val middle = (low + high) / 2.0
            if (safe(sign * middle)) low = middle else high = middle
        }
        return sign * low
    }

    private fun safe(shift: Double): Boolean {
        val zoom = 1.0 - profile.pullback
        val anchor = rest.anchorX + shift
        if (anchor <= 0.0 || anchor >= 1.0) return false
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
                val t = (area.top - y0) / (y1 - y0)
                if (x0 + (x1 - x0) * t < area.right - EPSILON) return false
            }
        }
        return true
    }

    companion object {
        /** Compatibility constants retained for existing callers/tests. */
        const val PULLBACK = .08
        const val MAX_PAN = .025
        private const val LEGACY_MAX_PAN = MAX_PAN
        private const val EPSILON = 1e-10
    }
}
