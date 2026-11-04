package at.heinz.deviceai

import android.speech.SpeechRecognizer
import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy.Failure
import org.junit.Assert.*
import org.junit.Test

class AndroidSpeechFailureTest {
    @Test fun androidNoMatchNoInputPermissionAndLanguagesHaveDistinctCategories() {
        assertEquals(Failure.NO_MATCH, classifySpeechError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(Failure.NO_INPUT, classifySpeechError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(Failure.PERMISSION, classifySpeechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(Failure.LANGUAGE_UNSUPPORTED, classifySpeechError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertEquals(Failure.LANGUAGE_UNAVAILABLE, classifySpeechError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
    }
    @Test fun platformClientErrorsAreNotReportedAsUserCancellation() {
        assertEquals(Failure.SERVICE_FAILURE, classifySpeechError(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(Failure.SERVICE_FAILURE, classifySpeechError(Int.MAX_VALUE))
    }
    @Test fun busyAudioNetworkAndServerStayActionable() {
        assertEquals(Failure.BUSY, classifySpeechError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(Failure.BUSY, classifySpeechError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS))
        assertEquals(Failure.AUDIO, classifySpeechError(SpeechRecognizer.ERROR_AUDIO))
        assertEquals(Failure.NETWORK, classifySpeechError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertEquals(Failure.NETWORK, classifySpeechError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(Failure.SERVICE_FAILURE, classifySpeechError(SpeechRecognizer.ERROR_SERVER_DISCONNECTED))
    }
}
