package at.heinz.deviceai.voice

/**
 * Remembers where a spoken reply was interrupted so that „weiter“ resumes it from the interrupted piece (sentence
 * group). The chat already shows the whole reply; only speech is resumed. Pure, single-threaded (UI thread).
 *
 * Bounds: one reply at a time, at most [MAX_PIECES] pieces, valid for [TTL_MS] after the interruption and only in the
 * chat session it came from. A new spoken reply replaces it; a reply that finished normally leaves nothing.
 */
class ResumableReplySpeech(private val clock: () -> Long) {
    private var turn: String? = null
    private var session: String? = null
    private var pieces: List<String> = emptyList()
    /** First piece that has not finished playing. */
    private var cursor = 0
    private var rest: List<String> = emptyList()
    private var restSession: String? = null
    private var interruptedAt = Long.MIN_VALUE

    fun begin(turnId: String, sessionId: String, spokenPieces: List<String>) {
        turn = turnId
        session = sessionId
        pieces = spokenPieces.take(MAX_PIECES)
        cursor = 0
        clearRest()
    }

    fun pieceStarted(turnId: String, index: Int) {
        if (turnId == turn && index in pieces.indices) cursor = index
    }

    fun pieceFinished(turnId: String, index: Int) {
        if (turnId == turn && index in pieces.indices) cursor = maxOf(cursor, index + 1)
    }

    /** The reply ended. [interrupted]: the owner (or a failure) cut it; the unspoken rest is kept for „weiter“. */
    fun ended(turnId: String, interrupted: Boolean) {
        if (turnId != turn) return
        if (interrupted && cursor < pieces.size) {
            rest = pieces.drop(cursor)
            restSession = session
            interruptedAt = clock()
        } else clearRest()
        turn = null
        pieces = emptyList()
        cursor = 0
    }

    /** True when „weiter“ in [sessionId] has something to resume. */
    fun canResume(sessionId: String): Boolean {
        if (rest.isEmpty() || restSession != sessionId) return false
        val age = clock() - interruptedAt
        if (age < 0 || age > TTL_MS) { clearRest(); return false }
        return true
    }

    /** Takes the rest (once). */
    fun takeRest(sessionId: String): List<String>? {
        if (!canResume(sessionId)) return null
        return rest.also { clearRest() }
    }

    fun clearRest() {
        rest = emptyList()
        restSession = null
        interruptedAt = Long.MIN_VALUE
    }

    companion object {
        const val MAX_PIECES = 64
        const val TTL_MS = 10 * 60_000L

        /** Index of a chunk id "<turn>-<index>" as the activity builds them; null when it is not one of [turn]. */
        fun pieceIndex(turn: String, chunkId: String): Int? =
            chunkId.takeIf { it.startsWith("$turn-") }?.substring(turn.length + 1)?.toIntOrNull()
    }
}
