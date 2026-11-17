package at.heinz.deviceai.cognitive

import java.util.Locale

/** Bounds UTF-16 output, including the marker, without splitting replacement tokens. */
internal object BoundedResearchTextSanitizer {
    private const val TRUNCATED = " [TRUNCATED]"
    private val roles = arrayOf("system", "developer", "assistant", "user")
    // Harmless display escapes are stable on a second pass. These strings are
    // formatting only, never authenticated source metadata or model control tokens.
    private val displayMarkers = Regex("\\[(?:quoted (?:role|marker)|TRUNCATED|U\\+[0-9A-F]{4,6})]")

    fun sanitize(text: String, maxCharacters: Int): String {
        require(maxCharacters >= 0)
        if (maxCharacters == 0 || text.isEmpty()) return ""
        var end = minOf(text.length, maxCharacters)
        if (end < text.length && end > 0 && Character.isHighSurrogate(text[end - 1]) &&
            Character.isLowSurrogate(text[end])) end--
        val result = StringBuilder()
        val tokenStarts = ArrayList<Int>()
        var cursor = 0
        while (cursor < end) {
            val roleEnd = roleEnd(text, cursor, end)
            val displayMarker = if (text[cursor] == '[') displayMarkers.matchAt(text, cursor)
                ?.takeIf { it.range.last < end } else null
            val next: Int
            val token: String
            if (roleEnd != null) {
                token = "[quoted role]:"
                next = roleEnd
            } else if (displayMarker != null) {
                token = displayMarker.value
                next = displayMarker.range.last + 1
            } else if (cursor + 3 <= end && text.startsWith("<<<", cursor)) {
                token = "[quoted marker]"
                next = cursor + 3
            } else {
                val cp = text.codePointAt(cursor)
                next = cursor + Character.charCount(cp)
                token = when {
                    Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT.toInt() ||
                        Character.getType(cp) == Character.SURROGATE.toInt() ->
                        "[U+${cp.toString(16).uppercase(Locale.ROOT).padStart(4, '0')}]"
                    Character.getType(cp) == Character.LINE_SEPARATOR.toInt() ||
                        Character.getType(cp) == Character.PARAGRAPH_SEPARATOR.toInt() -> " "
                    cp == '<'.code -> "‹"
                    cp == '>'.code -> "›"
                    cp == '['.code -> "［"
                    cp == ']'.code -> "］"
                    else -> String(Character.toChars(cp))
                }
            }
            if (token.length > maxCharacters - result.length) break
            tokenStarts.add(result.length)
            result.append(token)
            cursor = next
        }
        if (cursor < text.length) {
            val marker = if (maxCharacters >= TRUNCATED.length) TRUNCATED else "~"
            while (result.length > maxCharacters - marker.length) {
                result.setLength(tokenStarts.removeAt(tokenStarts.lastIndex))
            }
            result.append(marker)
        }
        check(result.length <= maxCharacters)
        return result.toString()
    }

    private fun roleEnd(text: String, start: Int, end: Int): Int? {
        if (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) return null
        for (role in roles) {
            if (start + role.length > end || !text.regionMatches(start, role, 0, role.length, true)) continue
            var position = start + role.length
            while (position < end && text[position].isWhitespace()) position++
            if (position < end && text[position] == ':') return position + 1
        }
        return null
    }
}
