package at.heinz.deviceai.ui

/** Pure policy: accessibility can stop motion; power/thermal pressure only removes decoration. */
object AvatarEssentialMotionPolicy {
    fun essentialAllowed(
        foreground: Boolean,
        animationsEnabled: Boolean,
        systemAnimatorsEnabled: Boolean
    ): Boolean = foreground && animationsEnabled && systemAnimatorsEnabled

    fun decorativeAllowed(
        essentialAllowed: Boolean,
        budget: JarvisRenderQuality.Budget
    ): Boolean = essentialAllowed && budget.animate

    fun frameDelayMillis(
        budget: JarvisRenderQuality.Budget,
        speaking: Boolean
    ): Long = if (speaking && budget.tier == JarvisRenderQuality.Tier.LOW)
        minOf(budget.frameDelayMillis, 50L)
    else budget.frameDelayMillis
}
