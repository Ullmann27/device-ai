# Continuum · Modell-Webrecherche → überprüfbare Quellenhinweise

Stand: 2026-10-29. Isolierter Entwicklungszweig, noch keine Gerätefreigabe.

## Geschlossene Verbindung

Der bestehende normale Chat benutzt `ToolGate` und `ApprovedWebResearchToolRunner`.
Die einmal freigegebene öffentliche Recherche erzeugt ein `ResearchDossier`, das
bislang lediglich als Tooltext an das lokale Modell ging. Im neuen Weg wird
es nach erfolgreicher, noch gültiger Freigabe zusätzlich in
`AuthorizedWebLearningReviewInbox` gehalten. `INTERNET · LERNSTOFF PRÜFEN`
zeigt diese Kandidaten über die bereits vorhandene Reviewoberfläche an.
Es werden weder Lerngewichte noch Fakten noch Dateiinhalte automatisch geändert.

## Grenzen und Prüfungen

- Der Zwischenspeicher ist nur pro Activity vorhanden, begrenzt auf acht Kandidaten.
- Beim Start einer neuen modellgesteuerten Webrecherche wird der alte Stand geleert.
- Kandidaten werden bei widerrufener Internetfreigabe nicht mehr zur Review angeboten;
  vor Übernahme und Neuron-Coach-Übergabe wird dieselbe Berechtigung erneut geprüft.
- Verlinkte Textbelege bleiben unbestätigte Fremdinhalte; eine Übernahme ist eine
  ausdrückliche Besitzerentscheidung als Hinweis, keine Wahrheitsbestätigung.
- Vorhandene lokal gespeicherte Dossiers und die 24 bereits möglichen
  Besitzer-Quellnotizen bleiben unangetastet.
- Nutzergeprüfte Dateien sind kein Beweis für einen direkt ausgeführten nativen
  Gemma-4-Token-Callback.

## Bisherige Verifikation

Drei neue JUnit-Methoden wurden in `AuthorizedWebLearningReviewInboxTest.kt`
hinzugefügt: gültige Freigabe, Verweigerung und Entzug vor Review, neuer
Suchlauf entwertet alte Belege. Der eigenständige Klassenalgorithmus ist mit
Kotlin-Compiler und acht Smoke-Assertions lokal durchgelaufen (mit minimalen
Typ-Fixtures). **Das ersetzt keinen Gradle-Android-Gesamttest**; Kotlin/Java-
Vollkompilierung des neuen Branches, Fold7-Gemma- und UI-Tests stehen noch aus.

## Unverändert

Keine Produktion-/Main-Änderung, keine APK und keine Fold7-Installation.
PR #236 (Owner-Updater) bleibt getrennt; Original-P12 nur lokal auf Fold7.
PR #237 ist geprüfte Baseline, PR #238 bleibt als DAN-Forschungsauftrag offen.
