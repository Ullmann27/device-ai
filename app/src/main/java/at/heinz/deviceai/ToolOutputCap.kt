package at.heinz.deviceai

/**
 * Tool output cap: a tool result fed back to the model is limited to [MAX_CHARS] measured in
 * JSON-escaped length (control chars count 6, quotes/backslashes 2), so even worst-case escaping
 * stays far below the 8000-char agent/observation limit that previously overflowed. Cuts never
 * split a UTF-16 surrogate pair and end with a clear German marker naming the original length.
 */
object ToolOutputCap {
    const val MAX_CHARS = 3_900

    fun marker(originalLength: Int) = "\n…[gekürzt: Werkzeugausgabe hatte $originalLength Zeichen, Limit $MAX_CHARS]"

    private fun escapedWeight(c: Char): Int = when {
        c == '"' || c == '\\' || c == '/' -> 2
        c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\u000C' -> 2
        c < ' ' || c in '\u0080'..'\u00a0' || c in '\u2000'..'\u2100' -> 6
        else -> 1
    }

    fun cap(output: String): String {
        var weight = 0
        for (c in output) weight += escapedWeight(c)
        if (weight <= MAX_CHARS && output.length <= MAX_CHARS) return output
        val mark = marker(output.length)
        val budget = MAX_CHARS - mark.length - 32 // marker itself is escaped too ("/" never, "\n" -> 2)
        var used = 0
        var end = 0
        while (end < output.length) {
            val w = escapedWeight(output[end])
            if (used + w > budget) break
            used += w; end++
        }
        if (end > 0 && Character.isHighSurrogate(output[end - 1])) end--
        return output.substring(0, end) + mark
    }
}
