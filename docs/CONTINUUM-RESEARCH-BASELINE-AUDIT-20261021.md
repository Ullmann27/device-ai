# CRE1-Forschungsbaseline: Audit vom 21.10.2026

Status: `RESEARCH_SHADOW`, fokussierte Node-Prüfung `MOCK_TESTED`.
Ausgangs-HEAD: `1c255d25c1597b925ddeb14e5acad717134eaa33` (PR #42),
isolierter Arbeitsstand `sol/continuum-quality-20261021`.
Der federführende Audit hat den aktuellen Entwicklungs-HEAD und
[Issue #41](https://github.com/Ullmann27/device-ai/issues/41) geprüft.
Maßgeblich bleiben `AGENTS.md`, `CONTINUUM-EXPERIMENTVERTRAG.md` und
`CONTINUUM-EXPERIMENTE.json`: Android zuerst, gemeinsamer Backlog und
Repair-Gate, keine automatische Promotion oder Änderung an `main`.

## Reproduzierbare Ursachen und Korrekturen

Die vollständige unveränderte Node-22-Baseline enthält 717 Tests:
705 bestanden, sechs fehlgeschlagen, sechs übersprungen. Vier Fehler
betreffen diesen Forschungsbereich. Die beiden anderen Fehler
(Retrieval-Snapshot und Preview-Quelltextprüfung) werden im Hauptaudit
separat behandelt.

| Test / Funktion | Tatsächliche Ursache | Begrenzte Korrektur |
| --- | --- | --- |
| `same original source never leaks into both training and sealed audit` / `buildExtractiveContrastiveSet` | Die 80 ursprünglichen Fixture-Aussagen unterschieden sich nur durch eine Nummer. Der bestehende Near-Duplicate-Filter verwarf sie zu Recht als Negativkandidaten; null statt 65 Beispiele entstanden. | Acht unterschiedliche synthetische Themen liefern unterscheidbare Negativkandidaten. Eine eigene Negativprüfung bewahrt die Ablehnung nummerierter Satzkopien. Zwei echte Extrakte desselben konstruierten Dokuments müssen zusammen im selben Hash-Split bleiben. |
| `training is research-only, leaves frozen E unchanged and rejects holdout failures` | Leere Kontrastivmengen aus denselben Fixtures erreichten den Trainer. Eine zu früh auf drei Zeilen begrenzte Audit-Auswahl kann außerdem wiederholte Themen enthalten. | Kontrastivbeispiele werden zuerst innerhalb jedes vollständigen Dokument-Splits erzeugt, anschließend auf fünf Trainings- und drei Audit-Beispiele begrenzt. Embedding-Unveränderlichkeit wird weiter geprüft. |
| `sibling negatives require disambiguating questions; no train/audit source leakage` / `validateExtractiveSpans` | Das Testfixture validierte Rohdaten, veränderte anschließend die Quelle und validierte die normalisierte Rückgabe erneut. Diese enthält absichtlich kein `trainingPermitted`; die Rechteprüfung schlug zu Recht fehl. | Die gemeinsame Originalquelle wird aus Rohdaten mit expliziter Rechtefreigabe aufgebaut und danach validiert. Fehlende Freigabe bleibt ein Fehler. Generische Dokumentfragen dürfen keine Geschwisteraussage als Negativziel erhalten. |
| `signed CRE1 research package binds model, audit, publisher and canonical manifest` / `checkEcKey` | `createPrivateKey` akzeptiert keinen bereits bestehenden `PrivateKeyObject`; auch ein öffentlicher KeyObject darf nicht erneut als PEM geparst werden. | Ein KeyObject der angeforderten Art wird direkt weiterverwendet. PEM-Eingaben durchlaufen weiterhin den vorhandenen Parser; Schlüsseltyp und P-256-Kurve werden weiterhin geprüft. |

Zusätzlich zeigte die vorher grüne Prüfung
`rejected candidate returns exact baseline bytes` eine Beleglücke:
Mit `learningRate=0.000001` akzeptierte das Gate den Kandidaten, und die
bedingte Rollback-Assertion wurde übersprungen. Eine verpflichtende
Ablehnungs-Assertion reproduzierte den Fehler. Das Fixture verwendet nun
`learningRate=0.001`: Der Trainingsverlust sinkt, aber Audit-Top-1 fällt
von eins auf null. Das Gate muss ablehnen und exakt die Baselinebytes
zurückgeben. Die Gate-Toleranzen wurden nicht verändert.

## Experimente, Kompatibilität und nächste Schritte

| Experiment | Nachweis und Kompatibilität | Nächster Schritt |
| --- | --- | --- |
| EXP-04: SWB1/CRE1-Schattenlernen | Bestehende CRE1-Bytes, Signaturpayload, P-256-Prüfung, Manifest-/Audithashes und Trainingsgrenzen bleiben erhalten. PEM und Node-KeyObjects werden akzeptiert; andere Kurven und Ed25519 werden abgelehnt. Manipuliertes Modell, Audit, Manifest sowie falscher Herausgeberschlüssel werden weiterhin abgelehnt. | Unabhängige Quellen-/Lizenzprüfung und prospektive Holdouts vor einer gesonderten Promotion. |
| EXP-06: Atlas-Provenienz | Keine Änderung an Quellenbeschaffung, Rechteprüfung, exaktem Extrakt, öffentlicher HTTPS-Herkunft oder dokumentenweisem Hash-Split. Die Testquellen sind konstruierte Fixtures; `trainingPermitted:true` in einem Test beweist keine reale Lizenz. | Echte freigegebene Originaldokumente und Kontaminationsprüfung getrennt belegen. |
| EXP-12: Checkpoint und Rollback | Ablehnung nach tatsächlicher Audit-Regression wird mit bytegleicher Baseline geprüft; Modellformat und produktive Checkpoints bleiben kompatibel. | Geräte- und modellübergreifende Kontinuität separat nachweisen. |

Die einzige Produktionsänderung ist die KeyObject-Normalisierung in
`research-learning-cycle.mjs`. Die übrigen Änderungen sind Fixtures und
Verhaltensprüfungen in den beiden vorhandenen Testsuiten.

## Verifikation

Werkzeug: Node `v22.23.3` unter `/workspace/toolchain/node22/bin/node`.
Unveränderte Gesamtbaseline: `/workspace/evidence/original-head-node22.tap`.
Die fokussierte Wiederholung vor den Änderungen ergab 12 Tests,
acht bestanden und vier fehlgeschlagen:
`/workspace/evidence/research-focused-red.tap`.

Nach den Änderungen wurde ausgeführt:

```bash
/workspace/toolchain/node22/bin/node --test \
  .github/continuum/research-learning-cycle.test.mjs \
  .github/continuum/cre1-blacklab-optimizer.test.mjs \
  .github/continuum/research-shadow-worker.test.mjs \
  .github/continuum/cre-sgd-parity.test.mjs \
  .github/continuum/cre-checkpoint-safety.test.mjs
git diff --check
```

Ergebnis: **25/25 bestanden**, null Fehler, null übersprungen,
Exitcode 0. Beleg: `/workspace/evidence/research-focused-green.tap`.
Die zusätzliche Signaturalgorithmusprüfung wurde vor der
Produktionskorrektur rot beobachtet; die ursprüngliche Signaturprüfung
reproduzierte denselben KeyObject-Fehler. `git diff --check` ist sauber.
Die abschließende vollständige Node-/Backend-Prüfung wird nach den
parallelen Auditänderungen zentral ausgeführt und separat protokolliert.

Keine Aussage über echte Modellqualität, Android-Build, APK,
Geräteinstallation oder dauerhaften Workerbetrieb folgt aus diesen
Hosttests. Es wurden keine Kotlin-Dateien, produktiven Gewichte,
bestätigten Fakten oder fremden Forschungsquellen verändert.

## Rücksetzweg

Nur die drei hier betroffenen Dateien auf den Ausgangs-HEAD zurücksetzen;
andere Auditänderungen bleiben erhalten:

```bash
git restore --source=1c255d25c1597b925ddeb14e5acad717134eaa33 -- \
  .github/continuum/research-learning-cycle.mjs \
  .github/continuum/research-learning-cycle.test.mjs \
  .github/continuum/cre1-blacklab-optimizer.test.mjs
```

Dieses Auditdokument kann zur Nachvollziehbarkeit bestehen bleiben.
Ein Rücksetzen stellt auch die vier beschriebenen Baselinefehler wieder her.
