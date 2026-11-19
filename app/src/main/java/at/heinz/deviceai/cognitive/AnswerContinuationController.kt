package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.LocalModelGenerationPolicy

/**
 * Code64 – bounded automatic continuation of an answer that stopped at the local output limit.
 *
 * It decides, after each finished generation of the owner's own text-only turn, one of three things:
 *  - [Decision.Unchanged]: this is not an automatic continuation – the caller presents the segment as before;
 *  - [Decision.Continue]: the caller re-enters the EXISTING continuation path („weiter“) once more and shows or
 *    speaks nothing yet; the visible state says truthfully „Antwort wird fortgesetzt (2/3)“;
 *  - [Decision.Deliver]: the joined answer is shown and spoken now – complete, or still cut with the manual
 *    „weiter“ hint exactly like a single cut answer.
 *
 * Hard bounds: at most [maxSegments] generations per owner turn (default [MAX_SEGMENTS], never more than
 * [SEGMENT_LIMIT]) and at most [maxCharacters] joined characters. It continues only while every segment ended at
 * the output limit and the caller still calls the turn eligible (same session, no new owner turn, no pending chat
 * switch, text only, no tools); a thermal stop, a stall, a rejected or failed segment ends it. A segment that
 * [AnswerContinuationJoiner] refuses (new start, list stepping back, nothing new) is not joined and not recorded.
 *
 * While a continuation runs, the part already generated is held here. When the next segment never arrives as a
 * result (STOP, failure, changed conversation), the caller takes it with [abort] and shows it – nothing generated
 * is lost and nothing is spoken that the owner stopped. Pure state machine: no model, no clock, no Android.
 */
internal class AnswerContinuationController(
    private val maxSegments: Int = MAX_SEGMENTS,
    private val maxCharacters: Int = LocalModelGenerationPolicy.MAX_RESPONSE_CHARACTERS
) {
    init { require(maxSegments in 1..SEGMENT_LIMIT && maxCharacters > 0) }

    /**
     * One finished generation. [successful]: complete, accepted and not blank. [partial]: incomplete but with
     * accepted text. [blocker]: the orchestrator's reason when it is not successful.
     */
    data class Segment(val response: String, val successful: Boolean, val partial: Boolean, val blocker: String?) {
        val outputLimit: Boolean get() = partial && response.isNotBlank() && blocker == OUTPUT_LIMIT_BLOCKER
    }

    enum class Reason { COMPLETE, SEGMENT_LIMIT, CHARACTER_LIMIT, NOT_OUTPUT_LIMIT, NOT_ELIGIBLE,
        SEGMENT_WITHHELD, SEGMENT_EMPTY, SEGMENT_RESTARTED, SEGMENT_FORMAT_BREAK }

    sealed class Decision {
        object Unchanged : Decision()

        data class Continue(val segment: Int, val of: Int) : Decision() {
            /** Owner-visible work status while the next segment is generated. */
            val status: String get() = "Antwort wird fortgesetzt ($segment/$of)"
        }

        /**
         * [answer]: all joined segments. [complete] false: still cut – present with [blocker] like a single partial
         * answer. [segmentAccepted] false: the last segment was not joined; it must not be shown, recorded or
         * become the anchor of a later „weiter“.
         */
        data class Deliver(val answer: String, val complete: Boolean, val blocker: String?, val segments: Int,
                           val segmentAccepted: Boolean, val reason: Reason) : Decision()
    }

    /** What was generated before a continuation ended without a result; [lastRequest]/[lastSegment] = its anchor. */
    data class Held(val sessionId: String, val answer: String, val blocker: String, val segments: Int,
                    val lastRequest: String, val lastSegment: String)

    private class State(val sessionId: String, val ownerRevision: Long, val answer: String, val blocker: String,
                        val segments: Int, val lastRequest: String, val lastSegment: String)

    private var state: State? = null

    val pending: Boolean @Synchronized get() = state != null

    /**
     * [continuation]: this generation was started by a [Decision.Continue] of this controller. [eligible]: the
     * caller's own conditions for going on (see class comment); it never forces a continuation.
     */
    @Synchronized fun onSegment(sessionId: String, ownerRevision: Long, request: String, continuation: Boolean,
                                eligible: Boolean, segment: Segment): Decision {
        val held = state
        state = null
        if (!continuation || held == null || held.sessionId != sessionId) {
            // A stale hold of another turn is the caller's to present (see abort); it is never joined with this one.
            if (held != null) { state = held; return Decision.Unchanged }
            if (continuation || !eligible || !segment.outputLimit || maxSegments < 2 ||
                segment.response.length >= maxCharacters) return Decision.Unchanged
            state = State(sessionId, ownerRevision, segment.response, OUTPUT_LIMIT_BLOCKER, 1, request, segment.response)
            return Decision.Continue(2, maxSegments)
        }
        fun keep(reason: Reason) = Decision.Deliver(held.answer, false, held.blocker, held.segments, false, reason)
        if (!segment.successful && !segment.partial) return keep(Reason.SEGMENT_WITHHELD)
        val joined = AnswerContinuationJoiner.join(held.answer, segment.response, maxCharacters)
        when (joined.stop) {
            AnswerContinuationJoiner.Stop.NONE -> Unit
            AnswerContinuationJoiner.Stop.EMPTY -> return keep(Reason.SEGMENT_EMPTY)
            AnswerContinuationJoiner.Stop.RESTARTED -> return keep(Reason.SEGMENT_RESTARTED)
            AnswerContinuationJoiner.Stop.FORMAT_BREAK -> return keep(Reason.SEGMENT_FORMAT_BREAK)
            AnswerContinuationJoiner.Stop.TOO_LONG -> return keep(Reason.CHARACTER_LIMIT)
        }
        val segments = held.segments + 1
        if (segment.successful) return Decision.Deliver(joined.text, true, null, segments, true, Reason.COMPLETE)
        val stop = when {
            !segment.outputLimit -> Reason.NOT_OUTPUT_LIMIT
            segments >= maxSegments -> Reason.SEGMENT_LIMIT
            joined.text.length >= maxCharacters -> Reason.CHARACTER_LIMIT
            !eligible || held.ownerRevision != ownerRevision -> Reason.NOT_ELIGIBLE
            else -> null
        }
        if (stop != null) return Decision.Deliver(joined.text, false, segment.blocker ?: held.blocker, segments, true, stop)
        state = State(sessionId, ownerRevision, joined.text, OUTPUT_LIMIT_BLOCKER, segments, request, segment.response)
        return Decision.Continue(segments + 1, maxSegments)
    }

    /** Ends a running continuation and hands out what it held, or null. Safe to call at any time. */
    @Synchronized fun abort(): Held? = state?.let {
        state = null
        Held(it.sessionId, it.answer, it.blocker, it.segments, it.lastRequest, it.lastSegment)
    }

    companion object {
        /** One answer is at most the first generation and two automatic continuations. */
        const val MAX_SEGMENTS = 3
        const val SEGMENT_LIMIT = 4
        /** The request the existing continuation path recognises ([LocalConversationFollowUpPolicy.requestsContinuation]). */
        const val CONTINUE_REQUEST = "weiter"
        /** PowerManager.THERMAL_STATUS_LIGHT. */
        private const val THERMAL_LIGHT = 1

        /**
         * A further generation directly after one that ran into the output limit is the longest compute stretch a
         * single owner turn can cause. It is only started while Android reports no thermal pressure (status NONE)
         * or reports no status at all; from LIGHT on the cut answer is shown with the „weiter“ hint and the owner
         * decides. [status] is PowerManager's thermal status (0..6), never a temperature.
         */
        fun thermalAllows(status: Int?): Boolean = status == null || status < THERMAL_LIGHT
        /** How [BoundedAgentOrchestrator] reports an accepted partial text that stopped at the output limit. */
        val OUTPUT_LIMIT_BLOCKER = "Antwort unvollständig: " + LocalGenerationFailureCause.OUTPUT_LIMIT.ownerMessage
    }
}
