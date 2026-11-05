# Tatsächliche Prüfung des Modellarchivs

**Ergebnis: `ACTUAL_ARCHIVE_CHECKPOINT_AND_RUNTIME_IDENTITIES_PASS`.**
Root hat das reale Archiv und den passenden separat vorhandenen Tokenizer
gelesen. Der Archivinhalt wurde weder auf die Platte extrahiert noch als
Modell oder JAR ausgeführt. Diese Prüfung ergänzt die getrennten Textaudits.

## Quellen und Identität

Ergebniscommit: `59891cfad6dd015f47c187cc688ea94b0026ad7d`.
Vor dem Finaltest veröffentlichter Freeze:
`9ceffc26a2159c8dc726c1f13d2882119159c130`.

Archiv: `LM1_Training_Research_20261104.zip`, **51.103.436 Bytes**.
SHA-256: `428d62b308e308afd9c971ff4974981e96b9dc8a783d80e3653c226983dcc016`.
Internes Manifest:
`23783270c359e7c0c50ff5ed74f2dc2ef076a0fd30341a2be0c2c10f75c550b6`.

Alle zehn Mitglieder wurden vollständig gelesen und gegen Größe und SHA-256
des Manifests geprüft: Manifest, README, vier Checkpoints, drei Studien-JARs
und Kotlin-stdlib. Die ZIP-CRC wird beim vollständigen Lesen ebenfalls geprüft.
Es gibt keine zusätzlichen oder doppelten Mitglieder. Archiv und Tokenizer
sind am Ende erneut byteidentisch zum Anfang.

## Checkpoints

| Arm | Verifizierte Parameter | Gewählter Schritt | Tatsächliche Dateibytes | Float32-Parameterbytes |
|---|---:|---:|---:|---:|
| S0 | 132.160 | 0 | 529.671 | 528.640 |
| S1 | 132.160 | 512 | 529.673 | 528.640 |
| W0 | 6.562.304 | 0 | 26.250.247 | 26.249.216 |
| W1 | 6.562.304 | 128 | 26.250.249 | 26.249.216 |

Für jede Datei bestätigt der Leser Magic, Version, Konfiguration, eingebettete
Tokenizeridentität, den SHA-256-Trailer über den vollständigen Körper, die
vorgeschriebene Parameterreihenfolge, alle Matrixdimensionen und das exakte
Dateiende. Jeder einzelne Float32-Wert ist endlich. Eingebettete
Adaptionsschritte, Parameterzahl, Datenidentität, Variante und Schedule sind
gegen Freeze und Registrierung gebunden. Die vier äußeren Checkpointhashes
stimmen mit dem früher veröffentlichten Freeze und den späteren Testbelegen
überein.

Die aktuelle Architektur ist MinGRU mit zwei Schichten. Die Parameterzahl aus
dem gepinnten Layout lautet bei Vokabular V, Breite d, MLP-Breite m und L
Schichten: `V*d + L*(4*d*d + 2*d*m + m + 4*d) + d`. Für Small gelten
V=512, d=64, m=256; für Wide V=512, d=512, m=2048. Diese unabhängig vom
Manifest gezählten Werte stimmen exakt mit den registrierten Zahlen überein.
Die reine Float32-Speichergröße enthält keine Aktivierungen, Gradienten,
Optimiererzustände oder JVM-Overheads.

Zwischen S0 und S1 unterscheiden sich alle 132.160 Float32-Slots im Bitmuster;
zwischen W0 und W1 sind es 6.562.303 von 6.562.304. Die Gewichte haben sich
tatsächlich geändert. Der Vergleich misst unterschiedliche 32-Bit-Muster und
beweist weder eine bestimmte Zahl ausgeführter Optimiererschritte noch eine
Qualitätsverbesserung.

## JARs und separater Tokenizer

Die tatsächlichen drei Studien-JARs und das stdlib-JAR stimmen mit den
Registrierungen überein. Sie wurden gehasht, nicht ausgeführt. Das ist eine
Binäridentitätsprüfung, keine unabhängige Bestätigung eines reproduzierbaren
Builds aus den Quellen.

Der Tokenizer hat **2.040 Bytes**, SHA-256
`94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f`.
Magic, 254 Merge-Einträge, gültige Referenzen und exaktes Dateiende ergeben
512 Vokabeleinträge. Seine tatsächlichen Bytes passen zu allen vier Modellen.
**Der Tokenizer liegt separat vor und ist nicht Bestandteil dieses ZIPs.**

## Readerprüfung und Reproduktion

Der Python-Leser ist an die tatsächlichen Kotlinquellen für Checkpoints,
Modelllayout und Tokenizer gebunden. Sie stimmen zwischen Ergebniscommit
und dem angegebenen Ausführungscommit `be4e4960…` bytegleich überein.
Der unabhängige Helfer `independent_current_gate` hat die Readerlogik zusätzlich
gegen diese Quellen geprüft. Seine Prüfung war ausschließlich eine Quellprüfung;
die tatsächliche Binärprüfung wurde von Root ausgeführt.

Geprüfter Leser-SHA-256:
`dca91cb311ac022a3f8f257f8ca61fffc9de41e33fae35a73b8412786b4fbc86`.
Die Metadaten dieses konkreten Archivs sind ASCII. Der Leser unterstützt genau
diesen gebundenen Fall und behauptet keinen allgemeinen Java-modified-UTF-Decoder.

```sh
python -B experimental/weight-space-20261104/continuation-review/root-verification/audit_binary_archive.py \
  --archive /workspace/scratch/3112c86b5191/LM1_Training_Research_20261104.zip \
  --tokenizer /workspace/scratch/3112c86b5191/training-recovery-inputs/lm1-tokenizer.lmbp
```

An einem anderen Rechner sind diese beiden lokalen Eingabepfade entsprechend
anzupassen; die Bytes müssen weiterhin den festgelegten Hashes entsprechen.
Der Auditor schreibt nur seinen JSON-Bericht in den eigenen Verifikationsordner.
Die verwendeten Modell- und Datensatzprozesse: **0**. Tatsächliche Datensätze
oder Generatoren werden nicht gelesen.

[binary-integrity.json](binary-integrity.json) enthält sämtliche Modell- und
Tensorhashes, Metadaten, Konfigurationen und geprüften Quellen.
[final-verification.json](final-verification.json) bindet die echten Binärwerte
zusätzlich an beide unabhängig nachgerechneten Finalberichte.

Die vorab festgelegten Modellkriterien bleiben verfehlt. Die Integrität der
Artefakte ist kein Ersatz für die fehlende Qualität oder die fehlende
vollständige 32-Token-Kapazität.
