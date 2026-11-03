# MΩ-1 workspaces handoff

Final source candidate: `4665302f6c967c1bfb1e13c9acf01fad4e7eec20` (LOCAL_ONLY,
not pushed because Chief reported expired Git credentials). Last actually pushed
checkpoint: `ef1c20e9bb18e06ab7ef8392ecc5d8bc05ec13af`. Original Node candidate:
`30cd8ce4c27cb78aada96b8ec5b405a2b5c8a127`,
[PR417](https://github.com/Ullmann27/device-ai/pull/417), based on `cf34fb6a`.
EXP-01/02/07/08/11/12. HOST_INTEGRATION only; independent review pending.

## Implemented connections

`ccc1-workspace.mjs::bindCcc1Workspace` reads the MAC-verified existing
`DurableTaskJournal` and existing `pendingRequest`, `jobIdFor`, `recover`,
`taskState` and `gateTestRequest`. It binds the canonical task/source/request/job
and a trusted caller's worker name to the existing fixed-profile executor.
Binding does not grant capabilities. Wrong job, torn/corrupt journal, stale source,
profile expansion and interrupted/replayed jobs are refused. Journal recovery,
leases and replay authority stay in CCC1; there is no new state store.

`createIsolatedExecutor().forWorkspace(binding,profiles)` creates a branded scoped
view in the original WeakSet. Each execution generates a distinct workspace ID,
snapshots actual tracked bytes, observes Docker HostConfig, validates CPU/RAM/pids,
privileges/network/read-only policy, removes the container, then re-inspects its
absence. `validWorkspaceEvidence` checks this new observation shape; it is
consistency checking, not remote attestation or proof of correct tests/models.
Unbound legacy callers remain supported.

BETA owns `coding-orchestrator.mjs`; Chief will integrate this reserved call before
the driver and before `runMobileRepairJob` (not yet wired in this Node freeze):

```js
const jobExecutor = executor === null ? null : bindCcc1Workspace({
  executor, project, stateDir, taskId: grant.ccc1TaskId,
  job: order.job, workerId: 'typed-coding-worker'
});
// existing localTeamleaderDriver receives executor: jobExecutor
```

New `chief/ChiefWorkspaceWiring.kt` now supplies a typed trusted-port WorkerJob
and an existing AgentMailbox reporter for the existing ChiefWorkerPool. Its
mandatory delivery callback reports full/rejected/error mailboxes explicitly;
no extra outbox/store exists. Scope/teardown/evidence identity must match; nonzero
profiles become BLOCKED, repeated invocation is refused. DONE messages say
verification=PENDING. The fixed test-only Node host harness also supports
canonical-job-labelled cancellation so the existing executor reaps its container.

Five focused Kotlin contracts PASS; two real host JUnit tests PASS: actual existing
pool + mailbox + two real fixed Node containers, then an observed real running
container cancelled through that same pool, Node runner/container closure confirmed,
CANCELLED delivered to Chief, and no success receipt produced. See
`docs/evidence/momega1/workspaces/KOTLIN-PROOF.md` for commands, trust boundaries,
raw observations and the exact compiled-class/source/dependency manifest.

Kotlin ChiefWorkerPool/ChiefBrainWiring/routines.AgentMailbox/cognitive.ComputeFabric
remain the coordination authority. Their core sources are unchanged. The new
adapter has no Activity or device IPC caller; its real connection is the explicit
host test. `tools/continuum-virtual-fabric` is an unintegrated prototype, never
claimed as capacity.

## Actual evidence

`docs/evidence/momega1/workspaces/red.log`: unchanged base missing-export RED.
`green-contract.log`: 9/9 focused contract checks PASS.
`host-green.log`: explicit real Docker proof PASS, two containers observed running
at once; each fixed node-continuum profile observed cgroup memory=256MiB, CPU=1,
pids=64; peer/host source reads and writes and Docker socket blocked; no network;
independent scratch markers survive peer-named local scratch alias writes.
HostConfig matched policy and each removal was independently inspected.
An additional 1000ms outer timeout killed the deliberately slower fixed profile;
no success receipt or remaining named container was accepted. Existing canonical
IN_PROGRESS backlog status prevented automatic workspace replay.
Original raw receipts/JUnit/summary are under `workspaces/host`.
Final same-source Node run is `host-green-final.log` and `host-final/`; final
Kotlin raw proof is `kotlin-host.log` and `chief-host/`.

`host-attempt-1.log` is a preserved FAILED test-harness assumption: writing a
peer-named file in one's own private scratch is legal. The corrected probe verifies
that such a private write cannot alter the peer's distinct private marker.
`host-attempt-2.log` records the corrected PASS before the final timeout probe.

Reproduce from the repo root (local pinned image must already exist; no pull):

```sh
node --test .github/continuum/ccc1-workspace.test.mjs .github/continuum/isolated-executor.test.mjs
CONTINUUM_WORKSPACE_HOST_PROOF=1 CONTINUUM_WORKSPACE_EVIDENCE_DIR=/tmp/workspace-proof node --test .github/continuum/ccc1-workspace-host.test.mjs
```

Image: `sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1`
(local empty FROM scratch image). Docker28.4.0, Node24.19.0, runtime runc,
nonroot1000, fixed trusted Node home mounted read-only. Containers are ephemeral,
not virtual machines. Two logical worker jobs share the host's real capacity;
no additional hardware/model count or throughput is claimed.

No model inference, Android/Fold7, owner data, signing, installation, deployment
or production integration acceptance. Full Gradle belongs to QA. Independent critic and Chief caller wiring are next;
do not infer production Android integration from host JUnit. Rollback: omit
this branch; inspect canonical CCC1 journal/backlog before retrying uncertain jobs.
