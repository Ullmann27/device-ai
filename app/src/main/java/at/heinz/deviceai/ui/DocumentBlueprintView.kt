package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.DocumentDraftAction
import at.heinz.deviceai.cockpit.StepState

/**
 * What [HollywoodFuiStageOverlay] may draw for Document Blueprint.
 * Absent facts stay absent: no invented page index, headings, buttons or progress.
 */
object DocumentBlueprintView {
    data class Visible(
        val documentName: String,
        val question: String?,
        val pageLabel: String?,
        val entriesLabel: String?,
        val steps: List<HollywoodFuiLiveData.Document.PipelineStep>,
        val draftTitle: String?,
        val draftGoal: String?,
        val draftApproach: String?,
        val draftOpenItems: String?,
        val draftBody: String?,
        val buttons: List<String>,
        val progressPercent: Int?
    ) {
        val showsDraft: Boolean
            get() = draftTitle != null
    }

    fun visible(data: HollywoodFuiLiveData.Document?): Visible? {
        data ?: return null
        val pageLabel = if (data.currentPage != null && data.pageCount != null) {
            "${data.currentPage}/${data.pageCount}"
        } else null
        val draft = data.draft
        val showsDraft = draft != null && (
            !draft.goal.isNullOrBlank() ||
                !draft.approach.isNullOrBlank() ||
                !draft.openItems.isNullOrBlank() ||
                !draft.body.isNullOrBlank()
            )
        return Visible(
            documentName = data.documentName,
            question = data.question,
            pageLabel = pageLabel,
            entriesLabel = data.entriesAdded?.let { "$it bestätigte Einträge" },
            steps = data.steps,
            draftTitle = if (showsDraft) "Zusammenfassung – Entwurf" else null,
            draftGoal = draft?.goal,
            draftApproach = draft?.approach,
            draftOpenItems = draft?.openItems,
            draftBody = draft?.body,
            buttons = data.actions.mapNotNull { buttonLabel(it) },
            progressPercent = data.progressPercent
        )
    }

    fun buttonLabel(action: DocumentDraftAction): String? = when (action) {
        DocumentDraftAction.OPEN -> "Entwurf öffnen"
        DocumentDraftAction.CONTINUE -> "Weiterbearbeiten"
        DocumentDraftAction.SAVE -> "Speichern"
    }

    fun lightBridgeFrom(steps: List<HollywoodFuiLiveData.Document.PipelineStep>): List<Int> =
        steps.indices.filter { index ->
            index < steps.lastIndex && steps[index].state == StepState.DONE
        }
}
