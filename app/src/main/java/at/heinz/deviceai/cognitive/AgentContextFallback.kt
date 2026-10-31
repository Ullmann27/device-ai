package at.heinz.deviceai.cognitive

/**
 * Safe fallback when the trusted context compiler is unavailable.
 *
 * Intentionally has no MemoryRecord or external-context input parameter:
 * no unfiltered memory, old prompt, source document, or tool output can
 * enter a model prompt through this emergency branch.
 */
object AgentContextFallback {
    fun render(reflection: AgentReflection?): String = buildString {
        append("Trusted memory context unavailable: no memory facts are supplied. ")
        append("Do not claim remembered owner facts or infer missing evidence.\n")
        reflection?.let {
            append("Unverified previous evaluation correction: ")
            append(it.correction.take(512))
        }
    }
}
