# Code54 Node-Regression auf `f0120e1b2` (HOST, Umgebung uid 0)

- Backend (`backend`, `node --test test/*.test.js`): **64/64 PASS**.
- Verträge (`node --test --test-concurrency=1 .github/continuum/*.test.mjs`, Node v22.23.4):
  **1637 Fälle: 1588 PASS, 26 FAIL, 23 SKIP**.
- Alle 26 FAIL sind namensgleich mit der Baseline auf dem installierten Code53-Stand `050a57cb` in derselben
  Umgebung (CCC1-Verifier 15, coding-orchestrator-failure-report 9 mit `ISOLATION_UNPRIVILEGED_UID_REQUIRED`,
  MΩ PR400 2) → **kein neuer Fehler, aber auch keine Behebung**. Liste: `failures.txt`.
- Abgrenzung: Das in `OMEGA-APK54-HANDOFF` genannte 354-Dateien-Profil (2455/6/37) umfasst mehr Dateien und ist
  nicht dieselbe Messung; dessen 6 FAIL sind hier nicht nachgeprüft.
