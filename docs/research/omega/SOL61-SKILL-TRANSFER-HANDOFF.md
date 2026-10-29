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
