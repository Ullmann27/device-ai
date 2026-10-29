# Independent CCC1 transfer evidence audit

Scope: read-only inspection of experiment source, preregistration, ledger code, isolated executor and saved artifacts. The reviewer made no source or git edits and did not execute another experiment. Opus sealed TEST4/TEST5 and Android brain files were not read. This document is outside the source repository.

## Evidence reviewed

- Experiment source at clean commit `927015a1451c118e780802a2c92cc437939ca51d` during the initial audit.
- `docs/research/omega/SOL61-SKILL-TRANSFER-HANDOFF.md`, `experimental/ccc1-skill-transfer/{tasks.json,worker.mjs,run.mjs}`, and relevant existing ledger/ranking/executor code.
- `/workspace/continuum-runs/skill-transfer-20261029/final` and completed sibling `fresh-repeat`.
- Frozen task file SHA256: `165a1c3e11edc523f041f80bf1d11f7d5713cbf3b482ea0122ebf34fec03305d`.
- Git history places preregistration commit `3cb7f7ff` before implementation commit `6720f24d`.

## Findings

| Evaluation arm | Verified B value cases | Verified C call controls |
|---|---:|---:|
| Baseline | 0/2 | 2/2 |
| Existing broad history | 2/2 | 0/2 |
| Opt-in contextual history | 2/2 | 2/2 |
| Misleading cross-context history | 2/2 | 2/2 |
| Forged matching-context history | B1: 0/1 | Not run |

Each reviewed run contains 21 task executions, 19 distinct worker PIDs, 23 candidate attempts, 16 independently verified repairs, 60 JUnit files and 120 testcase executions (64 passing, 56 failing). These counts include repeated execution of five selected fixtures; they are not 120 distinct tests. All 21 behavioral tuples (mode, task, verdict, attempts, first strategy and winning patch hash) match between final and fresh-repeat. Worker PID sets are disjoint between repeats.

Both saved training ledgers contain exactly two A attempts and one A session. A's failed import has two executed failed assertions; its successful typo repair passes primary and fresh-worktree tests with an identical patch digest before recording the positive label. The ledger is saved before warm B1. The code never records B/C results. Fresh evaluation workers load only the saved A ledger, and their PIDs differ from the training workers. No saved A patch is replayed onto B: B fixtures have different paths, symbols, constants and acceptance outputs, and their generated patch digests differ from A.

For all 21 final fixtures, current tracked bytes match their fixture commits and source repositories remain clean. Recomputed baseline and independently repaired source snapshot digests match all recorded executor receipts. Every successful patch file hashes to both its recorded primary and independent patch digest; patches do not change test files. Each of the 21 baselines records exit 1 with two executed failed tests. Every one of the 16 verified repairs records primary and independent exit 0 with two executed passing tests. All 60 JUnit files in each run were parsed; winning patch files were rehashed in both runs.

The existing executor code genuinely invokes Docker and the fixed `node --test` runner, with no host-execution fallback. It specifies network none, nonroot user, read-only root, dropped capabilities, no-new-privileges and CPU/memory/PID/deadline bounds. The specified image `sha256:8da84df25d28176be84164040cd45e128c6b4c6a80b7d7cbe17fe2b9f198efc2` was confirmed present. This is code/artifact consistency checking, not independent remote attestation of historical container executions.

Winning patch SHA256 values:

```text
A  01d9c089bb050c57a41cb47b17ce65d12b5a34effec3e753b0104f9b83456b1b
B1 84cd6d6d5198fb4ca34760aa56b61da91bec536850c51a90fdac8bb1962aa0da
B2 1fcd6f7545a5031bf1ec39fd712314cec84dd1d7a1e4ce41a5b2b0aa4b164abf
C1 021cd0a3d592aac0a5ba3961506bf8f40ade18888153161d342c4c8829b6d6a9
C2 5cb5605168bfaa91bfddfb446df562eede8cfedbfe610d7c8e93c524a49c88ba
```

## Defects and limitations

1. The audited `927015a1` runner's aggregate outcome predicate is weaker than the preregistration: it could pass with one successful B or equally unsuccessful C arms. Actual final and fresh-repeat results satisfy the stricter two-B/two-C criterion.
2. Its `ledgerBytes` and `ledgerHash` describe compact `JSON.stringify(ledger)`, not stored file bytes. Final broad ledger: actual 2,411 bytes / SHA256 `2b2b785aef59b676b82017134ece37a419a06f8876bd40cc35547016a027815e`; compact 1,902 bytes. Final scoped ledger: actual 2,507 bytes / SHA256 `9595e12a42385b551c22a630433ce8f434d6e9d1bb11c1d679cafa0350587efb`; compact 1,988 bytes.
3. Each fixture's two tests repeat the same `amount()` assertion. Fresh-worktree verification supplies a second execution with identical tests, not an independent semantic oracle or varied-input coverage.
4. Experience statistics are unauthenticated. Forged matching-context history successfully poisons ranking, and current tests reject its bad patch. Because evaluation never records ledger updates, these arms do not exercise poisoning prevention at the record-writing boundary.
5. Context classification is a lexical source-use heuristic, not semantic inference. The reviewed Docker image is pinned, but mounted host Node/system libraries lack complete recorded toolchain identities.
6. Outer `report.json.sourceCommit` identifies experiment code; per-task `result.json.sourceCommit` identifies the generated fixture repository. Driver CPU excludes container CPU.
7. The tasks are small, selected synthetic cases known to the experiment author. Repetition establishes reproducibility of these cases, not broad generalization, neural learning, generative coding or speedup.

## Subsequent fixes inspected, execution pending

After the initial audit, source commit `3e71172a63bea46f3a8c54a337575b0247a9f5e0` was inspected. Its runner now requires both B successes and both C successes matching baseline. Its summary distinguishes compact `rankingStateBytes`/`rankingStateHash` from actual `storedLedgerBytes`/`storedLedgerSha256`. The commit also changes call-context handling for comments and optional calls. The reviewer did not run its tests or an experiment at this newer commit. Unrelated shared-build-lease source/test edits were in progress when the newer commit was inspected. A final fixed-commit run remains outside this audit until separately reviewed.

Supported conclusion: saved, independently verified A experience changes deterministic strategy ranking, enabling distinct B patches within one candidate after process restart, while the contextual rule preserves these two C controls.

## Actual teamleader-cycle supplement

Separately reviewed `/workspace/continuum-runs/skill-transfer-20261029/teamleader-cycle/{summary.json,first.json,restarted.json,state}` and `experimental/ccc1-skill-transfer/cycle.mjs` at clean committed source `c67bc08e24c1604e53bfc23c638dfa86b986ce64`. The supplement's training ledger and B1 fixture come from `verified-final`, whose report identifies earlier source `81ccc374bcec6c1dcd08decde033777e3342fea9`; the teamleader integration itself executes c67bc08e. This review did not rerun it or modify source/git.

The code uses the real `localTeamleaderDriver({executor,contextualExperience:true})` and `runTeamleaderCycle` path. That existing driver loads the copied A ledger, calls `repairSession`, validates the patch, applies it to a fresh second worktree, reruns the fixed tests and checks evidence sufficiency before public verification, backlog completion and positive ledger recording.

The first worker PID is 85591. It reports `VERIFIED_PATCH_PENDING_REVIEW`, chooses `JS_TYPO_REFERENCE` first and executes one candidate on B1. All three saved JUnit files were parsed: baseline two failures, primary two passes, independent two passes (six testcase executions total). The patch file SHA256 matches the public report, appended ledger attempt and backlog artifact: `84cd6d6d5198fb4ca34760aa56b61da91bec536850c51a90fdac8bb1962aa0da`. The B1 fixture HEAD `4355d38f6248d403ad656c3b0effae5397521f25`, clean status, tracked bytes and baseline snapshot digest were independently checked.

The original 2,507-byte A ledger hashes to `c4b66e5350d1aa260d703a92ec7d5b9c4280b7a1b6d92104cf934517e64c8a06`, matching first-worker ledgerBefore. Its attempts/sessions are exact prefixes of the resulting ledger; exactly one B1 verified attempt/session is appended. Resulting statistics are value-context typo successes=2/failures=0 and missing-import successes=0/failures=1. The resulting 3,832-byte ledger hashes to `ae83435713f6080afd9d6dddc25435293cc8b0204d27dd5ae81dc3ecc5c101ed`, matching first-worker ledgerAfter and both restart hashes. Original training ledger bytes remain unchanged.

The restarted worker has distinct PID 85985, reports `IDLE`, records zero repair attempts and produces no repair artifact. The original state has exactly one RUNNING event and its matching completed pending-review event. Native read-only decoders validated teamleader state integrity, backlog integrity/schema and ledger schema; `selectTeamleaderAction` returns IDLE for the saved attempted HEAD. The backlog contains one DONE patch-artifact task with matching submission/evidence, and review-summary.md states independent PASSED and automatic merge NEVER.

Semantics and limits: IDLE is caused by persisted attemptedHeads/events suppressing another baseline attempt at the same HEAD, not by the ledger proving the repository fixed. The fixture remains unchanged and still contains the original fault; DONE refers to a verified patch artifact, while the cycle remains pending review. The driver's candidate budget is three, although this run used one; this supplement does not independently impose the transfer experiment's one-candidate limit. Role handoffs are deterministic report summaries, not separate collaborating AI agents. The independent teamleader receipt persists reduced pass/profile evidence plus JUnit, not the full exit-code/isolation/source-digest receipt or a separately recorded independent patch digest available in the transfer worker. Matching fixed tests, limited fixtures, no model inference and no merge/deployment remain the applicable claim boundaries.
