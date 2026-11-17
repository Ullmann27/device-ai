# Continuum autonome öffentliche Internetforschung (experimentell)

Stand 20.10.2026. Explizite Besitzerfreigabe **in der App** erforderlich,
Standard AUS. Bereits bestehende GROQ- und Websuche-Einzelfreigaben
bleiben vollständig getrennt.

## Anbindung
LERNEN → AUTONOME INTERNETFORSCHUNG · EIN/AUS oder
LERNKERN → GEWICHTE / LABOR → AUTONOME INTERNETFORSCHUNG.

Die Android-JobScheduler-Aufgabe `620137` wird nach expliziter Zustimmung
ungefähr stündlich mit *requiresCharging*, Batterie-/Speicher-Grenzen
und `NETWORK_TYPE_ANY` geplant. Sie funktioniert auch über Mobilfunk,
nicht ausschließlich WLAN. Android kann jeden Lauf verschieben oder
abbrechen; „endlos laufend“ bedeutet hier geplante wiederkehrende
Arbeit und nie eine CPU-Dauerschleife.

Pro Zyklus wird maximal **eine** der 48 festgelegten Wikipedia-Seiten
über eine streng festgelegte HTTPS-API-Adresse abgerufen; keine
beliebigen Benutzer-URLs, Redirects, Geräte-Zugangsdaten oder
benutzerbezogene Daten. Antwort ≤96 KiB, begrenzte Netzwerk-Timeouts
und ≤15 s gesamte Laufzeit. Auszüge werden normalisiert,
in SHA-256 gebunden und als `SOURCE_OBSERVATION` im
`MentorSourceNotebook` gesammelt. Vollständige Webseiten werden
nicht als Modell-Wissen oder Sicherheitsanweisung anerkannt.

`ContinuumSourceNovelty` erkennt unveränderte Quellversionen.
Neue Auszüge laufen durch den vorhandenen
`ContinuumLearningCore.learnFromSources`: Quell-Replay +
Trainings- und unabhängige Holdout-Vergleiche. Nur bei nachgewiesener
Qualitätsverbesserung werden aktive SWB1-Gewichte geändert und vorher
archiviert. Bei Ablehnung bleiben die Modellgewichte erhalten.

Weder Prompts noch Webseiten haben Android-Geräteautorität; kein
Commit/Merge, kein Root, keine Shell, keine externen Modell-APIs.
Private Lernbeispiele/Owner-Fakten werden niemals unbemerkt
als öffentliche Trainingsdaten behandelt.

**Die SWB1-Parameterzahl bleibt 4.113.** Dieses Feature erweitert den
quellengebundenen Wissensstand und kann *Gewichtswerte* verbessern,
jedoch nicht die Architektur. Adaptive zusätzliche Parameter erfordern
einen separaten versionierten Forschungskern mit eigenem Qualitätsgate.

Abnahme: Test `ContinuumPublicStudyPolicyTest`; Android-Build,
Logcat/JobScheduler-Zeitplan, Mobilfunk und WLAN, OFF-Schalter,
Quota-/Thermal-Tests und Lernholdouts am echten Samsung vor
Produktionsfreigabe prüfen.
