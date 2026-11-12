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

    private val device = "(?:handy|smartphone|telefon|android[- ]gerät|android[- ]geraet)"
    private val courtesy = "(?:(?:bitte|mal|kurz)\\s+)*"
    private val explicitDiagnosis = Regex("(?iu)^(?:(?:mach(?:e)?|starte|beginne|führ(?:e)?|fuehr(?:e)?)\\s+)?" +
        courtesy + "(?:(?:eine|die)\\s+)?(?:systemdiagnose|handyanalyse|geräteanalyse|geraeteanalyse|" +
        "analyse\\s+(?:meines|des)\\s+(?:handys|geräts|geraets|smartphones))(?![\\p{L}])")
    private val deviceAnalysis = Regex("(?iu)^(?:analysier(?:e)?|untersuch(?:e)?)\\s+" + courtesy +
        "(?:mein(?:en|es)?|das|dieses)\\s+(?:$device|gerät|geraet)(?![\\p{L}])|" +
        "^(?:prüf(?:e)?|pruef(?:e)?|überprüf(?:e)?)\\s+" + courtesy +
        "(?:mein(?:en|es)?|das|dieses)\\s+$device(?![\\p{L}])")
    private val directHeatQuestion = Regex("(?iu)^(?:wie\\s+(?:heiß|heiss|warm)\\s+ist\\s+mein\\s+$device" +
        "(?:\\s+gerade)?|ist\\s+mein\\s+$device\\s+(?:zu\\s+)?(?:heiß|heiss|warm)|" +
        "wie\\s+hoch\\s+ist\\s+die\\s+cpu[- ]auslastung\\s+meines\\s+(?:handys|smartphones))$")
    private val diagnosisDeferral = Regex("(?iu)(?<![\\p{L}])(?:nicht|nie|niemals|kein\\p{L}*|später|spaeter|" +
        "morgen|wenn|falls)(?![\\p{L}])|ohne\\s+(?:eine?\\s+)?(?:messung|diagnose|prüfung)")
    private val reported = Regex("(?iu)^(?:er|sie|jemand)\\s+(?:sagte|sagt|fragte|schrieb)(?![\\p{L}])")

    private fun normalizedRequest(text: String): String = text.trim().lowercase(Locale.ROOT)
        .replace(Regex("(?iu)^(?:(?:hey|hallo)\\s+)?(?:continuum|jarvis)[,:]?\\s+"), "")
        .replace(Regex("(?iu)^bitte\\s+"), "").trim().trimEnd('.', '!', '?').trim()

    fun isDiagnosisTarget(text: String): Boolean = Regex("(?iu)^(?:(?:eine|die)\\s+)?" +
        "(?:systemdiagnose|handyanalyse|geräteanalyse|geraeteanalyse|analyse(?:\\s+.*)?)$")
        .matches(text.trim().trimEnd('.', '!', '?'))

    fun isDiagnosisRequest(text: String): Boolean {
        if (text.length !in 4..2_048 || text.any { it in "\"„“”«»" }) return false
        val original = normalizedRequest(text)
        if (reported.containsMatchIn(original)) return false
        val safety = at.heinz.deviceai.cognitive.OwnerDeviceRequestSafety.inspect(original)
        if (safety.blocked) return false
        val normalized = safety.readOnlyText.trim().trimEnd('.', '!', '?')
        if (diagnosisDeferral.containsMatchIn(normalized)) return false
        val polite = Regex("(?iu)^(?:kannst|könntest|würdest)\\s+du\\s+" + courtesy +
            "(?:mein(?:en|es)?|das)\\s+(?:$device|gerät|geraet)\\s+(?:analysieren|untersuchen|prüfen)(?:\\s+bitte)?$")
        if (polite.matches(normalized) || directHeatQuestion.matches(normalized)) return true
        if (explicitDiagnosis.containsMatchIn(normalized) || deviceAnalysis.containsMatchIn(normalized)) return true
        if (Regex("(?iu)^(?:schau|sieh)\\s+dir\\s+(?:bitte\\s+)?mein\\s+$device\\s+" +
                "(?:mal\\s+)?(?:komplett\\s+)?an(?![\\p{L}])").containsMatchIn(normalized)) return true
        if (Regex("(?iu)^(?:schau|sieh) dir (?:bitte )?an,? warum mein (?:handy|gerät|geraet|smartphone) " +
                "(?:so )?langsam(?: geworden)? ist(?:[.!?]|$)").containsMatchIn(normalized)) return true
        val metrics = listOf("akkutemperatur", "arbeitsspeicher", "speicherplatz", "cpu[- ]auslastung")
            .count { Regex("(?iu)(?<![\\p{L}])(?:$it)(?![\\p{L}])").containsMatchIn(normalized) }
        return metrics >= 2 && Regex("(?iu)^(?:prüfe?|pruefe?|untersuche?|analysiere?)\\s+")
            .containsMatchIn(normalized)
    }

    fun diagnosisIncludesOwnCachePreview(text: String): Boolean = false

    /** Cleaning speech opens preview only. */
    fun isCachePreviewRequest(text: String): Boolean {
        if (text.length !in 4..320 || text.any { it.isISOControl() || it in "\"„“”«»" }) return false
        val normalized = normalizedRequest(text)
        if (at.heinz.deviceai.cognitive.OwnerDeviceRequestSafety.inspect(normalized).roleManipulation) return false
        if (normalized in setOf("cache-vorschau", "cache vorschau")) return true
        return Regex("(?iu)^(?:bereinige|reinige|leere|säubere|lösche)\\s+(?:bitte\\s+)?" +
            "(?:(?:meinen|den|deinen|eigenen)\\s+)?(?:cache|cage)(?:\\s+bitte)?" +
            "(?:\\s+und\\s+(?:sage?|zeige)\\s+mir\\s+(?:wie\\s+viel|wieviel)\\s+" +
            "(?:du\\s+)?(?:bereinigt|gelöscht|freigemacht)\\s+(?:hast|wurde))?$").matches(normalized)
    }
}
