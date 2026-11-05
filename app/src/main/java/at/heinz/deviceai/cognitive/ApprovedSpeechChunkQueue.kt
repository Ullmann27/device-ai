package at.heinz.deviceai.cognitive

/**
 * Ordered approved TTS chunks for one voice turn.
 * Only an external quality gate can approve text.
 * Draft TextDelta output must never enter this queue.
 */
internal class ApprovedSpeechChunkQueue(private val maxChunks: Int = 16) {
    init { require(maxChunks in 1..64) }

    data class Chunk(
        val turnId: String,
        val chunkId: String,
        val approvedText: String,
        val approvalEvidenceId: String
    )

    enum class Completion { IGNORED, MORE_CHUNKS, WAIT_FOR_CHUNKS, LAST_CHUNK_FINISHED }

    private data class Turn(
        val id: String,
        val pending: ArrayDeque<Chunk> = ArrayDeque(),
        val ids: MutableSet<String> = HashSet(),
        var activeChunkId: String? = null,
        var submittedCount: Int = 0,
        var final: Boolean = false
    )

    private var turn: Turn? = null

    @Synchronized fun beginTurn(turnId: String): Boolean {
        if (turnId.isBlank() || turnId.length > 96 || turn != null) return false
        turn = Turn(turnId)
        return true
    }

    @Synchronized fun appendApproved(
        turnId: String, chunkId: String, text: String, approvalEvidenceId: String
    ): Boolean {
        val current = turn ?: return false
        if (current.id != turnId || current.final ||
            chunkId.isBlank() || chunkId.length > 96 || chunkId in current.ids ||
            approvalEvidenceId.isBlank() ||
            text.isBlank() || text.length > 800 ||
            current.submittedCount >= maxChunks) return false
        current.ids.add(chunkId)
        current.submittedCount++
        current.pending.addLast(Chunk(turnId, chunkId, text, approvalEvidenceId))
        return true
    }

    @Synchronized fun markLastChunkSubmitted(turnId: String): Boolean {
        val current = turn ?: return false
        if (current.id != turnId || current.final) return false
        current.final = true
        return true
    }

    /** The caller must enqueue this chunk in TTS before sending another. */
    @Synchronized fun takeNextReady(turnId: String): Chunk? {
        val current = turn ?: return null
        if (current.id != turnId || current.activeChunkId != null) return null
        val next = current.pending.removeFirstOrNull() ?: return null
        current.activeChunkId = next.chunkId
        return next
    }

    @Synchronized fun onChunkFinished(turnId: String, chunkId: String): Completion {
        val current = turn ?: return Completion.IGNORED
        if (current.id != turnId || current.activeChunkId != chunkId)
            return Completion.IGNORED
        current.activeChunkId = null
        if (current.pending.isNotEmpty()) return Completion.MORE_CHUNKS
        if (!current.final) return Completion.WAIT_FOR_CHUNKS
        turn = null
        return Completion.LAST_CHUNK_FINISHED
    }

    /** STOP invalidates every pending and in-flight chunk. */
    @Synchronized fun stop(turnId: String): Boolean {
        if (turn?.id != turnId) return false
        turn = null
        return true
    }

    @Synchronized fun hasActiveTurn(): Boolean = turn != null
}
