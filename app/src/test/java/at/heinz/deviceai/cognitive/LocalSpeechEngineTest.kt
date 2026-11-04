package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only real on-device recognition counts as available. Below API 31 (minSdk 26) there is no
 * on-device API, so speech input is unavailable; the legacy recognizer is never used (may go online). */
class LocalSpeechEngineTest {
    @Test fun api31AndUpUsesTheOnDeviceRecognizer() {
        assertEquals(LocalSpeechEngine.ON_DEVICE, LocalSpeechEngine.choose(31, onDeviceAvailable = true))
        assertEquals(LocalSpeechEngine.ON_DEVICE, LocalSpeechEngine.choose(36, onDeviceAvailable = true))
    }

    @Test fun belowApi31IsUnavailableAndNeverFallsBackToTheLegacyRecognizer() {
        assertEquals(LocalSpeechEngine.NONE, LocalSpeechEngine.choose(26, onDeviceAvailable = false))
        assertEquals(LocalSpeechEngine.NONE, LocalSpeechEngine.choose(30, onDeviceAvailable = true))
        assertEquals(listOf("ON_DEVICE", "NONE"), LocalSpeechEngine.values().map { it.name })
    }

    @Test fun api30WithInternetOffOrOfflineVetoRoutesToUnavailable() {
        val available = LocalSpeechEngine.choose(30, onDeviceAvailable = true) != LocalSpeechEngine.NONE
        for (mode in InternetAccessMode.values()) {
            assertEquals(LocalSpeechInputPolicy.Route.UNAVAILABLE, LocalSpeechInputPolicy.route(
                mode, offlineVeto = true, onDeviceAvailable = available, microphoneGranted = true))
        }
        assertEquals(LocalSpeechInputPolicy.Route.UNAVAILABLE, LocalSpeechInputPolicy.route(
            InternetAccessMode.OFF, offlineVeto = false, onDeviceAvailable = available, microphoneGranted = true))
    }

    @Test fun noOnDeviceRecognizerMeansUnavailable() {
        assertEquals(LocalSpeechEngine.NONE, LocalSpeechEngine.choose(31, onDeviceAvailable = false))
    }
}
