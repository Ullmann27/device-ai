# C1-PATH-010 — Quellbeleg der lokalen Eingabe- und Recall-Strecke

**Status: reine Quellprüfung. Keine Tests oder Builds ausgeführt, keine alten PASS-Ergebnisse übertragen.**

## Quellenbindung

- Gelesener Head: `ced43fbe26cfc97c5c8fbbc3f429cd0845047c4f`.
- App-Tree: `91b313577cc3bf15ec73423e15cbadafb3c0acd6`; App-Arbeitsstand bei Abschluss der Lesung sauber.
- Checkout: `/workspace/scratch/174c84456ee3/continuum-c1`.
- Später von C1 gemeldeter Head: `165fc9f58ae9e36295690df60145174fd5dc58e7` (kleiner Bot-Nullprofil-Zweig und Tests). Dieser Head ist **nicht** Gegenstand dieser Prüfung.

## Korrigierter Avatar-Anschluss

Der gelesene Konstruktor verwendet initial `AvatarDirector(JarvisAvatarSlot.fanOut { Handler(mainLooper).post(it) })`. Später ruft `AgentActivity.buildConversationPane()` nachweislich `avatarDirector.attach(liveHologram)` auf. **C1 hat keinen Ersatz des initialen fanOut beschlossen. Die frühere Aussage einer Änderungsabsicht war falsch und wird zurückgenommen.** Ein dauerhaftes fanOut über das spätere Attach hinaus ist durch diese Prüfung nicht belegt.

## Eingabe bis Rückmeldung

1. `sendCurrentInput()` → direkt oder über `runContinuumDialogue()` zu `runLocal()`. Deterministische Status-/Bootstrap-/Faktantworten können vorher enden.
2. `runLocal()` verlangt geladenes Modell/READY und bindet Sitzung, Cancellation und Modell; `LocalFollowUpTaskFrame` baut den Auftrag.
3. Normale Owner-Strecke: `memoryStore.all() + ownerFactMemories()` → `AiContextCompiler.compile()` → `HybridMemoryRetriever.unambiguousRecords()/retrieve()`. Privacy, Validierungszustand, ganze Evidenzgruppen und Kontextbudget bleiben erhalten. Fachbots erhalten keinen privaten Owner-Kontext; Kompilierung ist an vorhandenen `continuumState` gebunden.
4. `BoundedAgentOrchestrator`/`AgentModel` → `generateLocal()` → `LocalToolContinuation.submit()` → `localRuntime.stream()` bzw. `respondToTools()`.
5. Strukturierter Werkzeugvorschlag → Orchestrator → `createToolGate()` → `ToolGate.execute()` → tatsächlicher `ToolRunner.execute()`. Validierung, Receipts/Replay, Risikofreigaben, Budget und Deadline gehen voraus; Beobachtungen speisen die lokale Fortsetzung.
6. Aktuelle Ergebnislieferung → `addAssistantMessage(visibleAnswer)`/Sitzungskontext; `setBusy()`/`refreshHologramState()` → `hologramView.setState()` und `avatarDirector.onRuntime()`. `evaluationConfirmsOutcome=false` verhindert eine Gleichsetzung von Textbewertung und bestätigter Werkzeugwirkung.

## Recall-Observer bis sichtbare Kugel

1. Lazy Activity-Compiler: `.withRecallObserver(ContinuumMemoryRecallPublisher(filesDir))`.
2. Erfolgreiches `compile()` meldet nur aufgenommene IDs/Scores/Ränge; ausgelassene Gruppe ergibt null Treffer, fehlgeschlagene Kompilierung kein Ereignis.
3. Publisher → kanonischer appgebundener `ContinuumMemoryEventHub` → `RecallHits` mit gesalzenem Query-Hash, ohne Frage-/Erinnerungstext. Ohne Listener kein Publish.
4. `subscribeMemoryEvents()` → `MemoryEventSources.forApp(filesDir)` → `ContinuumMemoryEventSource.subscribe(mapper)`. Das alte `production == null` wird hier nicht benutzt.
5. `MemoryEventMapper` bündelt den letzten Zustand → `Handler(mainLooper)` nach standardmäßig 100 ms → `flush()` → `cockpitBus.emit(RecallHits(MEMORY_TURN,...))`.
6. `CockpitAnimationPresenter.present()` → angebundene `home.avatarRenderer.onAnimationEvent()` → angedockter `JarvisHoloAvatar(home.hologram)`.
7. `JarvisSequenceView.render()/apply()` behandelt `MEMORY_TURN` vor der Gesprächs-Turn-Filterung → `BrainOrbAnimationRenderer.render()` → `JarvisSequenceEffects.recallLight()`.
8. `JarvisHoloAvatar.attach()` verbindet `setCockpitSequence(seq)` mit derselben Live-Oberfläche. `ContinuumHologramView.onDraw()` liest `recallBrightness(now)` für `ContinuumBrainOrbRenderer.Signals(recall=...)`.

**Grenzen:** Recall bestätigt Kontextauswahl, keinen gestarteten Modelllauf, keine richtige Antwort und keine bestätigte Speicherung. Null Treffer löschen altes Recall-Licht. Teardown: `mapper.detach(); subscription.close(); activity.close()`. Sprach-/Visemereignisse aus TTS (`speechStarted`, `wordRange(..., audioFrame=frame)`, Ende/Stopp) laufen separat.

## Vorhandene Klassen für das Android-Gate

Die folgenden vollständig qualifizierten Klassen sind vorhandene Prüfumfänge, **keine aktuellen Testergebnisse**:

### Kontext

- `at.heinz.deviceai.cognitive.AiContextCompilerTest`
- `at.heinz.deviceai.cognitive.AiContextIntegrityTest`
- `at.heinz.deviceai.cognitive.MemoryContextBoundaryTest`
- `at.heinz.deviceai.cognitive.MemoryRecallObservationTest`

### Recall und Handler

- `at.heinz.deviceai.cockpit.CompiledMemoryOrbIntegrationTest`
- `at.heinz.deviceai.cockpit.ContinuumMemoryEventSourceIntegrationTest`
- `at.heinz.deviceai.cockpit.MemoryEventFactoryIntegrationTest`
- `at.heinz.deviceai.MemoryRecallActivityUiTest`
- `at.heinz.deviceai.ui.JarvisMemorySignalWiringUiTest`
- `at.heinz.deviceai.ui.JarvisSequenceTurnLifecycleUiTest`

### Live-Avatar und TTS

- `at.heinz.deviceai.JarvisLiveAvatarWiringUiTest`
- `at.heinz.deviceai.avatar.JarvisHoloAvatarUiTest`
- `at.heinz.deviceai.AvatarTtsLiveConnectionUiTest`
- `at.heinz.deviceai.AvatarRangeFrameConnectionUiTest`
- `at.heinz.deviceai.AvatarSpeechReattachmentUiTest`

### Modell und ToolGate

- `at.heinz.deviceai.localmodel.LocalToolContinuationTest`
- `at.heinz.deviceai.cognitive.BoundedAgentTest`
- `at.heinz.deviceai.cognitive.BoundedAgentOutcomeEvidenceTest`
- `at.heinz.deviceai.AgentToolLayerTest`
- `at.heinz.deviceai.ToolReplayGuardTest`
- `at.heinz.deviceai.ToolDurableOutcomeTest`

## Grenzen der vorhandenen Tests

- `CompiledMemoryOrbIntegrationTest`: echte Compiler-/Hub-/Mapper-/Effektklassen; manuell abgearbeiteter Main-Thread-Scheduler.
- `MemoryRecallActivityUiTest`: echte Activity-Konstruktion, Compiler-Getter, Subscription und Android-Handler; direkt abonnierter Effektrenderer, kein vollständiger `onCreate`-/Modelllauf.
- Kein hier gelesener Einzeltest beweist die vollständige reale Eingabe → Modell/Tool → Rückmeldung → physische Gerätepixel-Kette.
- Echte Modellinferenz, Qualität/Latenz/RAM, physischer Fold7, native Filament-Darstellung und echte TTS bleiben gesonderte Laufzeitnachweise. Die finale C1-Integration benötigt eigene Gates.

## Konkrete Quellpfade

- [AgentActivity.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/AgentActivity.kt)
- [AgentToolLayer.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/AgentToolLayer.kt)
- [ContinuumHologramView.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/ContinuumHologramView.kt)
- [AiContextCompiler.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt)
- [ContinuumMemoryRecallPublisher.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/cognitive/ContinuumMemoryRecallPublisher.kt)
- [BoundedAgent.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/cognitive/BoundedAgent.kt)
- [LocalToolContinuation.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/localmodel/LocalToolContinuation.kt)
- [ContinuumMemoryEvents.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/learning/ContinuumMemoryEvents.kt)
- [MemoryEventSource.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/cockpit/MemoryEventSource.kt)
- [ContinuumMemoryEventSource.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/cockpit/ContinuumMemoryEventSource.kt)
- [CockpitAnimationEvent.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/cockpit/CockpitAnimationEvent.kt)
- [CockpitAnimationPresenter.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/ui/CockpitAnimationPresenter.kt)
- [ContinuumHomeView.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/ui/ContinuumHomeView.kt)
- [JarvisHoloAvatar.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/avatar/JarvisHoloAvatar.kt)
- [JarvisSequenceView.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceView.kt)
- [BrainOrbAnimationRenderer.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/ui/BrainOrbAnimationRenderer.kt)
- [JarvisSequenceEffects.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/ui/JarvisSequenceEffects.kt)
- [ContinuumBrainOrbRenderer.kt](sandbox:/workspace/scratch/174c84456ee3/continuum-c1/app/src/main/java/at/heinz/deviceai/ui/ContinuumBrainOrbRenderer.kt)

Beleg gesichert: 2026-11-04T20:37:58.652229+00:00. Nur diese eigenen Scratch-Belege wurden geschrieben.
