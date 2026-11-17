package at.heinz.deviceai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceNetworkProbeTest {
    @Test fun validatedMobileDataReportsSignalWithoutClaimingWebProviderSuccess() {
        val summary = DeviceNetworkProbe.describe(
            active = true, internetCapability = true, validated = true, transports = listOf("Mobilfunk")
        )
        assertTrue(summary.contains("Mobilfunk"))
        assertTrue(summary.contains("Android-Validierung: VALIDATED"))
        assertTrue(summary.contains("beweist nicht"))
        assertFalse(summary.contains("Shizuku"))
    }

    @Test fun offlineNetworkProducesSuccessfulButTruthfulDiagnostic() {
        val summary = DeviceNetworkProbe.describe(
            active = false, internetCapability = false, validated = false, transports = emptyList()
        )
        assertTrue(summary.contains("kein aktives Netzwerk"))
        assertTrue(summary.contains("nicht bestätigt"))
    }

    @Test fun vpnConnectivityIsExplicitAndBounded() {
        val summary = DeviceNetworkProbe.describe(
            active = true, internetCapability = true, validated = false, transports = listOf("VPN", "WLAN")
        )
        assertTrue(summary.contains("VPN, WLAN"))
        assertFalse(summary.contains("Android-Validierung: VALIDATED"))
    }
}
