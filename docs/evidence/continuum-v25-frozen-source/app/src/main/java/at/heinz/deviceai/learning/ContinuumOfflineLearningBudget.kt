package at.heinz.deviceai.learning

/**
 * A fail-safe resource gate for the daily offline learning job.
 * Values are observations, not prompts and never contain user content.
 * Neither charging nor Android's device-dependent battery-not-low threshold
 * are required. A locally observed battery level of at least 20 percent is
 * sufficient; unknown battery levels are deferred conservatively.
 * Thermal and free-storage limits remain independent guards.
 */
data class ContinuumLearningResources(
    val batteryPercent: Int?,
    val batteryLow: Boolean,
    val thermalStatus: Int?,
    val storageBytesAvailable: Long?
)

object ContinuumOfflineLearningBudget {
    const val MIN_BATTERY_PERCENT = 20
    const val MIN_STORAGE_BYTES = 128L * 1024 * 1024
    const val MAX_WALL_MILLIS = 15_000L
    // Android THERMAL_STATUS_MODERATE = 2, first available on API 29.
    const val MAX_PERMITTED_THERMAL_STATUS = 1

    fun batteryPercent(level:Int,scale:Int):Int? =
        if(scale>0 && level in 0..scale) (level.toLong()*100L/scale).toInt() else null

    /** Null means learning may run. Reasons are fixed codes, never source text. */
    fun deferralReason(resources: ContinuumLearningResources): String? = when {
        resources.batteryPercent == null -> "BATTERY_UNKNOWN"
        resources.batteryPercent < MIN_BATTERY_PERCENT || resources.batteryLow ->
            "BATTERY_LOW"
        resources.thermalStatus != null &&
            resources.thermalStatus > MAX_PERMITTED_THERMAL_STATUS -> "THERMAL_LIMIT"
        resources.storageBytesAvailable != null &&
            resources.storageBytesAvailable < MIN_STORAGE_BYTES -> "STORAGE_LOW"
        else -> null
    }

    fun deadlineReached(startMillis: Long, nowMillis: Long): Boolean =
        nowMillis < startMillis || nowMillis - startMillis >= MAX_WALL_MILLIS
}

/** Shared per-run resource/deadline check. A fresh retry gets fresh observations.
 * Once stopped, this generation stays stopped even if resources later recover.
 */
internal class ContinuumLearningRunBudget(
    private val startedAt: Long,
    private val wallMillis: Long,
    private val now: () -> Long,
    private val resources: () -> ContinuumLearningResources
) {
    private var nextResourceCheck = startedAt
    var deferredReason: String? = null
        private set

    fun cancelled(): Boolean {
        if (deferredReason != null) return true
        val elapsed = now()
        if (elapsed < startedAt || elapsed - startedAt >= wallMillis) {
            deferredReason = "TIME_BUDGET"
        } else if (elapsed >= nextResourceCheck) {
            nextResourceCheck = elapsed + 1_500L
            deferredReason = ContinuumOfflineLearningBudget.deferralReason(resources())
        }
        return deferredReason != null
    }
}
