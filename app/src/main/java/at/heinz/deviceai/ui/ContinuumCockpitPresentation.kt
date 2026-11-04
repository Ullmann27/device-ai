package at.heinz.deviceai.ui

import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.chief.TurnPhase
import at.heinz.deviceai.chief.WorkerReport
import at.heinz.deviceai.chief.WorkerStatus
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState

/**
 * Live inputs of the landscape cockpit that the 5-s dashboard collector cannot know (they live in the Activity).
 * `null` = source not available / not started; it is drawn as [ContinuumCockpitPresentation.NOT_AVAILABLE].
 */
data class CockpitStatus(
    /** Phase of the current (or last) owner turn; null = no turn in this session yet. */
    val turnPhase: TurnPhase? = null,
    /** Current ChiefWorkerPool assignments; null = the pool was never started in this session. */
    val workers: List<ChiefWorkerPool.WorkerSnapshot>? = null,
    val workerCapacity: Int? = null,
    val lastReport: WorkerReport? = null,
    /** Real step list of the latest turn/worker (CockpitAnimationTriggers). */
    val steps: List<ProcessStep> = emptyList(),
    /** Last owner / Jarvis lines of the visible chat (already shown there). */
    val dialog: List<DialogLine> = emptyList()
) {
    data class DialogLine(val fromOwner: Boolean, val text: String)
}

/**
 * Pure mapping from real state to the strings and fractions the cockpit draws. Every value is either measured
 * or "nicht verfügbar"; there is no sample data and no estimated progress.
 */
object ContinuumCockpitPresentation {
    const val NOT_AVAILABLE = "nicht verfügbar"

    data class Row(val label: String, val value: String, val fraction: Float?, val state: Tone = Tone.NEUTRAL)
    enum class Tone { NEUTRAL, ACTIVE, OK, WARN, ERROR }

    /** Plausible battery temperature from Android's tenths of °C, else null. */
    fun batteryTemperature(tenths: Int): Float? =
        if (tenths == Int.MIN_VALUE || tenths < -200 || tenths > 800) null else tenths / 10f

    fun phaseLabel(phase: TurnPhase?): String = when (phase) {
        null -> "Noch kein Auftrag"
        TurnPhase.IDLE -> "Bereit"
        TurnPhase.ACKNOWLEDGED -> "Auftrag angenommen"
        TurnPhase.WORKING -> "Arbeitet"
        TurnPhase.ENDED -> "Ergebnis geliefert"
    }

    /** The four real TurnLifecycle phases as a step rail; index of the reached phase, -1 = none. */
    val phaseRail = listOf("Annehmen", "Arbeiten", "Ergebnis")
    fun phaseIndex(phase: TurnPhase?): Int = when (phase) {
        null, TurnPhase.IDLE -> -1
        TurnPhase.ACKNOWLEDGED -> 0
        TurnPhase.WORKING -> 1
        TurnPhase.ENDED -> 2
    }

    fun brainRows(s: ContinuumDashboardState): List<Row> = listOf(
        Row("Modell", s.model.name.ifBlank { NOT_AVAILABLE }, null, if (s.model.online) Tone.OK else Tone.NEUTRAL),
        Row("Zustand", s.model.stateLabel, null, if (s.model.online) Tone.OK else Tone.WARN),
        Row("Antwortzeit", s.model.latencyMs?.let { "$it ms" } ?: NOT_AVAILABLE, null),
        Row("Kontext", s.model.context ?: NOT_AVAILABLE, null)
    )

    /** Memory ledger (PR #371): total, new today, last activity; missing reader → "nicht verfügbar". */
    fun memoryRows(s: ContinuumDashboardState, nowMillis: Long): List<Row> {
        val m = s.memory ?: return listOf(Row("Gedächtnis", NOT_AVAILABLE, null))
        val last = m.lastActivity?.let { ContinuumDashboardState.ago(nowMillis - it.toEpochMilli()) } ?: "noch keine"
        return listOf(
            Row("Gedächtnis", "${m.totalEntries} Einträge · +${m.newToday} heute", null, Tone.OK),
            Row("Bestätigt / offen", "${m.confirmed} / ${m.unconfirmed + m.hypothetical}", null),
            Row("Letzte Aktivität", last, null)
        )
    }

    private fun percentRow(label: String, value: Int?) =
        Row(label, value?.let { "$it %" } ?: NOT_AVAILABLE, value?.let { it.coerceIn(0, 100) / 100f })

    fun systemRows(s: ContinuumDashboardState): List<Row> = listOf(
        percentRow("CPU (App)", s.system.appCpuPercent),
        percentRow("RAM", s.system.memoryPercent),
        percentRow("Speicher", s.system.storagePercent),
        percentRow("Akku", s.system.batteryPercent),
        Row("Akku-Temp.", s.system.temperatureC?.let { String.format(java.util.Locale.GERMANY, "%.1f °C", it) } ?: NOT_AVAILABLE,
            s.system.temperatureC?.let { (it / 50f).coerceIn(0f, 1f) },
            when { s.system.temperatureC == null -> Tone.NEUTRAL; s.system.temperatureC >= 42f -> Tone.WARN; else -> Tone.OK })
    )

    fun agentsTitle(c: CockpitStatus): String {
        val workers = c.workers ?: return "Aktive Agenten"
        val capacity = c.workerCapacity?.let { " / $it" } ?: ""
        return "Aktive Agenten (${workers.count { it.running }}$capacity)"
    }

    /**
     * One row per real worker assignment. The bar is the share of the worker's own deadline already used
     * ("Frist"), labelled as such – ChiefWorkerPool reports no work progress and none is invented.
     */
    fun agentRows(c: CockpitStatus): List<Row> {
        val workers = c.workers
        val rows = workers.orEmpty().take(5).map { w ->
            val used = if (w.running && w.deadlineMillis > 0) (w.elapsedMillis.toFloat() / w.deadlineMillis).coerceIn(0f, 1f) else null
            val status = when {
                w.cancelling -> "bricht ab"
                !w.running -> "wartet"
                else -> "läuft · " + seconds(w.elapsedMillis) + " / Frist " + seconds(w.deadlineMillis)
            }
            Row(CockpitAnimationLabels.goal(w.goal), status, used, if (w.cancelling) Tone.WARN else if (w.running) Tone.ACTIVE else Tone.NEUTRAL)
        }
        if (rows.isNotEmpty()) return rows
        val last = c.lastReport
        return listOf(when {
            workers == null -> Row("Agenten-Pool", "noch nicht gestartet", null)
            last != null -> Row("Zuletzt: " + last.id.take(24), reportLabel(last.status) + " · " + seconds(last.durationMillis), null,
                if (last.status == WorkerStatus.DONE) Tone.OK else Tone.ERROR)
            else -> Row("Agenten-Pool", "kein laufender Auftrag", null)
        })
    }

    fun reportLabel(status: WorkerStatus): String = when (status) {
        WorkerStatus.DONE -> "erledigt"
        WorkerStatus.BLOCKED -> "blockiert"
        WorkerStatus.FAILED -> "fehlgeschlagen"
        WorkerStatus.TIMED_OUT -> "Frist überschritten"
        WorkerStatus.CANCELLED -> "abgebrochen"
    }

    fun processRows(c: CockpitStatus): List<Row> {
        if (c.steps.isEmpty()) return listOf(Row("Keine laufenden Prozesse", "", null))
        return c.steps.takeLast(4).map { step ->
            Row(step.label, stepLabel(step.state), null, when (step.state) {
                StepState.ACTIVE -> Tone.ACTIVE
                StepState.DONE -> Tone.OK
                StepState.WAITING -> Tone.WARN
                StepState.BLOCKED, StepState.FAILED -> Tone.ERROR
            })
        }
    }

    fun stepLabel(state: StepState): String = when (state) {
        StepState.ACTIVE -> "läuft"
        StepState.WAITING -> "wartet auf Freigabe"
        StepState.DONE -> "fertig"
        StepState.BLOCKED -> "blockiert"
        StepState.FAILED -> "fehlgeschlagen"
    }

    fun dialogRows(c: CockpitStatus): List<Row> {
        if (c.dialog.isEmpty()) return listOf(Row("Noch kein Gespräch", "", null))
        return c.dialog.takeLast(2).map { line ->
            Row(if (line.fromOwner) "DU" else "JARVIS", line.text.filterNot { it.isISOControl() }.trim().take(140), null,
                if (line.fromOwner) Tone.NEUTRAL else Tone.ACTIVE)
        }
    }

    private fun seconds(ms: Long): String = if (ms < 1_000) "<1 s" else "${ms / 1_000} s"
}

/** Caches an expensive read for [intervalMs]; a failed read yields null (shown as "nicht verfügbar"). Thread-safe. */
class ThrottledRead<T : Any>(private val intervalMs: Long, private val read: () -> T) {
    private var value: T? = null
    private var readAt = Long.MIN_VALUE
    @Synchronized fun get(nowMs: Long): T? {
        if (readAt == Long.MIN_VALUE || nowMs - readAt >= intervalMs) {
            value = runCatching(read).getOrNull()
            readAt = nowMs
        }
        return value
    }
}

internal object CockpitAnimationLabels {
    fun goal(goal: String): String = goal.filterNot { it.isISOControl() }.trim().take(40).ifEmpty { "Auftrag" }
}
