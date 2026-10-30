# M8 Worker · Werkzeugbasierter Coding-Agent-Loop (Stand 30.10.2026)

**Branch:** `claude/m8-coding-agent-loop-kxrxy2`, Basis `ed408a7`, also unabhängig von EXP-20. Er bleibt offen für die Weiterarbeit.

**Commits:**
- `9d4b17db` Loop
- `f2b0a086` Opt-in-Einbindung
- `aba085c3` Benchmark

**Kein Merge, kein PR, keine APK.** Für `claude/**` läuft kein CI; alle Zahlen sind lokale Host-Läufe.

## Ausgangslage (kartiert)
- **Prüfkette:** Die vorhandene Worker-Pipeline prüft schon streng:
  - Die Ausgangsbasis muss rot sein, und die volle Kette läuft.
  - Eine zweite Worktree rechnet unabhängig nach; Belege sind HMAC-signiert.
  - Allgemeine Modell-Patches laufen nur im isolierten Executor (Docker).
  - Ergebnis ist immer `PATCH_PENDING_OWNER_REVIEW`.
- **Lücke:** Das Modell hatte **keine Werkzeuge**. Es bekam einen festen Kontext und durfte höchstens 3 Einmal-Vorschläge machen (`scopedRepairSession` in `.github/continuum/repair-loop.mjs`).

## Neu
**`.github/continuum/coding-agent-loop.mjs`**, ein `candidateProvider`. Innerhalb eines Sitzungsaufrufs arbeitet ein *Brain* wie ein Entwickler:

| Werkzeug | Wirkung | Grenze |
|---|---|---|
| `list_files`, `search` | `git ls-tree` bzw. `git grep -E` auf dem **Task-Commit** | keine `.git`-Interna, kein Build-Output, keine Geheimnisse (secret, token, keystore, `.properties`), keine owner-data; Treffer- und Längenlimits |
| `read_file` | nummerierte Zeilen; erlaubte Dateien zeigen die eigenen Änderungen | Lesebudget, 300 Zeilen pro Aufruf |
| `edit_file` | exakte, **eindeutige** Ersetzung in einer In-Memory-Overlay | nur `allowedPaths` der Aufgabe; Kontrollzentrale wie bisher gesperrt (`isCodingPath`) |
| `check` | `node --check` der geänderten JS-Dateien, Patchgröße | – |
| `run_tests` | nur auf ausdrücklichen Wunsch; Scratch-Worktree mit `runProfile`, Fehlerorte gehen zurück an das Brain | **dieselbe Ausführungsregel wie die Sitzung**: ohne Executor nur die geprüfte numerische Grammatik |
| `finish` | erzeugt minimale, eindeutige Ganzzeilen-Patches; prüft selbst, dass sie die Änderung reproduzieren | Schema-Tor `validateCodingProposal` |

**Weitere Eigenschaften:**
- **Grenzen:** Schritte (24), Beobachtungen, Lesebudget, ungültige Antworten (3), Deadline, Bindung an den Task-Commit. Jeder Abbruch nutzt einen der Session bekannten Code.
- **Brains:**
  - *Skript* für Tests und Wiederholung.
  - *Chat-Completions*, OpenAI-kompatibel, standardmäßig der lokale llama.cpp-Server `127.0.0.1:8089`. Der Schlüssel geht nur über stdin. Ein entfernter Endpunkt braucht ausdrückliche Besitzerzustimmung (`allowRemote`).
- **Einbindung:** `CONTINUUM_CODING_AGENT_LOOP=1` lässt `runCodingTask` (in `coding-orchestrator.mjs`) diesen Loop verwenden. Ohne die Variable bleibt alles unverändert.
- **Protokoll:** `coding-evidence/<task>/<job>/agent-run-<n>.jsonl` pro Lauf.
- **Benchmark:** `scripts/continuum-agent-benchmark.mjs` mit 6 echten Fehleraufgaben (3 numerisch, 3 allgemein), Modi `--brain oracle|chat`, `--executor`, `--validate`.

## Belege (Host, Node 22, kein Docker-Daemon, kein Modell erreichbar)

| Prüfung | Ergebnis |
|---|---|
| `coding-agent-loop.test.mjs` | 8/8 – darunter End-to-End `repairSession` rot → `VERIFIED`, kein Geheimnis in Beobachtungen, Politik-Parität mit der Sitzung, Fehlerrückmeldung aus `run_tests`, Schlüssel nie in argv |
| benachbarte Suiten `coding-*`, `repair-loop`, `ccc1-*` | 152/152 |
| Benchmark `--validate` (ohne Modell) | 6/6 Aufgaben gültig (Ausgangsbasis rot, Referenz grün) |
| Benchmark `--brain oracle`, ohne Isolation | 3/3 numerische `VERIFIED`, 3 allgemeine `BLOCKED_NO_ISOLATION` |

Diese Zahlen belegen **Mechanik und Messinstrument**, nicht Modellfähigkeit. **Ein echtes Modell lief hier nicht**, weil HuggingFace nicht erreichbar ist.

## Ehrliche Einordnung
- **Ausführung auf dem Telefon:** Die geprüfte numerische Host-Grammatik erlaubt nur Dateien mit einer einzigen Rechenfunktion. Echte Aufgaben brauchen den isolierten Executor. Den gibt es auf dem Fold7 nicht (`CODING_ISOLATION_UNAVAILABLE`). Deshalb **kann das Telefon heute keine allgemeinen Modell-Patches selbst testen**.
- **Fähigkeit:** Wie gut der Agent „codiert“, bestimmt das Brain. Ein 1,5-B-Modell erreichte früher 1/2 auf einer Aufgabe; das kommt nicht an Opus heran. Der Loop macht ein starkes Brain voll nutzbar, ein schwaches aber nicht stark.

## Nächste Schritte (schwierigste zuerst, je mit Messkriterium)
1. **Isolierte Ausführung für das Telefon (M8-Blocker).**
   - **A: CI als Sandbox.** Der Patch geht auf `continuum-agent/<id>`; GitHub Actions führt die Kette isoliert aus; das Ergebnis kommt als Beleg zurück. `repair-publish.mjs` existiert bereits.
   - **B:** Ein Android-`isolatedProcess` für reine JS-Tests.
   - **Nicht annehmbar:** ohne Isolation auf dem Host ausführen.
   - **Kriterium:** Benchmark `gen-*` `VERIFIED` ohne lokale Docker-Pflicht.
2. **Echte Messung des Brains** mit `node scripts/continuum-agent-benchmark.mjs --brain chat --executor <config>` auf einem Docker-Host des Besitzers.
   - **Modelle:** Qwen2.5-Coder 1.5B, 3B und 7B (GGUF, llama.cpp); optional die vorhandene DeepSeek-Lehrer-Option mit Zustimmung.
   - **Vergleich** gegen den alten Einmal-Provider auf denselben Aufgaben, vorab registriert.
   - Die Aufgabenliste auf mindestens 30 Aufgaben erweitern, darunter Kotlin (Kette `android`).
3. **FEATURE- und TEST-Aufgaben** (heute nur Reparatur): eine vom Besitzer freigegebene Phase „Akzeptanztest zuerst“, in `owner-contract-worker.mjs` und `coding-task.mjs`.
4. **Übernahme nach Freigabe:** eine `apply`-Operation in `continuum-typed-coding-bridge.mjs` und `ContinuumTypedCodingBridge` (Commit auf `continuum-agent/<id>`, optional Draft-PR).
5. **Geprüftes Erfahrungs-Replay:** Nur `VERIFIED`-Transkripte dienen als Beispiele für ähnliche Aufgaben. Nutzen erst nach Messung gegen „ohne Replay“; frühere CCC1-Versuche zeigten 6/18 gegen 6/18.

## Sicherheitsgrenzen (unverändert)
- Der Agent schreibt nie in den Baum des Besitzers.
- Er committet, pusht und merged nicht und bekommt keine Shell.
- Er ändert keine Agent-Kontrolldateien, Tests oder Geheimnisse.
- OwnerGate, ToolGate und die CognitiveGenome-Invarianten sind nicht berührt.
