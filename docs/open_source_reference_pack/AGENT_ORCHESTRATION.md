# Bounded Agent Orchestration

Implement a small Kotlin state machine inspired by durable agent frameworks:

`OBSERVE → RETRIEVE → PLAN → MODEL → VALIDATE → TOOL_PROPOSAL? → GATED_EXECUTION → OBSERVE_RESULT → EVALUATE → STORE_EXPERIENCE → RESPOND`

Every step emits typed state/audit events.

## Checkpoints
- checkpoint after expensive or irreversible steps;
- persist only allowlisted schema-versioned state;
- validate on resume;
- never deserialize arbitrary executable classes;
- recover from process death using last valid checkpoint.

## Tool approval
Model-generated calls are proposals only. Execution stays behind Device AI ToolRegistry, typed schemas, permissions and Shizuku boundary. Never expose raw unrestricted shell to the model.

## Bounded reflection
Adapt Reflexion as a repair policy:
- default maximum 1–2 repair attempts;
- retry only for concrete evaluated failures;
- store structured failure/evidence/correction;
- successful lesson may become evaluated experience;
- exhaustion surfaces a blocker.

No open-ended autonomous loop.
