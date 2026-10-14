package at.heinz.deviceai.cognitive

/** A short-lived, single-use permission for a specific read-only web request.
 * A displayed dialog is not a grant: only explicit owner confirmation creates one.
 * Untrusted webpage content and model output have no access to this object.
 */
enum class InternetAccessMode { OFF, ASK }

class InternetConsentPolicy(initialMode: InternetAccessMode = InternetAccessMode.ASK) {
    var mode: InternetAccessMode = initialMode
        private set
    private var pending: String? = null

    @Synchronized fun setMode(value: InternetAccessMode) {
        mode = value
        pending = null
    }

    @Synchronized fun request(question: String): Boolean {
        pending = null
        if (mode != InternetAccessMode.ASK || question.isBlank() || question.length > 2048) return false
        pending = question
        return true
    }

    /** Consume even on mismatch to prevent replay of a stale user approval. */
    @Synchronized fun approve(question: String): Boolean {
        val allowed = mode == InternetAccessMode.ASK && pending == question
        pending = null
        return allowed
    }

    @Synchronized fun cancel() { pending = null }
}

/** Gate for the *actual* research execution; called after explicit confirmation.
 * This is separate from the device ToolGate, which never receives network authority.
 */
class InternetResearchGate(private val consent: InternetConsentPolicy) {
    @Synchronized fun authorize(question: String): Boolean = consent.approve(question)
    @Synchronized fun revoke() = consent.cancel()
}
