package at.heinz.deviceai.ui

/** Presentation budget from actual Android signals. This does not alter model inference or functional state. */
object JarvisRenderQuality {
    enum class Tier { HIGH, MEDIUM, LOW, SUSPENDED }
    data class Budget(val tier: Tier, val frameDelayMillis: Long, val particleCount: Int,
                      val orbitSegments: Int, val animate: Boolean) {
        val reducedBloom: Boolean get() = tier != Tier.HIGH
    }

    fun resolve(foreground: Boolean, powerSave: Boolean, thermalStatus: Int?,
                reducedMotion: Boolean, inferenceActive: Boolean): Budget = when {
        !foreground -> Budget(Tier.SUSPENDED, 0L, 0, 40, false)
        powerSave || reducedMotion || (thermalStatus ?: 0) >= 3 -> Budget(Tier.LOW, 80L, 12, 40, false)
        (thermalStatus ?: 0) >= 2 -> Budget(Tier.MEDIUM, 50L, 16, 48, true)
        inferenceActive || (thermalStatus ?: 0) >= 1 -> Budget(Tier.MEDIUM, 33L, 32, 64, true)
        else -> Budget(Tier.HIGH, 16L, 70, 96, true)
    }
}
