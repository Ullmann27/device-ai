# Code50 Gate-Fix Handoff — 2026-11-08

## Arbeitszweig

- Branch: `codex/code50-gate-fixes-20261108`
- Ausgangsbasis: PR #603 / `codex/continuum-code50-integration-20261107`
- Basis-SHA: `1689643fd0cdcf0f236c0513aaf27785b1306b96`
- Ziel-PR-Basis: `codex/continuum-code50-integration-20261107`
- `main`: unverändert

## Bestätigte Blocker

1. `AgentActivity` übergibt das optionale CTX3-State-Telegramm direkt an `LocalGroundedRecall.boundedOrNull`.
2. Ist das Telegramm größer als 3.000 Zeichen, wird der gesamte `LocalGroundedRecall` verworfen. Dadurch kann ein rein optionales Shadow-Feld den exakten Abruf und die lokale Modellsynthese abschalten.
3. `.github/workflows/continuum-code50-full-qa.yml` widerspricht `CLAUDE.md` und `AGENTS.md`: Android-/Gradle-Abnahme darf nicht über GitHub Actions erfolgen, sondern über den Render-Android-QA-Host oder einen nachweislich funktionierenden lokalen Build.

## Geplanter kleinster Fix

1. RED: Regressionstest so ändern/ergänzen, dass ein übergroßes optionales Telegramm verworfen wird, der exakte Abruf aber erhalten bleibt.
2. GREEN: `LocalGroundedRecall.boundedOrNull` so korrigieren, dass nur das optionale Telegramm entfällt; Grenzen für Antwort und Provenienz bleiben fail-closed.
3. Den unzulässigen Actions-Workflow löschen.
4. Zieltests und vollständige, SHA-gebundene Render-QA ausführen.
5. Review-Threads erst nach frischer Verifikation beantworten/auflösen.

## Relevante Experimente

- EXP-05: lokaler Modellpfad
- EXP-06: Provenienz und beleggebundener Kontext
- EXP-07: unabhängige Regressionstests
- EXP-11: bestehende Orchestrierung statt Parallelsystem
- EXP-12: Kontext-/Identitätskontinuität ohne unberechtigte Promotion

## Status

- Root Cause: `CONFIRMED`
- Codeänderungen: `NOT_STARTED`
- Zieltests: `NOT_RUN`
- Vollständiger Gradle/JUnit/UI/Lint-Lauf: `NOT_RUN`
- Render-Android-QA: `NOT_RUN`
- Fold7/Avatar/Voice/Thermik: `NOT_RUN`
- APK/Signierung/Release: `NOT_RUN`

## Rückfallplan

Den Reparaturzweig nicht in PR #603 übernehmen. Die Ausgangs-SHA bleibt unverändert und kann ohne Reset weiterverwendet werden.
