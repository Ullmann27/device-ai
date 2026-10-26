# Sol — JARVIS-MO open integration handoff

Status: **WIP / UNVERIFIED**, 2026-10-26. Repository: `Ullmann27/device-ai` only.
Branch: `sol/continuum-jarvis-open-fabric-20261026`.
Pinned starting point: PR #168, `78ceeeb863a3806b0bc088a4a2c2544d96be24cd`.

## Mission and boundaries

Connect existing components; keep ComputeNodeRegistry, owner grants, ToolGate,
canonical backlog, CodingProvider and CCC1 authoritative. No main merge, signing,
installation, paid resource creation, model promotion or owner-memory writes.
The owner's 2026-10-26 mission authorizes this research integration and bounded
local tests. No new permission is inferred from a resource offer.

## Initial recovery and audit

- Fresh workspace: no prior checkout or uncommitted NTW1 work found in workspace,
  `/tmp` or `/root` (bounded directory scan). Existing source is preserved remotely:
  original WIP `2abfee480d826e58ce6a80b2ce685a438d8b0329`, integrated PR #165
  `da81db718612aa6f12ba27bd6db043e57c739e5f`. Do not reconstruct from memory.
- PR #151–#168 heads queried anew through authenticated GitHub connector; all open
  drafts. Main remains `190f5eabaa60fc59292b2b80d3086aafd7743a80`.
- #166: `736aa0d67f8045d055e15098a5c414525a6660fd`.
- #167: `9a4f0632af2ef6d5ecc017eee009fccfe6969172` (scorecard sibling).
- #168: `78ceeeb863a3806b0bc088a4a2c2544d96be24cd` (fabric sibling).
- #154: `2fb604609d8f7c86ec49fe5ebd4d9158b80beb5a` (separate bridge).
- #154/#166/#167/#168 discussion timelines read. No blind sibling/file overwrite.
- AGENTS.md, experiment contract/register, Issue #41 and both master handoffs read.
  Active experiments: EXP-02/03/05/08/10/11/12; preserve Android-first capability.

## Environment and evidence

Native host Node `v24.19.0`; Java `21.0.12.1`. Docker CLI exists; executor
availability not yet established. No Android/model/owner-signing capabilities
declared by cloud runtime. Tests on this branch: **NOT_RUN at initial checkpoint**.
Historical V8 or native reports are upstream evidence, not this checkout's results.

## Checkpoint 2 — native audit and registry repair

- PR: https://github.com/Ullmann27/device-ai/pull/169. Reviewed #167's six-file
  delta against #166; merged sibling ancestry on this own branch, no shared ref edit.
- Native Node 24 on complete checkout `4e4abc61ffb9d9a7e9d6db351c306d1105baf2fe`:
  focused **58/58 PASS**, full **1306 PASS / 5 FAIL / 6 SKIP** (1317 tests).
  Exact command, SHA and raw outputs: `docs/evidence/sol-jarvis-20261026/`.
  Five failures: two outdated Android source-string expectations, one ledger
  fixture missing executed negative-test evidence, two executions of the same
  outdated typed callback assertion. No blanket green-suite claim.
- After #167 integration, scorecard/export native real module graph **9/9 PASS**.
- Three actual ComputeFabric regressions reproduced with Kotlin 2.2.0 / JVM 21:
  mutable signed sets escalated rights, future-issued manifests accepted,
  explicit assigned node silently substituted. RED **3/12 failed**, GREEN **12/12**.
  Registry now snapshots unmodifiable sets before verifying, checks issue/expiry,
  serializes registry operations, scheduler respects explicit assignment.
- Moved unchanged `DeviceToolExecutorRunner` into its own Android adapter file to
  compile real platform-independent contracts without device-executor doubles.
  Kotlin check uses real android-all framework dependency for CommandInterpreter;
  no app dependency stubs, no Gradle/Android build/device claim. Full reproduction
  script accompanies the forthcoming shared-contract slice.
- Docker 28.4.0 local daemon is healthy. Pinned busybox 1.37.0 image ID
  `sha256:30ecbe1509090b38ef3260f3609d64d8261230464eaa4d235901db496e9647fb`.
  First nonroot/read-only/no-network/cap-drop container probe exits 0. This is
  infrastructure availability, not a repair proof or model inference.
- Fabric adapter/shared fixture work is in progress; not yet Android product wiring.

## Checkpoint 3 — real cross-language planning connection

`VirtualModelFabricSnapshotExporter` consumes the existing registry and calls the
existing scheduler with explicit per-node assignment. It exports no context,
owner binding, signature, model identity or endpoint. The Node
`planVirtualModelRouteFromRegistry` validates the versioned bounded snapshot and
task/input/output/deadline binding, filters incompatible capabilities, then calls
the existing planner. JSON transport is explicitly **not authenticated**; all
plans remain non-executable. No parallel grant or scheduling authority added.

Native selected actual Kotlin graph: **15/15 PASS**, real ephemeral Ed25519 signing
and tamper rejection. Actual JVM output then passed byte-for-byte to Node:
**9/9 PASS / 0 SKIP**. No project dependency doubles. Reproduce:

```
CONTINUUM_FABRIC_JVM_DEPS=/path/to/official/jars \
  bash scripts/continuum-fabric-jvm-check.sh /tmp/continuum-fabric-check
```

Dependency SHA-256 inventory, raw compiler/JUnit/Node output and shared JSON are
in `docs/evidence/sol-jarvis-20261026/fabric-*`. Kotlin 2.2.0 CLI / JVM target 17
on Java 21; Gradle's configured Kotlin 2.4.0 and Android APK build are NOT_RUN.
The existing ComputeFabric had no production registry enrollment/transport caller
in the inspected source; this slice provides a real tested API connection, not a
claim of enrolled phone nodes or UI-to-provider operation.

## Next executable steps

1. Run focused native model/numeric/translator/CCC1/A-B-C/fabric tests from complete
   checkout; capture actual outputs and exit status without dependency doubles.
2. Compare #154/#166/#167/#168 ancestry and file differences. Integrate only reviewed
   nonconflicting scorecard changes and compatible bridge hunks.
3. Trace actual ComputeFabric registry APIs, signing and revocation; implement the
   smallest read-only Kotlin→Node planning contract with shared fixtures and negative
   tests. A planned route stays non-executable.
4. Probe genuine isolated executor and installed model before claiming a real coding
   cycle. Missing resources produce BLOCKED/NOT_CONFIGURED, never mock success.
5. Commit/push each stable slice and update this document with exact test evidence.

## Recovery / rollback

`gh repo clone Ullmann27/device-ai && cd device-ai && git switch sol/continuum-jarvis-open-fabric-20261026`

Read this handoff and `git log -6`. Continue only on this branch. Revert individual
integration commits on a new review branch if necessary; do not reset or force-push
any shared ref. No production state has been changed.
