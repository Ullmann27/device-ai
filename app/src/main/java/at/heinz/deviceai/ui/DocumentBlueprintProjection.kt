package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CockpitAnimationEvent
import at.heinz.deviceai.cockpit.DocumentDraftAction
import at.heinz.deviceai.cockpit.DocumentDraftReadyEvent
import at.heinz.deviceai.cockpit.DocumentPipelineStepEvent
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState

/**
 * Folds real cockpit events into [HollywoodFuiLiveData.Document].
 * Missing facts stay absent: no placeholder steps, draft headings, buttons or progress.
 */
class DocumentBlueprintProjection {
    private var name: String? = null
    private var mime: String? = null
    private var question: String? = null
    private var pageCount: Int? = null
    private var currentPage: Int? = null
    private var entriesAdded: Int? = null
    private val steps = LinkedHashMap<String, HollywoodFuiLiveData.Document.PipelineStep>()
    private var draft: HollywoodFuiLiveData.Document.Draft? = null
    private var progressPercent: Int? = null
    private var actions: Set<DocumentDraftAction> = emptySet()

    val hasDocument: Boolean get() = name != null

    fun reset() {
        name = null
        mime = null
        question = null
        pageCount = null
        currentPage = null
        entriesAdded = null
        steps.clear()
        draft = null
        progressPercent = null
        actions = emptySet()
    }

    fun onQuestion(event: CockpitAnimationEvent.DocumentQuestion) {
        name = event.documentName
        mime = event.mimeType
        question = event.question
        pageCount = event.pageCount?.takeIf { it >= 1 }
        // presentDocumentScene always mounts the PdfRenderer first page when pageCount was measured.
        currentPage = 1.takeIf { pageCount != null }
        upsert(DocumentPipelineStepEvent.KEY_OPEN, StepState.DONE)
    }

    // A memory hub DocumentLearned reports a ledger docId, which is NOT a verified identifier
    // for the owner-selected PDF intake. Until a common, provenance-checked ID exists, do
    // not turn unrelated background learning into this document's progress or added-entry count.
    // Keep the protocol method for existing presenters; the verified document pipeline alone
    // can advance its own CAPTURE step.
    fun onLearned(@Suppress("UNUSED_PARAMETER") event: CockpitAnimationEvent.DocumentLearned) = Unit

    fun onTimeline(processSteps: List<ProcessStep>) {
        processSteps.forEach { step ->
            val key = DocumentPipelineStepEvent.canonicalKey(step.key, step.label) ?: return@forEach
            upsert(key, step.state)
        }
    }

    fun onPipelineStep(event: DocumentPipelineStepEvent) {
        val key = DocumentPipelineStepEvent.canonicalKey(event.key, event.label) ?: return
        upsert(key, event.state)
    }

    fun onDraft(event: DocumentDraftReadyEvent) {
        val text = event.text.filterNot { it.isISOControl() && it != '\n' }.trim()
        if (text.isEmpty()) return
        draft = parseDraft(text)
        actions = event.actions
        val covered = event.coveredChars
        val total = event.totalChars
        progressPercent = if (covered != null && total != null && total > 0) {
            ((covered.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else null
        upsert(DocumentPipelineStepEvent.KEY_DRAFT, StepState.DONE)
    }

    fun snapshot(): HollywoodFuiLiveData.Document? {
        val documentName = name ?: return null
        val ordered = DocumentPipelineStepEvent.CANONICAL.keys.mapNotNull { steps[it] }
        return HollywoodFuiLiveData.document(
            documentName,
            mime ?: "",
            question,
            pageCount = pageCount,
            entriesAdded = entriesAdded,
            currentPage = currentPage,
            steps = ordered,
            draft = draft,
            progressPercent = progressPercent,
            actions = actions
        )
    }

    private fun upsert(key: String, state: StepState) {
        val label = DocumentPipelineStepEvent.CANONICAL[key] ?: return
        steps[key] = HollywoodFuiLiveData.Document.PipelineStep(key, label, state)
    }

    companion object {
        private val HEADING = Regex(
            "(?im)^[ \\t]*(Ziel|Vorgehen|Offene Punkte)[ \\t]*:[ \\t]*(.*)$"
        )

        internal fun parseDraft(text: String): HollywoodFuiLiveData.Document.Draft {
            val matches = HEADING.findAll(text).toList()
            if (matches.isEmpty()) {
                return HollywoodFuiLiveData.Document.Draft(null, null, null, text)
            }
            fun section(name: String): String? {
                val index = matches.indexOfFirst { it.groupValues[1].equals(name, ignoreCase = true) }
                if (index < 0) return null
                val start = matches[index]
                val afterHeading = start.groupValues[2]
                val from = start.range.last + 1
                val to = matches.getOrNull(index + 1)?.range?.first ?: text.length
                val rest = text.substring(from, to).trim()
                val combined = listOf(afterHeading.trim(), rest).filter { it.isNotEmpty() }.joinToString("\n").trim()
                return combined.ifEmpty { null }
            }
            return HollywoodFuiLiveData.Document.Draft(
                goal = section("Ziel"),
                approach = section("Vorgehen"),
                openItems = section("Offene Punkte"),
                body = null
            )
        }
    }
}
