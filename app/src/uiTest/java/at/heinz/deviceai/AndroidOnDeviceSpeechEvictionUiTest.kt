package at.heinz.deviceai

import android.Manifest
import android.os.Looper
import android.speech.SpeechRecognizer
import at.heinz.deviceai.cognitive.LocalSpeechInputPolicy.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSpeechRecognizer

/**
 * Field P0 (Fold7, Code54): a pooled on-device recognizer whose service reported
 * ERROR_SERVER / ERROR_SERVER_DISCONNECTED stayed warm, so the next hands-free re-arm reused the
 * dead instance and failed again. Real adapter against Robolectric's SpeechRecognizer shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidOnDeviceSpeechEvictionUiTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun shadow(r: SpeechRecognizer): ShadowSpeechRecognizer = Shadow.extract(r)

    private fun freshInput(): AndroidOnDeviceSpeechInput {
        ShadowSpeechRecognizer.reset()
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        return AndroidOnDeviceSpeechInput(RuntimeEnvironment.getApplication())
    }

    private fun startTurn(input: AndroidOnDeviceSpeechInput, failures: MutableList<Failure>): SpeechRecognizer {
        input.start({}, {}, {}, { failures += it })
        idle()
        return checkNotNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    }

    @Test fun serviceErrorsEvictThePooledRecognizerSoTheReArmIsFresh() {
        for (code in listOf(SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS, SpeechRecognizer.ERROR_AUDIO, 999)) {
            val input = freshInput()
            val failures = mutableListOf<Failure>()
            val first = startTurn(input, failures)
            shadow(first).triggerOnError(code)
            idle()
            assertEquals("code $code", 1, failures.size)
            assertTrue("code $code: dead recognizer must be destroyed", shadow(first).isDestroyed)
            val second = startTurn(input, failures)
            assertNotSame("code $code: re-arm must use a fresh native recognizer", first, second)
            input.shutdown()
        }
    }

    @Test fun silenceKeepsTheWarmRecognizerForTheNextTurn() {
        for (code in listOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            val input = freshInput()
            val failures = mutableListOf<Failure>()
            val first = startTurn(input, failures)
            shadow(first).triggerOnError(code)
            idle()
            assertEquals(1, failures.size)
            assertFalse("code $code", shadow(first).isDestroyed)
            assertSame("code $code: benign silence keeps the warm instance", first, startTurn(input, failures))
            input.shutdown()
            assertTrue("shutdown releases the native recognizer", shadow(first).isDestroyed)
        }
    }
}
