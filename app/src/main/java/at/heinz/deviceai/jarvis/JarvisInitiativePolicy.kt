package at.heinz.deviceai.jarvis

/** Advisory decision only: no OS notifications, Android Auto APIs or speech are invoked. */
enum class JarvisInitiativeDeliveryMode { SPEAK_WHEN_ENDPOINT_AUTHORIZED, QUEUE_SILENTLY, SUPPRESS }

data class JarvisInitiativeDecision(
    val delivery: JarvisInitiativeDeliveryMode,
    val reasonCode: String,
    /** Uncalibrated research score, NEVER a probability of fire or actual medical/safety risk. */
    val heuristicScore: Int
)

/**
 * Deliberately conservative initiative policy. The driving threshold is higher, and a merely unknown
 * occupancy status never causes unsolicited audio while driving. Genuine emergencies must be handled
 * by certified local safety devices independently of this app.
 */
object JarvisInitiativePolicy {
    fun evaluate(
        driving: Boolean,
        endpoint: JarvisActiveEndpoint,
        occupancy: HouseholdOccupancyState,
        occupancyVerified: Boolean,
        hazardousDeviceActive: Boolean,
        telemetryVerified: Boolean
    ): JarvisInitiativeDecision {
        if (!hazardousDeviceActive || !telemetryVerified) return JarvisInitiativeDecision(
            JarvisInitiativeDeliveryMode.SUPPRESS, "UNVERIFIED_OR_INACTIVE", 0)
        if (occupancyVerified && occupancy in setOf(
                HouseholdOccupancyState.OWNER_AT_HOME, HouseholdOccupancyState.OWNER_AWAY_OTHERS_PRESENT))
            return JarvisInitiativeDecision(JarvisInitiativeDeliveryMode.SUPPRESS, "HOUSE_OCCUPIED", 0)

        val verifiedEmpty = occupancyVerified && occupancy == HouseholdOccupancyState.HOUSE_CONFIRMED_EMPTY
        // These are fixed thresholds for experimental triage, not computed real-world hazard probabilities.
        val riskScore = if (verifiedEmpty) 90 else 45
        val interruptionCost = if (driving || endpoint == JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO) 70 else 25
        val decision = if (riskScore >= interruptionCost && verifiedEmpty)
            JarvisInitiativeDeliveryMode.SPEAK_WHEN_ENDPOINT_AUTHORIZED
        else JarvisInitiativeDeliveryMode.QUEUE_SILENTLY
        return JarvisInitiativeDecision(decision,
            if (verifiedEmpty) "UNATTENDED_ACTIVE_DEVICE" else "OCCUPANCY_UNCONFIRMED",
            riskScore - interruptionCost)
    }
}
