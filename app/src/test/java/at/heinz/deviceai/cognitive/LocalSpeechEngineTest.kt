package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

/** minSdk 26: below API 31 there is no on-device recognizer API; the legacy recognizer is the fallback. */
class LocalSpeechEngineTest {
    @Test fun api31AndUpUsesTheOnDeviceRecognizer() {
        assertEquals(LocalSpeechEngine.ON_DEVICE, LocalSpeechEngine.choose(31, onDeviceAvailable = true, legacyAvailable = true))
        assertEquals(LocalSpeechEngine.ON_DEVICE, LocalSpeechEngine.choose(36, onDeviceAvailable = true, legacyAvailable = false))
    }

    @Test fun belowApi31FallsBackToTheLegacyRecognizerPreferringOffline() {
        assertEquals(LocalSpeechEngine.LEGACY_PREFER_OFFLINE, LocalSpeechEngine.choose(26, onDeviceAvailable = false, legacyAvailable = true))
        assertEquals(LocalSpeechEngine.LEGACY_PREFER_OFFLINE, LocalSpeechEngine.choose(30, onDeviceAvailable = true, legacyAvailable = true))
    }

    @Test fun noRecognizerMeansUnavailable() {
        assertEquals(LocalSpeechEngine.NONE, LocalSpeechEngine.choose(26, onDeviceAvailable = false, legacyAvailable = false))
        assertEquals(LocalSpeechEngine.NONE, LocalSpeechEngine.choose(31, onDeviceAvailable = false, legacyAvailable = true))
    }
}
