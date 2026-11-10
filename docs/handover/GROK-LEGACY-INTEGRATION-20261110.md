# GROK Legacy-Integration – Inventur und Anschluss 10.11.2026

BOT 01 · Orphan Hunter + Integration · Auftrag t135u / Gesetz Nr. 1 (nichts liegen lassen).

- Geprüft: 10.11.2026, 14:03–14:20 Europe/Vienna
- Integrationsstand (Basis): `code52/final-integrator-cinema-20261110` **#698** HEAD `a0a69263cdc3c87ed1b728c58188ee9529a78eb4`
- Arbeitsbranch: `code52/grok-bot01-legacy-integration-20261110` (nicht auf #698 gepusht)
- Vorarbeit: `docs/handover/GROK-PHASE-A-NO-ORPHANS-20261110.md` (PR #705), erweitert von code52/* auf **alle** offenen PRs und Branch-Präfixe

## 1. Inventurzahlen

| Menge | Zahl | Beleg |
|---|---:|---|
| Remote-Branches (`gh api …/git/matching-refs/heads`) | 767 | `/tmp/bot01-all-branches.txt` |
| PRs gesamt (`gh pr list --state all --limit 1000`) | 661 | 526 open / 128 merged / 7 closed-ungemerged |
| Offene PRs gegen #698 verglichen (GitHub Compare API) | 526 | `/tmp/bot01-pr-compare-698.json` |
| davon ahead=0 (keine einzigartigen Commits vs. #698) | 235 | Klasse (a) per SHA-Ancestry |
| davon ahead>0 (SHA nicht in #698) | 291 | danach inhaltlich nach Dateinamen geprüft |
| Compare-Fehler | 0 | — |
| Unique „added“-Dateien, die auf #698 bereits existieren | 1288 | Inhalt oft schon portiert, andere SHAs |
| Unique „added“-Dateien, die auf #698 fehlen | 4761 | ganz überwiegend docs/evidence alter Parallelhistorien |
| Unique fehlende **MAIN**-Quellen (`app/src/main/`) | 54 | echte Produktkandidaten, unten klassifiziert |

Klassen: **(a)** bereits enthalten (SHA oder inhaltlich) · **(b)** echte fehlende Neuerung · **(c)** durch Neueres ersetzt · **(d)** kaputt aber wertvoll · **(e)** gefährlich/verboten.

## 2. Was dieser Branch anschließt

Basis `a0a69263`. Zwei Cherry-Picks aus #703 (Herkunftsangabe: Original-SHAs):

| Herkunft | Neuer Commit | Inhalt |
|---|---|---|
| `9b8aca62575702875d79164b4aa5820a07802117` (#703) | `ce94c68f4` | Expert-Modus an `ToolRegistry`-Consent; genaue Shell-Zeile; doppelte Bestätigung bei DESTRUCTIVE; `*.p12` in `.gitignore` |
| `753516eb80019c812f9d43bb7c594c5f9577e423` (#703) | `34e7e9e09` | Internet-Anzeige „Dauerhaft erlauben (Standard)“ für ALWAYS_ON; Verhalten unverändert ALWAYS_ON |

Verdrahtung geprüft: `MainActivity` nutzt `ExpertModeConsent.*`; `CommandInterpreter` bindet Risiken über `ToolRegistry.risk(...)`; `AgentActivity` nutzt `InternetAccessModeLabels.dialogOptions`.

Dieselben Patches stecken bereits im Bot-16-RC **#706** (Original-SHAs). Integrator: nicht doppelt mergen, sondern eine der beiden Quellen nehmen; Patch-Inhalt ist gleich.

**Nicht** von Bot 01 übernommen, obwohl Klasse (b):

- **#699 / #701 / #702** — ändern `Airbus2100CockpitOrchestrator.kt` (Bot-04-Sperre) und sind in #706 schon auf #698 gesetzt.
- **ui-fixes** `code52/final-integrator-ui-fixes-20261110` Commits `fbe35a7de` (dialogTop/Live-Dialog nach oben) und `3ba8fc4f6` — Layout, **für Bot 07**.
- **#598** `JarvisReferenceInputTest.kt` / Reference-Home-Input in `JarvisShellLayout.kt` — **für Bot 07**.
- **#487** restliche video3-Szenen (Alarm/Greeting/Calm/Pills) — **für Bot 04/07**.
- **#666/#659** nur fehlender Test `AvatarReadingContinuityTest.kt`; Produktdateien da. Grafik-Weiterarbeit **Bot 06/03**.

## 3. Heutige Cinema-Linie (Live, 14:20 Europe/Vienna)

| PR | HEAD-Thema | vs #698 | Klasse | Begründung |
|---|---|---|---|---|
| #706 | [Code52 Cinema][RELEASE CANDIDATE] 0.3.51 / versionCode 52: #698 + #70 | ahead=19 behind=0 | (b) | Bot 16 Release-Kandidat: #698 + #699/#701 + #702 + #703. Fast-forward von #698 (ahead=19, behind=0). Nicht von Bot 01 erneut gemergt, um Parallel-Arbeit nicht zu verdoppeln. Enthält u. a. Airbus2100CockpitOrchestrator (#701) — Datei von Bot 04. |
| #705 | [Code52][BOT01/BOT02][Draft] Phase-A No-Orphans-Matrix und Integration | ahead=13 behind=3 | (a) | Vorherige Phase-A-Analyse (nur docs/handover/GROK-PHASE-A-NO-ORPHANS-20261110.md). Kein Produktcode. |
| #704 | [Code52 Cinema][SIGNER PREP] Update #700 contract test to Cinema QA bi | ahead=12 behind=5613 | (e) | Signer-Vertragstest auf eigener Signier-Historie (behind=5613, unrelated zum Produktpfad). Nicht in die Cinema-Produktlinie mergen. |
| #703 | [Code52 Cinema][P1] Bind expert-mode planner to ToolRegistry consent a | ahead=14 behind=3 | (b) | IMPLEMENTIERT auf diesem Branch: Cherry-Picks 9b8aca625757 → ce94c68f4 (ExpertModeConsent an ToolRegistry) und 753516eb8001 → 34e7e9e09 (Internet-Anzeige ALWAYS_ON als Standard). Dieselben Patches stecken auch in #706. |
| #702 | fix(cinema): require #701 DocumentLearned Unit isolation in Node contr | ahead=15 behind=3 | (b) | Node-Verträge für #701 DocumentLearned-Isolation. Hängt an #699/#701 (Airbus2100CockpitOrchestrator). Für Bot 15/16 bzw. Integrator; Bot 01 fasst die Orchestrator-Datei nicht an (Bot-04-Sperre). |
| #700 | [Code52 Cinema][SIGNER PREP] Restrict protected original signer to Cin | ahead=11 behind=5613 | (e) | Original-Signer-Bindung, eigene Historie, unrelated. Nicht in den Produktbranch mergen. |
| #699 | [Code52 Cinema][P0] Repair native QA, exit-state recovery and avatar e | ahead=12 behind=3 | (b) | 12 einzigartige Commits gegen #698 (QA, Gaze-Serialisierung, Exit-Restore, #701). 3 Commits hinter #698 (inkl. Uhren-Fix a0a69263). Teil von #706. Enthält Bot-04-Datei. |
| #698 | [Code52] Complete cinema integration: Fold7, Hollywood fly-in, origina | ahead=0 behind=0 | (a) | Ausgangsstand dieser Inventur / Basis dieses Branches: a0a69263cdc3c87ed1b728c58188ee9529a78eb4. |
| #697 | [Code52 Integrator] Thermal + document lifecycle P0 fixes on green Swa | ahead=0 behind=36 | (a) | ahead=0. Thermal-Integrator ist Ancestor von #698. |
| #696 | feat(code52): visual depth / hero hierarchy toward ref-b (Bildwirkung) | ahead=0 behind=37 | (a) | ahead=0. Bildwirkung/Tiefe ist Ancestor; Weiterarbeit an JarvisVisualDepth/Glas/Orb ist Bot 06. |
| #695 | [Code52][P0 THERMAL] Cinema EXP15 cannot override Fold7 CPU/GPU coolin | ahead=2 behind=49 | (a) | SHA ahead=2 (e217e768, 5ae10135), Inhalt laut Phase-A gleichwertig über #697 in Cinema (JarvisRenderQuality EXP15-Boden). Nicht erneut mergen. |
| #693 | feat(code52): original-orb and HUD visual style toward reference B | ahead=2 behind=39 | (c) | ahead=2 nur leere Merge-Commits von cockpit-fidelity in visual-style. Inhalt steckt in #696/#698. |
| #692 | feat(code52): cinema fly-in for panels, dock and work windows | ahead=0 behind=30 | (a) | ahead=0. Fly-in/Wegflug-Quelle; Weiterarbeit JarvisCinemaEntrance = Bot 04. |
| #691 | feat(code52): pack Fold7/tablet cockpit to reference-B composition | ahead=0 behind=42 | (a) | ahead=0. Fold7/Tablet-Pack; Phone-Überlauf und dialogTop (ui-fixes) = Bot 07. |
| #690 | [Code52][BOT08 SUPPORT] Keep document drafts across verbal turns and r | ahead=11 behind=50 | (a) | Produktkern (Document-Lifecycle) über #697+#701 gleichwertig vorhanden. Spitze würde mit #701 kollidieren (Orchestrator). Nur extra QA-Workflow-Datei fehlt — nicht nötig. |
| #686 | [Code52][SWARM][Draft] Phase-A Inventur: Journal, No-Orphans-Delta, Ar | ahead=0 behind=43 | (a) | ahead=0. Swarm-Basis, Ancestor. |
| #685 | [Code52][QA] Isolierte Cinema-Prüfläufe je Branch; keine gegenseitigen | ahead=0 behind=61 | (a) | ahead=0. Branch-safe QA, Ancestor. |
| #684 | [Code52][FUI] Kein erfundener Antwort-Pill: echte Dokumentfrage in ani | ahead=0 behind=63 | (a) | ahead=0. Grounded document question, Ancestor. |
| #683 | [Code52][GRAPHICS FINAL AUDIT] Restore Orb phase guard & hypothesis te | ahead=0 behind=68 | (a) | ahead=0. Graphics audit, Ancestor. |
| #682 | [Code52][SIGNER PREP] Exact-SHA Cinema2030 QA artifact only; original  | ahead=13 behind=5613 | (e) | Älterer Signer-Kandidat, unrelated histories. Ersetzt durch #700/#704. |
| #681 | [Code52][CINEMA2030] AGSL prism rim pixel/dp coordinate fix + exact-SH | ahead=0 behind=76 | (a) | ahead=0. Prism-Pixel-Fix, Ancestor. |
| #680 | [Cinema2030 next wave] Echt schaltbare Effekte, 3D-Glasschichten und w | ahead=0 behind=79 | (a) | ahead=0. Switch-Contract, Ancestor. |
| #677 | [Code52][CINEMA2030] Räumliche Hologramme, Avatar-Licht, echte Arbeits | ahead=0 behind=102 | (a) | ahead=0. Argon4 holographic rim, Ancestor. |
| #672 | [Code52] Projektisolation – fremde App-Sonderpfade entfernen | ahead=7 behind=217 | (a) | SHA ahead=7, aber CODE52_PROJECT_BOUNDARY.md und code52-project-isolation.test.mjs sowie die Code-Änderungen sind auf #698 vorhanden (über #671 portiert). |
| #671 | [Code52][Integration] Vollinventur, P0-Fixes, Anschlussmatrix und sich | ahead=0 behind=162 | (a) | ahead=0 gegen aktuelles #698 (in Phase A noch unsicher, jetzt GitHub-Compare hinter #698). |
| #669 | [Fold7 P0][Code52] Native Generation-Abbruchgrund erhalten und CPU-Küh | ahead=0 behind=227 | (a) | ahead=0. Fold7 inference truth ist Ancestor von #698. |

Code51-Kern ist Ancestor von #698 (ahead=0): #646 Sprach-Recovery, #650/#653 Integration, #651 FUI, #654 Navigation per Sprache, #656 Websuche per Chat, #657 Memory-Meta, #658 Reconcile, #663 Master-Integrator, #669 Fold7-Inference, #671 Unified Integration.

## 4. Parallel-Bots — Dateisperren

| Bot | Dateien | Konsequenz für Altbestand |
|---|---|---|
| 04 Choreografie/Wegflug | `Airbus2100CockpitOrchestrator`, `JarvisCinemaEntrance` | #699/#701/#690-Spitze nicht von Bot 01 gemergt |
| 07 Layout | `referenceComposition`, Layout-Teile `JarvisShellRenderer`/`JarvisTiles`, `JarvisShellLayout` | ui-fixes `fbe35a7de`/`3ba8fc4f6` und #598 markiert |
| 06 Tiefe/Glas/Orb | `JarvisVisualDepth`, `JarvisGlassRenderer`, `ContinuumBrainOrbRenderer` | #696 Inhalt schon in #698; keine erneute Übernahme |
| 16 Release-Kandidat | Branch `code52/grok-release-candidate-0351-20261110` (#706) | #703-Inhalt dort und hier; #699/#702 nur dort |

## 5. Die 54 fehlenden MAIN-Dateien — Klassifikation

Alle eindeutig fehlenden `app/src/main/**`-Pfade gegen #698, mit neuestem PR der sie noch trägt:

| Datei | Neuester PR | Klasse | Begründung |
|---|---|---|---|
| `app/src/main/java/at/heinz/deviceai/ExpertModeConsent.kt` | #706 | (b) | Auf diesem Branch angeschlossen (Cherry-Pick #703). Auch in #706. |
| `app/src/main/java/at/heinz/deviceai/cognitive/InternetAccessModeLabels.kt` | #706 | (b) | Auf diesem Branch angeschlossen (Cherry-Pick #703). Auch in #706. |
| `app/src/main/java/at/heinz/deviceai/learning/context/Ctx3DiscourseState.kt` | #602 | (c) | Ersetzt durch resolveOrderedDeictic in aktueller Ctx3Context plus Diskurs-Tests (Nimm die davor / das andere). |
| `app/src/main/java/at/heinz/deviceai/learning/context/Ctx3RoleResolution.kt` | #602 | (c) | Ersetzt durch EntityRoleResolutionEngine (Tochter/Schwester-Possessiv-Tests) und EWM-/OwnerAttribute-Kinship. |
| `app/src/main/java/at/heinz/deviceai/cognitive/loop/CognitiveLoop.kt` | #490 | (c) | BoundedAgentOrchestrator ist der produktive Loop (CODE48-Audit). Eigener Handoff ohne Planner. |
| `app/src/main/java/at/heinz/deviceai/cognitive/loop/CognitiveLoopCockpitBridge.kt` | #490 | (c) | wie CognitiveLoop.kt |
| `app/src/main/java/at/heinz/deviceai/cognitive/loop/CognitiveLoopContracts.kt` | #490 | (c) | wie CognitiveLoop.kt |
| `app/src/main/java/at/heinz/deviceai/cognitive/loop/CognitiveLoopGatePorts.kt` | #490 | (c) | wie CognitiveLoop.kt |
| `app/src/main/java/at/heinz/deviceai/ui/video3/JarvisGreetingCard.kt` | #487 | (b) | Für Bot 04/07: video3-Rest neben vorhandenem Warp/Memory/FileTunnel. |
| `app/src/main/java/at/heinz/deviceai/ui/video3/JarvisUserCard.kt` | #487 | (b) | Für Bot 04/07. |
| `app/src/main/java/at/heinz/deviceai/ui/video3/Video3AlarmScene.kt` | #487 | (b) | Für Bot 04/07. |
| `app/src/main/java/at/heinz/deviceai/ui/video3/Video3CalmNotice.kt` | #487 | (b) | Für Bot 04/07. |
| `app/src/main/java/at/heinz/deviceai/ui/video3/Video3Contracts.kt` | #487 | (b) | Für Bot 04/07. |
| `app/src/main/java/at/heinz/deviceai/ui/video3/Video3Curves.kt` | #487 | (b) | Für Bot 04/07. |
| `app/src/main/java/at/heinz/deviceai/ui/video3/Video3Pills.kt` | #487 | (b) | Für Bot 04/07. |
| `app/src/main/java/at/heinz/deviceai/chief/ChiefProofGatedStrategyLedger.kt` | #387 | (c) | CCC1/Chief-Strategie später über coding-service / ChiefBot*-Pfad der Code50/51-Linie weitergeführt; 2070 Commits hinter Cinema. Nicht blind mergen. |
| `app/src/main/java/at/heinz/deviceai/chief/ChiefTaskGraph.kt` | #387 | (c) | wie StrategyLedger; aktuelle Chief-Task-Aufnahme ist ChiefBotTaskIntake. |
| `app/src/main/java/at/heinz/deviceai/ContinuumAvatarLabActivity.kt` | #338 | (c) | Offline-Avatar-Labor (Nov 1). Produktionsavatar ist Filament/Cinema-Pfad. Nicht als zweite Activity anschließen. |
| `app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentSourceChunkIndex.kt` | #326 | (d) | M5 Chunk-Index/Provenienz, 2495 hinter Cinema. Wertvoll für Quellenlernen, aber API-Drift — nächste Runde gezielt portieren, nicht ganzen Branch mergen. |
| `app/src/main/java/at/heinz/deviceai/cognitive/DevelopmentSourceStreamImport.kt` | #326 | (d) | wie ChunkIndex. |
| `app/src/main/java/at/heinz/deviceai/learning/PersonalLearningPreviewCopy.kt` | #311 | (c) | Lern-Preview-Texte; aktuelle Cognitive-/Memory-UI hat eigene Copy. 2495 hinter Cinema. |
| `app/src/main/java/at/heinz/deviceai/ContinuumTermuxFailureDiagnosis.kt` | #310 | (c) | Termux-Diagnose einer älteren Offline-Linie. Continuum ist kein Termux-Produktpfad mehr. |
| `app/src/main/java/at/heinz/deviceai/cognitive/ConversationResearchIntent.kt` | #310 | (c) | Web/Chat-Research von Code51 #656 ist Ancestor von #698 (ApprovedWebToolGate / Chat-Websuche). |
| `app/src/main/java/at/heinz/deviceai/ContinuumSelfInspectionQuery.kt` | #273 | (d) | Selbstinspektion aus der M8-Parallellinie (behind=2795). Kein Namens-Äquivalent auf #698. Nächste Runde einzeln portieren. |
| `app/src/main/java/at/heinz/deviceai/cognitive/Argon4SystemShadow.kt` | #273 | (c) | Research-Shadow; ARGON4-Produktpfad lief über spätere Cinema-/Code52-Linie. |
| `app/src/main/java/at/heinz/deviceai/learning/NeuralTutorBatchHint.kt` | #273 | (d) | Tutor-Hinweise der M8-Linie, 2795 hinter Cinema. Einzeln prüfen. |
| `app/src/main/java/at/heinz/deviceai/research/argon4/ComputeAllocationPolicy.kt` | #273 | (c) | Research-only ARGON4, nicht Produktpfad. |
| `app/src/main/java/at/heinz/deviceai/research/argon4/DialogStateKernel.kt` | #273 | (c) | Research-only. |
| `app/src/main/java/at/heinz/deviceai/research/argon4/JustifiedRelationStore.kt` | #273 | (c) | Research-only; Relation/Kinship heute in EWM + EntityRoleResolutionEngine. |
| `app/src/main/java/at/heinz/deviceai/cognitive/LocalModelClaimAudit.kt` | #264 | (d) | Ehrliche Modell-Claim-Prüfung. Kein Äquivalent-Dateiname auf #698 (AnswerClaimVerifier existiert). Inhaltlich gegen AnswerClaimVerifier abgleichen, nicht blind kopieren. |
| `app/src/main/java/at/heinz/deviceai/cognitive/MemoryQuestionResolver.kt` | #264 | (d) | Kein Dateiname auf #698. Memory-Fragen laufen über spätere Retriever/EWM. Delta gezielt prüfen. |
| `app/src/main/java/at/heinz/deviceai/cognitive/MemoryTimeline.kt` | #264 | (d) | Kein Dateiname auf #698. ContinuumTemporalMemory deckt Zeitlinien teilweise. Delta prüfen. |
| `app/src/main/java/at/heinz/deviceai/jarvis/ContinuumJarvisPresenceEngine.kt` | #264 | (d) | Präsenz/Initiative-Schatten (JPI1). Nicht verdrahtet in Cinema. Wertvoll, aber 2795 hinter Cinema — eigene Bot-10-Runde. |
| `app/src/main/java/at/heinz/deviceai/jarvis/JarvisInitiativePolicy.kt` | #264 | (d) | wie PresenceEngine. |
| `app/src/main/java/at/heinz/deviceai/jarvis/JarvisSessionHandoff.kt` | #264 | (d) | wie PresenceEngine. |
| `app/src/main/java/at/heinz/deviceai/jarvis/JarvisVoiceConversationPolicy.kt` | #264 | (c) | Freisprechen/Diktat läuft über LocalSpeechInputPolicy / ContinuousConversationMode der späteren Linie. |
| `app/src/main/java/at/heinz/deviceai/cognitive/AuthorizedWebLearningReviewInbox.kt` | #239 | (c) | Web-Lern-Review; genehmigtes Web läuft über ApprovedWebToolGate. 2795 hinter Cinema. |
| `app/src/main/java/at/heinz/deviceai/ContinuumMicroAgentBridge.kt` | #208 | (c) | Research Microagents (28.10.). Nicht Produktpfad. |
| `app/src/main/java/at/heinz/deviceai/learning/interactive/InteractiveDialogueContext.kt` | #174 | (c) | Interactive-Intelligence-Lab; Ctx3/EWM haben den Dialogkontext übernommen. |
| `app/src/main/java/at/heinz/deviceai/learning/interactive/InteractiveResearchRouting.kt` | #174 | (c) | wie oben. |
| `app/src/main/java/at/heinz/deviceai/localmodel/LocalModelImportPreset.kt` | #149 | (c) | Gemma-Import-Preset einer alten lokalen Modell-Linie (25.10.). Aktueller Importpfad ist ein anderer. |
| `app/src/main/java/at/heinz/deviceai/learning/synapse/ContinuumSynapseZeroShadow.kt` | #73 | (c) | Research Synapse v2 (22.10.), Shadow-only. |
| `app/src/main/java/at/heinz/deviceai/learning/synapse/SynapseContrastiveRoleProbe.kt` | #73 | (c) | Research. |
| `app/src/main/java/at/heinz/deviceai/learning/synapse/SynapseV2Reasoner.kt` | #73 | (c) | Research. |
| `app/src/main/java/at/heinz/deviceai/ContinuumPcmLevel.kt` | #35 | (c) | PCM-Pegel 19.10.; heutiger Pfad ist TtsHologramSignal + AvatarContract PCM_ENVELOPE / measuredSilence. |
| `app/src/main/java/at/heinz/deviceai/learning/ContinuumAnswerCorrection.kt` | #27 | (c) | Selektive Korrektur 15.10.; spätere Memory-/EWM-Korrektur- und Widerrufpfade existieren. |
| `app/src/main/java/at/heinz/deviceai/continuum/AiContextCompiler.kt` | #8 | (c) | Frühe Phase-1-Runtime (13.10., behind=5595). Gleichnamige Konzepte liegen heute unter cognitive/* (AiContextCompiler, CognitiveRaid, ComputeFabric existieren dort). Nicht die alten Dateien daneben legen. |
| `app/src/main/java/at/heinz/deviceai/continuum/BenchmarkHarness.kt` | #8 | (c) | Frühe Phase-1-Runtime (13.10., behind=5595). Gleichnamige Konzepte liegen heute unter cognitive/* (AiContextCompiler, CognitiveRaid, ComputeFabric existieren dort). Nicht die alten Dateien daneben legen. |
| `app/src/main/java/at/heinz/deviceai/continuum/CognitiveRaid.kt` | #8 | (c) | Frühe Phase-1-Runtime (13.10., behind=5595). Gleichnamige Konzepte liegen heute unter cognitive/* (AiContextCompiler, CognitiveRaid, ComputeFabric existieren dort). Nicht die alten Dateien daneben legen. |
| `app/src/main/java/at/heinz/deviceai/continuum/ComputeFabric.kt` | #8 | (c) | Frühe Phase-1-Runtime (13.10., behind=5595). Gleichnamige Konzepte liegen heute unter cognitive/* (AiContextCompiler, CognitiveRaid, ComputeFabric existieren dort). Nicht die alten Dateien daneben legen. |
| `app/src/main/java/at/heinz/deviceai/continuum/ContinuumLocalContextAdapter.kt` | #8 | (c) | Frühe Phase-1-Runtime (13.10., behind=5595). Gleichnamige Konzepte liegen heute unter cognitive/* (AiContextCompiler, CognitiveRaid, ComputeFabric existieren dort). Nicht die alten Dateien daneben legen. |
| `app/src/main/java/at/heinz/deviceai/continuum/ContinuumObjectStore.kt` | #8 | (c) | Frühe Phase-1-Runtime (13.10., behind=5595). Gleichnamige Konzepte liegen heute unter cognitive/* (AiContextCompiler, CognitiveRaid, ComputeFabric existieren dort). Nicht die alten Dateien daneben legen. |
| `app/src/main/java/at/heinz/deviceai/continuum/ContinuumSeed.kt` | #8 | (c) | Frühe Phase-1-Runtime (13.10., behind=5595). Gleichnamige Konzepte liegen heute unter cognitive/* (AiContextCompiler, CognitiveRaid, ComputeFabric existieren dort). Nicht die alten Dateien daneben legen. |
| `app/src/main/java/at/heinz/deviceai/continuum/Deliberation.kt` | #8 | (c) | Frühe Phase-1-Runtime (13.10., behind=5595). Gleichnamige Konzepte liegen heute unter cognitive/* (AiContextCompiler, CognitiveRaid, ComputeFabric existieren dort). Nicht die alten Dateien daneben legen. |

## 6. Branches ohne offenen Produkt-PR

| Branch | Klasse | Begründung |
|---|---|---|
| `code52/final-integrator-ui-fixes-20261110` `fbe35a7de` / `3ba8fc4f6` | (b) für Bot 07 | 2 Commits über gemeinsamem `9314bc499`; ändert `JarvisShellLayout` (dialogTop) und einen Viewport-Test. Konfliktträchtiger Layout-Nachzug, nicht von Bot 01 angefasst. |
| `wip/checkpoint-20261110-render-combo` | (e) als Merge | Absichtlich ungelöster Probe-Merge (7 Konfliktdateien inkl. JarvisCinemaEntrance/JarvisTiles). Archiv, nicht mergen. |
| `wip/stop-20261104-*` | (c) | Stopp-Snapshots 04.11., durch spätere Cinema-Linie ersetzt. Nicht löschen. |
| `grok/jarvis-closeout-20261104` #488, `grok/jarvis-design-system` #479, `grok/chief-e2e-tests` #377, `grok/e2e-erster-denkdurchlauf` | (c) | Historische Grok-Linien; Design/Avatar/E2E in Code50–52 neu aufgebaut. Nicht löschen, nicht in Cinema mergen. |
| Signer-Zweige `chatgpt/code52-owner-signing-20261109`, #700, #682, #704 | (e) | Eigene Historie, fremder Signierpfad. Nicht in die Produktlinie. |

## 7. Geschlossene, nicht gemergte PRs (7)

| PR | Klasse | Hinweis |
|---|---|---|
| #633 Code50 upstream reconciled | (c) | Durch spätere Code50/51/52-Linie überholt. |
| #612 Code50 ARGON4 reasoning workscene | (c) | In Cinema-Workscenes aufgegangen. |
| #605 Code50 gate fixes | (c) | QA-Gates der späteren Linie. |
| #504 Inworld text mentor | (c) | Explizit SHELVED: Avatar/Memory brauchen anderen Anschluss. |
| #105 render-dirty-audit | (c) | Diagnose, kein Feature. |
| #19 first vertical slice | (c) | Ursprungsscheibe, längst überbaut. |
| #4 open-source reference pack | (c) | Nur Docs. |

## 8. Offene PRs — automatisch klassifiziert (SHA) plus Hand-Audit wo vorhanden

Regel: ahead=0 → (a). Signer → (e). Sonst Hand-Audit wenn vorhanden, ansonsten (a) wenn keine fehlende MAIN-Datei, (b) wenn fehlende MAIN-Datei ohne Hand-Audit (dann in Abschnitt 5 nachschlagen). Research-only-Titel ohne MAIN-Lücke → (c).

| PR | Branch | ahead | Klasse | Kurzbegründung |
|---|---|---:|---|---|
| #706 | `code52/grok-release-candidate-0351-20261110` | 19 | (b) | Bot 16 Release-Kandidat: #698 + #699/#701 + #702 + #703. Fast-forward von #698 (ahead=19, behind=0). Nicht von Bot 01 erneut gemergt, um Parallel-Arbeit nicht z |
| #705 | `grok/bot01-no-orphans-20261110` | 13 | (a) | Vorherige Phase-A-Analyse (nur docs/handover/GROK-PHASE-A-NO-ORPHANS-20261110.md). Kein Produktcode. |
| #704 | `grok/bot13-signer-contract-cinema-20261110` | 12 | (e) | Signer-Vertragstest auf eigener Signier-Historie (behind=5613, unrelated zum Produktpfad). Nicht in die Cinema-Produktlinie mergen. |
| #703 | `grok/bot13-expert-mode-toolgate-20261110` | 14 | (b) | IMPLEMENTIERT auf diesem Branch: Cherry-Picks 9b8aca625757 → ce94c68f4 (ExpertModeConsent an ToolRegistry) und 753516eb8001 → 34e7e9e09 (Internet-Anzeige ALWAYS |
| #702 | `grok/bot15-node-reliability-20261110` | 15 | (b) | Node-Verträge für #701 DocumentLearned-Isolation. Hängt an #699/#701 (Airbus2100CockpitOrchestrator). Für Bot 15/16 bzw. Integrator; Bot 01 fasst die Orchestrat |
| #700 | `code52/cinema-original-signer-qa-binding-20261110` | 11 | (e) | Original-Signer-Bindung, eigene Historie, unrelated. Nicht in den Produktbranch mergen. |
| #699 | `code52/cinema-native-qa-review-fixes-20261110` | 12 | (b) | 12 einzigartige Commits gegen #698 (QA, Gaze-Serialisierung, Exit-Restore, #701). 3 Commits hinter #698 (inkl. Uhren-Fix a0a69263). Teil von #706. Enthält Bot-0 |
| #698 | `code52/final-integrator-cinema-20261110` | 0 | (a) | Ausgangsstand dieser Inventur / Basis dieses Branches: a0a69263cdc3c87ed1b728c58188ee9529a78eb4. |
| #697 | `code52/final-integrator-thermal-20261110` | 0 | (a) | ahead=0. Thermal-Integrator ist Ancestor von #698. |
| #696 | `code52/visual-depth-ref-d-20261110` | 0 | (a) | ahead=0. Bildwirkung/Tiefe ist Ancestor; Weiterarbeit an JarvisVisualDepth/Glas/Orb ist Bot 06. |
| #695 | `chatgpt/code52-fold7-thermal-budget-guard-20261110` | 2 | (a) | SHA ahead=2 (e217e768, 5ae10135), Inhalt laut Phase-A gleichwertig über #697 in Cinema (JarvisRenderQuality EXP15-Boden). Nicht erneut mergen. |
| #693 | `code52/visual-style-20261110` | 2 | (c) | ahead=2 nur leere Merge-Commits von cockpit-fidelity in visual-style. Inhalt steckt in #696/#698. |
| #692 | `code52/cinema-flyin-20261110` | 0 | (a) | ahead=0. Fly-in/Wegflug-Quelle; Weiterarbeit JarvisCinemaEntrance = Bot 04. |
| #691 | `code52/cockpit-fidelity-20261110` | 0 | (a) | ahead=0. Fold7/Tablet-Pack; Phone-Überlauf und dialogTop (ui-fixes) = Bot 07. |
| #690 | `chatgpt/code52-swarm-document-lifecycle-fix-20261110` | 11 | (a) | Produktkern (Document-Lifecycle) über #697+#701 gleichwertig vorhanden. Spitze würde mit #701 kollidieren (Orchestrator). Nur extra QA-Workflow-Datei fehlt — ni |
| #686 | `integration/code52-swarm-20261110` | 0 | (a) | ahead=0. Swarm-Basis, Ancestor. |
| #685 | `chatgpt/code52-cinema-branch-safe-qa-20261110` | 0 | (a) | ahead=0. Branch-safe QA, Ancestor. |
| #684 | `chatgpt/code52-cinema-grounded-document-question-20261110` | 0 | (a) | ahead=0. Grounded document question, Ancestor. |
| #683 | `chatgpt/code52-cinema-final-graphics-audit-20261110` | 0 | (a) | ahead=0. Graphics audit, Ancestor. |
| #682 | `chatgpt/code52-cinema-owner-signer-candidate-20261110` | 13 | (e) | Älterer Signer-Kandidat, unrelated histories. Ersetzt durch #700/#704. |
| #681 | `chatgpt/code52-cinema-prism-pixel-fix-20261110` | 0 | (a) | ahead=0. Prism-Pixel-Fix, Ancestor. |
| #680 | `chatgpt/code52-cinema-switch-contract-20261109` | 0 | (a) | ahead=0. Switch-Contract, Ancestor. |
| #677 | `chatgpt/code52-argon4-holographic-rim-20261109` | 0 | (a) | ahead=0. Argon4 holographic rim, Ancestor. |
| #672 | `chatgpt/code52-project-isolation-20261109` | 7 | (a) | SHA ahead=7, aber CODE52_PROJECT_BOUNDARY.md und code52-project-isolation.test.mjs sowie die Code-Änderungen sind auf #698 vorhanden (über #671 portiert). |
| #671 | `chatgpt/code52-unified-integration-20261109` | 0 | (a) | ahead=0 gegen aktuelles #698 (in Phase A noch unsicher, jetzt GitHub-Compare hinter #698). |
| #669 | `chatgpt/code52-fold7-inference-truth-20261109` | 0 | (a) | ahead=0. Fold7 inference truth ist Ancestor von #698. |
| #666 | `chatgpt/cinematic-sustained-hologram-20261109` | 14 | (a) | Produktcode (AvatarSentenceAccentPolicy, AvatarCoreLighting, SurfaceRadiance, JarvisWindowGaze, HollywoodFuiWorkActivity) auf #698 vorhanden. Einziger fehlender |
| #665 | `chatgpt/code51-ui-acceptance-recovery-20261109` | 4 | (a) | UI-Acceptance-Testdateien existieren auf #698; fehlend nur das Recovery-Handoff-Dokument. |
| #664 | `chatgpt/code51-voice-prosody-recovery-20261109` | 3 | (a) | JarvisVoiceProfile enthält abbreviationBeforeDot (Dr./Prof./Titel) und Klauselpausen; Handoff-Dokument vorhanden. Inhalt angeschlossen. |
| #663 | `copilot/code51-master-integrator-20261108` | 0 | (a) | ahead=0. Code51 Master-Integrator ist Ancestor. |
| #662 | `astra/continuum-bot-universe-20261108` | 4 | (a) | Coding-Service-/CCC1-Produktdateien auf #698 vorhanden; fehlen nur Astra-Handoff-Docs. |
| #661 | `astra/continuum-holo-reference-fidelity-20261108` | 1 | (c) | Nur Evidenzbilder/Messskripte fehlen. Grafik-Produktcode der V2-Linie ist in Cinema weiterentwickelt. Nicht als tote Evidenz mergen. |
| #660 | `research/continuum-genesis-independent-audit-20261108` | 3 | (c) | Research-only Genesis-Audit. Kein Produktanschluss. |
| #659 | `astra/continuum-holographic-v2-20261108` | 10 | (a) | Wie #666: Hollywood/Avatar-V2-Produktfiles auf #698 vorhanden; fehlt AvatarReadingContinuityTest.kt. |
| #658 | `sol/code51-reconcile-20261108` | 0 | (a) | ahead=0. Code51 reconcile Ancestor. |
| #657 | `fable/code51-p1-memory-meta-separation-20261108` | 0 | (a) | ahead=0. Memory-Meta-Trennung Ancestor. |
| #656 | `fable/code51-p1-chat-web-research-20261108` | 0 | (a) | ahead=0. Websuche per Sprache/Chat Ancestor. |
| #655 | `research/continuum-h4-robust-transfer-20261108` | 11 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #654 | `fable/code51-p1-internal-navigation-routing-20261108` | 0 | (a) | ahead=0. Interne Navigation per Sprache Ancestor. |
| #653 | `sol/code51-pinned-integration-20261108` | 0 | (a) | ahead=0. |
| #651 | `fable/code51-fui-cinematic-construct-20261108` | 0 | (a) | ahead=0. FUI cinematic construct Ancestor. |
| #650 | `sol/code51-master-integration-20261108` | 0 | (a) | ahead=0. |
| #649 | `research/continuum-verified-reuse-pilot-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #648 | `research/continuum-prior-batch-pilot-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #647 | `research/continuum-cognitive-pilots-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #646 | `fable/code51-p0-speech-recovery-20261108` | 0 | (a) | ahead=0. Speech-Recovery Ancestor. |
| #644 | `chatgpt/code51-owner-canon-fable51-handoff-20261108` | 2 | (c) | Nur Docs (Owner-Kanon). Kein Produktorphan. |
| #635 | `research/continuum-epistemic-lab-20261108` | 2 | (c) | Research-only ARGON-4 Labor. |
| #630 | `chatgpt/code50-qa-staging-20261108` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #629 | `chatgpt/code50-local-qa-20261108` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #628 | `chatgpt/code50-app-receipt-gate-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #614 | `chatgpt/code50-hollywood-fui-foundation-20261108` | 14 | (c) | Hollywood-FUI-Foundation; in Code52-Cinema (#677+) ersetzt/weitergeführt. |
| #603 | `codex/continuum-code50-integration-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #602 | `codex/continuum-role-resolution-shadow-20261107` | 17 | (c) | Ctx3RoleResolution.kt / Ctx3DiscourseState.kt fehlen als Dateinamen, sind aber ersetzt durch EntityRoleResolutionEngine (Familie/Possessiv, Tests zu Tochter/Sch |
| #601 | `codex/continuum-discourse-shadow-v2-20261107` | 12 | (c) | Teilmenge von #602, siehe dort. |
| #600 | `codex/continuum-cognitive-state-shadow-20261107` | 7 | (a) | Ctx3StateTelegram.kt und Verdrahtung in Ctx3Shadow sind auf #698 vorhanden. |
| #599 | `codex/continuum-avatar-live-proof-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #598 | `codex/continuum-code49-reference-input-20261107` | 4 | (b) | JarvisReferenceInputTest.kt fehlt. Layout-Änderung betraf JarvisShellLayout (dialogTop/reference home input) — für Bot 07 markiert, von Bot 01 nicht eingebaut. |
| #597 | `codex/continuum-dark-reference-layout-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #596 | `codex/continuum-code49-elevenlabs-voice-design-20261107` | 1 | (c) | Nur ElevenLabs-Designdokument; Cloud-Stimme nur mit Owner-Freigabe. Kein Produktcode. |
| #595 | `codex/continuum-code48-github-owner-20261107` | 9 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #594 | `codex/continuum-code49-orb-render-performance-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #593 | `codex/continuum-code48-release-completion-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #592 | `codex/continuum-code48-recovered-active-paths-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #591 | `codex/continuum-code48-fullstage-cinema-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #590 | `codex/continuum-code48-connected-cinema-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #589 | `codex/continuum-code48-workscene-thermal-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #588 | `codex/continuum-code47-apk-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #587 | `codex/continuum-code47-release-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #586 | `codex/continuum47-planning-context-regression-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #585 | `codex/continuum47-fallback-identity-contract-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #584 | `codex/continuum47-pdf-word-order-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #583 | `codex/continuum47-routing-regressions-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #582 | `codex/continuum47-app-query-format-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #578 | `codex/continuum-code47-qa-candidate-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #577 | `codex/continuum-build46-47-help-20261106` | 3 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #576 | `codex/continuum-code47-integration-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #575 | `codex/continuum-owner-apk-20261106` | 3 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #574 | `claude/continuum-fable51-continuation-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #573 | `codex/continuum-fold7-quality-lab-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #572 | `codex/verify-cockpit-centre-windows-20261106` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #571 | `codex/jarvis-camera-flight-20261106` | 6 | (a) | AvatarCameraFraming.kt und AvatarCameraMotion.kt sind auf #698 vorhanden. |
| #570 | `fix/continuum-code44-release-qa-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #565 | `claude/continuum-jarvis-cockpit-z7bo60` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #564 | `research/opus55-continuum-voice-hud-preflight-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #563 | `research/continuum-cockpit-voice-original-orb-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #562 | `support/continuum-pcm-silence-3e26-20261105` | 2 | (a) | PCM-Stille: AvatarContract.measuredSilence und AvatarMeasuredSilenceTest.kt sind auf #698 vorhanden. |
| #561 | `fix/continuum-original-backend-release-gate-20261105` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #560 | `repair/continuum-owner-backend-gate-20261105-now` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #559 | `fix/continuum-apk42-stabilize-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #558 | `qa/orb-hypothesis-marker-20261105-b3-r3` | 8 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #557 | `support/continuum-reference-binding-s5-20261105` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #556 | `support/h4-avatar-stop-next-turn-regression-20261105` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #555 | `fix/continuum-memory-r13-temporal-lexical-20261105` | 90 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #554 | `support/continuum-apk-preflight-aapt2-20261105-b6` | 7 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #553 | `fix/continuum-memory-r13-relative-cues-20261105` | 86 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #552 | `codex/continuum-owner-face-models-20261105` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #551 | `bot1/avatar-blink-kinematics-20261105` | 34 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #550 | `qa/orb-phase-finite-idle-20261105-b3-r2` | 5 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #549 | `bot2/isolated-neon-tile-energy-20261105` | 9 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #548 | `fix/continuum-memory-r12-numeric-evidence-20261105` | 82 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #547 | `support/continuum-dialog-layout-apk-20261105-3e26` | 3 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #546 | `codex/continuum-neon-finish-20261105` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #545 | `codex/continuum-avatar-dialog-finish-20261105` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #544 | `integration/continuum-avatar-complete-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #543 | `integration/continuum-interim-owner-avatar-neon-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #542 | `codex/continuum-orb-nonfinite-qa-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #541 | `codex/continuum-neon-tiles-20261105` | 26 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #540 | `codex/continuum-avatar-natural-blink-20261105` | 26 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #539 | `integration/continuum-orb-neon-stage-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #538 | `fix/continuum-memory-r11-single-digit-duplicate-negation-20261105` | 72 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #537 | `fix/continuum-memory-r10-fuzzy-ambiguity-20261105` | 61 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #536 | `fix/continuum-memory-r9-negation-fuzzy-boundary-20261105` | 43 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #535 | `ccr-4f80075a-ycl67y` | 25 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #534 | `fix/continuum-memory-r8-negation-k-boundary-20261105` | 24 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #533 | `fix/continuum-memory-index-atomic-r7-20261105` | 14 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #532 | `fix/continuum-memory-retriever-snapshot-r6-20261105` | 10 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #531 | `fix/continuum-memory-r5-credential-boundaries-20261105` | 33 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #530 | `research/continuum-lm1-resumable-training-20261104-319f3e` | 24 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #529 | `fix/continuum-memory-privacy-r4-20261104` | 17 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #528 | `research/continuum-cognition-learning-loop-20261104` | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #527 | `integration/continuum-connected-r3-20261104-822e` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #526 | `codex/continuum-avatar-alive-20261104` | 14 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #525 | `fix/continuum-r6-lm1-capacity-evidence-20261104` | 21 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #524 | `fix/continuum-r6-landscape-48dp-20261104` | 6 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #523 | `integration/continuum-completed-tuning-20261104-822e` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #522 | `fix/continuum-brain-integration-r3-20261104` | 4 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #521 | `codex/continuum-reference-avatar-v2-20261104` | 6 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #520 | `integration/continuum-g1-r5-avatar-accept-20261104` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #519 | `integration/continuum-c1-candidate-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #518 | `codex/continuum-reference-avatar-20261104` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #517 | `integration/continuum-finalization-20261104-18845` | 8 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #516 | `codex/continuum-internal-bot-training-20261104` | 11 | (a) | ChiefBotPersonas/Practice/TaskIntake und ContinuumBotTaskDialog sind auf #698 vorhanden und verdrahtet. |
| #515 | `codex/continuum-branch-integration-bots-20261104` | 32 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #514 | `research/continuum-16m-training-support-r2-20261104-319f3e` | 16 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #513 | `integration/continuum-apk-connections-wave2-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #512 | `integration/continuum-followup-connectors-20261104-822e` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #511 | `tuning/continuum-three-bots-20261104` | 71 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #510 | `codex/continuum-bot-activation-20261104` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #509 | `research/continuum-three-experiments-20261104` | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #508 | `research/continuum-lm1-training-recovery-20261104` | 17 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #507 | `research/continuum-16m-support-20261104-319f3e` | 11 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #506 | `integration/continuum-ready-connections-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #505 | `repair/continuum-three-bots-20261104` | 13 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #503 | `feature/continuum-navada-live-integration-20261104` | 7 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #502 | `feature/continuum-fable51-avatar-motion-20261104` | 5 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #501 | `gamma/parameter-audit-20261104` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #500 | `research/continuum-fable51-jarvis-genesis-20261104` | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #499 | `integration/continuum-memory-avatar-hooks-20261104` | 7 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #498 | `coordination/astra-nucleus-20261104-1027` | 5 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #497 | `research/continuum-weight-helper-team-20261104` | 7 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #496 | `support/continuum-ten-bot-assist-20261104` | 10 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #495 | `codex/continuum-three-avatar-helpers-20261104` | 17 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #494 | `feat/continuum-memory-brain-20261104` | 5 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #493 | `feat/continuum-personality-bots-20261104` | 4 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #492 | `research/continuum-weight-space-audit-20261104` | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #491 | `codex/astra-jarvis-takeover-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #490 | `kern/cognitive-loop-20261104` | 7 | (c) | CognitiveLoop (GOAL→PLAN→TOOL→VERIFY→MEMORY) nicht verdrahtet, eigener Handoff ohne Planner/Activity/Memory. CODE48-Audit: BoundedAgentOrchestrator bleibt der p |
| #489 | `jarvis-mathematik/motion-math` | 3 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #488 | `grok/jarvis-closeout-20261104` | 4 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #487 | `jarvis-luecke/video3-alarm-warp-ganzkoerper` | 12 | (b) | video3-Paket existiert (Warp/Memory/FileTunnel), aber Alarm/Greeting/Calm/Pills/Contracts/Curves aus #487 fehlen. Grafik/Choreografie — für Bot 04/07 prüfen, ni |
| #486 | `design/jarvis-menu-navigation-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #485 | `continuum/m5-safe-flows-fg-20261104` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #484 | `continuum/m2-calc-events` | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #483 | `integration/jarvis-on-chatgpt-untested-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #482 | `jarvis-grafik/avatar-ref-match` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #481 | `design/jarvis-cockpit-home-20261104` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #479 | `grok/jarvis-design-system` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #478 | `fix/continuum-device-experience-resume-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #477 | `fix/continuum-device-experience-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #476 | `design/continuum-reference-cockpit-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #475 | `fix/continuum-status-history-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #474 | `release/continuum-night-integration-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #473 | `codex/independence-native-failure-review-20261103` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #472 | `codex/independence-native-closure-20261103` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #471 | `codex/independence-failure-review-20261103` | 3 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #470 | `codex/momega1-portrait-rings-20261103` | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #469 | `codex/momega1-cli-fixtures-20261103` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #467 | `codex/momega1-gvisor-fixture-20261103` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #466 | `codex/momega1-layout-final-20261103` | 7 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #456 | `codex/continuum-independence-chief-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #455 | `codex/genesis-final-native-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #454 | `codex/momega1-avatar-effects-review-20261103` | 11 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #445 | `codex/momega1-readiness-review-20261103` | 8 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #443 | `codex/genesis-coding-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #439 | `codex/bot-genesis-chief-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #438 | `codex/genesis-brain-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #435 | `codex/momega1-android-plan-review-20261103` | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #433 | `codex/synapse-model-prerequisites-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #431 | `codex/synapse-runtime-audit-20261103` | 4 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #428 | `codex/synapse-ui-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #423 | `codex/synapse-swarm-chief-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #421 | `codex/synapse-p0-audit-20261103` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #419 | `codex/momega1-android-review-20261103` | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #413 | `codex/momega1-qa-20261103` | 51 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #410 | `codex/momega1-chief-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #402 | `codex/breakthrough-neural-20261103` | 7 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #397 | `codex/team-02-memory-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #396 | `codex/team-05-quality-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #395 | `codex/team-04-android-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #394 | `codex/team-01-intelligence-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #393 | `codex/team-03-ccc1-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #392 | `codex/team-chief/omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #391 | `design/continuum-avatar-user-ui-20261103` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #390 | `fix/continuum-code40-router-race-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #389 | `coordination/codex-six-agent-team-20261103` | 4 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #387 | `feature/continuum-alpha-squad-ccc1-entry-20261103` | 12 | (b) | SHA voraus; fehlende MAIN-Dateien: `ChiefProofGatedStrategyLedger.kt`, `ChiefTaskGraph.kt` — siehe §5. |
| #386 | `feature/continuum-chief-proof-gated-strategy-ledger-20261103` | 10 | (b) | SHA voraus; fehlende MAIN-Dateien: `ChiefProofGatedStrategyLedger.kt`, `ChiefTaskGraph.kt` — siehe §5. |
| #385 | `feature/continuum-chief-task-dag-20261103` | 8 | (b) | SHA voraus; fehlende MAIN-Dateien: `ChiefTaskGraph.kt` — siehe §5. |
| #384 | `integration/continuum-chief-team-staging-20261103` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #383 | `feature/continuum-chief-specialist-delegation-20261103` | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #382 | `repair/continuum-chief-routines-mailbox-20261103` | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #381 | `repair/continuum-chief-cancellation-scope-20261103` | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #380 | `integration/continuum-omega-sol61-20261103` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #379 | `continuum/haupt-chief-pattern-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #378 | `jarvis-grafik/avatar-live` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #377 | `grok/chief-e2e-tests` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #375 | `integration/continuum-grok1-369-372-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #374 | `continuum/grok1-integration-20261103` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #373 | `continuum/m3-grok-selbstrekonstruktion-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #372 | `continuum/m5-integration-repair` | 19 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #371 | `continuum/m2-cognitive-memory` | 18 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #370 | `continuum/m2-grok-context-state-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #369 | `continuum/m1-grok-multiturn-routing-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #368 | `research/continuum-native-session-lifecycle-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #367 | `research/continuum-omega-task-scoped-temporal-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #366 | `research/continuum-omega-context-integrity-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #364 | `repair/continuum-shizuku-composer-20261103` | 12 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #363 | `research/continuum-fold7-chat-routing-repair-20261103` | 2 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #362 | `research/continuum-sol-max-autonomy-20261103` | 6 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #361 | `research/continuum-sol61-jarvis-gates-20261102` | 1 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #360 | `integration/continuum-all-systems-apk-qa-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #359 | `research/continuum-execution-atomicity-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #358 | `research/continuum-sovereign-brain-iv-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #357 | `research/continuum-sovereign-cognition-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #356 | `research/continuum-sovereign-gguf-admission-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #354 | `coderabbit/continuum-brain-integration-20261102` | 2 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #353 | `coderabbit/continuum-reliability-sprint-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #352 | `coderabbit/get-device-ai-project-status/de3d7eb3` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #351 | `research/continuum-critical-qa-repair-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #350 | `research/continuum-quantumflow-upstreams-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #349 | `research/continuum-grand-challenge-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #348 | `research/continuum-dialog-vii-repair-audit-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #347 | `research/continuum-dialog-vi-nine-gates-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #346 | `research/continuum-autonomy-dialog-voice-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #345 | `research/continuum-fusion-iv-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #344 | `research/continuum-synapse-iii-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #343 | `research/continuum-synapse-ii-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #342 | `research/continuum-341-brain-wiring-qa-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #341 | `research/continuum-340-evidence-routing-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #338 | `experiment/continuum-avatar-lab-offline-20261101` | 13 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumAvatarLabActivity.kt` — siehe §5. |
| #337 | `fix/continuum-fold7-stream-scroll-20261101` | 7 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #336 | `release/continuum-local-0.3.36-render-20261101` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #335 | `research/continuum-sol61-post-fable-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #334 | `research/continuum-fable51-genesis-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #333 | `research/continuum-jarvis-04-architecture-implementation-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #331 | `research/continuum-jarvis-evolution-20261101` | 1 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #330 | `research/continuum-evidence-span-natural-entities-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #329 | `research/continuum-superki-architecture-proof-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #328 | `release/continuum-local-0.3.35-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #327 | `integration/continuum-ouroboros-ewm2-proof-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #326 | `fix/continuum-m5-chunk-index-provenance-20261101` | 13 | (b) | SHA voraus; fehlende MAIN-Dateien: `DevelopmentSourceChunkIndex.kt`, `DevelopmentSourceStreamImport.kt` — siehe §5. |
| #325 | `fix/continuum-ewm2-archive-plural-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #324 | `integration/continuum-offline-termux-proof-20261101` | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #323 | `fix/continuum-sol314-source-plural-e-20261101` | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #322 | `research/jarvis-autonomous-core` | 16 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #321 | `research/continuum-cdx1-negation-hardlock-20261101` | 11 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #320 | `fix/continuum-ewm2-role-age-production-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #319 | `fix/continuum-sol314-kinship-age-qa-20261101` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #318 | `research/continuum-m5-persistent-chunk-index-20261101` | 8 | (b) | SHA voraus; fehlende MAIN-Dateien: `DevelopmentSourceChunkIndex.kt`, `DevelopmentSourceStreamImport.kt` — siehe §5. |
| #316 | `research/continuum-m5-source-stream-20261101` | 4 | (b) | SHA voraus; fehlende MAIN-Dateien: `DevelopmentSourceStreamImport.kt` — siehe §5. |
| #313 | `fix/continuum-cdx1-collapse-diagnostics-20261101` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #312 | `qa/continuum-owner-v34-independent-readback-20261101` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #311 | `fix/continuum-v35-learning-preview-clarity-20261101` | 4 | (b) | SHA voraus; fehlende MAIN-Dateien: `PersonalLearningPreviewCopy.kt` — siehe §5. |
| #310 | `fix/continuum-v35-offline-long-input-20261031` | 18 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumTermuxFailureDiagnosis.kt`, `ConversationResearchIntent.kt` — siehe §5. |
| #309 | `fix/continuum-v35-owner-qa-intent-20261031` | 15 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumTermuxFailureDiagnosis.kt`, `ConversationResearchIntent.kt` — siehe §5. |
| #305 | `fix/continuum-web-learning-candidates-cleanup-20261101` | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #301 | `release/continuum-local-0.3.34-20261031` | 7 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #299 | `research/continuum-m10-verified-brain-and-web-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #296 | `feature/continuum-v32-openrouter-mentor-20261031` | 13 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #294 | `fix/continuum-v32-network-web-recovery-20261031` | 1 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #292 | `release/continuum-local-complete-v31-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #291 | `release/continuum-local-v30-integration-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #288 | `research/continuum-current-state-discovery-20261031` | 2 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #287 | `research/continuum-nemo-virtual-fabric-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #286 | `research/continuum-m8-render-signed-release-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #285 | `research/continuum-m8-operational-model-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #284 | `research/continuum-jarvis-neuro-integration-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #283 | `research/continuum-jarvis-intent-intake-20261031` | 4 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #282 | `research/continuum-compute-lease-20261031` | 2 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #281 | `research/continuum-context-validity-gate-20261031` | 2 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #280 | `research/continuum-evidence-recall-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #279 | `chatgpt/continuum-render-readback-20261031` | 21 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #278 | `chatgpt/continuum-p0-strict-release-metadata-20261031` | 17 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #277 | `research/continuum-queue-bootstrap-grant-time-20261031` | 18 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #275 | `research/continuum-cloudflare-owner-grant-20261031` | 16 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #274 | `research/continuum-cloudflare-nvidia-direct-20261031` | 15 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #273 | `research/continuum-nvidia-explicit-owner-consent-20261031` | 114 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #272 | `research/continuum-nvidia-executor-env-gate-20261031` | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #271 | `research/continuum-nvidia-claim-readback-20261031` | 108 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #270 | `research/continuum-nvidia-single-use-claim-20261031` | 103 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #268 | `research/continuum-cloud-gpu-admission-20261030` | 102 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #267 | `chatgpt/continuum-cdx1-legacy-v30-20261030` | 15 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #266 | `chatgpt/continuum-fold7-single-pass-release-20261030` | 12 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #265 | `claude/m7-m8-integration-kxrxy2` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #264 | `claude/m8-gvisor-executor-kxrxy2` | 185 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #263 | `research/continuum-jarvis-mcp-video-20261030` | 10 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #262 | `research/continuum-jarvis-video-evidence-20261030` | 8 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #261 | `chatgpt/continuum-upstream-first-20261030` | 5 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #260 | `chatgpt/continuum-one-tap-updates-20261030` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #259 | `chatgpt/continuum-m8-benchmark-coverage-20261030` | 167 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #258 | `chatgpt/continuum-m6-independent-holdout-20261030` | 244 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #257 | `chatgpt/continuum-p0-updater-hardening-20261030` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #256 | `claude/continuum-jarvis-exp20-p2-kxrxy2` | 217 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #255 | `research/exp20-p2-context-contract-20261030` | 156 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #254 | `research/exp20-p2-opus-project-brief-20261030` | 154 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #253 | `research/jarvis-voice1-stale-recognizer-session-20261030` | 152 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #252 | `opus/jarvis-voice1-fix-20261030` | 144 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #251 | `research/continuum-owner-resilient-jarvis-charter-20261030` | 143 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #250 | `research/jarvis-voice-conversation-owner-gated-20261030` | 131 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #249 | `research/continuum-self-evolution-shadow-20261030` | 154 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #248 | `research/continuum-mastercode-bounded-search-20261030` | 119 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #247 | `research/jpi1-presence-initiative-shadow-20261030` | 121 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #246 | `research/jpi1-presence-shadow-20261030` | 117 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #245 | `opus/continuum-m9-m16-20261030` | 116 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #244 | `opus/continuum-m1-m8-20261030` | 91 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #243 | `research/continuum-counterexample-acceptance-20261029` | 81 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #242 | `integration/continuum-brain-system-20261029` | 60 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt`, `ComputeAllocationPolicy.kt` … — siehe §5. |
| #241 | `research/sol61-ccc1-skill-transfer` | 52 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #240 | `opus/argon4-research-20261029` | 52 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #239 | `feature/continuum-model-web-learning-review-20261029` | 37 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt`, `AuthorizedWebLearningReviewInbox.kt` — siehe §5. |
| #238 | `sol/continuum-dan-evidence-loop-20261029` | 42 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #237 | `sol/continuum-dialog-integration-20261029` | 15 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #236 | `feature/continuum-p0-inapp-owner-updater-20261029` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #234 | `fix/continuum-dashboard-evidence-labels-20261029` | 6 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #233 | `fix/continuum-self-awareness-neuron-command-20261029` | 13 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #232 | `research/continuum-language-core-update-contract-20261029` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #230 | `sol/continuum-gpt6-closeout-20261029` | 8 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #229 | `review/continuum-memory-attribute-guard-20261029` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #228 | `review/ewm2-revocation-race-20261029` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #227 | `opus/continuum-ewm2-integration-20261029` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #226 | `research/gemma-monotonic-profile-20261029` | 2 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #225 | `codex/worker-prestart-lease-path-20261029` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #224 | `opus/continuum-brain-research-20261029` | 20 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #223 | `sol/continuum-runtime-reliability-20261029` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #222 | `codex/generation16-rescue-bom-bound-20261029` | 6 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #221 | `research/gemma-stream-callback-lifecycle-20261029` | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #220 | `codex/worker-descendant-stop-20261029` | 2 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #219 | `codex/generation16-readonly-rescue-20261029` | 10 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #218 | `sol/continuum-generation16-gemma-20261029` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #217 | `research/continuum-v25-owner-ledger-export-20261028` | 5 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #216 | `research/continuum-worker-untracked-gate-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #215 | `sol/continuum-oneapp-brain-recovery-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #214 | `research/continuum-sol-preflight-and-native-evidence-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #213 | `research/continuum-sol-max-handoff-readiness-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #212 | `research/continuum-local-integrated-gate-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #211 | `research/continuum-selfhosted-coding-20261028` | 7 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #210 | `research/continuum-optional-free-coding-bridge-20261028` | 3 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #209 | `research/continuum-learning-parity-readonly-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #208 | `research/continuum-local-microagents-20261028` | 21 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumMicroAgentBridge.kt` — siehe §5. |
| #207 | `research/continuum-evidence-bound-transfer-20261028` | 12 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #206 | `research/continuum-local-brain-sol-20261028` | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #205 | `chatgpt/continuum-german-research-contradiction-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #204 | `chatgpt/continuum-fusion-regression-gate-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #203 | `chatgpt/continuum-fusion-memory-vertical-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #198 | `chatgpt/continuum-research-integration-memory-m4-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #197 | `chatgpt/continuum-mainapp-m3-after-sol-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #196 | `sol/continuum-local-owner-integration-20261027` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #195 | `chatgpt/continuum-owner-current-qa-apk-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #194 | `chatgpt/continuum-evidence-switch-probe-20261027` | 13 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #193 | `chatgpt/continuum-gemma-reopen-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #189 | `astra/continuum-autonomous-cycle-20261027` | 5 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #187 | `integration/continuum-owner-v26-live-candidate-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #186 | `chatgpt/fold7-reference-exact-grid-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #185 | `chatgpt/continuum-coding-outcome-journal-20261027` | 3 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #184 | `sol/continuum-storage-prototype-20261027` | 3 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #183 | `sol/continuum-harness-fabric-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #182 | `sol/continuum-cognitive-evidence-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #181 | `chatgpt/continuum-jarvis-research-connection-audit-20261027` | 2 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #180 | `chatgpt/continuum-checked-shell-bounds-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #179 | `integration/continuum-autonomy-learning-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #178 | `sol/continuum-autonomy-contrastive-20261027` | 33 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #177 | `chatgpt/continuum-local-worker-learning-lab-20261026` | 23 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #176 | `chatgpt/continuum-feedback-process-safety-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #175 | `integration/continuum-feedback-review-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #174 | `research/continuum-interactive-intelligence-lab-20261026` | 6 | (b) | SHA voraus; fehlende MAIN-Dateien: `InteractiveDialogueContext.kt`, `InteractiveResearchRouting.kt` — siehe §5. |
| #173 | `review/continuum-followup-archive-gate-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #172 | `review/continuum-grounding-port-v24-20261026` | 4 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #171 | `integration/continuum-owner-0323-review-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #170 | `chatgpt/continuum-ccc1-evidence-completeness-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #169 | `sol/continuum-jarvis-open-fabric-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #168 | `research/continuum-virtual-model-fabric-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #167 | `research/continuum-bottleneck-scorecard-local-escalation-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #166 | `research/continuum-bottleneck-diagnosis-a-b-c-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #165 | `integration/continuum-sol-numeric-binding-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #164 | `review/continuum-ccc1-test-parity-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #163 | `research/continuum-coin-wm-independent-audit-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #162 | `integration/continuum-grounded-recall-fallback-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #161 | `integration/continuum-typed-source-identity-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #160 | `review/continuum-repair-learning-evidence-gate-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #159 | `astra/continuum-brain-integration-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #158 | `review/continuum-baby-memory-migration-20261026` | 10 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #157 | `review/continuum-brain-safety-continuation-20261026` | 29 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #156 | `sol/continuum-fast-repair-20261026` | 1 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #155 | `sol/coding-discovery-safety-20261026` | 7 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #154 | `sol/deepseek-authenticated-bridge-20261026` | 7 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #153 | `sol/grounding-relations-20261026` | 6 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #152 | `sol/cdx-generation-repair-20261026` | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #151 | `sol/continuum-brain-breakthroughs-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #150 | `sol/continuum-infrastructure-repair-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #149 | `fix/continuum-local-model-quality-gemma-20261025` | 13 | (b) | SHA voraus; fehlende MAIN-Dateien: `LocalModelImportPreset.kt` — siehe §5. |
| #148 | `feature/continuum-model-center-selected-models-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #147 | `integration/continuum-soul-infrastructure-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #146 | `config/continuum-deepseek-gemma-selection-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #145 | `research/continuum-native-foundation-20261025` | 5 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #143 | `integration/continuum-experiment-activation-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #142 | `fix/continuum-orion47-definition-intent-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #140 | `release/continuum-owner-v23-candidate-20261025` | 15 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #139 | `integration/continuum-owner-v23-jarvis-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #138 | `research/jarvis-international-adaptation-20261025` | 1 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #137 | `research/continuum-p256-signature-review-chatgpt-20261025` | 42 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #136 | `research/continuum-agent-handoff-standard-20261025` | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #135 | `research/continuum-typed-review-chatgpt-20261025` | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #134 | `research/continuum-omega-resume-chatgpt-20261025` | 34 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #133 | `research/continuum-omega-vhsr-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #132 | `integration/continuum-m6-all-verified-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #131 | `integration/continuum-m6-all-verified-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #130 | `chatgpt/continuum-m6-run-binding-preflight-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #129 | `chatgpt/continuum-typed-coding-isolation-recovery-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #128 | `chatgpt/continuum-typed-android-coding-bridge-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #127 | `chatgpt/continuum-coding-rejection-feedback-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #126 | `chatgpt/continuum-coding-invalid-feedback-20261024` | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #125 | `sol/continuum-autonomous-coding-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #124 | `release/continuum-owner-0321-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #123 | `integration/continuum-0320-all-connected-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #122 | `chatgpt/continuum-cdx1-postrun-audit-20261024` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #121 | `chatgpt/continuum-core-mirror-20261024` | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #120 | `chatgpt/continuum-0321-local-dialogue-quality-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #118 | `chatgpt/continuum-0320-v21-release-review-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #117 | `astra/continuum-0320-visual-integration-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #116 | `sol/continuum-0320-integration-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #115 | `chatgpt/continuum-autonomous-dialogue-research-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #113 | `chatgpt/continuum-hologram-context-recovery-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #112 | `sol/continuum-max-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #111 | `chatgpt/continuum-master-integration-v19-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #110 | `chatgpt/continuum-m5-draft-approval-guard-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #109 | `sol/continuum-m5-context-coding-experiments-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #108 | `sol/continuum-master-m4-research-integration-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #107 | `gpt6/continuum-m4-first-owned-app-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #100 | `integration/continuum-owner-apk-v18-candidate-20261023` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #99 | `opus/ccc1-e2e-20261023` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #98 | `integration/continuum-premium-ui-preservation-20261023` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #97 | `integration/continuum-current-review-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #96 | `integration/continuum-composer-reflow-fix-20261023` | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #95 | `integration/continuum-verifier-first-spine-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #93 | `integration/continuum-gguf-context-recovery-20261023` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #92 | `opus/continuum-intelligence-next-20261023` | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #91 | `astra/continuum-live-hologram-20261023` | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #90 | `astra/continuum-visual-engineering-20261023` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #84 | `integration/continuum-aaa-lm1-ccc1-preview-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #83 | `chatgpt/continuum-layout-pixel-alignment-20261023` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #82 | `opus/continuum-neural-frontier-lab-20261022` | 31 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #81 | `opus/continuum-aaa-ui-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #80 | `feature/continuum-measured-neon-wave-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #79 | `integration/continuum-aaa-bento-cockpits-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #78 | `integration/continuum-full-feature-candidate-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #77 | `integration/continuum-premium-ui-preview-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #75 | `opus/continuum-language-core-20261022` | 23 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #73 | `opus/continuum-synapse-v2-20261022` | 14 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSynapseZeroShadow.kt`, `SynapseContrastiveRoleProbe.kt`, `SynapseV2Reasoner.kt` — siehe §5. |
| #72 | `opus/continuum-i2-selective-risk-20261022` | 38 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #71 | `experiment/continuum-synapse-zero-20261022` | 6 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumSynapseZeroShadow.kt`, `SynapseContrastiveRoleProbe.kt` — siehe §5. |
| #70 | `chatgpt/continuum-argon-qf2-source-proof-20261022` | 27 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #69 | `chatgpt/continuum-release-integration-audit-20261022` | 5 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #68 | `chatgpt/continuum-i1-model-binding-20261022` | 35 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #67 | `chatgpt/continuum-bundled-quality-qf2-20261022` | 22 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #66 | `chatgpt/continuum-memory-retrieval-no-false-evidence-20261022` | 14 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #65 | `chatgpt/continuum-navigation-signed-delivery-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #64 | `chatgpt/continuum-app-navigation-completion-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #62 | `opus/continuum-csx1-abstention-20261022` | 32 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #61 | `chatgpt/continuum-residual-pointer-built-20261022` | 21 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #60 | `chatgpt/continuum-csx1-research-gate-20261022` | 15 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #59 | `chatgpt/continuum-intelligence-lab-readonly-20261022` | 12 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #58 | `chatgpt/continuum-opus-android-coding-ui-20261022` | 13 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #57 | `opus/continuum-mobile-coding-e2e-20261022` | 6 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #56 | `review/render-first-portable-build-20261022` | 3 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #55 | `chatgpt/continuum-render-delivery-audit-20261022` | 3 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #53 | `sol/continuum-isolated-research-20261021` | 4 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #52 | `astra/render-mentor-apk-builder-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #51 | `chatgpt/continuum-apk-preview-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #50 | `chatgpt/continuum-llama-recovery-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #49 | `astra/continuum-local-coding-milestone-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #48 | `integration/continuum-astra-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #46 | `astra/continuum-research-verification-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #45 | `astra/continuum-atomic-paired-checkpoints-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #44 | `astra/continuum-dual-core-binding-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #42 | `astra/continuum-experiment-charter-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #40 | `astra/render-mentor-apk-builder-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #39 | `astra/continuum-optional-groq-mentor-20261019` | 16 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #38 | `astra/continuum-samsung-research-hotfix-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #37 | `astra/continuum-integrated-apk-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #36 | `handoff/sol61-continuum-20261019` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #35 | `astra/continuum-holographic-ui-20261019` | 62 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumPcmLevel.kt` — siehe §5. |
| #34 | `astra/continuum-blacklab-20261019` | 52 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #33 | `astra/continuum-research-learning-gate-20261019` | 34 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #32 | `astra/continuum-offline-public-research-20261018` | 23 | (c) | Research-Zweig ohne fehlende MAIN-Dateien in der added-File-Prüfung. |
| #31 | `astra/continuum-always-on-web-20261018` | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #30 | `astra/continuum-dna-guardian-20261018` | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #28 | `continuum/argon-safety-review-20261017` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #27 | `copilot/continuum-selective-correction-revocation-20261015` | 28 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumAnswerCorrection.kt` — siehe §5. |
| #26 | `copilot/continuum-owner-answer-corrections-20261015` | 25 | (b) | SHA voraus; fehlende MAIN-Dateien: `ContinuumAnswerCorrection.kt` — siehe §5. |
| #25 | `copilot/continuum-feedback-ledger-20261015` | 7 | (a) | SHA voraus, aber alle damals neuen Produktdateien existieren unter gleichem Pfad auf #698 (portiert/cherry-picked). |
| #24 | `copilot/continuum-android-integration-20261015` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #22 | `continuum-agent/activate-hourly-developer-agent` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #21 | `continuum-agent/repair-22582016d3-e2cd043b` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #18 | `copilot/inspect-extend-compute-distributed-task-code` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #17 | `copilot/fix-64243701-1415878393-f443f816-2b3d-4c73-8097-d7df0cb722ca` | 7 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle; Produktkern bereits in späterer Linie oder bewusst Labor. |
| #16 | `copilot/add-benchmark-measurement-harness` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #14 | `copilot/add-deterministic-jvm-unit-tests` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #12 | `copilot/copilotcontinuum-phase2-intelligence-direct` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #11 | `copilot/continuum-phase2-intelligence-direct` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #10 | `copilot/continuum-phase1-direct` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #9 | `work/continuum-phase1-direct` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #8 | `copilot/continuum-phase1-runtime` | 2 | (b) | SHA voraus; fehlende MAIN-Dateien: `AiContextCompiler.kt`, `BenchmarkHarness.kt`, `CognitiveRaid.kt`, `ComputeFabric.kt` … — siehe §5. |
| #6 | `copilot/deepresearch-p0-durable-agent-memory` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #5 | `copilot/part4-agent-research` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |
| #3 | `copilot/part3-memory-model-ecology` | 0 | (a) | GitHub Compare: ahead=0 gegen #698 (Ancestor oder identisch). |

### Zählung offener PRs nach Klasse (nach den Regeln oben)

| Klasse | Offene PRs |
|---|---:|
| (a) | 402 |
| (b) | 53 |
| (c) | 64 |
| (d) | 0 |
| (e) | 7 |
| Summe | 526 |

Hinweis: Viele (a) bei ahead>0 sind **inhaltlich** enthalten (andere SHAs). Die (b)-Zahl der Autotabelle ist eine Obergrenze; die handgeprüften MAIN-Lücken stehen in §5. Die eigentlichen, heute anschlusswürdigen (b)-Reste nach dieser Runde: #699/#701/#702 (Bot 16 / Bot 04), ui-fixes + #598 (Bot 07), #487 video3-Rest (Bot 04/07), plus die mit (d) markierten M8-Kognitionsdateien für eine spätere Bot-10-Runde.

## 9. Tests

```
node --test scripts/continuum-expert-mode-toolgate.test.mjs scripts/continuum-internet-settings-label.test.mjs
```

Ergebnis lokal 10.11.2026 14:20 Europe/Vienna: **5/5 PASS** (keine Android-JVM-Suite in dieser Box; kein Cinema-QA-Lauf vor dem Push — der Push auf `code52/**` mit Änderungen unter `app/**` soll `continuum-code52-cinematic-qa.yml` auslösen).

## 10. Offene Risiken

- Patch-doppelung mit #706 bei späteren Merges der beiden Cherry-Picks (Inhalt gleich, SHAs neu). Integrator eine Quelle wählen.
- #698 selbst hat noch die schwächere DocumentLearned-Form (`if (!document.hasDocument) return`). Die härtere Isolation liegt in #699/#706, nicht hier (Bot-04-Datei).
- AccountCardFlyIn: Produktionsquelle `accountTools` bleibt bewusst leer („Quelle fehlt“, keine Täuschung). Math-Extension weiterhin unimplementiert.
- 4761 fehlende Nicht-MAIN-Dateien sind fast nur docs/evidence alter Labs; sie wurden nicht als Produktcode übernommen.
- Kein Fold7-Gerätebeweis in dieser Runde. Kein Signieren, kein main-Merge, kein Force-Push, keine Branches gelöscht.

## 11. Nachweiszeile

BOT 01 · Legacy-Inventur aller offenen PRs/Branches + Anschluss #703 · Ausgangs-SHA `a0a69263cdc3c87ed1b728c58188ee9529a78eb4` · geänderte Dateien: ExpertModeConsent.kt, InternetAccessModeLabels.kt, CommandInterpreter.kt, MainActivity.kt, AgentActivity.kt, Tests/Scripts, `.gitignore`, dieses Dokument · Cherry-Picks `ce94c68f4`, `34e7e9e09` plus Docs-Commit · Status gemischt IMPLEMENTIERT ( #703 ) / ANALYSE (Rest).
