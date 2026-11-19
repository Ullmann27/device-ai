package at.heinz.deviceai.deepclean

import java.util.Locale

/** Wording of the storage lane. Pure: the same facts always give the same text; only measured numbers appear. */
internal object DeepCleanText {
    /** Decimal units as Android's storage settings show them („1,4 GB“). */
    fun bytes(value: Long): String {
        val v = value.coerceAtLeast(0)
        fun one(x: Double) = String.format(Locale.GERMAN, "%.1f", x)
        return when {
            v < 1_000 -> "$v Byte"
            v < 1_000_000 -> one(v / 1_000.0) + " kB"
            v < 1_000_000_000 -> one(v / 1_000_000.0) + " MB"
            else -> one(v / 1_000_000_000.0) + " GB"
        }
    }

    fun files(count: Int): String = if (count == 1) "eine Datei" else "$count Dateien"

    /** „Alles klar, Heinz.“ / „Alles klar.“ – the name only when it is known, the warm lead only in the personal tone. */
    fun lead(name: String?, personal: Boolean, words: String = "Alles klar"): String =
        if (!personal) "" else words + (name?.trim()?.takeIf { it.isNotEmpty() }?.let { ", $it" } ?: "") + ". "

    fun accessLine(access: DeepCleanAccess): String? = when (access.shared) {
        DeepCleanAccess.Shared.GRANTED -> null
        DeepCleanAccess.Shared.MISSING -> "Deinen gemeinsamen Speicher (Fotos, Downloads, Dokumente) konnte ich nicht durchsehen: " +
            "„Zugriff auf alle Dateien“ ist für Continuum nicht eingeschaltet. Geprüft habe ich nur Continuums eigene Dateien."
        DeepCleanAccess.Shared.UNSUPPORTED -> "Den gemeinsamen Speicher kann ich auf diesem Gerät nicht durchsehen."
    }

    fun otherAppsLine(access: DeepCleanAccess): String? = if (access.otherAppStats) null else
        "Wie viel Platz andere Apps belegen, zeigt Android nur in den Einstellungen unter Speicher – Continuum hat dafür keine Berechtigung."

    /** The measured volume figures, labelled as what they are. */
    fun volumeLine(before: DeepCleanVolume?, after: DeepCleanVolume?): String? = when {
        before == null && after == null -> "Den freien Speicher hat Android mir gerade nicht gemeldet."
        after == null -> "Frei sind ${bytes(before!!.freeBytes)} von ${bytes(before.totalBytes)} (Android-Messung)."
        before == null -> "Frei sind jetzt ${bytes(after.freeBytes)} von ${bytes(after.totalBytes)} (Android-Messung)."
        else -> {
            val delta = after.freeBytes - before.freeBytes
            "Android misst: vorher ${bytes(before.freeBytes)} frei, jetzt ${bytes(after.freeBytes)} – " +
                (if (delta >= 0) "${bytes(delta)} mehr" else "${bytes(-delta)} weniger") + "."
        }
    }

    fun removedLine(count: Int, bytes: Long): String = if (count == 0) "Gelöscht habe ich nichts."
        else "Entfernt habe ich ${files(count)}, zusammen ${bytes(bytes)} (Summe der Dateigrößen)."

    /** Why the two numbers can differ – said whenever both appear. */
    const val DIFFERENCE_NOTE = "Die beiden Zahlen müssen nicht gleich sein: Andere Apps schreiben in der Zeit ebenfalls, und Android rechnet in Speicherblöcken."

    fun categories(byCategory: Map<DeepCleanCategory, Pair<Int, Long>>, limit: Int = 5): String? {
        val top = byCategory.entries.filter { it.value.second > 0 }.sortedByDescending { it.value.second }.take(limit)
        if (top.isEmpty()) return null
        return "Am meisten Platz belegen: " + top.joinToString(", ") { (category, value) -> "${category.label} ${bytes(value.second)}" } + "."
    }

    fun line(index: Int, item: DeepCleanItem, finding: DeepCleanFinding?): String = buildString {
        append(index).append(". ").append(shownPath(item.root, item.relative)).append(" · ").append(bytes(item.sizeBytes))
        finding?.let { append(" · ").append(it.origin).append(" · ").append(it.reason.trimEnd('.')) }
        append(" · ").append(item.recovery.label)
    }

    fun shownPath(root: DeepCleanRoot, relative: String): String = when (root) {
        DeepCleanRoot.SHARED -> "/sdcard/$relative"
        DeepCleanRoot.OWN_CACHE -> "Continuum-Cache/$relative"
        DeepCleanRoot.OWN_EXTERNAL_CACHE -> "Continuum-Cache (extern)/$relative"
        DeepCleanRoot.OWN_FILES -> "Continuum-Daten/$relative"
    }

    fun auditLine(result: DeepCleanAudit.Result?): String? = when {
        result == null || result.checked == 0 -> null
        result.clean -> "Gegenprobe: ${result.checked} geschützte Dateien vorher und nachher verglichen – alle unverändert."
        else -> "Gegenprobe: Von ${result.checked} geschützten Dateien sind ${result.unchanged} unverändert" +
            (if (result.changed.isNotEmpty()) ", ${result.changed.size} haben sich verändert (nicht durch mich gelöscht – ich habe sie nicht angefasst, " +
                "eine andere App kann sie geändert haben)" else "") +
            (if (result.unreadable.isNotEmpty()) ", ${result.unreadable.size} konnte ich nicht nachlesen" else "") + "."
    }

    fun forecastLine(outlook: DeepCleanForecast.Outlook): String = when (outlook) {
        is DeepCleanForecast.Outlook.TooFew -> "Für eine Vorhersage habe ich noch zu wenig Messwerte (nötig sind mindestens drei an drei verschiedenen Tagen)."
        is DeepCleanForecast.Outlook.NotShrinking -> "Schätzung aus ${outlook.samples} Messungen: Dein freier Speicher nimmt derzeit nicht ab."
        is DeepCleanForecast.Outlook.FullIn -> "Schätzung aus ${outlook.samples} Messungen über ${outlook.spanDays.toInt()} Tage: Bei gleichem Verbrauch " +
            "(etwa ${bytes(outlook.bytesPerDay.toLong())} pro Tag) wäre der Speicher in rund ${outlook.days.toLong().coerceAtLeast(0)} Tagen voll. " +
            "Das ist eine Schätzung, keine Messung."
    }

    const val CLARIFY = "Meinst du den Speicher deines Handys? Ich kann ihn nur prüfen („Schau, was unnötig Speicher verbraucht“), " +
        "sicher bereinigen („Bereinige mein Handy“ – dann entferne ich nur Continuums eigene, eindeutig entbehrliche Zwischendateien) " +
        "oder gründlich durchsuchen („Mach eine Tiefenbereinigung“ – Fotos, Downloads und Dokumente lösche ich dabei nur, was du einzeln auswählst)."
    const val CLARIFY_ALL = "Alles auf einmal lösche ich nicht – darunter wären deine Fotos, Dokumente und Continuums Gedächtnis. " +
        "Ich kann deinen Speicher prüfen oder sicher bereinigen; persönliche Dateien lösche ich nur, wenn du genau sie auswählst."
    const val DECLINE = "In Ordnung, ich bereinige nichts und lösche nichts."
    const val READ_ONLY_PICK = "Du hattest gesagt, dass ich nur prüfen soll – deshalb lösche ich davon nichts. " +
        "Wenn du Dateien davon loswerden willst, sag zum Beispiel „Lösch die doppelten Dateien“."
}

/** What Android lets the app see right now. */
internal data class DeepCleanAccess(
    val shared: Shared,
    /** StorageStatsManager for other packages (PACKAGE_USAGE_STATS) – the app does not declare it. */
    val otherAppStats: Boolean = false,
    /** MediaStore.createTrashRequest exists (Android 11+). */
    val trash: Boolean = true,
    /** StorageManager.ACTION_CLEAR_APP_CACHE may be offered (Android 11+ with all-files access). */
    val clearCacheDialog: Boolean = false
) {
    enum class Shared { GRANTED, MISSING, UNSUPPORTED }
}

/** Free and total bytes of a volume as Android measured them at [atMillis]. */
internal data class DeepCleanVolume(val freeBytes: Long, val totalBytes: Long, val atMillis: Long)

/** The answer of a system dialog: [granted] only when Android reported success. */
internal data class DeepCleanSystemResult(val granted: Boolean, val reason: String? = null)
