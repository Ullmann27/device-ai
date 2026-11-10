package at.heinz.deviceai.ui

/** Presentation budget from actual Android signals. This does not alter model inference or functional state. */
object JarvisRenderQuality {
    enum class Tier { HIGH, MEDIUM, LOW, SUSPENDED }
    data class Budget(val tier: Tier, val frameDelayMillis: Long, val particleCount: Int,
                      val orbitSegments: Int, val animate: Boolean,
                      val coolInferenceLighting: Boolean = false) {
        /** Enable cinematic lighting only when Android explicitly reports cool. */
        val reducedBloom: Boolean get() = tier != Tier.HIGH && !coolInferenceLighting
    }

    fun resolve(foreground: Boolean, powerSave: Boolean, thermalStatus: Int?,
                reducedMotion: Boolean, inferenceActive: Boolean): Budget = when {
        !foreground -> Budget(Tier.SUSPENDED, 0L, 0, 40, false)
        powerSave || reducedMotion || (thermalStatus ?: 0) >= 3 -> Budget(Tier.LOW, 80L, 12, 40, false)
        // Fold7 heat (#642): while the CPU backend decodes, a warm device gets the smallest animated budget
        // so presentation never competes with inference for the thermal headroom.
        inferenceActive && (thermalStatus ?: 0) >= 2 -> Budget(Tier.MEDIUM, 80L, 12, 40, true)
        inferenceActive && (thermalStatus ?: 0) >= 1 -> Budget(Tier.MEDIUM, 50L, 16, 48, true)
        (thermalStatus ?: 0) >= 2 -> Budget(Tier.MEDIUM, 50L, 16, 48, true)
        inferenceActive || (thermalStatus ?: 0) >= 1 -> Budget(Tier.MEDIUM, 33L, 32, 64, true,
            coolInferenceLighting = inferenceActive && thermalStatus == 0)
        else -> Budget(Tier.HIGH, 16L, 70, 96, true)
    }

    /**
     * EXP15 contract on top of [resolve]: no second quality model and no second clock.
     * Production (cinema not opted in) always keeps the resolved thermal budget. Under
     * cinema opt-in the owner can turn the experiment off; hard safety floors
     * (background, reduced motion, energy saving, severe thermal status) still apply.
     */
    fun adaptiveAnimation(foreground: Boolean, powerSave: Boolean, thermalStatus: Int?,
                          reducedMotion: Boolean, inferenceActive: Boolean,
                          cinemaOptIn: Boolean, experimentEnabled: Boolean): Budget {
        val resolved = resolve(foreground, powerSave, thermalStatus, reducedMotion, inferenceActive)
        if (!cinemaOptIn || experimentEnabled) return resolved
        if (!foreground || powerSave || reducedMotion || (thermalStatus ?: 0) >= 3) return resolved
        return Budget(Tier.HIGH, 16L, 70, 96, true)
    }
}
