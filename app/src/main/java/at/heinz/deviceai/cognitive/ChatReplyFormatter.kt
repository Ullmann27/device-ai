package at.heinz.deviceai.cognitive

/**
 * Code55 field finding (Fold7 screenshot 12.11.2026, Code54): local model replies showed raw Markdown in the chat
 * bubble („### Was ich tun kann“, „**Informationen abrufen**“, „`get_device_info`“). The bubble is plain text, so
 * the markers are removed for display and speech; headings stay as their text, list items become „•“. Fenced code
 * blocks are left untouched (coding answers need them). Pure, deterministic, no model call.
 */
internal object ChatReplyFormatter {
    private val heading = Regex("^(\\s*)#{1,6}\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[*+-]\\s+(.*)$")
    private val bold = Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1")
    private val inlineCode = Regex("`([^`\\n]{1,120})`")

    fun plain(text: String): String {
        if (text.isEmpty()) return text
        var inFence = false
        return text.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith("```")) { inFence = !inFence; return@joinToString line }
            if (inFence) return@joinToString line
            var out = line
            heading.matchEntire(out)?.let { out = it.groupValues[1] + it.groupValues[2] }
            bullet.matchEntire(out)?.let { out = it.groupValues[1] + "• " + it.groupValues[2] }
            out = out.replace(bold, "$2").replace(inlineCode, "$1")
            out
        }
    }
}
