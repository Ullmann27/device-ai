# TEST_RESULTS – Integrationszweig #375 (03.11.2026, Jarvis Integration)
- `./gradlew --offline assembleDebug testDebugUnitTest` auf 08cfb2cb: BUILD SUCCESSFUL, 1951 Tests / 0 Fehler / 17 skipped.
- `./gradlew --offline testDebugUnitTest` auf finalem Kopf: 1951 Tests / 0 Fehler / 24 skipped (davon 7 `@Ignore("S4 zu streng, B5")` in FirstThinkingRunE2ETest).
- Nicht gelaufen: Node-Wiring-Tests, Lint, Gerätetest Fold7, signierter Build.
- Details und Befunde: docs/INTEGRATIONS-CHECK.md
