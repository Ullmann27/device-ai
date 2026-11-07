# Drei zusätzliche Helfer für LM1 / 16 Millionen Parameter

Status: **SUPPORT_IMPLEMENTED_AND_VERIFIED / RESEARCH_SHADOW.** Auftrag von Heinz am 04.11.2026: drei zusätzliche Helfer für den Chat „Bots Helfer für Gewichtsmodelle“. Alle drei Beiträge wurden geliefert, unabhängig geprüft und im eigenen Entwurf zusammengeführt. Neue Trainingsläufe dieser Unterstützungsrunde: **0**. Eine konkrete EOS-Trainingsursache oder ein Qualitätsgewinn sind weiterhin nicht nachgewiesen.

## Anschluss und Zuständigkeit

- Gemeinsame Forschungsbasis: [PR #492](https://github.com/Ullmann27/device-ai/pull/492), eingefrorener Commit `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Diese Unterstützung: [Draft-PR #507](https://github.com/Ullmann27/device-ai/pull/507), Branch `research/continuum-16m-support-20261104-319f3e`.
- Führende neue Trainingsdiagnose und Intent-Experimente: [Draft-PR #508](https://github.com/Ullmann27/device-ai/pull/508), Branch `research/continuum-lm1-training-recovery-20261104`. Zuletzt gelesener Koordinationsstand: `def5acf9cbba82ee51a53cc06fecc52e9cd0324d`; spätere Ergebnisse sind hier nicht behauptet.
- Historischer Helfer: [PR #497](https://github.com/Ullmann27/device-ai/pull/497), `06f071e5850dd405f374bbb58d826f64740d83af`; dessen alte Reparatur ersetzt nicht die erweiterten Gates in #492.

#508 besitzt den neuen Kotlin-Diagnoseharness, die primäre Trainingsforschung und neue Intent-Datensplits. Diese Runde schreibt ausschließlich in den bestehenden experimentellen Bereich und dieses Handoff. Sie verändert weder #508 noch seine Datensplits. Der versiegelte Testsplit wurde nicht geöffnet.

## Drei gelieferte Beiträge

| Helfer | Beitrag | Unabhängige Prüfung |
| --- | --- | --- |
| `weight16m_gate_repair` | Reproduzierbaren Fehler beim Schreiben von Phasenmarkern behoben; vollständige JSON wird vor der exklusiven Veröffentlichung gespeichert. | Vier reproduzierte Fehler der neuen Zehnersuite vor dem Fix; danach 75 bestehende + 10 neue Fälle bestanden. Root bestätigt zusätzlich einen echten SIGKILL-Gegenversuch. |
| `weight16m_eos_diagnosis` | Nur lesender Prüfer für 16 historische Rohlogs, mit Quellen-/Checkpoint-/Tokenizerbindung und präziser EOS-Auswertung. | 9/9 Tests einschließlich echter historischer Regression; Root hat die Auswertung am festgelegten Git-Commit wiederholt. Zusätzliche unabhängige Sichtprüfung ohne konkreten Befund. |
| `weight16m_capacity_review` | Unabhängiger, paketfreier LM1C/LMBP-Leser, vollständige Tensor-/Float-/Hashprüfung und dokumentierte Resume-Grenze. | 14/14 Tests; Root hat den echten 16M-Checkpoint mit explizit vorgegebenen Dateiidentitäten erneut vollständig gelesen. |

Die Helfer haben ihre Aufgaben in dieser Arbeitssitzung abgeschlossen. Die Beiträge und Prüfer bleiben im Repository verfügbar; damit ist kein dauerhafter Hintergrunddienst eingerichtet.

## Verifikation: 109/109 Software-Prüffälle

| Suite | Bestanden |
| --- | ---: |
| Vorhandene adversarial / creation / dispatch / profile gates | 36 + 6 + 4 + 29 = 75 |
| Atomare Veröffentlichung | 10 |
| Unabhängiger echter Prozessabbruch beim Schreiben | 1 |
| Historische EOS-Auswertung | 9 |
| Checkpoint-/Tokenizer-Preflight | 14 |
| **Gesamt** | **109** |

Die Zahl umfasst Softwareprüfungen, keine Trainingsschritte oder erfolgreichen Modellaufgaben. Die Gateprüfungen verwenden synthetische Fixtures und starten keine Modellprozesse. EOS-Tests schließen die wirklichen archivierten Git-Blobs ein. Der zusätzliche echte Modell-Preflight und der native Inferenztest stehen separat.

Quellbindung: korrigierter Driver `e991683e6abf222ca0f213cc703ff0d23ea646efd2772204c069d89e8f542c8f`, EOS-Prüfer `07fc0fe393c15d5c60654bf946a8083da8aa7e1354dc0f291c78d75763af2fe3`, Preflight `73aaaeaeb0f65ba584d5e66a3a65b32b8d4132b27dbef276a59248b9ae72f0ba`.

Befehle, Rohlogs und Hashbelege: [Evidenzindex](../../../experimental/weight-space-20261104/support-319f3e-evidence/README.md), [Gatebericht](../../../experimental/weight-space-20261104/g1-evidence/atomic-phase-publication-handoff.md), [EOS-Bericht](../../../experimental/weight-space-20261104/EOS-RAWLOG-AUDIT-20261104.md) und [16M-Preflight](../../../experimental/weight-space-20261104/WEIGHT16M_PREFLIGHT.md).

## Tatsächlicher 16M-Ausgangscheckpoint

Der vorhandene historische, funktionserhaltend erweiterte Checkpoint wurde geladen und geprüft: **57 Tensoren, 16.011.776 tatsächlich gelesene endliche Float32-Parameter**, 64.048.511 Dateibytes, gültige interne Prüfsumme und passender Tokenizer mit Vokabulargröße 512.

- Modell-SHA-256: `93728796b1bfc3cadb7625e5f4ecc9651e82a14d5d340dbb6dcc6df2f2725312`.
- Tokenizer-SHA-256: `94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.
- Separater nativer Hosttest mit neu kompilierten, unveränderten Kotlinquellen: drei vollständige gemessene 1-/32-Token-Paare; maximal **624,662315 ms / 889,600476 ms** und **289.180 KiB / 282,40234375 MiB** beobachteter Prozesshöchstwert. Inferenzkapazität: **PASS** unter den unveränderten Grenzen.

Der Ein-Token-Wert ist ein vollständiger Aufruf einschließlich Promptverarbeitung, kein direkt beobachteter Streaming-Zeitstempel. Der Test läuft auf Linux-x86_64 mit Kotlin 2.0.20 / Java 21.0.13; er ersetzt weder den historischen Trainings-JAR-Pin noch einen Android-Gerätetest. Modell und Tokenizer blieben bytegleich.

**Trainingsspeicher: UNMEASURED. Exaktes Optimizer-Resume: UNAVAILABLE.** LM1C v1 speichert keinen vollständigen Optimizerzustand; die Gewichte können mit neuem Optimizer als Ausgangspunkt dienen. Die Inferenzmessung belegt keinen Trainingslauf unter 768 MiB. Die geerbte Metadatenangabe `epoch=8` belegt keine acht Trainingsepochen des 16M-Modells. Die Qualitätsauswahl wird durch diese Unterstützung nicht verändert.

## EOS-Befund für das Trainingsteam

Die vier archivierten Fortsetzungsläufe zeigen bereits vorher 4/6 und danach 6/6 sofortige EOS-Ausgaben. Beide neu betroffenen Aufgaben waren vorher ebenfalls falsch: Aufgabenrichtigkeit jeweils 0/6 vor und nach der Fortsetzung. Auch beide kleinen Kontrollen zeigen den Effekt. Die vier Laufaufzeichnungen enthalten nur drei verschiedene fortgesetzte Checkpoint-Identitäten, weil beide kleinen Kontrollen denselben Checkpoint-Hash melden.

Die jeweils acht leeren Kapazitätsaufrufe wiederholen einen einzigen Protokollprompt; sie sind keine acht unabhängigen Aufgaben. Alle vier Trainingslogs stimmen bei 16 Schritten, 3.255 Tokens und den protokollierten Eingabe-/Schedule-Identitäten überein. Der Prüfer bestätigt die Konsistenz dieser Belege, nicht ihre Authentizität oder die Bytes fehlender trainierter Checkpoints.

Eine konkrete Trainingsursache lässt sich aus den alten Logs nicht bestimmen. Für #508 ist deshalb der bereits dort vorgesehene Vergleich von EOS-Wahrscheinlichkeit, Abstand zum stärksten Nicht-EOS-Token und tatsächlicher Ausgabe vor dem Training sowie nach 1/4/16 Schritten relevant. Neue Entwicklungsaufgaben und der versiegelte Testsplit müssen getrennt bleiben. Diese Unterstützung führt das Experiment nicht doppelt aus.

## Integration und weitere Helfer

Der Speicherfix ändert ausschließlich `write_new` und einen Import. Historische Quell-/JAR-/Runtime-/Holdout-Gates bleiben identisch; der neue Driver-Hash erhält keine zusätzliche Freigabe für abgeschlossene Studien. Der Fix benötigt lokale Hardlinks, garantiert keine Stromausfallfestigkeit und repariert keine bereits beschädigten endgültigen Marker. Ein SIGKILL kann eine eigene temporäre Datei hinterlassen; sie wird nicht als Phasenmarker verwendet.

Weitere Helfer lesen zuerst die aktuellen Köpfe von #507 und #508 sowie deren Handoffs und übernehmen danach Aufgaben ohne Überschneidung mit den bestehenden Dateizuständigkeiten. #508 koordiniert das Training; #507 liefert den Prüf- und Reparaturbeitrag. Keine automatische Übernahme oder Modellpromotion ist erfolgt.

Grenzen bleiben unverändert: 64-MiB-Checkpoint, 768-MiB-Inferenzprozessgrenze, vollständige 1-/32-Token-Last bei 1.500/5.000 ms. Produktionsruntime, Hauptbranch, App-Konfiguration, Signierung, Release und Installation wurden nicht verändert. Keine kostenpflichtigen KI-APIs oder GitHub-Actions-Läufe. Rücknahme: experimentellen Driver gezielt auf den Basisstand zurücksetzen oder diesen Entwurf nicht übernehmen; historische Daten, Modelle und Belege bleiben erhalten.
