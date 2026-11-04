package at.heinz.deviceai.cockpit

/** Compile-only label dependency of the renderer. ToolGate and pill actions are never simulated or exercised. */
object ToolGatePillActionGate {
    val LABELS: Map<AnswerPillKind, String>
        get() = error("HOST_FIXTURE_PILL_ACTIONS_UNAVAILABLE")
    val SOURCE_MISSING: String
        get() = error("HOST_FIXTURE_PILL_ACTIONS_UNAVAILABLE")
}
