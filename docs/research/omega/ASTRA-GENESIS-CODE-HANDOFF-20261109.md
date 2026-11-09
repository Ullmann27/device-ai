# GENESIS-CODE / CCC2-H1 - active research handoff

Owner assignment read in full:
https://github.com/Ullmann27/device-ai/pull/662#issuecomment-6732145487

Branch: `astra/continuum-bot-universe-20261108`, Draft #662.
Starting HEAD: `5a0fd0a8e1bc2cd098693a62e766f3be19d23429`.
Code51 #663 observed at `16688ed015bf757c636a59126867b3ca76cc1bd0`;
its branch remains untouched. Relevant EXP-02/05/07/08/11/12.

## Trust and caller map

| Surface | Existing route | Authority / remaining boundary |
|---|---|---|
| Android | `AgentActivity` reviewed coding draft -> `ContinuumTypedCodingBridge` -> fixed Termux command -> `continuum-typed-coding-bridge.mjs` | Exact owner-reviewed task/source/scope digest, prepare/run separately; no selected-bot field yet |
| Host owner control | `scripts/continuum-coding-service.mjs` -> `coding-service.mjs` prepare/submit/cancel | Local owner review digest, private inbox, canonical grant and STOP; currently fixed Forge/Lint/Echo |
| Execution | `coding-service-worker.mjs` -> `runCodingTask` -> CCC1 `runTask` -> mobile job -> teamleader -> repair loop | Shared scheduler/backlog/journal; no new service allowed |
| Identity | `ccc1-bot-assignment.mjs` -> read-only Kotlin profile bridge -> `ChiefBotProfileStore` | Process-private assignment brand, distinct builder/verifier/reporter, current budgets, expiry; profiles do not grant rights |
| Sandbox | `bindCcc1Workspace` -> `createIsolatedExecutor` -> fixed `isolated-profile-runner.mjs` | Task/request/grant/workspace binding, non-root, no network, read-only host binds, bounded CPU/RAM/PIDs/time/output, confirmed teardown |
| Kotlin | existing `kotlin-fixture` compiler/runner; production typed chain remains `android` | Fixture is real Kotlin, but not a canonical Android/JUnit repair chain; do not relabel fixture execution as Android success |
| Model | `coding-provider.mjs` -> fixed local CLI -> `queryPinnedLocalCoding` | Exact reviewed model/runtime tuple, namespace/seccomp launcher, authenticated source/context/prompt/schema/output observation; injected transport is not real-model proof |
| Verification | distinct workspace executor -> canonical repair verifier -> immutable receipt/journal | Text/model opinions and a worker DONE are not independent correctness; reporter does not infer |
| UI text team | `ChiefBotTeam` -> existing coordinator/pool/orchestrator | Two text-only calls, no tool rights/CCC1 grants; remains separate from real coding authority |
| Release | Code51 integration and protected owner signer | NOT part of this assignment; no main merge, signing, installation, release or Actions dispatch |

## Reproduced blocker and alternatives

- At starting HEAD the real typed bridge returns
  `BLOCKED / CODING_ISOLATION_UNAVAILABLE` before grant/model access.
- Docker daemon 29.1.3 is available. The EXISTING scratch Dockerfile builds
  without downloads. Actual container startup with UID/GID65534, network none,
  read-only filesystem, no capabilities, no-new-privileges, 256MiB/1CPU/32PIDs
  and only system binary/library read-only mounts exits zero.
- Bubblewrap was missing. Restored distro bubblewrap0.9.0 after the missing
  dependency probe; real unprivileged `--unshare-all` startup exits zero.
- Existing fixed-profile full isolation/Kotlin proof: PENDING at this checkpoint.
- No local model weights in this task's persisted artifacts. Existing approved
  Qwen1.5B weights and exact CPU llama.cpp pin must be restored with provenance.
  Available GCC13 differs from the reviewed GCC14 binary recipe. Never change
  the reviewed tuple to admit an arbitrary replacement binary.
- Google Maven/Android build remains a separate known blockade; not used as
  an excuse to claim Android acceptance from Kotlin/JVM.

## Next bounded milestones

1. Exercise the existing kernel proof and compiler in the provisioned sandbox.
2. Extend the existing owner review/delivery/worker/read projection with exact
   selected identities. Preserve legacy fixed-team requests; no profile grants.
3. Only after admitted model + execution prerequisites: real Kotlin
   RED -> model patch -> GREEN -> separate verifier, retaining negative cases.
4. Freeze at least ten independent tasks and A/B/C fixed-budget criteria before
   evaluation; no measurements, skill promotion or weights training claimed yet.

Tests for this checkpoint: real minimal Docker and bubblewrap startup PASS;
full execution/model/repair/evaluation NOT_RUN. This is provisioning evidence,
not intelligence. New code changes will have separate exact-SHA test results.

Rollback: revert only this branch's isolated commits; stop only owned processes
and remove only named experiment containers/directories. Preserve all profiles,
private data, canonical task receipts and prior research. Keep Draft #662.
