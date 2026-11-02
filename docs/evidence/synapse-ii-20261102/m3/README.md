# Synapse II M3 — actual canonical host repair

**HOST_REPAIR_VERIFIED_PENDING_REVIEW**, 2026-11-02. EXP-02 / EXP-11; compatible with
the existing CCC1 job, receipt, canonical backlog and fixed test-profile interfaces.
No worker implementation change was needed. No new repair pipeline was added.

| Identity | Value |
| --- | --- |
| Official source commit at execution (40 hex) | `ec37288555fc29d8af99a276ea7323792a9f4bf8` |
| Official branch | `research/continuum-synapse-ii-20261102` |
| Disposable fixture seed (40 hex) | `397cd42df1bd63a58763583b939f834d97e45eb7` |
| Disposable failing source commit (40 hex) | `6e97b5612a1da8480ddbce0d8b55d49e578032a7` |
| Disposable committed candidate (40 hex) | `ccf1108a3249c28fc2c53f03d5fa558bbed1c50b` |
| Patch SHA-256 (64 hex, distinct type) | `da5c8bbf0e3a012d3cab4d8877c73f9c21ad6beec04df3411f169c2dd52a5a43` |
| CCC1 task | `t-35ec97302687a24a` |
| Canonical repair job | `219b42d6-2b36-41d4-b2fd-5d68752b58c7` |
| Backlog task | `mob-d0260da8b7efc105` |
| Actual Node / Git | `v24.19.0` / `2.52.0`, Linux x64 |
| Strategy / attempts | `JS_TYPO_REFERENCE` / 1 |

`sourceCommit` in `summary.json` binds the official worker implementation. The worker's
`receipt.commit`, report `base` and typed envelopes' `baseCommitSha` bind the separate
disposable fixture source. The patch digest identifies patch bytes, not either Git
commit. `summary.json.sourceFiles` records exact worker-module SHA-256 values and
the harness digest; the harness itself was newly untracked at execution.

## Executed path and results

`submitRepair` → typed CCC1 GOAL/HYPOTHESIS/PLAN/TEST_REQUEST → `runCcc1Cycle` →
`runMobileRepairJob` → canonical owner-accepted backlog → Teamleader →
`executeOwnerRepairTask` → `repairSession` → `CHAINS.node` / `PROFILES.node-continuum`
→ independent fresh worktree → CCC1 receipt/verifier → pending review.
The repository has no literal exported `runRepair` function; `repairSession` is
the existing canonical loop used by this path.

| Actual run | Exit | Executed | Failed |
| --- | --- | --- | --- |
| Seed fixture, original assertions | 0 | 4 | 0 |
| Fault fixture before worker (raw TAP) | 1 | 4 | 3 |
| Worker baseline, same fixed Node suite | 1 | 4 | 3 |
| Worker's candidate check | 0 | 4 | 0 |
| Worker's independent second worktree | 0 | 4 | 0 |
| Additional fresh checkout of committed candidate | 0 | 4 | 0 |

One unchanged positive control verifies `square(3) = 9`; three regressions exercise
`quad` with positive, zero and negative inputs. The worker fixed `sqare` to `square`
in the source module. Assertions and all test identities remained unchanged.
The independent run repeats the original suite in a fresh checkout; it is not a
blind task or broad benchmark.

After the worker returned its patch, the fixture checkout still had the broken
source bytes, unchanged HEAD and clean status. The existing `validateVerifiedReport`
accepted its exact report/patch. Existing `publishRepair({push:false})` then applied
and committed those bytes in another disposable worktree. The candidate's parent
was checked against the fixture source, and its Git diff against the exact patch.
Publishing to `main` was actually attempted with `push:false` and rejected before
worktree mutation: `Invalid target branch main` (`main-blocked.json`). A later CCC1
cycle returned `IDLE`, proving no task replay executed.

## Evidence and command

Executed from the official research checkout (exit **0**):

```sh
strace -f -s 4096 -e trace=execve,exit_group \
  -o docs/evidence/synapse-ii-20261102/m3/process-execution.txt \
  node tools/continuum-worker-proof/run-m3.mjs \
  > docs/evidence/synapse-ii-20261102/m3/harness.stdout.txt \
  2> docs/evidence/synapse-ii-20261102/m3/harness.stderr.txt
```

- `commands.json`: harness command argument arrays, working directories, exit codes,
  signals and errors. Nonempty stdout/stderr is retained in corresponding `.txt`
  files; absent per-command files mean empty streams.
- `process-execution.txt`: actual worker child executions and process exits,
  including three fixed JUnit suite executions in the canonical worker.
- `baseline-raw.stdout.txt`: actual raw pre-worker failing TAP. The worker's own
  baseline diagnostics and counts are in `report.json` and `receipt.json`;
  its single JUnit destination is reused for the candidate, so the retained
  top-level `node-junit.xml` is the candidate's green report.
- `independent-check/node-junit.xml` and `verification.json`: the actual independent
  worker's test and evidence records; the receipt binds the verification file hash.
- `ccc1-envelopes.json`: original hash-chained typed journal entries verified with
  the disposable runtime key during the run. No runtime key is archived.
- `fixture.bundle`: all three fixture Git commits, so the candidate commit survives
  disposal of the temporary repository. This fixture commit is evidence only.
- `aborted-budget-check/`: retained initial negative probe (harness exit 1).
  The first goal had a five-minute deadline and was correctly refused by the
  existing fixed 25-minute request budget gate before a repair ran. Only the
  harness deadline was corrected to 30 minutes. Worker code stayed unchanged.

Fresh independent replay of the archived fixture, without invoking a new worker:

```sh
proof="$PWD/docs/evidence/synapse-ii-20261102/m3"
replay_dir="$(mktemp -d /tmp/continuum-m3-review-XXXXXX)"
git clone "$proof/fixture.bundle" "$replay_dir/fixture"
git -C "$replay_dir/fixture" checkout --detach 6e97b5612a1da8480ddbce0d8b55d49e578032a7
(cd "$replay_dir/fixture" && node --test .github/continuum/math.test.mjs) # expected exit 1
git -C "$replay_dir/fixture" checkout --detach ccf1108a3249c28fc2c53f03d5fa558bbed1c50b
(cd "$replay_dir/fixture" && node --test .github/continuum/math.test.mjs) # expected exit 0
```

## Boundaries and handoff

Authorization covered this disposable fixture experiment. `approvedForMerge:false`,
`installed:false`, `deviceVerified:false`; no official source patch or Git write,
no paid API, no model invocation, no weight/learning promotion, no owner-data write.
The canonical worker's temporary repair ledger contains fixture experience only.
Execution mode is explicitly `HOST_LEGACY` with `STEP_WATCHDOG`; isolated Git
worktrees do not establish an OS sandbox. No free-form model shell was supplied.

**DEVICE_NOT_RUN**: no Fold7/Termux access, Android build, APK signing, release or
installation was performed. This narrow deterministic typo repair says nothing
about model coding quality or general task success. Independent root review and
the official research-branch evidence commit remain the parent agent's task;
M3 made no official commit/push. Next: source-exact review of these receipts,
then a separately authorized device run through the same path when available.
Rollback: remove only the two newly added proof/evidence directories; no production
worker or application behavior changed.
