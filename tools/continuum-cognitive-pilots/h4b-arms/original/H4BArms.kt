package at.heinz.deviceai.cognitive

/** Arm factory compiled against the unchanged source taken from a git ref. */
internal object H4BArms {
    fun create(arm: String): LocalConversationSituationTracker = when (arm) {
        "original" -> LocalConversationSituationTracker()
        else -> error("Arm $arm is not available on the original source")
    }
}
