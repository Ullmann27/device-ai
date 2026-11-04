# Integrations-Check – Jarvis Integration (03.11.2026)

Integrationszweig: `integration/continuum-grok1-369-372-20261103` (Draft-PR #375). Nie nach main, kein Release, kein signierter Build.

## Verbunden (Merges ohne Rebase/Force)
| Quelle | SHA | Merge |
|---|---|---|
| #371 m2-cognitive-memory | 6ec1d5b5 | abaf6f49 |
| #372 m5-integration-repair (S4 Injection-Schutz) | b598dd1f | 51f63df8 |
| #379 Jarvis Kern chief-pattern | ed4a9d3b | 65711987 |
| Lumo agent-loop-device-control | 0d3de23c | ff3229ca |
| Lumo Welten-Optik grok/e2e-erster-denkdurchlauf | 189fca80 | a9412a57 |
| #377 grok/chief-e2e-tests | 72c77e1e | 8cad9169 |
| #376 Routinen + Agent-Mailbox | b56bb1e3 | b6b249ac |
| #378 jarvis-grafik/avatar-live (nur Doku-Commit) | b95d08ca | 028b1f93 |
| M2-WIP `ContinuumThinkingMemory` (aus /workspace/continuum-m2-wip, Test grün) | – | 08cfb2cb |

Konflikte: nur `TEST_RESULTS.md` (eigene Version behalten, neu geschrieben). Lumo/HAUPT teilen Basis 4a4a1541, `ChiefWorkerPool.kt` automatisch zusammengeführt.

## Verdrahtung (`chief/ChiefBrainWiring.kt`, nur Adapter)
- `LayersMemoryPort` (Gedächtnis → Kern), wie in docs/CHIEF-PATTERN.md; `shared().facts()` = `activeOwnerFacts()` (nur OWNER_CONFIRMED + confirmable), Suche filtert REVOKED, Hypothesen bleiben `confirmed=false` → `UNVERIFIED`.
- `ChiefTurnBridge`: Orchestrator-Events → `TurnLifecycle`-Meilensteine.
- `UnattendedApproval` + `ChiefWiring.unattendedPool`: Routinen laufen mit Tools nur im Job-Scope (`WorkerScope`), nicht-lesende Aktionen werden nie automatisch freigegeben → BLOCKED.
- `RoutineDispatcher`: `RoutineBook` (#376) → `ChiefWorkerPool` (#379) → nur Neues als INFO in `AgentMailbox`, BLOCKED als URGENT (Weck-Hook).

Fluss: Ziel (`WorkerBrief`) → Plan/Handeln (`ChiefJobs.agentJob` = `BoundedAgentOrchestrator`) → Erinnern (`LayersMemoryPort` → `ChiefMemory.recall`) → Freigabe (`ToolGate` + S4 `ExternalContentActionGuard` im Orchestrator + ApprovalProvider) → Prüfen (`AgentEvaluator`, `evaluationConfirmsOutcome=false`) → Lernen (`ExperienceStore`, Lehren nur als Hypothese/UNVERIFIED) → Bericht (`WorkerReport`) → Routine/Mailbox.

## Bewusst NICHT verbunden
- `TurnLifecycle`/`ChiefWorkerPool` in `AgentActivity` (App-Pfad Fold7): braucht Gerätetest (DEVICE_REQUIRED), HAUPT hatte das begonnen, nicht committet.
- `DeviceControl` (Lumo): WIP, ungetestet, absichtlich nicht global registriert.
- `ContinuumThinkingMemory`: liegt im Baum mit Test, aber noch nicht in `ChiefJobs` eingehängt.
- #380–#387 (Chief-Team-Staging, DAG, Delegation, Cancellation, Routines-Mailbox-Fix): parallele Alternativ-Integration derselben Dateien, nicht geprüft → separat entscheiden.

## Was mich stört / Fehler
- **B5 (Fehler):** S4 `ExternalContentActionGuard.ownerRequested` ist eine reine Wortlisten-Suche. `move_file` wird nach Beobachtungen hart verweigert, wenn das Verb („verschieb/move“) nicht wörtlich im Ziel steht (z. B. „Schaffe Platz im Download-Ordner“). Außerdem negationsblind („ohne etwas zu loeschen“ zählt als Lösch-Absicht) und zu breit („teil“). 7 Schritte von `FirstThinkingRunE2ETest` sind deshalb `@Ignore("S4 zu streng, B5")`. Korrektur gehört Jarvis Sicherheit (#372).
- Injection-Tests (#379 `ToolSchemaSafetyTest`, #377 `s3c`) erwarteten „Owner wird gefragt“; S4 verweigert jetzt vorher. Angepasst auf strengere Invariante (keine Ausführung, kein Owner-Approved, nichts im Fake-Dateisystem gelöscht).
- `ChiefJobs` nutzt eine eigene `DeliberationCancellation`, die nicht mit `future.cancel(true)` gekoppelt ist: nach TIMED_OUT kann der Job-Thread weiterlaufen (#381 adressiert das, nicht integriert).
- `RoutineBook`/`AgentMailbox` nur im Speicher, kein JobScheduler-Takt – `tick()` muss noch jemand aufrufen.
- `OwnerStatementModality` liefert für „Vielleicht …“ jetzt UNCERTAIN statt HYPOTHETICAL (Test auf Invariante „nie bestätigter Fakt“ umgestellt).

## Tests (tatsächlich gelaufen)
- `assembleDebug` + `testDebugUnitTest` auf 08cfb2cb: BUILD SUCCESSFUL, 1951 Tests, 0 Fehler, 17 übersprungen.
- `testDebugUnitTest` auf finalem Stand (nur Test-Änderungen seit 08cfb2cb): 1951 Tests, 0 Fehler, 24 übersprungen (17 + 7 B5). Kein Fold7-Test.

## M5-Cap + B5-Fix (2026-11-04, Basis 68a34bd9)
- 9d85b362 M5-Patch (ChiefWorkerPool Tool-Output-Cap 3.900) aus aff0b852; 9aaec2c2 ToolOutputCap.kt + Test aus aff0b852 (Patch hing davon ab).
- aeec8a7e Cherry-pick 0c2d5a16 (S4 nach Taint/Aktionsrisiko statt Wortlisten; Herkunft origin/continuum/m5-integration-repair).
- aaafb183 7 B5-Schritte in FirstThinkingRunE2ETest ohne @Ignore.
- 9ec6f3f8 S4-Guard-Unittest auf neue API (aus 8c31ecaa, Sicherheits-RED-Test zu 0c2d5a16).
- 6299cbcf Fix ChiefWorkerPool: Report vor Slot-Freigabe (awaitIdle-Race, ChiefBrainWiringTest unter Last rot).
- Voller Lauf assembleDebug testDebugUnitTest: 1955 Tests, 0 Fehler, 17 skipped (vorher 24), assembleDebug OK.
