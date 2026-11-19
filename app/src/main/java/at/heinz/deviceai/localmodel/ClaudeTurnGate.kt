package at.heinz.deviceai.localmodel

import at.heinz.deviceai.cognitive.NetworkTurnConstraint

/**
 * Decides, before a turn is built, whether it may go to the cloud brain at all. It follows the app's existing
 * guards and only ever removes: with the local brain it never refuses anything.
 *
 *  - Owner veto: a message in which the owner excludes internet, network or cloud, or asks for a local/offline
 *    answer ([NetworkTurnConstraint.forbidsExternalSpeech], the same rule that keeps such a turn away from the
 *    network voice). Decision: the turn is refused, not answered by another model behind the owner's back.
 *  - Confirmed memory: a turn that needs a grounded recall from the owner's confirmed facts. Those records are
 *    classified DEVICE_LOCAL_ONLY (AgentActivity.ownerFactMemories), so they go to Claude only after the owner's
 *    separate opt-in ([ClaudeBrain.mayUseLocalOnlyMemory]).
 *
 * Screen content of other apps is refused inside the runtime ([ClaudeLocalOnlyGuard]); notification content never
 * reaches a model prompt in the first place.
 */
object ClaudeTurnGate {
    /** A [ClaudeBrainFailure] code when [ownerTurn] must not be sent to Claude, else null. */
    fun refusal(brain: ClaudeBrain?, ownerTurn: String, usesConfirmedMemory: Boolean): String? = when {
        brain == null || !brain.claudeSelected -> null
        NetworkTurnConstraint.forbidsExternalSpeech(ownerTurn) -> ClaudeBrainFailure.LOCAL_ONLY_OWNER_VETO
        usesConfirmedMemory && !brain.mayUseLocalOnlyMemory -> ClaudeBrainFailure.LOCAL_ONLY_MEMORY
        else -> null
    }

    /** Model reference recorded with an answer: a cloud answer is never labelled as an on-device model. */
    fun modelReference(model: ImportedLocalModel): String =
        if (ClaudeModelRuntime.isCloud(model)) "claude:${model.id}" else "litertlm:${model.id}@${model.version}"
}
