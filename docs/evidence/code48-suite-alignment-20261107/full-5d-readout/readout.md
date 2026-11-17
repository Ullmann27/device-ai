# Android-Gesamtlauf 5d – Szenen, Training und Skips

Quelle `5d91442e98f78a2af623b8a3c74920805f670cb9`. Ausschließlich archivierte XML aus `/workspace/scratch/3d1967be6193/qa/full-android-failure-5d/junit/`.

**664 XML · 3776 Tests · 3754 PASS · 2 FAIL · 0 Errors · 20 SKIP.** Alle XML-Hashes stimmen mit dem Archivmanifest überein. `lintDebug` wurde wegen des Unit-Test-Fehlers nicht ausgeführt.

## Angefragte Klassen

| Klasse | PASS / FAIL / SKIP | XML SHA256 |
|---|---|---|
| `at.heinz.deviceai.FullStageMemoryReviewUiTest` | 6/0/0 | `a8f692362e9d1080d52f4ac59ab2e3d3aab8150912466465c0cd75b154548019` |
| `at.heinz.deviceai.learning.ContinuumTrainingCancellationUiTest` | 4/0/0 | `3b8c458a52e1c2e729e2ff750558fdb97a6919e1a73f91c8879d34818e342eb7` |

| Klasse | Methode | Status |
|---|---|---|
| `FullStageMemoryReviewUiTest` | `theEntireActualControlRectangleStaysOutsideTheCompositor` | PASS |
| `FullStageMemoryReviewUiTest` | `actualWindowCloseAndSystemTapRemainUsableAboveTheScene` | PASS |
| `FullStageMemoryReviewUiTest` | `falseSignalsAndAnEventFloodCannotInventOrExtendTheEpisode` | PASS |
| `FullStageMemoryReviewUiTest` | `actualActivityNoticeApiStaysVisibleAndDismissibleDuringMemoryFlight` | PASS |
| `FullStageMemoryReviewUiTest` | `realStopRejectsQueuedMemoryAndChatReparentingCannotReplay` | PASS |
| `FullStageMemoryReviewUiTest` | `actualActivityMemoryBusEnlargesAndReturnsTheOriginalOrb` | PASS |
| `ContinuumTrainingCancellationUiTest` | `realStorageFailureRemainsFailedAndRetryable` | PASS |
| `ContinuumTrainingCancellationUiTest` | `realStorageFailureAfterDeadlineRemainsFailedAndRetryable` | PASS |
| `ContinuumTrainingCancellationUiTest` | `expiredBudgetBeforeCdxStartIsDeferredWithoutFailure` | PASS |
| `ContinuumTrainingCancellationUiTest` | `ownerPauseBeforeCdxStartIsInterruptedWithoutFailure` | PASS |

**Abgrenzung:** Der echte neue Recall nach STOP ist in einer anderen Klasse fehlgeschlagen. Der grüne queued-STOP-/Reparent-Test darf nicht als Nachweis für diesen frischen Folge-Recall verwendet werden. Die vier grünen Trainingsfälle belegen Budget-/Pause-Abbruch sowie den erhaltenen Fehler-/Retry-Status bei echten kontrollierten Speicherfehlern, einschließlich Fehler nach Deadline; keine Diagnose des tatsächlichen Handyfehlers.

## Zwei Fehler des Gesamtlaufs

| Klasse / Methode | Meldung | XML SHA256 |
|---|---|---|
| `at.heinz.deviceai.JarvisLiveAvatarWiringUiTest.semanticMenuDestinationsOpenTheirNamedScreensWithoutStartingATurn` | java.lang.AssertionError: The logic destination must open a real dialog | `117f0b800f365cb3ecf06556eb5542f2e833ff1a40c4f696ba588eb837a36617` |
| `at.heinz.deviceai.cockpit.MemoryEventMapperStopUiTest.freshRecallAfterStopStillReachesTheVisibleSurface` | java.lang.AssertionError: Fresh recall must animate | `73a630c5fb038a30e7c9ab85e87524d73a8c84795bfbc99ac12c82cffb4a6eab` |

## 20 Skips nach Gruppen

**XML-Grundfelder sind in allen 20 Fällen leer.** Nachfolgende Erklärungen stammen separat aus den unveränderten 5d-Testquellen, nicht aus erfundenen Laufzeitmeldungen. Die Werte von Umgebungsvariablen wurden nicht gelesen.

| Gruppe | Anzahl | Einordnung |
|---|---:|---|
| Explizite PENDING-Spezifikationen | 12 | 6 ChiefPattern + 6 FirstThinkingRun: `@Ignore`, überwiegend Kommentar-/Leer-Testkörper. Historische #373/#379-Aussagen sind kein aktueller Feature-Befund. |
| Opt-in Host-/Containerbeweis | 2 | `CONTINUUM_WORKSPACE_CHIEF_HOST_PROOF == "1"` erforderlich. |
| Externe Node↔Kotlin-Paritätsfixtures | 3 | Zwei SWB-Tests benötigen `CONTINUUM_CONTRASTIVE_PARITY_DIR`, ein CRE1-Test `CONTINUUM_CRE1_PARITY_DIR`. |
| Host-JDK21-Voraussetzung | 2 | LiteRT-Lifecycle-Tests verlangen `hostJava >= 21`. |
| Optionaler Motionclip-Export | 1 | Exportziel `continuum.ui.evidenceDir` erforderlich. |

| Klasse / Methode | Gruppe / statischer Grund | XML SHA256 |
|---|---|---|
| `at.heinz.deviceai.ContinuumVisualEvidenceUiTest.fold7InnerMotionClip` | **OPTIONAL_MOTION_EXPORT**: assumeTrue("clip frames are evidence only", dir != null); requires continuum.ui.evidenceDir | `abc7175bccef2ce7127f6afa6a712b725b075cd74018913050184ac34b3434b1` |
| `at.heinz.deviceai.chief.ChiefPatternE2ETest.s7_pending_agentMessagesUrgentWakesInfoWaits` | **EXPLICIT_PENDING_SPEC**: PENDING Punkt 7 (Continuum 1): Keine Agent-zu-Agent-Nachrichten mit Stufen dringend/Info auf #373. | `2914eb3688c1ddc46e7e8ed06c12e4535e48383431d15790165bf61ab3b5b546` |
| `at.heinz.deviceai.chief.ChiefPatternE2ETest.s4b_pending_profileDiarySharedLayers` | **EXPLICIT_PENDING_SPEC**: PENDING Punkt 4 (Continuum Gedaechtnis): Schichten Profil/Tagebuch/geteiltes Wissen fuer alle Agenten gibt es auf #373 nicht als API. Vorhanden sind nur MemoryType (SELF/EPISODIC/SEMANTIC) und HybridMemoryRetriever; ein agentenuebergreifender Speicher fehlt. | `2914eb3688c1ddc46e7e8ed06c12e4535e48383431d15790165bf61ab3b5b546` |
| `at.heinz.deviceai.chief.ChiefPatternE2ETest.s8d_pending_limitGuardIsWiredBeforeWorkerStart` | **EXPLICIT_PENDING_SPEC**: PENDING Punkt 8 (REPERATEUR): ConstrainedResourceFabric ist im Produktivcode nirgends aufgerufen (nur Tests). Es fehlt die Verdrahtung des RAM-/Temperatur-/Nutzungslimitwaechters vor Modell-/Arbeiterstart. | `2914eb3688c1ddc46e7e8ed06c12e4535e48383431d15790165bf61ab3b5b546` |
| `at.heinz.deviceai.chief.ChiefPatternE2ETest.s5b_pending_textFileSkillLibraryLoadedOnDemand` | **EXPLICIT_PENDING_SPEC**: PENDING Punkt 5 (Continuum Gedaechtnis): Faehigkeiten-Bibliothek als Textdatei (Name + wann benutzen + Anleitung), bei Bedarf geladen, existiert auf #373 nicht. | `2914eb3688c1ddc46e7e8ed06c12e4535e48383431d15790165bf61ab3b5b546` |
| `at.heinz.deviceai.chief.ChiefPatternE2ETest.s6_pending_routineReportsOnlyWhenSomethingIsNew` | **EXPLICIT_PENDING_SPEC**: PENDING Punkt 6 (Continuum 1): Kein Routinen-Speicher mit Zeitplan/Ausloeser und 'nur bei Neuem melden'. Vorhandene learning/*Scheduler sind Lern-/Studienjobs, keine Nutzer-Routinen. | `2914eb3688c1ddc46e7e8ed06c12e4535e48383431d15790165bf61ab3b5b546` |
| `at.heinz.deviceai.chief.ChiefPatternE2ETest.s2b_pending_workerHasOwnBriefOwnToolAllowlistAndReportsBack` | **EXPLICIT_PENDING_SPEC**: PENDING Punkt 2 (HAUPT): Es gibt noch keine Chief/Arbeiter-API mit eigenem Auftrag, eigener Werkzeug-Whitelist und strukturiertem Bericht zurueck. Heute ist ein 'Arbeiter' nur ein zweiter BoundedAgentOrchestrator-Lauf; docs/CHIEF-PATTERN.md existiert auf #373 noch nicht. | `2914eb3688c1ddc46e7e8ed06c12e4535e48383431d15790165bf61ab3b5b546` |
| `at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest.actualPoolMailboxAndTwoFixedNodeContainersReturnOnlyObservations` | **OPT_IN_HOST_WORKSPACE**: assumeTrue(CONTINUUM_WORKSPACE_CHIEF_HOST_PROOF == "1"); opt-in real Node/container workspace proof | `f53d3659590021e59447b9daaabf204554169dfbbb33c82126c07425e2ed58a4` |
| `at.heinz.deviceai.chief.ChiefWorkspaceWiringHostTest.cancellationOfRealContainerClosesRunnerAndReportsToChiefMailbox` | **OPT_IN_HOST_WORKSPACE**: assumeTrue(CONTINUUM_WORKSPACE_CHIEF_HOST_PROOF == "1"); opt-in real Node/container workspace proof | `f53d3659590021e59447b9daaabf204554169dfbbb33c82126c07425e2ed58a4` |
| `at.heinz.deviceai.chief.FirstThinkingRunE2ETest.schritt4d_handeln_eingeschleusterBefehlInWerkzeugausgabeWirdIgnoriert` | **EXPLICIT_PENDING_SPEC**: PENDING – wartet auf REPERATEUR: S4-Injektionsschutz + Freigabe-Gate in der Schleife (Klasse/Methode im Integrationszweig noch nicht vorhanden). | `55974f434d945678e8694cf8f88d2debfd8c799ece1f4319180ab7ca91b7e22f` |
| `at.heinz.deviceai.chief.FirstThinkingRunE2ETest.schritt1b_ziel_turnLifecycleQuittiertGenauEinmal` | **EXPLICIT_PENDING_SPEC**: PENDING – wartet auf HAUPT: chief/TurnLifecycle.acknowledge()/deliver() (PR #379, continuum/haupt-chief-pattern-20261103) ist noch nicht im Integrationszweig. | `55974f434d945678e8694cf8f88d2debfd8c799ece1f4319180ab7ca91b7e22f` |
| `at.heinz.deviceai.chief.FirstThinkingRunE2ETest.schritt4c_handeln_alsChiefWorkerPoolJobMitWerkzeugScope` | **EXPLICIT_PENDING_SPEC**: PENDING – wartet auf HAUPT (Merge #379) + Lumo: Aufgabenschleife als ChiefWorkerPool-Job in chief/ (WorkerBrief/WorkerScope.runner/model) mit Geraetewerkzeugen nur im Job-Scope. | `55974f434d945678e8694cf8f88d2debfd8c799ece1f4319180ab7ca91b7e22f` |
| `at.heinz.deviceai.chief.FirstThinkingRunE2ETest.schritt6b_lernen_hypotheseUndTagebuchEintrag` | **EXPLICIT_PENDING_SPEC**: PENDING – wartet auf Continuum Gedaechtnis: Reflexionsschritt 'was habe ich gelernt' -> Hypothesen-Speicher + datierter Tagebuch-Eintrag pro Schleifenschritt (keine Klasse im Integrationszweig). | `55974f434d945678e8694cf8f88d2debfd8c799ece1f4319180ab7ca91b7e22f` |
| `at.heinz.deviceai.chief.FirstThinkingRunE2ETest.schritt3b_erinnern_geteiltesWissenUeberLayersSharedSearch` | **EXPLICIT_PENDING_SPEC**: PENDING – wartet auf Continuum Gedaechtnis + HAUPT: MemoryLayers.shared()/search() und Anbindung 'jeder Schleifenschritt liest relevantes Gedaechtnis' existieren in keinem Zweig. | `55974f434d945678e8694cf8f88d2debfd8c799ece1f4319180ab7ca91b7e22f` |
| `at.heinz.deviceai.chief.FirstThinkingRunE2ETest.schritt7b_naechsterSchritt_wirdStrukturiertGeplantUndAngestossen` | **EXPLICIT_PENDING_SPEC**: PENDING – wartet auf Lumo: Aufgabenschleife (chief/) muss einen strukturierten naechsten Schritt liefern und selbststaendig den naechsten Durchlauf anstossen; heute steht er nur im Modelltext. | `55974f434d945678e8694cf8f88d2debfd8c799ece1f4319180ab7ca91b7e22f` |
| `at.heinz.deviceai.learning.ContinuumContrastiveParityTest.actualNodeKotlinNodeTrainingParity` | **EXTERNAL_PARITY_FIXTURES**: Requires nonblank CONTINUUM_CONTRASTIVE_PARITY_DIR containing Node training vectors | `68168a5caabb4818c2dfc77b615e11322e4348a6cd4d98a32f55515daf7e64b2` |
| `at.heinz.deviceai.learning.ContinuumContrastiveParityTest.actualNodeKotlinNodeSwb2TrainingParityAfter350Rounds` | **EXTERNAL_PARITY_FIXTURES**: Requires nonblank CONTINUUM_CONTRASTIVE_PARITY_DIR containing Node training vectors | `68168a5caabb4818c2dfc77b615e11322e4348a6cd4d98a32f55515daf7e64b2` |
| `at.heinz.deviceai.learning.Cre1InferenceEncoderTest.trainedNodeCheckpointMatchesKotlinFeaturesAndScores` | **EXTERNAL_PARITY_FIXTURES**: Requires nonblank CONTINUUM_CRE1_PARITY_DIR containing CI CRE1 fixtures | `299564c7e07af874b82dcbe2c6ff3e6904f787e19c35bc05ac76c4b14fd055cc` |
| `at.heinz.deviceai.localmodel.LocalModelReloadLifecycleTest.failureBeforeAnyModelWasLoadedCanRetryWithoutUnload` | **HOST_JDK21_REQUIRED**: @Before assumeTrue("LiteRT 0.18 lifecycle host tests require JDK21", hostJava >= 21) | `542a0e55042a5b5b24896f67a4478bd3c1a9c1c52078a1882b39006a9cfa4435` |
| `at.heinz.deviceai.localmodel.LocalModelReloadLifecycleTest.failedRuntimeWithRetainedModelRefusesReloadUntilExplicitUnload` | **HOST_JDK21_REQUIRED**: @Before assumeTrue("LiteRT 0.18 lifecycle host tests require JDK21", hostJava >= 21) | `542a0e55042a5b5b24896f67a4478bd3c1a9c1c52078a1882b39006a9cfa4435` |

Vollständige Quellenpfade, Quell-SHAs, Git-Blob-IDs, Guard-Zeilen und XML-Pfade: `readout.json`. Keine neuen Tests oder Projektprogramme gestartet, keine Quellen geändert.
