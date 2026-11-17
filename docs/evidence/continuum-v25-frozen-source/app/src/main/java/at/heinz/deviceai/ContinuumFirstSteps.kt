package at.heinz.deviceai

/**
 * Einsteigerführung mit überprüfbaren Möglichkeiten und ehrlich gekennzeichneten Grenzen.
 * Reine Inhalte ohne Netzwerk- oder Geräteaktionen; die Activity öffnet bestehende Dialoge.
 */
data class ContinuumGuideStep(
    val title: String,
    val description: String,
    val actionTitle: String,
    val action: ContinuumGuideAction
)

enum class ContinuumGuideAction {
    CHAT, INTERNET, LEARNING, MODELS, DEVELOPMENT, STATUS
}

object ContinuumFirstSteps {
    val steps: List<ContinuumGuideStep> = listOf(
        ContinuumGuideStep(
            "Deine eigene KI kennenlernen",
            "Du kannst sofort mit dem lokalen Startkern schreiben. Er erkennt einfache Aufgaben, " +
                "ersetzt aber noch kein großes Sprachmodell. Schreibe zum Beispiel: Was kannst du?",
            "CHAT ÖFFNEN", ContinuumGuideAction.CHAT
        ),
        ContinuumGuideStep(
            "Im gesamten öffentlichen Web recherchieren",
            "Nutze die Forschungssuche für Fragen und öffentlich erreichbare HTTPS-Seiten. " +
                "Schnell, Standard und Tief unterscheiden sich in Dauer und Anzahl der Quellen. " +
                "Die Websuche verwendet Suchanbieter und kann Seiten lesen; manche Webseiten " +
                "sind technisch nicht erreichbar oder verlangen eine Anmeldung. " +
                "Du kannst einmalig freigeben oder eigenständige öffentliche Recherche erlauben. " +
                "Gefundene Aussagen erhalten Quellenhinweise und sind nicht automatisch sichere Fakten.",
            "WEBRECHERCHE STARTEN", ContinuumGuideAction.INTERNET
        ),
        ContinuumGuideStep(
            "Wissen und Lernen verstehen",
            "Der Lernkern verarbeitet Beispiele und überprüft sie mit Kontrollaufgaben. " +
                "Webquellen werden mit Herkunft gespeichert, auch wenn ihre Aussagen noch unbestätigt sind. " +
                "Mit 'Merke dir: ...' kannst du eigene Tatsachen ausdrücklich bestätigen. " +
                "AUTO-ÜBEN bedeutet geplante Wiederholung; Android garantiert keine exakte Startzeit.",
            "LERNZENTRUM ANSEHEN", ContinuumGuideAction.LEARNING
        ),
        ContinuumGuideStep(
            "Ein lokales Sprachmodell hinzufügen",
            "Für freiere Gespräche und eigene Codevorschläge benötigt Continuum eine " +
                "kompatible LiteRT-LM-Modelldatei. Sie ist nicht automatisch enthalten. " +
                "Der optionale Groq-Mentor funktioniert getrennt über das Internet, " +
                "benötigt die Gerätefreigabe und kann keine lokalen Werkzeuge bedienen.",
            "MODELLE ANSEHEN", ContinuumGuideAction.MODELS
        ),
        ContinuumGuideStep(
            "Deinen Baumeister kennenlernen",
            "Im Chat erkennt Continuum 'Worker Status' und 'Worker: Erstelle eine Notizen-App'. " +
                "Dadurch werden lokale Entwicklungsaufgaben vorbereitet, nicht automatisch ausgeführt. " +
                "Erst eine getrennte Werkbank mit Compiler und echten Tests kann APK-Kandidaten bauen. " +
                "Änderungen, Installationen und Gerätebefehle brauchen weiterhin eigene Freigaben.",
            "ENTWICKLUNGSZENTRUM", ContinuumGuideAction.DEVELOPMENT
        ),
        ContinuumGuideStep(
            "Alles jederzeit kontrollieren",
            "Die Bereiche Chat, Forschung, Lernen, Modelle, Entwicklung und Speicher " +
                "sind voneinander getrennt. Unter System kannst du Internetmodus, " +
                "Geräteberechtigungen und Sicherheit prüfen. " +
                "Ein grüner Status bedeutet nur, dass die jeweilige Prüfung erfolgreich war, " +
                "nicht dass schon ein vollautonomer Coding-Agent läuft.",
            "SYSTEMSTATUS", ContinuumGuideAction.STATUS
        )
    )
}