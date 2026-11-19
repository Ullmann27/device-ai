package at.heinz.deviceai

import at.heinz.deviceai.cognitive.OwnerDeliverable

/**
 * How one owner order ended in its lane. The owner lanes (work tools, document desk, send lane) report it through
 * their host when an order is settled – typed, so nobody has to read an outcome out of the wording of a reply.
 * Every host's default is a no-op: a lane that nobody listens to behaves exactly as before.
 */
internal sealed interface OwnerLaneOutcome {
    /** The order is done; [result] is what really exists now. */
    data class Done(val result: OwnerLaneResult) : OwnerLaneOutcome

    /** The lane asked the owner and keeps that question open. Nothing was changed, saved or sent yet. */
    data class NeedsOwner(val need: Need, val question: String) : OwnerLaneOutcome

    /** Not done. [reason]: the lane's own sentence about it. */
    data class Failed(val reason: String) : OwnerLaneOutcome

    /** Ended by the owner: the stop word, or a no to the lane's question. */
    data object Stopped : OwnerLaneOutcome

    enum class Need { CHOICE, GRANT, CONFIRMATION, DETAIL }
}

/** What an order left behind – only facts the lane itself established. */
internal sealed interface OwnerLaneResult {
    /** A PDF is open in the work window. [query]: the name the owner asked for, when the desk searched for it. */
    data class DocumentOpened(val name: String, val folder: String?, val pages: Int, val query: String?) : OwnerLaneResult

    /** A pure search: the hits are shown, nothing was opened. */
    data class FilesFound(val query: String, val names: List<String>) : OwnerLaneResult

    /** The news briefing was shown; [deliverable] is what „das“ now refers to. */
    data class Briefing(val deliverable: OwnerDeliverable, val stories: Int, val sources: Int, val byModel: Boolean) : OwnerLaneResult

    /** [of] was written as [file]. [verified]: read back and equal; null when the lane does not read back. */
    data class FileSaved(val file: OwnerSavedFile, val of: OwnerDeliverable, val location: String?, val verified: Boolean?) : OwnerLaneResult

    /**
     * One page of the open PDF was really read ([coveredChars] > 0 of [totalChars] characters went into the summary)
     * and the accepted summary was shown in the chat. Nothing was saved; [of] is the result a later „speichere das als
     * Word“ means.
     */
    data class PageSummarised(val of: OwnerDeliverable, val document: String, val page: Int, val coveredChars: Int,
                              val totalChars: Int) : OwnerLaneResult

    /**
     * The calendar was read – never written – for [span] (the briefing lane's own words: „diese Woche“). [events]:
     * entries still ahead, [findings]: what the fixed rules of the agenda check noticed, [freeBlocks]: free stretches
     * of at least two hours. Counted from the entries themselves; no model saw them and nothing left the device.
     */
    data class Agenda(val span: String, val events: Int, val findings: Int, val freeBlocks: Int) : OwnerLaneResult

    /** A task chain found one file. [folderHint]: the desk's name for its folder, when it has one. */
    data class FileFound(val name: String, val folderHint: String?) : OwnerLaneResult

    /** A saved file was handed to the owner's viewer. */
    data class FileOpened(val file: OwnerSavedFile) : OwnerLaneResult

    /**
     * The storage lane checked or cleaned the storage ([mode]: the lane's mode name). Every number is measured:
     * [freeBefore]/[freeAfter] by Android's volume figures (null = not readable), [removedBytes] is the sum of the sizes
     * of the [removedFiles] files that were really removed or trashed; [offered]: files offered for the owner's choice.
     */
    data class Storage(val mode: String, val freeBefore: Long?, val freeAfter: Long?, val removedFiles: Int, val removedBytes: Long,
                       val offered: Int) : OwnerLaneResult

    /** A message or mail for [to]. [state] says how far it really got. */
    data class Delivered(val channel: String, val to: String, val attachment: OwnerSavedFile?, val state: State) : OwnerLaneResult {
        enum class State {
            /** The owner's mail app shows the prepared mail; nothing was sent. */
            HANDED_OVER,
            /** A verified draft in the app; by design not sent. */
            DRAFT,
            /** Pressed once and confirmed on screen. */
            SENT
        }
    }
}
