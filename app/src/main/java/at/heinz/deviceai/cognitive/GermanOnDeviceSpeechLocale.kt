package at.heinz.deviceai.cognitive

/** One bounded offline German locale retry within an explicitly started turn. */
internal class GermanOnDeviceSpeechLocale {
    var tag: String = "de-AT"
        private set

    private var triedAlternative = false

    fun retry(failure: LocalSpeechInputPolicy.Failure): Boolean {
        if (triedAlternative || (failure != LocalSpeechInputPolicy.Failure.LANGUAGE_UNAVAILABLE &&
                failure != LocalSpeechInputPolicy.Failure.LANGUAGE_UNSUPPORTED)) return false
        triedAlternative = true
        tag = "de-DE"
        return true
    }
}
