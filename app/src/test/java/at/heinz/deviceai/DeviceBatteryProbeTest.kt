package at.heinz.deviceai

import android.os.BatteryManager
import org.junit.Assert.*
import org.junit.Test

class DeviceBatteryProbeTest {
    @Test fun measuredFold7ValuesAreConciseAndUseBatteryNotCpuTemperature() {
        val answer = DeviceBatteryProbe.describe(75, 100,
            BatteryManager.BATTERY_STATUS_DISCHARGING, 347)
        assertTrue(answer.contains("Akkustand: 75 %"))
        assertTrue(answer.contains("entlädt sich"))
        assertTrue(answer.contains("34,7 °C"))
        assertTrue(answer.contains("nicht Prozessor"))
        assertFalse(answer.contains("Current Battery Service"))
        assertFalse(answer.contains("Broken pipe"))
        assertFalse(answer.contains("mSecPlugTypeSummary"))
    }

    @Test fun fractionalPercentIsCalculatedFromRealLevelAndScale() {
        val answer = DeviceBatteryProbe.describe(5, 8, BatteryManager.BATTERY_STATUS_CHARGING, null)
        assertTrue(answer.contains("62 %"))
        assertTrue(answer.contains("wird geladen"))
        assertFalse(answer.contains("Batterietemperatur"))
    }

    @Test fun missingOrInvalidAndroidReadingsFailClosed() {
        for (pair in listOf(-1 to 100, 75 to 0, 101 to 100, 10 to -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                DeviceBatteryProbe.describe(pair.first, pair.second,
                    BatteryManager.BATTERY_STATUS_UNKNOWN, null)
            }
        }
    }

    @Test fun chargingUnknownDoesNotInventAChargingState() {
        val answer = DeviceBatteryProbe.describe(80, 100,
            BatteryManager.BATTERY_STATUS_UNKNOWN, null)
        assertTrue(answer.contains("nicht eindeutig gemeldet"))
        assertFalse(answer.contains("wird geladen"))
    }

    @Test fun batteryTemperatureKeepsTenthsAndNegativeSign() {
        val answer = DeviceBatteryProbe.describe(75, 100,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING, -15)
        assertTrue(answer.contains("-1,5 °C"))
    }

    @Test fun theAndroidThermalStatusIsReportedOrExplicitlyUnavailable() {
        val reported = DeviceBatteryProbe.describe(66, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 279,
            DeviceBatteryProbe.ThermalReading(0))
        assertTrue(reported.contains("Akkustand: 66 %"))
        assertTrue(reported.contains("27,9 °C"))
        assertTrue(reported.contains("Android-Thermalstatus: normal (keine Drosselung)"))
        val missing = DeviceBatteryProbe.describe(66, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 279,
            DeviceBatteryProbe.ThermalReading(null))
        assertTrue(missing.contains("Android-Thermalstatus: nicht verfügbar"))
        assertTrue(DeviceBatteryProbe.thermalLabel(3).contains("stark"))
        assertTrue(DeviceBatteryProbe.thermalLabel(9).contains("unbekannt"))
        assertFalse(DeviceBatteryProbe.describe(66, 100, BatteryManager.BATTERY_STATUS_DISCHARGING, 279)
            .contains("Thermalstatus"))
    }
}
