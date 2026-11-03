package at.heinz.deviceai.localmodel

/**
 * One owner turn is bound to the exact model (id, version, weight hash) that was
 * loaded when it started. Same invariant as the research adapter
 * ContinuumLocalAgentModel, applied to the live runLocal AgentModel.
 */
object LocalTurnModelBinding {
    const val CHANGED = "LOCAL_MODEL_CHANGED_DURING_TURN"

    fun stillBound(started: ImportedLocalModel, current: ImportedLocalModel?): Boolean =
        current != null && current.id == started.id && current.version == started.version &&
            current.sha256 == started.sha256
}
