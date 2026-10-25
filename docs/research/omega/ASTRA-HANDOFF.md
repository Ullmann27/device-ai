# Astra: übernehmbarer GitHub-Stand (2026-10-25)

Repository: https://github.com/Ullmann27/device-ai · Branch `research/continuum-omega-vhsr-20261025` · Draft https://github.com/Ullmann27/device-ai/pull/133 · Basis `821df5d1acf68f0caf10369298352a3589f56a97` (erneut remote geprüft).

Gelesen und angewendet: `AGENTS.md` aus PR #135, Commit `e7ac9fdd94f9c568e1a3a55f42846c88dddb87d9`. Dessen Branch `research/continuum-typed-review-chatgpt-20261025` und Basis `research/continuum-omega-resume-chatgpt-20261025` bleiben unverändert. Keine automatische Zusammenführung paralleler Arbeiten. Frühere Forensik: [HANDOFF.md](HANDOFF.md); Forschungsplan: [PLAN.md](PLAN.md). Dieser Stand ersetzt dortige überholte Angaben zu noch offenen Baseline-Reparaturen und Android-Prüfungen.

## Gesicherte Meilensteine

- https://github.com/Ullmann27/device-ai/commit/0d4a0c88a99b8a88e92534083fd424794e5df524: Forensik, unveränderte Baseline-Logs, Plan.
- https://github.com/Ullmann27/device-ai/commit/4b9ead538514680427f3330ef6b6e1938f9123d5: Kotlin-Analyse verwirft AgentActivity nicht länger; tatsächlicher Bridge-Negativtest; QA-Gradleaufruf und Backend-Ignore repariert. Fokus 32/32. Gesamtlauf 1193: 1186 PASS / 1 FAIL / 6 SKIP. Offener Fehler: Watchdog-Abbruchbestätigung unter paralleler Gradle-Last; isolierter Wiederholungslauf 7/7 PASS. Kein behobener Watchdog behauptet.
- Der Commit dieser Datei ergänzt EXP-15. Exakte SHA: `git log -1 --format=%H -- docs/research/omega/ASTRA-HANDOFF.md`; nächste Sicherung ergänzt einen direkten Link.

## Implementiert und angeschlossen (EXP-15)

- `.github/continuum/repair-research-verifier.mjs`: streng gebundene, zusätzlich ablehnende Prüfer-Schnittstelle; Commit, Kandidatenbytes, Suitehash, Prüfanzahl, endliche Gegenbeispiele; Fehler/Frist/ungültige Antworten führen zur Enthaltung.
- `repair-loop.mjs`: optionale Capability `candidateVerifier`, nach unveränderten Standardtests. Gegenbeispiele fließen in dieselben Diagnosen und dasselbe Modellaufrufbudget zurück.
- `backlog-repair-worker.mjs`: erneute Prüfung der tatsächlichen Dateien im bestehenden unabhängigen Worktree; nur übereinstimmende PASS-Belege können die bisherigen Owner-/Receipt-Gates passieren.
- `experimental/omega/repair/`: eingefrorene numerische Fälle und metamorphe Relationen; separater Prozess mit Zeit-/Heaplimit. Nur die bestehende eingeschränkte Ausdrucksgrammatik wird ausgeführt. Node VM allein ist KEINE OS-Sandbox; kein Anspruch für beliebigen JavaScript-Code.
- `repair-critic.mjs`: OMEGA-Quellen und Auswertungsdefinitionen vor Modellpatches geschützt. `scripts/continuum-omega.test.mjs` bindet Forschungstests in normale M6-Discovery ein.
- `evidence/branch-inventory.json` und `pr-lineage.json`: reale Remote-Branch-/PR-Abstammung und Dateivergleiche; keine verloren gegangenen Sol-Quellen erfunden.

## Wirklich ausgeführte Prüfungen

```sh
env -u NODE_TEST_CONTEXT node --test --test-concurrency=2 .github/continuum/repair-research-verifier.test.mjs .github/continuum/research-verifier-integration.test.mjs .github/continuum/scoped-repair-pipeline.test.mjs scripts/continuum-omega.test.mjs
JAVA_HOME=<JDK17> ANDROID_HOME=<SDK36> gradle --no-daemon --max-workers=2 --console=plain -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin
```

EXP-15-Fokus: **26/26 PASS**, 0 SKIP, 3.522 s; Schutzpfadtest vorher reproduziert ROT.

JDK 17.0.21+9 / Gradle 8.11.1 / SDK36 / Build Tools35.0.0. Android: 192 Suiten, 970 Tests, 967 PASS, 3 SKIP, 0 FAIL/ERROR; Compile/Lint erfolgreich. Lint prüft per bestehender Konfiguration nur `NewApi`. UI-Widget-Opt-in, Instrumentation und physischer Fold7 NOT_RUN. Keine APK gebaut. Ausgangsversuch mit JRE21 fehlte JAVA_COMPILER; kein Quellfehler.

Backend unverändert: 60/60 PASS. Vollständiger Node-Lauf nach EXP-15 steht noch aus. GitHub Actions bleibt durch Account-Zahlungs-/Budgetblockade vor Teststart blockiert; lokaler Erfolg ist kein Actions-Erfolg.

## Grenzen, nächste Schritte, Rückfall

1. Kontrolliertes A/B-Experiment mit unveränderter Schleife, identischem Kandidatenbudget und getrennten Bewertungsfällen; synthetischer Kandidatenstrom ist keine Qwen-Inferenz und kein blinder Generalisierungsnachweis.
2. Vollständige Node-Suite nach Ende der Gradle-Last wiederholen; Watchdog-Befund dokumentieren, Sicherheitsbedingung nicht abschwächen.
3. Architekturkarte mit wirklichen Aufrufern, negativen Befunden und unabhängiges Code-Review fertigstellen; parallele PR #134/#135 vor einer späteren Integration vergleichen.
4. RPK1 an vorhandenen CEFR-Ranker anschließen; H-MAC an bestehenden ExperienceCompiler. EIG-SC, gemeinsamer Checkpoint-DAG, Operator-VM, AEGIS und ARCHETYP bislang Plan, nicht neue funktionsfähige Module.

Default `candidateVerifier=null` erhält die bestehende Pipeline. Kein Einbau in Besitzer-App/produktive Lerngewichte. Revert der EXP-15-Änderungen oder kein Import des optionalen Verifiers stellt bisherigen Pfad wieder her; keine Datenmigration. App-ID, Signatur, Besitzerrelease und main bleiben unverändert. Suiteautoren sind vertrauenswürdige Prüfautoren; Hashes beweisen weder Wahrheit noch Authentizität. Der numerische Prototyp bleibt eng begrenzt.

## Checkpoint: Vergleichsprotokoll eingefroren

VH-SR-Implementierung remote bestätigt: https://github.com/Ullmann27/device-ai/commit/6345fab3fceaa2cd44ca5de30aa82c9fbe229feb .

`experimental/omega/benchmarks/controlled-repair.mjs` führt beide Varianten über `executeOwnerRepairTask` aus. Je fünf synthetische Aufgaben, zwei Wiederholungen, abwechselnde Reihenfolge, maximal zwei Vorschläge. Separate Online-/Bewertungszahlen; kein blinder Holdout. Ein separater Entwicklungsfall prüft die Messung. Benchmark-Kommando: `env -u NODE_TEST_CONTEXT node experimental/omega/benchmarks/controlled-repair.mjs > results.json`. Ergebnis erst nach diesem Checkpoint ausführen und archivieren. Entwicklungsprüfung: 2/2 PASS; kein Benchmarkerfolg vorweggenommen. Parent-CPU/RSS sind keine Messung des gesamten Prozessbaums.

Ruling: Zeilen-Codec vorerst verschoben, weil die bestehende Schnittstelle bereits exakt gebunden ist und kein neues echtes Modell-A/B vorliegt. Die nächste Priorität bleibt gemessene zusätzliche Verifikation; Kosten der Verschiebung: kein Codec-Nutzenvergleich in diesem Meilenstein. RPK1/H-MAC/EIG-SC folgen erst nach diesem Messinstrument; keine zehn unverbundenen Kerne anlegen.

## Ausgeführter Vergleich und neue Besitzerpriorität

Eingefrorene Benchmarkquelle: https://github.com/Ullmann27/device-ai/commit/da8196c9c1cdb46aa20d651b330d99e49f4cf7f3 . `evidence/controlled-replay.json` enthält alle 20 Läufe, Bindungen, Gegenbeispiele und Messwerte. Pro Variante 5 Aufgaben × 2 Wiederholungen: Baseline 2/10 korrekt, 8 falsche Annahmen; VH-SR 8/10 korrekt, 0 falsche Annahmen, 2 Abbrüche bei erschöpftem Budget. Vorschläge 10 vs18; tatsächliche Modellaufrufe jeweils0. Summierte Reparaturzeit 5.183 vs7.505 Sekunden. Keine gleich hohe tatsächliche Rechenlast und keine allgemeine Intelligenzsteigerung nachgewiesen. Zusätzliche Prüffälle sind konstruktiv auf Fehlererkennung ausgelegt; unabhängige Aufgaben und echtes Modell fehlen.

Neue Besitzerpriorität: vorhandene Android-Dialog-/Gedächtnis-/Reasoning-/Tool-Pfade zu einem überprüfbaren JARVIS-Gesamtablauf verbinden. Bereits vorhanden: `AgentActivity.runLocal` → `BoundedAgentOrchestrator`, `HybridMemoryRetriever`, `ChatReasoningRouter`, `LocalModelReasoningAdapter`, LiteRT-LM, `ToolGate`, ExperienceStore. Kein neuer Orchestrator erforderlich. Konkret entdeckt: Gesprächskontext umfasst nur eine vorherige Antwort; Fallback-Prompt verliert Provenienz/Validierungsstatus; lexikalische Qualitätsprüfung wird als Experience-SUCCESS gespeichert, obwohl sie Wahrheit ausdrücklich nicht prüft. Nächster Schritt: bestehende Schnittstellen für begrenzten Mehrnachrichtenkontext und evidenzbewusste Erfahrung verbinden, dann fünf semantische Integrationsszenarien. Native Modell-/Fold7-Qualität bleibt ohne Gerät separat offen.
