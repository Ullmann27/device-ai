# OPUS-BRAIN-HANDOFF – EWM1/EWM2 Continuum-Gehirn (Forschung abgeschlossen, Integration offen)

> **Einstieg für alle Nachfolger:** `docs/research/ewm1/EWM-ARCHITEKTEN-AUFTRAG-20261029.md` (Bauplan, Arbeitspakete AP-1…AP-8,
> Abnahmen, Bauordnung). Ergebnis EWM2: `docs/research/ewm1/EWM2-RESULT-20261029.md`.

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
- EWM2: `Ewm1Brain` (Router Gedächtnis ↔ Quellen), `Ewm2Experiment` (K0 heutiger Chatpfad vs K1), `Ewm1BrainTest`;
  `docs/research/ewm1/EWM2-PREREG-20261029.md`, `ewm2-dev5-20261029.json`, `ewm2-dev5b-20261029.json` (DEV),
  `ewm2-test5-20261029.json` (versiegelt, verbraucht), `ewm2-test5-report-20261029.json`, `ewm2-test5-memory-derivative-20261029.json`,
  `ewm2-h7-report-20261029.json`, `EWM2-RESULT-20261029.md`, `EWM-ARCHITEKTEN-AUFTRAG-20261029.md`

## Stand 29.10.2026 – Ergebnisse (je ein TEST-Lauf, Details `docs/research/ewm1/EWM1-RESULT-20261029.md`)
- H1 Diskurs **BESTANDEN** (TEST4 falsch 11 vs B0 58, richtig 96 vs 76), H2 Verknüpfung **NICHT BESTANDEN** (14/17 vs 2×B0 gefordert),
  H3 Aktualität **BESTANDEN** (7/7 vs 0/7), H4 Aussagenprüfer **BESTANDEN** (48/49, 0 Fehlalarme), H5 Quellenabruf auf dem
  Gen16-Archiv **BESTANDEN** (98,4 % vs 72,5 %). Register: EXP-16 (RESEARCH_SHADOW).
- Gen16-Dateien (Besitzer, nur lokal in `$SCRATCH/gen16`, nie im Repo): SWB1 vom Produktions-Holdout-Gate verworfen,
  Archiv 556/556 gültig. Wiederholbar mit `Ewm1Gen16ImportCheck <swb1.bin> <archive.zip> <report.json>`.
- TEST4 und H5-TEST sind verbraucht; jede weitere Methodenänderung braucht ein neues versiegeltes TEST5.
- EWM2 (Freeze `e1669271`, TEST5 einmal): H6 **BESTANDEN** (K1 39 richtig / 0 falsch vs heutiger Chatpfad 30 / 18, p95 5,1 ms),
  H7 **BESTANDEN** (Fehlerklassen 20/20 vs EWM1 13/20). Unit 19/19, Mutationen 10/11 (R7 fand vor dem Freeze einen echten Fehler).
  Gesamter Kotlin-Harness am Freeze: **1 192/1 192** JUnit + 4 Checks grün (außerhalb Gradle, `LANG=C.UTF-8`, ohne `JAVA_TOOL_OPTIONS`).
  Register: EXP-17 (RESEARCH_SHADOW).
- TEST5 ist ebenfalls verbraucht. Nächster Einstieg: Architekten-Auftrag AP-1 (Umzug in `app/src/main`, Codex) und AP-2
  (Schalter OFF/SHADOW/ON); Forschung AP-8 mit TEST6 von einer **anderen** Instanz.

## Testbefehle
Kotlin-Harness (Basis): `scripts/android-kotlin-unit-check.sh <out>` – an `b47f4fac` 1 173 JUnit-Tests, 2 Fehlschläge
ausschließlich umgebungsbedingt (fehlendes UTF-8-Locale; `JAVA_TOOL_OPTIONS`-Banner im Kindprozess), beide grün mit
`LANG=C.UTF-8` bzw. ohne `JAVA_TOOL_OPTIONS`. EWM1-Runner:
`java … at.heinz.deviceai.intelligence.ewm1.Ewm1Experiment DEV docs/research/ewm1/ewm1-dev4-20261029.json`;
`java … Ewm2Experiment DEV docs/research/ewm1/ewm2-dev5-20261029.json` (K1 12/0/7), `EWM2_DIFF=1 … Ewm2Experiment DEV <korpus>`
(Router gegen reinen Workspace; DEV4/TEST3: 0 Abweichungen).

## Rückfall
Zweig verwerfen; nichts im Produktpfad geändert.
