package at.heinz.deviceai.ui

import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.CockpitWorkReducer
import at.heinz.deviceai.cognitive.ComputerGoal
import at.heinz.deviceai.cognitive.OwnerComputerIntent
import at.heinz.deviceai.cognitive.OwnerComputerWorkflow
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Row
import at.heinz.deviceai.ui.ContinuumCockpitPresentation.Tone

/** Existing workflow, read-only presentation. */
class ComputerPanelState private constructor(
    val taskId: String,
    val sessionId: Long,
    val appLabel: String,
    val goalLabel: String,
    val snapshot: CockpitWorkReducer.Snapshot,
    val resultMessage: String?
) {
    val verified: Boolean get() = snapshot.stage == CockpitWorkEvent.Stage.COMPLETED && resultMessage != null

    fun planningRows(): List<Row> = listOf(
        Row("App", appLabel, null),
        Row("Ziel", goalLabel, null),
        Row("Ablauf", stageLabel(), null, stageTone())
    ) + listOfNotNull(snapshot.action?.let { action ->
        Row("Aktion", when (action) {
            CockpitWorkEvent.Action.OPEN_APP -> "App öffnen"
            CockpitWorkEvent.Action.CLICK -> "Element anklicken"
            CockpitWorkEvent.Action.SET_TEXT -> "Text eintragen"
            CockpitWorkEvent.Action.SCROLL -> "Ansicht scrollen"
        }, null)
    })

    fun resultRows(): List<Row> = buildList {
        add(Row("Prüfung", when {
            verified -> "Auftragsziel bestätigt"
            snapshot.stage == CockpitWorkEvent.Stage.VERIFIED -> "Letzter Schritt bestätigt"
            snapshot.stage == CockpitWorkEvent.Stage.COMPLETED -> "Abschluss wird geprüft"
            snapshot.stage == CockpitWorkEvent.Stage.STOPPED -> "Auftrag abgebrochen"
            else -> "Ziel noch nicht bestätigt"
        }, null, if (verified) Tone.OK else stageTone()))
        add(Row("Quelle", if (snapshot.observationRevision > 0L || verified)
            "Beobachtete Android-Ansicht" else "$appLabel · gebundene Ansicht", null))
        resultMessage?.let { add(Row("Rückmeldung", it, null)) }
    }

    private fun stageLabel(): String = when (snapshot.stage) {
        CockpitWorkEvent.Stage.ACCEPTED -> "Auftrag angenommen"
        CockpitWorkEvent.Stage.FOCUS -> "Sichtbares Ziel gewählt"
        CockpitWorkEvent.Stage.DISPATCHED -> "Eingabe ausgelöst"
        CockpitWorkEvent.Stage.OBSERVED -> "Wirkung wird geprüft"
        CockpitWorkEvent.Stage.VERIFIED -> "Schritt geprüft"
        CockpitWorkEvent.Stage.ASK_OWNER -> "Rückfrage an Besitzer"
        CockpitWorkEvent.Stage.PAUSED -> "Arbeit pausiert"
        CockpitWorkEvent.Stage.STOPPED -> "Auftrag abgebrochen"
        CockpitWorkEvent.Stage.ERROR -> "Auftrag angehalten"
        CockpitWorkEvent.Stage.COMPLETED -> if (verified) "Auftrag geprüft" else "Abschluss wird geprüft"
        null -> "Noch nicht gestartet"
    }

    private fun stageTone(): Tone = when (snapshot.stage) {
        CockpitWorkEvent.Stage.VERIFIED -> Tone.OK
        CockpitWorkEvent.Stage.COMPLETED -> if (verified) Tone.OK else Tone.NEUTRAL
        CockpitWorkEvent.Stage.ASK_OWNER, CockpitWorkEvent.Stage.PAUSED -> Tone.WARN
        CockpitWorkEvent.Stage.ERROR -> Tone.ERROR
        CockpitWorkEvent.Stage.STOPPED, null -> Tone.NEUTRAL
        else -> Tone.ACTIVE
    }

    companion object {
        internal fun from(taskId: String, sessionId: Long, intent: OwnerComputerIntent,
                          snapshot: CockpitWorkReducer.Snapshot,
                          result: OwnerComputerWorkflow.Result? = null): ComputerPanelState? {
            if (taskId != snapshot.taskId || sessionId != snapshot.sessionId ||
                sessionId <= 0L || snapshot.stage == null) return null
            if (result != null && snapshot.stage != when (result.status) {
                    OwnerComputerWorkflow.Status.VERIFIED -> CockpitWorkEvent.Stage.COMPLETED
                    OwnerComputerWorkflow.Status.ASK_OWNER -> CockpitWorkEvent.Stage.ASK_OWNER
                    OwnerComputerWorkflow.Status.PAUSED -> CockpitWorkEvent.Stage.PAUSED
                    OwnerComputerWorkflow.Status.CANCELLED -> CockpitWorkEvent.Stage.STOPPED
                    OwnerComputerWorkflow.Status.FAILED -> CockpitWorkEvent.Stage.ERROR
                }) return null
            val goal = when (intent.goal) {
                ComputerGoal.OPEN -> "App öffnen"
                ComputerGoal.SEARCH -> "Suche prüfen"
                ComputerGoal.OPEN_DOWNLOADS -> "Download-Ordner öffnen"
                ComputerGoal.DRAFT_MESSAGE -> "Nachrichtenentwurf prüfen"
                ComputerGoal.SEND_MESSAGE -> "Entwurf prüfen · Versand offen"
            }
            return ComputerPanelState(taskId, sessionId, intent.app.discoveryQuery, goal, snapshot,
                result?.message?.filterNot(Char::isISOControl)?.trim()?.take(600)?.takeIf(String::isNotEmpty))
        }
    }
}
