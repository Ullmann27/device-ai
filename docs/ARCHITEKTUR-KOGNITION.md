# Architektur Kognition – CognitiveLoop (Phase 2, Jarvis Kern, 04.11.2026)

Paket `at.heinz.deviceai.cognitive.loop`, reines Kotlin (JVM-testbar), kein LLM im Kern.

## Schritte
`GOAL → (recall → PLAN → TOOL → VERIFY_RESULT → recordOutcome/MEMORY_WRITE → NEXT_STEP)* → DONE`
(`LoopStep`). Pro Iteration führt die Schleife nur den ersten Planschritt aus und plant dann mit der Historie neu.
Klasse: `CognitiveLoop(turnId, LoopPorts, LoopLimits, clock, listeners)`, `run(goal): LoopOutcome`, `cancel()`. Eine Instanz pro Ziel.

## Ereignisse
`sealed class CognitiveLoopEvent(turnId, atMillis, stepIndex)`: `GoalSet`, `PlanMade(steps)`, `ToolRequested(toolName)`,
`ToolResult(ok, summary)`, `ResultVerified(passed, reason)`, `MemoryWritten(entryId?)` (immer null, Port liefert keine ID),
`AskOwner(question, options: List<LoopOption>)`, `Aborted(reason: AbortReason, calmNote)`, `Finished(summary)`.
`AbortReason`: CANCELLED, TIMEOUT, BUDGET, GATE_DENIED, ERROR. `LoopOption` ist lokal (kein allgemeines ActionOption auf dem Branch).
Ausgabe: `CognitiveLoopListener` und `CognitiveLoopCockpitBridge` → bestehender `CockpitAnimationBus`:
`CognitiveLoopAnimationEvent` (Extension "cognitive_loop", Basis für ActionScene), `ProcessTimeline` (ganze Schrittliste), `ActionStarted(TOOL)`.
`CalculationEventSource` passt nicht (trägt nur `CalculationShown`).

## Ports
- `LoopPlanner.plan(goal, history, recalled, budget): PlanOutcome` (Plan / Done / Uncertain → Rückfrage / Failed)
- `LoopToolPort` – Produktion `ToolGateLoopToolPort` über den bestehenden `ToolGate` (AgentToolLayer.kt:119)
- `ResultVerifier.verify(...)`
- `LoopMemoryPort { recall(goal, k): List<RecallHit>; recordOutcome(turnId, stepIndex, toolName, ok, summary) }` (baut Gedächtnis auf #371).
  `RecallHit(entryId, score, rank)` ist lokal, weil `at.heinz.deviceai.learning.RecallHit` nicht auf dem Branch ist.
- `OwnerApprovalPort` – Produktion `PillActionGateApprovalPort` über `PillActionGate`
- `OwnerQuestionPort` – Rückfrage mit Pillen, noch ohne Produktions-Implementierung
- `LoopBudget(deadlineAtMillis, cancelled)` geht an jeden Port.

## Sicherheitsregeln
- Werkzeuge nur über ToolGate. READ_ONLY ohne Rückfrage; CONFIRM nur nach Owner-Ja (`AskOwner` + Approval-Port);
  DESTRUCTIVE und unbekannte Werkzeuge nie (GATE_DENIED), `destructiveConfirmed` ist immer false.
- Gate-Ablehnung → GATE_DENIED. Zeitlimit pro Schritt und gesamt, Schrittbudget (`LoopLimits`), `cancel()` → ruhiges Ende.
- Späte Port-Ergebnisse nach Fristablauf werden verworfen. Deterministisch über die injizierte Uhr.
- Fehler → nur `Aborted` mit ruhigem deutschem `calmNote`. Nie `AlarmRaised`/`SECURITY_FINDING` (getestet).
- Listener-Fehler sind isoliert.

## Offen
- `ReasoningModel`-Schnittstelle + lokaler Adapter: nicht begonnen (Sprint-Stopp 10:11). Vorgesehener Weg: vorhandener
  `LocalModelReasoningAdapter.execute` (cognitive/LocalModelReasoningAdapter.kt:30) → `LocalModelRuntime.streamThinking`
  (localmodel/LiteRtLmLocalModelRuntime.kt:68, Impl. :199), Runtime aus `LocalModelHost` (localmodel/LocalModelHost.kt:13).
  Ohne geladenes Modell: Fehler „Lokales Modell nicht geladen“. Kein Cloud-Adapter.
- Planer-Adapter ReasoningModel → LoopPlanner, Produktionsverdrahtung (AgentActivity), OwnerQuestionPort-Implementierung.
- CalmNotice-Typ fehlt; `PillActionGate` kennt nur Dokument-Pillen (`AnswerPillKind`), allgemeine Werkzeug-Pille offen (Sicherheit).
- RecallHit durch #371-Typ ersetzen, sobald gemergt.
