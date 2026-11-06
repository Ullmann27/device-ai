package at.heinz.deviceai.cognitive

/** Acoustic styling for an installed offline Android TTS voice.
 * This does not clone, impersonate, synthesize or ship an actor's voice.
 * Pitch stays at the engine's natural 1.0: Android's setPitch resamples the engine output, so any shift makes the
 * owner-chosen (female) voice sound processed. Natural sound comes from the chosen voice, not from post-processing. */
internal object JarvisVoiceProfile {
    const val pitch = 1.0f
    const val speechRate = 0.96f

    private val codeFence = Regex("(?s)(?:```|~~~)[^\n]*\n.*?(?:```|~~~)")
    private val unfinishedFence = Regex("(?s)(?:```|~~~).*$")
    private val inlineCode = Regex("(?s)`[^`]*`")
    private val unfinishedInlineCode = Regex("(?s)`.*$")
    private val url = Regex("(?i)https?://\\S+")
    private val markdownLinks = Regex("\\[([^\\]]{1,100})\\]\\(https?://[^)]*\\)")
    private val punctuation = Regex("[*_#`>]")
    private val whitespace = Regex("\\s+")

    /** Spoken form of [text]: code, links and markup are replaced, control characters removed. */
    private fun clean(text: String): String = text
            .replace(codeFence, " Codebeispiel ausgelassen. ")
            .replace(unfinishedFence, " Nicht abgeschlossenen Codeblock ausgelassen. ")
            .replace(inlineCode, " Codebeispiel ausgelassen. ")
            .replace(unfinishedInlineCode, " Unvollständigen Code ausgelassen. ")
            .replace(markdownLinks, "$1")
            .replace(url, " Link ausgelassen. ")
            .replace(punctuation, " ")
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(whitespace, " ")
            .trim()

    /** Length of the first spoken piece of [cleaned]: sentence end, else word end, else hard cut. */
    private fun boundary(cleaned: String, maxCharacters: Int): Int {
        if (cleaned.length <= maxCharacters) return cleaned.length
        val prefix = cleaned.take(maxCharacters)
        val lastSentence = listOf(prefix.lastIndexOf(". "), prefix.lastIndexOf("! "), prefix.lastIndexOf("? "))
            .maxOrNull() ?: -1
        return if (lastSentence >= maxCharacters / 3) lastSentence + 1
            else prefix.lastIndexOf(' ').takeIf { it >= maxCharacters / 2 } ?: maxCharacters
    }

    /** First speakable piece only (kept for callers that want a short preview). */
    fun speakable(text: String, maxCharacters: Int = 480): String? {
        require(maxCharacters in 80..800)
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return null
        return cleaned.take(boundary(cleaned, maxCharacters)).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * The whole reply as ordered TTS pieces of at most [chunkCharacters], split at sentence (else word) ends, so a
     * long answer is read completely (Fold7 report #566: speech stopped after the first 480 characters). Bounded by
     * [maxChunks] (the approved speech queue's capacity); text beyond it is not spoken and [Chunks.truncated] says so.
     */
    data class Chunks(val pieces: List<String>, val truncated: Boolean)

    fun speakableChunks(text: String, chunkCharacters: Int = 480, maxChunks: Int = 16): Chunks {
        require(chunkCharacters in 80..800 && maxChunks in 1..64)
        var rest = clean(text)
        val pieces = ArrayList<String>()
        while (rest.isNotEmpty() && pieces.size < maxChunks) {
            val end = boundary(rest, chunkCharacters)
            rest.take(end).trim().takeIf { it.isNotEmpty() }?.let(pieces::add)
            rest = rest.drop(end).trim()
        }
        return Chunks(pieces, rest.isNotEmpty())
    }
}
