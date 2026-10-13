# Continuum Phase 2 — Effective Intelligence

Phase 2 turns the Phase 1 continuity substrate into measured problem-solving improvements.

The first implementation block adds:

- deterministic task classification,
- runtime selection of bounded reasoning strategy,
- data-only Candidate / Critic / Verifier / Judge coordination,
- an explicit internal-pass contract that has no ToolGate, DeviceToolExecutor or shell authority,
- proof-carrying append-only memory mutations signed by the identity signer,
- MemoryWritePolicy gating before a mutation can become durable,
- evaluated Experience -> typed SkillCandidate crystallization,
- owner approval remaining mandatory for skill promotion.

The purpose is not to claim frontier-model parity. The purpose is to build the mechanisms that can be benchmarked against the exact same local model in single-pass mode.

## Permanent boundaries

Internal reasoning passes may receive bounded context, emit text, be cancelled, and be evaluated.

They may not execute tools, request shell access as an executable action, alter governance, enroll compute nodes, contact the web implicitly, or mutate durable memory without a proof-carrying MemoryWritePolicy-approved transaction.

## Next integration step

After the Phase 2 foundation is CI-green, add a text-only adapter around LocalModelRuntime and expose a controlled DEEP mode. The same imported local model will then be benchmarked in:

1. single-pass baseline,
2. Continuum multi-pass mode.

Only measured differences may be reported as capability improvements.
