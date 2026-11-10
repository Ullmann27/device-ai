package at.heinz.deviceai.cockpit

/**
 * Additive cinema fly-in sample on the existing [CockpitAnimationBus].
 *
 * Bot 04 (code52/cinema-flyin-20261110, Draft-PR #692) should publish the
 * actually projected visual centre of the moving window after dx/dy/scale/tilt,
 * not only the rest pose / endpoint. Unknown [CockpitAnimationEvent.Extension]
 * kinds are ignored by design. This is not a second animation clock.
 *
 * [gazeX]/[gazeY] use [at.heinz.deviceai.avatar.GazeTarget] space (viewer right / up).
 * [accentArgb] is the window's real palette colour, or null when none is known.
 * [turnId] must match the triggering CommandFlyIn / DocumentQuestion / ActionStarted.
 */
data class WindowProjectedMotion(
    override val turnId: String,
    override val atMillis: Long,
    val windowId: String,
    val gazeX: Float,
    val gazeY: Float,
    val accentArgb: Int? = null,
    val phase: Phase = Phase.FLYING,
) : CockpitAnimationEvent.Extension(KIND) {
    enum class Phase { FLYING, SETTLED, CLOSING, CLOSED }

    companion object {
        const val KIND = "window-projected-motion"
    }
}
