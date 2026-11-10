package at.heinz.deviceai.systemintelligence

import java.util.Locale

data class SystemDiagnosis(
    val observedAtMillis: Long,
    val androidSdk: Int,
    val storageTotalBytes: Long?,
    val storageAvailableBytes: Long?,
    val ramTotalBytes: Long?,
    val ramAvailableBytes: Long?,
    val lowMemory: Boolean?,
    val ownPssBytes: Long?,
    val batteryPercent: Int?,
    val batteryTemperatureC: Double?,
    val charging: Boolean?,
    val thermalStatus: Int?,
    val ownCpuCorePercent: Double?,
    val cpuSampleMillis: Long,
    val unavailable: Set<String> = emptySet()
) {
    fun recommendations(): List<String> = buildList {
        if (thermalStatus != null && thermalStatus >= 3)
            add("Android meldet starke thermische Belastung. Lokale Inferenz pausieren; vorhandene adaptive Grafikgrenzen beibehalten. Keine Schutzfunktion abschalten.")
        else if (thermalStatus == null)
            add("Thermalstatus unbekannt. Die Akkutemperatur ist keine CPU-Temperatur und erklärt allein keine Ursache.")
        if (storageAvailableBytes != null && storageTotalBytes != null && storageTotalBytes > 0 &&
            storageAvailableBytes.toDouble() / storageTotalBytes < .10)
            add("Weniger als 10 % nutzbarer Datenträgerplatz. Freigegebene Dateien prüfen; vor jeder Bereinigung Vorschau ansehen.")
        if (lowMemory == true)
            add("Android meldet Speicherdruck. Continuums Modellbedarf und Hintergrundarbeit prüfen; keine fremden Apps pauschal beenden.")
        else if (lowMemory == false)
            add("Android meldet derzeit keinen niedrigen Arbeitsspeicher. Belegter RAM allein ist kein Leistungsfehler.")
        if (ownCpuCorePercent != null && ownCpuCorePercent >= 70)
            add("Continuum beansprucht im kurzen Messfenster viel CPU-Zeit. Mit gleichem Modellauftrag erneut messen; das beweist noch keine Hitzeursache.")
        add("Fremde App-Energie, CPU-Temperatur und Start-/Frame-/Modellzeiten wurden hier nicht gemessen. Kein Geschwindigkeitsgewinn belegt.")
    }

    fun report(): String = buildString {
        append("Lokale Systemdiagnose · Android-API ").append(androidSdk)
        append("\nMesszeit: ").append(observedAtMillis)
        append("\nDatenträger verfügbar: ").append(bytes(storageAvailableBytes))
        append("\nDatenträger gesamt: ").append(bytes(storageTotalBytes))
        append("\nRAM verfügbar: ").append(bytes(ramAvailableBytes))
        append("\nContinuum-PSS: ").append(bytes(ownPssBytes))
        append("\nAkku: ").append(batteryPercent?.let { "$it %" } ?: "nicht verfügbar")
        append("\nAkkutemperatur: ").append(batteryTemperatureC?.let { decimal(it) + " °C" } ?: "nicht verfügbar")
        append("\nLädt: ").append(when (charging) { true -> "ja"; false -> "nein"; null -> "unbekannt" })
        append("\nAndroid-Thermalstatus: ").append(thermalStatus?.toString() ?: "nicht verfügbar")
        append("\nApp-CPU: ").append(ownCpuCorePercent?.let { decimal(it) + " % eines Kerns" } ?: "nicht verfügbar")
        append(" · Fenster ").append(cpuSampleMillis).append(" ms")
        if (unavailable.isNotEmpty()) append("\nNicht erfasst: ").append(unavailable.sorted().joinToString())
        recommendations().forEach { append("\n• ").append(it) }
    }

    companion object {
        fun bytes(value: Long?): String = value?.let {
            String.format(Locale.ROOT, "%.1f MiB", it / 1048576.0)
        } ?: "nicht verfügbar"
        private fun decimal(value: Double) = String.format(Locale.ROOT, "%.1f", value)
    }
}

/** Recognition never grants mutation permission. */
object SystemIntelligenceCommands {
    const val DIAGNOSE = "diagnose_device"
    const val CAPABILITIES = "get_system_capabilities"
    const val PREVIEW = "preview_own_cache"
    const val CLEANUP = "cleanup_own_cache"
    const val RESTORE = "restore_own_cache"
    const val FILES = "analyze_granted_storage"
    const val SAVE = "save_system_snapshot"
    val localTools = setOf(DIAGNOSE, CAPABILITIES, PREVIEW, CLEANUP, RESTORE, FILES, SAVE)
    fun cloudMayDispatch(toolName: String): Boolean = toolName in setOf("web_search", "web_read")

    fun isDiagnosisRequest(text: String): Boolean {
        val normalized = text.trim().lowercase(Locale.GERMAN)
            .removePrefix("continuum,").removePrefix("continuum ")
            .removePrefix("jarvis,").removePrefix("jarvis ").trim().trimEnd('.', '!')
        if (Regex("\\b(nicht|keine?|niemals|später|spaeter|wenn|falls)\\b").containsMatchIn(normalized)) return false
        return setOf("analysiere mein handy", "analysiere mein gerät", "analysiere mein geraet",
            "analysiere mein smartphone", "prüfe mein handy", "pruefe mein handy", "systemdiagnose",
            "schau dir mein handy mal komplett an").any {
                normalized == it || normalized.startsWith("$it.") || normalized.startsWith("$it?")
            }
    }
}
