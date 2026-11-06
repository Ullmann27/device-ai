package at.heinz.deviceai.avatar3d

/**
 * The orthographic head window shared by [FilamentAvatarRenderer], the 2D head aura and [OrthographicCockpitProbe].
 *
 * The projection places the camera axis (the head centre) at an anchor share of the viewport width. The former fit
 * sized the window for a centred head, so with the cockpit anchor 0.70 only 0.6 of that half width remained right of
 * the axis: on the open Fold7 (884 × 760 dp, head stage 313 × 382 dp) the face was cut at the right edge (measured
 * on the packaged GLB with linear-blend skinning: 12 dp at rest, about 40 dp in a full turn) and, in a full turn,
 * the chin reached into the dialogue card below.
 *
 * The window now always holds the measured pose reach of the rig ([AvatarGlbContract.Focus.reachLeft],
 * [AvatarGlbContract.Focus.reachRight]). A stage with enough room keeps the owner-calibrated close-up and anchor
 * unchanged when its full pose silhouette also clears the dialogue. With a [KeepOut], fit the largest head first,
 * then the anchor closest to the preferred one (never left of [MIN_ANCHOR_X]) whose posed face stays clear;
 * without one, the size alone. The crown margin stays fixed, so a larger window only reveals more neck.
 * Pure arithmetic; callers cache the result per size change, never per frame.
 */
object AvatarFraming {
    /** The head centre never moves left of the stage centre. */
    const val MIN_ANCHOR_X = .5
    private const val SCALE_STEP = .005
    private const val MIN_SEARCH_SCALE = .5
    private const val ANCHOR_STEP = .01

    /**
     * Stage-normalized rectangle the posed face must not enter:
     * `x < right` and `y > top` (0..1 of the stage width/height from its top-left corner). The cockpit passes its
     * dialogue card.
     */
    data class KeepOut(val right: Double, val top: Double) {
        init { require(right.isFinite() && top.isFinite()) }
    }

    data class Window(
        /** Orthographic left/right edges relative to the camera axis (world metres). */
        val left: Double,
        val right: Double,
        /** Orthographic half height (world metres); the window spans ±[halfHeight] around [cameraCenterY]. */
        val halfHeight: Double,
        /** World height of the camera axis; the window top (crown margin) is independent of the fit. */
        val cameraCenterY: Double,
        /** Head size relative to the calibrated close-up: 1 = unchanged, < 1 = fitted to a narrow stage. */
        val headScale: Double,
        /** Effective share of the viewport width left of the head centre. */
        val anchorX: Double
    )

    fun window(focus: AvatarGlbContract.Focus, aspect: Double, anchorX: Double, closeUp: Double,
               keepOut: KeepOut? = null): Window {
        require(aspect.isFinite() && aspect > 0.0) { "Viewport aspect" }
        require(anchorX.isFinite() && anchorX > 0.0 && anchorX <= 1.0) { "Anchor" }
        require(closeUp.isFinite() && closeUp > 0.0 && closeUp <= 1.0) { "Close-up" }
        val full = focus.verticalHalfSpan(aspect)
        val calibrated = full * closeUp
        val top = focus.centerY + full
        fun result(anchor: Double, vertical: Double): Window {
            val horizontal = vertical * aspect
            return Window(-2.0 * horizontal * anchor, 2.0 * horizontal * (1.0 - anchor), vertical,
                focus.centerY + (full - vertical), calibrated / vertical, anchor)
        }
        /** Closed form at the preferred anchor: the smallest window holding the full pose reach. */
        fun reachFit(): Window {
            var vertical = calibrated
            if (anchorX < 1.0) vertical = maxOf(vertical, focus.reachRight / (2.0 * aspect * (1.0 - anchorX)))
            vertical = maxOf(vertical, focus.reachLeft / (2.0 * aspect * anchorX))
            return result(anchorX, vertical)
        }
        val sizeOnly = reachFit()
        // Without a declared free area the head never moves sideways; only its size adapts.
        val face = focus.face
        if (keepOut == null || face == null) return sizeOnly
        val lowestAnchor = minOf(anchorX, MIN_ANCHOR_X)
        fun fits(anchor: Double, vertical: Double): Boolean {
            // Horizontal room on each side of the axis is 2·h·aspect·(1 − anchor) and 2·h·aspect·anchor.
            val width = 2.0 * vertical * aspect
            if (focus.reachRight > width * (1.0 - anchor)) return false
            if (focus.reachLeft > width * anchor) return false
            for (band in face.left.indices) {
                // The lowest point of a band is its lower edge: the most conservative stage row.
                val stageY = (top - (face.bottom + band * face.step)) / (2.0 * vertical)
                if (stageY <= keepOut.top) continue
                if (anchor + face.left[band] / width < keepOut.right) return false
            }
            return true
        }
        // Largest head first; for that size the anchor nearest the preferred one.
        var scale = 1.0
        while (scale >= MIN_SEARCH_SCALE - 1e-9) {
            val vertical = calibrated / scale
            var distance = 0.0
            while (distance <= 1.0 - lowestAnchor + 1e-9) {
                val right = anchorX + distance
                if (right < 1.0 && fits(right, vertical)) return result(right, vertical)
                val left = anchorX - distance
                if (left >= lowestAnchor - 1e-9 && fits(left, vertical)) return result(left, vertical)
                distance += ANCHOR_STEP
            }
            scale -= SCALE_STEP
        }
        // Unreachable keep-out (tiny stage): ignore it, still keep the full pose reach inside the window.
        return sizeOnly
    }
}
