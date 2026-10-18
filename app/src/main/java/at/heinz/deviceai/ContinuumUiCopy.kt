package at.heinz.deviceai

/**
 * Plain-language, deterministic UI guidance.
 * This layer never changes authorization, inference, state or evidence.
 */
object ContinuumUiCopy {
    fun developmentRequirements(modelReady: Boolean, hasGoal: Boolean, sources: Int): String {
        require(sources >= 0)
        return listOf(
            if (modelReady) "✓ KI-Sprachmodell ist bereit."
            else "1. Sprachmodell fehlt. Tippe auf „Sprachmodell einrichten“ und lade eine kompatible LiteRT-LM-Datei.",
            if (hasGoal) "✓ Entwicklungsziel eingegeben."
            else "2. Beschreibe kurz, was verbessert werden soll.",
            if (sources > 0) "✓ $sources Quelldatei(en) ausgewählt."
            else "3. Wähle mindestens eine Quelldatei über „Dateien auswählen“.",
            "Nur ein Änderungsvorschlag: Die App führt damit weder Builds noch automatische Codeänderungen aus."
        ).joinToString("\n")
    }

    fun developerModelMissing(): String =
        "Es ist noch kein KI-Sprachmodell geladen. Öffne „Sprachmodell einrichten“, importiere " +
            "eine kompatible LiteRT-LM-Datei und lade sie. Danach kannst du den Entwurf starten. " +
            "Es gibt keinen automatischen Cloud-Ersatz."

    fun emptyResearch(partialReason: String?): String = when {
        partialReason?.contains("large", ignoreCase = true) == true ||
            partialReason?.contains("byte budget", ignoreCase = true) == true ->
            "Die gefundenen Webseiten waren zu groß. Es wurden keine überprüfbaren Inhalte übernommen. " +
                "Versuche eine gezielte Frage oder gib eine direkte HTTPS-Adresse ein."
        partialReason?.contains("timeout", ignoreCase = true) == true ||
            partialReason?.contains("deadline", ignoreCase = true) == true ->
            "Die Recherche hat zu lange gedauert. Bitte prüfe die Internetverbindung und versuche es erneut."
        partialReason?.contains("not configured", ignoreCase = true) == true ->
            "Für diese Suche ist derzeit kein passender Suchdienst eingerichtet. " +
                "Du kannst stattdessen eine öffentlich erreichbare HTTPS-Adresse angeben."
        else ->
            "Es konnten keine verlässlichen Quellen geladen werden. Prüfe die Internetverbindung, " +
                "versuche einen genaueren Suchbegriff oder gib eine HTTPS-Adresse an."
    }

    fun researchFailure(reason: String?): String = when {
        reason?.contains("too large", ignoreCase = true) == true -> emptyResearch(reason)
        reason?.contains("timeout", ignoreCase = true) == true ||
            reason?.contains("deadline", ignoreCase = true) == true -> emptyResearch(reason)
        reason?.contains("cancel", ignoreCase = true) == true ||
            reason?.contains("abgebrochen", ignoreCase = true) == true ->
            "Die Recherche wurde beendet. Bereits gespeicherte Quellen bleiben erhalten."
        else ->
            "Die Recherche konnte nicht abgeschlossen werden. Versuche es später erneut; " +
                "bisher gespeicherte Informationen bleiben unverändert."
    }

    fun deviceBridgeMissing(): String =
        "Für diese Gerätefunktion brauchst du die App Shizuku mit aktiver Verbindung. " +
            "Öffne den Systembereich, richte Shizuku ein und versuche deinen Auftrag erneut. " +
            "Deine Eingabe bleibt stehen."

    fun modelStatus(ready: Boolean): String =
        if (ready) "Sprachmodell bereit" else "Freies Sprachmodell noch nicht geladen"
}
