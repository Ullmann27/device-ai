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

    /** Header shared by the plain report and the six-group evaluation output. */
    fun reportHeader(): String = buildString {
        append("Lokale Systemdiagnose · Android-API ").append(androidSdk)
        append("\nMesszeit: ").append(observedAtMillis)
        if (unavailable.isNotEmpty()) append("\nNicht erfasst: ").append(unavailable.sorted().joinToString())
    }

    /** The measured facts, one line each; a missing value reads „nicht verfügbar“, never zero. */
    fun measurementLines(): List<String> = listOf(
        "Datenträger verfügbar: " + bytes(storageAvailableBytes),
        "Datenträger gesamt: " + bytes(storageTotalBytes),
        "RAM verfügbar: " + bytes(ramAvailableBytes),
        "Continuum-PSS: " + bytes(ownPssBytes),
        "Akku: " + (batteryPercent?.let { "$it %" } ?: "nicht verfügbar"),
        "Akkutemperatur: " + (batteryTemperatureC?.let { decimal(it) + " °C" } ?: "nicht verfügbar"),
        "Lädt: " + when (charging) { true -> "ja"; false -> "nein"; null -> "nicht verfügbar" },
        "Android-Thermalstatus: " + (thermalStatus?.toString() ?: "nicht verfügbar"),
        "App-CPU: " + (ownCpuCorePercent?.let { decimal(it) + " % eines Kerns" } ?: "nicht verfügbar") +
            " · Fenster " + cpuSampleMillis + " ms"
    )

    fun report(): String = buildString {
        append("Lokale Systemdiagnose · Android-API ").append(androidSdk)
        append("\nMesszeit: ").append(observedAtMillis)
        measurementLines().forEach { append("\n").append(it) }
        if (unavailable.isNotEmpty()) append("\nNicht erfasst: ").append(unavailable.sorted().joinToString())
        recommendations().forEach { append("\n• ").append(it) }
    }

    companion object {
        fun bytes(value: Long?): String = value?.let {
            String.format(Locale.ROOT, "%.1f MiB", it / 1048576.0)
        } ?: "nicht verfügbar"
        internal fun decimal(value: Double) = String.format(Locale.ROOT, "%.1f", value)
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
    /** Optional read-only flag of [DIAGNOSE]; a string, as the existing ToolGate schema types unknown flags. */
    const val INCLUDE_CACHE_PREVIEW = "includeOwnCachePreview"
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

    private val cleanupCandidateQuestion = Regex("(?iu)(?<![\\p{L}])(?:bereinig\\p{L}*|aufräum\\p{L}*|" +
        "aufraeum\\p{L}*|säuber\\p{L}*|saeuber\\p{L}*|freimach\\p{L}*|cache|cage|" +
        "unnötige\\s+dateien|unnoetige\\s+dateien)(?![\\p{L}])")

    /**
     * A diagnosis request that also asks what could be cleaned additionally opens the own-cache preview.
     * Both are read-only; the cleanup itself still needs its own explicit approval in the system centre.
     */
    fun diagnosisIncludesOwnCachePreview(text: String): Boolean =
        isDiagnosisRequest(text) && cleanupCandidateQuestion.containsMatchIn(normalizedRequest(text))

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
