# Reproduktion und Prüfung

Diese Forschung läuft lokal mit vorhandenen Werkzeugen. Die protokollierten Messwerte stammen vom bezeichneten Host; ein anderer Rechner darf andere Laufzeiten und wegen numerischer Ausführungsdetails auch abweichende Trainingsresultate liefern. Die vier tatsächlich gemessenen Kandidaten liegen deshalb vollständig unter `language/run-v1/` vor.

## Unveränderte Quellen wiederherstellen

Der bereits in Runde 1 ausgeführte und geprüfte Helfer `../restore_sources.py` liest ausschließlich vorhandene lokale Git-Objekte, prüft deren SHA-256 und schreibt in ein neues Verzeichnis. Er lädt keine Modelle herunter. Benötigt werden die im Herkunftsmanifest genannten Commits einschließlich Originalcheckpoint, Tokenizer, Trainingsdokumenten und Avatarasset. Falls diese auf mehrere lokale Git-Objektdatenbanken verteilt sind, unterstützt der Helfer zusätzliche `--additional-repo`-Argumente. Ein fehlendes Objekt ist ein fehlender Input, kein Anlass für einen ungeprüften Ersatz.

Vom Repositorywurzelverzeichnis aus, wenn die erforderlichen Objekte dort vorliegen:

```bash
python3 experimental/three-bot-research-20261104/restore_sources.py \
  --repo . \
  --output /tmp/continuum-research-inputs
```

Der Ausgabeordner muss neu sein. Die wiederhergestellten Modellquellen stehen danach unter `/tmp/continuum-research-inputs/sources/weight`, Originalcheckpoint und Tokenizer unter `/tmp/continuum-research-inputs/sources/lm1-original-inputs`.

## Vorhandene Modellresultate ohne Training kontrollieren

Die zusätzliche Root-Prüfung liest die veröffentlichten vier Kandidaten und Rohbelege sowie den wiederhergestellten Originalcheckpoint. Sie verändert keinen Modellzustand. Der Ausgabepfad darf noch nicht existieren.

```bash
python3 experimental/three-bot-research-20261104/round2/verification/verify_language_results.py \
  --language experimental/three-bot-research-20261104/round2/language \
  --original /tmp/continuum-research-inputs/sources/lm1-original-inputs/lm1-baseline.lm1 \
  --output /tmp/continuum-language-result-audit.json
```

Der Audit ist absichtlich auf diesen registrierten Lauf und seine feste Identität zugeschnitten. Er ist keine allgemeine Modellfreigabe. `verification/verify_evidence.py` dokumentiert außerdem den auf dem ursprünglichen Arbeitsbestand ausgeführten Hash-/GLB-/Recall-Audit; dessen historischen SourceBindings enthalten absolute Herkunftspfade und er benötigt die dort bezeichneten vollständigen Zwischenprodukte.

## Erneute native Trainingsausführung

Für einen neuen Lauf werden Java sowie die in der Registrierung gebundenen Kotlin-2.0.20-Bibliotheken benötigt. Das bereits vor dem Training veröffentlichte `language/build/state-study.jar` ist vollständig enthalten. Der Runner prüft insbesondere die Hashes von Compiler und Standardbibliothek. Ein anderes Paket ist kein stiller Ersatz.

Der folgende Pfad benennt den tatsächlich verwendeten, vorhandenen Bibliotheksbestand dieser Umgebung. Auf einem anderen Host muss `--kotlin-lib` auf dieselben nach Hash verifizierten Bibliotheksdateien zeigen.

```bash
python3 experimental/three-bot-research-20261104/round2/language/run_study.py run \
  --registration-commit 93b43fa2a80109c64c30335149227ccfcebe0f0a \
  --source /tmp/continuum-research-inputs/sources/weight \
  --inputs /tmp/continuum-research-inputs/sources/lm1-original-inputs \
  --kotlin-lib /workspace/scratch/b7d23de0e715/build-toolchain/gradle-8.11.1/lib \
  --output experimental/three-bot-research-20261104/round2/language/run-reproduction
```

Der neue Ausgabeordner muss unter `language/` liegen und darf nicht existieren. Die ursprünglichen `run-v1`-Dateien bleiben erhalten. Der Runner erzwingt Ressourcenbudgets, neue Kapazitätsprüfungen, unveränderte Daten und Freeze vor der Endauswertung. Eine Wiederholung mit denselben bereits bekannten Testfällen ist eine Reproduktion; sie liefert keine neue unverbrauchte Bestätigung einer daran angepassten Methode.

Die zugehörige Auswertung erfolgt anschließend ausdrücklich mit dem neuen Pfad:

```bash
python3 experimental/three-bot-research-20261104/round2/language/analyze_study.py \
  --run experimental/three-bot-research-20261104/round2/language/run-reproduction
```

## Erfahrungsabruf

Der Recall-Runner benötigt Python, Node, Git und `rg` sowie die Produktbaseline als lokales Git-Objekt. Er erstellt selbst ein neues Laufverzeichnis, rekonstruiert die Quellen aus dem gepinnten Objekt und verwendet ausschließlich entsorgbare synthetische Nachweise:

```bash
python3 experimental/three-bot-research-20261104/round2/autonomy/run_experiment.py --repo .
```

Er startet keinen echten Besitzerauftrag oder Modellprozess. Die Bezeichnung eines synthetischen Nachweises als `LOCAL_MODEL_HOST` innerhalb der Vertragsfixture ist ausdrücklich kein Nachweis einer tatsächlichen Modellinvokation.

## Avatarvarianten

Die Herstellskripte benötigen Python mit NumPy und Pillow, die kontrollierten 3D-Renderings zusätzlich Blender. Der unabhängige Wiederaufbau dieser Runde verwendete die im Beleg `verification/avatar-rebuild.json` protokollierten Versionen. Die großen End-GLBs und End-PNGs sind bewusst reproduzierbare Zwischenprodukte; die endgültigen Review-JPEGs und die erste Fehliteration sind veröffentlicht.

`avatar/scripts/build_variants.py --source` erhält den ursprünglichen, nach Hash gebundenen GLB. Es schreibt relativ zu seinem eigenen Skriptverzeichnis unter `avatar/variants` und `avatar/results`. Für eine Reproduktion sollte deshalb zuerst eine eigene Kopie des Avatar-Forschungsordners angelegt werden, damit die erhaltenen originalen Messmanifeste nicht überschrieben werden. `render_variants.py` rendert anschließend den Originalkopf und beide dort erzeugten Varianten; `assemble_review.py --reference` setzt die tatsächlich gerenderten Ansichten mit der gebundenen Originalreferenz zusammen.

Die erwarteten finalen GLB-Hashes sind:

| Variante | SHA-256 |
|---|---|
| V1 | `ecead099e3e8312e8211dbfd48228a0af258836476691ce3302a58cf0dc0d6e3` |
| V2 | `60e4107e4577070ef123652501956e6cd41f678c8cd4b65f405ab19d73a07a14` |

Root hat beide Dateien mit einer unabhängigen Kopie des Herstellskripts tatsächlich erneut erzeugt und exakt diese Hashes bestätigt. Diese Reproduktion ist keine Android-/Filament- oder Liveaudio-Abnahme.
