package at.heinz.deviceai.localmodel

/**
 * Called under the runtime lifecycle lock while READY.
 *
 * Every new full chat prompt closes a used native conversation; tool responses
 * alone can continue the active native conversation. A just-created, *unused*
 * conversation from model load may serve the first full prompt without native
 * close/create churn. Never set reusePristineOnFirstPrompt for an already
 * dispatched, cancelled, tool-bearing or previous-owner conversation.
 */
internal fun <T : Any> selectLocalConversation(
    previous: T?, freshPrompt: Boolean, create: () -> T, close: (T) -> Unit,
    reusePristineOnFirstPrompt: Boolean = false
): T {
    if (!freshPrompt) return checkNotNull(previous) { "No active tool conversation." }
    if (reusePristineOnFirstPrompt)
        return checkNotNull(previous) { "No unused startup conversation." }
    previous?.let(close) // Fail closed; never use a conversation whose release failed.
    return create()
}
