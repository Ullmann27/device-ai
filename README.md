# LM1-Checkpoints vom 22.10.2026 (Datenzweig, nur Forschung)

Dieser Zweig enthält **keinen Code**. Er sichert alle Trainingsstände der LM1-Auswahlstudie, damit niemand sie neu
trainieren muss. Der Code liegt in PR #75 (`opus/continuum-language-core-20261022`).

- **Trainiert mit:** Commit `9630fd49` (letzte Änderung an `learning/lm` vor dem Start), JAR-SHA-256 `27f84b6b…706a`.
- **Lauf:** `LmExperiment <repo> select store=<dir>`, Start 2026-10-22T15:40:34Z, Ende 17:02:38Z, Exitcode 0, 4 Threads.
  Das Training ist bei gleicher Threadzahl deterministisch.
- **Inhalt:**
  - `checkpoints/<sha256>.lm1` – 128 Checkpoints (16 Läufe × 8 Epochen). Der Dateiname ist der SHA-256 des Inhalts;
    `LmCheckpointStore` prüft ihn bei jedem Lesen.
  - `probe/` – 2 Checkpoints des kurzen Vorlaufs (`quick`), nicht auswahlrelevant.
  - `MANIFEST.tsv` – je Checkpoint Familie, Seed, Epoche, VALIDATION-BPB, Trainings-NLL, Parameter, Bytes.
  - `select.log` – das vollständige Protokoll des Auswahllaufs.
- **Benutzung:** Ordner `checkpoints/` als `store=` übergeben, z. B.
  `LmExperiment <repo> final store=checkpoints kn=7 cdx1epochs=3 ckpt=512:<sha> …`.
- **Grenzen:** Forschungsgewichte. Sie werden von keiner App-Funktion geladen und ersetzen weder SWB1 noch CDX1.
