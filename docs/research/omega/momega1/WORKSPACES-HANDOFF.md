# MΩ-1 workspace repair handoff

Source freeze: `8aba17c6cc24b00a66d6c5de59d0c1fe1dc5c2db`.
Branch `codex/momega1-workspaces-20261103`, [PR417](https://github.com/Ullmann27/device-ai/pull/417).
The prior source `4665302f` was rejected by the independent workspace critic for
stale execution admission and Kotlin report correlation; its old PASS evidence
is historical. Chief published the formerly local `cce9be65` checkpoint; the old
LOCAL_ONLY notice no longer describes that checkpoint. The independent workspace critic now reports ACCEPT_HOST_WORKSPACE_SLICE
for exact source8aba17c6; its separate evidence checkpoint is owned by the critic.

EXP-01/02/07/08/11/12. HOST_INTEGRATION only. No model or device acceptance.

## Final interface and execution authority

`bindCcc1Workspace({executor,project,stateDir,taskId,job,workerId})` is still called
before `runMobileRepairJob` by the Chief's pending coding-orchestrator glue.
It verifies the existing MAC-protected CCC1 journal, pending request, canonical
job/source/chain, existing owner capability gate and recovery. Its branded view
cannot execute until the real owner repair worker claims the canonical task.
There is no unclaimed one-shot probe or second reservation store.

Immediately after the existing `BacklogStore` claim,
`backlog-repair-worker.mjs` now derives:

```js
const activeExecutor = typeof executor?.forClaim === 'function'
  ? executor.forClaim({backlog: backlogPath, id: task.id, token}) : executor;
```

The private existing token stays in this trusted worker/closure; it is never
copied into model requests, workspace receipts or mailbox messages. Every
baseline, candidate, fresh candidate verifier and final independent profile uses
this active executor. Multiple profiles in the same live canonical job are valid;
replay after completion/interruption, a replaced token or an expired lease is not.
Legacy unbound callers retain their prior behavior.

Every execution checks current verified journal MACs, terminal/revoked status,
unchanged goal/request, canonical clean source and full base SHA, owner gate,
exact backlog path/task/source/chain and live matching claim. It rechecks directly
before Docker launch and after the profile. The runtime deadline is clamped to
the existing caller deadline, goal deadline, claim lease and original request
budget measured from claim time. Nothing refreshes the original request budget.

Cancellation in this synchronous Node path remains CHECKPOINT_BOUNDARY_ONLY:
an in-flight bounded profile may finish, but revocation then suppresses its result
before the caller receives any accepted evidence. The existing fixed host test
port separately proves active container kill and runner cleanup. No immediate
Android cancellation guarantee or new exported IPC is claimed.

`isolated-executor.mjs` retains its original WeakSet branding, fixed profiles,
read-only source/tool mounts, tmpfs, no network and enforced resource limits.
Generated workspace IDs, actual Docker observations and confirmed teardown remain
correlation/consistency evidence, not model execution or independent test truth.

## Existing Chief and mailbox

`ChiefWorkspaceWiring.briefId(scope)` now derives the existing WorkerBrief.id from
the canonical job UUID, not the reusable logical worker name. The original
ChiefWorkerPool report must match that exact ID. Logical worker identity remains
in message provenance. Stable job-derived AgentMessage IDs plus the existing
AgentMailbox deduplication and a successful-delivery flag prevent duplicate
terminal callback delivery. Old-job callbacks cannot be relabelled as the current
job. Failed/cancelled real pool reports need no model-authored prose to correlate.
The required delivery callback still exposes rejected/full/error mailboxes.

No pool, mailbox, Activity, ToolGate or ComputeFabric core file is changed.
There is no extra mailbox database, scheduler or durable outbox. CCC1 journals and
canonical backlog remain recovery authority. The real Chief connection is still
the explicit host integration test, with a trusted injected fixed Node port;
production Android IPC and the Chief's typed coding caller glue remain separate.

## Evidence for this repair

- `review-original-4665302.tap`: unchanged original independent four real-Docker
  counterexamples, copied from critic commit c87e0b06. All four failed on old source.
- `review-red.tap`: author's reproduction of the critic's expanded old-source
  tests; its deadline setup also exposed an overly narrow test admission window.
- `review-claim-green.tap`: 13 frozen prospective critic claim tests PASS in the
  author's run, including two valid real profiles within one claim, wrong/copied
  claims, owner/journal/source revocation, expiry and token replacement. The critic has separately rerun and accepted the frozen candidate;
  this copied author run remains authored evidence.
- `repair-contract.tap`: 9 focused Node contract checks PASS.
- `repair-host.tap`: updated two-container isolation/resource/network/timeout
  proof PASS using existing canonical claims.
- `canonical-repair.tap` and `repaired-canonical/`: 3 real host lifecycle checks
  PASS. Actual `runMobileRepairJob -> localTeamleaderDriver -> owner worker`
  repairs a genuine deterministic source regression with RED, candidate GREEN,
  fresh independent Docker verification and durable normal receipt replay.
  A controlled clock also proves the real Docker timeout uses a 400ms live-lease
  remainder rather than its 10s configured cap. A real concurrent backlog abort
  suppresses an in-flight completed profile result at the checkpoint.
- `canonical-repair-attempt-1.tap`: preserved failed first fixture: no earlier good
  commit meant the deterministic VCS repair had no candidate. The final fixture
  creates actual good history and a subsequent regression; no model is simulated.
- `repair-kotlin/`: unchanged critic build script used by author; existing pool,
  mailbox and focused tests 19/19 PASS; adapted frozen report-correlation cases
  3/3 PASS; actual pool/mailbox/two-container/cancel host tests 2/2 PASS. Actual
  Gradle-compiled baseline classes are used without stubs, with source/class/jar
  hashes. Synthetic keys/repositories are discarded, only observations retained.
- `repair-proof-manifest.json`: exact frozen source and evidence hashes.

The repair test base merges reviewed BETA9ed11151 as `6a66d58f`. The worker diff
against that BETA version contains only private claim activation and propagation
through its three existing execution routes, preserving fresh verification and
onVerifiedCandidate hooks. No other BETA source is authored by this lane.

Reproduce real Node lifecycle/isolated proof, with the local pinned image already
available (no pull or model download):

```sh
node --test .github/continuum/ccc1-workspace.test.mjs .github/continuum/isolated-executor.test.mjs
CONTINUUM_WORKSPACE_HOST_PROOF=1 CONTINUUM_WORKSPACE_EVIDENCE_DIR=/tmp/fresh-workspace-lifecycle node --test .github/continuum/ccc1-workspace-lifecycle-host.test.mjs
CONTINUUM_WORKSPACE_HOST_PROOF=1 CONTINUUM_WORKSPACE_EVIDENCE_DIR=/tmp/fresh-workspace-dual node --test .github/continuum/ccc1-workspace-host.test.mjs
```

Pinned image: `sha256:3f44842e8a052beda7e358b9fb8d8acc3e180a7f58aaceff7e1b49ad7f3d46e1`.
Docker28.4, Node24.19, runc, nonroot1000; actual host capacity is shared. Containers
are ephemeral OS workspaces, not new hardware, VMs or models. Worktrees are source
checkouts only. No Android/Fold7, inference, learning, signing, installation,
deployment or release readiness is established. Full Gradle belongs to QA.

Independent retest: critic reports13 claim PASS, Kotlin3 reviewer +19 existing
+2 real host PASS, Node13 policy/workspace/confinement/lifecycle PASS with zero
skips. Acceptance remains HOST_WORKSPACE_SLICE with CHECKPOINT_BOUNDARY_ONLY.
Next: Chief-reviewed typed caller wiring and exact-source QA. Rollback: omit this research package; never repeat
uncertain jobs without the existing CCC1 recovery/owner process.
