package at.heinz.deviceai.learning

import android.content.Context

/**
 * Explicit owner opt-in for one research-only CDX1 stage within the existing
 * daily, charging-only SWB1 JobScheduler cycle. No new service or wake lock.
 */
object ContinuumDualCoreAutoStudy {
    private const val PREFS = "continuum-dual-core-auto-study-v1"
    private const val OWNER_OPT_IN = "cdx1_owner_opt_in"
    const val MIN_REMAINING_MILLIS = 6_500L

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(OWNER_OPT_IN, false)

    fun setEnabled(context: Context, enabled: Boolean): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(OWNER_OPT_IN, enabled).commit()

    /** Pure policy: never start a new stage near the Android job's deadline. */
    fun shouldStart(enabled: Boolean, remainingMillis: Long, finishedStages: Int): Boolean =
        enabled && remainingMillis >= MIN_REMAINING_MILLIS &&
            finishedStages in 0 until ContinuumCdxLargeCurriculum.STAGES
}
