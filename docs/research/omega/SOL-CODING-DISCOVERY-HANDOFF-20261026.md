# Coding/discovery continuation — 2026-10-26

Status: `SOURCE_INTEGRATED / HOST_TESTED`, not neural or phone acceptance.
Full Node regression and an actual Docker pipeline probe passed. Neural
generation, a free-language APK path and Fold7 remain `BLOCKED / NOT_RUN`.

Base: `c391b832` on `sol/continuum-brain-breakthroughs-20261026`, descendant of
PR #150 (`ad14f8b82f9be2cd0026bc16eef71a16bec41424`). Working branch:
`sol/coding-discovery-safety-20261026`, intended draft base is the parent branch.

## Scope and contract

EXP-00/01/02/05/08/09/11: inspect existing mobile-builder, owned-app, typed coding,
CCC1, canonical backlog and repair pipeline. Find the smallest proven missing
connection or fail-open, reproduce it with a red test, then repair it. Persistent
retry/diagnostic reuse must use the existing job and repair state, with bounded
attempts. No second backlog, framework or Android main-app replacement.

Read: root AGENTS.md, experiment contract/register, current integration register,
copilot instructions, active checkpoint, Opus night handoff, and live Issue #41
(updated 2026-10-22). The owner phone remains the primary development machine;
this optional Linux host cannot establish Fold7/Termux end-to-end acceptance.

## Safety and rollback

- No model-text shell and no arbitrary unisolated executable candidate.
- A git worktree is source isolation, not an OS sandbox. Real executor capability
  must be checked before any candidate execution; unavailable isolation blocks.
- No main merge, owner signing, installation, model training or promotion.
- Changes remain review-only on this branch. Rollback: revert branch commits;
  canonical prior state remains on the parent integration branch.

## Checks and next entry point

Completed read-only: repository HEAD/ancestry/remotes, clean starting worktree,
live Issue #41 and current managed environment status. Managed network policy
is enforced/unrestricted. No credentials printed.

Initial focused baseline: `node --test --test-concurrency=1
.github/continuum/coding-orchestrator.test.mjs scripts/continuum-typed-coding-bridge.test.mjs
.github/continuum/isolated-executor.test.mjs` — 19/19 PASS.

## Proven defect and repair plan

The provider reset its in-memory request count when reconstructed for the same
canonical job. It invoked the model before attempting `request-1.json` with
exclusive creation. On resume, an extra model call happened and then failed
with EEXIST. A process loss before evidence writing left no consumed budget.

Red tests (provider): 4 PASS / 3 FAIL, including the observed EEXIST, absent
pre-inference reservation and foreign-task evidence accepted before inference.
Green: provider plus rejection-feedback tests — 16/16 PASS.

The existing per-job evidence files now reserve and fsync each request before
inference. Reconstructed providers read the same task/commit/fingerprint-bound
slots and cannot exceed maxModelCalls (1..3). Interrupted reserved requests
remain consumed; corrupt or foreign evidence blocks. No new backlog or job
state store was added. Final response records still contain hashes and byte
counts, without raw source, model reply or inherited credentials.

Second red/green slice: scoped repair ignored all earlier canonical ledger
attempts. Two failing tests showed empty history on same-task restart and three
pointless retries after a durable budget/corruption block. The repair ledger now
optionally records the typed-task fingerprint on scoped attempts. Same exact
task/source/chain reuses at most three negative candidate IDs and fixed profile
test counts. Raw model hypotheses and diagnostic output are not persisted for
this reuse; no automatic patch replay or training is enabled. Different owner
task/goal/limits/source gets no history. Terminal evidence/budget codes remain
explicit and stop revision retries. Focused scoped/provider/ledger: 32/32 PASS
before the additional malformed-ledger-binding check.

Full source-exact Node regression at `91dac7eb78426ab43a02aed52d202b701d8f28f6`:
`env -u NODE_TEST_CONTEXT node --test --test-reporter=tap --test-concurrency=2
.github/continuum/*.test.mjs scripts/*.test.mjs` — 1253 total, 1247 PASS,
6 SKIP (kotlinc unavailable in PATH), 0 FAIL, 128.537s. The raw log is
`docs/evidence/sol-coding-discovery-20261026/full-node.tap`.

Backend initial run: 53 PASS / 7 FAIL, all seven API-integration hooks could not
import express because the isolated worktree had no installed dependencies.
The raw failure is preserved as backend-missing-deps.tap. After comparing the
unchanged package-lock.json and using the already installed official-checkout
node_modules through a temporary local symlink (no install/download), the same
backend command passed 60/60 in 1.237s. The symlink was removed after testing;
no backend source changed.

Local model files and llama-server are absent on this host. No neural model
download or inference was attempted. Do not conflate the injected candidate
infrastructure proof below with actual neural generation.

## Current capability findings and gaps

Linux x86_64 uid/gid 1000; Node24.19.0. The exact local Docker socket responds
with server28.4.0. bwrap/unshare exist; a user+mount+network+PID namespace probe
exited0. No Docker image was initially present. Built a tiny offline FROM-scratch
image from the fixed local Dockerfile: SHA256
`635ac4bd326bd4059588042faeae80654dbe1f950894f3ce4005e4380d3e3b4b`.
No package/model download. These checks do not themselves prove the existing
executor can run a profile.

## Actual isolated pipeline proof

At exact code commit `91dac7eb78426ab43a02aed52d202b701d8f28f6`, the existing
typed grant → CCC1 → mobile job → canonical backlog → teamleader → repair →
fresh-worktree verifier completed a general tag-normalization source patch:

- Frozen fixture `be78953197ec5af21db3ef472389105edb16f983`.
- Docker baseline: 7 executed / 2 failed. Candidate: 7 executed / 0 failed.
  Independent fresh worktree and container: 7 executed / 0 failed.
- Tests verified uid1000, zero effective capabilities, no-new-privileges,
  read-only source, inaccessible external host marker, and loopback-only
  network interfaces. Executor profile also enforced 512MiB,32pids,1CPU,30s.
- Canonical backlog DONE, result PATCH_PENDING_OWNER_REVIEW, same-task replay
  did not call the provider again. Original checkout/host marker unchanged.
- `actualModelUsed:false`, `injectedCandidate:true`. No claim of neural code
  generation, semantic completeness, remote attestation or device acceptance.

Reproducer, Dockerfile, task/result/summary, patch, report, profile evidence and
independent receipt are archived under
`docs/evidence/sol-coding-discovery-20261026/`. No runtime key, auth token or
whole private state directory is copied. External live run:
`/workspace/continuum-coding-proof-20261026` (not the sole evidence location).

Existing typed bridge already requires the branded Docker executor before any
journal/model access. Existing path: typed grant → CCC1 journal → mobile repair
job → canonical backlog/teamleader → repair session → fresh-worktree verifier.
Interrupted CCC1 execution already requires owner approval; do not bypass it.

Known remaining gaps: task acceptance is prompt text, scoped FEATURE on a green
baseline returns NO_FAILURE, and owned-app recipes deliberately reject modified
source. No free-language→arbitrary-APK bridge has been proved here. No local
model calls, APK builds, signing, installation or phone verification yet. The
existing recipe source-integrity gate must remain intact; do not treat it as a
connection to arbitrary candidate code.

## Published checkpoints and next handoff

- Initial checkpoint: https://github.com/Ullmann27/device-ai/commit/8304dd0326fdefd041d1760716cefd82aac10806
- Durable request repair: https://github.com/Ullmann27/device-ai/commit/216fa837ee8d40b8bb5c32b7ca374d21045a0553
- Same-task diagnostic repair: https://github.com/Ullmann27/device-ai/commit/91dac7eb78426ab43a02aed52d202b701d8f28f6
- Draft: https://github.com/Ullmann27/device-ai/pull/155

Review/cherry-pick source commits into the parent branch, retain the optional
same-schema taskFingerprint field for old ledgers, and rerun relevant integration
checks after sibling changes. The optional DeepSeek owner-request transport is
owned by the separate sibling branch; it must reuse these reservations and
preserve exact per-request external prompt consent. No DeepSeek calls here.

Next actual capability step requires a verified model and reviewed executable
acceptance tests. A feature on green baseline and generated APK bridge remain
separate reviewable work. Android/Termux isolation/callback/Fold7 acceptance is
still open. Rollback is a revert of this branch's two source commits; existing
legacy local deterministic repairs and owner APK/signing paths are unchanged.

## Owner stop / resume boundary

The parent authorized planning a bounded actual Gemma-generated repair through
the existing provider/CCC1/Docker path, then relayed the owner's 20% limit and
immediate STOP before any of that follow-up began. No new fixture, invoker,
server, model call, model download or source experiment was created or started.
That follow-up is `NOT_RUN`, not a failed model-quality result. Resume requires
the next authorized session and resource coordination with the parent.

The completed evidence above remains limited to the durable retry/diagnostic
repairs, full Node/backend checks, and the injected-candidate actual Docker
pipeline proof. Neural repair and APK/device execution are still `NOT_RUN`.
All useful source, raw red/green/final logs, synthetic fixture reproducer and
receipts are in the official repository branch/PR #155; no transient private
fixture or unpublished candidate exists. No coding proof container remains
active; the temporary backend dependency symlink was removed.
