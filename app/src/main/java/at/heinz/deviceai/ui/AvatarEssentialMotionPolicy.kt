package at.heinz.deviceai.ui

/** Pure policy: accessibility can stop motion; low budgets keep only a visible avatar alive. */
object AvatarEssentialMotionPolicy {
    fun frameLoopAllowed(
        foreground: Boolean,
        animationsEnabled: Boolean,
        systemAnimatorsEnabled: Boolean,
        avatarEnabled: Boolean,
        budget: JarvisRenderQuality.Budget
    ): Boolean = foreground && animationsEnabled && systemAnimatorsEnabled &&
        (budget.animate || avatarEnabled)

    fun decorativeAllowed(
        frameLoopAllowed: Boolean,
        budget: JarvisRenderQuality.Budget
    ): Boolean = frameLoopAllowed && budget.animate

    fun frameDelayMillis(
        budget: JarvisRenderQuality.Budget,
        speaking: Boolean
    ): Long = if (speaking && budget.tier == JarvisRenderQuality.Tier.LOW)
        minOf(budget.frameDelayMillis, 50L)
    else budget.frameDelayMillis
}
