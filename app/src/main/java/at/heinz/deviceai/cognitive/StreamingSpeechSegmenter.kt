package at.heinz.deviceai.cognitive

/**
 * Release policy for speaking while the local model still generates.
 *
 * GROUNDWORK FOR AN OWNER DECISION: no production code calls this class. Today only the complete,
 * quality-gated final reply is spoken (see docs/research/STREAMING-SPEECH-PROPOSAL-20261118.md).
 *
 * [ApprovedStreamingSpeechAssembler] proposes sentence boundaries eagerly. This class decides which
 * of those sentences may leave for the speech queue before the model has finished:
 *  - the turn must be eligible at all ([Turn.eligible]);
 *  - the sentence must be closed and its boundary confirmed by the text that follows it
 *    (enough further characters, and a continuation that starts like a new sentence);
 *  - it must have been closed for [Policy.holdMillis];
 *  - no tool proposal, buffer overflow or earlier rejection may have closed the stream;
 *  - it must pass the caller's per-sentence gate.
 * Everything released is remembered, so a later failure of the whole answer can be corrected aloud
 * ([retract]). State is bounded by the assembler buffer, [Policy.maxHeldCharacters] and
 * [Policy.maxTotalSegments].
 */
internal class StreamingSpeechSegmenter(
    turn: Turn,
    private val policy: Policy = Policy(),
    private val sentenceGate: (String) -> Boolean
) {
    /** Facts about the turn, known before generation starts. */
    data class Turn(
        /** Generation runs without any tool offered, so the visible reply is this text. */
        val textOnly: Boolean,
        /** The prompt carries tool observations: this text is a continuation, not a first reply. */
        val toolObservationsSupplied: Boolean,
        /** The reply must reproduce stored owner facts; only the final evidence check can approve it. */
        val groundedRecall: Boolean,
        /** The reply answers from a read document; only the final document check can approve it. */
        val documentAnswer: Boolean
    ) {
        val eligible: Boolean
            get() = textOnly && !toolObservationsSupplied && !groundedRecall && !documentAnswer
    }

    data class Policy(
        /** Non-whitespace characters that must follow a sentence before its boundary counts as confirmed. */
        val confirmCharacters: Int = 12,
        /** Minimum time a sentence must have been closed before it may be released. */
        val holdMillis: Long = 250,
        /** Sentences released before completion; the rest waits for [finish]. */
        val maxEarlySegments: Int = 8,
        val maxSegmentCharacters: Int = 480,
        val maxHeldCharacters: Int = 4_800,
        val maxTotalSegments: Int = 64
    ) {
        init {
            require(confirmCharacters in 1..200 && holdMillis in 0..10_000)
            require(maxEarlySegments in 1..maxTotalSegments && maxTotalSegments in 1..256)
            require(maxSegmentCharacters in 80..800 && maxHeldCharacters in 256..32_000)
        }
    }

    data class Segment(
        val index: Int,
        val text: String,
        /** Released while the model was still generating. */
        val early: Boolean,
        val closedAtMillis: Long,
        val releasedAtMillis: Long
    )

    enum class State {
        /** Sentences may still be released. */
        OPEN,
        /** The turn was never eligible; nothing is released. */
        INELIGIBLE,
        /** A tool proposal arrived: the streamed text is not the reply. */
        TOOL_PROPOSED,
        /** A sentence failed the per-sentence gate; it and everything after it stay unspoken. */
        GATE_REJECTED,
        /** Text was dropped or exceeded the bounds; a gap must never be spoken over. */
        OVERFLOWED,
        /** The model completed and every sentence was released. */
        FINISHED,
        /** The caller withdrew the stream after the final gate failed. */
        RETRACTED
    }

    data class Finish(
        val segments: List<Segment>,
        /** True only when the whole streamed text has been released, early or now. */
        val complete: Boolean
    )

    data class Retraction(
        /** Everything that had already been handed to speech, in order. */
        val released: List<Segment>,
        /** Spoken before the approved reply when something was already said; null when nothing was. */
        val spokenNotice: String?
    )

    private class Held(var raw: String, val closedAtMillis: Long)

    private val assembler = ApprovedStreamingSpeechAssembler()
    private val held = ArrayDeque<Held>()
    private val ledger = ArrayList<Segment>()
    private var lastElapsedMillis = 0L

    var state: State = if (turn.eligible) State.OPEN else State.INELIGIBLE
        private set

    /** Everything released so far, in order. */
    val released: List<Segment> get() = ledger.toList()

    /** One text delta of the running generation. Returns the sentences that may be spoken now. */
    fun onDelta(delta: String, elapsedMillis: Long): List<Segment> {
        if (!advance(elapsedMillis)) return emptyList()
        val candidates = assembler.append(delta)
        if (assembler.overflowed) return close(State.OVERFLOWED)
        candidates.forEach { held.addLast(Held(it.rawText, lastElapsedMillis)) }
        if (held.sumOf { it.raw.length } > policy.maxHeldCharacters) return close(State.OVERFLOWED)
        return release(final = false)
    }

    /** Time passed without a delta: a confirmed sentence may have reached its hold time. */
    fun poll(elapsedMillis: Long): List<Segment> =
        if (advance(elapsedMillis)) release(final = false) else emptyList()

    /** The model announced a tool call: nothing further is released and the caller must [retract]. */
    fun onToolProposal() {
        if (state == State.OPEN) close(State.TOOL_PROPOSED)
    }

    /**
     * The model completed and the caller's final gate approved the whole reply: releases the rest.
     * In any closed state nothing is returned and [Finish.complete] is false; the caller then speaks
     * the approved reply through the existing final path (after [retract] if something was released).
     */
    fun finish(elapsedMillis: Long): Finish {
        if (!advance(elapsedMillis)) return Finish(emptyList(), complete = false)
        // The tail is taken as text, not as an assembler candidate: a reply that ends inside unfinished
        // code keeps its prose, and the spoken form replaces the code exactly as the final reply path does.
        val tail = assembler.pendingText().trim()
        assembler.flushFinal()
        if (tail.isNotEmpty()) held.addLast(Held(tail, lastElapsedMillis))
        val rest = release(final = true)
        if (state != State.OPEN) return Finish(rest, complete = false)
        state = State.FINISHED
        return Finish(rest, complete = true)
    }

    /**
     * The final gate rejected the reply, or it differs from what was streamed: stops the stream and
     * reports what was already released so the caller can correct it aloud before the approved reply.
     */
    fun retract(): Retraction {
        if (state != State.INELIGIBLE) state = State.RETRACTED
        held.clear()
        val spoken = ledger.toList()
        return Retraction(spoken, if (spoken.isEmpty()) null else CORRECTION_NOTICE)
    }

    /**
     * True when the approved final reply begins with exactly the released text (compared in spoken
     * form). Anything else means the visible reply was changed after streaming and requires [retract].
     */
    fun releasedIsPrefixOf(finalReply: String): Boolean {
        val spoken = normalized(ledger.joinToString(" ") { it.text })
        if (spoken.isEmpty()) return true
        val approved = JarvisVoiceProfile.speakableChunks(finalReply, policy.maxSegmentCharacters, 64).pieces
        return normalized(approved.joinToString(" ")).startsWith(spoken)
    }

    private fun advance(elapsedMillis: Long): Boolean {
        require(elapsedMillis >= 0)
        if (state != State.OPEN) return false
        lastElapsedMillis = maxOf(lastElapsedMillis, elapsedMillis)
        return true
    }

    private fun close(reason: State): List<Segment> {
        state = reason
        held.clear()
        return emptyList()
    }

    private fun release(final: Boolean): List<Segment> {
        val out = ArrayList<Segment>()
        while (held.isNotEmpty()) {
            val head = held.first()
            val following = held.getOrNull(1)?.raw ?: assembler.pendingText().takeIf { !final }.orEmpty()
            val first = following.firstOrNull { !it.isWhitespace() }
            if (first != null && first.isLetter() && first.isLowerCase()) {
                // A sentence does not start in lower case: the proposed boundary was not real.
                // (A list marker, quote or digit in front is a new unit and is left alone.)
                if (held.size < 2) break
                held.removeFirst()
                held.first().raw = head.raw + " " + held.first().raw
                continue
            }
            if (!final) {
                if (held.size == 1 && following.count { !it.isWhitespace() } < policy.confirmCharacters) break
                if (lastElapsedMillis - head.closedAtMillis < policy.holdMillis) break
                if (ledger.size >= policy.maxEarlySegments) break
            }
            val chunks = JarvisVoiceProfile.speakableChunks(head.raw, policy.maxSegmentCharacters, 16)
            if (chunks.truncated || ledger.size + chunks.pieces.size > policy.maxTotalSegments) {
                close(State.OVERFLOWED)
                break
            }
            // The whole sentence is judged before any part of it leaves.
            if (chunks.pieces.isNotEmpty() && !sentenceGate(chunks.pieces.joinToString(" "))) {
                close(State.GATE_REJECTED)
                break
            }
            held.removeFirst()
            chunks.pieces.forEach { piece ->
                val segment = Segment(ledger.size + 1, piece, early = !final, head.closedAtMillis, lastElapsedMillis)
                ledger.add(segment)
                out.add(segment)
            }
        }
        return out
    }

    private fun normalized(text: String): String = text.replace(WHITESPACE, " ").trim()

    companion object {
        /** Spoken once before the approved reply when early speech has to be withdrawn. */
        const val CORRECTION_NOTICE =
            "Korrektur: Was ich gerade gesagt habe, war noch nicht geprüft. Hier ist die geprüfte Antwort."

        private val WHITESPACE = Regex("\\s+")

        /**
         * The lexical checks of [LocalDialogueQualityGate] that are meaningful for one sentence:
         * leaked prompt frames, denial of supplied context, unrequested dialect and a sentence
         * repeated from the previous reply. It cannot judge truth, and neither can the final gate.
         */
        fun lexicalSentenceGate(
            request: String,
            previousRequest: String? = null,
            previousResponse: String? = null,
            priorOwnerContextSupplied: Boolean = false
        ): (String) -> Boolean = { sentence ->
            LocalDialogueQualityGate.evaluate(
                request = request,
                response = sentence,
                previousRequest = previousRequest,
                previousResponse = previousResponse,
                priorOwnerContextSupplied = priorOwnerContextSupplied
            ).success
        }
    }
}
