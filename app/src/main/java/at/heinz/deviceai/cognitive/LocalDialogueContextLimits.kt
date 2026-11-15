package at.heinz.deviceai.cognitive

/** Storage bounds, not inference grants. */
internal object LocalDialogueContextLimits {
    const val MAX_OWNER_CHARACTERS = 11_500
    const val MAX_RESPONSE_CHARACTERS = 20_000
    const val MAX_TURNS = 4

    fun completeOwner(text: String): Boolean = text.isNotBlank() &&
        text.length <= MAX_OWNER_CHARACTERS &&
        text.toByteArray(Charsets.UTF_8).size <= MAX_OWNER_CHARACTERS

    fun responseTail(text: String): String {
        var start = (text.length - MAX_RESPONSE_CHARACTERS).coerceAtLeast(0)
        if (start > 0 && text[start].isLowSurrogate() && text[start - 1].isHighSurrogate()) start++
        return text.substring(start)
    }
}
