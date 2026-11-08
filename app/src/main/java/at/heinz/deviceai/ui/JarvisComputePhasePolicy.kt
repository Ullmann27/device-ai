package at.heinz.deviceai.ui

/** Phase-specific presentation shaping. It can only reduce load relative to the base budget. */
internal object JarvisComputePhasePolicy {
    enum class Phase { IDLE, LISTENING, THINKING, SPEAKING, RESEARCH, LEARNING }

    fun apply(
        base: JarvisRenderQuality.Budget,
        phase: Phase,
        diagnosticProofActive: Boolean = false
    ): JarvisRenderQuality.Budget {
        if (diagnosticProofActive || base.tier == JarvisRenderQuality.Tier.SUSPENDED) return base
        // LOW/reduced-motion/power-save remains authoritative. Essential avatar motion
        // is handled separately by AvatarEssentialMotionPolicy.
        if (!base.animate) return base
        return when (phase) {
            Phase.IDLE -> base.copy(
                frameDelayMillis = maxOf(base.frameDelayMillis, 33L),
                particleCount = minOf(base.particleCount, 40),
                orbitSegments = minOf(base.orbitSegments, 64)
            )
            Phase.LISTENING -> base.copy(
                particleCount = minOf(base.particleCount, 48),
                orbitSegments = minOf(base.orbitSegments, 72)
            )
            Phase.THINKING -> base.copy(
                frameDelayMillis = maxOf(base.frameDelayMillis, 42L),
                particleCount = minOf(base.particleCount, 16),
                orbitSegments = minOf(base.orbitSegments, 48)
            )
            Phase.SPEAKING -> base
            Phase.RESEARCH, Phase.LEARNING -> base.copy(
                frameDelayMillis = maxOf(base.frameDelayMillis, 50L),
                particleCount = minOf(base.particleCount, 12),
                orbitSegments = minOf(base.orbitSegments, 40)
            )
        }
    }
}
