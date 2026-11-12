package at.heinz.deviceai.avatar3d

import kotlin.math.sin

/** Original ContinuumOrbitalRing phase law. */
internal object CockpitOrbMotion {
    private val speeds = floatArrayOf(1f, -.8f, .65f, -.55f, .45f)
    data class Pose(val yawDegrees: Float, val pitchDegrees: Float)
    fun sample(index: Int, phase: Float): Pose {
        require(index in speeds.indices)
        val offset = (phase.takeIf(Float::isFinite) ?: .4f) - .4f
        val speed = speeds[index]
        val degrees = 180f / kotlin.math.PI.toFloat()
        return Pose(offset * 1.25f * speed * degrees,
            .22f * sin(offset * .65f * speed) * degrees)
    }
}
