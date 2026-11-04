# Unabhängige Prüfungen für G1-INTEGRATE-WEIGHT-002

Diese Tests prüfen die Freigabe von Fortsetzungstraining, die Wiederaufnahme
gespeicherter Ergebnisse und den direkten Abschlussaufruf. Sie verwenden
ausschließlich synthetische Dateien. Es werden keine echten Modellprozesse,
Trainingsläufe oder Holdout-Auswertungen gestartet.

Die kompakte, aus tatsächlich ausgeführten Berichten übernommene Evidenz steht in
[`../g1-evidence/independent-gate-review.json`](../g1-evidence/independent-gate-review.json).
Die separate Prüfung der vorhandenen echten Ergebnisdateien liegt in
[`../g1-evidence/root-integrity-resume.json`](../g1-evidence/root-integrity-resume.json).

## Ausführen

Voraussetzung: Python 3.11 oder neuer. Es werden nur Module der Standardbibliothek
verwendet. Vom Repository-Verzeichnis aus:

```bash
python3 experimental/weight-space-20261104/tests/adversarial_driver_gate.py
python3 experimental/weight-space-20261104/tests/targeted_final_creation_gate.py
python3 experimental/weight-space-20261104/tests/direct_dispatch_gate.py
```

Ohne Positionsargument prüfen die Programme die benachbarte Datei
`run_experiment.py`. Jedes Programm legt seine Dateien in einem neuen
System-Temporärverzeichnis ab und nennt den Berichtspfad in der letzten
JSON-Ausgabezeile. Mit `--workspace <Verzeichnis>` kann das Ausgabeverzeichnis
ausdrücklich festgelegt werden. Ein anderes Arbeitsverzeichnis ist ebenfalls
möglich, wenn der Pfad zum jeweiligen Testskript entsprechend angegeben wird.

Exitcode **0** bedeutet, dass alle Fälle der jeweiligen Suite bestanden wurden;
Exitcode **1** kennzeichnet mindestens einen fehlgeschlagenen Fall. Die historischen
fehlerhaften Quellen liefern deshalb absichtlich Exitcode 1.

## Drei getrennte Suiten

| Datei | Fälle | Prüfgegenstand |
| --- | ---: | --- |
| `adversarial_driver_gate.py` | 36 | Reale Orchestrierungs- und Belegprüfung mit 33 ungültigen Zuständen und drei gültigen Gegenproben |
| `targeted_final_creation_gate.py` | 6 | Direkter Abschlussaufruf bei fehlgeschlagener Baseline; fehlende oder veränderte Erzeugungsbelege |
| `direct_dispatch_gate.py` | 4 | Tatsächlicher zentraler `call`-Pfad, vorhandener Trainingscache und Dateiaustausch nach der Autorisierung |

Die erste Suite ersetzt keine Entscheidungs- oder Validierungshelfer. Sie führt
Konstruktor, Kalibrierungsladen, Belegprüfung und Fortsetzungssteuerung gegen
synthetische Dateien aus. Die drei festen Datenhash-Konstanten werden nur im
geladenen Testmodul an diese synthetischen Eingaben gebunden. Trainingsaufrufe
werden an den Grenzen `invoke`, `call` und Prozessstart protokolliert und
abgefangen. Auch andere Prozessstarts sind gesperrt.

Die drei gültigen Gegenproben müssen die Trainingsgrenze erreichen: eine vollständig
gültige Stufenfolge, die letzte erfolgreiche Stufe vor einem späteren Fehler und
ein vollständiger historischer Kapazitätsbeleg im unterstützten älteren Format.
Eine Implementierung, die grundsätzlich jede Fortsetzung blockiert, besteht diese
Gegenproben nicht.

Die direkte Dispatch-Suite ersetzt **nur `subprocess.Popen`**. `invoke`, `call`,
`authorize_training_call` und `input_hashes` bleiben real. Beim Austauschtest
beobachtet ein Python-Trace den tatsächlichen Rücksprung aus der Autorisierung und
ändert danach genau ein Byte des synthetischen erweiterten Checkpoints. Weder die
Entscheidung noch ihr Rückgabewert werden verändert. Der endgültige Treiber erkennt
den neuen Dateiinhalt vor dem Prozessstart.

## Ausgeführter Quellenvergleich

Alle vier folgenden Quellen wurden mit derselben portablen 36-Fälle-Suite geprüft:

| Quelle | Bestanden | Fehlgeschlagen |
| --- | ---: | ---: |
| Ursprüngliche Basis `ee5779725690c573d3310c061c6e2e6faf5f0a8b` | 7 | 29 |
| Zwischenstand `88d7b05072bdec6250990efaa9914a1e6744c29b` | 7 | 29 |
| Helferstand `06f071e5850dd405f374bbb58d826f64740d83af` | 10 | 26 |
| Endgültiger Treiber mit dem unten genannten Datei-Hash | **36** | **0** |

Geprüfter endgültiger SHA256 von `run_experiment.py`:

```text
9a68ddf27158692044738aed96892ad6476a520b7e30ab6651adfff2c4f38447
```

Die zusätzlichen Suiten bestanden mit diesem Treiber **6/6** und **4/4**.
Der davor geprüfte Reparaturstand `e75bbafd…` bestand zwar die ursprünglichen
36 Fälle, scheiterte aber an allen sechs späteren Abschluss-/Erzeugungsprüfungen
und am Austausch nach der Autorisierung. Diese Ergebnisse bleiben getrennt
dokumentiert; frühere Berichte wurden nicht nachträglich als vollständig bestanden
bezeichnet.

Die ursprüngliche 36-Fälle-Matrix wurde zuerst eingefroren. Später wurden ihre
gültigen synthetischen Eingaben um Erzeugungsbelege ergänzt, die in den echten
Läufen bereits vorhanden waren. Fallnamen und Prüfaussagen blieben unverändert.
Die anschließende portable Kopie verändert nur CLI-Vorgaben und Ausgabepfade;
die übrigen Syntaxbäume wurden auf Gleichheit geprüft. Die jeweiligen
Harness-Hashes stehen im Evidenzbericht.

## Einen historischen Stand erneut prüfen

Ein Positionsargument wählt eine andere Treiberdatei. Beispiel für die vom Nutzer
genannte ursprüngliche Basis:

```bash
G1_REVIEW_DIR="$(mktemp -d)"
git show ee5779725690c573d3310c061c6e2e6faf5f0a8b:experimental/weight-space-20261104/run_experiment.py > "$G1_REVIEW_DIR/ee577.py"
python3 experimental/weight-space-20261104/tests/adversarial_driver_gate.py "$G1_REVIEW_DIR/ee577.py" --workspace "$G1_REVIEW_DIR/results"
```

Für diese historische Quelle ist ein fehlgeschlagener Testlauf das erwartete
Ergebnis. Die tatsächlichen Quell- und Berichts-Hashes werden protokolliert.
Temporärpfade und Zeitangaben können bei einer Wiederholung abweichen.

## Aussagegrenzen

Die 46 bestandenen Prüfungen belegen die beschriebenen synthetischen
Steuerungs- und Validierungspfade. Sie sind kein Nachweis neuer Modellqualität,
eines erfolgreichen echten Trainings, einer Android-Abnahme oder vollständiger
Fehlerfreiheit. Generierte Fixtureverzeichnisse, Modellbytes und private
Holdout-Texte gehören nicht in den Commit; eingecheckt werden nur die Tests und
die kompakten Belege.
