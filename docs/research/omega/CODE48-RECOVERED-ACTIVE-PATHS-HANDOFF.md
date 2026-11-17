# Code48 — wiedergefundene aktive Zweigänderungen

Status: WIP / UNVERIFIED. Start-Checkpoint 2026-11-07.
Basis: `75d9a7b719915bf81b2fc5bd0eecebc48908ad46` aus PR591.
Arbeitszweig: `codex/continuum-code48-recovered-active-paths-20261107`.

## Anlass und begrenzter Umfang

Die drei unabhängigen Prüfungen von 33 zusätzlichen Zweigköpfen vergleichen
bereits vorhandene Dateinamen mit abweichenden Inhalten. Die Primärblobs
wurden gezielt gelesen und gegen ihre Git-Blob-SHA geprüft.

- PR528: Aktuelle Quellen im 48er-Replay priorisieren, vollständige Batch vor
  Mutation validieren und höchstens 17 eindeutige Quellen zulassen. Eine
  archivierte oder im Ledger vorhandene Quelle ist noch keine abgeschlossene
  Trainingsrunde. Persistierte Start-/Abschlussmarker sollen unterbrochene
  Runden wieder auswählbar machen.
- PR531: Fehlende Credential-Aliasse sowie Bearer-Zeilengrenzen im bestehenden
  AiContextCompiler. Bytebudget, Identität und Datenschutzrahmen erhalten.
- PR558: Der aktuelle Brain-Scene-Caller liefert bereits hypothesisVisible.
  Der abschließende sichtbare Hypothesenring fehlt im BrainOrbRenderer.
  Die Originalkugel des Hauptcockpits bleibt unverändert.

Der optionale PCM-v2-/Signer-Migrationsanteil aus PR528 ist nicht Teil dieses
begrenzten Reparaturschritts. Der PR515-Discovery-Cursor betrifft einen
expliziten Host-CLI-Aufruf; 647 aktuelle Quellcommits unterschreiten dessen
Defaultgrenze 1000. Daraus folgt kein aktueller APK-Blocker.

## Abnahme und Grenzen

An diesem Start-Checkpoint wurden noch keine Produktionsänderungen vorgenommen.
Die 75d-Vollprüfung läuft getrennt auf unverändertem Quellstand. Vorherige
PASS-Ergebnisse gelten nicht als Nachweis neuer Änderungen. Jede Reparatur
erhält gezielte Regressionen; anschließend wird eine neue exakte Quelle für
die vollständigen Gates und den unsigned APK-/AAB-Build eingefroren.

Keine echte Besitzer-Trainingsfehlermeldung wurde reproduziert. Der Quellenfix
ermöglicht neue begrenzte Runden vom bestätigten Modell, keine Fortsetzung
partieller Gradienten. Einwilligung, Ressourcenlimits, Holdoutentscheidung,
STOP und ursprünglicher Signaturvertrag bleiben maßgeblich.

Experimentbezug: EXP-02/07 Regression/Evidenz, EXP-04 Lernqualitätsgrenzen,
EXP-11 Ereignisverbindungen, EXP-12 Wiederaufnahme.
Rollback: unabhängigen Ergänzungszweig nicht übernehmen; PR591 bleibt erhalten.
Keine Migration privater Daten, Gewichte, Schlüssel oder Signaturen.
Originalsignierung, Veröffentlichung und Geräteinstallation bleiben offen.
