package at.heinz.deviceai.systemintelligence

import java.util.Locale

/** A suspected explanation; never a finding until its missing cross-check has been measured. */
data class DiagnosisHypothesis(val statement: String, val missingCrossCheck: String)

/** One own technical diagnosis report from a cache preview; bytes come only from that preview. */
data class CleanupCandidate(val entryId: String, val bytes: Long, val observedAtEpochMs: Long)

/**
 * Read-only evaluation of one [SystemDiagnosis] in six separated groups (Kapitel VI/VII, Auftrag 2):
 * measured values, anomalies (measured deviations), hypotheses (each with its missing cross-check),
 * cleanup candidates (own diagnostic cache only), actions that need an explicit approval, and the
 * changes actually performed with their measured results. The last group stays empty unless the
 * result of an approved cleanup is passed in. Building an evaluation never changes anything.
 */
data class DiagnosisEvaluation(
    val measured: List<String>,
    val anomalies: List<String>,
    val hypotheses: List<DiagnosisHypothesis>,
    val cleanupCandidates: List<CleanupCandidate>,
    val cleanupNotes: List<String>,
    val requiresApproval: List<String>,
    val approvedActions: List<String>,
    val performedChanges: List<String>,
    val measuredResults: List<String>
) {
    fun render(): String = buildString {
        append("Auswertung in sechs getrennten Gruppen")
        group("1 · Messwerte", measured, "Keine Messung.")
        group("2 · Auffälligkeiten (gemessene Abweichungen)", anomalies, "Keine gemessene Abweichung.")
        group("3 · Hypothesen (unbestätigt, mit fehlender Gegenprüfung)",
            hypotheses.map { "${it.statement} Fehlende Gegenprüfung: ${it.missingCrossCheck}" },
            "Keine Hypothese.")
        group("4 · Bereinigungskandidaten (nur eigene Diagnoseablage)",
            cleanupCandidates.map { "Bericht ${it.entryId}: ${it.bytes} Byte" } + cleanupNotes, "Keine.")
        group("5 · Erst nach Freigabe", requiresApproval + approvedActions.map { "Freigegeben: $it" },
            "Keine Aktion vorgeschlagen.")
        append("\n6 · Durchgeführte Änderungen und gemessene Ergebnisse")
        if (performedChanges.isEmpty()) append("\n• Keine Änderung durchgeführt.")
        performedChanges.forEach { append("\n• ").append(it) }
        measuredResults.forEach { append("\n• Ergebnis: ").append(it) }
        append("\n• Kein Geschwindigkeitsgewinn belegt.")
    }

    private fun StringBuilder.group(title: String, lines: List<String>, empty: String) {
        append("\n").append(title)
        if (lines.isEmpty()) append("\n• ").append(empty)
        lines.forEach { append("\n• ").append(it) }
    }

    companion object {
        const val SEVERE_THERMAL_STATUS = 3
        const val LOW_STORAGE_FRACTION = 0.10
        const val HIGH_OWN_CPU_PERCENT = 70.0

        private val thermalNames = listOf("keine", "leicht", "mäßig", "stark", "kritisch", "Notfall", "Abschaltung")

        /**
         * [preview] is the own-cache preview of this same read-only step, if one was made.
         * [approvedCleanup] is passed only after the owner explicitly approved a previewed cleanup.
         */
        fun evaluate(
            diagnosis: SystemDiagnosis,
            preview: OwnCachePreview? = null,
            approvedCleanup: OwnCacheCleanupResult? = null,
            previewUnavailableReason: String? = null
        ): DiagnosisEvaluation = with(diagnosis) {
            val storageUsed = if (storageTotalBytes != null && storageAvailableBytes != null && storageTotalBytes > 0 &&
                storageAvailableBytes in 0..storageTotalBytes) storageTotalBytes - storageAvailableBytes else null
            val freeFraction = storageUsed?.let { storageAvailableBytes!!.toDouble() / storageTotalBytes!! }
            val thermalSevere = thermalStatus != null && thermalStatus >= SEVERE_THERMAL_STATUS
            val cpuHigh = ownCpuCorePercent != null && ownCpuCorePercent >= HIGH_OWN_CPU_PERCENT

            val measured = buildList {
                add("Datenträger belegt: " + (storageUsed?.let {
                    "${SystemDiagnosis.bytes(it)} von ${SystemDiagnosis.bytes(storageTotalBytes)} " +
                        "(${percent(it.toDouble() / storageTotalBytes!!)} %)"
                } ?: "nicht verfügbar"))
                measurementLines().forEach { line ->
                    if (line.startsWith("Android-Thermalstatus: ") && thermalStatus != null)
                        add("Android-Thermalstatus: $thermalStatus (${thermalNames.getOrElse(thermalStatus) { "unbekannt" }})")
                    else add(line)
                }
                add("RAM gesamt: " + SystemDiagnosis.bytes(ramTotalBytes))
                add("Android-Speicherdruck: " + when (lowMemory) { true -> "ja"; false -> "nein"; null -> "nicht verfügbar" })
                add("CPU-Temperatur: nicht verfügbar (ohne Systemberechtigung nicht lesbar; die Akkutemperatur ist keine CPU-Temperatur)")
                add("Fremde App-Energie: nicht verfügbar (ohne Systemberechtigung nicht messbar)")
                add("Speicher je fremder App: nicht verfügbar (ohne Android-Berechtigung nicht lesbar)")
                add("Start-, Frame- und Modellzeiten: nicht verfügbar (in diesem Schritt nicht gemessen)")
            }

            val anomalies = buildList {
                if (thermalSevere) add("Android meldet Thermalstatus $thermalStatus " +
                    "(${thermalNames.getOrElse(thermalStatus!!) { "unbekannt" }}, Stufe ≥ $SEVERE_THERMAL_STATUS). " +
                    "Gemessene Auffälligkeit, Ursache nicht bestimmt.")
                if (freeFraction != null && freeFraction < LOW_STORAGE_FRACTION)
                    add("Weniger als 10 % nutzbarer Datenträgerplatz (${percent(freeFraction)} % frei).")
                if (lowMemory == true) add("Android meldet Speicherdruck (wenig verfügbarer Arbeitsspeicher).")
                if (cpuHigh) add("Continuum beanspruchte im Messfenster ${SystemDiagnosis.decimal(ownCpuCorePercent!!)} % " +
                    "eines Kerns (Schwelle ${HIGH_OWN_CPU_PERCENT.toInt()} %).")
            }

            val hypotheses = buildList {
                when {
                    thermalStatus == null -> add(DiagnosisHypothesis(
                        "Wärmeursache unbekannt: Android-Thermalstatus nicht verfügbar.",
                        "Thermalstatus erneut lesen; die Akkutemperatur ist keine CPU-Temperatur und erklärt allein nichts."))
                    thermalSevere -> {
                        add(DiagnosisHypothesis(
                            "Continuums lokale Inferenz oder Grafik könnte zur Wärme beitragen.",
                            "Gleiche Messung mit pausierter Inferenz unter gleicher Umgebung wiederholen; " +
                                "CPU-Temperatur und fremde App-Energie sind nicht verfügbar."))
                        add(DiagnosisHypothesis(
                            "Andere Apps, Umgebungswärme oder Funkverbindungen könnten zur Wärme beitragen.",
                            "Fremde App-Energie ist ohne Systemberechtigung nicht messbar; Android-Akkuverbrauch in den Einstellungen ansehen."))
                    }
                    else -> add(DiagnosisHypothesis(
                        "Android meldet keine starke thermische Belastung (Thermalstatus $thermalStatus); " +
                            "spürbare Wärme könnte von Laden, Umgebung oder kurzer Last stammen.",
                        "Messung unter derselben Last wiederholen; CPU-Temperatur nicht verfügbar."))
                }
                if (charging == true) add(DiagnosisHypothesis(
                    "Laden kann den Akku zusätzlich erwärmen.", "Messung ohne Ladekabel wiederholen."))
                if (cpuHigh) add(DiagnosisHypothesis(
                    "Continuums CPU-Last könnte zur Wärme beitragen.",
                    "Mit gleichem Modellauftrag erneut messen; ein kurzes Messfenster beweist keine Hitzeursache."))
                if (lowMemory == true) add(DiagnosisHypothesis(
                    "Continuums Modellbedarf könnte den Speicherdruck erhöhen.",
                    "Continuum-PSS mit und ohne geladenes Modell vergleichen; fremde Apps werden nicht pauschal beendet."))
                if (storageUsed != null) add(DiagnosisHypothesis(
                    "Medien, Apps und Systemdaten belegen den genutzten Datenträgerplatz; ihre Verteilung ist unbekannt.",
                    "Die Android-Speicheraufschlüsselung je App ist ohne Berechtigung nicht lesbar; " +
                        "Einstellungen › Speicher ansehen oder Dateien ausdrücklich freigeben (DATEIEN AUSWÄHLEN & PRÜFEN)."))
            }

            val candidates = if (preview != null && preview.complete)
                preview.entries.map { CleanupCandidate(it.id, it.bytes, it.observedAtEpochMs) } else emptyList()
            val cleanupNotes = buildList {
                when {
                    preview == null -> add(previewUnavailableReason?.let { "Cache-Vorschau nicht verfügbar: $it." }
                        ?: "Keine Cache-Vorschau in diesem Schritt erstellt: Bereinigungskandidaten nicht verfügbar.")
                    !preview.complete -> add("Vorschau unvollständig: keine Kandidaten und keine Freigabe; erneut prüfen.")
                    else -> add("Eigene Diagnoseablage: ${candidates.size} Berichte, ${preview.eligibleBytes} Byte " +
                        "(Bytes aus der Vorschau).")
                }
                if (preview != null && preview.protectedEntryCount > 0)
                    add("${preview.protectedEntryCount} geschützte Einträge im App-Cache bleiben unberührt.")
                add("Fremde App-Caches sind ohne Android-Berechtigung nicht zugänglich und werden weder gemessen noch bereinigt.")
            }

            val requiresApproval = buildList {
                if (preview != null && preview.complete && candidates.isNotEmpty() && approvedCleanup == null)
                    add("Eigene Diagnoseberichte entfernen: ${candidates.size} Berichte, ${preview.eligibleBytes} Byte. " +
                        "Nur nach ausdrücklicher Freigabe (VORSCHAU FREIGEBEN); Rücknahme 10 Minuten im laufenden App-Prozess.")
                if (thermalSevere) add("Lokale Inferenz pausieren: Besitzerentscheidung; vorhandene adaptive Grafikgrenzen " +
                    "und Schutzfunktionen bleiben aktiv.")
            }

            val approvedActions = if (approvedCleanup == null) emptyList()
                else listOf("Bereinigung der in der Vorschau gezeigten eigenen Diagnoseberichte.")
            val performedChanges = if (approvedCleanup != null && approvedCleanup.removedFiles > 0)
                listOf("${approvedCleanup.removedFiles} eigene Diagnoseberichte entfernt (Status ${approvedCleanup.status}).")
                else emptyList()
            val measuredResults = buildList {
                approvedCleanup ?: return@buildList
                if (approvedCleanup.removedFiles == 0) {
                    add("Bereinigungsstatus ${approvedCleanup.status}; nichts entfernt.")
                } else {
                    add("Logisch entfernt: ${approvedCleanup.logicalBytesRemoved} Byte.")
                    add("Gemessene Platzänderung: " +
                        (approvedCleanup.availableBytesDelta?.let { "$it Byte" } ?: "nicht verfügbar") + ".")
                    add("Rücknahme verfügbar: " + if (approvedCleanup.undoId != null) "ja." else "nein.")
                }
                approvedCleanup.quarantineId?.let { add("Zur Prüfung erhalten: $it; nicht als bereinigt gewertet.") }
            }

            DiagnosisEvaluation(measured, anomalies, hypotheses, candidates, cleanupNotes, requiresApproval,
                approvedActions, performedChanges, measuredResults)
        }

        private fun percent(fraction: Double) = String.format(Locale.ROOT, "%.0f", fraction * 100)
    }
}
