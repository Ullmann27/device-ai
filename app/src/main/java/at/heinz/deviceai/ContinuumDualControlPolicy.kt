package at.heinz.deviceai

/** Policy only: does not issue OS permissions, execute tools, or sign releases. */
enum class ContinuumControlProfile { PROTECTED, OWNER_AUTONOMOUS }
enum class ContinuumControlDecision { ALLOW, REQUIRE_OWNER_REVIEW, DENY }

data class ContinuumToolGrant(
    val ownerAuthenticated: Boolean,
    val sessionActive: Boolean,
    val androidPermissionGranted: Boolean,
    val allowedToolNames: Set<String>,
    val reusableConsentToolNames: Set<String> = emptySet(),
    val protectedBoundary: Boolean = false,
    val externalSideEffect: Boolean = false
)

object ContinuumDualControlPolicy {
    /** REQUIRE_OWNER_REVIEW is not authorization. Existing ToolGate and
     * Android permissions must still be independently enforced by the caller. */
    fun evaluate(
        profile: ContinuumControlProfile,
        name: String,
        grant: ContinuumToolGrant
    ): ContinuumControlDecision {
        val spec = ToolRegistry.get(name) ?: return ContinuumControlDecision.DENY
        if (!grant.ownerAuthenticated || !grant.sessionActive ||
            !grant.androidPermissionGranted || name !in grant.allowedToolNames) {
            return ContinuumControlDecision.DENY
        }
        if (grant.protectedBoundary || spec.risk == Risk.DESTRUCTIVE ||
            grant.externalSideEffect) {
            return ContinuumControlDecision.REQUIRE_OWNER_REVIEW
        }
        if (spec.risk == Risk.READ_ONLY) return ContinuumControlDecision.ALLOW
        if (profile == ContinuumControlProfile.OWNER_AUTONOMOUS &&
            name in grant.reusableConsentToolNames) {
            return ContinuumControlDecision.ALLOW
        }
        return ContinuumControlDecision.REQUIRE_OWNER_REVIEW
    }
}
