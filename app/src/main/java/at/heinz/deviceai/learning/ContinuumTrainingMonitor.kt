package at.heinz.deviceai.learning

import android.content.Context
import android.os.SystemClock

/**
 * Auditable UI telemetry only. Never drives scheduling or modifies model weights.
 * Separate public/offline/CDX channels prevent one job hiding another.
 */
object ContinuumTrainingMonitor {
    const val PUBLIC = "public"
    const val OFFLINE = "offline"
    const val CDX = "cdx"
    private const val PREFIX = "continuum-training-monitor-v1-"
    private const val RUNNING_TIMEOUT_MS = 45_000L

    data class Snapshot(
        val channel: String,
        val active: Boolean,
        val phase: String,
        val subject: String,
        val model: String,
        val startedAt: Long,
        val changedAt: Long,
        val endedAt: Long,
        val outcome: String,
        val detail: String,
        val stale: Boolean
    ) {
        val trainingNow: Boolean get() =
            active && !stale && phase == "TRAIN_WEIGHTS"
        val researchingNow: Boolean get() =
            active && !stale && !trainingNow
    }

    private fun prefs(context: Context, channel: String) =
        context.applicationContext.getSharedPreferences(PREFIX + checkedChannel(channel),
            Context.MODE_PRIVATE)

    private fun checkedChannel(channel: String): String {
        require(channel in setOf(PUBLIC, OFFLINE, CDX))
        return channel
    }

    private fun safe(text: String, limit: Int = 86): String =
        text.filter { it >= ' ' && it != '\u007f' }.trim().take(limit)

    @Synchronized
    fun begin(context: Context, channel: String, subject: String, model: String) {
        val now = System.currentTimeMillis()
        prefs(context, channel).edit()
            .putBoolean("active", true).putString("phase", "PREPARING")
            .putString("subject", safe(subject)).putString("model", safe(model, 30))
            .putLong("started", now).putLong("changed", now)
            .putLong("elapsed", SystemClock.elapsedRealtime())
            .putString("detail", "").commit()
    }

    @Synchronized
    fun phase(context: Context, channel: String, phase: String, detail: String = "") {
        require(phase in setOf("PREPARING", "FETCH_SOURCE", "DISCOVER_TOPICS",
            "ARCHIVE_SOURCE", "TRAIN_WEIGHTS", "AUDIT", "SAVE_RESULT"))
        val p = prefs(context, channel)
        if (!p.getBoolean("active", false)) return
        p.edit().putString("phase", phase).putString("detail", safe(detail))
            .putLong("changed", System.currentTimeMillis())
            .putLong("elapsed", SystemClock.elapsedRealtime()).commit()
    }

    @Synchronized
    fun end(context: Context, channel: String, outcome: String, detail: String = "") {
        require(outcome in setOf("ACCEPTED", "REJECTED", "NO_CHANGE", "DEFERRED",
            "INTERRUPTED", "FAILED", "FINISHED"))
        val p = prefs(context, channel)
        p.edit().putBoolean("active", false).putString("phase", "DONE")
            .putString("outcome", outcome).putString("detail", safe(detail))
            .putLong("ended", System.currentTimeMillis())
            .putLong("changed", System.currentTimeMillis())
            .remove("elapsed").commit()
    }

    fun read(context: Context, channel: String): Snapshot {
        val p = prefs(context, channel)
        val active = p.getBoolean("active", false)
        val changed = p.getLong("changed", 0L)
        val now = System.currentTimeMillis()
        val age = now - changed
        // A killed process cannot retain a live 'training' badge indefinitely.
        val stale = active && (changed <= 0L || age < 0L ||
            age > RUNNING_TIMEOUT_MS ||
            p.getLong("elapsed", Long.MAX_VALUE) > SystemClock.elapsedRealtime())
        return Snapshot(channel, active, p.getString("phase", "NOT_STARTED").orEmpty(),
            p.getString("subject", "").orEmpty(), p.getString("model", "").orEmpty(),
            p.getLong("started", 0L), changed, p.getLong("ended", 0L),
            p.getString("outcome", "NOT_STARTED").orEmpty(),
            p.getString("detail", "").orEmpty(), stale)
    }
}
