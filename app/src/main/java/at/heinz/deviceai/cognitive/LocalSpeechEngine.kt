package at.heinz.deviceai.cognitive

/**
 * Which platform recognizer to use. Only real on-device recognition counts as available:
 * API 31+ with an on-device recognizer -> ON_DEVICE, everything else -> NONE.
 * Below API 31 (minSdk 26) there is no on-device API; the legacy SpeechRecognizer is NOT used,
 * because EXTRA_PREFER_OFFLINE is only advisory and audio could leave the device unnoticed.
 */
internal enum class LocalSpeechEngine {
    ON_DEVICE, NONE;

    companion object {
        fun choose(sdkInt: Int, onDeviceAvailable: Boolean): LocalSpeechEngine =
            if (sdkInt >= 31 && onDeviceAvailable) ON_DEVICE else NONE
    }
}
