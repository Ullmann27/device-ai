package at.heinz.deviceai.cognitive

/**
 * Process-scoped, RAM-only carrier of the ephemeral local dialogue across an
 * Activity recreation of the SAME chat session.
 *
 * Fold7: AgentActivity handles only size/orientation/layout changes itself. A
 * density change between cover and inner display, a theme (uiMode), font-scale,
 * locale or keyboard change still destroys and recreates the Activity. The chat
 * session id is restored from the saved state, so the owner continues the same
 * conversation, but a per-instance LocalConversationContext was empty again.
 *
 * Boundaries (unchanged from LocalConversationContext): never written to disk,
 * never a memory, fact proof or tool grant; a different session id still clears
 * it; a real finish (not a configuration change) forgets everything. If Android
 * kills the process, the dialogue is gone as before.
 */
object LocalDialogueRetention {
    data class RetainedReply(val sessionId: String, val request: String, val response: String)

    /** One instance per process; LocalConversationContext is internally synchronized. */
    val context: LocalConversationContext = LocalConversationContext()

    @Volatile private var reply: RetainedReply? = null

    fun retainReply(value: RetainedReply?) { reply = value }

    fun replyFor(sessionId: String): RetainedReply? = reply?.takeIf { it.sessionId == sessionId }

    /** changingConfigurations = Activity.isChangingConfigurations at onDestroy. */
    fun activityDestroyed(changingConfigurations: Boolean) {
        if (changingConfigurations) return
        reply = null
        context.forgetForExit()
    }
}
