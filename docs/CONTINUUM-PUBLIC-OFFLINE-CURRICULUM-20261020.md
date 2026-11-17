# SWB1 Public Offline Curriculum v1 – Device AI Continuum

Stand: 20.10.2026. Kein Pretrained-LLM und kein blindes Importieren von Gewichten.

## Was in der App neu ist

Unter **LERNEN → SWB1 · ÖFFENTLICHES OFFLINE-LERNPAKET** ist ein
ausdrücklich bestätigter und vollständig lokaler Lernlauf verfügbar.
Alternativ unter **LERNKERN · DETAILS → GEWICHTE / LABOR** und im
erweiterten Modellfenster. Neben den bereits vorhandenen Importwegen
`GEWICHTE IMPORTIEREN` (nur SWB1, 32.960 Bytes) und `PAKET-IMPORT`
(SWB1 plus JSON-Manifest, SHA-256) steht nun
**GEWICHTE EXPORTIEREN** als Android-Dateidialog zur Verfügung.

Das gebündelte synthetische Laborkorpus umfasst:

- 72 Intent-Trainingssätze (36 Gruß, 36 Frage) und 24 separate
  Prüfbeispiele (12 + 12)
- 32 semantische Trainingspaare (16 positiv, 16 negativ)
  sowie 12 separate Prüfpaare (6 + 6)
- zusätzlich unverändert: 8 bisherige feste Intent-Holdouts und
  6 semantische Holdouts sowie der Replay-Test der persönlichen
  bestätigten Intent-Lektionen.

Die Kotlin-Trainingsroutine startet vom eigenen aktiven
4.113-Parameter-Modell und bearbeitet **nur einen Kandidaten**. Sie
führt maximal 1.100 Intent- und 900 Semantik-Updates durch,
mit begrenzten Laufzeiten und Android-Abbruchkontrolle. Nur wenn sich
beide Trainingsaufgaben verbessern, die Summe der neuen Audit-Verluste
messbar sinkt und jeder neue, alte und persönliche Holdout nicht
regressiert, wird das Kandidatenmodell archiviert und aktiviert. Alle
anderen Fälle lassen das produktive Modell unverändert.
Abgebrochene Durchgänge schreiben weder neue Gewichte noch ein
Run-once-Audit; abgeschlossene Versuche werden einmal pro
Korpusversion auditiert.

Dieses Dataset ist **öffentlich/synthetisch**. Kein Datensatz bekommt
fälschlich den Status einer bestätigten Owner-Aussage, und keine
Mentor-/Webquelle wird zur Geräteautorität. Das ist Forschung an
Gruß-/Frage- und Satzähnlichkeitsrepräsentationen, keine Bestätigung,
dass ein generatives Sprachmodell bereits selbstständig denkt.

## Datenschutz und Grenzen

Gewichte werden nur durch eine explizite Dateiauswahl als
`continuum-swb1-backup.bin` exportiert. Sie enthalten trainierte
Modellparameter, nicht die Erfahrungsliste oder private Chattexte.
Da Gewichte indirekt Trainingsinformation kodieren können, dürfen
persönliche Backups trotzdem nicht automatisch ins öffentliche
GitHub-Repository hochgeladen werden.

Vorhandene SHA-256-Prüfungen und Holdout-Gates der bestehenden Importe
bleiben unangetastet. Export und Import sind **unterschiedliche**
Operationen: der Import einer eigenen Sicherung ist kein garantierter
Gewichtsaustausch, sondern muss die lokalen Qualitätsprüfungen
bestehen. Für die eingebaute Public-Curriculum-Aktion ist kein
Internetdownload notwendig.

## Abnahme

1. Im Experimentbranch `testDebugUnitTest`, Lint und APK-Build prüfen.
2. Samsung ohne Deinstallation aktualisieren, bestehende Gewichte/
   Checkpoints und importiertes Qwen-Modell erhalten.
3. Im Lernkern vor dem Training Fingerprint, Holdouts und Updates
   notieren; Lernpaket explizit starten und Resultat vergleichen.
4. Sowohl bei akzeptiertem als auch bei verworfenem Kandidaten die
   Checkpoint-, Audit- und Owner-Fakten-Grenzen prüfen.
5. Gewichtsexport über Android-Dateidialog testen und mit
   SHA-256/Dateigröße bestätigen.
6. Qwen-Fehler `Local generation cancelled` **separat** mit
   `Continuum Modelltest` untersuchen; dieses SWB1-Paket repariert
   nicht die native LiteRT-LM-Inferenz.
