package at.heinz.deviceai.ui

import at.heinz.deviceai.ui.JarvisShellLayout.Box
import at.heinz.deviceai.ui.JarvisShellLayout.Slot

/**
 * EXP-52-CINE-DEPTH: optional depth from the *existing* authoritative
 * JarvisDeckClearance. No event bus, timer, state replay or View hit-target movement.
 */
object PerimeterWingChoreographer {
    fun depth(slot: Slot?, box: Box, screenWidth: Float, clearance: Float,
              enabled: Boolean, budget: JarvisRenderQuality.Budget): JarvisMotion.Depth.State {
        if (!enabled || !budget.animate || !JarvisDeckClearance.clears(slot) ||
            !clearance.isFinite() || clearance <= 0f || !screenWidth.isFinite() ||
            screenWidth <= 0f) return JarvisMotion.Depth.FLAT

        val c = clearance.coerceIn(0f, 1f)
        val side = ((box.centerX - screenWidth * .5f) / (screenWidth * .5f)).coerceIn(-1f, 1f)
        val tiltX = if (slot == null) -22f * c else -12f * c
        val tiltY = -18f * side * c
        val blur = when (budget.tier) {
            JarvisRenderQuality.Tier.HIGH -> 4.5f * c
            JarvisRenderQuality.Tier.MEDIUM -> 1.5f * c
            else -> 0f
        }
        return JarvisMotion.Depth.State(1f, blur, tiltX, tiltY, 0f, 0f)
    }
}
