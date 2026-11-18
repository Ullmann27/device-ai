package at.heinz.deviceai.cognitive

/**
 * Incremental sentence boundary detector for future early TTS.
 *
 * It never approves speech. It only exposes complete prose candidates. Callers must
 * pass each candidate through their existing quality/evidence gate before appending
 * it to ApprovedSpeechChunkQueue. Raw TextDelta output must never be spoken.
 *
 * A boundary is proposed eagerly (terminal mark plus following whitespace). Whether the
 * following text confirms it is the caller's decision; see StreamingSpeechSegmenter.
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

    /** True once text was dropped because no sentence ended within the buffer bound. */
    var overflowed = false
        private set

    fun append(delta: String): List<Candidate> {
        if (delta.isEmpty()) return emptyList()
        if (buffer.length + delta.length > maxBufferedChars) {
            // Fail closed, but keep sequence numbers monotonic so a later candidate is never
            // mistaken for an earlier one.
            resetBufferOnly()
            fenced = null
            inlineCode = false
            overflowed = true
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
        fenced = null
        inlineCode = false
        if (raw.isEmpty() || insideCode) return null
        return candidate(raw)
    }

    /** Text received after the last proposed boundary (not yet a candidate). */
    fun pendingText(): String = buffer.toString()

    fun reset() {
        resetBufferOnly()
        fenced = null
        inlineCode = false
        sequence = 0
        overflowed = false
    }

    private fun drainCompletedSentences(): List<Candidate> {
        val out = ArrayList<Candidate>()
        // The retained suffix is rescanned from index 0 on every append. Rebuild code
        // state from that same origin instead of carrying end-state into a fresh scan.
        fenced = null
        inlineCode = false
        // Closers still expected for quotes and brackets opened in the current sentence.
        val open = ArrayList<String>()
        var openedAt = 0
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
            if (fenced == null && !inlineCode) {
                if (open.isEmpty()) openedAt = scan
                trackPair(scan, open)
                // A stray opener must not suppress boundaries for the rest of the turn.
                if (open.isNotEmpty() && (scan - openedAt > MAX_OPEN_SPAN || paragraphBreak(scan))) open.clear()
                if (open.isEmpty() && (isSentenceBoundary(scan, sentenceStart) || isLineBoundary(scan, sentenceStart))) {
                    val end = scan + 1
                    val raw = buffer.substring(sentenceStart, end).trim()
                    if (raw.isNotEmpty()) candidate(raw)?.let(out::add)
                    sentenceStart = end
                }
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

    /** „…“, “…”, "…", »…«, «…», (…) and […]: a sentence does not end inside an open pair. */
    private fun trackPair(index: Int, open: MutableList<String>) {
        val c = buffer[index]
        val expected = open.lastOrNull()
        if (expected != null && c in expected) {
            open.removeAt(open.lastIndex)
            return
        }
        when (c) {
            '„' -> open.add("“”\"")
            '“' -> open.add("”\"")
            '"' -> open.add("\"”“")
            '»' -> open.add("«")
            '«' -> open.add("»")
            '[' -> open.add("]")
            // ":(" and ";-(" are emoticons, not an opening bracket.
            '(' -> if (index == 0 || buffer[index - 1] !in ":;=-") open.add(")")
        }
    }

    private fun paragraphBreak(index: Int): Boolean =
        buffer[index] == '\n' && index > 0 && buffer[index - 1] == '\n'

    private fun isSentenceBoundary(index: Int, sentenceStart: Int): Boolean {
        // Closing quotes and brackets belong to the sentence they end: „Komm!“ Dann …
        var terminal = index
        while (terminal > sentenceStart && buffer[terminal] in CLOSERS) terminal--
        val c = buffer[terminal]
        if (c !in TERMINALS) return false
        val next = index + 1
        if (next >= buffer.length) return false
        if (c == '.' && looksLikeAbbreviationOrDecimal(terminal, next)) return false
        if (!buffer[next].isWhitespace()) return false
        if (terminal == index) return true
        // After a closing quote or bracket the sentence often goes on („Halt!“ rief er): wait for
        // the continuation and accept the boundary only if it does not start in lower case.
        var following = next
        while (following < buffer.length && buffer[following].isWhitespace()) following++
        return following < buffer.length && !(buffer[following].isLetter() && buffer[following].isLowerCase())
    }

    /**
     * A line end closes a list item or heading even without punctuation; otherwise a list of
     * bullet points would stay one unbounded run-on text. An ordinary line break inside a
     * paragraph is not a boundary.
     */
    private fun isLineBoundary(index: Int, sentenceStart: Int): Boolean {
        if (buffer[index] != '\n') return false
        var contentStart = sentenceStart
        while (contentStart < index && buffer[contentStart].isWhitespace()) contentStart++
        if (contentStart >= index) return false
        var next = index + 1
        if (next >= buffer.length) return false
        if (buffer[next] == '\n') return true
        while (next < buffer.length && (buffer[next] == ' ' || buffer[next] == '\t')) next++
        // Wait until the start of the next line is long enough to be classified.
        if (next + LINE_MARKER_LOOKAHEAD > buffer.length) return false
        return listMarkerAt(contentStart, index) || listMarkerAt(next, buffer.length)
    }

    private fun listMarkerAt(start: Int, end: Int): Boolean {
        if (start >= end) return false
        val c = buffer[start]
        if (c == '#') return true
        if (c in "-*•–" ) return start + 1 < end && buffer[start + 1] == ' '
        var i = start
        while (i < end && i - start < 3 && buffer[i].isDigit()) i++
        return i > start && i + 1 < end && buffer[i] in ".)" && buffer[i + 1] == ' '
    }

    private fun looksLikeAbbreviationOrDecimal(index: Int, lookahead: Int): Boolean {
        if (index > 0 && lookahead < buffer.length &&
            buffer[index - 1].isDigit() && buffer[lookahead].isDigit()) return true
        // Ordinals, dates and enumerations: "am 3. Mai", "17.11. war", "1. Erstens". A one- or
        // two-digit number before the dot never proves a sentence end.
        var digitStart = index
        while (digitStart > 0 && buffer[digitStart - 1].isDigit()) digitStart--
        if (index - digitStart in 1..2) return true
        val prefix = buffer.substring(0, index + 1)
        if (ABBREVIATIONS.any { abbreviation ->
                prefix.endsWith(abbreviation, ignoreCase = true) &&
                    (prefix.length == abbreviation.length ||
                        !prefix[prefix.length - abbreviation.length - 1].isLetterOrDigit())
            }) return true
        // First dot of forms like "z. B.": one-letter token + whitespace + one-letter token + dot.
        var tokenStart = index - 1
        while (tokenStart > 0 && buffer[tokenStart - 1].isLetter()) tokenStart--
        if (index - tokenStart == 1) {
            var next = lookahead
            while (next < buffer.length && buffer[next].isWhitespace()) next++
            if (next + 1 >= buffer.length) return true
            if (buffer[next].isLetter() && buffer[next + 1] == '.') return true
        }
        return false
    }

    private fun candidate(raw: String): Candidate? {
        // The buffer bound keeps every candidate far below this chunk capacity: nothing is cut off.
        val speech = JarvisVoiceProfile.speakableChunks(raw, chunkCharacters = 480, maxChunks = 64)
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
        private const val TERMINALS = ".!?…"
        private const val CLOSERS = "“”\"«»)]"
        private const val MAX_OPEN_SPAN = 400
        private const val LINE_MARKER_LOOKAHEAD = 4
        private val ABBREVIATIONS = listOf(
            "z. B.", "z.B.", "d. h.", "d.h.", "u. a.", "u.a.", "o. ä.", "o.ä.", "u. U.", "z. T.", "i. d. R.",
            "bzw.", "bspw.", "ca.", "inkl.", "exkl.", "ggf.", "evtl.", "vgl.", "sog.", "bzgl.", "usw.", "etc.",
            "Dr.", "Mag.", "Prof.", "Dipl.", "Ing.", "Hr.", "Fr.", "Nr.", "Art.", "Abs.", "Abb.", "Kap.",
            "Tel.", "Str.", "St.", "Mio.", "Mrd.", "max.", "min.", "Std.", "Sek.", "Jh.", "Chr.", "geb.",
            "Fa.", "Co.", "vs.", "Mr.", "Mrs.", "e.g.", "i.e."
        )
    }
}
