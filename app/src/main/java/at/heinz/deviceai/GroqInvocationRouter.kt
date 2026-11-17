package at.heinz.deviceai

/**
 * Explicit opt-in prefixes for a Groq mentor question.
 * Returns null for every other chat message; calling code must always ask
 * for owner confirmation before invoking the remote endpoint.
 */
object GroqInvocationRouter {
    private val addressedGreeting = Regex(
        """^(?:hallo|hey|hi)\s+groq\b[\s,.:;!?-]*""",
        RegexOption.IGNORE_CASE
    )
    private val addressedName = Regex(
        """^groq\s*:\s*""",
        RegexOption.IGNORE_CASE
    )

    fun extractQuestion(text: String): String? {
        val message = text.trim()
        if (message.startsWith("mentor:", ignoreCase = true)) {
            return message.substringAfter(':').trim()
        }
        val prefix = addressedGreeting.find(message) ?: addressedName.find(message)
        return if (prefix != null) message.substring(prefix.range.last + 1).trim() else null
    }
}
