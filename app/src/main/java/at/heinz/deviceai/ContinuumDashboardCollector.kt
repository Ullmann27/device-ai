package at.heinz.deviceai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import at.heinz.deviceai.cognitive.DeviceDevelopmentBacklog
import at.heinz.deviceai.cognitive.WebLearningJournal
import at.heinz.deviceai.diagnostics.ContinuumDiagnostics
import at.heinz.deviceai.learning.ContinuumLearningCore
import at.heinz.deviceai.learning.ContinuumPublicStudyScheduler
import at.heinz.deviceai.learning.LearningExperienceType
import at.heinz.deviceai.learning.LearningProvenance
import at.heinz.deviceai.ui.Argb
import at.heinz.deviceai.ui.ContinuumDashboardMath
import at.heinz.deviceai.ui.ContinuumDashboardState
import at.heinz.deviceai.ui.ContinuumDashboardState.Event
import at.heinz.deviceai.ui.ContinuumDashboardState.Project
import at.heinz.deviceai.ui.ContinuumDashboardState.ProjectState
import at.heinz.deviceai.ui.ContinuumMetricHistory
import java.io.File

/**
 * Reads the real state of Continuum for the start screen. Runs off the UI thread. Every value comes from a subsystem
 * that already exists (learning core, development backlog, permissions, storage, connectivity, process statistics);
 * a source that cannot be read yields `null` ("—") and is reported to the diagnostics center instead of being guessed.
 */
class ContinuumDashboardCollector(private val context: Context, private val diagnostics: ContinuumDiagnostics) {
    /** Live flags of the activity, captured on the UI thread before collecting. */
    data class Live(
        val modelName: String?,
        val modelReady: Boolean,
        val modelLoading: Boolean,
        val internetMode: String,
        val busy: Boolean,
        val listening: Boolean,
        val speaking: Boolean,
        val researching: Boolean,
        val learningActive: Boolean,
        val builderJobRunning: Boolean,
        val practiceEnabled: Boolean,
        val autoStudyEnabled: Boolean,
        val curiosityPending: Boolean,
        val responseTimesMs: List<Long>
    )

    private val files: File = context.filesDir
    private val history = ContinuumMetricHistory(File(files, "diagnostics/quality-history-v1.tsv"))
    private var lastCpuMs = -1L
    private var lastWallMs = -1L
    private val cpuSeries = ArrayDeque<Float>()

    fun collect(live: Live, now: Long = System.currentTimeMillis()): ContinuumDashboardState {
        val learningCore = guard("Lernkern", "LEARNING_STATUS", "Lernkern-Status nicht lesbar",
            "Lernzentrum öffnen; bleibt der Fehler, Diagnose exportieren.") { ContinuumLearningCore(files) }
        val status = learningCore?.let { core ->
            guard("Lernkern", "LEARNING_STATUS", "Lernkern-Status nicht lesbar", "Lernzentrum öffnen; bleibt der Fehler, Diagnose exportieren.") { core.status() }
        }
        val timeline = learningCore?.let { core ->
            guard("Lernkern", "LEARNING_TIMELINE", "Lernverlauf nicht lesbar", "Lernzentrum öffnen und Status prüfen.") { core.experienceTimeline() }
        }.orEmpty()

        // Quality = measured holdout accuracy of the learning core (intent + semantic cases).
        val quality = status?.let {
            ContinuumDashboardMath.qualityPercent(it.intentHoldout.correct + it.semanticHoldout.correct,
                it.intentHoldout.total + it.semanticHoldout.total)
        }
        val qualityHistory = runCatching { history.record(now, quality) }.getOrDefault(emptyList())
        val delta = ContinuumDashboardMath.delta24h(qualityHistory, now, quality)

        val times = timeline.map { it.third }
        val today = if (status != null) ContinuumDashboardMath.countToday(times, now) else null
        val yesterday = ContinuumDashboardMath.countYesterday(times, now)
        val recent = timeline.sortedByDescending { it.third }.take(4).map { (type, provenance, at) ->
            Event(eventTitle(type, provenance), ContinuumDashboardState.ago(now - at), type.ordinal)
        }

        val backlog = guard("Entwicklung", "BACKLOG_READ", "Entwicklungs-Backlog nicht lesbar",
            "Entwicklungszentrum öffnen; das Backlog wird beim nächsten Zugriff geprüft.") {
            DeviceDevelopmentBacklog(File(files, "development")).read().doc
        }
        val tasks = (backlog?.get("tasks") as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
        val doneTimes = tasks.flatMap { task -> (task["history"] as? List<*>).orEmpty().mapNotNull { h ->
            val m = h as? Map<*, *> ?: return@mapNotNull null
            if (m["to"] == "DONE") (m["at"] as? Number)?.toLong() else null
        } }
        val active = tasks.count { it["state"] !in setOf("DONE", "ABORTED", "REJECTED") }
        val running = tasks.count { it["state"] in setOf("IN_PROGRESS", "VERIFYING") }
        val failed = tasks.count { it["state"] == "FAILED" }
        val projects = tasks.sortedByDescending { (it["updatedAt"] as? Number)?.toLong() ?: 0L }.take(4).map { t ->
            val state = t["state"] as? String ?: "?"
            Project((t["title"] as? String ?: t["id"] as? String ?: "Aufgabe").take(40), stateLabel(state), projectState(state),
                ContinuumDashboardState.ago(now - ((t["updatedAt"] as? Number)?.toLong() ?: now)))
        }

        val storage = storage()
        val sources = guard("Quellen", "SOURCES_READ", "Gespeicherte Quellen nicht lesbar", "Gedächtniszentrum öffnen.") {
            WebLearningJournal(context).notes().size
        }
        val publicStudy = runCatching { ContinuumPublicStudyScheduler(context).enabled }.getOrDefault(false)

        val (cpu, memory, online) = systemSamples()
        val openIssues = runCatching { diagnostics.open().size }.getOrNull()

        // "Aktiv" only for a loaded, READY local language model. A readable SWB1 status means the start core can answer
        // from confirmed memories, nothing more.
        val modelState = when {
            live.modelLoading -> "Lädt"
            live.modelReady -> "Aktiv"
            status != null -> "Nur Startkern"
            else -> "Nicht verfügbar"
        }
        return ContinuumDashboardState(
            model = ContinuumDashboardState.ModelStatus(
                name = live.modelName ?: "Continuum-Startkern (SWB1)",
                online = live.modelReady,
                stateLabel = modelState,
                qualityPercent = quality,
                qualityDelta24h = delta,
                latencyMs = ContinuumDashboardMath.median(live.responseTimesMs)?.toInt(),
                // The start core keeps 8 turns on disk but answers with the topic of the last turn only
                // (ContinuumMemoryDialogue: history.lastOrNull()?.topic); a loaded model's window is not reported.
                context = if (live.modelName != null) null else "1 Zug (Thema)",
                mode = if (live.modelName != null) "Lokales Sprachmodell" else "Startkern ohne Sprachmodell",
                lastUpdate = timeline.maxByOrNull { it.third }?.let { ContinuumDashboardState.ago(now - it.third) }
            ),
            learning = ContinuumDashboardState.LearningStatus(
                today = today,
                changePercent = today?.let { ContinuumDashboardMath.changePercent(it, yesterday) },
                series = ContinuumDashboardMath.hourlySeries(times, now),
                recent = recent,
                live = live.learningActive
            ),
            storage = ContinuumDashboardState.StorageStatus(
                usedPercent = storage?.usedPercent,
                segments = storage?.segments.orEmpty(),
                activeSources = sources,
                totalSources = sources,
                syncLabel = if (publicStudy) "Öffentliches Lernen aktiv" else "Keine Synchronisation"
            ),
            development = ContinuumDashboardState.DevelopmentStatus(
                activeProjects = backlog?.let { active }, runningTasks = backlog?.let { running }, errors = backlog?.let { failed },
                series = ContinuumDashboardMath.dailySeries(doneTimes, now), projects = projects
            ),
            system = ContinuumDashboardState.SystemStatus(
                appCpuPercent = cpu, memoryPercent = memory, storagePercent = storage?.usedPercent,
                networkOnline = online, networkLabel = when (online) { true -> "Verbunden"; false -> "Offline"; null -> "Unbekannt" },
                activity = cpuSeries.toList(), openIssues = openIssues
            ),
            core = ContinuumDashboardState.CoreStatus(
                connected = when {
                    online != true -> "Offline"
                    live.internetMode == "OFF" -> "Internet gesperrt"
                    live.internetMode == "ALWAYS_ON" -> "Globale Ressourcen"
                    else -> "Freigabe pro Anfrage"
                },
                thinking = when { live.listening -> "Hört zu"; live.speaking -> "Spricht"; live.busy -> "Denkt nach"; else -> "Bereit" },
                learning = if (live.learningActive) "Lernt gerade" else if (live.practiceEnabled) "Auto-Üben an" else "Bereit",
                analysing = if (live.researching) "Recherchiert" else "Bereit",
                developing = if (live.builderJobRunning || running > 0) "Aktiv" else "Bereit",
                evolving = if (live.autoStudyEnabled || publicStudy) "Automatisch an" else "Aus"
            ),
            pendingNotifications = (openIssues ?: 0) + (if (live.curiosityPending) 1 else 0)
        )
    }

    private data class Storage(val usedPercent: Int?, val segments: List<ContinuumDashboardState.Segment>)

    private fun storage(): Storage? = guard("Speicher", "STORAGE_STATS", "Speicherbelegung nicht lesbar", "Gerätespeicher in den Systemeinstellungen prüfen.") {
        val stat = StatFs(files.path)
        val total = stat.totalBytes
        val used = total - stat.availableBytes
        var own = 0L; var knowledge = 0L; var projects = 0L
        files.listFiles().orEmpty().forEach { f ->
            val size = sizeOf(f)
            val name = f.name.lowercase()
            when {
                "develop" in name || "workspace" in name || "builder" in name -> projects += size
                listOf("archive", "web", "mentor", "source", "curriculum", "knowledge", "wiki").any { it in name } -> knowledge += size
                else -> own += size
            }
        }
        // Device-wide StatFs usage is the denominator. Only filesDir was classified above:
        // "other" therefore contains the whole remaining occupied device filesystem
        // (Android, other apps, caches AND any app paths outside filesDir).
        // It must never be presented as a measured "System" size.
        val appClassified = own + knowledge + projects
        val other = (used - appClassified).coerceAtLeast(0L)
        Storage(ContinuumDashboardMath.usedPercent(total, stat.availableBytes), listOf(
            ContinuumDashboardState.Segment("App: eigene Dateien", own, Argb.rgb(70, 110, 255)),
            ContinuumDashboardState.Segment("App: Wissensquellen", knowledge, Argb.rgb(40, 220, 200)),
            ContinuumDashboardState.Segment("App: Projektdateien", projects, Argb.rgb(190, 90, 255)),
            ContinuumDashboardState.Segment("Übrige Gerätebelegung", other, Argb.rgb(60, 190, 255))))
    }

    private fun sizeOf(file: File, depth: Int = 0): Long =
        if (file.isFile) file.length() else if (depth > 6) 0L else file.listFiles().orEmpty().sumOf { sizeOf(it, depth + 1) }

    /** App CPU share since the previous sample, device RAM in use, validated internet connection. */
    private fun systemSamples(): Triple<Int?, Int?, Boolean?> {
        val cpuMs = Process.getElapsedCpuTime()
        val wallMs = SystemClock.elapsedRealtime()
        val cpu = if (lastCpuMs >= 0 && wallMs > lastWallMs) {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            (((cpuMs - lastCpuMs) * 100.0) / ((wallMs - lastWallMs) * cores)).toInt().coerceIn(0, 100)
        } else null
        lastCpuMs = cpuMs; lastWallMs = wallMs
        if (cpu != null) { cpuSeries.addLast(cpu.toFloat()); while (cpuSeries.size > 24) cpuSeries.removeFirst() }
        val memory = runCatching {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            ContinuumDashboardMath.usedPercent(info.totalMem, info.availMem)
        }.getOrNull()
        val online = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }.getOrNull()
        return Triple(cpu, memory, online)
    }

    /** Runs [read]; failures are recorded once (deduplicated) and resolved automatically on the next success. */
    private fun <T> guard(component: String, code: String, title: String, recommendation: String, read: () -> T): T? = try {
        read().also { runCatching { diagnostics.resolve(component, code, "Wieder lesbar") } }
    } catch (e: Throwable) {
        runCatching {
            diagnostics.report(component, code, ContinuumDiagnostics.Severity.WARNING, title,
                e.javaClass.simpleName + ": " + (e.message ?: "ohne Meldung"), recommendation)
        }
        null
    }

    private fun eventTitle(type: LearningExperienceType, provenance: LearningProvenance) = when (type) {
        LearningExperienceType.INTENT_LABEL -> "Absicht gelernt"
        LearningExperienceType.SOURCE_TEXT -> if (provenance == LearningProvenance.MENTOR_WIKIPEDIA) "Quelle beobachtet (Wikipedia)" else "Quelle beobachtet"
        LearningExperienceType.SEMANTIC_PAIR -> "Bedeutungspaar gelernt"
        LearningExperienceType.TRAINING_AUDIT -> "Training geprüft"
        LearningExperienceType.USER_FACT -> "Erinnerung bestätigt"
        LearningExperienceType.COMMAND_MAPPING -> "Befehl zugeordnet"
        LearningExperienceType.USER_FEEDBACK -> "Rückmeldung gelernt"
    }

    private fun stateLabel(state: String) = when (state) {
        "PROPOSED" -> "Vorgeschlagen"; "READY" -> "Bereit"; "IN_PROGRESS" -> "In Arbeit"; "VERIFYING" -> "Tests laufen"
        "BLOCKED" -> "Blockiert"; "DONE" -> "Erledigt und geprüft"; "FAILED" -> "Fehlgeschlagen"; "ABORTED" -> "Abgebrochen"
        "REJECTED" -> "Abgelehnt"; else -> state
    }

    private fun projectState(state: String) = when (state) {
        "DONE" -> ProjectState.OK; "VERIFYING" -> ProjectState.RUNNING; "IN_PROGRESS" -> ProjectState.WORKING
        "READY", "PROPOSED" -> ProjectState.READY; "FAILED", "BLOCKED" -> ProjectState.FAILED; else -> ProjectState.IDLE
    }
}
