# Weiterarbeitsauftrag M8 Worker (Coding-Agent) – Übergabe von Opus 5.5, 30.10.2026

**Gesamtplan:** `docs/handoff/OPUS-WEITERARBEIT-20261030.md` auf `claude/continuum-jarvis-exp20-p2-kxrxy2`; dieser Branch ist M8-spezifisch.

**Branch:** `claude/m8-coding-agent-loop-kxrxy2`, Basis `ed408a7`. Offen lassen, nicht mergen, nicht force-pushen. **Details:** `docs/research/m8/M8-CODING-AGENT-LOOP-20261030.md`.

## Was hier fertig ist
- **`.github/continuum/coding-agent-loop.mjs`**, ein werkzeugbasierter Loop als `candidateProvider`:
  - Werkzeuge: list_files, search, read_file, edit_file, check, run_tests (opt-in), finish.
  - Sichtungsmodus mit `propose_scope`.
  - `brainKind`: `SIMULATED_SCRIPT` oder `MODEL_INFERENCE_UNVERIFIED`.
- **`scripts/continuum-agent.mjs`**: Befehl wie bei Claude Code.
  - `node scripts/continuum-agent.mjs "<Auftrag>"` oder interaktiv mit `Auftrag> `.
  - Ablauf: Sichtung (nur lesend) → Freigabe `j` → `repairSession` → Patch zur Prüfung. Nie angewendet, committet oder gepusht.
- **`CONTINUUM_CODING_AGENT_LOOP=1`** schaltet den Loop in `coding-orchestrator.mjs` ein; ohne die Variable bleibt alles unverändert.
- **`scripts/continuum-agent-benchmark.mjs`**: 6 Aufgaben, `--validate`, `--brain oracle|chat`, `--executor`.
- **Tests:** `coding-agent-loop.test.mjs` 14/14. Darin: echter Korrekturzyklus, wiederholter Patch, eingeschleuste Anweisungen, Budget und Deadline, CLI als Prozess, interaktiver Modus mit Umlauten. Nachbarsuiten grün.

## Was NICHT nachgewiesen ist
- **Keine echte Modellinferenz.** Alle Erfolge stammen vom simulierten Brain.
- **Kein isolierter Executor auf dem Fold7.** Allgemeine Patches werden dort ausdrücklich nicht ausgeführt (`REJECTED_BY_EXECUTION_POLICY`).

## Aufträge in dieser Reihenfolge (je mit Abnahmekriterium)
1. **Echte Messung, Owner oder Codex auf einem Docker-Host.**
   - Ein llama.cpp-Server mit Qwen2.5-Coder-1.5B, 3B und 7B (GGUF):
     ```
     node scripts/continuum-agent-benchmark.mjs --validate
     node scripts/continuum-agent-benchmark.mjs --brain chat --executor <isolation-config.json> --out bench-<modell>.json
     ```
   - Vorher vorregistrieren: Vergleichsarm ist der alte Einmal-Provider auf denselben Aufgaben, mit McNemar.
   - **Abnahme:** Rohdateien committet; Anteil `VERIFIED` je Modell. Nur Läufe mit `MODEL_INFERENCE_UNVERIFIED` plus Server-Log des Modells gelten als echte Inferenz.
2. **Aufgabensammlung erweitern (ChatGPT-6 schreibt, Codex baut).**
   - Mindestens 30 Aufgaben, darunter Kotlin (Kette `android`) und die 4 historischen echten Fehler aus PR #245.
   - Ein fremd geschriebener, versiegelter Teil wird nur einmal geöffnet.
   - **Abnahme:** `--validate` meldet alle Aufgaben gültig.
3. **Isolation fürs Telefon (schwierigster Punkt).**
   - CI als Sandbox: Der Patch geht auf `continuum-agent/<id>`; ein Workflow führt die Kette aus (mit `continuum-agent/**` als Trigger); das Ergebnis kommt als Beleg zurück; `repair-publish.mjs` existiert.
   - Alternative: ein Android-`isolatedProcess` für JS.
   - **Abnahme:** Benchmark `gen-*` wird auf dem Telefon `VERIFIED`, ohne Ausführung ohne Isolation.
4. **Anschluss an CCC1 und die App.**
   - Der Befehl soll über `prepareCodingTask`/`runCodingTask` laufen (Journal, Belege), statt direkt über `repairSession`.
   - Danach im Chat „Worker: <Auftrag>“ über `ContinuumWorkerChatRouter` → Typed-Bridge → Termux.
   - **Abnahme:** ein Gerätelauf mit Beleg-ID.
5. **FEATURE- und TEST-Aufgaben:** eine vom Besitzer freigegebene Phase „Akzeptanztest zuerst“; danach **Übernahme nach Freigabe** (`apply` auf `continuum-agent/<id>`).
6. **Geprüftes Erfahrungs-Replay:** Nur `VERIFIED`-Transkripte dienen als Beispiele. Gemessen wird gegen „ohne Replay“; frühere Daten zeigen keinen Gewinn (6/18 gegen 6/18).

## Regeln (bleiben)
- Kein zweiter Worker.
- Keine Selbstfreigabe von Dateien.
- Keine Ausführung von Modellcode ohne Isolation.
- „Simuliert“ nie als „autonom“ berichten.
- OwnerGate, ToolGate und die CognitiveGenome-Invarianten bleiben unverändert.

**Nachtrag (letzter Auftrag):** `scripts/continuum-agent.mjs` übergibt jetzt `createScratchTestRunner` als `runTests`; `close()` läuft im `finally`.
- Der Agent testet und korrigiert innerhalb seiner Schleife: falscher Patch → `run_tests: FAILED` → Korrektur → `ALL PASSED` → `repairSession` `VERIFIED`.
- Allgemeiner Code ohne Isolation bleibt `NOT RUN` bzw. `REJECTED_BY_EXECUTION_POLICY`.
- „Geprüft“ zeigt die CLI nur bei `report.verified`.
- Test 15/15, nur mit simuliertem Brain.
