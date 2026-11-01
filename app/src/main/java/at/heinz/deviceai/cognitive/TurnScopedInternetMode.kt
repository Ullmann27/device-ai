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
}
