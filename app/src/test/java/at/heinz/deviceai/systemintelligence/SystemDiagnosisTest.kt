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
    @Test fun existingCloudToolsStillReachTheirCentralGate() {
        SystemIntelligenceCommands.localTools.forEach {
            assertFalse(it, SystemIntelligenceCommands.cloudMayDispatch(it))
        }
        listOf("get_battery_info", "get_device_info", "web_search", "get_storage_info").forEach {
            assertTrue(it, SystemIntelligenceCommands.cloudMayDispatch(it))
        }
    }
    @Test fun naturalDiagnosisPlansNeverAuthorizeCleanup() {
        val text = "Continuum, schau dir bitte an, warum mein Handy so langsam geworden ist. " +
            "Prüf zuerst alles, sag mir, was du gefunden hast, und mach danach die Verbesserungen, die ich dir freigebe."
        assertTrue(SystemIntelligenceCommands.isDiagnosisRequest(text))
        assertFalse(SystemIntelligenceCommands.isDiagnosisRequest("Er sagte: $text"))
        assertFalse(SystemIntelligenceCommands.isDiagnosisRequest(text + " Aber nicht jetzt."))
        assertFalse(SystemIntelligenceCommands.isDiagnosisRequest("Wenn du Zeit hast, " + text))
        assertFalse(SystemIntelligenceCommands.isDiagnosisRequest("Schau dir bitte an, warum mein Handy so langsam geworden ist und lösche alles"))
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
