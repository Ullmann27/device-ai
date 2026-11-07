package at.heinz.deviceai.avatar3d

/**
 * Pixel view of FilamentAvatarRenderer's ORTHO projection (same [AvatarFraming] window).
 * Does not itself know the animated GLB vertex positions or skin weights.
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

    /** Delegates to [AvatarFraming], the function the renderer itself uses; reach defaults to the half width. */
    fun fromCurrentFilamentMath(
        widthPx: Int,
        heightPx: Int,
        focusHalfWidthWorld: Double,
        focusHalfHeightWorld: Double,
        closeUp: Double,
        anchorX: Double,
        reachLeftWorld: Double = focusHalfWidthWorld,
        reachRightWorld: Double = focusHalfWidthWorld
    ): Window {
        require(widthPx > 0 && heightPx > 0)
        require(focusHalfWidthWorld.isFinite() && focusHalfWidthWorld > 0.0)
        require(focusHalfHeightWorld.isFinite() && focusHalfHeightWorld > 0.0)
        require(reachLeftWorld.isFinite() && reachLeftWorld > 0.0 && reachRightWorld.isFinite() && reachRightWorld > 0.0)
        require(closeUp.isFinite() && closeUp in 0.4..1.0)
        require(anchorX.isFinite() && anchorX in 0.2..1.0)
        val focus = AvatarGlbContract.Focus(0.0, 0.0, 0.0, focusHalfWidthWorld, focusHalfHeightWorld, 0.0,
            reachLeftWorld, reachRightWorld)
        val fitted = AvatarFraming.window(focus, widthPx.toDouble() / heightPx, anchorX, closeUp)
        return Window(widthPx, heightPx, fitted.left, fitted.right, -fitted.halfHeight, fitted.halfHeight)
    }
}
