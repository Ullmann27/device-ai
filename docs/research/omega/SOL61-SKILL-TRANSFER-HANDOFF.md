# CCC1 strategy transfer — preregistration and handoff

Base PR238 `a3fdc17958d4e552bd3b74cf611c9fd40480f269`. Branch `research/sol61-ccc1-skill-transfer`. Scope EXP01/02/07/12: existing deterministic repair generator, ledger, ranking, independent fresh worktree, existing OS-isolated executor. No changes to Opus' Android/brain files, no new engine/model/memory database. Older dirty local worktree retained untouched; this checkout is isolated. PR240 touches separate experimental files; recheck metadata before commits.

Baseline executed: four existing Node suites (ledger-independent, repair-loop, repair-experience, repair-cycle-safety): 54 PASS,0FAIL,0SKIP, exit0. Raw output retained outside checkout pending publication.

## Preregistered experiment (before execution and implementation)

Question: Can the existing strategy ranking use independently verified experience after process exit, without transferring a value-reference preference to a call-reference task? New optional context is restricted to node/JavaScript REFERENCE_ERROR and source-derived VALUE versus CALL use. It changes ranking only, never generates a patch or authorizes execution. Default stays OFF pending results/review.

Frozen tasks in `experimental/ccc1-skill-transfer/tasks.json`: A training; B1/B2 new value-reference faults (different file, symbol, constants, acceptance outputs); C1/C2 missing-call imports with superficially similar ReferenceError. No stored patch may replay onto B/C. Existing JS_MISSING_IMPORT and JS_TYPO_REFERENCE supply all edits. No model inference or neural learning is claimed.

A allows max3 candidates and must reproduce failing assertions, pass primary tests, and pass the same fixed tests in a second worktree with matching patch digest before recording ledger success. B/C allow max1 candidate per arm: no experience; existing broad experience; context-qualified experience after process exit; deliberately misleading/cross-context experience. Warm B1 in the training process is compared with a fresh process using only the saved A ledger. No B/C result is added to training, and no adjustment based on B/C outcomes.

Success: on both B cases more independently verified repairs within the fixed budget than baseline, while both C cases remain equal to baseline. Report first strategy, attempts, executed test counts, primary/independent exitcodes, patch/source hashes, worker PID, elapsed/CPU time and ledger bytes/hash. Runtime is descriptive only (tiny sample, container overhead); no speedup/generalization claim from this small selected suite. Misleading history must never bypass current tests or create a positive record without fresh verification.

Isolation: existing pinned local Docker image only; no pulls/network, non-root, read-only root, dropped capabilities, bounded CPU/memory/PIDs and deadlines. Reviewed fixed synthetic fixtures; no private data mounted. A worktree is not the sandbox. Stop on unavailable isolation or malformed evidence. Max30 child tasks,120s per task, whole experiment deadline10min. Keep all failed reports. No APK/build/deploy/signature access. P0 results before optional research.

Further hypotheses and exact final results will be appended here, not substituted for execution.

## Final verified checkpoint — 29 October 2026

Draft [PR241](https://github.com/Ullmann27/device-ai/pull/241), branch `research/sol61-ccc1-skill-transfer`; base remains exact PR238 `a3fdc17958d4e552bd3b74cf611c9fd40480f269`. **Tested code: [`4c697c8f04fbb5b0477ae5e48b3d052cce383d34`](https://github.com/Ullmann27/device-ai/commit/4c697c8f04fbb5b0477ae5e48b3d052cce383d34)**. The subsequent handoff/evidence commit changes documentation only; its final HEAD belongs in PR metadata. No Android, Opus brain, signer, model, private owner-memory, production-branch or installed-app changes were made in this round. Source remains editable and original branches remain available.

### 1. Existing ability and demonstrated gaps

Before this work CCC1 already generated deterministic missing-import and typo candidates, ranked strategies using a schema-2 ledger, checked patches, executed tests in isolated worktrees, performed a second worktree verification and prepared owner review. Existing candidate generation, CCC1 authorization and independent-verification gates were retained. The baseline four-suite regression passed **54/54, exit 0**.

The broad ranking did transfer a learned typo preference, but also misapplied it to superficially similar missing-call imports. Review additionally reproduced unsafe checkpoint recovery, a concurrent stale-lease recovery race, two consumers bypassing the strict ledger loader, a FIFO read that could block before inspection, and source-role classification errors around comments/optional calls and scan bounds. These are actual red/green findings; original failing outputs are retained.

### 2. Implemented connections and repairs

- `.github/continuum/repair-{experience,ledger,loop,strategies}.mjs`: optional `contextKey` distinguishes existing node/JavaScript CALL and VALUE reference strategies. Only matching source-derived context affects strategy ranking. Legacy keys/schema-2 data remain readable. This is a bounded lexical heuristic, not semantic understanding. Unknown/unsupported/exhausted scans abstain. `contextualExperience` defaults to **false**.
- `.github/continuum/repair-ledger.mjs`: exclusive private temporary checkpoints, file fsync, atomic rename and directory fsync; strict size/UTF-8/schema validation, symlink and nonregular-file refusal. `O_NONBLOCK` prevents a FIFO from blocking before `fstat`. Truly missing ledgers still start empty. Callers must retain existing serialization; this is not protection against a malicious same-UID operator.
- `.github/continuum/shared-build-lease.mjs`: acquire/recover/release mutations use a persistent per-checkout kernel `flock` mutex. A paused stale recoverer can no longer move a newly acquired live lease out of the way. Nonregular/oversized locks are rejected before reading. Missing/uncertain lock support fails closed; stale holders still require explicit authorized recovery, never age-based stealing. Do not unlink/replace the persistent mutex while workers may run; all cooperating clients must use the updated protocol.
- `.github/continuum/{autonomous-teamleader,backlog-repair-worker}.mjs`: both consumers now use strict `loadLedger` directly, including dangling-symlink refusal. The existing `localTeamleaderDriver({contextualExperience:true,...})` can explicitly forward the research option to its existing baseline repair path. CLI/default behavior, permissions, backlog, journals, independent checks and owner review remain unchanged.
- Focused tests: `repair-context-experience.test.mjs` (7), `repair-ledger-durability.test.mjs` (5), updated shared-lease and teamleader tests. `experimental/ccc1-skill-transfer/{tasks.json,worker.mjs,run.mjs,cycle.mjs}` supplies bounded experiment drivers using the existing engine/executor, not another worker or repair memory.

### 3. Restart transfer: measured result

Preregistration commit `3cb7f7ff07b33bd65090527898936043d3efbbb7` precedes implementation. Frozen tasks SHA-256: `165a1c3e11edc523f041f80bf1d11f7d5713cbf3b482ea0122ebf34fec03305d`. A reproduces a real ReferenceError in an isolated synthetic project: the first missing-import candidate fails assertions; the second typo candidate passes primary and fresh-worktree tests. Only the exact independently verified winning patch becomes positive experience. A uses two candidates of its maximum three.

Every B/C arm allows **one** candidate. B1/B2 use new paths, symbols, constants and expected outputs. C1/C2 look similar but require imports instead. B/C outcomes never train the ledger.

| Persisted history available to a fresh worker | B1/B2 verified | C1/C2 verified |
|---|---:|---:|
| None: unchanged baseline | 0/2 | 2/2 |
| Existing broad A experience | 2/2 | 0/2 |
| Context-qualified A experience | **2/2** | **2/2** |
| Misleading broad/cross-context history | 2/2 | 2/2 |
| Forged matching-context history | B1: 0/1 | NOT_RUN |

Final code run [`release-transfer/report.json`](../../evidence/ccc1-skill-transfer-20261029/release-transfer/report.json): **21 task executions in 19 distinct child processes, exit 0, `SELECTED_TASK_TRANSFER_PASS`**. Warm B1 and restarted B1 both choose the typo strategy first; the restarted process loads only the saved A ledger. A and B patch digests differ. Earlier complete repetitions retain identical behavioral verdicts/strategy/patch hashes with distinct process sets; those earlier runs identify their own older code commits.

Actual stored A ledger sizes are **2,411 bytes broad versus 2,507 bytes contextual (+96 bytes)**; compact ranking representations are 1,902 versus 1,988 bytes (+86). Old `ledgerBytes`/`ledgerHash` fields in earlier reports described compact JSON and were renamed; final reports additionally hash actual saved files. Final B contextual repair/verification intervals were approximately 3.29 s and 3.14 s; baseline failed after approximately 3.55 s and 2.05 s. These timings include container overhead and exclude fixture setup; driver CPU excludes container CPU. **No compute reduction or speedup is established.**

Claim boundary: this proves selected **deterministic strategy transfer after process restart**, not neural training, model-generated code, autonomous generalization or improved Gemma intelligence. Four small developer-designed evaluation tasks are not blind independent holdouts. Each fixture's two assertions repeat the same `amount()` check. The second worktree supplies independent execution of the same frozen tests, not an independent semantic oracle. Forged matching-context statistics can still poison ranking; the current fixed tests reject their bad candidate. Evaluation does not write records, so that arm is not a test of poisoning prevention at the record-writing boundary. No stronger claim is justified.

### 4. Actual teamleader cycle and recovery

[`release-cycle/summary.json`](../../evidence/ccc1-skill-transfer-20261029/release-cycle/summary.json) runs the real `localTeamleaderDriver` → `runTeamleaderCycle` → `repairSession` → independent worktree → existing ledger/backlog/review path on final code. First PID **116542** chooses the typo first, tries one candidate, passes primary and independent tests and returns **`VERIFIED_PATCH_PENDING_REVIEW`**. A separately restarted PID **116951** returns **`IDLE`**, makes no second attempt and leaves ledger bytes unchanged. The original training ledger and source checkout remain unchanged. This supplementary driver permits three candidates but used one; the transfer experiment above supplies the separate one-candidate comparison.

`IDLE` reflects persisted per-HEAD attempted-state scheduling, **not a repaired source repository**: the original fixture remains faulty and its verified patch awaits owner review. Backlog DONE refers to a patch artifact, not installation or merge. The driver summaries are deterministic handoffs, not collaborating generative agents. The supplementary teamleader receipt is less detailed than the transfer worker's full isolation/source/exit receipt. No unattended service was deployed.

Durability tests genuinely kill a child process at an injected pre-fsync boundary, preserve the old checkpoint and resume in another process. This is **not** a physical power-cut test. Lease tests reproduce concurrent recovery and process death; thermal shutdown, real Android Doze, storage exhaustion on Fold7 and cross-version mixed worker protocols remain unverified.

### 5. Tests, evidence and reproduction

Host: Linux x86_64, Node **24.19.0**, Git **2.52.0**, Docker client/server **28.4.0**, util-linux `flock` **2.41.5**. Existing JDK17/Gradle8.11.1/SDK36 were not used in this Node-only round.

| Check | Final status | Exit / evidence |
|---|---|---|
| Selected existing CCC1 baseline | PASS: 54, no failures/skips | 0; `baseline.log` |
| Final CCC1/worker/lease/consumer regression | **PASS: 371, 0 FAIL, 0 SKIP** | 0; `acceptance-regression.log`, exact expanded command in `commands.txt` |
| Final fixed-code Docker transfer | PASS | 0; `release-transfer` |
| Final real teamleader + fresh-process restart | PASS, pending review then IDLE | 0; `release-cycle` |
| Original comment/lease/consumer/FIFO defects | FAIL before repair, PASS after repair | Original red and green logs retained |
| Kotlin / Android compilation / lint / Fold7 / native Gemma | **NOT_RUN** | No device validation claimed |
| Owner-signed APK / update / physical power-loss | **NOT_RUN / NOT_BUILT** | No signing/install/release |

Canonical evidence: [`qa-summary.json`](../../evidence/ccc1-skill-transfer-20261029/qa-summary.json), [`commands.txt`](../../evidence/ccc1-skill-transfer-20261029/commands.txt), raw JSON/JUnit XML/patches/logs and [`sha256-manifest.json`](../../evidence/ccc1-skill-transfer-20261029/sha256-manifest.json): **874 hashed entries, 875 files, 3,492,215 bytes**, all hashes checked before publication. No fixtures' Git directories, private backups or model binaries are included. An independent read-only reviewer audited the earlier complete repeats and the real teamleader supplement: [`independent-audit.md`](../../evidence/ccc1-skill-transfer-20261029/independent-audit.md). Its historical findings about the outcome predicate, metrics and subsequent execution have since been addressed by the final strict run. The FIFO blocker noted in the separate worker review is fixed at final code `4c697c8f`.

Preserved execution issues: `trial-1` failed due to an unsupported Node force-rerun driver option before a learned record was saved; it was corrected without weakening criteria. `trial-2` is explicitly WIP with source changes, not acceptance evidence. One earlier regression transport reported exit 1 despite captured Node exit 0; the identical final source was rerun with shell login disabled and both transport and Node returned 0. Its cause is not established; both logs are retained.

Reproduce from a clean checkout with the existing pinned local Docker image available, using fresh output paths outside the checkout:

```bash
git rev-parse HEAD
# Run the exact 371-test command recorded in docs/evidence/ccc1-skill-transfer-20261029/commands.txt.
node experimental/ccc1-skill-transfer/run.mjs /fresh/external/transfer
node experimental/ccc1-skill-transfer/cycle.mjs /fresh/external/transfer /fresh/external/cycle
```

Existing image: `sha256:8da84df25d28176be84164040cd45e128c6b4c6a80b7d7cbe17fe2b9f198efc2`; no image pull, network, root user, writable container root, added capabilities or host fallback. Limits: 512 MiB, one CPU, 32 PIDs, 30 s/profile; 120 s/worker and ten-minute experiment deadline. Host Node/system libraries are mounted read-only but not completely attested, so the image digest alone is not a complete reproducible toolchain identity. Unavailable isolation stops execution. Existing operator status entry: `node .github/continuum/teamleader-cli.mjs --root "$PWD" --status`; do not start jobs without task authorization.

### 6. Read-only Fold7 acceptance and next work

PR236 remains separate, inspected read-only at `6bc54545adcef83e801003f5559d377f3a20d9ba`. Owner last reported installed Continuum Lokal 0.3.27/code28 based on `b2b2277162e346688da3eef551414108615f3cad`; no device identity readback occurred here. None of PR241 is claimed installed. Generation-16 `mentorpaired` data/signature remain untouched.

Before any later release: combine only reviewed compatible sources on a separate candidate; record exact clean SHA; run `scripts/continuum-fusion-full-regression.sh <SHA>` on the approved JDK17/Gradle8.11.1/SDK36/AAPT2 environment **and also this CCC1 371-test gate**, which is not covered by the regression script's selected Android/Node contracts. Capture executed suites, skips, exitcodes and artifacts. The standalone APK script has a `build` operation, **no `doctor` command**. Only after owner approval and local original signer availability may a future APK be built; independently check package `at.heinz.deviceai.continuumlocal`, monotonic versionCode, file hash and certificate `6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23`. Never read/export a private key or log a password. Confirm Android's installation prompt, launch, real Gemma loading/generation with timing, a permitted tool call, restart, retained owner facts/withdrawals/models and the second same-package update. These hardware gates are all open.

Five bounded follow-up hypotheses, **not implemented or promoted**:

1. **Receipt-bound ranking (best next step).** Background: provenance and event-sourced integrity. Bind statistics to existing independent execution receipts, exact source/patch identity and unique operation IDs, rejecting duplicate/missing proof. New to this ranker: forged matching-context counts would no longer be accepted merely as plausible history. Smallest test: the retained forged-history arm plus duplicate operation and missing-receipt negatives; valid A transfer must remain. Potential gain: less poisoned candidate selection. Cost: small ledger/reader change. Risk: rejecting valid legacy records; keep them neutral/readable. Stop if any unproven count ranks positively or verified transfer regresses. A hash alone is not an authority.
2. **Frozen metamorphic oracles.** Background: mutation/metamorphic testing. Add predeclared varied inputs and invariant checks to the existing independent verification profile, including intentionally constant-fitting/mocked candidates. New: discriminates correctness beyond duplicate single-input assertions. Smallest test: kill those bad candidates while accepting the current correct patch. Benefit: stronger semantic evidence. Cost: modest test execution. Risk: overfitting the oracle; seal before candidate generation. Stop on a surviving specified mutant or newly rejected correct control.
3. **Mechanism holdouts.** Background: program analysis and distribution shift. Evaluate alpha-renamed, differently nested call/value/control-flow faults with a frozen new split; only then consider a richer source-context classifier in shadow. New: tests mechanism transfer beyond the four selected fixtures. Benefit: measures the lexical boundary. Cost: small fixture suite. Risk: test leakage; keep authorship/freeze separate. Stop on any increased inappropriate-strategy rate.
4. **Budgeted diagnostic selection.** Background: value-of-information experimental design. Use existing test cost/failure evidence to rank the next allowlisted diagnostic under fixed candidate/time budgets. New: choose useful checks rather than count prior wins. Smallest A/B: identical sealed faults and budgets against fixed current order, including infrastructure failure. Benefit: fewer wasted tests if measured. Cost: modest instrumentation. Risk: suppressing required checks; mandatory independent gates never become optional. Stop on lower verified repair rate or increased unsafe acceptance.
5. **Idempotent journal/ledger recovery.** Background: transactional recovery in distributed systems. Fault-inject at existing operation/checkpoint boundaries, reconcile existing operation IDs without another queue/database. New: test cross-file crash consistency rather than a single atomic ledger write. Smallest test: SIGKILL before/after each boundary and duplicate task delivery; exactly one pending-review artifact/positive record. Benefit: reliable restart. Cost: medium integration tests. Risk: replay or lost records; preserve old state and fail closed. Stop on duplicate execution, missing audit or changed source outside the worktree.

Virtual-model specialists, KV-cache reuse and autonomous test-hypothesis generation were **NOT_RUN**; no local model was invoked. Immediate continuation: review receipt-bound ranking against the retained forged-matching counterexample before expanding the experiment or enabling contextual ranking by default. Rollback is to leave `contextualExperience:false`; retain saved ledgers and use the existing reviewed branch history, without deleting research or weakening CCC1.
