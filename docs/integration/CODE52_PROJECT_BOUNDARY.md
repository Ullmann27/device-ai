# Code 52 – verbindliche Projektgrenze

Stand: 09.11.2026. Dieser Branch dient ausschließlich der Bereinigung projektfremder
Spezialfälle innerhalb **Device AI Continuum**.

## Nachgewiesene Ausgangslage

- Eine unbenutzte, fest an eine andere App gebundene Skill-Implementierung
  war im allgemeinen Android-Client vorhanden.
- Ältere Shell-Beispiele und ein fest eingebauter APK-Suchfilter waren auf
  dieses andere Projekt zugeschnitten.
- Ein inaktiver, nicht an den App-ToolRouter angeschlossener Entwurf für
  Tippen, Wischen und Texteingabe besaß eine irreführende Projektzuordnung.
- Aus diesen Fragmenten folgt weder ein auf dem Fold7 ausführbarer
  Bildschirmsteuerungsauftrag noch ein funktionierender Messenger-Sendepfad.

## Trennung und Gültigkeitsbereich

Unbenutzte app-fremde Skill-Registrierungen, Beispieltexte, Sonderfilter und
irreführende Querverweise wurden aus der aktiven Quelllinie entfernt.
Die generischen Android-Gerätewerkzeuge (App suchen/öffnen, Bildschirm
mit Nutzerfreigabe beobachten), die Android-Bedienungshilfen und Shizuku
bleiben als **Continuum-eigene Verträge** unverändert.

Keine Kopie von Quellcode, Modellen, persönlichen Daten, Task-History oder
Signiermaterial aus unabhängigen anderen Projekten. Historische Git-Belege
werden nicht als funktionsfähiger aktueller Runtime-Pfad gewertet.

## Offener Continuum-Android-Integrationsbefund

- `ToolRegistry`: `find_app`, `launch_app`, `read_app_screen`, aber
  weder `send_message` noch eine allgemein registrierte Texteingabe.
- `ContinuumScreenObservationService`: Window-Events, begrenztes Lesen,
  geschützter Klick-Kandidat, kein produktiver Texteingabe-Port.
- `chief/DeviceControl.kt`: isolierte, nicht registrierte Typen/Router,
  kein belegter produktiver Android-Adapter und keine verknüpfte Owner-UI.
- WhatsApp-Nachrichten erfordern zunächst eindeutig bestätigtes Ziel,
  Entwurf/Empfänger-Anzeige und eine gesonderte Sendebestätigung.
  Ohne unabhängigen Sendebeleg kein `SENT`, keine automatische Wiederholung.
- **Root ist für die Android-Accessibility-Basismöglichkeiten nicht
  erforderlich.** Gesonderte Nutzeraktivierung bleibt zwingend;
  privilegierte ADB-/Shizuku-/Root-Aktionen werden nicht als universal
  verfügbar behauptet.

## Teststatus

Quellbereinigung ist im Review-Branch sichtbar. Separater
`code52-project-isolation.test.mjs`-Vertrag deckt die betroffenen
Produktionspfade und generische Tool-Namen ab.
`node --test`, Android-Compile, vollständige Regressionen und physische
Fold7-Aktionen: **NOT_RUN**. Kein APK-Build, kein Main-Merge, keine Signatur.
