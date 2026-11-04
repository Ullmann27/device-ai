# Übergabe Jarvis Kern – Kognition (Phase 2), 04.11.2026

Für ChatGPT Astra. Branch `kern/cognitive-loop-20261104`, abgezweigt von `design/jarvis-cockpit-home-20261104`
@764694295667c431ef3e85c5e7e3d1750ce7c4d7. Draft-PR #490 (Base = #481s Branch, damit kein pull_request-Workflow startet). Nicht mergen.

## Stand
- Vertrag + Implementierung `CognitiveLoop` in `app/src/main/java/at/heinz/deviceai/cognitive/loop/`:
  `CognitiveLoopContracts.kt` (Ereignisse, Ports, Limits, `LoopMemoryPort`, lokaler `RecallHit`),
  `CognitiveLoop.kt`, `CognitiveLoopCockpitBridge.kt`, `CognitiveLoopGatePorts.kt` (ToolGate / PillActionGate).
- Beschreibung: `docs/ARCHITEKTUR-KOGNITION.md`.
- RED→GREEN-Historie: f1a108c0 (25 rot) → 8fc6f1e6 (25 grün) → 3306bd72 (Memory-Port, 2 rot) → Folgecommit (27 grün).

## Teststand
Nur gezielt: `./gradlew :app:testDebugUnitTest --tests 'at.heinz.deviceai.cognitive.loop.*'` → 27/27 grün
(CognitiveLoopTest 19, CognitiveLoopCockpitBridgeTest 4, CognitiveLoopGatePortsTest 4). Keine volle Suite.

## Nächster Schritt
1. `ReasoningModel` (synchron + `cancel()`, passt zum Repo-Stil von `ContinuumReasoningAdapter`) mit lokalem Adapter
   auf `LocalModelReasoningAdapter` / `LocalModelRuntime.streamThinking`, siehe Architektur-Doku „Offen“. Erst roter Test.
2. Planer-Adapter ReasoningModel → `LoopPlanner` (Test mit Fake-Modell nur in Testquellen).
3. Verdrahtung in AgentActivity, OwnerQuestionPort, Gedächtnis' `LoopMemoryPort` (#371).
