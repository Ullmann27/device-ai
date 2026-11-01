package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Test

class TurnScopedInternetModeTest {
    @Test fun offlineVetoSurvivesInternetReconnection() {
        for (mode in InternetAccessMode.values()) {
            assertEquals(InternetAccessMode.OFF, TurnScopedInternetMode.effective(InternetAccessMode.OFF, mode))
            assertEquals(InternetAccessMode.OFF, TurnScopedInternetMode.effective(mode, InternetAccessMode.OFF))
        }
    }

    @Test fun askIsNotUpgradedWhileTurnIsActive() {
        assertEquals(InternetAccessMode.ASK,
            TurnScopedInternetMode.effective(InternetAccessMode.ASK, InternetAccessMode.ALWAYS_ON))
        assertEquals(InternetAccessMode.ASK,
            TurnScopedInternetMode.effective(InternetAccessMode.ALWAYS_ON, InternetAccessMode.ASK))
        assertEquals(InternetAccessMode.ALWAYS_ON,
            TurnScopedInternetMode.effective(InternetAccessMode.ALWAYS_ON, InternetAccessMode.ALWAYS_ON))
    }
}
