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

    /**
     * Reparenting a live hologram detaches its TextureView. Keep the native surface mounted
     * while hands-free owns the cockpit or native 3D is enabled.
     */
    fun mayReparentHologram(handsFreeActive: Boolean, native3dEnabled: Boolean): Boolean =
        !handsFreeActive && !native3dEnabled
}
