# Device AI Continuum – hier beginnen

## Sofort ansehen

Öffne die bestätigte private Webvorschau aus der Auslieferungsnachricht. Alle Projektdateien stehen dort unter „Projektdateien“. Die gesondert gelieferte Continuum_Vorschau.html enthält die vollständige Offlineansicht. Die neue Fassung trägt oben die Kennung KOMMANDO / 03. Mit „Recherche erleben“ startet ein steuerbarer Beispielablauf; „Eigene Datei öffnen“ liest tatsächliche lokale Textdateien. Die Seite ausprobieren.html bietet Breit-, Fold- und Schmalansicht.

## Bearbeitbare App starten

Node.js 20 oder neuer. Im entpackten Projektordner:

```sh
cd continuum-preview
npm start
```

Es werden keine Laufzeitpakete installiert. Der Server nennt die lokale Adresse. Der Originalkopf wird vor dem Start auf seine Prüfsumme geprüft.

## Aufbau

- continuum-preview: fertige App einschließlich Originalmodell, Originalkugel, lokaler Schriften und Herkunftshinweise.
- interop: lesbare Adapter, unveränderte Kotlin-Originalquellen, reproduzierbare Builds und gezielte Prüfungen.
- handoff-3d: bearbeitbare Blender-Szenen, Display-GLB, Renderbilder und Builder.
- qa: Bedienprüfungen und tatsächliche Ergebnisse.
- Continuum_Designuebergabe.md: Aufbau, Entscheidungen und verbleibende Grenzen.
- Continuum_Toolkette_und_Uebergabe.md: konkrete Schritte und Prompts für die nächsten Anwendungen.

Der Webexport ist ein zusätzliches ZIP mit index.html direkt an der Wurzel; er eignet sich für statisches Hosting. Das Projektpaket enthält außerdem Quellen und 3D-Arbeitsdateien. Die Android-App und ihr Signierschlüssel werden hier nicht verändert.
