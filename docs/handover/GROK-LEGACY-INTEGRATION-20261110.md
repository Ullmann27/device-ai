# GROK Legacy-Integration – Inventur und Anschluss 10.11.2026

BOT 01 · Orphan Hunter + Integration · Auftrag t135u / Gesetz Nr. 1 (nichts liegen lassen).

- Geprüft: 10.11.2026, 14:03–14:20 Europe/Vienna
- Integrationsstand (Basis): `code52/final-integrator-cinema-20261110` **#698** HEAD `a0a69263cdc3c87ed1b728c58188ee9529a78eb4`
- Arbeitsbranch: `code52/grok-bot01-legacy-integration-20261110` (nicht auf #698 gepusht)
- Vorarbeit: `docs/handover/GROK-PHASE-A-NO-ORPHANS-20261110.md` (PR #705), erweitert von code52/* auf **alle** offenen PRs und Branch-Präfixe

**Lückenlos-Nachtrag 14:25 Europe/Vienna:** Abschnitte 12–13 listen jetzt alle **767 Branches** und alle **661 PRs**. Vorher fehlten 122 Branches ohne PR-Head und die 128 gemergten PRs als Einzeltabelle.

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

---

## 12. Lückenlose Branch-Tabelle (alle 767 Remote-Heads)

Stand 10.11.2026 14:25 Europe/Vienna. Jeder Remote-Branch genau einmal. Klasse und Beleg. PRs am selben Head sind mitverkettet.

Zählung Branches: (a) 525, (b) 57, (c) 153, (d) 4, (e) 28, Summe 767.

| Branch | PR(s) | ahead vs #698 | Klasse | Begründung/Beleg |
|---|---|---:|---|---|
| `artifacts/sol61-preview-20261019` | — | 2 | (c) | Artefakt-/Preview-Zweig, kein Produktanschluss. |
| `astra/continuum-0320-visual-integration-20261024` | #117 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #117 (OPEN). |
| `astra/continuum-always-on-web-20261018` | #31 | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #31 (OPEN). |
| `astra/continuum-atomic-paired-checkpoints-20261021` | #47, #45 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #45 (OPEN). |
| `astra/continuum-autonomous-cycle-20261027` | #189 | 5 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #189 (OPEN). |
| `astra/continuum-blacklab-20261019` | #34 | 52 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #34 (OPEN). |
| `astra/continuum-bot-universe-20261108` | #662 | 4 | (a) | CCC1/Coding-Service auf #698 PR #662 (OPEN). |
| `astra/continuum-brain-integration-20261026` | #159 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #159 (OPEN). |
| `astra/continuum-critical-recovery-20261028` | — | 1 | (c) | SHA voraus (ahead=1, behind=2928), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `astra/continuum-dna-guardian-20261018` | #30 | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #30 (OPEN). |
| `astra/continuum-dual-core-binding-20261021` | #44 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #44 (OPEN). |
| `astra/continuum-experiment-charter-20261019` | #42 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #42 (OPEN). |
| `astra/continuum-holo-reference-fidelity-20261108` | #661 | 1 | (c) | Nur Evidenzbilder PR #661 (OPEN). |
| `astra/continuum-holographic-ui-20261019` | #35 | 62 | (b) | SHA voraus; fehlende MAIN: `ContinuumPcmLevel.kt` — siehe §5 PR #35 (OPEN). |
| `astra/continuum-holographic-v2-20261108` | #659 | 10 | (a) | wie #666 PR #659 (OPEN). |
| `astra/continuum-integrated-apk-20261019` | #37 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #37 (OPEN). |
| `astra/continuum-live-hologram-20261023` | #91 | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #91 (OPEN). |
| `astra/continuum-local-coding-milestone-20261021` | #49 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #49 (OPEN). |
| `astra/continuum-offline-public-research-20261018` | #32 | 23 | (c) | Research-Zweig PR #32 (OPEN). |
| `astra/continuum-optional-groq-mentor-20261019` | #39 | 16 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #39 (OPEN). |
| `astra/continuum-research-bridge-20261019` | — | 35 | (c) | SHA voraus (ahead=35, behind=4680), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `astra/continuum-research-learning-gate-20261019` | #33 | 34 | (c) | Research-Zweig PR #33 (OPEN). |
| `astra/continuum-research-verification-20261021` | #46 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #46 (OPEN). |
| `astra/continuum-samsung-research-hotfix-20261019` | #38 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #38 (OPEN). |
| `astra/continuum-visual-engineering-20261023` | #90 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #90 (OPEN). |
| `astra/render-mentor-apk-builder-20261019` | #52, #40 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #52 (OPEN). |
| `astra/runner-isolation-diagnostic-20261019` | — | 1 | (c) | SHA voraus (ahead=1, behind=4600), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `backup/continuum-day-close-20261017-f04f7235` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-main-20261017-190f5eab` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-original-signed-v30-20261031` | — | 14 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr19-20261017-ec4b4cae` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-20261017-4c4b3fd8` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-autonomous-evidence-gate-20261018-2e06c432` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-cefr-prospective-benchmark-20261018-5944aec1` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-cefr-research-20261018-084c083f` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-cegis-handoff-20261018-762cb80a` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-evidence-ledger-cefr-shadow-20261018-7387ae9c` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-four-powers-cefr-20261018-b544bb57` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-acceptance-20261017-e9150e5` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-closed-loop-gate-20261017-f7308898` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-junit-node22-20261017-70a9db83` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-junit-single-read-20261018-33b9c4cb` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-real-e2e-proof-20261017-87313052` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-required-e2e-20261017-62fa6cf0` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-verified-tests-20261017-b9e056cb` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-worker-failclosed-20261017-67de0069` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-m4-xml-proof-hardening-20261018-c1249ae2` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-repair-critic-control-boundary-20261018-7c075c56` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-repair-recursion-report-20261017-eb089bb3` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-secured-20261017-0d62d0aa` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/continuum-pr28-worker-heartbeat-20261017-5d92eb11` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/mentor-apk-before-pr50-update-20261021` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/render-apk-before-astra-integration-20261021` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/render-mentor-before-pr42-delivery-20261020` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `backup/render-mentor-service-before-pr42-20261020` | — | 0 | (c) | Backup-Zweig, Archiv. Nicht mergen, nicht löschen. |
| `bot1/avatar-blink-kinematics-20261105` | #551 | 34 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #551 (OPEN). |
| `bot2/isolated-neon-tile-energy-20261105` | #549 | 9 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #549 (OPEN). |
| `ccr-4f80075a-ycl67y` | #535 | 25 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #535 (OPEN). |
| `chatgpt/cinematic-sustained-hologram-20261109` | #666 | 14 | (a) | Produktfiles auf #698; fehlt nur AvatarReadingContinuityTest PR #666 (OPEN). |
| `chatgpt/code50-acceptance-repair-20261108` | #627 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #627 (MERGED). |
| `chatgpt/code50-airbus-acceptance-tests-20261108` | #622 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #622 (MERGED). |
| `chatgpt/code50-airbus-orchestrator-20261108` | #621 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #621 (MERGED). |
| `chatgpt/code50-airbus2100-camera-gaze-20261108` | #617 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #617 (MERGED). |
| `chatgpt/code50-airbus2100-footprints-20261108` | #610 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #610 (MERGED). |
| `chatgpt/code50-android27-repair-20261108` | #632 | 0 | (a) | Gemergt 2026-11-08 in `chatgpt/code50-qa-staging-20261108`; ahead=0 gegen #698 PR #632 (MERGED). |
| `chatgpt/code50-app-intent-evidence-gate-20261108` | #624 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #624 (MERGED). |
| `chatgpt/code50-app-intent-exact-gate-20261108` | #626 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #626 (MERGED). |
| `chatgpt/code50-app-receipt-gate-20261108` | #628 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #628 (OPEN). |
| `chatgpt/code50-argon4-reasoning-workscene-20261108` | #612 | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt PR #612 (CLOSED). |
| `chatgpt/code50-avatar-proof-result-20261108` | #608 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #608 (MERGED). |
| `chatgpt/code50-context-inplace-tool-20261108` | #616 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #616 (MERGED). |
| `chatgpt/code50-countdown-sidequest-20261108` | — | 2 | (a) | ProactiveSideQuestAndCountdownEngine.kt existiert und ist in Airbus2100CockpitOrchestrator verdrahtet. |
| `chatgpt/code50-countdown-sidequest-current-20261108` | #620 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #620 (MERGED). |
| `chatgpt/code50-deck-footprint-tests-20261108` | #634 | 0 | (a) | Gemergt 2026-11-08 in `chatgpt/code50-qa-staging-20261108`; ahead=0 gegen #698 PR #634 (MERGED). |
| `chatgpt/code50-fold7-base-guards-20261108` | #619 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #619 (MERGED). |
| `chatgpt/code50-fui-live-data-20261108` | #623 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #623 (MERGED). |
| `chatgpt/code50-hollywood-fui-current-20261108` | #618 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #618 (MERGED). |
| `chatgpt/code50-hollywood-fui-foundation-20261108` | #614 | 14 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #614 (OPEN). |
| `chatgpt/code50-local-qa-20261108` | #629 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #629 (OPEN). |
| `chatgpt/code50-local-work-window-20261108` | #609 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #609 (MERGED). |
| `chatgpt/code50-natural-app-intake-20261108` | #606 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #606 (MERGED). |
| `chatgpt/code50-node22-repair-20261108` | #631 | 0 | (a) | Gemergt 2026-11-08 in `chatgpt/code50-qa-staging-20261108`; ahead=0 gegen #698 PR #631 (MERGED). |
| `chatgpt/code50-pdf-pagecount-fui-20261108` | #625 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #625 (MERGED). |
| `chatgpt/code50-qa-staging-20261108` | #630 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #630 (OPEN). |
| `chatgpt/code50-reasoning-live-telemetry-20261108` | #613 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #613 (MERGED). |
| `chatgpt/code50-reasoning-main-scene-20261108` | #611 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #611 (MERGED). |
| `chatgpt/code50-recall-qa-fix-20261108` | #604 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #604 (MERGED). |
| `chatgpt/code50-signierworkflow-registrierung-20261108` | #636 | 1 | (e) | Signierworkflow-Registrierung PR #636 (MERGED). |
| `chatgpt/code50-upstream-reconciled-20261108` | #633 | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt PR #633 (CLOSED). |
| `chatgpt/code50-voice-budget-20261108` | #607 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #607 (MERGED). |
| `chatgpt/code50-warm-speech-recognizer-20261108` | #615 | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698 PR #615 (MERGED). |
| `chatgpt/code51-manual-qa-registration-20261109` | #668 | 3 | (e) | Manual-QA-Registrierung auf Default-Branch / Signierlinie PR #668 (MERGED). |
| `chatgpt/code51-owner-canon-fable51-handoff-20261108` | #644 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #644 (OPEN). |
| `chatgpt/code51-ui-acceptance-recovery-20261109` | #665 | 4 | (a) | UI-Acceptance auf #698 PR #665 (OPEN). |
| `chatgpt/code51-voice-prosody-recovery-20261109` | #664 | 3 | (a) | TTS-Prosodie in JarvisVoiceProfile vorhanden PR #664 (OPEN). |
| `chatgpt/code52-argon4-holographic-rim-20261109` | #677 | 0 | (a) | Ancestor PR #677 (OPEN). |
| `chatgpt/code52-ci-coverage-20261109` | #676 | 0 | (a) | Gemergt 2026-11-09 in `chatgpt/code52-unified-integration-20261109`; ahead=0 gegen #698 PR #676 (MERGED). |
| `chatgpt/code52-cinema-avatar-finesse-20261109` | #678 | 0 | (a) | Gemergt 2026-11-09 in `chatgpt/code52-argon4-holographic-rim-20261109`; ahead=0 gegen #698 PR #678 (MERGED). |
| `chatgpt/code52-cinema-branch-safe-qa-20261110` | #685 | 0 | (a) | Ancestor PR #685 (OPEN). |
| `chatgpt/code52-cinema-document-learning-isolation-20261110` | #701 | 11 | (b) | MERGED in #699. DocumentLearned-Isolation. Fehlt in #698-Spitze; in #706. Bot-04-Datei PR #701 (MERGED). |
| `chatgpt/code52-cinema-final-graphics-audit-20261110` | #683 | 0 | (a) | Ancestor PR #683 (OPEN). |
| `chatgpt/code52-cinema-grounded-document-question-20261110` | #684 | 0 | (a) | Ancestor PR #684 (OPEN). |
| `chatgpt/code52-cinema-owner-signer-candidate-20261110` | #682 | 13 | (e) | Älterer Signer-Kandidat PR #682 (OPEN). |
| `chatgpt/code52-cinema-prism-pixel-fix-20261110` | #681 | 0 | (a) | Ancestor PR #681 (OPEN). |
| `chatgpt/code52-cinema-prism-shader-20261109` | #679 | 0 | (a) | Gemergt 2026-11-09 in `chatgpt/code52-argon4-holographic-rim-20261109`; ahead=0 gegen #698 PR #679 (MERGED). |
| `chatgpt/code52-cinema-switch-contract-20261109` | #680 | 0 | (a) | Ancestor PR #680 (OPEN). |
| `chatgpt/code52-fold7-inference-truth-20261109` | #669 | 0 | (a) | Ancestor PR #669 (OPEN). |
| `chatgpt/code52-fold7-thermal-budget-guard-20261110` | #695 | 2 | (a) | Inhalt über #697 gleichwertig in Cinema PR #695 (OPEN). |
| `chatgpt/code52-intent-regression-20261109` | #673 | 0 | (a) | Gemergt 2026-11-09 in `chatgpt/code52-unified-integration-20261109`; ahead=0 gegen #698 PR #673 (MERGED). |
| `chatgpt/code52-owner-signing-20261109` | #675 | 10 | (e) | Owner-Signing-Registrierung, unrelated (behind=5613) PR #675 (MERGED). |
| `chatgpt/code52-project-isolation-20261109` | #672 | 7 | (a) | Inhalt über #671 auf #698 vorhanden PR #672 (OPEN). |
| `chatgpt/code52-swarm-document-lifecycle-fix-20261110` | #690 | 11 | (a) | Produktkern über #697+#701; Spitze würde kollidieren PR #690 (OPEN). |
| `chatgpt/code52-unified-integration-20261109` | #671 | 0 | (a) | Ancestor von aktuellem #698 PR #671 (OPEN). |
| `chatgpt/continuum-0320-v21-release-review-20261024` | #118 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #118 (OPEN). |
| `chatgpt/continuum-0321-fold7-orb-runtime-diagnostics-20261024` | #119 | 0 | (a) | Gemergt 2026-10-24 in `chatgpt/continuum-0320-v21-release-review-20261024`; ahead=0 gegen #698 PR #119 (MERGED). |
| `chatgpt/continuum-0321-local-dialogue-quality-20261024` | #120 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #120 (OPEN). |
| `chatgpt/continuum-apk-preview-20261021` | #51 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #51 (OPEN). |
| `chatgpt/continuum-app-navigation-completion-20261022` | #64 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #64 (OPEN). |
| `chatgpt/continuum-argon-qf2-source-proof-20261022` | #70 | 27 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #70 (OPEN). |
| `chatgpt/continuum-autonomous-dialogue-research-20261024` | #115 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #115 (OPEN). |
| `chatgpt/continuum-bundled-quality-qf2-20261022` | #67 | 22 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #67 (OPEN). |
| `chatgpt/continuum-ccc1-evidence-completeness-20261026` | #170 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #170 (OPEN). |
| `chatgpt/continuum-cdx1-legacy-v30-20261030` | #267 | 15 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #267 (OPEN). |
| `chatgpt/continuum-cdx1-postrun-audit-20261024` | #122 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #122 (OPEN). |
| `chatgpt/continuum-checked-shell-bounds-20261027` | #180 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #180 (OPEN). |
| `chatgpt/continuum-coding-invalid-feedback-20261024` | #126 | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #126 (OPEN). |
| `chatgpt/continuum-coding-outcome-journal-20261027` | #185 | 3 | (c) | Research-Zweig PR #185 (OPEN). |
| `chatgpt/continuum-coding-rejection-feedback-20261024` | #127 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #127 (OPEN). |
| `chatgpt/continuum-core-mirror-20261024` | #121 | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #121 (OPEN). |
| `chatgpt/continuum-csx1-research-gate-20261022` | #60 | 15 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #60 (OPEN). |
| `chatgpt/continuum-evidence-switch-probe-20261027` | #194 | 13 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #194 (OPEN). |
| `chatgpt/continuum-feedback-process-safety-20261026` | #176 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #176 (OPEN). |
| `chatgpt/continuum-fold7-single-pass-release-20261030` | #266 | 12 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #266 (OPEN). |
| `chatgpt/continuum-fold7-usable-navigation-20261027` | #190 | 0 | (a) | Gemergt 2026-10-27 in `integration/continuum-owner-v26-live-candidate-20261027`; ahead=0 gegen #698 PR #190 (MERGED). |
| `chatgpt/continuum-fusion-memory-vertical-20261028` | #203 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #203 (OPEN). |
| `chatgpt/continuum-fusion-regression-gate-20261028` | #204 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #204 (OPEN). |
| `chatgpt/continuum-gemma-reopen-20261027` | #193 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #193 (OPEN). |
| `chatgpt/continuum-german-research-contradiction-20261028` | #205 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #205 (OPEN). |
| `chatgpt/continuum-hologram-context-recovery-20261024` | #113 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #113 (OPEN). |
| `chatgpt/continuum-i1-model-binding-20261022` | #68 | 35 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #68 (OPEN). |
| `chatgpt/continuum-intelligence-lab-readonly-20261022` | #59 | 12 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #59 (OPEN). |
| `chatgpt/continuum-jarvis-research-connection-audit-20261027` | #181 | 2 | (c) | Research-Zweig PR #181 (OPEN). |
| `chatgpt/continuum-layout-pixel-alignment-20261023` | #83 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #83 (OPEN). |
| `chatgpt/continuum-live-hologram-device-fix-20261024` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `chatgpt/continuum-llama-recovery-20261021` | #50 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #50 (OPEN). |
| `chatgpt/continuum-local-mainapp-qa-m3-20261027` | — | 3 | (c) | SHA voraus (ahead=3, behind=3026), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `chatgpt/continuum-local-worker-learning-lab-20261026` | #177 | 23 | (c) | Research-Zweig PR #177 (OPEN). |
| `chatgpt/continuum-m2-20-gates-20261027` | #192, #191 | 0 | (a) | Gemergt 2026-10-27 in `integration/continuum-owner-v26-live-candidate-20261027`; ahead=0 gegen #698 PR #192 (MERGED). |
| `chatgpt/continuum-m5-draft-approval-guard-20261024` | #110 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #110 (OPEN). |
| `chatgpt/continuum-m6-independent-holdout-20261030` | #258 | 244 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #258 (OPEN). |
| `chatgpt/continuum-m6-run-binding-preflight-20261025` | #130 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #130 (OPEN). |
| `chatgpt/continuum-m7-release-tests-repair-20261030` | — | 1 | (c) | SHA voraus (ahead=1, behind=2744), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `chatgpt/continuum-m8-benchmark-coverage-20261030` | #259 | 167 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #259 (OPEN). |
| `chatgpt/continuum-m8-gvisor-mcp-offline-20261030` | — | 167 | (d) | M8-Parallellinie, dieselben 13 fehlenden MAIN-Dateien wie §5. |
| `chatgpt/continuum-mainapp-m3-after-sol-20261027` | #197 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #197 (OPEN). |
| `chatgpt/continuum-master-integration-v19-20261024` | #111 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #111 (OPEN). |
| `chatgpt/continuum-memory-retrieval-no-false-evidence-20261022` | #66 | 14 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #66 (OPEN). |
| `chatgpt/continuum-mentor-update-20261021` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `chatgpt/continuum-navigation-signed-delivery-20261022` | #65 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #65 (OPEN). |
| `chatgpt/continuum-one-tap-updates-20261030` | #260 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #260 (OPEN). |
| `chatgpt/continuum-opus-android-coding-ui-20261022` | #58 | 13 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #58 (OPEN). |
| `chatgpt/continuum-owner-0.3.19-hologram-20261024` | #114 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #114 (MERGED). |
| `chatgpt/continuum-owner-current-qa-apk-20261027` | #195 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #195 (OPEN). |
| `chatgpt/continuum-p0-strict-release-metadata-20261031` | #278 | 17 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #278 (OPEN). |
| `chatgpt/continuum-p0-updater-hardening-20261030` | #257 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #257 (OPEN). |
| `chatgpt/continuum-release-integration-audit-20261022` | #69 | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #69 (OPEN). |
| `chatgpt/continuum-render-delivery-audit-20261022` | #55 | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #55 (OPEN). |
| `chatgpt/continuum-render-readback-20261031` | #279 | 21 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #279 (OPEN). |
| `chatgpt/continuum-research-integration-memory-m4-20261027` | #198 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #198 (OPEN). |
| `chatgpt/continuum-residual-pointer-built-20261022` | #61 | 21 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #61 (OPEN). |
| `chatgpt/continuum-typed-android-coding-bridge-20261024` | #128 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #128 (OPEN). |
| `chatgpt/continuum-typed-coding-isolation-recovery-20261025` | #129 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #129 (OPEN). |
| `chatgpt/continuum-upstream-first-20261030` | #261 | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #261 (OPEN). |
| `chatgpt/fold7-reference-exact-grid-20261027` | #186 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #186 (OPEN). |
| `claude/cdx1-root-cause-kxrxy2` | — | 13 | (c) | SHA voraus (ahead=13, behind=2740), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `claude/continuum-fable51-continuation-20261106` | #574 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #574 (OPEN). |
| `claude/continuum-fable51-visual-20261106` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `claude/continuum-fable51-voice-20261106` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `claude/continuum-jarvis-cockpit-z7bo60` | #565 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #565 (OPEN). |
| `claude/continuum-jarvis-exp20-p2-kxrxy2` | #256 | 217 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #256 (OPEN). |
| `claude/ewm-owner-subject-fix-kxrxy2` | — | 214 | (d) | M8/EWM-Parallellinie, dieselben fehlenden MAIN-Dateien wie §5. |
| `claude/m7-m8-integration-kxrxy2` | #265 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #265 (OPEN). |
| `claude/m8-coding-agent-loop-kxrxy2` | — | 162 | (d) | M8 Coding-Agent-Loop, dieselben fehlenden MAIN-Dateien wie §5. |
| `claude/m8-gvisor-executor-kxrxy2` | #264 | 185 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #264 (OPEN). |
| `code52/avatar-gaze-light-20261110` | #694 | 0 | (a) | MERGED, Ancestor PR #694 (MERGED). |
| `code52/cinema-avatar-20261110` | #688 | 0 | (a) | Gemergt 2026-11-10 in `integration/code52-swarm-20261110`; ahead=0 gegen #698 PR #688 (MERGED). |
| `code52/cinema-flyin-20261110` | #692 | 0 | (a) | Ancestor; Weiterarbeit Bot 04 PR #692 (OPEN). |
| `code52/cinema-native-qa-review-fixes-20261110` | #699 | 12 | (b) | QA/#701-Reparaturen; Teil von #706; Bot-04-Datei PR #699 (OPEN). |
| `code52/cinema-original-signer-qa-binding-20261110` | #700 | 11 | (e) | Signer #700. |
| `code52/cockpit-20261110` | #689 | 0 | (a) | Gemergt 2026-11-10 in `integration/code52-swarm-20261110`; ahead=0 gegen #698 PR #689 (MERGED). |
| `code52/cockpit-fidelity-20261110` | #691 | 0 | (a) | Ancestor; Phone/dialogTop Bot 07 PR #691 (OPEN). |
| `code52/final-integrator-cinema-20261110` | #698 | 0 | (a) | Ausgangsstand #698 / a0a69263. |
| `code52/final-integrator-thermal-20261110` | #697 | 0 | (a) | Ancestor (Thermal) PR #697 (OPEN). |
| `code52/final-integrator-ui-fixes-20261110` | — | 2 | (b) | Commits fbe35a7de (dialogTop/Live-Dialog) und 3ba8fc4f6. Für Bot 07 (JarvisShellLayout). |
| `code52/fui-worlds-20261110` | #687 | 0 | (a) | Gemergt 2026-11-10 in `integration/code52-swarm-20261110`; ahead=0 gegen #698 PR #687 (MERGED). |
| `code52/grok-bot13-expert-mode-qa-20261110` | — | 14 | (b) | code52-Kopie von #703 inkl. #699-Ancestors. Expert-Mode-Anteil hier bereits cherry-picked. |
| `code52/grok-bot15-node-reliability-20261110` | — | 15 | (b) | code52-Kopie von #702 / Bot 15. Orchestrator = Bot 04; Inhalt in #706. |
| `code52/grok-bot16-signer-rc-binding-20261110` | — | 13 | (e) | Signer-Bindung auf eigener Historie (behind=5613). |
| `code52/grok-release-candidate-0351-20261110` | #706 | 19 | (b) | Bot-16 #706. |
| `code52/visual-depth-ref-d-20261110` | #696 | 0 | (a) | Ancestor (Tiefe); Weiterarbeit Bot 06 PR #696 (OPEN). |
| `code52/visual-style-20261110` | #693 | 2 | (c) | Nur leere Merge-Commits; Inhalt in #696/#698 PR #693 (OPEN). |
| `coderabbit/continuum-brain-integration-20261102` | #354 | 2 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #354 (OPEN). |
| `coderabbit/continuum-reliability-sprint-20261102` | #353 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #353 (OPEN). |
| `coderabbit/get-device-ai-project-status/de3d7eb3` | #352 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #352 (OPEN). |
| `codex/airbus2100-hollywood-fui-20261108` | — | 14 | (a) | HollywoodFuiConstruct/StageOverlay auf #698 vorhanden (Cinema-Nachfolger). |
| `codex/astra-jarvis-takeover-20261104` | #491 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #491 (OPEN). |
| `codex/bot-genesis-chief-20261103` | #439 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #439 (OPEN). |
| `codex/breakthrough-autonomy-20261103` | #400 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #400 (MERGED). |
| `codex/breakthrough-cognition-20261103` | #398 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #398 (MERGED). |
| `codex/breakthrough-memory-20261103` | #399 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #399 (MERGED). |
| `codex/breakthrough-neural-20261103` | #402 | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #402 (OPEN). |
| `codex/breakthrough-research-20261103` | #401 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #401 (MERGED). |
| `codex/chief-release-harness-20261103` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `codex/code50-gate-fixes-20261108` | #605 | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt PR #605 (CLOSED). |
| `codex/continuum-avatar-alive-20261104` | #526 | 14 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #526 (OPEN). |
| `codex/continuum-avatar-dialog-finish-20261105` | #545 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #545 (OPEN). |
| `codex/continuum-avatar-live-proof-20261107` | #599 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #599 (OPEN). |
| `codex/continuum-avatar-natural-blink-20261105` | #540 | 26 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #540 (OPEN). |
| `codex/continuum-bot-activation-20261104` | #510 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #510 (OPEN). |
| `codex/continuum-branch-integration-bots-20261104` | #515 | 32 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #515 (OPEN). |
| `codex/continuum-build46-47-help-20261106` | #577 | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #577 (OPEN). |
| `codex/continuum-code47-apk-20261107` | #588 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #588 (OPEN). |
| `codex/continuum-code47-integration-20261106` | #576 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #576 (OPEN). |
| `codex/continuum-code47-qa-candidate-20261106` | #578 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #578 (OPEN). |
| `codex/continuum-code47-release-20261106` | #587 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #587 (OPEN). |
| `codex/continuum-code48-connected-cinema-20261107` | #590 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #590 (OPEN). |
| `codex/continuum-code48-fullstage-cinema-20261107` | #591 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #591 (OPEN). |
| `codex/continuum-code48-github-owner-20261107` | #595 | 9 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #595 (OPEN). |
| `codex/continuum-code48-recovered-active-paths-20261107` | #592 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #592 (OPEN). |
| `codex/continuum-code48-release-completion-20261107` | #593 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #593 (OPEN). |
| `codex/continuum-code48-workscene-thermal-20261107` | #589 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #589 (OPEN). |
| `codex/continuum-code49-elevenlabs-voice-design-20261107` | #596 | 1 | (c) | Nur ElevenLabs-Docs; Cloud nur mit Freigabe PR #596 (OPEN). |
| `codex/continuum-code49-orb-render-performance-20261107` | #594 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #594 (OPEN). |
| `codex/continuum-code49-reference-input-20261107` | #598 | 4 | (b) | Reference-Input-Test fehlt; Layout für Bot 07 PR #598 (OPEN). |
| `codex/continuum-code50-integration-20261107` | #603 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #603 (OPEN). |
| `codex/continuum-cognitive-state-shadow-20261107` | #600 | 7 | (a) | Ctx3StateTelegram auf #698 PR #600 (OPEN). |
| `codex/continuum-dark-reference-layout-20261107` | #597 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #597 (OPEN). |
| `codex/continuum-discourse-shadow-20261107` | — | 20 | (c) | Wie #601: Ctx3DiscourseState durch resolveOrderedDeictic ersetzt. |
| `codex/continuum-discourse-shadow-v2-20261107` | #601 | 12 | (c) | Teilmenge von #602 PR #601 (OPEN). |
| `codex/continuum-fold7-field-issues-20261106` | — | 2 | (a) | PDF-öffnen liegt in OwnerDocumentIntent (öffne+pdf) der späteren Document-Linie. |
| `codex/continuum-fold7-quality-lab-20261106` | #573 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #573 (OPEN). |
| `codex/continuum-independence-chief-20261103` | #456 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #456 (OPEN). |
| `codex/continuum-internal-bot-training-20261104` | #516 | 11 | (a) | ChiefBot* auf #698 verdrahtet PR #516 (OPEN). |
| `codex/continuum-neon-finish-20261105` | #546 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #546 (OPEN). |
| `codex/continuum-neon-tiles-20261105` | #541 | 26 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #541 (OPEN). |
| `codex/continuum-orb-nonfinite-qa-20261105` | #542 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #542 (OPEN). |
| `codex/continuum-owner-apk-20261106` | #575 | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #575 (OPEN). |
| `codex/continuum-owner-face-models-20261105` | #552 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #552 (OPEN). |
| `codex/continuum-reference-avatar-20261104` | #518 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #518 (OPEN). |
| `codex/continuum-reference-avatar-v2-20261104` | #521 | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #521 (OPEN). |
| `codex/continuum-role-resolution-shadow-20261107` | #602 | 17 | (c) | Ersetzt durch EntityRoleResolutionEngine + resolveOrderedDeictic PR #602 (OPEN). |
| `codex/continuum-three-avatar-helpers-20261104` | #495 | 17 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #495 (OPEN). |
| `codex/continuum47-app-query-format-20261106` | #582 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #582 (OPEN). |
| `codex/continuum47-context-contract-20261106` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `codex/continuum47-context-identity-20261106` | #580 | 0 | (a) | Gemergt 2026-11-06 in `codex/continuum-code47-qa-candidate-20261106`; ahead=0 gegen #698 PR #580 (MERGED). |
| `codex/continuum47-fallback-identity-contract-20261106` | #585 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #585 (OPEN). |
| `codex/continuum47-natural-actions-20261106` | #579 | 0 | (a) | Gemergt 2026-11-06 in `codex/continuum-code47-qa-candidate-20261106`; ahead=0 gegen #698 PR #579 (MERGED). |
| `codex/continuum47-pdf-word-order-20261106` | #584 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #584 (OPEN). |
| `codex/continuum47-planning-context-regression-20261106` | #586 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #586 (OPEN). |
| `codex/continuum47-response-completion-20261106` | #581 | 0 | (a) | Gemergt 2026-11-06 in `codex/continuum-code47-qa-candidate-20261106`; ahead=0 gegen #698 PR #581 (MERGED). |
| `codex/continuum47-routing-regressions-20261106` | #583 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #583 (OPEN). |
| `codex/delta-challenger-20261103` | #407 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #407 (MERGED). |
| `codex/delta-pool-completion-20261103` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `codex/epsilon-android-20261103` | #405 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #405 (MERGED). |
| `codex/gamma-challenger-20261103` | #406 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #406 (MERGED). |
| `codex/generation16-readonly-rescue-20261029` | #219 | 10 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #219 (OPEN). |
| `codex/generation16-rescue-bom-bound-20261029` | #222 | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #222 (OPEN). |
| `codex/genesis-brain-20261103` | #448 | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698 PR #448 (MERGED). |
| `codex/genesis-brain-review-20261103` | #438 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #438 (OPEN). |
| `codex/genesis-caller-review-20261103` | #450 | 1 | (c) | Genesis caller review, Forschungs-/Repair-Notiz PR #450 (MERGED). |
| `codex/genesis-coding-review-20261103` | #443 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #443 (OPEN). |
| `codex/genesis-final-native-review-20261103` | #455 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #455 (OPEN). |
| `codex/genesis-native-runtime-20261103` | #440 | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698 PR #440 (MERGED). |
| `codex/genesis-ui-review-20261103` | #446, #437 | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698 PR #446 (MERGED). |
| `codex/genesis-workspace-review-20261103` | #451, #441 | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698 PR #451 (MERGED). |
| `codex/genesis-workspaces-20261103` | #444 | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698 PR #444 (MERGED). |
| `codex/independence-failure-review-20261103` | #471 | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #471 (OPEN). |
| `codex/independence-final-qa-20261103` | #462 | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698 PR #462 (MERGED). |
| `codex/independence-mcp-20261103` | #459 | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698 PR #459 (MERGED). |
| `codex/independence-mcp-integration-check-20261103` | — | 4 | (c) | SHA voraus (ahead=4, behind=1520), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `codex/independence-mcp-review-20261103` | #460 | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698 PR #460 (MERGED). |
| `codex/independence-native-closure-20261103` | #472 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #472 (OPEN). |
| `codex/independence-native-failure-review-20261103` | #473 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #473 (OPEN). |
| `codex/independence-native-review-20261103` | #461 | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698 PR #461 (MERGED). |
| `codex/independence-service-20261103` | #463 | 3 | (c) | Independence CCC1-Service; heutiger coding-service ist in der späteren Linie PR #463 (MERGED). |
| `codex/independence-service-review-20261103` | #458 | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698 PR #458 (MERGED). |
| `codex/jarvis-camera-flight-20261106` | #571 | 6 | (a) | AvatarCamera* auf #698 PR #571 (OPEN). |
| `codex/jarvis-reference-review-20261103` | #468, #464, #434 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #468 (MERGED). |
| `codex/jarvis-reference-ui-20261103` | #432 | 1 | (a) | Avatar-Motion/Mund; heutiger Filament/Cinema-Pfad enthält Nachfolger PR #432 (MERGED). |
| `codex/living-jarvis-3d-20261103` | #404 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #404 (MERGED). |
| `codex/momega1-android-20261103` | #442, #414 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #442 (MERGED). |
| `codex/momega1-android-plan-review-20261103` | #435 | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #435 (OPEN). |
| `codex/momega1-android-review-20261103` | #419 | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #419 (OPEN). |
| `codex/momega1-avatar-effects-review-20261103` | #454 | 11 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #454 (OPEN). |
| `codex/momega1-challenger-20261103` | #452, #430, #416 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #452 (MERGED). |
| `codex/momega1-chief-20261103` | #410 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #410 (OPEN). |
| `codex/momega1-cli-fixtures-20261103` | #469 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #469 (OPEN). |
| `codex/momega1-effects-20261103` | #465 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #465 (MERGED). |
| `codex/momega1-effects-caller-20261103` | #457 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #457 (MERGED). |
| `codex/momega1-gvisor-fixture-20261103` | #467 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #467 (OPEN). |
| `codex/momega1-layout-final-20261103` | #466 | 7 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #466 (OPEN). |
| `codex/momega1-loop-20261103` | #411 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #411 (MERGED). |
| `codex/momega1-memory-20261103` | #415 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #415 (MERGED). |
| `codex/momega1-memory-review-20261103` | #418 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #418 (MERGED). |
| `codex/momega1-portrait-rings-20261103` | #470 | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #470 (OPEN). |
| `codex/momega1-qa-20261103` | #413 | 51 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #413 (OPEN). |
| `codex/momega1-readiness-20261103` | #436 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #436 (MERGED). |
| `codex/momega1-readiness-review-20261103` | #445 | 8 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #445 (OPEN). |
| `codex/momega1-runtime-20261103` | #449, #427, #412 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #449 (MERGED). |
| `codex/momega1-spoken-guide-20261103` | #447 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #447 (MERGED). |
| `codex/momega1-visible-avatar-20261103` | #453 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #453 (MERGED). |
| `codex/momega1-wiring-tests-20261103` | — | 4 | (c) | SHA voraus (ahead=4, behind=1575), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `codex/momega1-workspace-review-20261103` | #420 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #420 (MERGED). |
| `codex/momega1-workspaces-20261103` | #417 | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698 PR #417 (MERGED). |
| `codex/synapse-lifecycle-20261103` | #425 | 1 | (a) | Shutdown-Reports; spätere Chief-Linie hat Nachfolger oder Inhalt portiert (ahead=1 oft Docs) PR #425 (MERGED). |
| `codex/synapse-lifecycle-review-20261103` | #424 | 0 | (a) | Gemergt 2026-11-03 in `codex/synapse-swarm-chief-20261103`; ahead=0 gegen #698 PR #424 (MERGED). |
| `codex/synapse-model-prerequisites-20261103` | #433 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #433 (OPEN). |
| `codex/synapse-p0-audit-20261103` | #421 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #421 (OPEN). |
| `codex/synapse-registry-20261103` | #426 | 0 | (a) | Gemergt 2026-11-03 in `codex/synapse-swarm-chief-20261103`; ahead=0 gegen #698 PR #426 (MERGED). |
| `codex/synapse-registry-review-20261103` | #422 | 0 | (a) | Gemergt 2026-11-03 in `codex/synapse-swarm-chief-20261103`; ahead=0 gegen #698 PR #422 (MERGED). |
| `codex/synapse-runtime-audit-20261103` | #431 | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #431 (OPEN). |
| `codex/synapse-sources-20261103` | #429 | 0 | (a) | Gemergt 2026-11-03 in `codex/synapse-swarm-chief-20261103`; ahead=0 gegen #698 PR #429 (MERGED). |
| `codex/synapse-swarm-chief-20261103` | #423 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #423 (OPEN). |
| `codex/synapse-ui-review-20261103` | #428 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #428 (OPEN). |
| `codex/team-01-intelligence` | — | 4 | (c) | SHA voraus (ahead=4, behind=5613), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `codex/team-01-intelligence-omega-20261103` | #394 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #394 (OPEN). |
| `codex/team-02-memory` | — | 4 | (c) | SHA voraus (ahead=4, behind=5613), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `codex/team-02-memory-omega-20261103` | #397 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #397 (OPEN). |
| `codex/team-03-ccc1` | — | 4 | (c) | SHA voraus (ahead=4, behind=5613), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `codex/team-03-ccc1-omega-20261103` | #393 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #393 (OPEN). |
| `codex/team-04-android` | — | 4 | (c) | SHA voraus (ahead=4, behind=5613), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `codex/team-04-android-omega-20261103` | #395 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #395 (OPEN). |
| `codex/team-05-quality` | — | 4 | (c) | SHA voraus (ahead=4, behind=5613), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `codex/team-05-quality-breakthrough-20261103` | #403 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #403 (MERGED). |
| `codex/team-05-quality-harness-20261103` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `codex/team-05-quality-omega-20261103` | #396 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #396 (OPEN). |
| `codex/team-chief/omega-20261103` | #392 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #392 (OPEN). |
| `codex/verify-cockpit-centre-windows-20261106` | #572 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #572 (OPEN). |
| `codex/worker-descendant-stop-20261029` | #220 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #220 (OPEN). |
| `codex/worker-prestart-lease-path-20261029` | #225 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #225 (OPEN). |
| `codex/zeta-challenger-20261103` | #409 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #409 (MERGED). |
| `codex/zeta-ui-20261103` | #408 | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698 PR #408 (MERGED). |
| `codex/zeta-ui-node-20261103` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `config/continuum-deepseek-gemma-selection-20261025` | #146 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #146 (OPEN). |
| `continuum-agent-state` | — | — | (e) | Kein gemeinsamer Ancestor mit #698 (GitHub 404). Nicht mergen. |
| `continuum-agent/activate-hourly-developer-agent` | #22 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #22 (OPEN). |
| `continuum-agent/demo/38696111909/fault-kt-historical-escaped-newline-48bd150` | — | 1 | (c) | Repair-/Demo-Worktree des Continuum-Agenten, kein Produktmerge. |
| `continuum-agent/demo/38696111909/repair-kt-historical-escaped-newline-48bd150` | — | 2 | (c) | Repair-/Demo-Worktree des Continuum-Agenten, kein Produktmerge. |
| `continuum-agent/repair-0bf848a1be-ad28fd1d` | — | 1 | (c) | Repair-/Demo-Worktree des Continuum-Agenten, kein Produktmerge. |
| `continuum-agent/repair-22582016d3-e2cd043b` | #21 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #21 (OPEN). |
| `continuum-agent/repair-9e1412ed3f-2f132248` | — | 12 | (c) | Repair-/Demo-Worktree des Continuum-Agenten, kein Produktmerge. |
| `continuum-agent/repair-ec14c4b94e-a82e4caf` | — | 1 | (c) | Repair-/Demo-Worktree des Continuum-Agenten, kein Produktmerge. |
| `continuum-state` | — | — | (e) | Kein gemeinsamer Ancestor mit #698 (GitHub 404). Nicht mergen. |
| `continuum/argon-safety-review-20261017` | #28 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #28 (OPEN). |
| `continuum/backlog-agent-v2-20261015` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `continuum/contrastive-recovery-20261015` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `continuum/grok1-integration-20261103` | #374 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #374 (OPEN). |
| `continuum/grok1-routines-a2a-20261103` | #376 | 0 | (a) | Gemergt 2026-11-03 in `integration/continuum-grok1-369-372-20261103`; ahead=0 gegen #698 PR #376 (MERGED). |
| `continuum/haupt-chief-pattern-20261103` | #379 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #379 (OPEN). |
| `continuum/m1-grok-multiturn-routing-20261103` | #369 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #369 (OPEN). |
| `continuum/m2-calc-events` | #484 | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #484 (OPEN). |
| `continuum/m2-cognitive-memory` | #371 | 18 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #371 (OPEN). |
| `continuum/m2-grok-context-state-20261103` | #370 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #370 (OPEN). |
| `continuum/m3-grok-selbstrekonstruktion-20261103` | #373 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #373 (OPEN). |
| `continuum/m5-integration-repair` | #372 | 19 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #372 (OPEN). |
| `continuum/m5-safe-flows-fg-20261104` | #485 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #485 (OPEN). |
| `coordination/astra-nucleus-20261104-1027` | #498 | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #498 (OPEN). |
| `coordination/codex-six-agent-team-20261103` | #389 | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #389 (OPEN). |
| `copilot/add-benchmark-measurement-harness` | #16 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #16 (OPEN). |
| `copilot/add-deterministic-jvm-unit-tests` | #14 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #14 (OPEN). |
| `copilot/code51-master-integrator-20261108` | #663 | 0 | (a) | Ancestor PR #663 (OPEN). |
| `copilot/code52-fold7-inference-qa-20261109` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `copilot/continuum-agent-repair-demo` | — | 1 | (c) | SHA voraus (ahead=1, behind=5346), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `copilot/continuum-android-integration-20261015` | #24 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #24 (OPEN). |
| `copilot/continuum-argon-source-review-20261015` | — | 32 | (c) | SHA voraus (ahead=32, behind=5289), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `copilot/continuum-atlas-provenance-gate-20261020` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `copilot/continuum-contrastive-recovery-20261015` | #23 | 0 | (a) | Gemergt 2026-10-15 in `copilot/pr-12-working-first-vertical-slice`; ahead=0 gegen #698 PR #23 (MERGED). |
| `copilot/continuum-feedback-ledger-20261015` | #25 | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #25 (OPEN). |
| `copilot/continuum-owner-answer-corrections-20261015` | #26 | 25 | (b) | SHA voraus; fehlende MAIN: `ContinuumAnswerCorrection.kt` — siehe §5 PR #26 (OPEN). |
| `copilot/continuum-phase1-direct` | #10 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #10 (OPEN). |
| `copilot/continuum-phase1-runtime` | #8 | 2 | (b) | SHA voraus; fehlende MAIN: `AiContextCompiler.kt`, `BenchmarkHarness.kt`, `CognitiveRaid.kt` … — siehe §5 PR #8 (OPEN). |
| `copilot/continuum-phase2-adaptive` | — | 4 | (c) | SHA voraus (ahead=4, behind=5569), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `copilot/continuum-phase2-intelligence-direct` | #11 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #11 (OPEN). |
| `copilot/continuum-selective-correction-revocation-20261015` | #27 | 28 | (b) | SHA voraus; fehlende MAIN: `ContinuumAnswerCorrection.kt` — siehe §5 PR #27 (OPEN). |
| `copilot/copilotcontinuum-phase2-intelligence-direct` | #12 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #12 (OPEN). |
| `copilot/deepresearch-p0-durable-agent-memory` | #6 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #6 (OPEN). |
| `copilot/fix-64243701-1415878393-f443f816-2b3d-4c73-8097-d7df0cb722ca` | #17 | 7 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #17 (OPEN). |
| `copilot/inspect-extend-compute-distributed-task-code` | #18 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #18 (OPEN). |
| `copilot/main-task-review` | #2 | 0 | (a) | Gemergt 2026-10-12 in `main`; ahead=0 gegen #698 PR #2 (MERGED). |
| `copilot/part3-memory-model-ecology` | #3 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #3 (OPEN). |
| `copilot/part4-agent-research` | #5 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #5 (OPEN). |
| `copilot/pr-12-working-first-vertical-slice` | #19 | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt PR #19 (CLOSED). |
| `deepresearch/p0-durable-agent-memory` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `design/continuum-avatar-user-ui-20261103` | #391 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #391 (OPEN). |
| `design/continuum-reference-cockpit-20261104` | #476 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #476 (OPEN). |
| `design/jarvis-cockpit-home-20261104` | #481 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #481 (OPEN). |
| `design/jarvis-menu-navigation-20261104` | #486 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #486 (OPEN). |
| `development/continuum-backlog-20261015` | — | 2 | (c) | SHA voraus (ahead=2, behind=5340), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `docs/code52-handover-chatgpt-20261110` | — | 2 | (a) | Nur Handover-Docs (CHECKPOINT/HANDOVER 10.11.). Kein Produktorphan. |
| `docs/device-ai-open-source-reference-pack` | #4 | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt PR #4 (CLOSED). |
| `evidence/continuum-owner-code42-20261105` | — | 1 | (c) | SHA voraus (ahead=1, behind=983), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `evidence/continuum-p0-525fb04b-20261103` | — | 13 | (c) | SHA voraus (ahead=13, behind=2186), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `experiment/continuum-avatar-lab-offline-20261101` | #338 | 13 | (b) | SHA voraus; fehlende MAIN: `ContinuumAvatarLabActivity.kt` — siehe §5 PR #338 (OPEN). |
| `experiment/continuum-synapse-zero-20261022` | #71 | 6 | (b) | SHA voraus; fehlende MAIN: `ContinuumSynapseZeroShadow.kt`, `SynapseContrastiveRoleProbe.kt` — siehe §5 PR #71 (OPEN). |
| `fable/code51-fui-cinematic-construct-20261108` | #651 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #651 (OPEN). |
| `fable/code51-p0-speech-recovery-20261108` | #646 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #646 (OPEN). |
| `fable/code51-p1-chat-web-research-20261108` | #656 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #656 (OPEN). |
| `fable/code51-p1-internal-navigation-routing-20261108` | #654 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #654 (OPEN). |
| `fable/code51-p1-memory-meta-separation-20261108` | #657 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #657 (OPEN). |
| `feat/continuum-inworld-router-20261104` | #504 | — | (c) | SHELVED Inworld-Mentor PR #504 (CLOSED). |
| `feat/continuum-memory-brain-20261104` | #494 | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #494 (OPEN). |
| `feat/continuum-personality-bots-20261104` | #493 | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #493 (OPEN). |
| `feature/continuum-alpha-squad-ccc1-entry-20261103` | #387 | 12 | (b) | SHA voraus; fehlende MAIN: `ChiefProofGatedStrategyLedger.kt`, `ChiefTaskGraph.kt` — siehe §5 PR #387 (OPEN). |
| `feature/continuum-authorized-camera-bridge-20261030` | — | 8 | (a) | Kamera-Brücke/Mission-Control-Dateien der späteren Linie vorhanden oder als Docs; keine fehlende MAIN außer ggf. DualControl (Nachbarbranch). |
| `feature/continuum-chief-proof-gated-strategy-ledger-20261103` | #386 | 10 | (b) | SHA voraus; fehlende MAIN: `ChiefProofGatedStrategyLedger.kt`, `ChiefTaskGraph.kt` — siehe §5 PR #386 (OPEN). |
| `feature/continuum-chief-specialist-delegation-20261103` | #383 | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #383 (OPEN). |
| `feature/continuum-chief-task-dag-20261103` | #385 | 8 | (b) | SHA voraus; fehlende MAIN: `ChiefTaskGraph.kt` — siehe §5 PR #385 (OPEN). |
| `feature/continuum-dual-control-profiles-20261030` | — | 11 | (d) | ContinuumDualControlPolicy.kt fehlt auf #698 (zwei Owner-Profile ohne ToolGate-Änderung). 2745 hinter Cinema; gezielt portieren, nicht ganzen Branch mergen. |
| `feature/continuum-fable51-avatar-motion-20261104` | #502 | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #502 (OPEN). |
| `feature/continuum-measured-neon-wave-20261022` | #80 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #80 (OPEN). |
| `feature/continuum-model-center-selected-models-20261025` | #148 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #148 (OPEN). |
| `feature/continuum-model-web-learning-review-20261029` | #239 | 37 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt`, `AuthorizedWebLearningReviewInbox.kt` — siehe §5 PR #239 (OPEN). |
| `feature/continuum-navada-live-integration-20261104` | #503 | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #503 (OPEN). |
| `feature/continuum-p0-inapp-owner-updater-20261029` | #236 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #236 (OPEN). |
| `feature/continuum-v32-openrouter-mentor-20261031` | #296 | 13 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #296 (OPEN). |
| `feature/continuum-v35-agent-risk-tools-20261031` | #304 | 0 | (a) | Gemergt 2026-10-31 in `release/continuum-v35-integrated-20261031`; ahead=0 gegen #698 PR #304 (MERGED). |
| `fix/continuum-apk42-stabilize-20261105` | #559 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #559 (OPEN). |
| `fix/continuum-brain-integration-r3-20261104` | #522 | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #522 (OPEN). |
| `fix/continuum-cdx1-collapse-diagnostics-20261101` | #313 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #313 (OPEN). |
| `fix/continuum-code40-chief-apk-gates-20261103` | #388 | 0 | (a) | Gemergt 2026-11-03 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698 PR #388 (MERGED). |
| `fix/continuum-code40-router-race-review-20261103` | #390 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #390 (OPEN). |
| `fix/continuum-code44-release-qa-20261105` | #570 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #570 (OPEN). |
| `fix/continuum-dashboard-evidence-labels-20261029` | #234 | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #234 (OPEN). |
| `fix/continuum-device-experience-20261104` | #477 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #477 (OPEN). |
| `fix/continuum-device-experience-resume-20261104` | #478 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #478 (OPEN). |
| `fix/continuum-ewm2-archive-plural-20261101` | #325 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #325 (OPEN). |
| `fix/continuum-ewm2-role-age-production-20261101` | #320 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #320 (OPEN). |
| `fix/continuum-fold-live-responsive-20261023` | #86 | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #86 (MERGED). |
| `fix/continuum-fold-motion-regression-gates-20261023` | #87 | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #87 (MERGED). |
| `fix/continuum-fold7-stream-scroll-20261101` | #337 | 7 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #337 (OPEN). |
| `fix/continuum-local-model-quality-gemma-20261025` | #149 | 13 | (b) | SHA voraus; fehlende MAIN: `LocalModelImportPreset.kt` — siehe §5 PR #149 (OPEN). |
| `fix/continuum-m5-chunk-index-provenance-20261101` | #326 | 13 | (b) | SHA voraus; fehlende MAIN: `DevelopmentSourceChunkIndex.kt`, `DevelopmentSourceStreamImport.kt` — siehe §5 PR #326 (OPEN). |
| `fix/continuum-memory-index-atomic-r7-20261105` | #533 | 14 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #533 (OPEN). |
| `fix/continuum-memory-privacy-r4-20261104` | #529 | 17 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #529 (OPEN). |
| `fix/continuum-memory-r10-fuzzy-ambiguity-20261105` | #537 | 61 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #537 (OPEN). |
| `fix/continuum-memory-r11-single-digit-duplicate-negation-20261105` | #538 | 72 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #538 (OPEN). |
| `fix/continuum-memory-r12-numeric-evidence-20261105` | #548 | 82 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #548 (OPEN). |
| `fix/continuum-memory-r13-relative-cues-20261105` | #553 | 86 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #553 (OPEN). |
| `fix/continuum-memory-r13-temporal-lexical-20261105` | #555 | 90 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #555 (OPEN). |
| `fix/continuum-memory-r5-credential-boundaries-20261105` | #531 | 33 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #531 (OPEN). |
| `fix/continuum-memory-r8-negation-k-boundary-20261105` | #534 | 24 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #534 (OPEN). |
| `fix/continuum-memory-r9-negation-fuzzy-boundary-20261105` | #536 | 43 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #536 (OPEN). |
| `fix/continuum-memory-retriever-snapshot-r6-20261105` | #532 | 10 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #532 (OPEN). |
| `fix/continuum-original-backend-release-gate-20261105` | #561 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #561 (OPEN). |
| `fix/continuum-orion47-definition-intent-20261025` | #142 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #142 (OPEN). |
| `fix/continuum-owner-v18-shallow-attestation-20261023` | #103 | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #103 (MERGED). |
| `fix/continuum-qwen3-quality-budget-20261025` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `fix/continuum-r6-landscape-48dp-20261104` | #524 | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #524 (OPEN). |
| `fix/continuum-r6-lm1-capacity-evidence-20261104` | #525 | 21 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #525 (OPEN). |
| `fix/continuum-self-awareness-neuron-command-20261029` | #233 | 13 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5 PR #233 (OPEN). |
| `fix/continuum-signed-archive-missing-release-20261023` | #89 | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #89 (MERGED). |
| `fix/continuum-signed-rollback-metadata-20261023` | #88 | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #88 (MERGED). |
| `fix/continuum-sol314-kinship-age-qa-20261101` | #319 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #319 (OPEN). |
| `fix/continuum-sol314-source-plural-e-20261101` | #323 | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #323 (OPEN). |
| `fix/continuum-status-history-20261104` | #475 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #475 (OPEN). |
| `fix/continuum-v18-cache-safe-git-gate-20261023` | #104 | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #104 (MERGED). |
| `fix/continuum-v18-render-detached-head-20261023` | #102 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #102 (MERGED). |
| `fix/continuum-v18-render-dirty-audit-20261023` | #105 | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt PR #105 (CLOSED). |
| `fix/continuum-v32-network-web-recovery-20261031` | #294 | 1 | (c) | Research-Zweig PR #294 (OPEN). |
| `fix/continuum-v34-web-discovery-retry-20261031` | #300 | 0 | (a) | Gemergt 2026-10-31 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698 PR #300 (MERGED). |
| `fix/continuum-v35-learning-preview-clarity-20261101` | #311 | 4 | (b) | SHA voraus; fehlende MAIN: `PersonalLearningPreviewCopy.kt` — siehe §5 PR #311 (OPEN). |
| `fix/continuum-v35-local-chat-first-20261031` | — | 3 | (c) | SHA voraus (ahead=3, behind=2495), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `fix/continuum-v35-offline-long-input-20261031` | #310 | 18 | (b) | SHA voraus; fehlende MAIN: `ContinuumTermuxFailureDiagnosis.kt`, `ConversationResearchIntent.kt` — siehe §5 PR #310 (OPEN). |
| `fix/continuum-v35-owner-qa-intent-20261031` | #309 | 15 | (b) | SHA voraus; fehlende MAIN: `ContinuumTermuxFailureDiagnosis.kt`, `ConversationResearchIntent.kt` — siehe §5 PR #309 (OPEN). |
| `fix/continuum-web-learning-candidates-cleanup-20261101` | #305 | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #305 (OPEN). |
| `gamma/parameter-audit-20261104` | #501 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #501 (OPEN). |
| `gpt6/continuum-0.3.18-release-preflight-20261023` | #106 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #106 (MERGED). |
| `gpt6/continuum-m4-first-owned-app-20261023` | #107 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #107 (OPEN). |
| `grok/bot01-no-orphans-20261110` | #705 | 13 | (a) | Phase-A-Analyse, nur Dokument PR #705 (OPEN). |
| `grok/bot13-expert-mode-toolgate-20261110` | #703 | 14 | (b) | IMPLEMENTIERT auf diesem Branch (Cherry-Picks 9b8aca625757→ce94c68f4, 753516eb8001→34e7e9e09). Auch in #706 PR #703 (OPEN). |
| `grok/bot13-signer-contract-cinema-20261110` | #704 | 12 | (e) | Signer-Vertrag, unrelated Historie (behind=5613) PR #704 (OPEN). |
| `grok/bot15-node-reliability-20261110` | #702 | 15 | (b) | Node-Verträge zu #701. Für Bot 15/16; ändert Orchestrator (Bot 04) PR #702 (OPEN). |
| `grok/chief-e2e-tests` | #377 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #377 (OPEN). |
| `grok/e2e-erster-denkdurchlauf` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `grok/jarvis-closeout-20261104` | #488 | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #488 (OPEN). |
| `grok/jarvis-design-system` | #479 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #479 (OPEN). |
| `handoff/sol61-continuum-20261019` | #36 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #36 (OPEN). |
| `integration/code52-swarm-20261110` | #686 | 0 | (a) | Swarm-Ancestor PR #686 (OPEN). |
| `integration/continuum-0320-all-connected-20261024` | #123 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #123 (OPEN). |
| `integration/continuum-0320-core-cdx-20261024` | — | 6 | (c) | SHA voraus (ahead=6, behind=3533), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `integration/continuum-aaa-bento-cockpits-20261022` | #79 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #79 (OPEN). |
| `integration/continuum-aaa-lm1-ccc1-preview-20261023` | #85, #84 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #84 (OPEN). |
| `integration/continuum-all-current-20261023` | — | 5 | (c) | SHA voraus (ahead=5, behind=3863), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `integration/continuum-all-systems-apk-qa-20261102` | #360 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #360 (OPEN). |
| `integration/continuum-apk-connections-wave2-20261104` | #513 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #513 (OPEN). |
| `integration/continuum-astra-20261021` | #48 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #48 (OPEN). |
| `integration/continuum-autonomy-learning-20261027` | #179 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #179 (OPEN). |
| `integration/continuum-avatar-complete-20261105` | #544 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #544 (OPEN). |
| `integration/continuum-brain-system-20261029` | #242 | 60 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #242 (OPEN). |
| `integration/continuum-c1-candidate-20261104` | #519 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #519 (OPEN). |
| `integration/continuum-chief-team-staging-20261103` | #384 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #384 (OPEN). |
| `integration/continuum-completed-tuning-20261104-822e` | #523 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #523 (OPEN). |
| `integration/continuum-composer-reflow-fix-20261023` | #96 | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #96 (OPEN). |
| `integration/continuum-connected-r3-20261104-822e` | #527 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #527 (OPEN). |
| `integration/continuum-current-review-20261023` | #97 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #97 (OPEN). |
| `integration/continuum-experiment-activation-20261025` | #143 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #143 (OPEN). |
| `integration/continuum-feedback-review-20261026` | #175 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #175 (OPEN). |
| `integration/continuum-finalization-20261104-18845` | #517 | 8 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #517 (OPEN). |
| `integration/continuum-followup-connectors-20261104-822e` | #512 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #512 (OPEN). |
| `integration/continuum-full-feature-candidate-20261022` | #78 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #78 (OPEN). |
| `integration/continuum-g1-r5-avatar-accept-20261104` | #520 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #520 (OPEN). |
| `integration/continuum-gguf-context-recovery-20261023` | #93 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #93 (OPEN). |
| `integration/continuum-grok1-369-372-20261103` | #375 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #375 (OPEN). |
| `integration/continuum-grok1-369-372-wip-untested-20261103` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `integration/continuum-grok1-m5-b5-untested-20261104` | #480 | 0 | (a) | Gemergt 2026-11-04 in `integration/continuum-grok1-369-372-20261103`; ahead=0 gegen #698 PR #480 (MERGED). |
| `integration/continuum-grounded-recall-fallback-20261026` | #162 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #162 (OPEN). |
| `integration/continuum-interim-owner-avatar-neon-20261105` | #543 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #543 (OPEN). |
| `integration/continuum-m6-all-verified-20261025` | #132, #131 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #132 (OPEN). |
| `integration/continuum-memory-avatar-hooks-20261104` | #499 | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #499 (OPEN). |
| `integration/continuum-offline-termux-proof-20261101` | #324 | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #324 (OPEN). |
| `integration/continuum-omega-sol61-20261103` | #380 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #380 (OPEN). |
| `integration/continuum-orb-neon-stage-20261105` | #539 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #539 (OPEN). |
| `integration/continuum-ouroboros-ewm2-proof-20261101` | #327 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #327 (OPEN). |
| `integration/continuum-owner-0323-review-20261026` | #171 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #171 (OPEN). |
| `integration/continuum-owner-apk-v18-candidate-20261023` | #101, #100 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #100 (OPEN). |
| `integration/continuum-owner-v23-jarvis-20261025` | #139 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #139 (OPEN). |
| `integration/continuum-owner-v26-live-candidate-20261027` | #187 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #187 (OPEN). |
| `integration/continuum-premium-ui-preservation-20261023` | #98 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #98 (OPEN). |
| `integration/continuum-premium-ui-preview-20261022` | #77 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #77 (OPEN). |
| `integration/continuum-ready-connections-20261104` | #506 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #506 (OPEN). |
| `integration/continuum-sol-numeric-binding-20261026` | #165 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #165 (OPEN). |
| `integration/continuum-soul-infrastructure-20261025` | #147 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #147 (OPEN). |
| `integration/continuum-typed-source-identity-20261026` | #161 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #161 (OPEN). |
| `integration/continuum-ui-freeze-ccc1-20261023` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `integration/continuum-ui-opus-gguf-20261023` | #94 | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #94 (MERGED). |
| `integration/continuum-verifier-first-spine-20261023` | #95 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #95 (OPEN). |
| `integration/gitlab-mr299-web-study-20261031` | #298 | 0 | (a) | Gemergt 2026-10-31 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698 PR #298 (MERGED). |
| `integration/jarvis-on-chatgpt-20261104` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `integration/jarvis-on-chatgpt-untested-20261104` | #483 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #483 (OPEN). |
| `jarvis-grafik/avatar-live` | #378 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #378 (OPEN). |
| `jarvis-grafik/avatar-ref-match` | #482 | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #482 (OPEN). |
| `jarvis-luecke/video3-alarm-warp-ganzkoerper` | #487 | 12 | (b) | video3-Rest (Alarm/Greeting/Calm) für Bot 04/07 PR #487 (OPEN). |
| `jarvis-mathematik/motion-math` | #489 | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #489 (OPEN). |
| `kern/cognitive-loop-20261104` | #490 | 7 | (c) | CognitiveLoop durch BoundedAgentOrchestrator ersetzt PR #490 (OPEN). |
| `lumo/agent-loop-device-control-20261103` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `main` | — | 11 | (e) | divergente historische main (behind=5613); kein Merge nach/von main ohne Owner. |
| `ops/deepseek-v41-1017-h200-deployment-20261025` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `opus/argon4-research-20261029` | #240 | 52 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5 PR #240 (OPEN). |
| `opus/ccc1-e2e-20261023` | #99 | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #99 (OPEN). |
| `opus/continuum-0.3.18-candidate-20261023` | — | 5 | (c) | SHA voraus (ahead=5, behind=3674), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `opus/continuum-aaa-ui-20261022` | #81 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #81 (OPEN). |
| `opus/continuum-app-navigation-20261022` | #63 | 0 | (a) | Gemergt 2026-10-22 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #63 (MERGED). |
| `opus/continuum-app-navigation-wip-archive-20261022` | — | 1 | (c) | SHA voraus (ahead=1, behind=4043), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `opus/continuum-brain-research-20261029` | #224 | 20 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #224 (OPEN). |
| `opus/continuum-csx1-abstention-20261022` | #62 | 32 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #62 (OPEN). |
| `opus/continuum-ewm2-integration-20261029` | #227 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #227 (OPEN). |
| `opus/continuum-i2-selective-risk-20261022` | #72 | 38 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #72 (OPEN). |
| `opus/continuum-intelligence-next-20261023` | #92 | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #92 (OPEN). |
| `opus/continuum-language-core-20261022` | #75 | 23 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #75 (OPEN). |
| `opus/continuum-m1-m8-20261030` | #244 | 91 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #244 (OPEN). |
| `opus/continuum-m9-m16-20261030` | #245 | 116 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #245 (OPEN). |
| `opus/continuum-mobile-coding-e2e-20261022` | #57 | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #57 (OPEN). |
| `opus/continuum-neural-frontier-lab-20261022` | #82 | 31 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #82 (OPEN). |
| `opus/continuum-next-20261023` | — | 1 | (c) | SHA voraus (ahead=1, behind=3657), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `opus/continuum-premium-ui-20261022` | #74 | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698 PR #74 (MERGED). |
| `opus/continuum-sol-handoff-20261024` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `opus/continuum-synapse-v2-20261022` | #73 | 14 | (b) | SHA voraus; fehlende MAIN: `ContinuumSynapseZeroShadow.kt`, `SynapseContrastiveRoleProbe.kt`, `SynapseV2Reasoner.kt` — siehe §5 PR #73 (OPEN). |
| `opus/jarvis-voice1-fix-20261030` | #252 | 144 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #252 (OPEN). |
| `opus/lm1-checkpoints-20261022` | — | — | (e) | LM1-Checkpoint-Historie ohne gemeinsamen Ancestor. Nicht mergen. |
| `opus/nf-checkpoints-20261022` | — | — | (e) | Checkpoint-Historie ohne gemeinsamen Ancestor. Nicht mergen. |
| `qa/continuum-owner-v34-independent-readback-20261101` | #312 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #312 (OPEN). |
| `qa/orb-hypothesis-marker-20261105-b3-r3` | #558 | 8 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #558 (OPEN). |
| `qa/orb-phase-finite-idle-20261105-b3-r2` | #550 | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #550 (OPEN). |
| `release/continuum-local-0.3.34-20261031` | #301 | 7 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #301 (OPEN). |
| `release/continuum-local-0.3.35-20261101` | #328 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #328 (OPEN). |
| `release/continuum-local-0.3.36-render-20261101` | #336 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #336 (OPEN). |
| `release/continuum-local-0.3.37-owner-candidate-20261102` | — | 0 | (c) | Alter Release-Zweig; Produktkern der späteren Cinema-Linie. Nicht löschen. |
| `release/continuum-local-complete-v31-20261031` | #293, #292 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #292 (OPEN). |
| `release/continuum-local-owner-update-channel-20261029` | — | 1 | (c) | Alter Release-Zweig; Produktkern der späteren Cinema-Linie. Nicht löschen. |
| `release/continuum-local-v30-integration-20261031` | #291 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #291 (OPEN). |
| `release/continuum-night-integration-20261103` | #474 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #474 (OPEN). |
| `release/continuum-owner-0321-20261024` | #124 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #124 (OPEN). |
| `release/continuum-owner-v23-candidate-20261025` | #140 | 15 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #140 (OPEN). |
| `release/continuum-owner-v24-candidate-20261026` | — | 0 | (c) | Alter Release-Zweig; Produktkern der späteren Cinema-Linie. Nicht löschen. |
| `release/continuum-owner-v25-candidate-20261026` | — | 0 | (c) | Alter Release-Zweig; Produktkern der späteren Cinema-Linie. Nicht löschen. |
| `release/continuum-v31-render-publisher-sync-20261031` | — | 20 | (c) | Alter Release-Zweig; Produktkern der späteren Cinema-Linie. Nicht löschen. |
| `release/continuum-v32-openrouter-fullweb-20261031` | #297 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #297 (MERGED). |
| `release/continuum-v35-integrated-20261031` | #302 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #302 (MERGED). |
| `repair/continuum-chief-cancellation-scope-20261103` | #381 | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #381 (OPEN). |
| `repair/continuum-chief-routines-mailbox-20261103` | #382 | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #382 (OPEN). |
| `repair/continuum-fold7-storage-verb-routing-20261103` | #365 | 0 | (a) | Gemergt 2026-11-03 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698 PR #365 (MERGED). |
| `repair/continuum-owner-backend-gate-20261105-now` | #560 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #560 (OPEN). |
| `repair/continuum-shizuku-composer-20261103` | #364 | 12 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #364 (OPEN). |
| `repair/continuum-three-bots-20261104` | #505 | 13 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #505 (OPEN). |
| `research/continuum-16m-support-20261104-319f3e` | #507 | 11 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #507 (OPEN). |
| `research/continuum-16m-training-support-r2-20261104-319f3e` | #514 | 16 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #514 (OPEN). |
| `research/continuum-340-evidence-routing-20261101` | #341 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #341 (OPEN). |
| `research/continuum-341-brain-wiring-qa-20261101` | #342 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #342 (OPEN). |
| `research/continuum-agent-handoff-standard-20261025` | #136 | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #136 (OPEN). |
| `research/continuum-autonomy-dialog-voice-20261102` | #346 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #346 (OPEN). |
| `research/continuum-bottleneck-diagnosis-a-b-c-20261026` | #166 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #166 (OPEN). |
| `research/continuum-bottleneck-scorecard-local-escalation-20261026` | #167 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #167 (OPEN). |
| `research/continuum-causal-diagnosis-pr183-sol-20261027` | — | 0 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-causal-diagnosis-sol-20261027` | — | 0 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-cdx1-negation-hardlock-20261101` | #321 | 11 | (c) | Research-Zweig PR #321 (OPEN). |
| `research/continuum-cloud-gpu-admission-20261030` | #268 | 102 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #268 (OPEN). |
| `research/continuum-cloudflare-nvidia-direct-20261031` | #274 | 15 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #274 (OPEN). |
| `research/continuum-cloudflare-owner-grant-20261031` | #275 | 16 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #275 (OPEN). |
| `research/continuum-cockpit-voice-original-orb-20261105` | #563 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #563 (OPEN). |
| `research/continuum-cognition-learning-loop-20261104` | #528 | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #528 (OPEN). |
| `research/continuum-cognitive-pilots-20261108` | #647 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #647 (OPEN). |
| `research/continuum-coin-wm-independent-audit-20261026` | #163 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #163 (OPEN). |
| `research/continuum-compute-lease-20261031` | #282 | 2 | (c) | Research-Zweig PR #282 (OPEN). |
| `research/continuum-context-validity-gate-20261031` | #281 | 2 | (c) | Research-Zweig PR #281 (OPEN). |
| `research/continuum-counterexample-acceptance-20261029` | #243 | 81 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #243 (OPEN). |
| `research/continuum-critical-qa-repair-20261102` | #351 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #351 (OPEN). |
| `research/continuum-current-state-discovery-20261031` | #288 | 2 | (c) | Research-Zweig PR #288 (OPEN). |
| `research/continuum-dialog-vi-nine-gates-20261102` | #347 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #347 (OPEN). |
| `research/continuum-dialog-vii-repair-audit-20261102` | #348 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #348 (OPEN). |
| `research/continuum-epistemic-lab-20261108` | #635 | 2 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #635 (OPEN). |
| `research/continuum-evidence-bound-transfer-20261028` | #207 | 12 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #207 (OPEN). |
| `research/continuum-evidence-recall-20261031` | #280 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #280 (OPEN). |
| `research/continuum-evidence-span-natural-entities-20261101` | #330 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #330 (OPEN). |
| `research/continuum-execution-atomicity-20261102` | #359 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #359 (OPEN). |
| `research/continuum-fable51-genesis-20261101` | #334 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #334 (OPEN). |
| `research/continuum-fable51-jarvis-genesis-20261104` | #500 | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #500 (OPEN). |
| `research/continuum-fold7-chat-routing-repair-20261103` | #363 | 2 | (c) | Research-Zweig PR #363 (OPEN). |
| `research/continuum-fusion-iv-20261102` | #345 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #345 (OPEN). |
| `research/continuum-genesis-independent-audit-20261108` | #660 | 3 | (c) | Research-only PR #660 (OPEN). |
| `research/continuum-grand-challenge-20261102` | #349 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #349 (OPEN). |
| `research/continuum-h4-robust-transfer-20261108` | #655 | 11 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #655 (OPEN). |
| `research/continuum-interactive-intelligence-lab-20261026` | #174 | 6 | (b) | SHA voraus; fehlende MAIN: `InteractiveDialogueContext.kt`, `InteractiveResearchRouting.kt` — siehe §5 PR #174 (OPEN). |
| `research/continuum-jarvis-04-architecture-implementation-20261101` | #333 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #333 (OPEN). |
| `research/continuum-jarvis-evolution-20261101` | #331 | 1 | (c) | Research-Zweig PR #331 (OPEN). |
| `research/continuum-jarvis-intent-intake-20261031` | #283 | 4 | (c) | Research-Zweig PR #283 (OPEN). |
| `research/continuum-jarvis-mcp-video-20261030` | #263 | 10 | (c) | Research-Zweig PR #263 (OPEN). |
| `research/continuum-jarvis-neuro-integration-20261031` | #284 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #284 (OPEN). |
| `research/continuum-jarvis-video-evidence-20261030` | #262 | 8 | (c) | Research-Zweig PR #262 (OPEN). |
| `research/continuum-language-core-update-contract-20261029` | #232 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #232 (OPEN). |
| `research/continuum-learning-parity-readonly-20261028` | #209 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #209 (OPEN). |
| `research/continuum-live-dialog-routing-20261101` | #332 | 0 | (a) | Gemergt 2026-11-01 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698 PR #332 (MERGED). |
| `research/continuum-lm1-resumable-training-20261104-319f3e` | #530 | 24 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #530 (OPEN). |
| `research/continuum-lm1-training-recovery-20261104` | #508 | 17 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #508 (OPEN). |
| `research/continuum-local-brain-sol-20261028` | #206 | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #206 (OPEN). |
| `research/continuum-local-integrated-gate-20261028` | #212 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #212 (OPEN). |
| `research/continuum-local-microagents-20261028` | #208 | 21 | (b) | SHA voraus; fehlende MAIN: `ContinuumMicroAgentBridge.kt` — siehe §5 PR #208 (OPEN). |
| `research/continuum-m10-verified-brain-and-web-20261031` | #299 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #299 (OPEN). |
| `research/continuum-m11-evidence-negation-gate-20261031` | #303 | 0 | (a) | Gemergt 2026-10-31 in `release/continuum-v35-integrated-20261031`; ahead=0 gegen #698 PR #303 (MERGED). |
| `research/continuum-m5-persistent-chunk-index-20261101` | #318 | 8 | (b) | SHA voraus; fehlende MAIN: `DevelopmentSourceChunkIndex.kt`, `DevelopmentSourceStreamImport.kt` — siehe §5 PR #318 (OPEN). |
| `research/continuum-m5-source-stream-20261101` | #316 | 4 | (b) | SHA voraus; fehlende MAIN: `DevelopmentSourceStreamImport.kt` — siehe §5 PR #316 (OPEN). |
| `research/continuum-m8-operational-model-20261031` | #285 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #285 (OPEN). |
| `research/continuum-m8-render-signed-release-20261031` | #286 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #286 (OPEN). |
| `research/continuum-mastercode-bounded-search-20261030` | #248 | 119 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #248 (OPEN). |
| `research/continuum-native-foundation-20261025` | #145 | 5 | (c) | Research-Zweig PR #145 (OPEN). |
| `research/continuum-native-session-lifecycle-20261103` | #368 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #368 (OPEN). |
| `research/continuum-nemo-virtual-fabric-20261031` | #287 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #287 (OPEN). |
| `research/continuum-nvidia-automation-ledger-20261030` | #269 | 32 | (c) | NVIDIA-Forschungsplan/Ledger, kein Produktpfad PR #269 (MERGED). |
| `research/continuum-nvidia-claim-readback-20261031` | #271 | 108 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #271 (OPEN). |
| `research/continuum-nvidia-executor-env-gate-20261031` | #272 | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #272 (OPEN). |
| `research/continuum-nvidia-explicit-owner-consent-20261031` | #273 | 114 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #273 (OPEN). |
| `research/continuum-nvidia-single-use-claim-20261031` | #270 | 103 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #270 (OPEN). |
| `research/continuum-omega-context-integrity-20261103` | #366 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #366 (OPEN). |
| `research/continuum-omega-resume-chatgpt-20261025` | #134 | 34 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #134 (OPEN). |
| `research/continuum-omega-task-scoped-temporal-20261103` | #367 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #367 (OPEN). |
| `research/continuum-omega-vhsr-20261025` | #133 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #133 (OPEN). |
| `research/continuum-openrouter-backend-qa-20261031` | — | 16 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-optional-free-coding-bridge-20261028` | #210 | 3 | (c) | Research-Zweig PR #210 (OPEN). |
| `research/continuum-owner-resilient-jarvis-charter-20261030` | #251 | 143 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #251 (OPEN). |
| `research/continuum-p256-signature-review-chatgpt-20261025` | #137 | 42 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #137 (OPEN). |
| `research/continuum-prior-batch-pilot-20261108` | #648 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #648 (OPEN). |
| `research/continuum-quantumflow-upstreams-20261102` | #350 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #350 (OPEN). |
| `research/continuum-queue-bootstrap-grant-time-20261031` | #277 | 18 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #277 (OPEN). |
| `research/continuum-self-evolution-shadow-20261030` | #249 | 154 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #249 (OPEN). |
| `research/continuum-selfhosted-coding-20261028` | #211 | 7 | (c) | Research-Zweig PR #211 (OPEN). |
| `research/continuum-session-fact-priority-20261025` | — | 0 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-sol-cdx1-evidence-20261101` | #317 | 0 | (a) | Gemergt 2026-11-01 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698 PR #317 (MERGED). |
| `research/continuum-sol-max-autonomy-20261103` | #362 | 6 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #362 (OPEN). |
| `research/continuum-sol-max-handoff-readiness-20261028` | #213 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #213 (OPEN). |
| `research/continuum-sol-native-numeric-binding-20261026` | — | 1 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-sol-owner-consent-20261025` | — | 39 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-sol-preflight-and-native-evidence-20261028` | #214 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #214 (OPEN). |
| `research/continuum-sol61-jarvis-gates-20261102` | #361 | 1 | (c) | Research-Zweig PR #361 (OPEN). |
| `research/continuum-sol61-post-fable-20261101` | #335 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #335 (OPEN). |
| `research/continuum-sovereign-brain-iv-20261102` | #358 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #358 (OPEN). |
| `research/continuum-sovereign-cognition-20261102` | #357 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #357 (OPEN). |
| `research/continuum-sovereign-gguf-admission-20261102` | #356 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #356 (OPEN). |
| `research/continuum-superki-architecture-proof-20261101` | #329 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #329 (OPEN). |
| `research/continuum-synapse-ii-20261102` | #343 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #343 (OPEN). |
| `research/continuum-synapse-iii-20261102` | #344 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #344 (OPEN). |
| `research/continuum-three-experiments-20261104` | #509 | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #509 (OPEN). |
| `research/continuum-typed-review-chatgpt-20261025` | #135 | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #135 (OPEN). |
| `research/continuum-v25-owner-ledger-export-20261028` | #217 | 5 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #217 (OPEN). |
| `research/continuum-v30-update-version-repair-20261031` | — | 14 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-v32-autonomy-controls-20261031` | — | 0 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-verified-reuse-pilot-20261108` | #649 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #649 (OPEN). |
| `research/continuum-verified-skill-review-20261031` | — | 2 | (c) | Research-Zweig ohne eigenen PR. |
| `research/continuum-virtual-model-fabric-20261026` | #168 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #168 (OPEN). |
| `research/continuum-weight-helper-team-20261104` | #497 | 7 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #497 (OPEN). |
| `research/continuum-weight-space-audit-20261104` | #492 | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #492 (OPEN). |
| `research/continuum-worker-untracked-gate-20261028` | #216 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #216 (OPEN). |
| `research/exp20-p2-context-contract-20261030` | #255 | 156 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #255 (OPEN). |
| `research/exp20-p2-opus-project-brief-20261030` | #254 | 154 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #254 (OPEN). |
| `research/gemma-monotonic-profile-20261029` | #226 | 2 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #226 (OPEN). |
| `research/gemma-stream-callback-lifecycle-20261029` | #221 | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #221 (OPEN). |
| `research/jarvis-autonomous-core` | #322 | 16 | (c) | Research-Zweig PR #322 (OPEN). |
| `research/jarvis-international-adaptation-20261025` | #138 | 1 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #138 (OPEN). |
| `research/jarvis-voice-conversation-owner-gated-20261030` | #250 | 131 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #250 (OPEN). |
| `research/jarvis-voice1-stale-recognizer-session-20261030` | #253 | 152 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #253 (OPEN). |
| `research/jpi1-presence-initiative-shadow-20261030` | #247 | 121 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #247 (OPEN). |
| `research/jpi1-presence-shadow-20261030` | #246 | 117 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5 PR #246 (OPEN). |
| `research/opus55-continuum-voice-hud-preflight-20261105` | #564 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #564 (OPEN). |
| `research/sol61-ccc1-skill-transfer` | #241 | 52 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5 PR #241 (OPEN). |
| `research/sol61-owner-auth-lab-20261025` | — | 40 | (c) | Research-Zweig ohne eigenen PR. |
| `review/continuum-baby-memory-migration-20261026` | #158 | 10 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #158 (OPEN). |
| `review/continuum-brain-safety-continuation-20261026` | #157 | 29 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #157 (OPEN). |
| `review/continuum-ccc1-test-parity-20261026` | #164 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #164 (OPEN). |
| `review/continuum-followup-archive-gate-20261026` | #173 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #173 (OPEN). |
| `review/continuum-grounding-port-v24-20261026` | #172 | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #172 (OPEN). |
| `review/continuum-memory-attribute-guard-20261029` | #229 | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen PR #229 (OPEN). |
| `review/continuum-repair-learning-evidence-gate-20261026` | #160 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #160 (OPEN). |
| `review/ewm2-revocation-race-20261029` | #228 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #228 (OPEN). |
| `review/render-first-portable-build-20261022` | #56 | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #56 (OPEN). |
| `sol/cdx-generation-repair-20261026` | #152 | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #152 (OPEN). |
| `sol/code51-master-integration-20261108` | #650 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #650 (OPEN). |
| `sol/code51-pinned-integration-20261108` | #653 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #653 (OPEN). |
| `sol/code51-reconcile-20261108` | #658 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #658 (OPEN). |
| `sol/coding-discovery-safety-20261026` | #155 | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #155 (OPEN). |
| `sol/continuum-0320-integration-20261024` | #116 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #116 (OPEN). |
| `sol/continuum-autonomous-coding-20261024` | #125 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #125 (OPEN). |
| `sol/continuum-autonomy-contrastive-20261027` | #178 | 33 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #178 (OPEN). |
| `sol/continuum-brain-breakthroughs-20261026` | #151 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #151 (OPEN). |
| `sol/continuum-cognitive-evidence-20261027` | #182 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #182 (OPEN). |
| `sol/continuum-dan-evidence-loop-20261029` | #238 | 42 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5 PR #238 (OPEN). |
| `sol/continuum-dialog-integration-20261029` | #237 | 15 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5 PR #237 (OPEN). |
| `sol/continuum-experiment-bridge-20261027` | — | 10 | (c) | SHA voraus (ahead=10, behind=3047), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `sol/continuum-fast-repair-20261026` | #156 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #156 (OPEN). |
| `sol/continuum-generation16-gemma-20261029` | #218 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #218 (OPEN). |
| `sol/continuum-gpt6-closeout-20261029` | #230 | 8 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #230 (OPEN). |
| `sol/continuum-harness-fabric-20261027` | #183 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #183 (OPEN). |
| `sol/continuum-hologram-intelligence-20261024` | — | 1 | (c) | SHA voraus (ahead=1, behind=3601), kein PR; typischer Parallel-/Archivstand, nicht blind mergen. |
| `sol/continuum-infrastructure-repair-20261025` | #150 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #150 (OPEN). |
| `sol/continuum-isolated-research-20261021` | #53 | 4 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle PR #53 (OPEN). |
| `sol/continuum-jarvis-open-fabric-20261026` | #169 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #169 (OPEN). |
| `sol/continuum-local-owner-integration-20261027` | #196 | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie PR #196 (OPEN). |
| `sol/continuum-m5-context-coding-experiments-20261024` | #109 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #109 (OPEN). |
| `sol/continuum-master-m4-research-integration-20261023` | #108 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #108 (OPEN). |
| `sol/continuum-max-20261024` | #112 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #112 (OPEN). |
| `sol/continuum-oneapp-brain-recovery-20261028` | #215 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #215 (OPEN). |
| `sol/continuum-quality-20261021` | #43 | 0 | (a) | Gemergt 2026-10-21 in `astra/continuum-experiment-charter-20261019`; ahead=0 gegen #698 PR #43 (MERGED). |
| `sol/continuum-runtime-reliability-20261029` | #223 | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #223 (OPEN). |
| `sol/continuum-storage-prototype-20261027` | #184 | 3 | (c) | Research-Zweig PR #184 (OPEN). |
| `sol/deepseek-authenticated-bridge-20261026` | #154 | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #154 (OPEN). |
| `sol/grounding-relations-20261026` | #153 | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #153 (OPEN). |
| `support/continuum-apk-preflight-aapt2-20261105-b6` | #554 | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #554 (OPEN). |
| `support/continuum-dialog-layout-apk-20261105-3e26` | #547 | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #547 (OPEN). |
| `support/continuum-pcm-silence-3e26-20261105` | #562 | 2 | (a) | PCM-Stille auf #698 PR #562 (OPEN). |
| `support/continuum-reference-binding-s5-20261105` | #557 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #557 (OPEN). |
| `support/continuum-ten-bot-assist-20261104` | #496 | 10 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #496 (OPEN). |
| `support/h4-avatar-stop-next-turn-regression-20261105` | #556 | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #556 (OPEN). |
| `tuning/continuum-three-bots-20261104` | #511 | 71 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698 PR #511 (OPEN). |
| `v0.2-redesign` | — | 0 | (a) | ahead=0 gegen #698 (kein PR). |
| `v0.3-agent` | #1 | 17 | (c) | Historischer v0.3-Agent, durch Continuum-Linie ersetzt PR #1 (MERGED). |
| `wip/checkpoint-20261110-render-combo` | — | 1 | (e) | Ungelöster Probe-Merge, nicht mergen. |
| `wip/stop-20261104-integration-nav` | — | 5 | (c) | Stopp-Snapshot 04.11. |
| `wip/stop-20261104-sicherheit-m5fg` | — | 3 | (c) | Stopp-Snapshot 04.11. |
| `wip/stop-20261104-welten-optik-design` | — | 3 | (c) | Stopp-Snapshot 04.11. |
| `work/continuum-phase1-direct` | #9 | 0 | (a) | GitHub Compare: ahead=0 gegen #698 PR #9 (OPEN). |

## 13. Lückenlose PR-Tabelle (alle 661: open + merged + closed)

Zählung PRs: (a) 511, (b) 54, (c) 73, (e) 23, Summe 661.

| PR | State | Branch | ahead | Klasse | Begründung/Beleg |
|---|---|---|---:|---|---|
| #706 | OPEN/DRAFT | `code52/grok-release-candidate-0351-20261110` | 19 | (b) | Bot-16-RC: #698+#699+#701+#702+#703. Fast-forward von #698. Orchestrator = Bot 04. |
| #705 | OPEN/DRAFT | `grok/bot01-no-orphans-20261110` | 13 | (a) | Phase-A-Analyse, nur Dokument. |
| #704 | OPEN/DRAFT | `grok/bot13-signer-contract-cinema-20261110` | 12 | (e) | Signer-Vertrag, unrelated Historie (behind=5613). |
| #703 | OPEN/DRAFT | `grok/bot13-expert-mode-toolgate-20261110` | 14 | (b) | IMPLEMENTIERT auf diesem Branch (Cherry-Picks 9b8aca625757→ce94c68f4, 753516eb8001→34e7e9e09). Auch in #706. |
| #702 | OPEN/DRAFT | `grok/bot15-node-reliability-20261110` | 15 | (b) | Node-Verträge zu #701. Für Bot 15/16; ändert Orchestrator (Bot 04). |
| #701 | MERGED 2026-11-10 | `chatgpt/code52-cinema-document-learning-isolation-20261110` | 11 | (b) | MERGED in #699. DocumentLearned-Isolation. Fehlt in #698-Spitze; in #706. Bot-04-Datei. |
| #700 | OPEN/DRAFT | `code52/cinema-original-signer-qa-binding-20261110` | 11 | (e) | Original-Signer, unrelated. |
| #699 | OPEN/DRAFT | `code52/cinema-native-qa-review-fixes-20261110` | 12 | (b) | QA/#701-Reparaturen; Teil von #706; Bot-04-Datei. |
| #698 | OPEN/DRAFT | `code52/final-integrator-cinema-20261110` | 0 | (a) | Ausgangsstand a0a69263. |
| #697 | OPEN/DRAFT | `code52/final-integrator-thermal-20261110` | 0 | (a) | Ancestor (Thermal). |
| #696 | OPEN/DRAFT | `code52/visual-depth-ref-d-20261110` | 0 | (a) | Ancestor (Tiefe); Weiterarbeit Bot 06. |
| #695 | OPEN/DRAFT | `chatgpt/code52-fold7-thermal-budget-guard-20261110` | 2 | (a) | Inhalt über #697 gleichwertig in Cinema. |
| #694 | MERGED 2026-11-10 | `code52/avatar-gaze-light-20261110` | 0 | (a) | MERGED, Ancestor. |
| #693 | OPEN/DRAFT | `code52/visual-style-20261110` | 2 | (c) | Nur leere Merge-Commits; Inhalt in #696/#698. |
| #692 | OPEN/DRAFT | `code52/cinema-flyin-20261110` | 0 | (a) | Ancestor; Weiterarbeit Bot 04. |
| #691 | OPEN/DRAFT | `code52/cockpit-fidelity-20261110` | 0 | (a) | Ancestor; Phone/dialogTop Bot 07. |
| #690 | OPEN/DRAFT | `chatgpt/code52-swarm-document-lifecycle-fix-20261110` | 11 | (a) | Produktkern über #697+#701; Spitze würde kollidieren. |
| #689 | MERGED 2026-11-10 | `code52/cockpit-20261110` | 0 | (a) | Gemergt 2026-11-10 in `integration/code52-swarm-20261110`; ahead=0 gegen #698. |
| #688 | MERGED 2026-11-10 | `code52/cinema-avatar-20261110` | 0 | (a) | Gemergt 2026-11-10 in `integration/code52-swarm-20261110`; ahead=0 gegen #698. |
| #687 | MERGED 2026-11-10 | `code52/fui-worlds-20261110` | 0 | (a) | Gemergt 2026-11-10 in `integration/code52-swarm-20261110`; ahead=0 gegen #698. |
| #686 | OPEN/DRAFT | `integration/code52-swarm-20261110` | 0 | (a) | Swarm-Ancestor. |
| #685 | OPEN/DRAFT | `chatgpt/code52-cinema-branch-safe-qa-20261110` | 0 | (a) | Ancestor. |
| #684 | OPEN/DRAFT | `chatgpt/code52-cinema-grounded-document-question-20261110` | 0 | (a) | Ancestor. |
| #683 | OPEN/DRAFT | `chatgpt/code52-cinema-final-graphics-audit-20261110` | 0 | (a) | Ancestor. |
| #682 | OPEN/DRAFT | `chatgpt/code52-cinema-owner-signer-candidate-20261110` | 13 | (e) | Älterer Signer-Kandidat. |
| #681 | OPEN/DRAFT | `chatgpt/code52-cinema-prism-pixel-fix-20261110` | 0 | (a) | Ancestor. |
| #680 | OPEN/DRAFT | `chatgpt/code52-cinema-switch-contract-20261109` | 0 | (a) | Ancestor. |
| #679 | MERGED 2026-11-09 | `chatgpt/code52-cinema-prism-shader-20261109` | 0 | (a) | Gemergt 2026-11-09 in `chatgpt/code52-argon4-holographic-rim-20261109`; ahead=0 gegen #698. |
| #678 | MERGED 2026-11-09 | `chatgpt/code52-cinema-avatar-finesse-20261109` | 0 | (a) | Gemergt 2026-11-09 in `chatgpt/code52-argon4-holographic-rim-20261109`; ahead=0 gegen #698. |
| #677 | OPEN/DRAFT | `chatgpt/code52-argon4-holographic-rim-20261109` | 0 | (a) | Ancestor. |
| #676 | MERGED 2026-11-09 | `chatgpt/code52-ci-coverage-20261109` | 0 | (a) | Gemergt 2026-11-09 in `chatgpt/code52-unified-integration-20261109`; ahead=0 gegen #698. |
| #675 | MERGED 2026-11-09 | `chatgpt/code52-owner-signing-20261109` | 10 | (e) | Owner-Signing-Registrierung, unrelated (behind=5613). |
| #673 | MERGED 2026-11-09 | `chatgpt/code52-intent-regression-20261109` | 0 | (a) | Gemergt 2026-11-09 in `chatgpt/code52-unified-integration-20261109`; ahead=0 gegen #698. |
| #672 | OPEN/DRAFT | `chatgpt/code52-project-isolation-20261109` | 7 | (a) | Inhalt über #671 auf #698 vorhanden. |
| #671 | OPEN/DRAFT | `chatgpt/code52-unified-integration-20261109` | 0 | (a) | Ancestor von aktuellem #698. |
| #669 | OPEN/DRAFT | `chatgpt/code52-fold7-inference-truth-20261109` | 0 | (a) | Ancestor. |
| #668 | MERGED 2026-11-09 | `chatgpt/code51-manual-qa-registration-20261109` | 3 | (e) | Manual-QA-Registrierung auf Default-Branch / Signierlinie. |
| #666 | OPEN/DRAFT | `chatgpt/cinematic-sustained-hologram-20261109` | 14 | (a) | Produktfiles auf #698; fehlt nur AvatarReadingContinuityTest. |
| #665 | OPEN/DRAFT | `chatgpt/code51-ui-acceptance-recovery-20261109` | 4 | (a) | UI-Acceptance auf #698. |
| #664 | OPEN/DRAFT | `chatgpt/code51-voice-prosody-recovery-20261109` | 3 | (a) | TTS-Prosodie in JarvisVoiceProfile vorhanden. |
| #663 | OPEN/DRAFT | `copilot/code51-master-integrator-20261108` | 0 | (a) | Ancestor. |
| #662 | OPEN/DRAFT | `astra/continuum-bot-universe-20261108` | 4 | (a) | CCC1/Coding-Service auf #698. |
| #661 | OPEN/DRAFT | `astra/continuum-holo-reference-fidelity-20261108` | 1 | (c) | Nur Evidenzbilder. |
| #660 | OPEN/DRAFT | `research/continuum-genesis-independent-audit-20261108` | 3 | (c) | Research-only. |
| #659 | OPEN/DRAFT | `astra/continuum-holographic-v2-20261108` | 10 | (a) | wie #666. |
| #658 | OPEN/DRAFT | `sol/code51-reconcile-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #657 | OPEN/DRAFT | `fable/code51-p1-memory-meta-separation-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #656 | OPEN/DRAFT | `fable/code51-p1-chat-web-research-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #655 | OPEN/DRAFT | `research/continuum-h4-robust-transfer-20261108` | 11 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #654 | OPEN/DRAFT | `fable/code51-p1-internal-navigation-routing-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #653 | OPEN/DRAFT | `sol/code51-pinned-integration-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #651 | OPEN/DRAFT | `fable/code51-fui-cinematic-construct-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #650 | OPEN/DRAFT | `sol/code51-master-integration-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #649 | OPEN/DRAFT | `research/continuum-verified-reuse-pilot-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #648 | OPEN/DRAFT | `research/continuum-prior-batch-pilot-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #647 | OPEN/DRAFT | `research/continuum-cognitive-pilots-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #646 | OPEN/DRAFT | `fable/code51-p0-speech-recovery-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #644 | OPEN/DRAFT | `chatgpt/code51-owner-canon-fable51-handoff-20261108` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #636 | MERGED 2026-11-08 | `chatgpt/code50-signierworkflow-registrierung-20261108` | 1 | (e) | Signierworkflow-Registrierung. |
| #635 | OPEN/DRAFT | `research/continuum-epistemic-lab-20261108` | 2 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #634 | MERGED 2026-11-08 | `chatgpt/code50-deck-footprint-tests-20261108` | 0 | (a) | Gemergt 2026-11-08 in `chatgpt/code50-qa-staging-20261108`; ahead=0 gegen #698. |
| #633 | CLOSED | `chatgpt/code50-upstream-reconciled-20261108` | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt. |
| #632 | MERGED 2026-11-08 | `chatgpt/code50-android27-repair-20261108` | 0 | (a) | Gemergt 2026-11-08 in `chatgpt/code50-qa-staging-20261108`; ahead=0 gegen #698. |
| #631 | MERGED 2026-11-08 | `chatgpt/code50-node22-repair-20261108` | 0 | (a) | Gemergt 2026-11-08 in `chatgpt/code50-qa-staging-20261108`; ahead=0 gegen #698. |
| #630 | OPEN/DRAFT | `chatgpt/code50-qa-staging-20261108` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #629 | OPEN/DRAFT | `chatgpt/code50-local-qa-20261108` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #628 | OPEN/DRAFT | `chatgpt/code50-app-receipt-gate-20261108` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #627 | MERGED 2026-11-08 | `chatgpt/code50-acceptance-repair-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #626 | MERGED 2026-11-08 | `chatgpt/code50-app-intent-exact-gate-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #625 | MERGED 2026-11-08 | `chatgpt/code50-pdf-pagecount-fui-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #624 | MERGED 2026-11-08 | `chatgpt/code50-app-intent-evidence-gate-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #623 | MERGED 2026-11-08 | `chatgpt/code50-fui-live-data-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #622 | MERGED 2026-11-08 | `chatgpt/code50-airbus-acceptance-tests-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #621 | MERGED 2026-11-08 | `chatgpt/code50-airbus-orchestrator-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #620 | MERGED 2026-11-08 | `chatgpt/code50-countdown-sidequest-current-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #619 | MERGED 2026-11-08 | `chatgpt/code50-fold7-base-guards-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #618 | MERGED 2026-11-08 | `chatgpt/code50-hollywood-fui-current-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #617 | MERGED 2026-11-08 | `chatgpt/code50-airbus2100-camera-gaze-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #616 | MERGED 2026-11-08 | `chatgpt/code50-context-inplace-tool-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #615 | MERGED 2026-11-08 | `chatgpt/code50-warm-speech-recognizer-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #614 | OPEN/DRAFT | `chatgpt/code50-hollywood-fui-foundation-20261108` | 14 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #613 | MERGED 2026-11-08 | `chatgpt/code50-reasoning-live-telemetry-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #612 | CLOSED | `chatgpt/code50-argon4-reasoning-workscene-20261108` | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt. |
| #611 | MERGED 2026-11-08 | `chatgpt/code50-reasoning-main-scene-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #610 | MERGED 2026-11-08 | `chatgpt/code50-airbus2100-footprints-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #609 | MERGED 2026-11-08 | `chatgpt/code50-local-work-window-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #608 | MERGED 2026-11-08 | `chatgpt/code50-avatar-proof-result-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #607 | MERGED 2026-11-08 | `chatgpt/code50-voice-budget-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #606 | MERGED 2026-11-08 | `chatgpt/code50-natural-app-intake-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #605 | CLOSED | `codex/code50-gate-fixes-20261108` | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt. |
| #604 | MERGED 2026-11-08 | `chatgpt/code50-recall-qa-fix-20261108` | 0 | (a) | Gemergt 2026-11-08 in `codex/continuum-code50-integration-20261107`; ahead=0 gegen #698. |
| #603 | OPEN/DRAFT | `codex/continuum-code50-integration-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #602 | OPEN/DRAFT | `codex/continuum-role-resolution-shadow-20261107` | 17 | (c) | Ersetzt durch EntityRoleResolutionEngine + resolveOrderedDeictic. |
| #601 | OPEN/DRAFT | `codex/continuum-discourse-shadow-v2-20261107` | 12 | (c) | Teilmenge von #602. |
| #600 | OPEN/DRAFT | `codex/continuum-cognitive-state-shadow-20261107` | 7 | (a) | Ctx3StateTelegram auf #698. |
| #599 | OPEN/DRAFT | `codex/continuum-avatar-live-proof-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #598 | OPEN/DRAFT | `codex/continuum-code49-reference-input-20261107` | 4 | (b) | Reference-Input-Test fehlt; Layout für Bot 07. |
| #597 | OPEN/DRAFT | `codex/continuum-dark-reference-layout-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #596 | OPEN/DRAFT | `codex/continuum-code49-elevenlabs-voice-design-20261107` | 1 | (c) | Nur ElevenLabs-Docs; Cloud nur mit Freigabe. |
| #595 | OPEN/DRAFT | `codex/continuum-code48-github-owner-20261107` | 9 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #594 | OPEN/DRAFT | `codex/continuum-code49-orb-render-performance-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #593 | OPEN/DRAFT | `codex/continuum-code48-release-completion-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #592 | OPEN/DRAFT | `codex/continuum-code48-recovered-active-paths-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #591 | OPEN/DRAFT | `codex/continuum-code48-fullstage-cinema-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #590 | OPEN/DRAFT | `codex/continuum-code48-connected-cinema-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #589 | OPEN/DRAFT | `codex/continuum-code48-workscene-thermal-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #588 | OPEN/DRAFT | `codex/continuum-code47-apk-20261107` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #587 | OPEN/DRAFT | `codex/continuum-code47-release-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #586 | OPEN/DRAFT | `codex/continuum47-planning-context-regression-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #585 | OPEN/DRAFT | `codex/continuum47-fallback-identity-contract-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #584 | OPEN/DRAFT | `codex/continuum47-pdf-word-order-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #583 | OPEN/DRAFT | `codex/continuum47-routing-regressions-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #582 | OPEN/DRAFT | `codex/continuum47-app-query-format-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #581 | MERGED 2026-11-06 | `codex/continuum47-response-completion-20261106` | 0 | (a) | Gemergt 2026-11-06 in `codex/continuum-code47-qa-candidate-20261106`; ahead=0 gegen #698. |
| #580 | MERGED 2026-11-06 | `codex/continuum47-context-identity-20261106` | 0 | (a) | Gemergt 2026-11-06 in `codex/continuum-code47-qa-candidate-20261106`; ahead=0 gegen #698. |
| #579 | MERGED 2026-11-06 | `codex/continuum47-natural-actions-20261106` | 0 | (a) | Gemergt 2026-11-06 in `codex/continuum-code47-qa-candidate-20261106`; ahead=0 gegen #698. |
| #578 | OPEN/DRAFT | `codex/continuum-code47-qa-candidate-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #577 | OPEN/DRAFT | `codex/continuum-build46-47-help-20261106` | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #576 | OPEN/DRAFT | `codex/continuum-code47-integration-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #575 | OPEN/DRAFT | `codex/continuum-owner-apk-20261106` | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #574 | OPEN/DRAFT | `claude/continuum-fable51-continuation-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #573 | OPEN/DRAFT | `codex/continuum-fold7-quality-lab-20261106` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #572 | OPEN/DRAFT | `codex/verify-cockpit-centre-windows-20261106` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #571 | OPEN/DRAFT | `codex/jarvis-camera-flight-20261106` | 6 | (a) | AvatarCamera* auf #698. |
| #570 | OPEN/DRAFT | `fix/continuum-code44-release-qa-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #565 | OPEN/DRAFT | `claude/continuum-jarvis-cockpit-z7bo60` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #564 | OPEN/DRAFT | `research/opus55-continuum-voice-hud-preflight-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #563 | OPEN/DRAFT | `research/continuum-cockpit-voice-original-orb-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #562 | OPEN/DRAFT | `support/continuum-pcm-silence-3e26-20261105` | 2 | (a) | PCM-Stille auf #698. |
| #561 | OPEN/DRAFT | `fix/continuum-original-backend-release-gate-20261105` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #560 | OPEN/DRAFT | `repair/continuum-owner-backend-gate-20261105-now` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #559 | OPEN/DRAFT | `fix/continuum-apk42-stabilize-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #558 | OPEN/DRAFT | `qa/orb-hypothesis-marker-20261105-b3-r3` | 8 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #557 | OPEN/DRAFT | `support/continuum-reference-binding-s5-20261105` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #556 | OPEN/DRAFT | `support/h4-avatar-stop-next-turn-regression-20261105` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #555 | OPEN/DRAFT | `fix/continuum-memory-r13-temporal-lexical-20261105` | 90 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #554 | OPEN/DRAFT | `support/continuum-apk-preflight-aapt2-20261105-b6` | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #553 | OPEN/DRAFT | `fix/continuum-memory-r13-relative-cues-20261105` | 86 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #552 | OPEN/DRAFT | `codex/continuum-owner-face-models-20261105` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #551 | OPEN/DRAFT | `bot1/avatar-blink-kinematics-20261105` | 34 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #550 | OPEN/DRAFT | `qa/orb-phase-finite-idle-20261105-b3-r2` | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #549 | OPEN/DRAFT | `bot2/isolated-neon-tile-energy-20261105` | 9 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #548 | OPEN/DRAFT | `fix/continuum-memory-r12-numeric-evidence-20261105` | 82 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #547 | OPEN/DRAFT | `support/continuum-dialog-layout-apk-20261105-3e26` | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #546 | OPEN/DRAFT | `codex/continuum-neon-finish-20261105` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #545 | OPEN/DRAFT | `codex/continuum-avatar-dialog-finish-20261105` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #544 | OPEN/DRAFT | `integration/continuum-avatar-complete-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #543 | OPEN/DRAFT | `integration/continuum-interim-owner-avatar-neon-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #542 | OPEN/DRAFT | `codex/continuum-orb-nonfinite-qa-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #541 | OPEN/DRAFT | `codex/continuum-neon-tiles-20261105` | 26 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #540 | OPEN/DRAFT | `codex/continuum-avatar-natural-blink-20261105` | 26 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #539 | OPEN/DRAFT | `integration/continuum-orb-neon-stage-20261105` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #538 | OPEN/DRAFT | `fix/continuum-memory-r11-single-digit-duplicate-negation-20261105` | 72 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #537 | OPEN/DRAFT | `fix/continuum-memory-r10-fuzzy-ambiguity-20261105` | 61 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #536 | OPEN/DRAFT | `fix/continuum-memory-r9-negation-fuzzy-boundary-20261105` | 43 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #535 | OPEN/DRAFT | `ccr-4f80075a-ycl67y` | 25 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #534 | OPEN/DRAFT | `fix/continuum-memory-r8-negation-k-boundary-20261105` | 24 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #533 | OPEN/DRAFT | `fix/continuum-memory-index-atomic-r7-20261105` | 14 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #532 | OPEN/DRAFT | `fix/continuum-memory-retriever-snapshot-r6-20261105` | 10 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #531 | OPEN/DRAFT | `fix/continuum-memory-r5-credential-boundaries-20261105` | 33 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #530 | OPEN/DRAFT | `research/continuum-lm1-resumable-training-20261104-319f3e` | 24 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #529 | OPEN/DRAFT | `fix/continuum-memory-privacy-r4-20261104` | 17 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #528 | OPEN/DRAFT | `research/continuum-cognition-learning-loop-20261104` | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #527 | OPEN/DRAFT | `integration/continuum-connected-r3-20261104-822e` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #526 | OPEN/DRAFT | `codex/continuum-avatar-alive-20261104` | 14 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #525 | OPEN/DRAFT | `fix/continuum-r6-lm1-capacity-evidence-20261104` | 21 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #524 | OPEN/DRAFT | `fix/continuum-r6-landscape-48dp-20261104` | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #523 | OPEN/DRAFT | `integration/continuum-completed-tuning-20261104-822e` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #522 | OPEN/DRAFT | `fix/continuum-brain-integration-r3-20261104` | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #521 | OPEN/DRAFT | `codex/continuum-reference-avatar-v2-20261104` | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #520 | OPEN/DRAFT | `integration/continuum-g1-r5-avatar-accept-20261104` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #519 | OPEN/DRAFT | `integration/continuum-c1-candidate-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #518 | OPEN/DRAFT | `codex/continuum-reference-avatar-20261104` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #517 | OPEN/DRAFT | `integration/continuum-finalization-20261104-18845` | 8 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #516 | OPEN/DRAFT | `codex/continuum-internal-bot-training-20261104` | 11 | (a) | ChiefBot* auf #698 verdrahtet. |
| #515 | OPEN/DRAFT | `codex/continuum-branch-integration-bots-20261104` | 32 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #514 | OPEN/DRAFT | `research/continuum-16m-training-support-r2-20261104-319f3e` | 16 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #513 | OPEN/DRAFT | `integration/continuum-apk-connections-wave2-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #512 | OPEN/DRAFT | `integration/continuum-followup-connectors-20261104-822e` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #511 | OPEN/DRAFT | `tuning/continuum-three-bots-20261104` | 71 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #510 | OPEN/DRAFT | `codex/continuum-bot-activation-20261104` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #509 | OPEN/DRAFT | `research/continuum-three-experiments-20261104` | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #508 | OPEN/DRAFT | `research/continuum-lm1-training-recovery-20261104` | 17 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #507 | OPEN/DRAFT | `research/continuum-16m-support-20261104-319f3e` | 11 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #506 | OPEN/DRAFT | `integration/continuum-ready-connections-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #505 | OPEN/DRAFT | `repair/continuum-three-bots-20261104` | 13 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #504 | CLOSED | `feat/continuum-inworld-router-20261104` | — | (c) | SHELVED Inworld-Mentor. |
| #503 | OPEN/DRAFT | `feature/continuum-navada-live-integration-20261104` | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #502 | OPEN/DRAFT | `feature/continuum-fable51-avatar-motion-20261104` | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #501 | OPEN/DRAFT | `gamma/parameter-audit-20261104` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #500 | OPEN/DRAFT | `research/continuum-fable51-jarvis-genesis-20261104` | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #499 | OPEN/DRAFT | `integration/continuum-memory-avatar-hooks-20261104` | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #498 | OPEN/DRAFT | `coordination/astra-nucleus-20261104-1027` | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #497 | OPEN/DRAFT | `research/continuum-weight-helper-team-20261104` | 7 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #496 | OPEN/DRAFT | `support/continuum-ten-bot-assist-20261104` | 10 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #495 | OPEN/DRAFT | `codex/continuum-three-avatar-helpers-20261104` | 17 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #494 | OPEN/DRAFT | `feat/continuum-memory-brain-20261104` | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #493 | OPEN/DRAFT | `feat/continuum-personality-bots-20261104` | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #492 | OPEN/DRAFT | `research/continuum-weight-space-audit-20261104` | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #491 | OPEN/DRAFT | `codex/astra-jarvis-takeover-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #490 | OPEN/DRAFT | `kern/cognitive-loop-20261104` | 7 | (c) | CognitiveLoop durch BoundedAgentOrchestrator ersetzt. |
| #489 | OPEN/DRAFT | `jarvis-mathematik/motion-math` | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #488 | OPEN/DRAFT | `grok/jarvis-closeout-20261104` | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #487 | OPEN/DRAFT | `jarvis-luecke/video3-alarm-warp-ganzkoerper` | 12 | (b) | video3-Rest (Alarm/Greeting/Calm) für Bot 04/07. |
| #486 | OPEN/DRAFT | `design/jarvis-menu-navigation-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #485 | OPEN/DRAFT | `continuum/m5-safe-flows-fg-20261104` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #484 | OPEN/DRAFT | `continuum/m2-calc-events` | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #483 | OPEN/DRAFT | `integration/jarvis-on-chatgpt-untested-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #482 | OPEN/DRAFT | `jarvis-grafik/avatar-ref-match` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #481 | OPEN/DRAFT | `design/jarvis-cockpit-home-20261104` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #480 | MERGED 2026-11-04 | `integration/continuum-grok1-m5-b5-untested-20261104` | 0 | (a) | Gemergt 2026-11-04 in `integration/continuum-grok1-369-372-20261103`; ahead=0 gegen #698. |
| #479 | OPEN/DRAFT | `grok/jarvis-design-system` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #478 | OPEN/DRAFT | `fix/continuum-device-experience-resume-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #477 | OPEN/DRAFT | `fix/continuum-device-experience-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #476 | OPEN/DRAFT | `design/continuum-reference-cockpit-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #475 | OPEN/DRAFT | `fix/continuum-status-history-20261104` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #474 | OPEN/DRAFT | `release/continuum-night-integration-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #473 | OPEN/DRAFT | `codex/independence-native-failure-review-20261103` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #472 | OPEN/DRAFT | `codex/independence-native-closure-20261103` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #471 | OPEN/DRAFT | `codex/independence-failure-review-20261103` | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #470 | OPEN/DRAFT | `codex/momega1-portrait-rings-20261103` | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #469 | OPEN/DRAFT | `codex/momega1-cli-fixtures-20261103` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #468 | MERGED 2026-11-03 | `codex/jarvis-reference-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #467 | OPEN/DRAFT | `codex/momega1-gvisor-fixture-20261103` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #466 | OPEN/DRAFT | `codex/momega1-layout-final-20261103` | 7 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #465 | MERGED 2026-11-03 | `codex/momega1-effects-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #464 | MERGED 2026-11-03 | `codex/jarvis-reference-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #463 | MERGED 2026-11-03 | `codex/independence-service-20261103` | 3 | (c) | Independence CCC1-Service; heutiger coding-service ist in der späteren Linie. |
| #462 | MERGED 2026-11-03 | `codex/independence-final-qa-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698. |
| #461 | MERGED 2026-11-03 | `codex/independence-native-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698. |
| #460 | MERGED 2026-11-03 | `codex/independence-mcp-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698. |
| #459 | MERGED 2026-11-03 | `codex/independence-mcp-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698. |
| #458 | MERGED 2026-11-03 | `codex/independence-service-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/continuum-independence-chief-20261103`; ahead=0 gegen #698. |
| #457 | MERGED 2026-11-03 | `codex/momega1-effects-caller-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #456 | OPEN/DRAFT | `codex/continuum-independence-chief-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #455 | OPEN/DRAFT | `codex/genesis-final-native-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #454 | OPEN/DRAFT | `codex/momega1-avatar-effects-review-20261103` | 11 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #453 | MERGED 2026-11-03 | `codex/momega1-visible-avatar-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #452 | MERGED 2026-11-03 | `codex/momega1-challenger-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #451 | MERGED 2026-11-03 | `codex/genesis-workspace-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698. |
| #450 | MERGED 2026-11-03 | `codex/genesis-caller-review-20261103` | 1 | (c) | Genesis caller review, Forschungs-/Repair-Notiz. |
| #449 | MERGED 2026-11-03 | `codex/momega1-runtime-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #448 | MERGED 2026-11-03 | `codex/genesis-brain-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698. |
| #447 | MERGED 2026-11-03 | `codex/momega1-spoken-guide-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #446 | MERGED 2026-11-03 | `codex/genesis-ui-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698. |
| #445 | OPEN/DRAFT | `codex/momega1-readiness-review-20261103` | 8 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #444 | MERGED 2026-11-03 | `codex/genesis-workspaces-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698. |
| #443 | OPEN/DRAFT | `codex/genesis-coding-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #442 | MERGED 2026-11-03 | `codex/momega1-android-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #441 | MERGED 2026-11-03 | `codex/genesis-workspace-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698. |
| #440 | MERGED 2026-11-03 | `codex/genesis-native-runtime-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698. |
| #439 | OPEN/DRAFT | `codex/bot-genesis-chief-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #438 | OPEN/DRAFT | `codex/genesis-brain-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #437 | MERGED 2026-11-03 | `codex/genesis-ui-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/bot-genesis-chief-20261103`; ahead=0 gegen #698. |
| #436 | MERGED 2026-11-03 | `codex/momega1-readiness-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #435 | OPEN/DRAFT | `codex/momega1-android-plan-review-20261103` | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #434 | MERGED 2026-11-03 | `codex/jarvis-reference-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #433 | OPEN/DRAFT | `codex/synapse-model-prerequisites-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #432 | MERGED 2026-11-03 | `codex/jarvis-reference-ui-20261103` | 1 | (a) | Avatar-Motion/Mund; heutiger Filament/Cinema-Pfad enthält Nachfolger. |
| #431 | OPEN/DRAFT | `codex/synapse-runtime-audit-20261103` | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #430 | MERGED 2026-11-03 | `codex/momega1-challenger-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #429 | MERGED 2026-11-03 | `codex/synapse-sources-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/synapse-swarm-chief-20261103`; ahead=0 gegen #698. |
| #428 | OPEN/DRAFT | `codex/synapse-ui-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #427 | MERGED 2026-11-03 | `codex/momega1-runtime-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #426 | MERGED 2026-11-03 | `codex/synapse-registry-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/synapse-swarm-chief-20261103`; ahead=0 gegen #698. |
| #425 | MERGED 2026-11-03 | `codex/synapse-lifecycle-20261103` | 1 | (a) | Shutdown-Reports; spätere Chief-Linie hat Nachfolger oder Inhalt portiert (ahead=1 oft Docs). |
| #424 | MERGED 2026-11-03 | `codex/synapse-lifecycle-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/synapse-swarm-chief-20261103`; ahead=0 gegen #698. |
| #423 | OPEN/DRAFT | `codex/synapse-swarm-chief-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #422 | MERGED 2026-11-03 | `codex/synapse-registry-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/synapse-swarm-chief-20261103`; ahead=0 gegen #698. |
| #421 | OPEN/DRAFT | `codex/synapse-p0-audit-20261103` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #420 | MERGED 2026-11-03 | `codex/momega1-workspace-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #419 | OPEN/DRAFT | `codex/momega1-android-review-20261103` | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #418 | MERGED 2026-11-03 | `codex/momega1-memory-review-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #417 | MERGED 2026-11-03 | `codex/momega1-workspaces-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #416 | MERGED 2026-11-03 | `codex/momega1-challenger-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #415 | MERGED 2026-11-03 | `codex/momega1-memory-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #414 | MERGED 2026-11-03 | `codex/momega1-android-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #413 | OPEN/DRAFT | `codex/momega1-qa-20261103` | 51 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #412 | MERGED 2026-11-03 | `codex/momega1-runtime-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #411 | MERGED 2026-11-03 | `codex/momega1-loop-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/momega1-chief-20261103`; ahead=0 gegen #698. |
| #410 | OPEN/DRAFT | `codex/momega1-chief-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #409 | MERGED 2026-11-03 | `codex/zeta-challenger-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #408 | MERGED 2026-11-03 | `codex/zeta-ui-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #407 | MERGED 2026-11-03 | `codex/delta-challenger-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #406 | MERGED 2026-11-03 | `codex/gamma-challenger-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #405 | MERGED 2026-11-03 | `codex/epsilon-android-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #404 | MERGED 2026-11-03 | `codex/living-jarvis-3d-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #403 | MERGED 2026-11-03 | `codex/team-05-quality-breakthrough-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #402 | OPEN/DRAFT | `codex/breakthrough-neural-20261103` | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #401 | MERGED 2026-11-03 | `codex/breakthrough-research-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #400 | MERGED 2026-11-03 | `codex/breakthrough-autonomy-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #399 | MERGED 2026-11-03 | `codex/breakthrough-memory-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #398 | MERGED 2026-11-03 | `codex/breakthrough-cognition-20261103` | 0 | (a) | Gemergt 2026-11-03 in `codex/team-chief/omega-20261103`; ahead=0 gegen #698. |
| #397 | OPEN/DRAFT | `codex/team-02-memory-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #396 | OPEN/DRAFT | `codex/team-05-quality-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #395 | OPEN/DRAFT | `codex/team-04-android-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #394 | OPEN/DRAFT | `codex/team-01-intelligence-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #393 | OPEN/DRAFT | `codex/team-03-ccc1-omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #392 | OPEN/DRAFT | `codex/team-chief/omega-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #391 | OPEN/DRAFT | `design/continuum-avatar-user-ui-20261103` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #390 | OPEN/DRAFT | `fix/continuum-code40-router-race-review-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #389 | OPEN/DRAFT | `coordination/codex-six-agent-team-20261103` | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #388 | MERGED 2026-11-03 | `fix/continuum-code40-chief-apk-gates-20261103` | 0 | (a) | Gemergt 2026-11-03 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698. |
| #387 | OPEN/DRAFT | `feature/continuum-alpha-squad-ccc1-entry-20261103` | 12 | (b) | SHA voraus; fehlende MAIN: `ChiefProofGatedStrategyLedger.kt`, `ChiefTaskGraph.kt` — siehe §5. |
| #386 | OPEN/DRAFT | `feature/continuum-chief-proof-gated-strategy-ledger-20261103` | 10 | (b) | SHA voraus; fehlende MAIN: `ChiefProofGatedStrategyLedger.kt`, `ChiefTaskGraph.kt` — siehe §5. |
| #385 | OPEN/DRAFT | `feature/continuum-chief-task-dag-20261103` | 8 | (b) | SHA voraus; fehlende MAIN: `ChiefTaskGraph.kt` — siehe §5. |
| #384 | OPEN/DRAFT | `integration/continuum-chief-team-staging-20261103` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #383 | OPEN/DRAFT | `feature/continuum-chief-specialist-delegation-20261103` | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #382 | OPEN/DRAFT | `repair/continuum-chief-routines-mailbox-20261103` | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #381 | OPEN/DRAFT | `repair/continuum-chief-cancellation-scope-20261103` | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #380 | OPEN/DRAFT | `integration/continuum-omega-sol61-20261103` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #379 | OPEN/DRAFT | `continuum/haupt-chief-pattern-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #378 | OPEN/DRAFT | `jarvis-grafik/avatar-live` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #377 | OPEN/DRAFT | `grok/chief-e2e-tests` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #376 | MERGED 2026-11-03 | `continuum/grok1-routines-a2a-20261103` | 0 | (a) | Gemergt 2026-11-03 in `integration/continuum-grok1-369-372-20261103`; ahead=0 gegen #698. |
| #375 | OPEN/DRAFT | `integration/continuum-grok1-369-372-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #374 | OPEN/DRAFT | `continuum/grok1-integration-20261103` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #373 | OPEN/DRAFT | `continuum/m3-grok-selbstrekonstruktion-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #372 | OPEN/DRAFT | `continuum/m5-integration-repair` | 19 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #371 | OPEN/DRAFT | `continuum/m2-cognitive-memory` | 18 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #370 | OPEN/DRAFT | `continuum/m2-grok-context-state-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #369 | OPEN/DRAFT | `continuum/m1-grok-multiturn-routing-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #368 | OPEN/DRAFT | `research/continuum-native-session-lifecycle-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #367 | OPEN/DRAFT | `research/continuum-omega-task-scoped-temporal-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #366 | OPEN/DRAFT | `research/continuum-omega-context-integrity-20261103` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #365 | MERGED 2026-11-03 | `repair/continuum-fold7-storage-verb-routing-20261103` | 0 | (a) | Gemergt 2026-11-03 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698. |
| #364 | OPEN/DRAFT | `repair/continuum-shizuku-composer-20261103` | 12 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #363 | OPEN/DRAFT | `research/continuum-fold7-chat-routing-repair-20261103` | 2 | (c) | Research-Zweig. |
| #362 | OPEN/DRAFT | `research/continuum-sol-max-autonomy-20261103` | 6 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #361 | OPEN/DRAFT | `research/continuum-sol61-jarvis-gates-20261102` | 1 | (c) | Research-Zweig. |
| #360 | OPEN/DRAFT | `integration/continuum-all-systems-apk-qa-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #359 | OPEN/DRAFT | `research/continuum-execution-atomicity-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #358 | OPEN/DRAFT | `research/continuum-sovereign-brain-iv-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #357 | OPEN/DRAFT | `research/continuum-sovereign-cognition-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #356 | OPEN/DRAFT | `research/continuum-sovereign-gguf-admission-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #354 | OPEN/DRAFT | `coderabbit/continuum-brain-integration-20261102` | 2 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #353 | OPEN/DRAFT | `coderabbit/continuum-reliability-sprint-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #352 | OPEN/DRAFT | `coderabbit/get-device-ai-project-status/de3d7eb3` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #351 | OPEN/DRAFT | `research/continuum-critical-qa-repair-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #350 | OPEN/DRAFT | `research/continuum-quantumflow-upstreams-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #349 | OPEN/DRAFT | `research/continuum-grand-challenge-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #348 | OPEN/DRAFT | `research/continuum-dialog-vii-repair-audit-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #347 | OPEN/DRAFT | `research/continuum-dialog-vi-nine-gates-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #346 | OPEN/DRAFT | `research/continuum-autonomy-dialog-voice-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #345 | OPEN/DRAFT | `research/continuum-fusion-iv-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #344 | OPEN/DRAFT | `research/continuum-synapse-iii-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #343 | OPEN/DRAFT | `research/continuum-synapse-ii-20261102` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #342 | OPEN/DRAFT | `research/continuum-341-brain-wiring-qa-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #341 | OPEN/DRAFT | `research/continuum-340-evidence-routing-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #338 | OPEN/DRAFT | `experiment/continuum-avatar-lab-offline-20261101` | 13 | (b) | SHA voraus; fehlende MAIN: `ContinuumAvatarLabActivity.kt` — siehe §5. |
| #337 | OPEN/DRAFT | `fix/continuum-fold7-stream-scroll-20261101` | 7 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #336 | OPEN/DRAFT | `release/continuum-local-0.3.36-render-20261101` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #335 | OPEN/DRAFT | `research/continuum-sol61-post-fable-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #334 | OPEN/DRAFT | `research/continuum-fable51-genesis-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #333 | OPEN/DRAFT | `research/continuum-jarvis-04-architecture-implementation-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #332 | MERGED 2026-11-01 | `research/continuum-live-dialog-routing-20261101` | 0 | (a) | Gemergt 2026-11-01 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698. |
| #331 | OPEN/DRAFT | `research/continuum-jarvis-evolution-20261101` | 1 | (c) | Research-Zweig. |
| #330 | OPEN/DRAFT | `research/continuum-evidence-span-natural-entities-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #329 | OPEN/DRAFT | `research/continuum-superki-architecture-proof-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #328 | OPEN/DRAFT | `release/continuum-local-0.3.35-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #327 | OPEN/DRAFT | `integration/continuum-ouroboros-ewm2-proof-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #326 | OPEN/DRAFT | `fix/continuum-m5-chunk-index-provenance-20261101` | 13 | (b) | SHA voraus; fehlende MAIN: `DevelopmentSourceChunkIndex.kt`, `DevelopmentSourceStreamImport.kt` — siehe §5. |
| #325 | OPEN/DRAFT | `fix/continuum-ewm2-archive-plural-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #324 | OPEN/DRAFT | `integration/continuum-offline-termux-proof-20261101` | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #323 | OPEN/DRAFT | `fix/continuum-sol314-source-plural-e-20261101` | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #322 | OPEN/DRAFT | `research/jarvis-autonomous-core` | 16 | (c) | Research-Zweig. |
| #321 | OPEN/DRAFT | `research/continuum-cdx1-negation-hardlock-20261101` | 11 | (c) | Research-Zweig. |
| #320 | OPEN/DRAFT | `fix/continuum-ewm2-role-age-production-20261101` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #319 | OPEN/DRAFT | `fix/continuum-sol314-kinship-age-qa-20261101` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #318 | OPEN/DRAFT | `research/continuum-m5-persistent-chunk-index-20261101` | 8 | (b) | SHA voraus; fehlende MAIN: `DevelopmentSourceChunkIndex.kt`, `DevelopmentSourceStreamImport.kt` — siehe §5. |
| #317 | MERGED 2026-11-01 | `research/continuum-sol-cdx1-evidence-20261101` | 0 | (a) | Gemergt 2026-11-01 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698. |
| #316 | OPEN/DRAFT | `research/continuum-m5-source-stream-20261101` | 4 | (b) | SHA voraus; fehlende MAIN: `DevelopmentSourceStreamImport.kt` — siehe §5. |
| #313 | OPEN/DRAFT | `fix/continuum-cdx1-collapse-diagnostics-20261101` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #312 | OPEN/DRAFT | `qa/continuum-owner-v34-independent-readback-20261101` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #311 | OPEN/DRAFT | `fix/continuum-v35-learning-preview-clarity-20261101` | 4 | (b) | SHA voraus; fehlende MAIN: `PersonalLearningPreviewCopy.kt` — siehe §5. |
| #310 | OPEN/DRAFT | `fix/continuum-v35-offline-long-input-20261031` | 18 | (b) | SHA voraus; fehlende MAIN: `ContinuumTermuxFailureDiagnosis.kt`, `ConversationResearchIntent.kt` — siehe §5. |
| #309 | OPEN/DRAFT | `fix/continuum-v35-owner-qa-intent-20261031` | 15 | (b) | SHA voraus; fehlende MAIN: `ContinuumTermuxFailureDiagnosis.kt`, `ConversationResearchIntent.kt` — siehe §5. |
| #305 | OPEN/DRAFT | `fix/continuum-web-learning-candidates-cleanup-20261101` | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #304 | MERGED 2026-10-31 | `feature/continuum-v35-agent-risk-tools-20261031` | 0 | (a) | Gemergt 2026-10-31 in `release/continuum-v35-integrated-20261031`; ahead=0 gegen #698. |
| #303 | MERGED 2026-10-31 | `research/continuum-m11-evidence-negation-gate-20261031` | 0 | (a) | Gemergt 2026-10-31 in `release/continuum-v35-integrated-20261031`; ahead=0 gegen #698. |
| #302 | MERGED 2026-10-31 | `release/continuum-v35-integrated-20261031` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #301 | OPEN/DRAFT | `release/continuum-local-0.3.34-20261031` | 7 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #300 | MERGED 2026-10-31 | `fix/continuum-v34-web-discovery-retry-20261031` | 0 | (a) | Gemergt 2026-10-31 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698. |
| #299 | OPEN/DRAFT | `research/continuum-m10-verified-brain-and-web-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #298 | MERGED 2026-10-31 | `integration/gitlab-mr299-web-study-20261031` | 0 | (a) | Gemergt 2026-10-31 in `research/continuum-m8-render-signed-release-20261031`; ahead=0 gegen #698. |
| #297 | MERGED 2026-10-31 | `release/continuum-v32-openrouter-fullweb-20261031` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #296 | OPEN/DRAFT | `feature/continuum-v32-openrouter-mentor-20261031` | 13 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #294 | OPEN/DRAFT | `fix/continuum-v32-network-web-recovery-20261031` | 1 | (c) | Research-Zweig. |
| #293 | MERGED 2026-10-31 | `release/continuum-local-complete-v31-20261031` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #292 | OPEN/DRAFT | `release/continuum-local-complete-v31-20261031` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #291 | OPEN/DRAFT | `release/continuum-local-v30-integration-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #288 | OPEN/DRAFT | `research/continuum-current-state-discovery-20261031` | 2 | (c) | Research-Zweig. |
| #287 | OPEN/DRAFT | `research/continuum-nemo-virtual-fabric-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #286 | OPEN/DRAFT | `research/continuum-m8-render-signed-release-20261031` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #285 | OPEN/DRAFT | `research/continuum-m8-operational-model-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #284 | OPEN/DRAFT | `research/continuum-jarvis-neuro-integration-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #283 | OPEN/DRAFT | `research/continuum-jarvis-intent-intake-20261031` | 4 | (c) | Research-Zweig. |
| #282 | OPEN/DRAFT | `research/continuum-compute-lease-20261031` | 2 | (c) | Research-Zweig. |
| #281 | OPEN/DRAFT | `research/continuum-context-validity-gate-20261031` | 2 | (c) | Research-Zweig. |
| #280 | OPEN/DRAFT | `research/continuum-evidence-recall-20261031` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #279 | OPEN/DRAFT | `chatgpt/continuum-render-readback-20261031` | 21 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #278 | OPEN/DRAFT | `chatgpt/continuum-p0-strict-release-metadata-20261031` | 17 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #277 | OPEN/DRAFT | `research/continuum-queue-bootstrap-grant-time-20261031` | 18 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #275 | OPEN/DRAFT | `research/continuum-cloudflare-owner-grant-20261031` | 16 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #274 | OPEN/DRAFT | `research/continuum-cloudflare-nvidia-direct-20261031` | 15 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #273 | OPEN/DRAFT | `research/continuum-nvidia-explicit-owner-consent-20261031` | 114 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #272 | OPEN/DRAFT | `research/continuum-nvidia-executor-env-gate-20261031` | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #271 | OPEN/DRAFT | `research/continuum-nvidia-claim-readback-20261031` | 108 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #270 | OPEN/DRAFT | `research/continuum-nvidia-single-use-claim-20261031` | 103 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #269 | MERGED 2026-10-30 | `research/continuum-nvidia-automation-ledger-20261030` | 32 | (c) | NVIDIA-Forschungsplan/Ledger, kein Produktpfad. |
| #268 | OPEN/DRAFT | `research/continuum-cloud-gpu-admission-20261030` | 102 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #267 | OPEN/DRAFT | `chatgpt/continuum-cdx1-legacy-v30-20261030` | 15 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #266 | OPEN/DRAFT | `chatgpt/continuum-fold7-single-pass-release-20261030` | 12 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #265 | OPEN/DRAFT | `claude/m7-m8-integration-kxrxy2` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #264 | OPEN/DRAFT | `claude/m8-gvisor-executor-kxrxy2` | 185 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #263 | OPEN/DRAFT | `research/continuum-jarvis-mcp-video-20261030` | 10 | (c) | Research-Zweig. |
| #262 | OPEN/DRAFT | `research/continuum-jarvis-video-evidence-20261030` | 8 | (c) | Research-Zweig. |
| #261 | OPEN/DRAFT | `chatgpt/continuum-upstream-first-20261030` | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #260 | OPEN/DRAFT | `chatgpt/continuum-one-tap-updates-20261030` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #259 | OPEN/DRAFT | `chatgpt/continuum-m8-benchmark-coverage-20261030` | 167 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #258 | OPEN/DRAFT | `chatgpt/continuum-m6-independent-holdout-20261030` | 244 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #257 | OPEN/DRAFT | `chatgpt/continuum-p0-updater-hardening-20261030` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #256 | OPEN/DRAFT | `claude/continuum-jarvis-exp20-p2-kxrxy2` | 217 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #255 | OPEN/DRAFT | `research/exp20-p2-context-contract-20261030` | 156 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #254 | OPEN/DRAFT | `research/exp20-p2-opus-project-brief-20261030` | 154 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #253 | OPEN/DRAFT | `research/jarvis-voice1-stale-recognizer-session-20261030` | 152 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #252 | OPEN/DRAFT | `opus/jarvis-voice1-fix-20261030` | 144 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #251 | OPEN/DRAFT | `research/continuum-owner-resilient-jarvis-charter-20261030` | 143 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #250 | OPEN/DRAFT | `research/jarvis-voice-conversation-owner-gated-20261030` | 131 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #249 | OPEN/DRAFT | `research/continuum-self-evolution-shadow-20261030` | 154 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #248 | OPEN/DRAFT | `research/continuum-mastercode-bounded-search-20261030` | 119 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #247 | OPEN/DRAFT | `research/jpi1-presence-initiative-shadow-20261030` | 121 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #246 | OPEN/DRAFT | `research/jpi1-presence-shadow-20261030` | 117 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #245 | OPEN/DRAFT | `opus/continuum-m9-m16-20261030` | 116 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #244 | OPEN/DRAFT | `opus/continuum-m1-m8-20261030` | 91 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #243 | OPEN/DRAFT | `research/continuum-counterexample-acceptance-20261029` | 81 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #242 | OPEN/DRAFT | `integration/continuum-brain-system-20261029` | 60 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `Argon4SystemShadow.kt`, `NeuralTutorBatchHint.kt` … — siehe §5. |
| #241 | OPEN/DRAFT | `research/sol61-ccc1-skill-transfer` | 52 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #240 | OPEN/DRAFT | `opus/argon4-research-20261029` | 52 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #239 | OPEN/DRAFT | `feature/continuum-model-web-learning-review-20261029` | 37 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt`, `AuthorizedWebLearningReviewInbox.kt` — siehe §5. |
| #238 | OPEN/DRAFT | `sol/continuum-dan-evidence-loop-20261029` | 42 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #237 | OPEN/DRAFT | `sol/continuum-dialog-integration-20261029` | 15 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #236 | OPEN/DRAFT | `feature/continuum-p0-inapp-owner-updater-20261029` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #234 | OPEN/DRAFT | `fix/continuum-dashboard-evidence-labels-20261029` | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #233 | OPEN/DRAFT | `fix/continuum-self-awareness-neuron-command-20261029` | 13 | (b) | SHA voraus; fehlende MAIN: `ContinuumSelfInspectionQuery.kt`, `NeuralTutorBatchHint.kt` — siehe §5. |
| #232 | OPEN/DRAFT | `research/continuum-language-core-update-contract-20261029` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #230 | OPEN/DRAFT | `sol/continuum-gpt6-closeout-20261029` | 8 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #229 | OPEN/DRAFT | `review/continuum-memory-attribute-guard-20261029` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #228 | OPEN/DRAFT | `review/ewm2-revocation-race-20261029` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #227 | OPEN/DRAFT | `opus/continuum-ewm2-integration-20261029` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #226 | OPEN/DRAFT | `research/gemma-monotonic-profile-20261029` | 2 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #225 | OPEN/DRAFT | `codex/worker-prestart-lease-path-20261029` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #224 | OPEN/DRAFT | `opus/continuum-brain-research-20261029` | 20 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #223 | OPEN/DRAFT | `sol/continuum-runtime-reliability-20261029` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #222 | OPEN/DRAFT | `codex/generation16-rescue-bom-bound-20261029` | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #221 | OPEN/DRAFT | `research/gemma-stream-callback-lifecycle-20261029` | 3 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #220 | OPEN/DRAFT | `codex/worker-descendant-stop-20261029` | 2 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #219 | OPEN/DRAFT | `codex/generation16-readonly-rescue-20261029` | 10 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #218 | OPEN/DRAFT | `sol/continuum-generation16-gemma-20261029` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #217 | OPEN/DRAFT | `research/continuum-v25-owner-ledger-export-20261028` | 5 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #216 | OPEN/DRAFT | `research/continuum-worker-untracked-gate-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #215 | OPEN/DRAFT | `sol/continuum-oneapp-brain-recovery-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #214 | OPEN/DRAFT | `research/continuum-sol-preflight-and-native-evidence-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #213 | OPEN/DRAFT | `research/continuum-sol-max-handoff-readiness-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #212 | OPEN/DRAFT | `research/continuum-local-integrated-gate-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #211 | OPEN/DRAFT | `research/continuum-selfhosted-coding-20261028` | 7 | (c) | Research-Zweig. |
| #210 | OPEN/DRAFT | `research/continuum-optional-free-coding-bridge-20261028` | 3 | (c) | Research-Zweig. |
| #209 | OPEN/DRAFT | `research/continuum-learning-parity-readonly-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #208 | OPEN/DRAFT | `research/continuum-local-microagents-20261028` | 21 | (b) | SHA voraus; fehlende MAIN: `ContinuumMicroAgentBridge.kt` — siehe §5. |
| #207 | OPEN/DRAFT | `research/continuum-evidence-bound-transfer-20261028` | 12 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #206 | OPEN/DRAFT | `research/continuum-local-brain-sol-20261028` | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #205 | OPEN/DRAFT | `chatgpt/continuum-german-research-contradiction-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #204 | OPEN/DRAFT | `chatgpt/continuum-fusion-regression-gate-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #203 | OPEN/DRAFT | `chatgpt/continuum-fusion-memory-vertical-20261028` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #198 | OPEN/DRAFT | `chatgpt/continuum-research-integration-memory-m4-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #197 | OPEN/DRAFT | `chatgpt/continuum-mainapp-m3-after-sol-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #196 | OPEN/DRAFT | `sol/continuum-local-owner-integration-20261027` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #195 | OPEN/DRAFT | `chatgpt/continuum-owner-current-qa-apk-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #194 | OPEN/DRAFT | `chatgpt/continuum-evidence-switch-probe-20261027` | 13 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #193 | OPEN/DRAFT | `chatgpt/continuum-gemma-reopen-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #192 | MERGED 2026-10-27 | `chatgpt/continuum-m2-20-gates-20261027` | 0 | (a) | Gemergt 2026-10-27 in `integration/continuum-owner-v26-live-candidate-20261027`; ahead=0 gegen #698. |
| #191 | MERGED 2026-10-27 | `chatgpt/continuum-m2-20-gates-20261027` | 0 | (a) | Gemergt 2026-10-27 in `integration/continuum-owner-v26-live-candidate-20261027`; ahead=0 gegen #698. |
| #190 | MERGED 2026-10-27 | `chatgpt/continuum-fold7-usable-navigation-20261027` | 0 | (a) | Gemergt 2026-10-27 in `integration/continuum-owner-v26-live-candidate-20261027`; ahead=0 gegen #698. |
| #189 | OPEN/DRAFT | `astra/continuum-autonomous-cycle-20261027` | 5 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #187 | OPEN/DRAFT | `integration/continuum-owner-v26-live-candidate-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #186 | OPEN/DRAFT | `chatgpt/fold7-reference-exact-grid-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #185 | OPEN/DRAFT | `chatgpt/continuum-coding-outcome-journal-20261027` | 3 | (c) | Research-Zweig. |
| #184 | OPEN/DRAFT | `sol/continuum-storage-prototype-20261027` | 3 | (c) | Research-Zweig. |
| #183 | OPEN/DRAFT | `sol/continuum-harness-fabric-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #182 | OPEN/DRAFT | `sol/continuum-cognitive-evidence-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #181 | OPEN/DRAFT | `chatgpt/continuum-jarvis-research-connection-audit-20261027` | 2 | (c) | Research-Zweig. |
| #180 | OPEN/DRAFT | `chatgpt/continuum-checked-shell-bounds-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #179 | OPEN/DRAFT | `integration/continuum-autonomy-learning-20261027` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #178 | OPEN/DRAFT | `sol/continuum-autonomy-contrastive-20261027` | 33 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #177 | OPEN/DRAFT | `chatgpt/continuum-local-worker-learning-lab-20261026` | 23 | (c) | Research-Zweig. |
| #176 | OPEN/DRAFT | `chatgpt/continuum-feedback-process-safety-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #175 | OPEN/DRAFT | `integration/continuum-feedback-review-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #174 | OPEN/DRAFT | `research/continuum-interactive-intelligence-lab-20261026` | 6 | (b) | SHA voraus; fehlende MAIN: `InteractiveDialogueContext.kt`, `InteractiveResearchRouting.kt` — siehe §5. |
| #173 | OPEN/DRAFT | `review/continuum-followup-archive-gate-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #172 | OPEN/DRAFT | `review/continuum-grounding-port-v24-20261026` | 4 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #171 | OPEN/DRAFT | `integration/continuum-owner-0323-review-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #170 | OPEN/DRAFT | `chatgpt/continuum-ccc1-evidence-completeness-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #169 | OPEN/DRAFT | `sol/continuum-jarvis-open-fabric-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #168 | OPEN/DRAFT | `research/continuum-virtual-model-fabric-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #167 | OPEN/DRAFT | `research/continuum-bottleneck-scorecard-local-escalation-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #166 | OPEN/DRAFT | `research/continuum-bottleneck-diagnosis-a-b-c-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #165 | OPEN/DRAFT | `integration/continuum-sol-numeric-binding-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #164 | OPEN/DRAFT | `review/continuum-ccc1-test-parity-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #163 | OPEN/DRAFT | `research/continuum-coin-wm-independent-audit-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #162 | OPEN/DRAFT | `integration/continuum-grounded-recall-fallback-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #161 | OPEN/DRAFT | `integration/continuum-typed-source-identity-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #160 | OPEN/DRAFT | `review/continuum-repair-learning-evidence-gate-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #159 | OPEN/DRAFT | `astra/continuum-brain-integration-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #158 | OPEN/DRAFT | `review/continuum-baby-memory-migration-20261026` | 10 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #157 | OPEN/DRAFT | `review/continuum-brain-safety-continuation-20261026` | 29 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #156 | OPEN/DRAFT | `sol/continuum-fast-repair-20261026` | 1 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #155 | OPEN/DRAFT | `sol/coding-discovery-safety-20261026` | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #154 | OPEN/DRAFT | `sol/deepseek-authenticated-bridge-20261026` | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #153 | OPEN/DRAFT | `sol/grounding-relations-20261026` | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #152 | OPEN/DRAFT | `sol/cdx-generation-repair-20261026` | 8 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #151 | OPEN/DRAFT | `sol/continuum-brain-breakthroughs-20261026` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #150 | OPEN/DRAFT | `sol/continuum-infrastructure-repair-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #149 | OPEN/DRAFT | `fix/continuum-local-model-quality-gemma-20261025` | 13 | (b) | SHA voraus; fehlende MAIN: `LocalModelImportPreset.kt` — siehe §5. |
| #148 | OPEN/DRAFT | `feature/continuum-model-center-selected-models-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #147 | OPEN/DRAFT | `integration/continuum-soul-infrastructure-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #146 | OPEN/DRAFT | `config/continuum-deepseek-gemma-selection-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #145 | OPEN/DRAFT | `research/continuum-native-foundation-20261025` | 5 | (c) | Research-Zweig. |
| #143 | OPEN/DRAFT | `integration/continuum-experiment-activation-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #142 | OPEN/DRAFT | `fix/continuum-orion47-definition-intent-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #140 | OPEN/DRAFT | `release/continuum-owner-v23-candidate-20261025` | 15 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #139 | OPEN/DRAFT | `integration/continuum-owner-v23-jarvis-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #138 | OPEN/DRAFT | `research/jarvis-international-adaptation-20261025` | 1 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #137 | OPEN/DRAFT | `research/continuum-p256-signature-review-chatgpt-20261025` | 42 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #136 | OPEN/DRAFT | `research/continuum-agent-handoff-standard-20261025` | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #135 | OPEN/DRAFT | `research/continuum-typed-review-chatgpt-20261025` | 40 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #134 | OPEN/DRAFT | `research/continuum-omega-resume-chatgpt-20261025` | 34 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #133 | OPEN/DRAFT | `research/continuum-omega-vhsr-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #132 | OPEN/DRAFT | `integration/continuum-m6-all-verified-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #131 | OPEN/DRAFT | `integration/continuum-m6-all-verified-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #130 | OPEN/DRAFT | `chatgpt/continuum-m6-run-binding-preflight-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #129 | OPEN/DRAFT | `chatgpt/continuum-typed-coding-isolation-recovery-20261025` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #128 | OPEN/DRAFT | `chatgpt/continuum-typed-android-coding-bridge-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #127 | OPEN/DRAFT | `chatgpt/continuum-coding-rejection-feedback-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #126 | OPEN/DRAFT | `chatgpt/continuum-coding-invalid-feedback-20261024` | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #125 | OPEN/DRAFT | `sol/continuum-autonomous-coding-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #124 | OPEN/DRAFT | `release/continuum-owner-0321-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #123 | OPEN/DRAFT | `integration/continuum-0320-all-connected-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #122 | OPEN/DRAFT | `chatgpt/continuum-cdx1-postrun-audit-20261024` | 3 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #121 | OPEN/DRAFT | `chatgpt/continuum-core-mirror-20261024` | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #120 | OPEN/DRAFT | `chatgpt/continuum-0321-local-dialogue-quality-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #119 | MERGED 2026-10-24 | `chatgpt/continuum-0321-fold7-orb-runtime-diagnostics-20261024` | 0 | (a) | Gemergt 2026-10-24 in `chatgpt/continuum-0320-v21-release-review-20261024`; ahead=0 gegen #698. |
| #118 | OPEN/DRAFT | `chatgpt/continuum-0320-v21-release-review-20261024` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #117 | OPEN/DRAFT | `astra/continuum-0320-visual-integration-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #116 | OPEN/DRAFT | `sol/continuum-0320-integration-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #115 | OPEN/DRAFT | `chatgpt/continuum-autonomous-dialogue-research-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #114 | MERGED 2026-10-24 | `chatgpt/continuum-owner-0.3.19-hologram-20261024` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #113 | OPEN/DRAFT | `chatgpt/continuum-hologram-context-recovery-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #112 | OPEN/DRAFT | `sol/continuum-max-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #111 | OPEN/DRAFT | `chatgpt/continuum-master-integration-v19-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #110 | OPEN/DRAFT | `chatgpt/continuum-m5-draft-approval-guard-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #109 | OPEN/DRAFT | `sol/continuum-m5-context-coding-experiments-20261024` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #108 | OPEN/DRAFT | `sol/continuum-master-m4-research-integration-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #107 | OPEN/DRAFT | `gpt6/continuum-m4-first-owned-app-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #106 | MERGED 2026-10-24 | `gpt6/continuum-0.3.18-release-preflight-20261023` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #105 | CLOSED | `fix/continuum-v18-render-dirty-audit-20261023` | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt. |
| #104 | MERGED 2026-10-23 | `fix/continuum-v18-cache-safe-git-gate-20261023` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #103 | MERGED 2026-10-23 | `fix/continuum-owner-v18-shallow-attestation-20261023` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #102 | MERGED 2026-10-23 | `fix/continuum-v18-render-detached-head-20261023` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #101 | MERGED 2026-10-23 | `integration/continuum-owner-apk-v18-candidate-20261023` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #100 | OPEN/DRAFT | `integration/continuum-owner-apk-v18-candidate-20261023` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #99 | OPEN/DRAFT | `opus/ccc1-e2e-20261023` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #98 | OPEN/DRAFT | `integration/continuum-premium-ui-preservation-20261023` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #97 | OPEN/DRAFT | `integration/continuum-current-review-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #96 | OPEN/DRAFT | `integration/continuum-composer-reflow-fix-20261023` | 4 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #95 | OPEN/DRAFT | `integration/continuum-verifier-first-spine-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #94 | MERGED 2026-10-23 | `integration/continuum-ui-opus-gguf-20261023` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #93 | OPEN/DRAFT | `integration/continuum-gguf-context-recovery-20261023` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #92 | OPEN/DRAFT | `opus/continuum-intelligence-next-20261023` | 15 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #91 | OPEN/DRAFT | `astra/continuum-live-hologram-20261023` | 5 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #90 | OPEN/DRAFT | `astra/continuum-visual-engineering-20261023` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #89 | MERGED 2026-10-23 | `fix/continuum-signed-archive-missing-release-20261023` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #88 | MERGED 2026-10-23 | `fix/continuum-signed-rollback-metadata-20261023` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #87 | MERGED 2026-10-23 | `fix/continuum-fold-motion-regression-gates-20261023` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #86 | MERGED 2026-10-23 | `fix/continuum-fold-live-responsive-20261023` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #85 | MERGED 2026-10-23 | `integration/continuum-aaa-lm1-ccc1-preview-20261023` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #84 | OPEN/DRAFT | `integration/continuum-aaa-lm1-ccc1-preview-20261023` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #83 | OPEN/DRAFT | `chatgpt/continuum-layout-pixel-alignment-20261023` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #82 | OPEN/DRAFT | `opus/continuum-neural-frontier-lab-20261022` | 31 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #81 | OPEN/DRAFT | `opus/continuum-aaa-ui-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #80 | OPEN/DRAFT | `feature/continuum-measured-neon-wave-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #79 | OPEN/DRAFT | `integration/continuum-aaa-bento-cockpits-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #78 | OPEN/DRAFT | `integration/continuum-full-feature-candidate-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #77 | OPEN/DRAFT | `integration/continuum-premium-ui-preview-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #75 | OPEN/DRAFT | `opus/continuum-language-core-20261022` | 23 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #74 | MERGED 2026-10-23 | `opus/continuum-premium-ui-20261022` | 0 | (a) | Gemergt 2026-10-23 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #73 | OPEN/DRAFT | `opus/continuum-synapse-v2-20261022` | 14 | (b) | SHA voraus; fehlende MAIN: `ContinuumSynapseZeroShadow.kt`, `SynapseContrastiveRoleProbe.kt`, `SynapseV2Reasoner.kt` — siehe §5. |
| #72 | OPEN/DRAFT | `opus/continuum-i2-selective-risk-20261022` | 38 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #71 | OPEN/DRAFT | `experiment/continuum-synapse-zero-20261022` | 6 | (b) | SHA voraus; fehlende MAIN: `ContinuumSynapseZeroShadow.kt`, `SynapseContrastiveRoleProbe.kt` — siehe §5. |
| #70 | OPEN/DRAFT | `chatgpt/continuum-argon-qf2-source-proof-20261022` | 27 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #69 | OPEN/DRAFT | `chatgpt/continuum-release-integration-audit-20261022` | 5 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #68 | OPEN/DRAFT | `chatgpt/continuum-i1-model-binding-20261022` | 35 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #67 | OPEN/DRAFT | `chatgpt/continuum-bundled-quality-qf2-20261022` | 22 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #66 | OPEN/DRAFT | `chatgpt/continuum-memory-retrieval-no-false-evidence-20261022` | 14 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #65 | OPEN/DRAFT | `chatgpt/continuum-navigation-signed-delivery-20261022` | 0 | (e) | Signer-/Release-Zweig, nicht in die Produktlinie. |
| #64 | OPEN/DRAFT | `chatgpt/continuum-app-navigation-completion-20261022` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #63 | MERGED 2026-10-22 | `opus/continuum-app-navigation-20261022` | 0 | (a) | Gemergt 2026-10-22 in `astra/render-mentor-apk-builder-20261019`; ahead=0 gegen #698. |
| #62 | OPEN/DRAFT | `opus/continuum-csx1-abstention-20261022` | 32 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #61 | OPEN/DRAFT | `chatgpt/continuum-residual-pointer-built-20261022` | 21 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #60 | OPEN/DRAFT | `chatgpt/continuum-csx1-research-gate-20261022` | 15 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #59 | OPEN/DRAFT | `chatgpt/continuum-intelligence-lab-readonly-20261022` | 12 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #58 | OPEN/DRAFT | `chatgpt/continuum-opus-android-coding-ui-20261022` | 13 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #57 | OPEN/DRAFT | `opus/continuum-mobile-coding-e2e-20261022` | 6 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #56 | OPEN/DRAFT | `review/render-first-portable-build-20261022` | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #55 | OPEN/DRAFT | `chatgpt/continuum-render-delivery-audit-20261022` | 3 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #53 | OPEN/DRAFT | `sol/continuum-isolated-research-20261021` | 4 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #52 | OPEN/DRAFT | `astra/render-mentor-apk-builder-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #51 | OPEN/DRAFT | `chatgpt/continuum-apk-preview-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #50 | OPEN/DRAFT | `chatgpt/continuum-llama-recovery-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #49 | OPEN/DRAFT | `astra/continuum-local-coding-milestone-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #48 | OPEN/DRAFT | `integration/continuum-astra-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #47 | MERGED 2026-10-21 | `astra/continuum-atomic-paired-checkpoints-20261021` | 0 | (a) | Gemergt 2026-10-21 in `integration/continuum-astra-20261021`; ahead=0 gegen #698. |
| #46 | OPEN/DRAFT | `astra/continuum-research-verification-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #45 | OPEN/DRAFT | `astra/continuum-atomic-paired-checkpoints-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #44 | OPEN/DRAFT | `astra/continuum-dual-core-binding-20261021` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #43 | MERGED 2026-10-21 | `sol/continuum-quality-20261021` | 0 | (a) | Gemergt 2026-10-21 in `astra/continuum-experiment-charter-20261019`; ahead=0 gegen #698. |
| #42 | OPEN/DRAFT | `astra/continuum-experiment-charter-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #40 | OPEN/DRAFT | `astra/render-mentor-apk-builder-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #39 | OPEN/DRAFT | `astra/continuum-optional-groq-mentor-20261019` | 16 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #38 | OPEN/DRAFT | `astra/continuum-samsung-research-hotfix-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #37 | OPEN/DRAFT | `astra/continuum-integrated-apk-20261019` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #36 | OPEN/DRAFT | `handoff/sol61-continuum-20261019` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #35 | OPEN/DRAFT | `astra/continuum-holographic-ui-20261019` | 62 | (b) | SHA voraus; fehlende MAIN: `ContinuumPcmLevel.kt` — siehe §5. |
| #34 | OPEN/DRAFT | `astra/continuum-blacklab-20261019` | 52 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #33 | OPEN/DRAFT | `astra/continuum-research-learning-gate-20261019` | 34 | (c) | Research-Zweig. |
| #32 | OPEN/DRAFT | `astra/continuum-offline-public-research-20261018` | 23 | (c) | Research-Zweig. |
| #31 | OPEN/DRAFT | `astra/continuum-always-on-web-20261018` | 10 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #30 | OPEN/DRAFT | `astra/continuum-dna-guardian-20261018` | 6 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #28 | OPEN/DRAFT | `continuum/argon-safety-review-20261017` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #27 | OPEN/DRAFT | `copilot/continuum-selective-correction-revocation-20261015` | 28 | (b) | SHA voraus; fehlende MAIN: `ContinuumAnswerCorrection.kt` — siehe §5. |
| #26 | OPEN/DRAFT | `copilot/continuum-owner-answer-corrections-20261015` | 25 | (b) | SHA voraus; fehlende MAIN: `ContinuumAnswerCorrection.kt` — siehe §5. |
| #25 | OPEN/DRAFT | `copilot/continuum-feedback-ledger-20261015` | 7 | (a) | SHA voraus, Produktdateien gleichen Pfads existieren auf #698. |
| #24 | OPEN/DRAFT | `copilot/continuum-android-integration-20261015` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #23 | MERGED 2026-10-15 | `copilot/continuum-contrastive-recovery-20261015` | 0 | (a) | Gemergt 2026-10-15 in `copilot/pr-12-working-first-vertical-slice`; ahead=0 gegen #698. |
| #22 | OPEN/DRAFT | `continuum-agent/activate-hourly-developer-agent` | 1 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #21 | OPEN/DRAFT | `continuum-agent/repair-22582016d3-e2cd043b` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #19 | CLOSED | `copilot/pr-12-working-first-vertical-slice` | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt. |
| #18 | OPEN/DRAFT | `copilot/inspect-extend-compute-distributed-task-code` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #17 | OPEN/DRAFT | `copilot/fix-64243701-1415878393-f443f816-2b3d-4c73-8097-d7df0cb722ca` | 7 | (c) | Research/Evidenz ohne fehlende MAIN-Quelle. |
| #16 | OPEN/DRAFT | `copilot/add-benchmark-measurement-harness` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #14 | OPEN/DRAFT | `copilot/add-deterministic-jvm-unit-tests` | 2 | (a) | SHA voraus, keine fehlende MAIN-Datei nachgewiesen. |
| #12 | OPEN | `copilot/copilotcontinuum-phase2-intelligence-direct` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #11 | OPEN/DRAFT | `copilot/continuum-phase2-intelligence-direct` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #10 | OPEN/DRAFT | `copilot/continuum-phase1-direct` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #9 | OPEN/DRAFT | `work/continuum-phase1-direct` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #8 | OPEN/DRAFT | `copilot/continuum-phase1-runtime` | 2 | (b) | SHA voraus; fehlende MAIN: `AiContextCompiler.kt`, `BenchmarkHarness.kt`, `CognitiveRaid.kt` … — siehe §5. |
| #6 | OPEN/DRAFT | `copilot/deepresearch-p0-durable-agent-memory` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #5 | OPEN/DRAFT | `copilot/part4-agent-research` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #4 | CLOSED | `docs/device-ai-open-source-reference-pack` | — | (c) | Geschlossen ungemerged; durch spätere Linie ersetzt oder explizit zurückgestellt. |
| #3 | OPEN/DRAFT | `copilot/part3-memory-model-ecology` | 0 | (a) | GitHub Compare: ahead=0 gegen #698. |
| #2 | MERGED 2026-10-12 | `copilot/main-task-review` | 0 | (a) | Gemergt 2026-10-12 in `main`; ahead=0 gegen #698. |
| #1 | MERGED 2026-10-12 | `v0.3-agent` | 17 | (c) | Historischer v0.3-Agent, durch Continuum-Linie ersetzt. |

## 14. Nachzug nach Heinz 14:18 Europe/Vienna

APK 52 soll der vollständige Nullpunkt sein. Diese Tabellen machen jeden Branch und jeden PR sichtbar, damit danach niemand sagen kann, etwas sei ungezählt geblieben.

Noch nicht in *diesem* Produktbranch eingebaut, aber gezählt und zugewiesen:

1. #699/#701/#702 und `code52/grok-bot15-node-reliability-20261110` — Bot 16 (#706) / Bot 04 (Orchestrator).
2. `code52/final-integrator-ui-fixes-20261110` und #598 — Bot 07.
3. #487 video3-Rest — Bot 04/07.
4. `feature/continuum-dual-control-profiles-20261030` (`ContinuumDualControlPolicy.kt`) und die M8-MAIN-Dateien aus §5 (Presence/Initiative, MemoryQuestionResolver, MemoryTimeline, LocalModelClaimAudit, SelfInspection) — nächste sichere Portierungsrunde, nicht als toter Code und nicht über 2700 Commits Drift blind mergen.
5. Signer-Linie (#700/#704/#682/#675/#668/#636, `code52/grok-bot16-signer-rc-binding-20261110`) — Klasse (e), eigener Pfad.

Keine Branches gelöscht. Kein Force-Push. Kein Merge nach main.
