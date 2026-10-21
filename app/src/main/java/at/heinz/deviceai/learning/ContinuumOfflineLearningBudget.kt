package at.heinz.deviceai.learning

/**
 * A fail-safe resource gate for the daily offline learning job.
 * Values are observations, not prompts and never contain user content.
 * Unknown values are tolerated only while Android JobScheduler reports a
 * battery-not-low state; charging is not required. The concrete 25% battery,
 * thermal and storage gates remain enforced when measurements are available.
 */
data class ContinuumLearningResources(
    val batteryPercent: Int?,
    val batteryLow: Boolean,
    val thermalStatus: Int?,
    val storageBytesAvailable: Long?
)

object ContinuumOfflineLearningBudget {
    const val MIN_BATTERY_PERCENT = 25
    const val MIN_STORAGE_BYTES = 128L * 1024 * 1024
    const val MAX_WALL_MILLIS = 15_000L
    // Android THERMAL_STATUS_MODERATE = 2, first available on API 29.
    const val MAX_PERMITTED_THERMAL_STATUS = 1

    /** Null means learning may run. Reasons are fixed codes, never source text. */
    fun deferralReason(resources: ContinuumLearningResources): String? = when {
        resources.batteryLow ||
            (resources.batteryPercent != null && resources.batteryPercent <= MIN_BATTERY_PERCENT) ->
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
