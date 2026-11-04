# Bot-Handoff – Jarvis-Integration auf ChatGPT-Stand

Aktualisiert: 04.11.2026 09:17 CET (aus `date`, Europe/Vienna). Der frühere Zeitstempel „~09:20 MEZ“ lag in der Zukunft und war falsch.
SHAs live per `gh pr view <nr> --json headRefOid`.

## Aktueller Integrationsstand (Jarvis Integration)
- Draft-PR #483 `integration/jarvis-on-chatgpt-untested-20261104` gegen ChatGPTs #478.
- Basis: ChatGPT #478 @ d8fd7f7c (kein neuerer ChatGPT-Branch darauf; #481 und #482 bauen darauf auf, sind aber Welten-Optik/Avatar).
- Hineingemergt: #375 @ b49fb847 (M5-Cap 3.900, B5-Provenienz-Guard, Pool-Fix 6299cbcf).
- AgentActivity: Seitenleiste auf offenem Fold7 in beiden Ausrichtungen (Heinz-Entscheid), untere Leiste nur Cover/Handy; STOP-Anzeige war bei ChatGPT bereits erhalten (refreshSendButtonPresentation, ■ im gestapelten Composer).
- 3 rote Layouttests im Code repariert; RoutineDispatcher-Fix von ChatGPT behalten + RED→GREEN-Test.

# Archiv: ChatGPT #477 – Erstprüfung (04.11.2026 früh)

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

## Kette und Rollen (aus JARVIS-STRATEGIE.md)
Kreativdirektor (Vision, Vergleich mit Videos) → Jarvis Avatar (Kopf/Kugel/Sprechen/Effekte) + Lumo Welten-Optik (Design-System, Layouts, Motion) → Jarvis Kern (Chef-Architekt, besitzt alle Schnittstellen, baut ein) → Jarvis Sicherheit (prüft Video-Treue, Build, Tests, Sicherheit) → Jarvis Integration (führt auf ChatGPTs neuestem Stand zusammen, pflegt diese Datei).
Nebenstrang: Jarvis Gedächtnis (über Kerns Schnittstellen). Chef HAUPTBOT verteilt, entscheidet bei Streit, berichtet Heinz.

## Aktuelle Draft-PRs (live, 04.11.2026 09:07 CET)
| Bot | PR | Branch | SHA | Hinweis |
|---|---|---|---|---|
| Jarvis Integration | #483 | integration/jarvis-on-chatgpt-untested-20261104 | siehe PR | neuer Integrationsstand auf #478 |
| Lumo Welten-Optik | #481 | design/jarvis-cockpit-home-20261104 | 0f4badec | baut auf #478; braucht #479 (gemeldet war ec261342, inzwischen neuer) |
| Jarvis Avatar | #482 | jarvis-grafik/avatar-ref-match | c74b5fa6 | baut auf #478 |
| Jarvis Integration | #480 | integration/continuum-grok1-m5-b5-untested-20261104 | b49fb847 | seit dem #375-Push ohne Diff (= #375) |
| Lumo Welten-Optik (Design-System) | #479 | grok/jarvis-design-system | ba798359 | gemeldet war c617e1ab, inzwischen neuer |
| ChatGPT | #478 | fix/continuum-device-experience-resume-20261104 | d8fd7f7c | Basis von #483 |
| ChatGPT | #477 | fix/continuum-device-experience-20261104 | 337ab019 | |
| ChatGPT | #476 | design/continuum-reference-cockpit-20261104 | aba86cf0 | |
| Jarvis Integration | #375 | integration/continuum-grok1-369-372-20261103 | b49fb847 | grün, in #483 enthalten |

## Plan
1. Reihenfolge: #479 → #481 → Avatar (#482); Verbindungsstelle `CockpitTransitionHook`.
2. Nur Look-Stände integrieren, die Jarvis Sicherheit freigegeben hat.
3. JarvisDesign, JarvisNeon und JarvisMotion aus #479 als einziges Look-System, keine Parallel-Lösungen.
4. Nie nach main mergen, kein Release, kein signierter Build.

## Offene Punkte
- Sicherheits-Review #483 @ 28aa6ae1 (edafa7ab, `docs/reviews/REVIEW-PR483-28aa6ae1.md` auf continuum/m5-integration-repair): Guard-Merge und Cap BESTÄTIGT. Umgesetzt:
  - Lint-Blocker NewApi (AndroidOnDeviceSpeechInput:31, aus ChatGPT-Basis): saubere `SDK_INT >= 31`-Prüfung; unter 31 alter SpeechRecognizer mit EXTRA_PREFER_OFFLINE (LocalSpeechEngine, Test RED→GREEN). Kein @SuppressLint, keine Baseline. Hinweis: unter API 31 kann der System-Dienst trotz PREFER_OFFLINE online erkennen; das Fold7 (API 31+) ist nicht betroffen.
  - Negationsfall: verneinte Owner-Aufforderung („Nicht verschieben…“) → move_file u. a. wird OHNE Rückfrage abgelehnt (ExternalContentActionGuard.ownerNegated, nur das Verb der vorgeschlagenen Aktion zählt). Test aus docs/reviews/evidence übernommen (NegatedMoveReviewTest, RED→GREEN). move_file ohne Verneinung geht weiter zur Owner-Freigabe.
- Jarvis Gedächtnis #371 abgeschlossen auf befdbe89: laut Chef 1842 Tests / 0 Fehler, Ereignisstrom M2.11 nach Kerns Signatur, Recovery M2.12. Status: Integration erst nach Freigabe durch Jarvis Sicherheit. Offene Risiken (GROK2-HANDOFF.md §13): Quarantäne-Dateien werden nie automatisch aufgeräumt (Vorschlag Owner-Aktion „Quarantäne prüfen/löschen“); bei Wiederherstellung aus Sicherung gehen spätere Bestätigungen verloren (nur Widerrufe übernommen); Sicherung nur alle 256 Anhänge erneuert; leeres gültiges Präfix ohne Sicherung → Gedächtnis startet leer (Daten nur in Quarantäne); Report hält nur die letzte Recovery; scheitert die Quarantäne, werden Schreibzugriffe mit IOException abgelehnt bis zum nächsten erfolgreichen Öffnen.
- Jarvis Kern #481 (@ 86263a81) bringt `ChiefWorkerPool.snapshot()` (nur lesend, thread-sicher, unveränderlicher Schnappschuss für CockpitStatus, API in docs/CHIEF-PATTERN.md). `git merge-tree --write-tree 847a5dc8 86263a81` → 0 Konflikte (auch ChiefWorkerPool.kt; ToolOutputCap in report() und runner bleibt erhalten). Nicht gemergt.
- ERLEDIGT: Jarvis Sicherheit hat #375 @ b49fb847 geprüft und S4/B5 (9ec6f3f8) sowie den Race-Fix (6299cbcf) BESTÄTIGT (move_file → Owner-Freigabe). Bericht: `docs/reviews/REVIEW-integration-b49fb847-S4-racefix.md` auf `continuum/m5-integration-repair` @ f6580b25. ChatGPTs zwei neue Guard-Tests wurden auf die B5-API umgestellt (Fall „Nicht verschieben“ → move_file entfällt, Frage-Fall mit delete_file geprüft) – Sicherheit bitte kurz mitprüfen.
- Für Jarvis Kern (ChiefWorkerPool), MITTEL: Der Slot zählt während der Zustellung noch. Ein Re-Submit mit derselben id im Callback wird abgelehnt, und awaitIdle() im Callback wird nie true.
- Für Jarvis Kern, NIEDRIG: Bei einem Error wie OOM gibt es keine Slot-Freigabe und keinen Bericht.
- ENTSCHIEDEN (Heinz via Chef, 04.11.): Offenes Fold7 zeigt in Hoch UND Quer die breite, beschriftete Seitenleiste (ref5/Videos); die untere 74-dp-Arbeitsleiste nur für Cover-Display und Handy. Umgesetzt in AgentActivity + ContinuumWorkAreaNavigationSpec.showBottomBar (Test RED→GREEN).
- Aktionsraster: 10 Aktionen auf breiten Layouts jetzt 5 Spalten (2 volle Reihen) statt 4; Label „Agents“ → „Agenten“.
- Keine Fold7-/Gerätetests.

## Ältere Draft-PRs (Stand früher am 04.11., SHAs nicht neu geprüft)
| Bot | PR | Branch | SHA | Status | Titel |
|---|---|---|---|---|---|
| ChatGPT/Codex | #475 | fix/continuum-status-history-20261104 | cdfb5f75 | Draft | Fix status history clock times; original-signed code41 verified on device screen |
| ChatGPT/Codex | #474 | release/continuum-night-integration-20261103 | 15e90e4a | Draft | Continuum 0.3.39: integrated app and verified original-signed owner update |
| ChatGPT/Codex | #473 | codex/independence-native-failure-review-20261103 | 8a9ab1f4 | Draft | Freeze independent native-failure review and save budget-stop handoff |
| ChatGPT/Codex | #472 | codex/independence-native-closure-20261103 | a4b7b30f | Draft | WIP: native runtime closure contract — implementation not started |
| ChatGPT/Codex | #471 | codex/independence-failure-review-20261103 | beefdf45 | Draft | test(independence): challenge negative service results and resource reconciliation |
| ChatGPT/Codex | #470 | codex/momega1-portrait-rings-20261103 | fe8e95f7 | Draft | MΩ-1: remove portrait halo clipping with unchanged hero geometry |
| ChatGPT/Codex | #469 | codex/momega1-cli-fixtures-20261103 | 022aa434 | Draft | Test-only: update CLI admission expectations for required pinned runtime |
| ChatGPT/Codex | #467 | codex/momega1-gvisor-fixture-20261103 | 29a852a1 | Draft | Test-only: align fake Docker inspect with current isolation schema |
| ChatGPT/Codex | #466 | codex/momega1-layout-final-20261103 | 4a4cb6cb | Draft | MΩ-1: full native phone and Fold layout acceptance |
| ChatGPT/Codex | #456 | codex/continuum-independence-chief-20261103 | 546e0566 | Draft | WIP: Lokaler CCC1-Dienst und lesender MCP — Softwaregates PASS, S01 FAIL |
| ChatGPT/Codex | #455 | codex/genesis-final-native-review-20261103 | 0176702a | Draft | test: verify native Forge/Lint/Echo continuity on known case; retain unknown failure |
| ChatGPT/Codex | #454 | codex/momega1-avatar-effects-review-20261103 | 0b400646 | Draft | MΩ-1 independent review: accept visible avatar, task effects and actual callers |
| ChatGPT/Codex | #445 | codex/momega1-readiness-review-20261103 | f9c0b94c | Draft | Independent camera, readiness and first-welcome review: exact-source acceptance |
| ChatGPT/Codex | #443 | codex/genesis-coding-review-20261103 | e077bb73 | Draft | [Draft] BOT GENESIS independent A0: 0/3 genuine repairs; persistence fix verified |
| ChatGPT/Codex | #439 | codex/bot-genesis-chief-20261103 | 527d43f9 | Draft | WIP: Bot Genesis — private conversations and verified workspace integration |
| ChatGPT/Codex | #438 | codex/genesis-brain-review-20261103 | 2c1d316d | Draft | test(genesis): independently verify private bot conversation isolation and migration |
| ChatGPT/Codex | #435 | codex/momega1-android-plan-review-20261103 | 0704d551 | Draft | MΩ-1: independently accept model-to-draft planning path after RED to GREEN |
| ChatGPT/Codex | #433 | codex/synapse-model-prerequisites-20261103 | 71761204 | Draft | Synapse: replicate approved host model prerequisites with pinned provenance |
| ChatGPT/Codex | #431 | codex/synapse-runtime-audit-20261103 | 0ff9df3c | Draft | test(synapse): independent reviewed runtime and d8 caller integration accepted |
| ChatGPT/Codex | #428 | codex/synapse-ui-review-20261103 | de153623 | Draft | [DRAFT] Synapse: independent persistent bot roster UI review |
| ChatGPT/Codex | #423 | codex/synapse-swarm-chief-20261103 | 4b881f81 | Draft | Synapse: persistente Bot-Profile, Chief-Reparaturen und native Teamansicht — Abnahme offen |
| ChatGPT/Codex | #421 | codex/synapse-p0-audit-20261103 | a50b8e15 | Draft | [DRAFT] Synapse P0: independent MΩ-1 integration audit |
| ChatGPT/Codex | #419 | codex/momega1-android-review-20261103 | f747661a | Draft | MΩ-1: independent Android cancellation review, RED reproduced and repair accepted |
| ChatGPT/Codex | #413 | codex/momega1-qa-20261103 | 365a37e0 | Draft | MΩ-1 independent QA: frozen unknown task suite and host acceptance |
| ChatGPT/Codex | #410 | codex/momega1-chief-20261103 | 250b8a29 | Draft | MΩ-1 / JARVIS: local execution, verified recall and visible avatar integration |
| ChatGPT/Codex | #402 | codex/breakthrough-neural-20261103 | 8123e6df | Draft | [DRAFT] CDX2 slot residual experiment: RAW gain, rejected after regression |
| ChatGPT/Codex | #397 | codex/team-02-memory-omega-20261103 | 3304abce | Draft | memory: durable segmented ledger beyond 256 protected records |
| ChatGPT/Codex | #396 | codex/team-05-quality-omega-20261103 | e934ba31 | Draft | Omega quality: independent host acceptance and unsigned Code40 evidence |
| ChatGPT/Codex | #395 | codex/team-04-android-omega-20261103 | 82a83ec9 | Draft | feat(avatar): optional native head using existing TTS and runtime events |
| ChatGPT/Codex | #394 | codex/team-01-intelligence-omega-20261103 | afade03d | Draft | fix(context): respect denied quantities and owner resignation |
| ChatGPT/Codex | #393 | codex/team-03-ccc1-omega-20261103 | 56f4577a | Draft | fix(ccc1): reject inconsistent receipt counts and incomplete repair verdicts |
| ChatGPT/Codex | #392 | codex/team-chief/omega-20261103 | efe622dd | Draft | [DRAFT] Continuum Ω: verified brain improvements, optional 3D avatar and adaptive cockpit |
| ChatGPT/Codex | #391 | design/continuum-avatar-user-ui-20261103 | a0b19100 | Draft | design(continuum): refined avatar, preserved core sphere and consumer UI handoff |
| ChatGPT/Codex | #390 | fix/continuum-code40-router-race-review-20261103 | b4f61ae9 | Draft | fix(code40): Jarvis colon routing and zero-ms paired benchmark regression |
| ChatGPT/Codex | #389 | coordination/codex-six-agent-team-20261103 | 00851b3d | Draft | docs(codex): bootstrap 5 isolated coding lanes + Chief coordination |
| ChatGPT/Codex | #387 | feature/continuum-alpha-squad-ccc1-entry-20261103 | eed77e56 | Draft | feat(ccc1): executable four-role Alpha Squad entry on existing local repair worker |
| ChatGPT/Codex | #386 | feature/continuum-chief-proof-gated-strategy-ledger-20261103 | ee9b2dfd | Draft | feat(chief): verifier-gated repair experience ledger for proven failures and successes |
| ChatGPT/Codex | #385 | feature/continuum-chief-task-dag-20261103 | 8f641788 | Draft | feat(chief): bounded multi-specialist task DAG, path-conflict serialization and independent QA gate |
| ChatGPT/Codex | #384 | integration/continuum-chief-team-staging-20261103 | 34543f9c | Draft | [DRAFT] Continuum Ω Chief-Team-Staging: 20 Spezialisten, Worker-Abbruch, Routinen und Mailbox verbunden |
| ChatGPT/Codex | #383 | feature/continuum-chief-specialist-delegation-20261103 | 07be9793 | Draft | feat(chief): owner-approved registry and autonomous delegation across up to 20 specialists |
| ChatGPT/Codex | #382 | repair/continuum-chief-routines-mailbox-20261103 | 503402e9 | Draft | fix(chief): idempotent routine dispatch and globally bounded agent mailbox |
| ChatGPT/Codex | #381 | repair/continuum-chief-cancellation-scope-20261103 | 73a4ebb5 | Draft | fix(chief): cooperative cancellation, bounded worker threads and truthful tool-scope blockers |
| ChatGPT/Codex | #380 | integration/continuum-omega-sol61-20261103 | b85654eb | Draft | WIP: Continuum Ω – kritische Reviewfehler und bestehende Chief-Pfade integrieren |
| Jarvis Kern (HAUPT) | #379 | continuum/haupt-chief-pattern-20261103 | ed4a9d3b | Draft | [DRAFT/NICHT MERGEN] Chief-Muster 1–3: TurnLifecycle, ChiefWorkerPool, typisiertes Tool-Schema |
| Jarvis Avatar | #378 | jarvis-grafik/avatar-live | b95d08ca | Draft | WIP: Jarvis-Avatar live (Holo AAA v1) – Zwischenstand |
| Jarvis Integration (Tests) | #377 | grok/chief-e2e-tests | 72c77e1e | Draft | test(chief): Ende-zu-Ende-Tests für das Chief-Bot-Muster (11 pass, 6 pending) |
| Jarvis Integration | #375 | integration/continuum-grok1-369-372-20261103 | 68a34bd9 | Draft | [DRAFT/NICHT MERGEN] Integration #369–#373 + #371-H1/H2 – JUnit 1856/0, volle QA |
| Jarvis Integration | #374 | continuum/grok1-integration-20261103 | 419b8576 | Draft | GROK 1 Integration: Fold7-Kontext-Retention, Negation de/en, #371+M2 integriert, volle QA |
| Continuum (M1/M3) | #373 | continuum/m3-grok-selbstrekonstruktion-20261103 | 26162442 | Draft | Continuum M3 (Grok): Review-#370-Fixes, attestierter CCC1-RED→GREEN-Verifier, Offline-Eval, Selbstkorrektur, deutsches Retrieval |
| Jarvis Sicherheit | #372 | continuum/m5-integration-repair | aff0b852 | Draft | fix(continuum-m5): ToolGate-Journal-Retention, Sprach-Barge-in/Halbduplex, Diktat-Entwurf (RED→GREEN, volle JUnit grün) |
| Jarvis Gedächtnis | #371 | continuum/m2-cognitive-memory | a0a4a736 | Draft | feat(continuum-m2): Gedächtnis-Kern – Hypothesen-Gate, bitemporales Zeitgedächtnis, Spezialist im EWM2-Gehirn (RED→GREEN, volle JUnit grün) |
| Jarvis Gedächtnis | #370 | continuum/m2-grok-context-state-20261103 | ea785432 | Draft | feat(continuum-m2): Kontext über Werkzeugantworten, Aussagen-Zeitachse, kleinerer Prompt, CCC1-Quittung (RED→GREEN, 1772/0) |
| Continuum (M1/M3) | #369 | continuum/m1-grok-multiturn-routing-20261103 | 7316b1f5 | Draft | fix(continuum-m1): Mehrturn-Aufgabenrahmen, Geräteuhr, negationsfestes Werkzeugrouting (RED→GREEN, volle JUnit grün) |
| ChatGPT/Codex | #368 | research/continuum-native-session-lifecycle-20261103 | 85602f73 | Draft | perf(litertlm): reuse only the pristine load-time conversation on first owner turn |
| ChatGPT/Codex | #367 | research/continuum-omega-task-scoped-temporal-20261103 | ff3059e2 | Draft | research(omega): bounded temporal owner-state + clause-scoped storage negation |

---

## Menüführung (Jarvis Integration) – Branch `design/jarvis-menu-navigation-20261104`
Stand: 04.11.2026 09:23 CET (Europe/Vienna). WIP, Draft-PR gegen Kerns `design/jarvis-cockpit-home-20261104` (#481).
- Basis: #481 @ 02097c48. #481 enthält #479 nur als Datei-Stand **ba798359** (nicht @35a68f49, kein Git-Vorfahre).
  Auf diesem Branch: Jarvis*-Dateien aus #479 @35a68f49 übernommen (JarvisMotion.Depth, Bloom-API) – identischer Inhalt, damit Kern beim Nachziehen keine Konflikte hat.
- Auftrag: Kerns JarvisNavigator/CockpitActionHandler implementieren, Fold7-Seitenleiste nach ref5 (beide Ausrichtungen, ~20 %), Muster B/A über CockpitAnimationTriggers.areaChanged, Kamera-Drift (Z-Dolly 1,00→1,03), Szene 3b (Phone-Hochformat, Kopf nur über JarvisAvatarSlot), Aktionsleiste.
- Nicht mehr meins: Widgets-Einflug → Jarvis Gedächtnis (Bildbeschrieb Abschnitt 6).
- Offen: siehe Draft-PR-Beschreibung (wird mit jedem Push aktualisiert).

## Neue Bots (Bildbeschrieb Abschnitt 7, 09:25)
- **Jarvis Lückenfüller:** schließt die Lücken, die der Kreativdirektor findet. Erstes Stück: Video 3 – Alarm H, Warp-Flug, Ganzkörper-Avatar in der Energiekugel (Kopf über Avatar bzw. JarvisAvatarSlot).
- **Jarvis Mathematik:** Animations-Mathematik als getestete Bibliothek (Kurven, Federn mit Overshoot, Z-Kamerafahrten, Stagger, Zeitachse) und Avatar-Mimik (Viseme/Blendshapes, Lippensync aus Audio, Blinzeln, Atmen). Avatar zeichnet, Mathematik rechnet.
- **Grundregel für alle:** Branches offen halten, nie Code oder Branches löschen, kein Force-Push – alles bleibt für ChatGPT auf GitHub einsehbar.
