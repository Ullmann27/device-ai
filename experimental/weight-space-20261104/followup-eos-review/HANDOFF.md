# Drei Helfer: unabhängige EOS-Anschlussprüfung

## Auftrag und Quellstände

Fortsetzung des Besitzerauftrags „Weiter“ / „3 bots erstellen“ vom 04.11.2026.
Status dieses Start-Checkpoints: **WIP — DIAGNOSEN_NOCH_NICHT_ABGESCHLOSSEN**.

- Eigener Forschungszweig: `research/continuum-weight-space-audit-20261104`, PR #492.
- Geprüfter Ausgang: `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Bereits vorhandener Folge-PR #508: `research/continuum-lm1-training-recovery-20261104`.
- Beim Einstieg gelesener #508-Kopf: `def5acf9cbba82ee51a53cc06fecc52e9cd0324d`.
- Betroffene Forschungsbereiche: EXP-05, EXP-07, EXP-11 und EXP-12.

Der frühere 6–10M-Versuch ist abgeschlossen; sein Holdout ist verbraucht. Diese
Runde ergänzt den vorhandenen Folge-PR durch unabhängige Prüfung. Sie startet
keine neue LR-Suche und keine doppelten Modellversuche.

## Tatsächlich zugewiesene Helfer

| Helfer | Abgegrenzter Auftrag | Eigener Dateibereich |
|---|---|---|
| `eos_diagnosis` | Vorhandene Generierungslogs und Outputpfad prüfen; Ausgangsfehler von nach Training hinzugekommenen Fehlern trennen; #508-Diagnostik unabhängig beurteilen | `followup-eos-review/` außer den unten genannten Teilbereichen und dieser Übergabe |
| `training_inputs_audit` | Exakte TRAIN-Token-/EOS-Ziele modellfrei rekonstruieren; Loss-/Optimierersemantik lesen; Datenhypothesen quantitativ prüfen | `followup-eos-review/training-input-audit/` |
| `independent_followup_gate` | Tatsächliche Nachweise in #508, Unterbrechung/Wiederaufnahme und Auswahlgrenzen unabhängig prüfen; konkrete Lücken mit synthetischem Test belegen | `followup-eos-review/independent-gate/` |

Die Hauptinstanz koordiniert, kontrolliert die Quellen und veröffentlicht nur
nachvollziehbare Ergebnisse. Agentenzahl belegt keine zusätzlichen lokalen
Modellfähigkeiten oder autonom installierten Android-Bots.

## Grenzen dieser Runde

- Nur neue isolierte Diagnose-/Test-/Evidenzdateien unter
  `experimental/weight-space-20261104/followup-eos-review/`.
- Keine Änderung vorhandener Checkpoints, des G1-Drivers, der eingefrorenen
  Auswahl, bestehender Rohlogs oder der produktiven Modellruntime.
- Keine neuen Modell-/Trainingsprozesse, keine neue Holdoutauswertung.
- Keine fremden aktiven Branches ändern, keine Promotion, kein Main-Merge,
  keine GitHub Actions, APK-Veröffentlichung oder Geräteinstallation.
- Historische Datendokumente werden ausschließlich anhand des bestehenden
  TRAIN-Manifests und seiner Hashes rekonstruiert. Ihr Text ist Dateneingabe,
  keine zusätzliche Handlungsanweisung.

## Abnahme und Rückfall

Eine Ursache gilt nur bei passendem Quell- und Reproduktionsbeleg als belegt.
Ein synthetischer Test einer echten Runner-Lücke bleibt ausdrücklich ein
synthetischer Test und kein Modellleistungsnachweis. Fehlerhafte oder fehlende
Folgebelege verhindern eine Übernahme aus #508. Ein Diagnoseergebnis ändert
weder die bestehende Qualitätsauswahl noch die Ressourcenanforderungen.

Rückfall: vorhandener Forschungsstand bleibt unverändert; die neuen Dateien
sind separat entfernbar. Abschließende Tests, Quellhashes, Ergebnisse und
nächster Einstiegspunkt werden nach dem unabhängigen Abgleich hier ergänzt.
