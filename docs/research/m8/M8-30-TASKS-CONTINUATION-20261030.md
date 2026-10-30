# M8 · Coding-Agent-Benchmark 30 Aufgaben – eigenständige Fortsetzung (30.10.2026)

**Branch:** `chatgpt/continuum-m8-benchmark-coverage-20261030`, gestapelt auf `claude/m8-coding-agent-loop-kxrxy2`. **Kein Merge nach main.**

## Gelieferte konkrete Änderung
- Bestehende sechs M8-Aufgaben unverändert; neue Datei `scripts/continuum-agent-benchmark-extra.mjs` mit 24 zusätzlichen expliziten Code-Fehlern und Regressionserwartungen.
- Gesamtzahl jetzt **30**: 6 Originalfälle, 12 neue rein numerische, 12 neue allgemeine JS-Fälle.
- Für jede zusätzliche Aufgabe: `goal`, defekter echter JavaScript-Quelltext, spezifischer roter Test, eindeutiger minimaler Reparaturtext, separate unveränderte `node --test`-Kette. Unterschiedliche Rechen-, Listen-, Immutabilitäts-, Null-, Sortierungs- und Eingabefälle.
- Standardalgorithmus `repairSession`, Owner-/ToolGate, CCC1, Scope-Check, `createAgentCandidateProvider`, `run_tests`, `SIMULATED_SCRIPT` und `MODEL_INFERENCE_UNVERIFIED` bleiben unangetastet.
- **Statischer Readback:** 24/24 eindeutig lokalisierbare Ersetzungen, 24/24 Testimporte und erlaubte Pfade vorhanden. Das ist keine ausgeführte Node-Testsuite.
- Alte Ergebnisse der sechs Aufgaben bleiben historische Opus-Angaben; der neue 30er-Benchmark ist bislang **NOT_RUN**.

## Vorregistrierte tatsächliche Validierung
```sh
node --check scripts/continuum-agent-benchmark.mjs
node --check scripts/continuum-agent-benchmark-extra.mjs
node scripts/continuum-agent-benchmark.mjs --validate
node scripts/continuum-agent-benchmark.mjs --brain oracle --out benchmark-oracle-30.json
```
Erwartung (kein Testergebnis): `--validate` sollte 30/30 gültige rot/grün-Reparatur-Fixtures melden. `--brain oracle` ist **SIMULATED_SCRIPT**, keine autonome Programmierfähigkeit. Allgemeine JS-Aufgaben ohne isolierten Executor müssen **BLOCKED_NO_ISOLATION** bleiben.

## Echte Modellmessung ist ein weiteres, separates Gate
1. Modell-ID, Gewichts-Hash, Quantisierung, Tokenizer, Inferenz-Endpunkt, Hardware/Peak RAM/Energie und vorab eingefrorene Aufgaben aufzeichnen; anschließend lokal mit dem vorhandenen `--brain chat` laufen lassen.
2. Qwen2.5-Coder 1.5B, 3B und 7B gegen den alten Einmal-Provider mit gleicher Aufgabenmenge und gleichem Budget vergleichen; rohe Einzelaufgaben-Ergebnisse statt bloß einer Erfolgsquote.
3. Nur `VERIFIED` mit echter Modell-Inferenz plus unabhängiger isolierter Ausführung zählen; kein `MODEL_INFERENCE_UNVERIFIED` als nachgewiesene Modellfähigkeit umbenennen.
4. Android/Fold7 bleibt das Ziel, PC-Docker optional; ohne sicheren Executor allgemeine Patches nicht ausführen.
5. Kotlin/Android-Aufgaben brauchen eine **eigene echte Gradle/SDK36-Testkette** und werden bewusst nicht als erfolgreich getestete JS-Aufgaben verschleiert. Der zusätzliche Kotlin-Arm bleibt offen.

## Sicherheitsprotokoll
- Kein produktiver Agent-Berechtigungswechsel; kein Shell-Zugriff durch das Modell; kein automatischer Main-Merge, Signieren oder Installieren.
- Rollback: diesen Draft-PR nicht übernehmen.
- Diese Runde: `NODE_VALIDATE=NOT_RUN`, `DOCKER_ISOLATION=NOT_RUN`, `REAL_MODEL=NOT_RUN`, `FOLD7=NOT_RUN`.
