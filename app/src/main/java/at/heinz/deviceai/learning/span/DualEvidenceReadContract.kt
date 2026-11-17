package at.heinz.deviceai.learning.span

import at.heinz.deviceai.cognitive.NetworkTurnConstraint

/** Read-only planning boundary; never fetches, dispatches a tool, persists or confirms a fact. */
object DualEvidenceReadContract {
    enum class Mode { OFF, ASK, ALWAYS_ON }
    enum class FetchPlan { LOCAL_ONLY, OFFLINE_VETO, NO_NETWORK, PERMISSION_REQUIRED, ASK_OWNER, READ_ONLY_ELIGIBLE }
    enum class Origin { EWM_LOCAL_VIEW, ARCHIVE_OBSERVATION, WEB_OBSERVATION }

    fun plan(userTurn: String, mode: Mode = Mode.OFF, networkOnline: Boolean,
        networkPermissionGranted: Boolean, askApprovedForThisTurn: Boolean = false): FetchPlan = when {
        NetworkTurnConstraint.forbidsExternalResearch(userTurn) -> FetchPlan.OFFLINE_VETO
        mode == Mode.OFF -> FetchPlan.LOCAL_ONLY
        !networkOnline -> FetchPlan.NO_NETWORK
        !networkPermissionGranted -> FetchPlan.PERMISSION_REQUIRED
        mode == Mode.ASK && !askApprovedForThisTurn -> FetchPlan.ASK_OWNER
        else -> FetchPlan.READ_ONLY_ELIGIBLE
    }

    /** Caller supplies existing immutable EWM/archive views, not another knowledge store. */
    data class Lease(val snapshot: LocationEvidenceSnapshot, val origin: Origin,
        val observedAtMillis: Long, val expiresAtMillis: Long) {
        init {
            require(observedAtMillis >= 0 && expiresAtMillis > observedAtMillis)
            require(expiresAtMillis - observedAtMillis <= 3_600_000)
        }
        // A source label/hash never creates owner-fact authority.
        val grantsOwnerAuthority: Boolean get() = false
        fun isCurrent(nowMillis: Long, current: LocationEvidenceSnapshot): Boolean =
            nowMillis >= observedAtMillis && nowMillis < expiresAtMillis && snapshot.sha256 == current.sha256
    }
}
