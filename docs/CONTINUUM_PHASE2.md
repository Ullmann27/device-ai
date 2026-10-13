# Continuum Phase 2 — adaptive reasoning increment

Base: fa4285d6120e262e8f7e40fa5497e23be930a5af.
Branch: copilot/continuum-phase2-adaptive. No merge to main.

## Implemented in this increment
- TaskComplexityAnalyzer with explainable, deterministic signals.
- AdaptiveReasoningPolicy intersects every strategy budget with immutable runtime ceilings.
- Tool/research requirements take precedence over requested reasoning depth.
- Deliberation checks deadlines/cancellation after calls as well as before them.
- Oversized outputs fail instead of being silently truncated; UTF-8 task/output bounds are checked.
- VERIFY_ONLY verifies a supplied answer without generating a replacement candidate.
- Checkpoint notifications follow successfully completed calls.
- Deterministic strategy, boundary and baseline/multi-pass regression tests.

## Validation status
Code submitted for CI; no passing build, new APK, physical-device measurement or intelligence improvement is claimed by this document. Inspect the Actions run for the actual committed head.

## Deliberate limits
This increment is not the complete first vertical slice. The isolated LocalModelRuntime Candidate/Critic/Judge adapter and normal-chat integration remain to be committed and validated. Full assembled provider prompts must be byte-bounded in that adapter; the generic orchestrator checks only the task and pass outputs. Synchronous provider callbacks must implement cancellable waits; the generic orchestrator cannot interrupt an opaque blocking provider. The fixture benchmark uses zero latency and task-only bytes, not measured LiteRT-LM performance. Heuristic complexity scores are not calibrated probabilities.

## Highest-value follow-up
Reuse the loaded LiteRT-LM Engine with a fresh tool-free Conversation for every thinking pass; reject native tool events, bind model ID/version/SHA-256 for the task, enforce complete prompt/output budgets, and fix terminal-event/readiness ordering. Then wire the non-tool path into AgentActivity and compare the same real model/state on a scored fixed corpus.

## Separate persistence finding to reproduce
After recovering a signed archive from LKG while primary contains an uncommitted archive, another interrupted checkpoint may overwrite the only matching LKG archive before the seed commit. Add repeated-crash injection and stale-root compare-and-set tests before expanding durable mutations.
