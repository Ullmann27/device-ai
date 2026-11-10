package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetAccessModeLabelsTest {
    @Test
    fun settingsStandardLabelMatchesFreshInstallRestoreDefault() {
        val restored = InternetConsentPolicy.restoredMode(null)
        assertEquals(InternetAccessMode.ALWAYS_ON, restored)
        assertEquals(restored, InternetAccessModeLabels.advertisedDefault())
        assertTrue(InternetAccessModeLabels.option(restored), InternetAccessModeLabels.option(restored).contains("Standard"))
        for (mode in InternetAccessMode.entries) {
            if (mode == restored) continue
            assertFalse(InternetAccessModeLabels.option(mode), InternetAccessModeLabels.option(mode).contains("Standard"))
        }
        assertEquals(
            "Auf Nachfrage",
            InternetAccessModeLabels.option(InternetAccessMode.ASK)
        )
        assertEquals(
            listOf("Auf Nachfrage", "Dauerhaft erlauben · ALWAYS_ON (Standard)", "Aus (kein Webzugriff)"),
            InternetAccessModeLabels.dialogOptions(
                listOf(InternetAccessMode.ASK, InternetAccessMode.ALWAYS_ON, InternetAccessMode.OFF)
            ).toList()
        )
    }

    @Test
    fun restoredModeStillPreservesStoredAskAndOff() {
        assertEquals(InternetAccessMode.OFF, InternetConsentPolicy.restoredMode("OFF"))
        assertEquals(InternetAccessMode.ASK, InternetConsentPolicy.restoredMode("ASK"))
        assertEquals(InternetAccessMode.ALWAYS_ON, InternetConsentPolicy.restoredMode("ALWAYS_ON"))
    }
}
