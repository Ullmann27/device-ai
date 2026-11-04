# Zusätzliche drei Helfer für LM1 / 16 Millionen Parameter

Status: WIP / UNVERIFIED. Auftrag von Heinz am 04.11.2026: drei zusätzliche Helfer für den Chat „Bots Helfer für Gewichtsmodelle“, Ziel brauchbares Training des vorhandenen LM1-Modells mit rund 16 Millionen Parametern.

## Anschluss

- Führender Forschungsstand: PR #492, `research/continuum-weight-space-audit-20261104`, eingefrorene Basis `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Historischer Helfer: PR #497, `06f071e5850dd405f374bbb58d826f64740d83af`. Dessen alte Reparatur ersetzt nicht die erweiterten Gates in #492.
- Eigener Integrationsbranch: `research/continuum-16m-support-20261104-319f3e`. Keine Schreibzugriffe auf den laufenden Zweig #492.

## Drei tatsächlich gestartete Helfer

| Agent | Aufgabe | Schreibbereich |
| --- | --- | --- |
| weight16m_gate_repair | Konkrete Gate-/Wiederaufnahmefehler reproduzieren und klein beheben | `experimental/weight-space-20261104/run_experiment.py`, eigene neue Tests und Evidenz |
| weight16m_eos_diagnosis | Sofortiges EOS anhand realer Quellen und Rohbelege diagnostizieren | Neue Diagnose-/Testdateien im experimentellen Verzeichnis |
| weight16m_capacity_review | 16M-Parameterzahl, Checkpointintegrität und Ressourcenbelege unabhängig prüfen | Neue Preflight-/Testdateien im experimentellen Verzeichnis |

Die Zentrale integriert und prüft die Beiträge; keine parallelen Änderungen an denselben Dateien. Spätere Helfer sollen vor Dateizuweisung den aktuellen HEAD und dieses Handoff lesen. Die Agenten sind für diese Arbeitssitzung gestartet; damit ist kein dauerhafter Hintergrunddienst eingerichtet.

## Ausgangslage und Erfolgskriterien

Die 16.011.776 Parameter sind historisch als funktionserhaltende Erweiterung belegt. Die aktuellen 6.562.304- und 9.712.128-Parameter-Fortsetzungen scheiterten nach 16 Schritten an sofortigem EOS ohne neue Tokens. Der ursprüngliche 132.160-Parameter-Kandidat bleibt die Qualitätsauswahl. Eine Parameterzahl ist kein Beleg für neues Wissen oder bessere Antworten.

Die Helfer sollen den konkreten nächsten Trainingsschritt ermöglichen: sichere Freigabe und Wiederaufnahme, reproduzierbare Diagnose der EOS-Ursache, echte Checkpoint-/Ressourcenprüfung. Neue Modelltrainingsläufe, Qualitätsgewinn und Geräteabnahme sind an diesem Startpunkt NOT_RUN. Alte Holdouts sind verbraucht und werden nicht zur Optimierung verwendet.

Grenzen bleiben unverändert: 64-MiB-Checkpoint, 768-MiB beobachteter Prozessspeicher und vollständige 1-/32-Token-Last bei 1.500/5.000 ms. Keine Änderungen an produktiver Runtime, Besitzerdaten, App-Konfiguration, Modellpromotion, Main, Signierung oder Release. Keine kostenpflichtigen APIs oder Actions-Läufe.

## Fortsetzung und Rückfall

Nach jedem belegten Beitrag: Tests mit exakter Quellbindung, Ergebnis und Grenzen hier ergänzen, auf diesem Branch sichern. Erst danach ist eine Übernahme durch den Hauptforschungszweig reviewbar. Rückfall: diesen Entwurf nicht übernehmen; alle bisherigen Modelle, Daten und Forschungsbelege bleiben erhalten.

## Erster belegter Zwischenstand

Die unveränderten 75 Python-Gateprüfungen bestehen. Der echte gespeicherte 16.011.776-Parameter-Initialcheckpoint wurde mit neu kompilierten, unveränderten Kotlinquellen auf diesem Host geladen und besteht die vollständige Kapazitätsprüfung: drei gemessene 1-/32-Token-Paare, maximal 624,662 ms / 889,600 ms, beobachtetes Prozessmaximum 282,402 MiB. Modell und Tokenizer bleiben bytegleich. Neue Trainingsläufe: 0.

Rohbelege, Quell-/Werkzeugbindung und Reproduktion: `experimental/weight-space-20261104/support-319f3e-evidence/README.md`. Der Test bestätigt den vorhandenen Ausgangscheckpoint; er behebt den EOS-Fehler der trainierten Fortsetzungen noch nicht. Die drei Implementierungsbeiträge bleiben bis zu ihrer separaten Prüfung WIP.
