package at.heinz.deviceai.ui

import java.util.Locale

/**
 * Everything the Continuum start screen shows, as plain values read from the app's real subsystems
 * (learning core, backlog, model runtime, storage, permissions). `null` always means "not measured / not available"
 * and is rendered as "—"; no field ever carries a sample number. Example values for the JVM preview live only in the
 * test sources.
 */
data class ContinuumDashboardState(
    val model: ModelStatus = ModelStatus(),
    val learning: LearningStatus = LearningStatus(),
    val storage: StorageStatus = StorageStatus(),
    val development: DevelopmentStatus = DevelopmentStatus(),
    val system: SystemStatus = SystemStatus(),
    val core: CoreStatus = CoreStatus(),
    /** Real pending items (curiosity question, receipts to review, web learning inbox); 0 hides the bell badge. */
    val pendingNotifications: Int = 0
) {
    data class ModelStatus(
        /** Name of the core that currently answers in chat, e.g. the loaded local model or the start core. */
        val name: String = "Continuum-Kern",
        val online: Boolean = false,
        val stateLabel: String = "Offline",
        /** Measured quality in percent (holdout accuracy of the learning core); null = not measured. */
        val qualityPercent: Int? = null,
        /** Median of the measured response times of the last chat answers; null = no answer measured yet. */
        val latencyMs: Int? = null,
        /** Real context capacity of the answering core, e.g. "8 Züge"; null = unknown. */
        val context: String? = null,
        val mode: String = "Lokal",
        val lastUpdate: String? = null
    )

    data class Event(val title: String, val ago: String, val kind: Int = 0)

    data class LearningStatus(
        /** Learning events recorded today (journal entries / weight updates); null = journal unreadable. */
        val today: Int? = null,
        /** Change versus yesterday in percent; null when yesterday had no data. */
        val changePercent: Int? = null,
        /** Real per-hour activity of the last 24 h (oldest first); empty = nothing recorded. */
        val series: List<Float> = emptyList(),
        val recent: List<Event> = emptyList(),
        val live: Boolean = false
    )

    data class Segment(val label: String, val bytes: Long?, val color: Int)

    data class StorageStatus(
        /** Used share of the device storage that holds the app data; null = unreadable. */
        val usedPercent: Int? = null,
        val segments: List<Segment> = emptyList(),
        val activeSources: Int? = null,
        val totalSources: Int? = null,
        val syncLabel: String = "Keine Synchronisation"
    )

    data class Project(val name: String, val status: String, val state: ProjectState, val ago: String)
    enum class ProjectState { OK, RUNNING, WORKING, READY, FAILED, IDLE }

    data class DevelopmentStatus(
        val activeProjects: Int? = null,
        val runningTasks: Int? = null,
        val errors: Int? = null,
        /** Real per-day completed checks of the last 14 days (oldest first); empty = none recorded. */
        val series: List<Float> = emptyList(),
        val projects: List<Project> = emptyList()
    ) {
        val healthy: Boolean? get() = errors?.let { it == 0 }
        val statusLabel: String get() = when (healthy) {
            true -> "Alle Systeme nominal"
            false -> "${errors} Fehler offen"
            null -> "Status unbekannt"
        }
    }

    data class SystemStatus(
        /** CPU share of this app process, measured over the last refresh interval. */
        val appCpuPercent: Int? = null,
        /** Device RAM in use. */
        val memoryPercent: Int? = null,
        /** Device storage in use. */
        val storagePercent: Int? = null,
        /** Connectivity: true = validated internet, false = offline, null = unknown. */
        val networkOnline: Boolean? = null,
        val networkLabel: String = "—",
        /** Real recent activity values (e.g. app CPU samples), oldest first. */
        val activity: List<Float> = emptyList(),
        val overall: String = "—"
    )

    /** Status lines under the six labels around the core; each is a real state, never a slogan. */
    data class CoreStatus(
        val connected: String = "Offline",
        val thinking: String = "Bereit",
        val learning: String = "Bereit",
        val analysing: String = "Bereit",
        val developing: String = "Bereit",
        val evolving: String = "Aus"
    )

    companion object {
        const val UNKNOWN = "—"

        fun percent(value: Int?): String = value?.let { "$it%" } ?: UNKNOWN
        fun count(value: Int?): String = value?.toString() ?: UNKNOWN
        fun millis(value: Int?): String = value?.let { "$it ms" } ?: UNKNOWN

        /** "432 GB", "1,4 GB", "820 MB", "12 KB" – German decimal comma, as in the reference. */
        fun bytes(value: Long?): String {
            if (value == null || value < 0) return UNKNOWN
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var v = value.toDouble(); var u = 0
            while (v >= 1000.0 && u < units.size - 1) { v /= 1000.0; u++ }
            val text = if (v >= 100 || u == 0) String.format(Locale.GERMANY, "%.0f", v) else String.format(Locale.GERMANY, "%.1f", v)
            return "$text ${units[u]}"
        }

        /** "vor 6 Min.", "vor 2 Std.", "vor 3 Tagen" (as in the reference) from an age in milliseconds. */
        fun ago(ageMs: Long?): String {
            if (ageMs == null || ageMs < 0) return UNKNOWN
            val minutes = ageMs / 60_000
            return when {
                minutes < 1 -> "gerade eben"
                minutes < 60 -> "vor $minutes Min."
                minutes < 60 * 24 -> "vor ${minutes / 60} Std."
                else -> "vor ${minutes / (60 * 24)} Tagen"
            }
        }
    }
}
