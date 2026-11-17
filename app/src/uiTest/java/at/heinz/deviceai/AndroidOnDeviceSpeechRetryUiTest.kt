package at.heinz.deviceai

import android.speech.SpeechRecognizer
import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy
import at.heinz.deviceai.cognitive.SpeechRecoveryPolicy
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
    @Test fun onlyClientAndBusyUseTheBoundedRecoveryClass() {
        assertTrue(isWarmRetryableSpeechError(SpeechRecognizer.ERROR_CLIENT))
        assertTrue(isWarmRetryableSpeechError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertFalse(isWarmRetryableSpeechError(SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(isWarmRetryableSpeechError(SpeechRecognizer.ERROR_NETWORK))
        // First warm retry stays at 250 ms; later attempts escalate to a cold restart (#641).
        assertEquals(250L, SpeechRecoveryPolicy.DEFAULT_WARM_DELAYS_MS.first())
        assertTrue(SpeechRecoveryPolicy.MAX_TOTAL_DELAY_MS <= 5_000L)
        assertEquals(LocalSpeechInputPolicy.Failure.BUSY,
            classifySpeechError(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(LocalSpeechInputPolicy.Failure.BUSY,
            classifySpeechError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
    }
}
