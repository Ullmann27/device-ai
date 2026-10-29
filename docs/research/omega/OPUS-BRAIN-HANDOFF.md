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
  `Ewm1Workspace` (Diskurs/Entscheidung/Evidenzpaket), `Ewm1ClaimCheck` (Aussagenprüfer), `Ewm1Experiment` (Runner)

## Stand (Entwicklung, nur DEV-Daten; TEST4 noch NICHT gelaufen)
Zwischenstand der laufenden Entwicklung, ständig aktualisiert; maßgeblich sind die Commits.

## Testbefehle
Kotlin-Harness (Basis): `scripts/android-kotlin-unit-check.sh <out>` – an `b47f4fac` 1 173 JUnit-Tests, 2 Fehlschläge
ausschließlich umgebungsbedingt (fehlendes UTF-8-Locale; `JAVA_TOOL_OPTIONS`-Banner im Kindprozess), beide grün mit
`LANG=C.UTF-8` bzw. ohne `JAVA_TOOL_OPTIONS`. EWM1-Runner:
`java … at.heinz.deviceai.intelligence.ewm1.Ewm1Experiment DEV docs/research/ewm1/ewm1-dev4-20261029.json`.

## Rückfall
Zweig verwerfen; nichts im Produktpfad geändert.
