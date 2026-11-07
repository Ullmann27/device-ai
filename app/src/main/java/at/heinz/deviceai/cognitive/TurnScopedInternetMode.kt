package at.heinz.deviceai.cognitive

/** Pure prompt-rights upper bound; never grants access to a network tool. */
internal object TurnScopedInternetMode {
    fun effective(start: InternetAccessMode, now: InternetAccessMode,
                  sameConsentRevision: Boolean = true): InternetAccessMode =
        when {
            !sameConsentRevision -> InternetAccessMode.OFF
            start == InternetAccessMode.OFF || now == InternetAccessMode.OFF -> InternetAccessMode.OFF
            start == InternetAccessMode.ASK || now == InternetAccessMode.ASK -> InternetAccessMode.ASK
            else -> InternetAccessMode.ALWAYS_ON
        }

    /** Native tool continuations keep their old prompt. A changed consent epoch must
     * abort before dispatch and discard any result that races a revocation. */
    fun <T> withCurrentConsentRevision(expected: Long, revision: () -> Long, action: () -> T): T {
        fun checkCurrent() { check(revision() == expected) { "Internetfreigabe geändert; bitte die Anfrage neu starten" } }
        checkCurrent()
        val result = action()
        checkCurrent()
        return result
    }
}
