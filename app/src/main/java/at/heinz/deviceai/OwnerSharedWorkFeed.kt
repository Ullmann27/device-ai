package at.heinz.deviceai

import at.heinz.deviceai.briefing.ContinuumBriefingDirector
import at.heinz.deviceai.cognitive.CockpitWorkJournal
import at.heinz.deviceai.cognitive.OwnerComputerWorkflow
import at.heinz.deviceai.cognitive.OwnerSharedWorkRecord
import at.heinz.deviceai.cognitive.SharedWorkAction
import at.heinz.deviceai.cognitive.SharedWorkEntry
import at.heinz.deviceai.cognitive.SharedWorkKind
import at.heinz.deviceai.cognitive.SharedWorkState

/**
 * What feeds the shared-work record ([OwnerSharedWorkRecord]): the typed reports the app already has – a lane's
 * [OwnerLaneOutcome], a mission's [OwnerMissionSettled], an episode of the cockpit work journal, the end of a research
 * run, a briefing, a coding-agent run, a computer order, a model answer. One entry per piece of work, with the device
 * clock's time and the lane as provenance.
 *
 * Nothing here reads a reply's wording and nothing comes from a model: a title is a file name as the lane itself
 * shows it to the owner, the owner's own search words, or a count. An order to take the work up again ([SharedWorkEntry
 * .resume]) is set only where repeating it reads and nothing else – a sent message never gets one. Free of Android.
 */
internal class OwnerSharedWorkFeed(private val record: OwnerSharedWorkRecord, private val now: () -> Long = System::currentTimeMillis) {
    private val agentRuns = HashMap<String, Pair<String, Long>>()

    private fun note(entry: SharedWorkEntry) { runCatching { record.note(entry) } }

    /**
     * A lane settled an order. Only a real result is noted: a question, a refusal or a stop carries no subject, and a
     * step of a running mission ([partOfMission]) is part of that mission's own entry.
     */
    fun lane(lane: OwnerMissionLane, outcome: OwnerLaneOutcome, partOfMission: Boolean) {
        if (partOfMission) return
        val result = (outcome as? OwnerLaneOutcome.Done)?.result ?: return
        val at = now()
        fun entry(kind: SharedWorkKind, action: SharedWorkAction, title: String, resume: String? = null) =
            SharedWorkEntry("lane:${lane.name}:$at", kind, action, title, SharedWorkState.DONE, at, at, laneLabel(lane), resume)
        when (result) {
            is OwnerLaneResult.DocumentOpened -> note(entry(SharedWorkKind.DOCUMENT, SharedWorkAction.OPENED, result.name,
                resume = openOrder(result.query ?: result.name)))
            is OwnerLaneResult.FileSaved -> note(entry(SharedWorkKind.DOCUMENT, SharedWorkAction.SAVED, result.file.displayName))
            is OwnerLaneResult.Briefing -> note(entry(SharedWorkKind.RESEARCH, SharedWorkAction.SUMMARISED, "Nachrichten",
                resume = ContinuumBriefingDirector.ORDER_NEWS))
            is OwnerLaneResult.Storage -> note(entry(SharedWorkKind.STORAGE,
                if (result.removedFiles > 0) SharedWorkAction.CLEANED else SharedWorkAction.CHECKED, ""))
            is OwnerLaneResult.Delivered -> note(entry(SharedWorkKind.SEND,
                if (result.state == OwnerLaneResult.Delivered.State.SENT) SharedWorkAction.SENT else SharedWorkAction.PREPARED,
                listOf(result.to, result.channel).filter { it.isNotBlank() }.joinToString(" über ")))
            // A search, a found file or a file handed to the viewer is a step on the way, not a piece of work.
            is OwnerLaneResult.FilesFound, is OwnerLaneResult.FileFound, is OwnerLaneResult.FileOpened -> Unit
        }
    }

    /** A mission paused or ended: one entry for the whole sequence, updated as it goes on. */
    fun mission(settled: OwnerMissionSettled) {
        val state = when (settled.how) {
            OwnerMissionSettled.How.COMPLETED -> SharedWorkState.DONE
            OwnerMissionSettled.How.PAUSED -> SharedWorkState.PAUSED
            OwnerMissionSettled.How.FAILED -> SharedWorkState.FAILED
            OwnerMissionSettled.How.STOPPED -> SharedWorkState.STOPPED
        }
        note(SharedWorkEntry("mission:" + settled.id, SharedWorkKind.MISSION, SharedWorkAction.SEQUENCE, "${settled.steps} Schritte", state,
            settled.startedAt, now(), "Auftragsfolge", resume = settled.request.takeIf { settled.resumable && state != SharedWorkState.DONE },
            pausedAt = "Schritt ${settled.step} von ${settled.steps}".takeIf { state != SharedWorkState.DONE }))
    }

    /**
     * The cockpit work journal as it stands after a write: its document episodes are mirrored, state for state, with
     * the journal's own notion of what is still open. Only the newest open episode can be taken up again – it is the
     * one the journal's „beim letzten offenen Punkt weitermachen“ continues.
     */
    fun journal(episodes: List<CockpitWorkJournal.Episode>) {
        val at = now()
        val documents = episodes.filter { it.kind == CockpitWorkJournal.Kind.DOCUMENT }.sortedBy { it.startedAt }.takeLast(MAX_JOURNAL)
        val open = documents.lastOrNull { CockpitWorkJournal.nextStep(it, at) != null }
        runCatching { record.noteAll(documents.mapNotNull { e ->
            val next = CockpitWorkJournal.nextStep(e, at)
            val state = when (e.state) {
                CockpitWorkJournal.State.READ, CockpitWorkJournal.State.SAVED -> SharedWorkState.DONE
                CockpitWorkJournal.State.DRAFTED, CockpitWorkJournal.State.INTERRUPTED -> SharedWorkState.PAUSED
                // Still being written: not settled yet, unless the run was cut off.
                CockpitWorkJournal.State.DRAFTING -> if (next != null) SharedWorkState.PAUSED else return@mapNotNull null
                CockpitWorkJournal.State.ABORTED -> SharedWorkState.STOPPED
                CockpitWorkJournal.State.NO_MODEL, CockpitWorkJournal.State.FAILED -> SharedWorkState.FAILED
            }
            SharedWorkEntry("journal:" + e.id, SharedWorkKind.DOCUMENT,
                if (e.state == CockpitWorkJournal.State.READ) SharedWorkAction.OPENED else SharedWorkAction.DRAFTED,
                e.source.orEmpty(), state, e.startedAt, e.updatedAt, JOURNAL,
                resume = JOURNAL_CONTINUE_ORDER.takeIf { e.id == open?.id },
                pausedAt = when (e.state) {
                    CockpitWorkJournal.State.DRAFTED -> "der Entwurf ist noch nicht gespeichert"
                    CockpitWorkJournal.State.INTERRUPTED, CockpitWorkJournal.State.DRAFTING -> "der Entwurf wurde unterbrochen"
                    else -> null
                })
        }) }
    }

    /** A web research run ended with a dossier. [question]: the owner's own search words. */
    fun research(question: String, sources: Int, startedAt: Long) {
        val at = now()
        note(SharedWorkEntry("research:$at", SharedWorkKind.RESEARCH, SharedWorkAction.RESEARCHED, question,
            if (sources > 0) SharedWorkState.DONE else SharedWorkState.FAILED, startedAt.takeIf { it in 1..at } ?: at, at, "Recherche"))
    }

    /** The daily briefing was given. */
    fun briefing() {
        val at = now()
        note(SharedWorkEntry("briefing:$at", SharedWorkKind.BRIEFING, SharedWorkAction.BRIEFED, "", SharedWorkState.DONE, at, at, "Briefing",
            resume = BRIEFING_ORDER))
    }

    /** A cloud coding-agent run started ([title]: the run's own title as the work window shows it). */
    fun codingAgentStarted(runId: String, title: String) { agentRuns[runId] = title to now() }

    /** … and ended. [delivered]: the agent delivered a result; otherwise it was stopped or failed. */
    fun codingAgentEnded(runId: String, delivered: Boolean) {
        val (title, startedAt) = agentRuns.remove(runId) ?: ("" to now())
        note(SharedWorkEntry("agent:$runId", SharedWorkKind.CODING_AGENT, SharedWorkAction.CODED, title,
            if (delivered) SharedWorkState.DONE else SharedWorkState.FAILED, startedAt, now(), "Coding-Agenten"))
    }

    /** A computer order reported its typed status. [app]: the app's label as the approval dialog showed it. */
    fun computer(app: String, status: OwnerComputerWorkflow.Status, startedAt: Long) {
        val state = when (status) {
            OwnerComputerWorkflow.Status.VERIFIED -> SharedWorkState.DONE
            OwnerComputerWorkflow.Status.PAUSED -> SharedWorkState.PAUSED
            OwnerComputerWorkflow.Status.CANCELLED -> SharedWorkState.STOPPED
            OwnerComputerWorkflow.Status.FAILED -> SharedWorkState.FAILED
            // A question back is not an end.
            OwnerComputerWorkflow.Status.ASK_OWNER -> return
        }
        val at = now()
        note(SharedWorkEntry("computer:$at", SharedWorkKind.COMPUTER, SharedWorkAction.OPERATED, app, state,
            startedAt.takeIf { it in 1..at } ?: at, at, "Computer-Auftrag"))
    }

    /**
     * A model answered the owner in the chat [sessionId]: one entry per chat, extended with every further answer.
     * No topic is noted – the app has none that is not a guess.
     */
    fun conversation(sessionId: String) {
        val at = now()
        note(SharedWorkEntry("talk:" + sessionId.take(60), SharedWorkKind.CONVERSATION, SharedWorkAction.TALKED, "", SharedWorkState.DONE,
            at, at, "Gespräch"))
    }

    private fun laneLabel(lane: OwnerMissionLane): String = when (lane) {
        OwnerMissionLane.DESK -> "Dokumentenablage"
        OwnerMissionLane.WORK -> "Arbeitswerkzeuge"
        OwnerMissionLane.SEND -> "Sende-Lenkung"
        OwnerMissionLane.CHAIN -> "Aufgabenkette"
        OwnerMissionLane.DEEPCLEAN -> "Speicher-Lenkung"
    }

    companion object {
        const val MAX_JOURNAL = 12
        /** Provenance of the entries mirrored from the cockpit work journal. */
        const val JOURNAL = "Arbeitsverlauf"
        /** The work journal's own sentence for its newest open point ([at.heinz.deviceai.cognitive.WorkDecisionRecall]). */
        const val JOURNAL_CONTINUE_ORDER = "Mach beim letzten offenen Punkt weiter"
        const val BRIEFING_ORDER = "Gib mir mein Briefing"

        /** The document desk's own order for opening a PDF by name; null when the name gives no usable words. */
        fun openOrder(name: String): String? {
            val base = name.trim().replace(Regex("(?i)\\.pdf$"), "").replace(Regex("[\\p{Cntrl}„“”\"]+"), " ").trim()
            return if (base.length in 2..80) "Öffne das PDF $base" else null
        }
    }
}
