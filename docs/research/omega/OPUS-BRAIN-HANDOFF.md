# OPUS-BRAIN-HANDOFF – EWM1 Evidenz-Arbeitsgedächtnis (WIP / UNVERIFIED)

Zweig `opus/continuum-brain-research-20261029`, Basis PR #216 @ `b47f4fac` (enthält #215). Experiment: EWM1, angelehnt an
EXP-13/EXP-14 (Gedächtnis/Kontext, RESEARCH_SHADOW). Reines Test-Quellset, nicht in der APK, keine Gewichte,
keine persönlichen Daten, keine Generation 16, kein Gemma-Lauf, kein Gerät.

## Zweck
Ein Evidenz-Arbeitsgedächtnis zwischen Ledger und Gemma: Diskursfokus (Pronomen, Wechsel, Korrektur), Entitätsgraph
(Rollen/Aliase, Zwei-Schritt-Fragen), Aktualität (neuere Aussage verdrängt ältere, wird nicht gelöscht) und ein
deterministischer Aussagenprüfer für Modellantworten. Ruft den unveränderten B0 (`ContinuumMemoryDialogue.rank`) auf.

## Dateien
- `docs/research/ewm1/EWM1-PREREG-20261029.md` – Vorregistrierung (vor dem Code, Commit `3e41bb8b`)
- `docs/research/ewm1/EWM1-ARCHITEKTUR-BESTAND-20261029.md` – Bestandsaufnahme des echten Chatpfads
- `docs/research/ewm1/ewm1-dev4-20261029.json` (DEV), `ewm1-test4-20261029.json` (TEST, versiegelt `fbe484ac…`)
- `app/src/test/java/at/heinz/deviceai/intelligence/ewm1/` – `Ewm1Lexicon`, `Ewm1EvidenceWorkspace` (Fakten, Graph),
  `Ewm1Workspace` (Diskurs/Entscheidung/Evidenzpaket), `Ewm1ClaimCheck` (Aussagenprüfer), `Ewm1Experiment` (Runner),
  `Ewm1SourceIndex` + `Ewm1SourceExperiment` (H5 Quellen), `Ewm1Gen16ImportCheck` (Trockenlauf der Besitzer-Sicherungen),
  Tests `Ewm1WorkspaceTest`, `Ewm1SourceIndexTest` (14/14; 9 Mutationen alle erkannt)

## Stand 29.10.2026 – Ergebnisse (je ein TEST-Lauf, Details `docs/research/ewm1/EWM1-RESULT-20261029.md`)
- H1 Diskurs **BESTANDEN** (TEST4 falsch 11 vs B0 58, richtig 96 vs 76), H2 Verknüpfung **NICHT BESTANDEN** (14/17 vs 2×B0 gefordert),
  H3 Aktualität **BESTANDEN** (7/7 vs 0/7), H4 Aussagenprüfer **BESTANDEN** (48/49, 0 Fehlalarme), H5 Quellenabruf auf dem
  Gen16-Archiv **BESTANDEN** (98,4 % vs 72,5 %). Register: EXP-16 (RESEARCH_SHADOW).
- Gen16-Dateien (Besitzer, nur lokal in `$SCRATCH/gen16`, nie im Repo): SWB1 vom Produktions-Holdout-Gate verworfen,
  Archiv 556/556 gültig. Wiederholbar mit `Ewm1Gen16ImportCheck <swb1.bin> <archive.zip> <report.json>`.
- TEST4 und H5-TEST sind verbraucht; jede weitere Methodenänderung braucht ein neues versiegeltes TEST5.
- Nächster Einstieg: Fehlerliste im Ergebnisdokument (Abkürzungspunkt, Ort nach Präposition, Merkmalsnomen) auf DEV-Daten
  beheben, dann TEST5 vorab versiegeln; Integrationsschalter für Codex (Ergebnisdokument, Abschnitt Integrationsstellen).

## Testbefehle
Kotlin-Harness (Basis): `scripts/android-kotlin-unit-check.sh <out>` – an `b47f4fac` 1 173 JUnit-Tests, 2 Fehlschläge
ausschließlich umgebungsbedingt (fehlendes UTF-8-Locale; `JAVA_TOOL_OPTIONS`-Banner im Kindprozess), beide grün mit
`LANG=C.UTF-8` bzw. ohne `JAVA_TOOL_OPTIONS`. EWM1-Runner:
`java … at.heinz.deviceai.intelligence.ewm1.Ewm1Experiment DEV docs/research/ewm1/ewm1-dev4-20261029.json`.

## Rückfall
Zweig verwerfen; nichts im Produktpfad geändert.
