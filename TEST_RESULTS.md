# TEST_RESULTS – continuum/haupt-chief-pattern-20261103

Stand 2026-11-03, 14:45 (Europe/Vienna). GREEN-SHA `9e811a87` (RED `cf164bcd`, Basis #373 `26162442`).

| Lauf | Ergebnis |
|------|----------|
| `./gradlew :app:testDebugUnitTest` (voll, JDK 17) | 1803 Tests, 0 Fehler, 0 Errors, 5 übersprungen – HOST_VERIFIED |
| davon neu `chief.*` (TurnLifecycle 4, ChiefWorkerPool 3, ToolSchemaSafety 3) | 10/10 grün; RED-Commit: 9 rot, Injection-Pin bereits grün (bestehendes Verhalten) |
| `node --test .github/continuum/*.test.mjs` | 1309 Tests, 1296 pass, 0 fail (Rest übersprungen) |
| `./gradlew :app:lintDebug` | BUILD SUCCESSFUL, keine neuen Lint-Fehler – HOST_VERIFIED |

Ungetestet / offen: App-Verdrahtung von TurnLifecycle und ChiefWorkerPool (nicht enthalten), alle Fold7-Prüfungen (DEVICE_REQUIRED).
