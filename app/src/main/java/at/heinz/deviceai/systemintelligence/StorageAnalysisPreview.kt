package at.heinz.deviceai.systemintelligence

/** Local UI only; never tool output. */
fun StorageAnalysis.localPreview(): String = buildString {
    append("LOKALE DATEIVORSCHAU · ausgewählter Bereich")
    val videos = files.count { it.document.mimeType.startsWith("video/") }
    val pictures = files.count { it.document.mimeType.startsWith("image/") }
    append("\nVideos: $videos; Bilder: $pictures; sonstige Dateien: ${files.size - videos - pictures}.")
    append("\nGrößte ausgewählte Dateien (höchstens 12):")
    files.sortedByDescending { it.document.sizeBytes ?: -1L }.take(12).forEach {
        append("\n• ${safeLocalName(it.document.displayName)} · ${SystemDiagnosis.bytes(it.document.sizeBytes)}")
    }
    if (files.size > 12) append("\n${files.size - 12} weitere Dateien erfasst.")
    if (duplicates.isNotEmpty()) {
        append("\n\nInhaltlich bestätigte Duplikate (höchstens 8 Gruppen):")
        val byId = files.associateBy { it.document.id }
        duplicates.take(8).forEachIndexed { index, group ->
            append("\nGruppe ${index + 1} · ${group.documentIds.size} Dateien · je ${SystemDiagnosis.bytes(group.bytesPerFile)}")
            group.documentIds.take(8).forEach { id ->
                append("\n  ${safeLocalName(byId[id]?.document?.displayName ?: "Datei nicht mehr zugeordnet")}")
            }
            if (group.documentIds.size > 8) append("\n  ${group.documentIds.size - 8} weitere Gruppeneinträge.")
        }
        if (duplicates.size > 8) append("\n${duplicates.size - 8} weitere Gruppen erfasst.")
        val potential = runCatching {
            duplicates.fold(0L) { sum, group ->
                Math.addExact(sum, Math.multiplyExact(group.bytesPerFile,
                    (group.documentIds.size - 1).coerceAtLeast(0).toLong()))
            }
        }.getOrNull()
        append("\nRechnerisch mehrfach gespeicherter Inhalt: ${SystemDiagnosis.bytes(potential)}.")
        append(" Kein freigegebener Löschplan; Dateibedeutung und Verweise sind ungeprüft.")
    }
    append("\n\nKeine Datei gelöscht. Größe oder Alter beweisen keine Nichtbenutzung.")
    if (!complete) append(" Die Analyse ist begrenzt; ungeprüfte Inhalte gelten nicht als Duplikate.")
}

private fun safeLocalName(name: String): String = name.filterNot {
    it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()
}.take(120).ifBlank { "Datei ohne Anzeigenamen" }
