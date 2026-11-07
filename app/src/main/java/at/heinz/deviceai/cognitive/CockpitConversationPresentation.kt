package at.heinz.deviceai.cognitive

/**
 * Presentation-only decision. Voice ownership never follows a text-tab view.
 * This class must not change microphone, model, memory or tool permissions.
 */
internal object CockpitConversationPresentation {
    fun showChat(
        explicitChatSelected: Boolean,
        conversationActive: Boolean,
        keyboardCompact: Boolean,
        handsFreeActive: Boolean,
        pinHomeCockpit: Boolean
    ): Boolean {
        if (handsFreeActive && pinHomeCockpit && !explicitChatSelected) return false
        return explicitChatSelected || conversationActive || keyboardCompact
    }

    fun keepHomeForResult(
        handsFreeActive: Boolean,
        pinHomeCockpit: Boolean,
        homeSelected: Boolean
    ): Boolean = handsFreeActive && pinHomeCockpit && homeSelected
}
