package at.heinz.deviceai

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumFirstVoiceUiTest {
    @Test fun unconfiguredInstallOffersVoiceOutputFromItsFirstOpen() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue("The requested first-open companion remains silent by default", BootstrapMemoryStore(app).voiceEnabled)
    }
    @Test fun storedMuteChoiceAndOwnerFactsAreNeverChangedByTheNewDefault() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE)
        prefs.edit().clear().putBoolean("voice_enabled", false)
            .putStringSet("facts", setOf("Synthetische Testnotiz")).commit()
        val before = prefs.all.toMap()
        assertFalse(BootstrapMemoryStore(app).voiceEnabled)
        assertEquals(before, prefs.all)
    }
}
