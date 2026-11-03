package at.heinz.deviceai.cognitive

/**
 * RED stub (Fold7 continuity): the ephemeral dialogue is still created per access,
 * i.e. per Activity instance, and is therefore lost on every Activity recreation.
 */
object LocalDialogueRetention {
    data class RetainedReply(val sessionId: String, val request: String, val response: String)

    val context: LocalConversationContext get() = LocalConversationContext()

    fun retainReply(reply: RetainedReply?) { }
    fun replyFor(sessionId: String): RetainedReply? = null

    /** changingConfigurations = Activity.isChangingConfigurations at onDestroy. */
    fun activityDestroyed(changingConfigurations: Boolean) { }
}
