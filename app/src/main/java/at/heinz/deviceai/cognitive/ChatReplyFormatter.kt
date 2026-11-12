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
    // Review #791: inline code is protected before bold handling, and "__…__" is bold only when its content is text
    // with a space – identifiers like __init__ or __name__ stay intact.
    private val bold = Regex("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*|(?<!\\w)__(?=\\S)([^_]*\\s[^_]*?)(?<=\\S)__(?!\\w)")
    private val inlineCode = Regex("`([^`\\n]{1,120})`")

    private fun formatOutsideCode(line: String): String {
        val out = StringBuilder()
        var last = 0
        for (code in inlineCode.findAll(line)) {
            out.append(line.substring(last, code.range.first).replace(bold) { it.groupValues[1].ifEmpty { it.groupValues[2] } })
            out.append(code.groupValues[1])
            last = code.range.last + 1
        }
        out.append(line.substring(last).replace(bold) { it.groupValues[1].ifEmpty { it.groupValues[2] } })
        return out.toString()
    }

    fun plain(text: String): String {
        if (text.isEmpty()) return text
        var inFence = false
        return text.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith("```")) { inFence = !inFence; return@joinToString line }
            if (inFence) return@joinToString line
            var out = line
            heading.matchEntire(out)?.let { out = it.groupValues[1] + it.groupValues[2] }
            bullet.matchEntire(out)?.let { out = it.groupValues[1] + "• " + it.groupValues[2] }
            out = formatOutsideCode(out)
            out
        }
    }
}
