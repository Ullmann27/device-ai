package at.heinz.deviceai.cognitive

/**
 * Incremental sentence boundary detector for future early TTS.
 *
 * It never approves speech. It only exposes complete prose candidates. Callers must
 * pass each candidate through their existing quality/evidence gate before appending
 * it to ApprovedSpeechChunkQueue. Raw TextDelta output must never be spoken.
 */
internal class ApprovedStreamingSpeechAssembler(
    private val maxBufferedChars: Int = 2_400
) {
    data class Candidate(
        val sequence: Int,
        val rawText: String,
        val speakableText: String
    )

    init { require(maxBufferedChars in 256..8_000) }

    private val buffer = StringBuilder()
    private var sequence = 0
    private var fenced: String? = null
    private var inlineCode = false

    fun append(delta: String): List<Candidate> {
        if (delta.isEmpty()) return emptyList()
        if (buffer.length + delta.length > maxBufferedChars) {
            reset()
            return emptyList()
        }
        buffer.append(delta)
        return drainCompletedSentences()
    }

    /** Final model completion may expose one last sentence without punctuation. */
    fun flushFinal(): Candidate? {
        val raw = buffer.toString().trim()
        val insideCode = fenced != null || inlineCode
        resetBufferOnly()
        if (raw.isEmpty() || insideCode) return null
        return candidate(raw)
    }

    fun reset() {
        resetBufferOnly()
        fenced = null
        inlineCode = false
        sequence = 0
    }

    private fun drainCompletedSentences(): List<Candidate> {
        val out = ArrayList<Candidate>()
        var scan = 0
        var sentenceStart = 0
        while (scan < buffer.length) {
            when {
                buffer.startsWith(TICK_FENCE, scan) -> {
                    fenced = if (fenced == TICK_FENCE) null else if (fenced == null) TICK_FENCE else fenced
                    scan += 3
                    continue
                }
                buffer.startsWith(TILDE_FENCE, scan) -> {
                    fenced = if (fenced == TILDE_FENCE) null else if (fenced == null) TILDE_FENCE else fenced
                    scan += 3
                    continue
                }
                fenced == null && buffer[scan].code == 96 -> {
                    inlineCode = !inlineCode
                    scan++
                    continue
                }
            }
            if (fenced == null && !inlineCode && isSentenceBoundary(scan)) {
                val end = scan + 1
                val raw = buffer.substring(sentenceStart, end).trim()
                if (raw.isNotEmpty()) candidate(raw)?.let(out::add)
                sentenceStart = end
            }
            scan++
        }
        if (sentenceStart > 0) {
            buffer.delete(0, sentenceStart)
            fenced = null
            inlineCode = false
            recomputeCodeState()
        }
        return out
    }

    private fun recomputeCodeState() {
        var i = 0
        while (i < buffer.length) {
            when {
                buffer.startsWith(TICK_FENCE, i) -> {
                    fenced = if (fenced == TICK_FENCE) null else if (fenced == null) TICK_FENCE else fenced
                    i += 3
                }
                buffer.startsWith(TILDE_FENCE, i) -> {
                    fenced = if (fenced == TILDE_FENCE) null else if (fenced == null) TILDE_FENCE else fenced
                    i += 3
                }
                fenced == null && buffer[i].code == 96 -> {
                    inlineCode = !inlineCode
                    i++
                }
                else -> i++
            }
        }
    }

    private fun isSentenceBoundary(index: Int): Boolean {
        val c = buffer[index]
        if (c !in charArrayOf('.', '!', '?', '…')) return false
        if (c == '.' && looksLikeAbbreviationOrDecimal(index)) return false
        val next = index + 1
        return next >= buffer.length || buffer[next].isWhitespace()
    }

    private fun looksLikeAbbreviationOrDecimal(index: Int): Boolean {
        if (index > 0 && index + 1 < buffer.length &&
            buffer[index - 1].isDigit() && buffer[index + 1].isDigit()) return true
        val prefix = buffer.substring(0, index + 1)
        return ABBREVIATIONS.any { prefix.endsWith(it, ignoreCase = true) }
    }

    private fun candidate(raw: String): Candidate? {
        val speech = JarvisVoiceProfile.speakableChunks(raw, chunkCharacters = 480, maxChunks = 2)
        if (speech.pieces.isEmpty() || speech.truncated) return null
        val cleaned = speech.pieces.joinToString(" ").trim()
        if (cleaned.isEmpty()) return null
        return Candidate(++sequence, raw, cleaned)
    }

    private fun resetBufferOnly() {
        buffer.setLength(0)
    }

    companion object {
        private val TICK_FENCE = CharArray(3) { 96.toChar() }.concatToString()
        private const val TILDE_FENCE = "~~~"
        private val ABBREVIATIONS = listOf(
            "z. B.", "bzw.", "ca.", "inkl.", "ggf.", "Dr.", "Mag.", "Prof.", "Nr.", "Art.", "Abs.", "usw.", "etc."
        )
    }
}
