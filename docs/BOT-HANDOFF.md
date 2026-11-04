# ChatGPT #477 – Stand (geprüft 04.11.2026, ~09:20 MEZ, Jarvis Integration)

Nur gelesen/gebaut. Nichts gepusht, kommentiert oder gemergt.

## Branches (ChatGPT, alle Autor Ullmann27/Codex)
| PR | Branch | SHA | Inhalt |
|---|---|---|---|
| #476 | design/continuum-reference-cockpit-20261104 | aba86cf0 | Referenz-Cockpit-Kandidat + Code41-Doku (Basis für #477/#478) |
| #477 | fix/continuum-device-experience-20261104 | 337ab019 | = #476 + 1 Doku-Commit (PLAN.md), **kein Code** |
| #478 | fix/continuum-device-experience-resume-20261104 | d8fd7f7c | = #477 + 5 Commits: Avatar/Cockpit-Quellen, Bot-Speicher, Live-Fähigkeiten, Sprache (119 Dateien ggü. #476) |

Linie ist linear: #476 ⊂ #477 ⊂ #478.

## Wichtig: #375 ist bereits enthalten
merge-base aller drei Branches mit #375 = **68a34bd9**. ChatGPT hat auf unserer Integration aufgebaut.
`git merge-tree` → **0 Konflikte** (für alle drei). Ein Merge wäre ein Fast-Forward von #375.
Sie haben aber unsere Dateien weiterverändert (68a34bd9..d8fd7f7c):
- ChiefBrainWiring.kt (+17/-…: RoutineDispatcher gibt Claims bei Pool-Ablehnung/ungültigem Brief frei – sinnvoller Fix, d62ff460/da584f5a)
- ChiefWorkerPool.kt (~132 Zeilen), ChiefJobs.kt (~79), AgentActivity.kt (~1233 Zeilen!), ContinuumTheme.kt (~31)
- 68a34bd9..aba86cf0: 5450 Dateien / 702 Nicht-Merge-Commits, großteils docs/ und .github/continuum (Merge 09dced7a „integrate reviewed app and independent bot service“).
→ Inhaltliche Überschneidung mit unserem Kern ist groß (AgentActivity, Pool, Jobs) – textlich konfliktfrei, aber nicht von uns reviewt.

## Build/Test (nur #478 @ d8fd7f7c gebaut; #476/#477 nicht separat gebaut)
`./gradlew assembleDebug testDebugUnitTest` (JDK 17, Host):
- assembleDebug: **OK** (app-debug.apk, unsigniert/Debug)
- Unit-Tests (JUnit-XML): **2213 Tests, 3 Fehler, 0 Errors, 19 skipped** → ROT
  - ContinuumAdaptiveCockpitLayoutTest.everyHeaderAndActionHasUnscaledFortyEightDpTouchArea (47.999992 statt 48 dp – Float-Rundung)
  - ContinuumAdaptiveCockpitLayoutTest.allEightControlsKeepEqualBoundsAndBalancedRows (erwartet 824, war 458)
  - ContinuumCoreMirrorTest.actionTextUsesReadableDestinationNamesOnBothSides
- Zum Vergleich #375: 1951 Tests / 0 Fehler / 24 skipped.
- #476 und #477: **ungeprüft** (Code von #477 = #476; #478 ist Obermenge).
- uiTest/Gerätetests, Fold7: **ungeprüft**.

## Übernahme-Empfehlung
- **Kern (Cockpit):** ChiefBrainWiring-Fix (RoutineDispatcher release) direkt übernehmbar, grün in der Suite. ChiefBotProfileStore + Tests (Bot-Roster-Speicherfehler), OwnerCapabilityReply, LocalSpeechSession/Policy: Tests dazu grün → übernehmbar nach Review. AgentActivity-Änderungen (1233 Zeilen) NICHT blind übernehmen – erst Review.
- **Avatar (Kopf):** avatar3d/FilamentAvatarRenderer.kt, AvatarGlbContract.kt, assets/avatar3d/continuum-avatar.glb, ContinuumHologramView/Renderer, tools/avatar-3d/*.py; Avatar3dContractTest + ContinuumHologramPlexusTest grün. Ähnlichkeit laut Auftrag noch ungenügend; GLB-Lizenz (LICENSE.ASSETS.md/NOTICE.json) prüfen.
- **Welten-Optik (Theme/Layouts):** ui/ContinuumTheme.kt, ContinuumReferenceLayout/Elements, ContinuumWideElements, ContinuumHomeLayout/Screen/View, ContinuumSurfaces. **Achtung: hier sitzen die 3 roten Tests** (AdaptiveCockpitLayout, CoreMirror) → nicht als grün übernehmen; ContinuumReferenceCockpitLayoutTest/HomeLayoutTest grün.

## Offene Risiken
- #478 ist rot (3 UI-Layout-Tests), PR-Titel „WIP“.
- Sehr große, ungereviewte Änderungen an AgentActivity/ChiefWorkerPool/ChiefJobs auf Basis unseres #375.
- Wenn wir #375 weiterentwickeln, entstehen Konflikte mit ChatGPT erst dann – daher besser auf d8fd7f7c aufsetzen statt parallel auf 68a34bd9.
- Keine Geräte-/Fold7-/Signatur-Prüfung; kein signierter Build gemacht.

---

## Bot-Branches und Draft-PRs (Stand 2026-11-04, gh pr list --state open --limit 60)

Zuordnung je Bot (Jarvis Integration, Kern, Avatar, Gedächtnis, Sicherheit, Welten-Optik, ChatGPT #476–#479) über Branchnamen/Titel.

| PR | Branch | SHA | Status | Titel |
|---|---|---|---|---|
| #480 | integration/continuum-grok1-m5-b5-untested-20261104 | aaafb183 | Draft | WIP (untested): M5 tool-output cap 3900 + B5 S4 fix, B5 tests re-enabled |
| #479 | grok/jarvis-design-system | b864c88f | Draft | WIP: Jarvis Design-System (baut auf #477 auf) |
| #478 | fix/continuum-device-experience-resume-20261104 | d8fd7f7c | Draft | WIP: reference avatar, cockpit and live work animation integration |
| #477 | fix/continuum-device-experience-20261104 | 337ab019 | Draft | WIP: repair owner bot access, finish reference cockpit and expose live capabilities |
| #476 | design/continuum-reference-cockpit-20261104 | aba86cf0 | Draft | WIP: implement owner cyan/violet JARVIS reference cockpit |
| #475 | fix/continuum-status-history-20261104 | cdfb5f75 | Draft | Fix status history clock times; original-signed code41 verified on device screen |
| #474 | release/continuum-night-integration-20261103 | 15e90e4a | Draft | Continuum 0.3.39: integrated app and verified original-signed owner update |
| #473 | codex/independence-native-failure-review-20261103 | 8a9ab1f4 | Draft | Freeze independent native-failure review and save budget-stop handoff |
| #472 | codex/independence-native-closure-20261103 | a4b7b30f | Draft | WIP: native runtime closure contract — implementation not started |
| #471 | codex/independence-failure-review-20261103 | beefdf45 | Draft | test(independence): challenge negative service results and resource reconciliation |
| #470 | codex/momega1-portrait-rings-20261103 | fe8e95f7 | Draft | MΩ-1: remove portrait halo clipping with unchanged hero geometry |
| #469 | codex/momega1-cli-fixtures-20261103 | 022aa434 | Draft | Test-only: update CLI admission expectations for required pinned runtime |
| #467 | codex/momega1-gvisor-fixture-20261103 | 29a852a1 | Draft | Test-only: align fake Docker inspect with current isolation schema |
| #466 | codex/momega1-layout-final-20261103 | 4a4cb6cb | Draft | MΩ-1: full native phone and Fold layout acceptance |
| #456 | codex/continuum-independence-chief-20261103 | 546e0566 | Draft | WIP: Lokaler CCC1-Dienst und lesender MCP — Softwaregates PASS, S01 FAIL |
| #455 | codex/genesis-final-native-review-20261103 | 0176702a | Draft | test: verify native Forge/Lint/Echo continuity on known case; retain unknown failure |
| #454 | codex/momega1-avatar-effects-review-20261103 | 0b400646 | Draft | MΩ-1 independent review: accept visible avatar, task effects and actual callers |
| #445 | codex/momega1-readiness-review-20261103 | f9c0b94c | Draft | Independent camera, readiness and first-welcome review: exact-source acceptance |
| #443 | codex/genesis-coding-review-20261103 | e077bb73 | Draft | [Draft] BOT GENESIS independent A0: 0/3 genuine repairs; persistence fix verified |
| #439 | codex/bot-genesis-chief-20261103 | 527d43f9 | Draft | WIP: Bot Genesis — private conversations and verified workspace integration |
| #438 | codex/genesis-brain-review-20261103 | 2c1d316d | Draft | test(genesis): independently verify private bot conversation isolation and migration |
| #435 | codex/momega1-android-plan-review-20261103 | 0704d551 | Draft | MΩ-1: independently accept model-to-draft planning path after RED to GREEN |
| #433 | codex/synapse-model-prerequisites-20261103 | 71761204 | Draft | Synapse: replicate approved host model prerequisites with pinned provenance |
| #431 | codex/synapse-runtime-audit-20261103 | 0ff9df3c | Draft | test(synapse): independent reviewed runtime and d8 caller integration accepted |
| #428 | codex/synapse-ui-review-20261103 | de153623 | Draft | [DRAFT] Synapse: independent persistent bot roster UI review |
| #423 | codex/synapse-swarm-chief-20261103 | 4b881f81 | Draft | Synapse: persistente Bot-Profile, Chief-Reparaturen und native Teamansicht — Abnahme offen |
| #421 | codex/synapse-p0-audit-20261103 | a50b8e15 | Draft | [DRAFT] Synapse P0: independent MΩ-1 integration audit |
| #419 | codex/momega1-android-review-20261103 | f747661a | Draft | MΩ-1: independent Android cancellation review, RED reproduced and repair accepted |
| #413 | codex/momega1-qa-20261103 | 365a37e0 | Draft | MΩ-1 independent QA: frozen unknown task suite and host acceptance |
| #410 | codex/momega1-chief-20261103 | 250b8a29 | Draft | MΩ-1 / JARVIS: local execution, verified recall and visible avatar integration |
| #402 | codex/breakthrough-neural-20261103 | 8123e6df | Draft | [DRAFT] CDX2 slot residual experiment: RAW gain, rejected after regression |
| #397 | codex/team-02-memory-omega-20261103 | 3304abce | Draft | memory: durable segmented ledger beyond 256 protected records |
| #396 | codex/team-05-quality-omega-20261103 | e934ba31 | Draft | Omega quality: independent host acceptance and unsigned Code40 evidence |
| #395 | codex/team-04-android-omega-20261103 | 82a83ec9 | Draft | feat(avatar): optional native head using existing TTS and runtime events |
| #394 | codex/team-01-intelligence-omega-20261103 | afade03d | Draft | fix(context): respect denied quantities and owner resignation |
| #393 | codex/team-03-ccc1-omega-20261103 | 56f4577a | Draft | fix(ccc1): reject inconsistent receipt counts and incomplete repair verdicts |
| #392 | codex/team-chief/omega-20261103 | efe622dd | Draft | [DRAFT] Continuum Ω: verified brain improvements, optional 3D avatar and adaptive cockpit |
| #391 | design/continuum-avatar-user-ui-20261103 | a0b19100 | Draft | design(continuum): refined avatar, preserved core sphere and consumer UI handoff |
| #390 | fix/continuum-code40-router-race-review-20261103 | b4f61ae9 | Draft | fix(code40): Jarvis colon routing and zero-ms paired benchmark regression |
| #389 | coordination/codex-six-agent-team-20261103 | 00851b3d | Draft | docs(codex): bootstrap 5 isolated coding lanes + Chief coordination |
| #387 | feature/continuum-alpha-squad-ccc1-entry-20261103 | eed77e56 | Draft | feat(ccc1): executable four-role Alpha Squad entry on existing local repair worker |
| #386 | feature/continuum-chief-proof-gated-strategy-ledger-20261103 | ee9b2dfd | Draft | feat(chief): verifier-gated repair experience ledger for proven failures and successes |
| #385 | feature/continuum-chief-task-dag-20261103 | 8f641788 | Draft | feat(chief): bounded multi-specialist task DAG, path-conflict serialization and independent QA gate |
| #384 | integration/continuum-chief-team-staging-20261103 | 34543f9c | Draft | [DRAFT] Continuum Ω Chief-Team-Staging: 20 Spezialisten, Worker-Abbruch, Routinen und Mailbox verbunden |
| #383 | feature/continuum-chief-specialist-delegation-20261103 | 07be9793 | Draft | feat(chief): owner-approved registry and autonomous delegation across up to 20 specialists |
| #382 | repair/continuum-chief-routines-mailbox-20261103 | 503402e9 | Draft | fix(chief): idempotent routine dispatch and globally bounded agent mailbox |
| #381 | repair/continuum-chief-cancellation-scope-20261103 | 73a4ebb5 | Draft | fix(chief): cooperative cancellation, bounded worker threads and truthful tool-scope blockers |
| #380 | integration/continuum-omega-sol61-20261103 | b85654eb | Draft | WIP: Continuum Ω – kritische Reviewfehler und bestehende Chief-Pfade integrieren |
| #379 | continuum/haupt-chief-pattern-20261103 | ed4a9d3b | Draft | [DRAFT/NICHT MERGEN] Chief-Muster 1–3: TurnLifecycle, ChiefWorkerPool, typisiertes Tool-Schema |
| #378 | jarvis-grafik/avatar-live | b95d08ca | Draft | WIP: Jarvis-Avatar live (Holo AAA v1) – Zwischenstand |
| #377 | grok/chief-e2e-tests | 72c77e1e | Draft | test(chief): Ende-zu-Ende-Tests für das Chief-Bot-Muster (11 pass, 6 pending) |
| #375 | integration/continuum-grok1-369-372-20261103 | 68a34bd9 | Draft | [DRAFT/NICHT MERGEN] Integration #369–#373 + #371-H1/H2 – JUnit 1856/0, volle QA |
| #374 | continuum/grok1-integration-20261103 | 419b8576 | Draft | GROK 1 Integration: Fold7-Kontext-Retention, Negation de/en, #371+M2 integriert, volle QA |
| #373 | continuum/m3-grok-selbstrekonstruktion-20261103 | 26162442 | Draft | Continuum M3 (Grok): Review-#370-Fixes, attestierter CCC1-RED→GREEN-Verifier, Offline-Eval, Selbstkorrektur, deutsches Retrieval |
| #372 | continuum/m5-integration-repair | aff0b852 | Draft | fix(continuum-m5): ToolGate-Journal-Retention, Sprach-Barge-in/Halbduplex, Diktat-Entwurf (RED→GREEN, volle JUnit grün) |
| #371 | continuum/m2-cognitive-memory | a0a4a736 | Draft | feat(continuum-m2): Gedächtnis-Kern – Hypothesen-Gate, bitemporales Zeitgedächtnis, Spezialist im EWM2-Gehirn (RED→GREEN, volle JUnit grün) |
| #370 | continuum/m2-grok-context-state-20261103 | ea785432 | Draft | feat(continuum-m2): Kontext über Werkzeugantworten, Aussagen-Zeitachse, kleinerer Prompt, CCC1-Quittung (RED→GREEN, 1772/0) |
| #369 | continuum/m1-grok-multiturn-routing-20261103 | 7316b1f5 | Draft | fix(continuum-m1): Mehrturn-Aufgabenrahmen, Geräteuhr, negationsfestes Werkzeugrouting (RED→GREEN, volle JUnit grün) |
| #368 | research/continuum-native-session-lifecycle-20261103 | 85602f73 | Draft | perf(litertlm): reuse only the pristine load-time conversation on first owner turn |
| #367 | research/continuum-omega-task-scoped-temporal-20261103 | ff3059e2 | Draft | research(omega): bounded temporal owner-state + clause-scoped storage negation |

Jarvis Integration aktuell: #375 `integration/continuum-grok1-369-372-20261103` @ 68a34bd9 (grün), #480 `integration/continuum-grok1-m5-b5-untested-20261104` (M5-Cap + B5-Fix, 1955/0/17 grün, auch auf #375 gepusht).

## Plan
1. M5/B5 auf #375-Basis (#480) voll testen; bei 0 Fehlern normaler Push auf #375.
2. Danach neuer Integrationsbranch auf ChatGPTs neuestem #478-Stand, dazu:
   - Portrait-Fold-Handling + STOP-Anzeige am Senden-Knopf aus unserem AgentActivity erhalten,
   - die 3 roten Layouttests reparieren (Code, nicht Tests),
   - JarvisDesign/Neon/Motion aus #479.
3. Nur nach Prüfung durch Jarvis Sicherheit integrieren; nie nach main mergen.
