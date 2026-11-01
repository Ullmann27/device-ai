PR #329, exakter HEAD `3064ceb85a84b24dd1d2a4d36c9059fccbf68d24`: Render-Deploy `dep-dbjfc2dg1s2s73fj9keg` auf `srv-dbje8os9v7es739rsn7g` endete am 01.11.2026 um 08:26:50 UTC mit `build_failed`. Der konkrete Fehler um 08:26:48 UTC lautet:

`Error: Node/TAP receipt rejected: BACKEND-TESTS.txt: Missing machine-readable TAP summary`

Der Fehler kommt aus `scripts/continuum-architecture-proof.mjs:149`, nach erfolgreichem Android-Test/Lint-Lauf (`BUILD SUCCESSFUL in 4m 41s`) und nach erfolgreichem Assembly-Prozess. Der Proof scheitert beim Backend-TAP-Bericht, bevor die vier JUnit-XML-Suiten validiert werden. Die tatsächlichen XML-Dateien dieses fehlgeschlagenen Builds sind nicht veröffentlicht; aus dieser Diagnose folgt keine unabhängige JUnit-XML- oder Geräteabnahme.

Ursache: QA-Zeile 115 ruft `npm test -- --test-reporter=tap` auf. Mit dem tatsächlichen `backend/package.json` entsteht `node --test test/*.test.js --test-reporter=tap`: Das Reporterflag steht hinter den Dateinamen. Der vollständige unveränderte Backend-Lauf endet zwar mit Exitcode 0 und 64 bestandenen Tests, schreibt aber Spec-Ausgabe (`✔`, `ℹ tests 64`) statt TAP. Der strikte Parser weist dieses Format korrekt zurück.

Kleinste Reparatur im bestehenden Backend-Block von `scripts/render-continuum-m8-qa.sh`:

```diff
-  npm test -- --test-reporter=tap
+  node --test --test-reporter=tap test/*.test.js
```

`npm ci`, `npm run check`, Exitcode-Gates, TAP-Parser und JUnit-Parser unverändert lassen. Weder Spec in TAP umschreiben noch fehlende Summaries akzeptieren.

Scratch-Verifikation auf exakt exportierten #329-Quellen, Node **v24.19.0** (Render verwendete **v26.13.0**):

- `npm ci --ignore-scripts --no-audit --no-fund`: Exitcode 0.
- `npm run check`: Exitcode 0.
- Alter vollständiger Backend-Aufruf: Exitcode 0; 64/64 bestanden; Originalparser lehnt den Spec-Bericht ab.
- Korrigierter vollständiger Backend-Aufruf: Exitcode 0; Originalparser bestätigt `tests=64, pass=64, fail=cancelled=skipped=todo=0`.
- Unveränderte `continuum-architecture-proof.test.mjs`: 7/7 bestanden, einschließlich roter/skipped TAP-/JUnit-Negativfälle.
- Zusätzlicher Scratch-Regressionsfall `reporter-contract.test.mjs` liest die tatsächliche Backend-Testzeile aus dem QA-Skript und führt sie gegen einen echten Minimaltest aus: unverändertes Skript **RED**, nur die vorgeschlagene Zeile geändert **GREEN 1/1**.

Für den Regressionstest einen unabhängig gestarteten Node-Testprozess verwenden und nur dessen `NODE_TEST_CONTEXT` aus dem Kind-Environment entfernen: Sonst erkennt Node den geerbten internen Testkontext als rekursiven Runner und überspringt die Kinddateien mit Exitcode 0 und leerem stdout. Dieser Testharness-Fall ist separat reproduziert und berücksichtigt.

Keine gemeinsamen Quellen/Branches bearbeitet, kein Push, kein Render-Neustart/Deploy, keine Signierung und keine Besitzerdaten. Das endgültige kombinierte SHA benötigt einen neuen tatsächlichen Render-Lauf; ein vollständiger Architektur-Proof-PASS wird hier nicht behauptet.
