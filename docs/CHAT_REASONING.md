# Chat Reasoning Integration (Phase 2)

`ChatReasoningRouter` runs the bounded reasoning chain before the first local model call of a chat turn.

## Gating
- Runs only on the first agent iteration (no tool observations, no reflection).
- Skipped for device/tool tasks and for strategies other than DEEP / MULTI_CANDIDATE.

## Bounds (defaults)
- max 4 extra model calls, 20 s total deadline, 2500 chars per pass, 1500 chars advisory.
- Cancelled together with the local request; any failure returns null (single-pass fallback).

## Trust model
- Output is an UNVERIFIED P6 advisory, brackets stripped, placed after the body and before the closing instruction.
- Identity, user intent and the tool-rule tail keep priority inside the 15,500 char cap.
- Reasoning workers have no tool authority; ToolGate is unchanged.

## Unverified
- On-device latency of the pre-reasoning chain.
- Whether LiteRT-LM permits a second concurrent conversation on the target device.
- Chain runs with STANDARD strategy (4 calls) instead of full DEEP (~7 calls).
