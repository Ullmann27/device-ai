# LM1-Gewichte prüfen und stufenweise erweitern

Diese Werkzeuge gehören zum abgeschlossenen Offline-Experiment vom 4. November
2026. Sie verändern keine App-Konfiguration. Der vollständige Ergebnisbericht
steht unter [WEIGHT-SPACE-AUDIT-20261104.md](../../docs/research/omega/WEIGHT-SPACE-AUDIT-20261104.md).

## Ergebnis und Grenzen

Die letzte bestandene größere Version hat 16.011.776 Parameter. Die folgende
Stufe mit 19.161.600 Parametern überschreitet die vorhandene 64-MiB-Grenze des
LM1-Formats in der Laufzeit. Die enthaltenen Werkzeuge erhöhen diese Grenze
nicht. Die sechs Skalierungsexperimente und das Weitertraining des großen Modells
lieferten keine Qualitätsverbesserung. Die Ausgangsfunktion bleibt in der
unverändert erweiterten Version numerisch erhalten. Android-Geschwindigkeit und
Integration in die aktive App wurden nicht geprüft.

Die ursprünglichen Messungen verwenden Quellstand
`ee5779725690c573d3310c061c6e2e6faf5f0a8b`, Kotlin 2.4.0 und Java 21 mit
Bytecodeziel 17. Der abschließende Code enthält zusätzliche Prüfsicherungen:
Der Speicherhöchstwert wird über alle Messpunkte gebildet, und die eingefrorene
Auswahl muss während der Schlussauswertung unverändert bleiben. Die historischen
Rohlogs werden dadurch nicht nachträglich geändert.

## P0-Integration und unabhängige Gate-Tests

Der Folgeauftrag `G1-INTEGRATE-WEIGHT-002` prüft die Trainingsfreigabe aus
Helfer-PR #497 unabhängig und übernimmt eine zusätzlich abgesicherte Reparatur.
Die Entscheidung, Quellhashes, Vergleichsergebnisse und unveränderten Grenzen
stehen in [G1-INTEGRATION.md](G1-INTEGRATION.md).

Die folgenden Tests benötigen nur Python und führen keine Modellprozesse aus.
Sie schreiben synthetische Belege ausschließlich in neue temporäre Verzeichnisse:

```bash
python3 experimental/weight-space-20261104/tests/adversarial_driver_gate.py
python3 experimental/weight-space-20261104/tests/targeted_final_creation_gate.py
python3 experimental/weight-space-20261104/tests/direct_dispatch_gate.py
```

Die abgenommene Integrationsfassung besteht 36 allgemeine Gate-Fälle, sechs
gezielte Abschluss-/Herkunftsprüfungen und vier direkte Aufrufprüfungen. Jede
Suite bindet ihren Bericht an die tatsächlich gelesenen Quell- und Testhashes.
Ein optionaler erster Dateiparameter erlaubt denselben Vergleich gegen eine
unveränderte historische Quelldatei.

## Werkzeuge bauen

Aus dem Repository-Stamm, mit `kotlinc` und Java auf dem Suchpfad:

```bash
mkdir -p /tmp/continuum-weight-audit-build
kotlinc app/src/main/java/at/heinz/deviceai/learning/lm/*.kt \
  experimental/weight-space-20261104/LmWeightSurgery.kt \
  experimental/weight-space-20261104/LmWeightExperiment.kt \
  experimental/weight-space-20261104/LmCapacityBenchmark.kt \
  -jvm-target 17 -include-runtime \
  -d /tmp/continuum-weight-audit-build/experiment.jar
```

Für Modell-Unit-Tests zusätzlich die beiden bestehenden Tests `LmCoreTest` und
`LmRuntimeTest` sowie `LmWeightSurgeryTest` mit JUnit 4.13.2 und Hamcrest 1.3
kompilieren. Die tatsächlich ausgeführte Prüfung und ihre Ausgabe sind im
Evidenzverzeichnis dokumentiert. Das Experiment benötigt keine externe KI-API.

## Originaleingaben aus Git wiederherstellen

Das lokale Repository benötigt die beiden angegebenen historischen Commits.
Die folgenden Fetches laden Git-Daten; das Experiment selbst arbeitet danach
lokal. `prepare_inputs.py` schreibt ausschließlich in ein neues Verzeichnis und
prüft sämtliche Modell-, Tokenizer- und Dokumentenhashes vor der Ausgabe.

```bash
git fetch origin opus/lm1-checkpoints-20261022
git fetch origin 1d92db03b87558c0b330dc584fd1f4c025a93c82
python3 experimental/weight-space-20261104/prepare_inputs.py \
  --repo . --output /tmp/continuum-weight-audit-inputs
```

`train-source-manifest.json` bindet die ursprünglichen 77 TRAIN-Dokumente an ihre
Pfade und Bytes. VALIDATION- und TEST-Dokumente werden nicht übernommen. Die
bereits vorhandene Kotlin-Korpuslogik bereinigt und segmentiert diese Dokumente
erst in der anschließenden Experimentphase.

## Historischen Versuch nachvollziehen

```bash
python3 experimental/weight-space-20261104/run_experiment.py all \
  --inputs /tmp/continuum-weight-audit-inputs \
  --output /tmp/continuum-weight-audit-results \
  --jar /tmp/continuum-weight-audit-build/experiment.jar \
  --holdout docs/research/omega/weight-space-20261104-evidence/holdout-after-freeze.txt
```

Die Phasen `calibration`, `ladder`, `continuation` und `final` können auch einzeln
in dieser Reihenfolge aufgerufen werden. Bereits abgeschlossene Aufrufe werden
nur wiederverwendet, wenn ihre Nachweise und die Eingabeidentitäten passen.
Ein unterbrochener Aufruf ohne Abschlussnachweis wird ausdrücklich abgewiesen.
Vorhandene Modelle werden nicht überschrieben; Ausgaben tragen ihren Inhalts-
SHA-256 als Dateinamen. Ein geänderter Datensatz oder kompiliertes Programm
erfordert ein neues Ergebnisverzeichnis.

Der veröffentlichte Holdout ist inzwischen bekannt. Dieser Aufruf ist daher eine
Reproduktion des historischen Ablaufs und kein neuer blinder Qualitätsnachweis.
Für eine neue Optimierungsrunde ist ein neuer unabhängiger Testsatz erforderlich.
Laufzeiten sind rechnerabhängig; das Protokoll legt die Vergleichsbedingungen
und Abbruchgrenzen fest, keine auf jedem Gerät identischen Zeitwerte.

## Einzelnen Modellcheckpoint prüfen

Das heruntergeladene Prüfpaket wird im Repository-Stamm vorausgesetzt:

```bash
unzip Continuum-LM1-16M-Pruefpaket-20261104.zip -d /tmp/continuum-lm1-16m
timeout 120s java -Xms64m -Xmx1g -cp /tmp/continuum-weight-audit-build/experiment.jar \
  at.heinz.deviceai.learning.lm.LmCapacityBenchmark \
  /tmp/continuum-lm1-16m/model/continuum-lm1-16011776.lm1 \
  /tmp/continuum-lm1-16m/tokenizer/lm1-tokenizer.lmbp
```

Dieser Linux-Aufruf verwendet `timeout` aus GNU Coreutils als äußeren
120-Sekunden-Prozesswächter; dessen Exitcode 124 bedeutet einen verworfenen Lauf.
Bei der vollständigen Experimentausführung übernimmt die Python-Orchestrierung
diese Grenze. Die Speicherprüfung benötigt Linux mit lesbarem `/proc/self/status`.
Der Laufzeitprompt hat beim gebundenen Tokenizer 64 Tokens ohne BOS. Der
Ersttokenwert misst den vollständigen Ein-Token-Aufruf. Eine sofortige EOS-
Ausgabe erfüllt die geforderte 32-Token-Arbeitslast nicht.
