package at.heinz.deviceai.cognitive

import java.time.Instant
import java.time.ZoneId

/**
 * Test-only access for the evaluation runner to the package-internal deterministic lanes.
 * It calls the productive objects unchanged and adds no behaviour of its own.
 */
object EvalProbe {
    /** `READY:<APP>:<GOAL>`, `ASK`, `UNSUPPORTED` or `NONE`. */
    fun computer(utterance: String): String = when (val result = OwnerComputerIntentParser.parse(utterance)) {
        is OwnerComputerIntentParser.Result.Ready -> "READY:${result.intent.app.name}:${result.intent.goal.name}"
        is OwnerComputerIntentParser.Result.Ask -> "ASK"
        is OwnerComputerIntentParser.Result.Unsupported -> "UNSUPPORTED"
        OwnerComputerIntentParser.Result.NotComputerCommand -> "NONE"
    }

    fun objectAnswer(ownerTurns: List<String>, question: String): String? =
        OwnerObjectStateLedger().apply { ownerTurns.forEach(::observeOwnerTurn) }.answer(question)

    fun clock(utterance: String, now: Instant, zone: ZoneId): String? = OwnerDeviceClockReply.answer(utterance, now, zone)

    fun register(utterance: String): String = ConversationStyle.detect(utterance).name

    fun unfinished(utterance: String): Boolean = OwnerTurnCompletion.looksUnfinished(utterance)

    /** Drive-mode command of a whole utterance while the mode is off. */
    fun driveCommand(utterance: String): String? = DriveMode().commandFor(utterance)?.name

    /** [kind]: RESOLVED, AMBIGUOUS, MISSING or NONE; [text]: the resolved passage, the question back or the reason. */
    data class ReferenceOutcome(val kind: String, val document: String? = null, val page: Int? = null,
                                val paragraph: Int? = null, val text: String = "", val intent: String? = null,
                                val pageAssumed: Boolean = false, val comparedWith: ReferenceOutcome? = null)

    /** One session of the productive reference registry: documents are opened, owner turns routed, an input resolved. */
    class ReferenceSession {
        private val registry = ConversationReferences()
        private var opened = 0

        fun open(name: String, pageCount: Int, pages: List<String>, textState: String = "EXTRACTED") {
            val state = OwnerDocumentDialogue.TextState.valueOf(textState)
            val read = state == OwnerDocumentDialogue.TextState.EXTRACTED
            registry.register(OwnerDocumentDialogue.Reference("eval", null, "content://eval/${opened++}", name, pageCount,
                null, state, if (read) pages.size else 0, if (read) pages.sumOf { it.length } else 0,
                if (read) pages else null, if (read) pages.joinToString("\n\n") else "", 0L))
        }

        fun say(turn: String) { registry.route(turn) }

        fun resolve(input: String): ReferenceOutcome = outcome(registry.resolve(input))

        private fun outcome(resolution: ConversationReferences.Resolution): ReferenceOutcome = when (resolution) {
            is ConversationReferences.Resolution.Resolved -> ReferenceOutcome("RESOLVED", resolution.document.name,
                resolution.page, resolution.paragraph, resolution.text, resolution.intent.name, resolution.pageAssumed,
                resolution.comparedWith?.let(::outcome))
            is ConversationReferences.Resolution.Ambiguous -> ReferenceOutcome("AMBIGUOUS", text = resolution.question)
            is ConversationReferences.Resolution.Missing -> ReferenceOutcome("MISSING", text = resolution.reason)
            ConversationReferences.Resolution.NotAReference -> ReferenceOutcome("NONE")
        }
    }
}
