package at.heinz.deviceai.cognitive

/** Acoustic styling for an installed offline German Android TTS voice.
 * This does not clone, impersonate, synthesize or ship an actor's voice. */
internal object JarvisVoiceProfile {
    const val pitch = 0.86f
    const val speechRate = 0.93f

    private val codeFence = Regex("(?s)```[^\n]*\n.*?```")
    private val unfinishedFence = Regex("(?s)```.*$")
    private val inlineCode = Regex("`[^`\\n]{1,500}`")
    private val url = Regex("(?i)https?://\\S+")
    private val markdownLinks = Regex("\\[([^\\]]{1,100})\\]\\(https?://[^)]*\\)")
    private val punctuation = Regex("[*_#`>]")
    private val whitespace = Regex("\\s+")

    fun speakable(text: String, maxCharacters: Int = 480): String? {
        require(maxCharacters in 80..800)
        val cleaned = text
            .replace(codeFence, " Codebeispiel ausgelassen. ")
            .replace(unfinishedFence, " Nicht abgeschlossenen Codeblock ausgelassen. ")
            .replace(inlineCode, " Codebeispiel ausgelassen. ")
            .replace(markdownLinks, "$1")
            .replace(url, " Link ausgelassen. ")
            .replace(punctuation, " ")
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(whitespace, " ")
            .trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.length <= maxCharacters) return cleaned
        val prefix = cleaned.take(maxCharacters)
        val lastSentence = listOf(prefix.lastIndexOf(". "), prefix.lastIndexOf("! "), prefix.lastIndexOf("? "))
            .maxOrNull() ?: -1
        val boundary = if (lastSentence >= maxCharacters / 3) lastSentence + 1
            else prefix.lastIndexOf(' ').takeIf { it >= maxCharacters / 2 } ?: maxCharacters
        return prefix.take(boundary).trim().takeIf { it.isNotEmpty() }
    }
}
