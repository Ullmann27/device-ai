package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.AvatarRuntimeSignals
import at.heinz.deviceai.cockpit.CockpitAction
import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.CockpitAnimationRenderer
import at.heinz.deviceai.cockpit.MEMORY_TURN
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The single source of the Command03 page's presence and quality tokens.
 *
 * The productive HTML stage previously saw only three booleans (listening/speaking/busy), so research,
 * learning, a pending approval and a blocked turn were indistinguishable from "working", and no thermal
 * information reached it at all. These tokens are derived from exactly the runtime flags that already
 * drive the native hologram and the avatar director; nothing here measures or invents a state.
 */
object Command03StageSignals {
    enum class Profile(val label: String) {
        AUTO("Automatisch · passt sich Wärme und Energiesparen an"),
        FULL("Volle Qualität · reduziert erst bei kritischer Wärme"),
        SAVER("Sparsam · reduzierte Nebeneffekte");

        companion object {
            fun of(stored: String?): Profile = entries.firstOrNull { it.name == stored } ?: AUTO
        }
    }

    /** Same precedence as [at.heinz.deviceai.avatar.AvatarDirector.onRuntime]; the names are AvatarState names. */
    fun presence(signals: AvatarRuntimeSignals): String = when {
        signals.awaitingApproval -> "WAITING_FOR_APPROVAL"
        signals.listening -> "LISTENING"
        signals.speaking -> "SPEAKING"
        signals.blocked -> "BLOCKED"
        signals.researching -> "RESEARCHING"
        signals.busy && signals.learning -> "LEARNING"
        signals.busy -> "THINKING"
        else -> "IDLE"
    }

    /**
     * Quality tier for secondary page effects. [thermalStatus] is PowerManager's status (0 none … 6 shutdown)
     * or null when the platform does not report one; an unknown status never lowers quality by itself.
     * Only expensive secondary effects follow this tier; content, controls, avatar and orb stay visible.
     *
     * Owner finding (Fold7, Code64): the device heated up within three or four spoken turns and the thermal guard
     * stopped the answer. AUTO used to lower the stage only at MODERATE and reduce it at SEVERE – the status at
     * which [at.heinz.deviceai.localmodel.LocalModelThermalGuard] already stops the model, i.e. too late to help.
     * AUTO now steps down one status earlier: BALANCED at LIGHT, REDUCED from MODERATE. The page also limits its
     * frame rate per tier (native-bridge.js, frame governor).
     */
    fun quality(thermalStatus: Int?, powerSave: Boolean?, profile: Profile): String {
        val thermal = thermalStatus?.takeIf { it in 0..6 }
        return when (profile) {
            Profile.SAVER -> "REDUCED"
            Profile.FULL -> if (thermal != null && thermal >= THERMAL_CRITICAL) "REDUCED" else "FULL"
            Profile.AUTO -> when {
                powerSave == true || (thermal != null && thermal >= THERMAL_MODERATE) -> "REDUCED"
                thermal != null && thermal >= THERMAL_LIGHT -> "BALANCED"
                else -> "FULL"
            }
        }
    }

    /**
     * Display refresh rate Continuum asks Android for while it is in front: 60 Hz in every profile (the Fold7's
     * own rate is 120 Hz). CSS and compositor animations run at the display rate, so this halves their work.
     * Until Code67 "full quality" kept 120 Hz; the owner then reported that the device overheated as soon as an
     * order started with the effects on. A request only – Android may keep another rate.
     */
    @Suppress("UNUSED_PARAMETER")
    fun preferredRefreshRate(profile: Profile): Float = 60f

    private const val THERMAL_LIGHT = 1
    private const val THERMAL_MODERATE = 2
    private const val THERMAL_CRITICAL = 4
}

/**
 * Projects real cockpit events into the one bounded "activity" record of the Command03 snapshot.
 *
 * Until now file search, memory recall, document intake and the step timeline were emitted only to
 * native renderers that are hidden while the HTML stage is shown. This renderer keeps the latest real
 * fact so the page can show which work is running, on what, and which step follows. It never
 * synthesizes progress: no event means no activity, and a finished turn is reported as settled.
 */
class Command03ActivityProjection : CockpitAnimationRenderer {
    private var revision = 0L
    private var turnId: String? = null
    private var kind: String? = null
    private var title = ""
    private var detail = ""
    private var active = false
    private var steps: List<ProcessStep> = emptyList()

    @Synchronized override fun render(event: CockpitAnimationEvent) {
        when (event) {
            is CockpitAnimationEvent.ActionStarted ->
                set(event.turnId, event.action.name, actionTitle(event.action), event.detail, active = true)
            is CockpitAnimationEvent.ProcessTimeline -> {
                val window = stepWindow(event.steps)
                if (turnId != event.turnId) set(event.turnId, CockpitAction.EXECUTE.name, "Arbeitsschritte", "",
                    active = event.steps.any { it.state == StepState.ACTIVE || it.state == StepState.WAITING })
                if (window != steps) { steps = window; revision++ }
            }
            is CockpitAnimationEvent.DocumentQuestion -> set(event.turnId, "DOCUMENT", "Dokument wird gelesen",
                event.documentName + (event.pageCount?.let { pages -> " · $pages Seite" + if (pages == 1) "" else "n" } ?: ""),
                active = true)
            is CockpitAnimationEvent.DocumentLearned -> set(event.turnId, "DOCUMENT", "Dokument gelernt",
                countLabel(event.entriesAdded, "Eintrag", "Einträge"), active = false)
            is CockpitAnimationEvent.MemoryStored -> set(event.turnId, "MEMORY", "Erinnerung gespeichert",
                countLabel(event.count, "Eintrag", "Einträge"), active = false)
            is CockpitAnimationEvent.RecallHits -> set(event.turnId, "MEMORY", "Gedächtnis durchsucht",
                if (event.count <= 0) "Keine Treffer" else countLabel(event.count, "Treffer", "Treffer") +
                    (event.topScore?.takeIf { it.isFinite() }?.let {
                        " · beste Übereinstimmung " + String.format(Locale.GERMAN, "%.2f", it)
                    } ?: ""),
                active = false)
            is CockpitAnimationEvent.CalculationShown -> set(event.turnId, "CALCULATION", "Berechnung",
                event.expression + " = " + number(event.result) + (event.unit?.let { " $it" } ?: ""), active = true)
            is CockpitAnimationEvent.TurnEnded -> if (event.turnId == turnId && active) { active = false; revision++ }
            else -> Unit
        }
    }

    /** Null until a real event arrived. */
    @Synchronized fun snapshot(): JSONObject? {
        val currentKind = kind ?: return null
        val list = JSONArray()
        steps.forEach { step ->
            list.put(JSONObject().put("label", bounded(step.label, MAX_STEP_LABEL)).put("state", step.state.name))
        }
        return JSONObject().put("revision", revision).put("kind", currentKind)
            .put("title", bounded(title, MAX_TITLE)).put("detail", bounded(detail, MAX_DETAIL))
            .put("active", active).put("steps", list)
    }

    /**
     * A notification the owner allowed to be announced. Title and detail are exactly what was spoken
     * (already bounded and stripped of codes and links by the announcement policy); a running turn keeps
     * its steps and its running state.
     */
    @Synchronized fun notification(title: String, detail: String) {
        kind = "NOTIFICATION"
        this.title = title
        this.detail = detail.replace(Regex("\\s+"), " ").trim()
        revision++
    }

    @Synchronized fun clear() {
        if (kind == null) return
        turnId = null; kind = null; title = ""; detail = ""; active = false; steps = emptyList(); revision++
    }

    private fun set(turn: String, newKind: String, newTitle: String, newDetail: String, active: Boolean) {
        // Memory events are not bound to a turn: they keep the running turn's steps and its running state.
        if (turn != MEMORY_TURN) {
            if (turn != turnId) steps = emptyList()
            turnId = turn
            this.active = active
        }
        kind = newKind
        title = newTitle
        detail = newDetail.replace(Regex("\\s+"), " ").trim()
        revision++
    }

    /** At most [MAX_STEPS] steps around the running one, so the next step stays visible. */
    private fun stepWindow(all: List<ProcessStep>): List<ProcessStep> {
        if (all.size <= MAX_STEPS) return all.toList()
        val running = all.indexOfFirst { it.state == StepState.ACTIVE }.let { if (it < 0) all.lastIndex else it }
        val start = (running - 2).coerceIn(0, all.size - MAX_STEPS)
        return all.subList(start, start + MAX_STEPS).toList()
    }

    private fun actionTitle(action: CockpitAction): String = when (action) {
        CockpitAction.THINK -> "Denkt nach"
        CockpitAction.RESEARCH -> "Recherchiert"
        CockpitAction.LEARN -> "Lernt"
        CockpitAction.DEVELOP -> "Entwickelt"
        CockpitAction.EXECUTE -> "Führt aus"
        CockpitAction.VERIFY -> "Prüft das Ergebnis"
        CockpitAction.TOOL -> "Werkzeug läuft"
        CockpitAction.WORKER -> "Helfer arbeitet"
    }

    private fun countLabel(count: Int, one: String, many: String): String =
        "${count.coerceAtLeast(0)} " + if (count == 1) one else many

    private fun number(value: Double): String =
        if (!value.isFinite()) "nicht darstellbar"
        else if (value == Math.rint(value) && Math.abs(value) < 1e15) String.format(Locale.GERMAN, "%.0f", value)
        else String.format(Locale.GERMAN, "%.4f", value).trimEnd('0').trimEnd(',')

    private fun bounded(text: String, max: Int): String {
        val clean = text.filter { !it.isISOControl() }
        if (clean.length <= max) return clean
        var end = max - 1
        if (end > 0 && clean[end - 1].isHighSurrogate()) end--
        return clean.substring(0, end) + "…"
    }

    companion object {
        const val MAX_STEPS = 6
        const val MAX_TITLE = 80
        const val MAX_DETAIL = 160
        const val MAX_STEP_LABEL = 80
    }
}
