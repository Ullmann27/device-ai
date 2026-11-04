# Zweite Dreiergruppe: LM1 / 16M-Training

Status: **WIP / UNVERIFIED / NEW_TRAINING_NOT_RUN**. Heinz hat am 04.11.2026 unmittelbar die nächsten drei Helfer und weitere Arbeit beauftragt.

## Gemeinsamer Anschluss

- Basis dieser Unterstützung: PR #507, `e726deb96dc74bacd40c2fe0ef640c6db57903f5`, Baum `ef283f27ccd7ca2c2951391a9f205e994d85bc09`.
- Unveränderte LM1-Kernel: PR #492, `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Führende Trainingsforschung: PR #508, zuletzt gelesener Stand `7131c1a50a9c697e481a6debe259758dbf422eef`. Dort existieren inzwischen study-v1-Rohbelege und ein gesondert registrierter long-study. Die ältere Statuszeile WIP/EXPERIMENTS_NOT_RUN beschreibt den Dateistand daher nicht vollständig; konkrete Ergebnisse werden vor Aussagen geprüft.
- Eigener Branch: `research/continuum-16m-training-support-r2-20261104-319f3e`. Produktive Dateien, #508 und seine Datensplits bleiben unangetastet.

## Genau drei neue Helfer

| Agent | Auftrag | Eigener Dateibereich unter experimental/weight-space-20261104/support-round2/ |
| --- | --- | --- |
| weight16m_gradient_check_r2 | Numerische Gegenprüfung der existierenden minGRU-Gradienten mit kleinen synthetischen Beispielen, korrekter Float32-Fehlerbewertung und Negativkontrollen. | LmGradientAudit.kt, GRADIENT_AUDIT.md, eigene Evidenz |
| weight16m_training_probe_r2 | Native Messung vorbereiten, ob ein begrenzter echter 16M-Trainingsschritt durchläuft und welche Ressourcen er benötigt. | LmTrainingResourceProbe.kt, TRAINING_RESOURCE_PROTOCOL.md, eigene Tests/Evidenz |
| weight16m_recovery_audit_r2 | Unabhängige Konsistenzprüfung der neuen #508-Rohbelege und Entscheidungen, ohne dessen Trainingsläufe zu duplizieren. | recovery_evidence_audit.py, eigene Tests, RECOVERY_AUDIT.md, eigene Evidenz |

Root integriert, prüft und veröffentlicht. Vor einem tatsächlichen 16M-Optimizerschritt werden der fertige Harness, die genaue Konfiguration, Quellen-/Eingabehashes und der Ablauf separat festgeschrieben und veröffentlicht. Tiny-Gradienten-/Format-/Controllerprüfungen sind keine Modellqualitätsexperimente.

## Geplanter eng begrenzter Ressourcenversuch

Geplant ist ein einzelner AdamW-Update mit dem vorhandenen 16.011.776-Parameter-Checkpoint, einem frisch festgelegten synthetischen Beispiel mit höchstens acht Tokens, Batch 1 und einem Worker. Maximale JVM-Heapgrenze 3 GiB, Prozesszeit höchstens 120 Sekunden. Keine adaptive Erweiterung nach Fehler oder Timeout. Die konkreten Hyperparameter und Ein-/Ausgabebelege werden vor Ausführung festgelegt; ein einzelner Schritt muss tatsächlich eine von null verschiedene Lernrate verwenden.

Modell-SHA-256: `93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312`; Tokenizer-SHA-256: `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`. Originale sind schreibgeschützt zu behandeln. LM1C v1 enthält keinen vollständigen Optimizerzustand; ein frischer Optimizer ist ausdrücklich Teil des Versuchs. Das Ergebnis soll die Trainingsspeicherlücke schließen und belegt allein keine Sprachqualität.

## Grenzen und Rückfall

Der neue versiegelte Testsplit wird nicht geöffnet. Alte Holdouts werden nicht zum Tuning benutzt. Kein neues Trainingsframework, kein Austausch der Produktionsruntime, keine fremden vortrainierten Gewichte, kostenpflichtigen APIs, Actions-Läufe, Modellpromotion, Main-Merges, App-Installation oder Signierung. App-Offline-Einstellung bleibt unverändert.

Scheitert eine Prüfung, bleibt der Befund erhalten und wird ehrlich als FAIL/UNAVAILABLE/NOT_RUN berichtet. Keine ursprünglichen Modelle oder historischen Belege werden verändert. Rollback: diese separaten Forschungsdateien nicht übernehmen. Die erste Dreiergruppe ist in #507 abgeschlossen; diese zweite Gruppe bearbeitet neue, abgegrenzte Mess- und Prüfaufgaben.
