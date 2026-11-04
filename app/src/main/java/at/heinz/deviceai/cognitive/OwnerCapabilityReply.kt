package at.heinz.deviceai.cognitive

import at.heinz.deviceai.ui.ContinuumDashboardState

/** Public operational facts only. Never accepts prompts, private bot notes, memory or project titles. */
internal data class OwnerCapabilitySnapshot(
    val modelState: ModelState = ModelState.UNKNOWN,
    val loadedModelName: String? = null,
    val importedModelCount: Int? = null,
    val onDeviceSpeechAvailable: Boolean? = null,
    val microphoneGranted: Boolean? = null,
    val internetMode: InternetAccessMode? = null,
    val networkOnline: Boolean? = null,
    val runningTasks: Int? = null,
    val failedTasks: Int? = null,
    val builderRunning: Boolean? = null,
    val selectedBot: Boolean? = null,
    val botTurnActive: Boolean? = null
) {
    enum class ModelState { UNKNOWN, NOT_LOADED, LOADING, READY, GENERATING, UNLOADING, FAILED }
}

/** Reuses the canonical collector's most recent value without calling the collector (which writes history).
 * Fresh runtime flags always outrank cached dashboard presentation; expired telemetry becomes unknown. */
internal object OwnerCapabilityReply {
    const val MAX_DASHBOARD_AGE_MS = 15_000L

    fun withDashboard(
        live: OwnerCapabilitySnapshot,
        dashboard: ContinuumDashboardState?,
        capturedAtElapsedMs: Long?,
        nowElapsedMs: Long
    ): OwnerCapabilitySnapshot {
        val age = capturedAtElapsedMs?.let { nowElapsedMs - it }
        val fresh = dashboard.takeIf { age != null && age in 0..MAX_DASHBOARD_AGE_MS }
        return live.copy(
            networkOnline = fresh?.system?.networkOnline,
            runningTasks = fresh?.development?.runningTasks?.takeIf { it >= 0 },
            failedTasks = fresh?.development?.errors?.takeIf { it >= 0 }
        )
    }

    /** Null preserves ordinary conversation/tutor routing; status never calls inference or any tool. */
    fun answer(message: String, snapshot: OwnerCapabilitySnapshot, coachActive: Boolean = false): String? {
        if (OwnerChatIntentRouter.route(message, coachActive) != OwnerChatLocalIntent.CAPABILITIES) return null
        return describe(snapshot)
    }

    fun describe(s: OwnerCapabilitySnapshot): String = buildString {
        append("Mein aktueller Stand auf diesem Gerät:\n\n")
        append(when (s.modelState) {
            OwnerCapabilitySnapshot.ModelState.UNKNOWN -> "Lokales Sprachmodell: Laufzeitstatus unbekannt."
            OwnerCapabilitySnapshot.ModelState.NOT_LOADED -> "Lokales Sprachmodell: keines geladen. Der Startkern ersetzt kein frei formulierendes Sprachmodell."
            OwnerCapabilitySnapshot.ModelState.LOADING -> "Lokales Sprachmodell: wird geladen und ist noch nicht bereit."
            OwnerCapabilitySnapshot.ModelState.READY -> if (s.loadedModelName.isNullOrBlank())
                "Lokales Sprachmodell: Bereitschaft gemeldet, Modellidentität unbekannt."
            else "Lokales Sprachmodell: ${safeModelName(s.loadedModelName)} ist geladen und bereit. Die Antwortqualität ist damit nicht geprüft."
            OwnerCapabilitySnapshot.ModelState.GENERATING -> "Lokales Sprachmodell: gerade mit einer Antwort beschäftigt."
            OwnerCapabilitySnapshot.ModelState.UNLOADING -> "Lokales Sprachmodell: wird entladen und ist nicht bereit."
            OwnerCapabilitySnapshot.ModelState.FAILED -> "Lokales Sprachmodell: Laufzeitfehler; Modellzentrum und Diagnose prüfen."
        })
        append("\nImportierte Modelle: ").append(s.importedModelCount?.takeIf { it >= 0 }?.toString() ?: "unbekannt")
            .append(". Ein Import allein bedeutet keine ausführbare KI.")
        append("\n\nSpracheingabe: ").append(when (s.onDeviceSpeechAvailable) {
            null -> "Verfügbarkeit der lokalen Erkennung unbekannt."
            false -> "lokaler Android-Spracherkenner nicht verfügbar; bitte tippen und die lokale Sprachunterstützung in Android prüfen."
            true -> when (s.microphoneGranted) {
                true -> "lokaler Android-Spracherkenner vorhanden und Mikrofon freigegeben. Start nur über die Mikrofontaste; erkannter Text bleibt zuerst im Eingabefeld."
                false -> "lokaler Android-Spracherkenner vorhanden; Mikrofonberechtigung fehlt. Über die Mikrofontaste ausdrücklich anfordern."
                null -> "lokaler Android-Spracherkenner vorhanden; Mikrofonberechtigung unbekannt."
            }
        })
        append("\n\nInternet: ").append(when (s.internetMode) {
            InternetAccessMode.OFF -> "in der App gesperrt."
            InternetAccessMode.ASK -> "Freigabe pro Anfrage erforderlich."
            InternetAccessMode.ALWAYS_ON -> "öffentliche Recherche grundsätzlich erlaubt; bestehende Tool- und Datenschutzgrenzen gelten weiter."
            null -> "Freigabestatus unbekannt."
        })
        append(" Verbindung: ").append(when (s.networkOnline) { true -> "zuletzt bestätigt"; false -> "zuletzt offline"; null -> "unbekannt oder nicht aktuell gemessen" }).append('.')
        append("\n\nEntwicklung: ").append(count(s.runningTasks)).append(" laufende Backlog-Aufträge, ")
            .append(count(s.failedTasks)).append(" fehlgeschlagene Aufträge. Handy-Builder: ")
            .append(when (s.builderRunning) { true -> "Auftrag ausstehend"; false -> "kein ausstehender Auftrag gemeldet"; null -> "Status unbekannt" })
            .append(". Das belegt keinen erfolgreichen Build auf diesem Handy.")
        append("\n\nBots: ").append(when (s.selectedBot) { true -> "ein Profil ausgewählt"; false -> "kein Profil ausgewählt"; null -> "Auswahl unbekannt" })
            .append("; ").append(when (s.botTurnActive) { true -> "ein Bot-Dialog läuft"; false -> "kein Bot-Dialog aktiv"; null -> "Dialogstatus unbekannt" })
            .append(". Gespeicherte Profile sind keine unabhängigen laufenden KI-Worker.")
        append("\n\nWerkzeuge und Geräteaktionen brauchen weiterhin die bestehenden Berechtigungen und Freigaben. Diese Statusantwort startet kein Modell, keine Recherche, kein Mikrofon und keinen Entwicklungsauftrag.")
    }

    private fun count(value: Int?): String = value?.takeIf { it >= 0 }?.toString() ?: "unbekannt viele"
    private fun safeModelName(value: String): String = value.take(100).map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
}
