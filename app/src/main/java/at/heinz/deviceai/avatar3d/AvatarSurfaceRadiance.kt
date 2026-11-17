package at.heinz.deviceai.avatar3d

/** Multiplies the original face texture's emission, never its hue, alpha, eye materials or geometry. */
object AvatarSurfaceRadiance {
    const val IDLE_GAIN = 1.10f
    const val MAX_GAIN = 1.32f

    fun gain(energy: Float, enabled: Boolean): Float {
        if (!enabled || !energy.isFinite()) return 1f
        val t = ((energy - AvatarCoreLighting.IDLE_ENERGY) /
            (1f - AvatarCoreLighting.IDLE_ENERGY)).coerceIn(0f, 1f)
        return IDLE_GAIN + (MAX_GAIN - IDLE_GAIN) * t * t * (3f - 2f * t)
    }
}
