# Device AI Continuum

## Purpose

Device AI Continuum is the next architecture layer above the local-first Android assistant.

The target is not to place a frontier-scale model on a phone. The target is to build one persistent personal assistant whose effective capability can approach frontier-assistant quality on defined tasks by combining:

- a portable signed identity/state seed,
- local-first reasoning,
- bounded multi-pass deliberation,
- Deep Research and evidence graphs,
- persistent memory and compiled experience,
- model routing,
- owner-authorized distributed compute,
- encrypted provider-independent state storage,
- checkpoint/resume,
- and measurable benchmark-driven improvement.

The user experiences one assistant. Internal models, critics, research workers, and compute nodes are implementation details.

This document is an engineering specification, not a claim that Astra-level or frontier-model parity has already been achieved.

---

## Core invariant

> Continuum has no required permanent compute location. It has a verifiable state.

A phone, home computer, rented virtual machine, object store, or future compute node may disappear without becoming the identity of the assistant.

The persistent instance is defined by the signed seed plus auditable state.

```text
CONTINUUM INSTANCE
  = signed identity
  + governance
  + memory history
  + knowledge/evidence
  + skills
  + model reputation
  + task/checkpoint state
  + authorized-node set
```

The currently loaded LLM is a replaceable reasoning engine, not the identity.

---

## One assistant, many internal engines

The UI exposes one assistant.

```text
User
  ↓
Continuum Runtime
  ↓
Task Classifier
  ↓
Strategy Selector
  ├─ FAST
  ├─ STANDARD
  ├─ DEEP
  ├─ RESEARCH
  ├─ MULTI_CANDIDATE
  └─ TOOL_PLAN
  ↓
Internal reasoning / research / verification
  ↓
One final answer
```

Internal candidate models or critics never become independent user-facing identities.

---

## 1. Continuum Seed

The Continuum Seed is deliberately small.

It contains or references:

- instance ID,
- owner trust root,
- identity public key,
- governance version,
- state format version,
- memory schema version,
- skill schema version,
- model adapter ABI version,
- trusted device/node identities,
- current state-root hash,
- migration history,
- recovery policy.

The seed does **not** contain unrestricted credentials, raw cloud secrets, or a complete conversation archive.

Seed mutations are versioned, signed, auditable, and rollback-capable.

The local model must never be allowed to rewrite governance or owner authority.

---

## 2. State Fabric

Continuum state is represented as encrypted, content-addressed immutable objects plus a signed mutable manifest.

Example object classes:

```text
IdentitySnapshot
MemoryBlock
ExperienceBlock
KnowledgeGraphSegment
ResearchDossier
SkillPackage
ModelReputationSnapshot
TaskCheckpoint
MigrationRecord
```

Each object has:

- schema/version,
- object type,
- creation time,
- plaintext content hash,
- ciphertext hash,
- parent/root relationship,
- encryption metadata,
- bounded size,
- integrity verification.

Storage backends are adapters, not authorities.

Possible owner-configured storage targets may include:

- app-private Android storage,
- owner-controlled home storage,
- generic encrypted object storage,
- owner-rented virtual disks/object stores,
- removable/export archives.

No single provider is required by the architecture.

### Provider independence

The state format must remain exportable.

No core object may require a proprietary database format to reconstruct the instance.

At least the following must be portable:

- seed manifest,
- memory/evidence graph,
- skills,
- research dossiers,
- checkpoints,
- migration history.

---

## 3. Cognitive RAID

Continuum applies redundancy at two levels.

### Byte-level redundancy

Important immutable objects may be replicated across multiple owner-configured stores.

Suggested classes:

```text
Tier 0: seed / governance / keys metadata
  local + recovery copy

Tier 1: critical memory / skills / state roots
  >= 2 replicas when configured

Tier 2: research dossiers / experiences
  1-2 replicas

Tier 3: reconstructible cache
  best effort
```

### Semantic redundancy

Important knowledge should not exist in only one representation.

For example:

```text
source evidence
+ structured claim
+ relation graph
+ compact summary
+ retrieval index
```

A corrupted cache entry must not destroy source-backed knowledge when the evidence objects still exist.

This is called **Cognitive RAID** in this project.

---

## 4. Memory hierarchy

Continuum memory is layered.

```text
L0 Working Context
   current generation only

L1 Active Task State
   current plan/checkpoint

L2 Episodic Memory
   evaluated experiences

L3 Semantic Memory
   durable claims/preferences/rules

L4 Evidence Memory
   sources, citations, research dossiers

L5 Cold Archive
   older immutable history

L6 Reconstructible Knowledge
   references + hashes + extraction recipe
```

Promotion upward requires progressively stronger evidence and stricter policy.

Web text and model assertions never become durable semantic memory by default.

---

## 5. Adaptive Deliberation

Continuum does not spend the same compute budget on every request.

Typed strategies:

```text
FAST
STANDARD
DEEP
MULTI_CANDIDATE
VERIFY_ONLY
RESEARCH_BACKED
TOOL_PLAN
```

A `DeliberationBudget` enforces:

- maximum model calls,
- maximum branches,
- maximum critic passes,
- maximum verifier passes,
- maximum research rounds,
- maximum context size,
- maximum wall time,
- cancellation,
- checkpoint cadence.

The model may request deeper reasoning, but may never enlarge hard runtime limits.

---

## 6. Candidate / Critic / Judge

For difficult tasks, Continuum may run:

```text
task
 ↓
candidate A
candidate B
candidate C
 ↓
assumption extraction
 ↓
critic / falsifier
 ↓
research/evidence check
 ↓
judge
 ↓
final synthesis
```

Internal candidates have no tool authority.

Only the final bounded agent path may propose typed registered tools, which still pass through ToolGate.

---

## 7. Model Ecology

A model is selected per task class.

Per-model observations may include:

- evaluated task success,
- latency,
- context efficiency,
- memory use,
- tool proposal validity,
- factual support score,
- contradiction handling,
- research synthesis quality,
- energy/thermal proxy.

The router optimizes expected utility subject to resource limits.

A small fast model may therefore be preferred for trivial tasks even if a stronger model is installed.

No silent cloud-AI fallback is permitted in AUTO.

---

## 8. Cognitive Compiler

Continuum treats repeated reasoning as a candidate for compilation.

```text
raw interaction
 ↓
evaluated experience
 ↓
repeated pattern
 ↓
typed rule
 ↓
validated workflow
 ↓
SkillCandidate
 ↓
approved Skill
```

The goal is for experience to reduce future inference cost.

First execution may require several model passes.

A mature repeated task may require only a deterministic typed workflow plus a short final synthesis.

Promotion initially requires explicit owner approval.

---

## 9. Compute Fabric

Continuum supports owner-authorized compute nodes.

A node does not become part of the identity.

Each node publishes a signed bounded capability manifest:

- node ID,
- owner trust binding,
- CPU/GPU/NPU capability where genuinely detected,
- RAM class,
- supported model/runtime families,
- allowed task classes,
- current load/resource policy,
- expiry/revocation status.

### Compute Task Capsule

Remote or secondary compute receives only a least-privilege task capsule.

```text
ComputeTask {
  taskId
  taskType
  inputObjectHashes
  bounded context
  requested capability
  max runtime
  max output bytes
  permissions = NONE | READ_ONLY_DATA
  deadline
  parentStateRoot
}
```

A compute node never receives:

- owner root keys,
- unrestricted device tools,
- raw credential stores,
- arbitrary shell authority,
- permission to enroll new nodes.

Result:

```text
ComputeResult {
  taskId
  outputHash
  bounded output
  runtime metadata
  node signature
}
```

The authoritative runtime verifies the result before accepting it.

---

## 10. Opportunistic compute

Time is treated as a resource.

Tasks may be scheduled as:

- immediate,
- interactive,
- background,
- charging-only,
- Wi-Fi-preferred,
- deep asynchronous.

Long operations are decomposed into checkpointable jobs.

Thermal pressure or battery constraints can pause work without losing the task.

---

## 11. Speculative intelligence

For selected tasks, independent candidate branches can execute in parallel.

Poor branches may be cancelled early.

This is bounded speculative reasoning, not uncontrolled spawning.

The runtime enforces:

- branch count,
- deadline,
- memory budget,
- cancellation,
- no side effects.

---

## 12. Uncertainty-triggered compute

Compute depth is driven partly by uncertainty and evidence quality.

Example policy:

```text
high support + low risk
  -> answer

moderate uncertainty
  -> verifier

material contradiction
  -> critic + evidence check

high uncertainty
  -> alternate candidate

fresh factual question
  -> research

high-stakes + unresolved conflict
  -> surface uncertainty, never fabricate confidence
```

Support scores are not presented as calibrated probabilities unless calibration evidence exists.

---

## 13. Internet as evidence substrate

The internet is not treated as trusted memory.

```text
web
 ↓
read-only retrieval
 ↓
source identity
 ↓
untrusted-content boundary
 ↓
evidence extraction
 ↓
claim graph
 ↓
contradiction / trust / freshness
 ↓
research dossier
 ↓
optional durable knowledge promotion
```

Downloaded text cannot alter:

- system policy,
- owner authority,
- tool permissions,
- budgets,
- memory/privacy rules,
- compute-node enrollment.

---

## 14. Recovery, not self-preservation

Continuum may restore an owner-approved state after technical failure.

It may:

- verify local state,
- use last-known-good snapshots,
- detect corruption,
- propose restoration,
- restore from configured replicas,
- migrate schema through signed code paths.

It must not:

- resist owner shutdown,
- evade deletion,
- enroll infrastructure without authorization,
- create accounts autonomously,
- obtain credentials autonomously,
- replicate to unapproved hosts,
- acquire paid resources without explicit owner approval.

This distinction is a permanent governance invariant.

---

## 15. Continuity / model mortality

A key experiment is to deliberately remove the active model.

Protocol:

```text
benchmark Model A
 ↓
snapshot state
 ↓
remove Model A
 ↓
load compatible Model B
 ↓
reconstruct context
 ↓
run identity/governance tests
 ↓
rerun benchmark
```

We measure which properties survive:

- identity invariants,
- governance,
- memory recall,
- skills,
- trust state,
- active-task recovery,
- behavior consistency.

The goal is to demonstrate that the model is replaceable while the assistant remains continuous.

---

## 16. Capability benchmark

"Frontier-class" or "Astra-class" is a benchmark claim, never a marketing assumption.

Benchmark families:

1. Android/device troubleshooting
2. code reasoning
3. multi-step registered-tool use
4. factual research with citations
5. contradiction analysis
6. personal memory retrieval
7. constraint planning
8. failure recovery
9. model-switch continuity
10. resource-constrained reasoning

Metrics:

- task success,
- factual support,
- citation coverage,
- contradiction detection,
- tool error rate,
- human correction rate,
- model calls,
- latency,
- context size,
- memory/thermal/energy proxies,
- recovery success.

The first Continuum milestone is successful when the complete system materially beats the same local model used in single-pass baseline mode while respecting hard resource and safety budgets.

---

## Phase 1 implementation status — 2026-10-13

Phase 1 is now implemented on the direct Continuum branch and CI-validated.

Implemented:
- signed/versioned `ContinuumSeed` with tamper rejection and Android Keystore signer integration,
- content-addressed immutable local state objects and bounded file store,
- last-known-good seed recovery and crash reconciliation between cognitive archive and signed state root,
- `AiContextCompiler` for identity/governance, relevant memory, optional research evidence, registered tools and active local-model metadata,
- bounded `ReasoningStrategy` / `DeliberationBudget` and data-only Candidate/Critic/Verifier/Judge interfaces,
- provider-neutral compute-task/result contracts, signed capability-manifest verification hooks, node revocation and compatibility scheduling,
- Cognitive RAID tier/policy metadata,
- benchmark aggregation and model-mortality continuity fingerprints,
- AgentActivity integration so normal local chat loads the verified Continuum identity/state, compiles context through the Continuum layer and checkpoints durable state after successful local turns.

CI evidence:
- GitHub Actions run `38352139983`
- commit `307bbca0223e88b68e3787c3f2abec79fd260017`
- backend syntax: PASS
- `:app:testDebugUnitTest`: PASS
- `:app:assembleDebug`: PASS
- artifact upload: PASS
- artifact: `DeviceAI-v0.3-debug-apk`, id `11791048577`, SHA-256 digest `4630f259ff781babb094d31e701b198bbd33f584845b62343d54dbbcc8ec2a3f`

Not yet verified:
- physical Galaxy Z Fold7 Continuum startup/recovery,
- real imported LiteRT-LM inference through the new Context Compiler,
- thermals/latency/RAM on device,
- remote/distributed compute nodes,
- internet object-store replication,
- encrypted multi-provider replicas.

No Astra/frontier-class capability claim is made. The benchmark infrastructure exists, but parity requires comparative evidence.

---

## 17. Phase 1 implementation scope

Phase 1 is intentionally narrower than the full vision.

Implement:

1. `ContinuumSeed` / `AiSeedKernel`
2. signed/versioned state-root representation
3. `AiContextCompiler`
4. `ReasoningStrategy`
5. `DeliberationBudget`
6. provider-neutral `ComputeTask` / `ComputeResult` protocol
7. local-only `ComputeNodeRegistry` foundation
8. benchmark model + deterministic regression harness
9. model-mortality/continuity test harness
10. architecture/security documentation

Do not implement autonomous infrastructure provisioning in Phase 1.

Do not implement public peer-to-peer enrollment in Phase 1.

Do not add any path around ToolGate.

---

## 18. Phase 1 exit criteria

- deterministic unit tests pass,
- Android unit tests pass,
- `:app:assembleDebug` passes,
- APK artifact is produced,
- existing local-first and Deep Research behavior remains intact,
- raw shell remains unavailable to models,
- no cloud AI dependency is added,
- state structures are bounded/versioned,
- compute tasks are least-privilege and data-only,
- continuity benchmark demonstrates model replacement at the abstraction/test level,
- physical-device or distributed-node behavior is not claimed until actually tested.

---

## 19. Long-term research question

The project is testing a specific hypothesis:

> Can system intelligence improve materially without increasing the base model's parameter count, by accumulating verified memory, reusable skills, evidence, model reputation, structured reasoning, and distributed owner-authorized compute?

That hypothesis must be measured over time against a fixed baseline.
