# Coding/discovery continuation — 2026-10-26

Status: `WIP / SOURCE_INTEGRATED`. Focused provider checks pass; full regression,
real isolation probe and model/APK path are still `NOT_RUN`.

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

Next: actual Docker isolation proof, then full Node suite. Local model files and
llama-server are absent on this host; do not conflate an injected candidate
infrastructure proof with actual neural generation.

## Current capability findings and gaps

Linux x86_64 uid/gid 1000; Node24.19.0. The exact local Docker socket responds
with server28.4.0. bwrap/unshare exist; a user+mount+network+PID namespace probe
exited0. No Docker image was initially present. Built a tiny offline FROM-scratch
image from the fixed local Dockerfile: SHA256
`635ac4bd326bd4059588042faeae80654dbe1f950894f3ce4005e4380d3e3b4b`.
No package/model download. These checks do not themselves prove the existing
executor can run a profile.

Existing typed bridge already requires the branded Docker executor before any
journal/model access. Existing path: typed grant → CCC1 journal → mobile repair
job → canonical backlog/teamleader → repair session → fresh-worktree verifier.
Interrupted CCC1 execution already requires owner approval; do not bypass it.

Known remaining gaps: task acceptance is prompt text, scoped FEATURE on a green
baseline returns NO_FAILURE, and owned-app recipes deliberately reject modified
source. No free-language→arbitrary-APK bridge has been proved here. No local
model calls, APK builds, signing, installation or phone verification yet.
