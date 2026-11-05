package at.heinz.deviceai.avatar3d

/**
 * Pure mirror of FilamentAvatarRenderer's current ORTHO projection math.
 * Does not claim to know the animated GLB vertex positions or skin weights.
 * Feed projected camera-relative vertex bounds from a separate rig probe.
 */
internal object OrthographicCockpitProbe {
    data class Window(
        val widthPx: Int,
        val heightPx: Int,
        val leftWorld: Double,
        val rightWorld: Double,
        val bottomWorld: Double,
        val topWorld: Double
    ) {
        init {
            require(widthPx > 0 && heightPx > 0)
            require(rightWorld > leftWorld && topWorld > bottomWorld)
        }

        val projectedCenterFraction: Double
            get() = -leftWorld / (rightWorld - leftWorld)

        val availableRightFraction: Double
            get() = rightWorld / (rightWorld - leftWorld)

        fun pixelX(projectedWorldX: Double): Double =
            (projectedWorldX - leftWorld) * widthPx / (rightWorld - leftWorld)

        fun clipping(projectedMinX: Double, projectedMaxX: Double): Clip {
            require(projectedMinX.isFinite() && projectedMaxX.isFinite() && projectedMinX <= projectedMaxX)
            val leftPx = maxOf(0.0, -pixelX(projectedMinX))
            val rightPx = maxOf(0.0, pixelX(projectedMaxX) - widthPx)
            return Clip(leftPx, rightPx, leftPx == 0.0 && rightPx == 0.0)
        }
    }

    data class Clip(val leftOverhangPx: Double, val rightOverhangPx: Double, val fits: Boolean)

    fun fromCurrentFilamentMath(
        widthPx: Int,
        heightPx: Int,
        focusHalfWidthWorld: Double,
        focusHalfHeightWorld: Double,
        closeUp: Double,
        anchorX: Double
    ): Window {
        require(widthPx > 0 && heightPx > 0)
        require(focusHalfWidthWorld.isFinite() && focusHalfWidthWorld > 0.0)
        require(focusHalfHeightWorld.isFinite() && focusHalfHeightWorld > 0.0)
        require(closeUp.isFinite() && closeUp in 0.4..1.0)
        require(anchorX.isFinite() && anchorX in 0.2..1.0)
        val aspect = widthPx.toDouble() / heightPx
        val vertical = maxOf(focusHalfHeightWorld, focusHalfWidthWorld / aspect) * 1.08 * closeUp
        val horizontal = vertical * aspect
        return Window(widthPx, heightPx, -2.0 * horizontal * anchorX,
            2.0 * horizontal * (1.0 - anchorX), -vertical, vertical)
    }
}
