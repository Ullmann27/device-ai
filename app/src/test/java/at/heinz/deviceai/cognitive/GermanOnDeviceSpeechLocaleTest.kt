package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanOnDeviceSpeechLocaleTest {
    @Test fun unsupportedAustrianGermanRetriesGermanGermanyOnce() {
        val locale = GermanOnDeviceSpeechLocale()
        assertEquals("de-AT", locale.tag)
        assertTrue(locale.retry(LocalSpeechInputPolicy.Failure.LANGUAGE_UNSUPPORTED))
        assertEquals("de-DE", locale.tag)
        assertFalse(locale.retry(LocalSpeechInputPolicy.Failure.LANGUAGE_UNSUPPORTED))
        assertEquals("de-DE", locale.tag)
    }

    @Test fun missingLocalLanguageRetriesOnceThenStops() {
        val locale = GermanOnDeviceSpeechLocale()
        assertTrue(locale.retry(LocalSpeechInputPolicy.Failure.LANGUAGE_UNAVAILABLE))
        assertFalse(locale.retry(LocalSpeechInputPolicy.Failure.LANGUAGE_UNAVAILABLE))
    }

    @Test fun otherSpeechFailuresNeverChangeLanguage() {
        for (failure in LocalSpeechInputPolicy.Failure.values()) {
            if (failure in setOf(LocalSpeechInputPolicy.Failure.LANGUAGE_UNAVAILABLE,
                    LocalSpeechInputPolicy.Failure.LANGUAGE_UNSUPPORTED)) continue
            val locale = GermanOnDeviceSpeechLocale()
            assertFalse(locale.retry(failure))
            assertEquals("de-AT", locale.tag)
        }
    }

    @Test fun allLaterFailuresAreTerminalAfterFallback() {
        val locale = GermanOnDeviceSpeechLocale()
        assertTrue(locale.retry(LocalSpeechInputPolicy.Failure.LANGUAGE_UNAVAILABLE))
        for (failure in LocalSpeechInputPolicy.Failure.values()) {
            assertFalse(locale.retry(failure))
        }
    }
}
