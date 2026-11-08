package at.heinz.deviceai

import android.speech.SpeechRecognizer
import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidOnDeviceSpeechRetryUiTest {
    @Test fun onlyClientAndBusyUseTheSingleWarmRetryClass() {
        assertTrue(isWarmRetryableSpeechError(SpeechRecognizer.ERROR_CLIENT))
        assertTrue(isWarmRetryableSpeechError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertFalse(isWarmRetryableSpeechError(SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(isWarmRetryableSpeechError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(250L, WARM_RECOGNIZER_RETRY_MS)
        assertEquals(LocalSpeechInputPolicy.Failure.BUSY,
            classifySpeechError(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(LocalSpeechInputPolicy.Failure.BUSY,
            classifySpeechError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
    }
}
