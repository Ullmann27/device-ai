package at.heinz.deviceai.cognitive

/** A one-shot, user-confirmed read-only web capability. Model output cannot grant it. */
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

    @Synchronized fun approve(question: String): Boolean {
        val allowed = mode == InternetAccessMode.ASK && pending == question
        pending = null
        return allowed
    }

    @Synchronized fun cancel() { pending = null }
}
