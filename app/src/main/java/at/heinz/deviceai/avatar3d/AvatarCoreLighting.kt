package at.heinz.deviceai.avatar3d

/** Bounded response of the existing cyan fill to the already-smoothed, real orb energy. No clock or pose state. */
object AvatarCoreLighting {
    const val BASE_LUMENS = 24_000f
    const val MAX_LUMENS = 30_000f
    const val IDLE_ENERGY = .18f

    fun intensity(energy: Float, enabled: Boolean): Float {
        // Same defensive presentation boundary as FilamentAvatarRenderer.applyGlow.
        if (!enabled || !energy.isFinite()) return BASE_LUMENS
        val t = ((energy - IDLE_ENERGY) / (1f - IDLE_ENERGY)).coerceIn(0f, 1f)
        val response = t * t * (3f - 2f * t)
        return BASE_LUMENS + (MAX_LUMENS - BASE_LUMENS) * response
    }
}
