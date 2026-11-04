package at.heinz.deviceai.cognitive

/**
 * Which platform recognizer to use. API 31+ uses only the on-device recognizer (no fallback).
 * Below API 31 (minSdk 26) that API does not exist; the legacy SpeechRecognizer is used with
 * EXTRA_PREFER_OFFLINE, which asks the system service to stay on the device.
 */
internal enum class LocalSpeechEngine {
    ON_DEVICE, LEGACY_PREFER_OFFLINE, NONE;

    companion object {
        fun choose(sdkInt: Int, onDeviceAvailable: Boolean, legacyAvailable: Boolean): LocalSpeechEngine = when {
            sdkInt >= 31 -> if (onDeviceAvailable) ON_DEVICE else NONE
            legacyAvailable -> LEGACY_PREFER_OFFLINE
            else -> NONE
        }
    }
}
