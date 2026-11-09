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
| Host owner control | `scripts/continuum-coding-service.mjs` -> `coding-service.mjs` prepare/submit/cancel | Local owner review digest now optionally binds selected builder/verifier/reporter; legacy Forge/Lint/Echo preserved |
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
- Existing fixed-profile full isolation/Kotlin proof: PASS at
  `e5b8f365f311025414e019fb91cbf90038d97d59`. Four real Node containment
  checks, real Kotlin compile/run, unchanged owner canary, timeout and confirmed
  container removal. No containers remained. These are harness-authored safety
  fixtures, NOT model-authored patches or canonical coding success.
- Reproduction: build `scripts/continuum-isolation.Dockerfile --network=none`;
  provide an external JSON with exact image hash, concrete Node/JDK/Kotlin tool
  roots and limits; run `node scripts/continuum-isolation-proof.mjs CONFIG`
  as a non-root controller with authorized local Docker access. This run used
  UID65534/GID102, Node26.12.1, Temurin21.0.13, Kotlin2.2.21,
  1024MiB/64PIDs/1CPU/60s. No global socket permission changes.
  Raw output: `evidence/ASTRA-GENESIS-ISOLATION-20261109.json`,
  SHA256 `8adb896860a70a90e40ed127abe88df3626c47527a4f0f283c5f4cc3b34bbdd5`.
  Executor SHA256 `7dda3445ade45c8a484a312e3a30f5e6c5200f80f9333a96970c52eefcce3594`;
  proof script SHA256 `d45af99e035c7763d4c2e532a6469b6c4ec93fc89e3527817c46f6df5c68135e`.
- No local model weights in this task's persisted artifacts. Existing approved
  Qwen1.5B weights and exact CPU llama.cpp pin must be restored with provenance.
  Available GCC13 differs from the reviewed GCC14 binary recipe. Never change
  the reviewed tuple to admit an arbitrary replacement binary.
  Official Debian trixie image retrieval succeeded as a possible reproducible
  compiler route, but the approved Hugging Face model revision cannot resolve
  here in either curl or the independent web-fetch tool. Native inference is
  BLOCKED, not replaced with scripted output or an unapproved mirror.
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

## Selected-role control-plane milestone

The existing host owner CLI now accepts all-or-none `--builder`, `--verifier`,
`--reporter` on prepare/review/submit. Strict distinct existing IDs become part
of the owner review digest, private delivery record and signed metrics; the
worker reconstructs the existing branded assignment and checks it against that
selection before execution. Current canonical reads reject changed selections.
Task/report/active-profile projections no longer guess Forge/Lint/Echo for
explicitly selected teams. Default requests retain their original review hash.

No separate service, journal, profile registry, permissions or competence claims.
Task SHA/path/limits, source/config/model/toolchain hashes, assignment expiry,
current-profile guards, canonical receipts and non-replay still use their
existing contracts. Revocation can still abort the exact canonical grant after
the selection/review becomes stale; it does not authorize execution.

Initial regression: **47/47 PASS**, no skips, on an exact-byte owned snapshot,
Node26.12.1, UID65534/GID102, UTF-8 and a private writable HOME. Command:

```bash
node --test --test-concurrency=1 \
  .github/continuum/coding-service.test.mjs \
  .github/continuum/coding-orchestrator-failure-report.test.mjs \
  .github/continuum/ccc1-bot-assignment.test.mjs \
  .github/continuum/ccc1-bot-report.test.mjs \
  .github/continuum/coding-task.test.mjs
```

This includes distinct/unknown/missing roles, changed/omitted review selection,
idempotent admission, current roster, restart, stale source, profile revocation,
STOP after stale selection, worker-role mismatch before execution and a real
canonical negative journal/report. Profile metadata and unavailable-container
ports are test doubles; no successful native-model inference is counted.
Root first failed the intentional isolation UID gate. A non-root run with
`HOME=/nonexistent` then could not obtain the worker. The private writable HOME
resolved that environment failure without changing any gate.

Still **NOT_PROVEN**: actual model-authored Kotlin patch, real independent
second-bot code inspection, full Android selected-bot IPC, JUnit repair-chain
integration, generalization, verified new skills or competence. This milestone
can be reviewed independently of those research claims. Prior 3599 JVM checks
apply to the unchanged Android source, not a new Android acceptance.

Tests for this checkpoint: real Docker, bubblewrap, fixed Kotlin/Node isolation
and timeout cleanup PASS; model/canonical repair/evaluation NOT_RUN. This is provisioning evidence,
not intelligence. New code changes will have separate exact-SHA test results.

Rollback: revert only this branch's isolated commits; stop only owned processes
and remove only named experiment containers/directories. Preserve all profiles,
private data, canonical task receipts and prior research. Keep Draft #662.
