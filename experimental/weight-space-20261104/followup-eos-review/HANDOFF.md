# Drei Helfer: unabhängige EOS-Anschlussprüfung

## Auftrag und Quellstände

Fortsetzung des Besitzerauftrags „Weiter“ / „3 bots erstellen“ vom 04.11.2026.
Status der abgeschlossenen Prüfrunde: **REVIEW_COMPLETE / UPSTREAM_GATE_DEFECT_REPRODUCED**.
Start-Checkpoint: `57d939a390d6b5a2fd197681aa78606b4f8b8e18`.

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
sind separat entfernbar. Kein Modell aus #508 wurde übernommen.

## Tatsächlich abgeschlossene Arbeit

### 1. Ausgabeabbruch eingegrenzt

`analyze_archived_eos.py` prüft 16 veröffentlichte Log-/Receipt-Paare und
insgesamt 37 Quell-/Evidenzdateien. Root hat die Auswertung unabhängig
wiederholt; der vollständige JSON-Bericht stimmt bytegenau überein.

- Original und große Initialmodelle bestanden je acht Kapazitätsaufrufe am
  selben festen Prompt. Alle vier Fortsetzungen brechen danach dort mit EOS
  und null neuen Tokens ab.
- Schon das Original und die großen Initialmodelle liefern bei vier von sechs
  Instruktionsproben sofort EOS. Bei den zwei übrigen Proben erzeugen sie vor
  Training jeweils 32 falsche Tokens; danach ebenfalls sofort EOS.
- Die unvergrößerte kleine Kontrolle zeigt denselben Übergang. Vergrößerung
  ist daher keine notwendige Bedingung des beobachteten Fehlers.
- Ein konkreter Runtime-/Optimiererdefekt und die kausale Ursache sind noch
  nicht belegt. Vorhandene Kapazität ist keine belegte Instruktionsfähigkeit.

Bericht: [`README.md`](README.md),
Rohbefund: [`archived-eos-findings.json`](archived-eos-findings.json).

### 2. Exakte Trainingsziele unabhängig rekonstruiert

`training-input-audit/audit_training_inputs.py` liest ausschließlich die
77 hashgebundenen historischen TRAIN-Dokumente, den Tokenizer, Quellcode und
die vier Trainingslogs. Der unabhängige Python-Leser reproduziert exakt den
historischen Korpus, Shuffle und vollständigen Tokensequenz-Hash. Alle
16 kumulativen Tokencounts aller vier Arme stimmen. Root hat dies separat
ausgeführt; alle Befunde außer dem Beobachtungszeitpunkt sind identisch.

| Größe der tatsächlich verwendeten Folge | Wert |
|---|---:|
| Unterschiedliche ausgewählte Segmente | 64 |
| Inhaltstargets | 3.229 |
| EOS-Targets | 26 |
| Summe | 3.255 |
| EOS-Anteil | 0,7987711214 % |
| BOS-Targets | 0 |
| Lange, auf 63 Inhaltstokens gekürzte Segmente ohne EOS-Ziel | 38 |

Eine EOS-only- oder BOS-Label-Korruption wurde nicht gefunden. Das ist keine
kausale Erklärung dafür, welches Token das trainierte Modell auswählt.
Inhalts-BPB maskiert EOS; Trainingsloss enthält die tatsächlich vorhandenen
EOS-Ziele. Beide Kennwerte messen unterschiedliche dokumentierte Größen.

Nachweis: [`training-input-audit/target-replay.json`](training-input-audit/target-replay.json).

### 3. Konkrete Nachweislücke in #508 reproduziert

Geprüft wurde ausschließlich der gepinnte #508-Runner bei
`def5acf9cbba82ee51a53cc06fecc52e9cd0324d`, SHA-256
`409f87f5a174804d7290c29972d6b4e2bbb19ad293ac1da027122c3f28d14d4f`.

Der echte `Study`-Konstruktor und die echten Methoden `verify`, `selection`,
`freeze_evidence`, `freeze`, `test` und `run` laufen auf synthetischen Dateien.
Nur `subprocess.Popen` wird direkt vor dem Prozessstart abgefangen.

| Gegenprobe | Tatsächlicher Befund |
|---|---|
| Beide Nachtrainings-Kapazitätsbelege fehlen | Freeze akzeptiert; erster Testprozess wird angefordert |
| Nachtrainingsbeleg für den großen Arm ist abgeschnitten | Freeze akzeptiert; erster Testprozess wird angefordert |
| Positive Metadatenkontrolle | Testprozess wird angefordert |
| Explizit protokolliertes Capacity-FAIL als begrenzter Intent-Vergleich | Testprozess wird angefordert, gemäß Protokoll zulässig |
| Manipulierter Trainings-Receipt | Vor Freeze/Test zurückgewiesen |
| Unbekannte Runner-Quellbytes | Vor Compile zurückgewiesen |

Ergebnis: **4 Kontrollen PASS, 2 erforderliche Zurückweisungen RED; Exit 1**.
Root hat denselben Befund separat reproduziert; ein weiterer Helfer hat den
Kontrollfluss unabhängig gelesen. Tatsächlich gestartete Modellprozesse: **0**.
Die temporären synthetischen Checkpoints sind keine Modellvalidierung.

Dies ist **kein erneut gefundener BASELINE_GATE-Trainingsbypass**. Das neue
Intent-Protokoll erlaubt einen begrenzten Task-Score auch bei einem gültig
belegten nachträglichen Kapazitäts-FAIL; dieses FAIL muss sichtbar mitberichtet
werden. Der konkrete Defekt besteht darin, dass fehlende oder beschädigte
Nachtrainingsbelege vor Freeze/Test überhaupt nicht gelesen werden. Auch
`intent-summary.json` übernimmt derzeit keine Kapazitätsergebnisse.

Nachweis: [`independent-gate/probe-result.json`](independent-gate/probe-result.json).

## Unabhängige Root-Wiederholung

[`root-verification.json`](root-verification.json) bindet die drei tatsächlich
ausgeführten Prüfungen an die Quellhashes. Die vollständigen Root-Ausgaben
liegen in [`root-reproduction/`](root-reproduction/). Beim Berichtvergleich
wurden ausschließlich Beobachtungszeitpunkte und Freeze-Hashes mit temporären
Fixture-Pfaden normalisiert; alle inhaltlichen Befunde stimmen überein.

```sh
python experimental/weight-space-20261104/followup-eos-review/analyze_archived_eos.py
python experimental/weight-space-20261104/followup-eos-review/training-input-audit/audit_training_inputs.py --repo . --inputs /path/to/verified-inputs --output /path/to/new-target-report.json
python experimental/weight-space-20261104/followup-eos-review/independent-gate/probe_pr508_post_capacity.py
```

Der letzte Befehl muss beim gepinnten fehlerhaften Quellstand **Exit 1** liefern.
Dies ist der erwartete RED-Nachweis, kein fehlgeschlagenes Modelltraining.
Die 75 bereits bestandenen G1-Fälle wurden nicht ohne neue Notwendigkeit
wiederholt. G1-Driver, Runtime, bestehende Gates und eingefrorene Ergebnisse
werden durch diese neuen Diagnoseprogramme nicht verändert.

## Nächster Einstiegspunkt

Vor einer Übernahme aus #508 muss dessen Eigentümer den Nachtrainingsbeleg
als Pflichtabhängigkeit der getrennten Freeze-/Testphasen binden: Checkpoint,
Quell-/Registrierungs-/Programmidentität, Loghash, vollständige Arbeitslast
sowie Prozessgrenzen nachprüfen und gültige PASS- oder FAIL-Ergebnisse in
Auswahl und Zusammenfassung erhalten. Fehlende, beschädigte oder vertauschte
Belege müssen vor Freeze/Test stoppen. Ein belegtes FAIL darf entsprechend
dem vorhandenen Protokoll weiterhin eine begrenzte Intent-Metrik begleiten.

Danach ist der hier gesicherte Gegenfall unabhängig gegen die reparierten
Originalquellen zu prüfen. Für eine kausale EOS-Diagnose nutzt #508 bereits
Wahrscheinlichkeiten/Margen und getrennte Loss-Anteile; weitere doppelte
LR-Versuche sind aus dieser Prüfung nicht entstanden. Ohne belegte Ursache
wird keine Änderung am produktiven Trainer oder Decoder vorgeschlagen.

## Späterer Quellabgleich während der Veröffentlichung

PR #508 ist während unseres Abschlusses auf
`fc2eb797210bee25091caf734aacbfd694a3372e` weitergegangen. Dieser Snapshot
publiziert für die erste Intent-Runde `NO_DEVELOPMENT_SIGNAL` und
`testEvaluated: false` und registriert eine gesonderte 512-Schritt-Runde.
Dies sind veröffentlichte Fremdbefunde; wir haben dafür keine Modellläufe
oder vollständige Ergebnisprüfung durchgeführt.

Der ursprüngliche Runner ist weiterhin bytegleich mit der dynamisch geprüften
Fixture. Im neuen `long-study/run_registered_study.py` sind die beiden
entscheidenden Funktionen `freeze_evidence` und `freeze` unverändert; auch
dort werden Nach-Kapazitätsbelege nicht zur Pflichtabhängigkeit der Auswahl.
Die neuen Controller-Tests erzeugen nur Vorher-Kapazitätsbelege. Root und der
unabhängige Helfer haben dies statisch abgeglichen; es ist kein zusätzlicher
dynamischer Test des neuen Runners.

[`latest-source-check.json`](latest-source-check.json) hält die beiden neuen
Git-Blob-/SHA-256-Identitäten und den AST-Vergleich fest. Der oben beschriebene
Reparaturbedarf besteht für diese geprüften Quellen weiter. Spätere fremde
Commits sind von dieser versionsgebundenen Aussage nicht automatisch erfasst.
