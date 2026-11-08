package at.heinz.deviceai.cognitive

/** Arm factory compiled against the worktree; ablations only change the normalization policy. */
internal object H4BArms {
    private val default = ConversationSituationModel.CueNormalization.DEFAULT

    fun create(arm: String): LocalConversationSituationTracker = when (arm) {
        "candidate" -> LocalConversationSituationTracker()
        "ablation-prefix3" -> LocalConversationSituationTracker(default.copy(prefixMin = 3))
        "ablation-truncate-only" -> LocalConversationSituationTracker(default.copy(foldUmlauts = false, prefixMin = null))
        else -> error("Unknown arm: $arm")
    }
}
