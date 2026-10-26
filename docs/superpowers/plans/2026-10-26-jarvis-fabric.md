# JARVIS-MO verified fabric integration plan

> **For agentic workers:** Execute inline using superpowers:executing-plans. The
> owner explicitly requests autonomous implementation and frequent checkpoints.

**Goal:** Connect the existing signed Android compute registry to read-only model
route planning, retaining canonical coding/execution/owner gates.

**Architecture:** A bounded registry snapshot exports resource limits; separately
declared model availability never becomes identity attestation. The Node adapter
checks task identity and resource compatibility before calling the existing
`planVirtualModelRoute`. It cannot authorize execution or create another scheduler.

**Tech stack:** Existing Kotlin/JVM and native Node ESM/test runner, Docker executor
only if actually available. No new runtime package dependencies.

**Spec:** Owner's 2026-10-26 JARVIS-MO mission; pinned full text in
`docs/research/omega/SOL61-MASTER-MISSION-VIRTUAL-FABRIC-20261026.md`;
`docs/CONTINUUM-EXPERIMENTVERTRAG.md` and Issue #41.

## Constraints and decisions

- Own branch/PR #169; never modify main or another developer's branch.
- Existing owner grants, ToolGate, ComputeNodeRegistry, CodingProvider and CCC1
  remain authoritative. Plan serialization is not an authenticated grant.
- No private context/owner binding in shared planning fixtures; synthetic inputs only.
- Read-only planning stays research-only. No productive weight updates.
- Ruling: execute the owner's already specified architecture without another
  design approval round; preserve missing-resource boundaries explicitly.
- Ruling: fresh dedicated clone is isolated from other developers; use it in place
  on the explicitly named new branch instead of creating a nested worktree.

## Review focus

1. Signed manifest collections mutated after verification must not gain rights.
2. Future-issued/expired/revoked capabilities must never influence planning.
3. Forged registry JSON must never become execution authority in Node.
4. A model declaration must not bypass task/source/resource limits or locality.
5. Missing Docker/model/SDK cannot become a simulated end-to-end success.

## Task 1 — Native baseline and branch composition

- [x] Query #151–168 heads/comments, preserve original numeric work, create draft.
- [x] Native focused source-graph tests; retain raw output and exact SHA.
- [ ] Run full Node suite; inspect every failure/skip.
- [ ] Compare ancestry, review #167 six-file delta, merge into own #168-based branch.
- [ ] Compare #154 bridge with current provider/tier-plan; keep shared semantics.
- [ ] Publish dependency map, native evidence and checkpoint.

## Task 2 — Registry integrity prerequisites

Files: `ComputeFabric.kt`, `ComputeFabricTest.kt`.

- [ ] RED: mutation of signed taskTypes/backends cannot grant new capabilities;
  future issue time cannot be used; assigned node cannot be silently substituted.
- [ ] Freeze copied manifest collections before signature verification and storage;
  respect issue/expiry/revocation and explicit assignment in existing scheduler.
- [ ] Native Kotlin/JVM tests of real source; document unavailable Android SDK.
- [ ] Commit/push code, tests, RED/GREEN outputs.

## Task 3 — Registry-to-planner contract

Files: Kotlin `VirtualModelFabric.kt`, corresponding JVM test, Node
`.github/continuum/compute-fabric-model-adapter.mjs` and `.test.mjs`, shared fixture.

Interfaces: Kotlin adapter consumes `ComputeNodeRegistry`, `ComputeTask` and
separate model declarations; exports versioned deterministic resource snapshots.
Node adapter consumes snapshot, existing fabric request and task requirements;
produces only the existing advisory plan plus explicit transport trust limitations.

- [ ] RED: real registry export matches shared fixture; revoked/expired/foreign
  nodes excluded; model availability is not authenticated.
- [ ] RED: Node rejects wrong task hash, unknown fields/schema, stale resources,
  insufficient RAM/runtime/output, unsupported task/backend and changed node IDs.
- [ ] Implement bounded snapshot/adapter using existing registry and planner.
- [ ] Native cross-language tests with actual Kotlin exporter bytes and Node
  consumer; no project dependency replacements.
- [ ] Commit/push complete sources and evidence.

## Task 4 — Authentic executor and coding-chain proof

- [ ] Inspect current CCC1 evidence parsing, fixed profiles, grants and executor.
- [ ] Reproduce remaining false-green examples; RED→GREEN regression fixes.
- [ ] Probe Docker using actual restrictions. If unavailable, preserve exact error
  and stop execution-dependent claims; never weaken isolation.
- [ ] If available, run A reference then B forced then C strict rank1 on controlled
  public development fixtures through canonical owner-approved path and fresh
  worktree; preserve independent oracle and test identity data.
- [ ] Integrate compatible #154 seam selectively; missing endpoint NOT_CONFIGURED.

## Task 5 — Whole-system audit and continuation

- [ ] Trace actual Android chat→memory→local-model→coding→CCC1→review path and
  classify implemented/test-double/research/missing/blocked links.
- [ ] Inspect worker lease/recovery/budget and Android package/signing/data boundaries.
- [ ] Four separate reviews: syntax/API, concurrency, security/provenance, whole path.
- [ ] Native affected/full tests and fresh whole-branch code review.
- [ ] Push final handoff with exact SHAs, tests, missing resources and next command.

Scope honesty: broad scientific/Android/device acceptance requires real resources.
Unrun experiments remain explicitly NOT_RUN; no quality or generalization score is
inferred from parameter changes, protocol fixtures or passing unit tests.

## Owner closure, 2026-10-26

The owner reported approximately 20% remaining quota and explicitly ended new
feature/experiment/delegation work. This overrides further plan execution.
Tasks 1–3 delivered checked branch composition, registry fixes and shared native
Kotlin→Node planning adapter. Task 4 delivered the optional consented bridge and
actual public-development Docker A/B/C proofs, while real LLM inference remains
NOT_RUN. Task 5 delivered the three independent audit reports and final native
verification; Android/device and broad scientific acceptance remain OPEN.
The unchecked original items are preserved as remaining work, not claimed complete.
See `docs/research/omega/SOL-JARVIS-FINAL-HANDOFF-20261026.md` for final disposition,
precise SHA, checks, open findings and the next bounded repair.
