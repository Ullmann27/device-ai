package at.heinz.deviceai.systemintelligence

import org.junit.Assert.*
import org.junit.Test

class SystemDiagnosisTest {
    private fun sample() = SystemDiagnosis(1000, 37, 1000, 700, 400, 100, false,
        50, 80, 33.0, false, 0, 10.0, 250)
    @Test fun explicitCommandsOnly() {
        listOf("Continuum, analysiere mein Handy", "Systemdiagnose", "Prüfe mein Handy!",
            "Schau dir mein Handy mal komplett an. Warum wird es so heiß?").forEach {
            assertTrue(it, SystemIntelligenceCommands.isDiagnosisRequest(it))
        }
    }
    @Test fun cloudCannotReadAnyDeviceTool() {
        (SystemIntelligenceCommands.localTools + setOf("read_app_screen", "get_device_info", "read_logcat",
            "get_battery_info", "list_files", "get_app_info", "get_storage_info", "unknown_future_tool")).forEach {
            assertFalse(it, SystemIntelligenceCommands.cloudMayDispatch(it))
        }
        assertTrue(SystemIntelligenceCommands.cloudMayDispatch("web_search"))
    }
    @Test fun negationQuotesAndConditionalCannotDispatch() {
        listOf("Analysiere mein Handy nicht", "Keine Systemdiagnose", "Wenn ich sage analysiere mein Handy",
            "Er sagte: analysiere mein Handy", "\"Analysiere mein Handy\"", "Analysiere mein Handy. Aber nicht jetzt.",
            "Was bedeutet Systemdiagnose?", "Warum ist das Handy heiß?").forEach {
            assertFalse(it, SystemIntelligenceCommands.isDiagnosisRequest(it))
        }
    }
    @Test fun unknownMetricsDoNotBecomeHealthyZero() {
        val unknown = sample().copy(storageAvailableBytes = null, ownPssBytes = null, thermalStatus = null,
            batteryPercent = null, ownCpuCorePercent = null)
        assertTrue(unknown.report().contains("nicht verfügbar"))
        assertFalse(unknown.report().contains("Stufe 0"))
        assertTrue(unknown.recommendations().any { it.contains("Thermalstatus unbekannt") })
    }
    @Test fun normalRamIsNotBoosterRecommendation() {
        assertTrue(sample().recommendations().any { it.contains("kein Leistungsfehler") })
        assertFalse(sample().recommendations().any { it.startsWith("Apps beenden") })
    }
    @Test fun measuredHeatPressureAndStorageLeadToBoundedAdvice() {
        val advice = sample().copy(thermalStatus = 4, lowMemory = true, storageAvailableBytes = 50,
            ownCpuCorePercent = 220.0).recommendations()
        assertTrue(advice.any { it.contains("thermische Belastung") })
        assertTrue(advice.any { it.contains("Speicherdruck") })
        assertTrue(advice.any { it.contains("10 %") })
        assertTrue(advice.any { it.contains("beweist noch keine Hitzeursache") })
        assertTrue(sample().report().contains("Kein Geschwindigkeitsgewinn belegt"))
    }
}
