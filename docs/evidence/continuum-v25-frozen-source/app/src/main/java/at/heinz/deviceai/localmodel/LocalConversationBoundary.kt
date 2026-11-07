package at.heinz.deviceai.localmodel

/** Called under the runtime lifecycle lock while READY. Full prompts carry all
 * permitted context. Only tool replies continue a previous native conversation. */
internal fun <T : Any> selectLocalConversation(
    previous: T?, freshPrompt: Boolean, create: () -> T, close: (T) -> Unit
): T {
    if (!freshPrompt) return checkNotNull(previous) { "No active tool conversation." }
    previous?.let(close) // Fail closed; never keep using a conversation whose release failed.
    return create()
}
