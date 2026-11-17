package at.heinz.deviceai

/**
 * Plain-language, deterministic UI guidance.
 * This layer never changes authorization, inference, state or evidence.
 */
object ContinuumUiCopy {
    fun cdxUnavailableStatus(conditioningState: String): String = when (conditioningState) {
        "CORRUPT" -> "CDX1-Checkpoint oder SWB1-Bindung beschädigt bzw. nicht lesbar. " +
            "Vor weiterem Training zuerst Diagnose und Sicherung prüfen. Es wurde nichts zurückgesetzt."
        "RECOVERED_LKG" -> "CDX1-Sicherung ist nur lesend verfügbar. " +
            "Vor weiterem Training die gezielte Wiederherstellung prüfen."
        "NO_CHECKPOINT" -> "CDX1 hat noch keinen eigenen Forschungscheckpoint. Starte erst das Paarlabor."
        else -> "CDX1-Lesetest derzeit nicht verfügbar. Prüfe den Bindungsstatus im Lernzentrum. " +
            "Es wurde nichts trainiert oder zurückgesetzt."
    }

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

    /**
     * #567 P0-12: a general question without a matching memory and without a loaded local model is not a memory
     * gap. Add what is really missing; the original memory reply and its guidance stay. Exact recalls stay unchanged;
     * with a ready model this path is not reached for general questions (LocalFirstPolicy routes them to the model).
     */
    fun memoryFallbackAnswer(answer: String, grounded: Boolean, modelReady: Boolean): String =
        memoryFallbackAnswer(answer, grounded, if (modelReady) at.heinz.deviceai.localmodel.LocalModelState.READY
            else at.heinz.deviceai.localmodel.LocalModelState.EMPTY)

    /**
     * Fold7 (#642): „nicht geladen“ appeared right after a successful Gemma test because the runtime was still
     * GENERATING/LOADING/UNLOADING. The hint now names the real state instead of claiming the model is absent.
     */
    fun memoryFallbackAnswer(answer: String, grounded: Boolean, modelState: at.heinz.deviceai.localmodel.LocalModelState): String {
        if (grounded || modelState == at.heinz.deviceai.localmodel.LocalModelState.READY ||
            answer != at.heinz.deviceai.learning.ContinuumMemoryDialogue.NO_EVIDENCE_ANSWER) return answer
        // The memory reply stays first (personal questions and revoked memories keep the „Merke dir“ guidance);
        // the model's actual state is added as the real reason a general question cannot be answered now.
        val reason = when (modelState) {
            at.heinz.deviceai.localmodel.LocalModelState.GENERATING ->
                "Das lokale Sprachmodell ist geladen, arbeitet aber gerade noch an einer anderen Antwort. " +
                    "Bitte kurz warten und die Frage dann noch einmal stellen."
            at.heinz.deviceai.localmodel.LocalModelState.LOADING ->
                "Das lokale Sprachmodell wird gerade geladen. Bitte kurz warten und die Frage dann noch einmal stellen."
            at.heinz.deviceai.localmodel.LocalModelState.UNLOADING ->
                "Das lokale Sprachmodell wird gerade entladen. Allgemeine Wissensfragen kann ich erst beantworten, " +
                    "wenn du es in der Modellverwaltung wieder lädst."
            at.heinz.deviceai.localmodel.LocalModelState.FAILED ->
                "Das lokale Sprachmodell ist nach einem Fehler nicht einsatzbereit. Bitte in der Modellverwaltung neu laden."
            else -> "Das lokale Sprachmodell ist gerade nicht geladen. Allgemeine Wissensfragen kann ich " +
                "erst beantworten, wenn du es in der Modellverwaltung lädst."
        }
        return answer + "\n\nHinweis: " + reason + " Ich habe dafür nicht im Internet gesucht."
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

    fun modelFailure(problem: String?): String = when {
        problem == at.heinz.deviceai.localmodel.LocalModelHost.PAUSED_MEMORY ->
            "Keine neue Modellantwort gestartet: Android meldet zu wenig Arbeitsspeicher. " +
                "Schließe andere Apps und frage erneut."
        problem == at.heinz.deviceai.localmodel.LocalModelHost.MEMORY_NO_MEASUREMENT ->
            "Keine neue Modellantwort gestartet: Die Arbeitsspeichermessung ist nicht verfügbar. " +
                "Versuche es später erneut."
        problem == "LOCAL_MODEL_RESPONSE_EXCEEDS_AGENT_LIMIT" ->
            "Die lokale Modellantwort überschreitet das Ausgabelimit. " +
                "Bitte fordere eine kürzere Antwort an oder teile die Aufgabe in kleinere Schritte."
        problem == "LOCAL_MODEL_UNLOAD_REQUIRED" ->
            "Das bisherige Modell wurde noch nicht vollständig entladen. " +
                "Entlade es zuerst in der Modellverwaltung. Lade das gewünschte Modell erst " +
                "nach erfolgreichem Entladen erneut."
        problem?.contains("sha", ignoreCase = true) == true ||
            problem?.contains("checksum", ignoreCase = true) == true ||
            problem?.contains("digest", ignoreCase = true) == true ->
            "Die Datei konnte nicht anhand des erwarteten SHA-256-Prüfwerts bestätigt werden. " +
                "Vergleiche die Datei und den Prüfwert mit den Angaben des Anbieters."
        problem?.contains("unsupported", ignoreCase = true) == true ||
            problem?.contains("incompatible", ignoreCase = true) == true ||
            problem?.contains("compatib", ignoreCase = true) == true ->
            "Dieses Modell ist möglicherweise nicht mit der eingebauten LiteRT-LM-Laufzeit kompatibel. " +
                "Prüfe Modellformat und Geräteunterstützung."
        problem?.contains("memory", ignoreCase = true) == true ->
            "Für dieses Modell steht möglicherweise nicht genügend Arbeitsspeicher zur Verfügung. " +
                "Schließe andere Apps oder verwende ein kleineres kompatibles Modell."
        problem?.contains("timeout", ignoreCase = true) == true ||
            problem?.contains("exceeded", ignoreCase = true) == true ->
            "Der Vorgang hat zu lange gedauert. Prüfe den verfügbaren Speicher und starte die Modellverwaltung erneut."
        problem?.contains("busy", ignoreCase = true) == true ||
            problem?.contains("in progress", ignoreCase = true) == true ->
            "Die Modellverwaltung arbeitet noch. Warte kurz und versuche es anschließend erneut."
        else -> "Das Modell konnte nicht verarbeitet werden. Prüfe Dateiformat, Modellversion und Gerätespeicher."
    }

    /** Cockpit footer for tool calls whose outcome is unknown and awaits the owner (German, honest count). */
    fun pendingToolReview(count: Int): String =
        if (count == 1) "1 Werkzeugaufruf wartet auf deine Freigabe · Ergebnis unbekannt, kein automatischer Neuversuch"
        else "$count Werkzeugaufrufe warten auf deine Freigabe · Ergebnis unbekannt, kein automatischer Neuversuch"

    fun modelStatus(ready: Boolean): String =
        if (ready) "Sprachmodell bereit" else "Freies Sprachmodell noch nicht geladen"
}
