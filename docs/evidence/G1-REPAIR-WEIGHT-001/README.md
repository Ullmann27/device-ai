# G1-REPAIR-WEIGHT-001: Pflichtgate vor der Trainingsfortsetzung

## Auftrag und Quellenbindung

- Eigener Draft-PR: [#497](https://github.com/Ullmann27/device-ai/pull/497).
- Führender Gewichte-PR: [#492](https://github.com/Ullmann27/device-ai/pull/492).
- Geprüfte Basis: `f9ea9ba461360fb6b4f5369416eda605a8354766`.
- Eigener Branch: `research/continuum-weight-helper-team-20261104`.
- Produktivcode im Fix: ausschließlich `experimental/weight-space-20261104/run_experiment.py`, Methode `continuation()`.
- Neue isolierte Tests: `experimental/weight-space-20261104/test_helper_regressions.py`.

| Quelle | Vollständiger SHA256 |
| --- | --- |
| Orchestrator vor dem Fix | `609215b0f2671e2407c8b81867255bd1ce60680626c081fd91b0a12d14f62deb` |
| Orchestrator nach dem Fix | `6bd9c00c635f100fdedc05e60a1d720565e43ba7e6722bdc7cf7188f4a87487d` |
| Identische Tests für RED und GREEN | `c32a7d80d0592fe3a4e9d6b889c7d7aaa8e059ade30047a20d701f27df4d73cc` |

Der über GitHub für die Basis gelesene Orchestrator hat den Git-Blob-SHA
`d3ed369a79403b3625f4e3aa9eada465184e3148`. Der isolierte RED-Snapshot wurde aus
dem lokal vorhandenen Quellcommit `ee5779725690c573d3310c061c6e2e6faf5f0a8b`
exportiert und gegen genau diesen Blob geprüft. Die beiden Revisionen enthalten
denselben Orchestrator. Die Basis für den veröffentlichten Fix bleibt der oben
angegebene PR-Head `f9ea9ba461360fb6b4f5369416eda605a8354766`.

## Reproduzierter Fehler und Änderung

Bei `lastPassed=null` und `stop=BASELINE_GATE` übersprang der bisherige Code nur
den erweiterten Trainingspfad. `continued-small-train` wurde dennoch aufgerufen.
Eine vorhandene `frozen-selection.json` konnte außerdem einen stillen erfolgreichen
Return auslösen, bevor der fehlgeschlagene Pflichtgate-Status ausgewertet wurde.

`continuation()` liest und prüft jetzt den Ladder-Zustand vor der Kalibrierung,
vor jedem Trainingsaufruf und vor dem Frühereturn für eine vorhandene Auswahl.
Geschlossene Gates lösen einen `RuntimeError` mit stabiler Kennung aus:

| Zustand | Fehlerstatus |
| --- | --- |
| Ladder-JSON ist kein Objekt | `CONTINUATION_GATE_REJECTED: INVALID_LADDER` |
| `stop=BASELINE_GATE`, auch bei widersprüchlichem `lastPassed` | `CONTINUATION_GATE_REJECTED: BASELINE_GATE` |
| `lastPassed` fehlt, ist `null` oder kein Objekt | `CONTINUATION_GATE_REJECTED: NO_PASSING_CANDIDATE` |
| `lastPassed.capacityEligible` ist nicht exakt `true` | `CONTINUATION_GATE_REJECTED: LAST_PASSED_INELIGIBLE` |

Der Fehler verlässt die Phasensteuerung in `main()`. Im Modus `all` wird danach
`final()` nicht ausgeführt. Der Fehlerpfad erzeugt keine neue Auswahl und verändert
eine bereits vorhandene Auswahl nicht. Fehlende Dateien und syntaktisch ungültiges
JSON brechen weiterhin mit ihren bestehenden Python-Exceptions vor der Fortsetzung
ab; hierfür wird keine neue Fehlerkennung eingeführt.

`PARITY_OR_CAPACITY_FAILURE` und `CHECKPOINT_64_MIB_CONTRACT` bleiben mit einem
gültigen, kapazitätsgeprüften `lastPassed` zulässig. Trainingsparameter, Auswahlregel,
Modellcode und Prüfschwellen werden durch diesen Fix nicht geändert.

## Tatsächlicher RED→GREEN-Nachweis

Ausgeführt am 2026-11-04 mit Python 3.12.14. Die vollständigen Ausgaben stehen in
[red.log](red.log) und [green.log](green.log); Befehle, Laufzeiten, Exitcodes und
SHA256-Werte stehen maschinenlesbar in [manifest.json](manifest.json).

| Ausführung | Tests | Ergebnis | Exitcode |
| --- | ---: | --- | ---: |
| RED: unveränderter Basis-Orchestrator | 12 | 7 fehlgeschlagene Assertions, 1 Fehler, 4 positive Kontrollen bestanden | 1 |
| GREEN: Orchestrator mit Gate-Fix | 12 | Alle bestanden | 0 |

Die acht negativen Fälle prüfen das gescheiterte Baseline-Gate, denselben Zustand
nach echtem Konstruktor-Resume, fehlenden Stop-Grund, fehlendes `lastPassed`-Feld,
ein nicht zugelassenes `lastPassed`, einen widersprüchlichen Baseline-Stop, eine
bereits vorhandene Auswahl beim Resume und den Abbruch von `main(all)` vor `final()`.

Die vier positiven Fälle prüfen den normalen Trainingsübergang, den letzten
gültigen Kandidaten nach einem Fehler einer höheren Stufe, das Kapazitätslimit
sowie einen vollständigen synthetischen Ablauf beider Trainingspfade bis zur
Auswahl. Dessen anschließender Konstruktor-Resume wiederholt keinen Trainingsaufruf
und erhält die Auswahl bytegenau.

Die Tests verwenden den echten Konstruktor, die echten Beleg- und Hashprüfungen
und die echte Phasensteuerung. Alle Eingaben und Belege entstehen in temporären
Verzeichnissen; die Checkpoint-Dateien enthalten ausschließlich synthetische Bytes.
`java -version` und die JVM-Prozessgrenze werden abgefangen. Der vollständige
positive Durchlauf liest vorbereitete synthetische Trainingsbelege. `invoke()`
wird zusätzlich beobachtet, damit bei gesperrtem Gate auch ein Aufruf mit bereits
vorhandenen Trainingsbelegen erkannt wird. Die Zeilen `START continued-small-train`
in den Logs stammen von Übergangsprüfungen, die vor dem Prozessstart abgefangen
werden. Sie sind keine ausgeführten Trainingsläufe.

Es liefen keine JVM-Prozesse und kein tatsächliches Modelltraining. Reale Modell-
und Holdoutinhalte wurden nicht verwendet. Die Tests belegen den Kontrollfluss;
Modellqualität und Android-Laufzeitverhalten sind kein Ergebnis dieser Prüfung.

Eine unabhängige zweite Code-Review prüfte Gate-Reihenfolge, gültige spätere
Stop-Gründe, Resume-Verhalten, den Abbruch vor `final()` und den Dateiumfang ohne
blockierenden Befund. Diese Review ist eine Codeprüfung, kein zusätzlicher Testlauf.

## Lokal wiederholen

Vom Repository-Stamm aus, mit Python 3.11 oder neuer; keine zusätzlichen Pakete,
kein JAR und keine Modelldateien erforderlich:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s experimental/weight-space-20261104 \
  -p test_helper_regressions.py -v
```

RED gegen die exakte Basis mit denselben neuen Tests wiederholen. Der letzte
Befehl soll mit Exitcode 1 enden:

```bash
G1_RED_DIR="$(mktemp -d)"
git show f9ea9ba461360fb6b4f5369416eda605a8354766:experimental/weight-space-20261104/run_experiment.py \
  > "$G1_RED_DIR/run_experiment.py"
cp experimental/weight-space-20261104/test_helper_regressions.py "$G1_RED_DIR/"
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s "$G1_RED_DIR" -p test_helper_regressions.py -v
```

## Rückfall und Übergabe

Vorgesehener Rückfall: den Fix nicht übernehmen und den ursprünglichen Modellstand
behalten. Der Fix schreibt keine Modellartefakte. Eine vorhandene, zum
fehlgeschlagenen Gate widersprüchliche `frozen-selection.json` wird nicht gelöscht
oder nachträglich legitimiert; der neue Gate-Fehler sperrt ihre Wiederverwendung
durch `continuation()`.

Falls der Fix später übernommen wurde, den konkreten Fix-Commit auf einem eigenen
Review-Branch mit `git revert` zurücknehmen und die Rücknahme prüfen lassen. Keine
erzwungene Branch-Rücksetzung und keine Änderung an bestehenden Checkpoints oder
Belegdaten. Die Rücknahme stellt auch den alten Gate-Fehler wieder her; deshalb
darf ein fehlgeschlagener Ladder-Lauf mit dem alten Code nicht fortgesetzt werden.

Der Fix wird als eigener Commit auf PR #497 bereitgestellt. Die vollständige
Commit-SHA wird nach dem Push in der PR-Beschreibung und der Übergabe an #492
angegeben. Es erfolgt keine Modell-Promotion, kein produktives Training, kein
Release und kein Main-Merge.
