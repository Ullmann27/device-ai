# CCC1 independent verification integration — 2026-10-26

## Status
**SOURCE_INTEGRATED / PRECONDITIONS_TESTED / GENUINE OS-ISOLATED DUAL WORKTREE NOT RUN / RESEARCH_ONLY**

Source PR: #163 (review-only, stacked on prior Continuum branches).
The owner provided an Argon draft verifier with four mocked tests. It was not copied into production because it relied on guessed APIs and arbitrary callbacks.

### Canonical contract corrections
- Real owner-approved task: \`validateCodingTask\` requires \`sourceCommit\` as **40** lowercase hex characters, \`allowedPaths\`, \`chain\`, exact typed identity; \`taskFingerprint\` is calculated over the canonical validated task. The supplied draft used an 8-character SHA, \`allowedFiles\` and \`testCommand\`, none of which correspond to the canonical typed task schema.
- \`ensureCcc1StateOutsideSource\` checks a *state directory*, not automatically a secure arbitrary OS executor. Worktrees must come from \`createWorktree\` and be removed by \`removeWorktree\`.
- The caller must provide an existing *brand-checked* executor created by \`createIsolatedExecutor\`; a function-shaped mock is never accepted. The only test command comes from \`runChain('node')\` / existing fixed \`node-continuum\` profile. Never accept a free-form shell command from the task.
- The grant MUST already exist in the owner-controlled state directory's \`coding-grants/{taskId}.json\`, with exact matching fingerprint, task body and complete \`t-<16hex>\` CCC1 task ID. Missing, symlinked, unfinished or mismatched grant fails closed.
- Real \`repair-ledger.mjs\` has \`recordSession\`, not a free \`appendVerifiedEntry\`. The research model exposes \`trainResearchSample\`, **not** trusted production \`trainStep\`. This verifier calls neither, and never writes owner data or learning weights.

## Actually committed source
- \`.github/continuum/ccc1-independent-verifier.mjs\`, Git blob \`11c5d92d9716d17be9cf136359ad90c1ebfffa2c\`.
- \`.github/continuum/ccc1-independent-verifier.test.mjs\`, Git blob \`6522a3a9223a4603fd2447fa3524cc3c5df48505\`.

The new entrypoint \`runIndependentCcc1PatchVerification\` is deliberately owner-approval- and Docker-capability-dependent. It enforces:
1. Full source SHA and candidate digest, task fingerprint, one authorized code file, no test/spec/fixture path, existing CCC1 prepared owner grant.
2. Clean root and detached worktree on exact source commit; complete tracked test-file SHA-256 snapshot. Candidate source bytes must match the research proposal's original source digest.
3. Baseline RED from the fixed \`node-continuum\` profile: a branded OS-isolated executor, real nonzero executed JUnit tests, and at least one failed test. Empty results and infrastructure interruptions cannot establish RED.
4. Primary GREEN: exact one-file tracked diff; tracked tests unchanged; genuine isolation result evidence; \`evidenceSufficient\` checks regression in test counts/skips.
5. Separate fresh detached worktree at the same source SHA, same source bytes, same patch content, new isolated test run. Tests and authorized one-file diff rechecked, executed test/skip coverage compared.
6. Cleanup of both worktrees and output even on refusal or crash, with cleanup failures surfaced as errors. Returns review-only object with **\`verifiedSuccess:false\`, \`learningEligible:false\`, \`promotionEligible:false\` regardless of source outcome**. Any tests-only "PASS" is \`DUAL_WORKTREE_CHECKS_PASS_REVIEW_REQUIRED\`, never an automatic owner/weight/main promotion.

### Independent RED/GREEN (preflight only)
Exact current \`.github/continuum/coding-task.mjs\` validator and exact two GitHub source/test function bodies were reloaded in isolated V8 with test-only SHA-256/fingerprint, mock filesystem and mock OS isolation brand. **5/5 preflight test cases PASS**:
- Canonical typed task + required identity binding.
- Reject altered Git/source/task/candidate hashes, no-op claim and unsupported candidate status.
- Reject disguised test/spec files and unauthorized patch paths.
- Reject fake executable callback, forged runner and injected shell command before any execution.
- Accept only canonical, matching, fully prepared owner grant; reject malformed grant fingerprints/CCC1 task identities and state directory inside source.

**This is NOT native \`node --test\`, nor a Docker/Termux/full CCC1 successful execution.** The test shims do NOT compute actual SHA-256 and do not exercise real file permissions; only the committed production source uses Node crypto, the actual canonical worktree helpers and actual branded isolation. This separation is essential: no 100%-success claim from mocks.

### Why native verification remains open
Host Node \`v22.16.0\` is available, but **Docker is not installed** in the independent assistant container. A native OS-isolated dual-run cannot be executed here without access to an authorized Docker-capable local worker and a real owner-approved task grant and failing test. Never fall back to \`spawnSync\` or in-process \`node:vm\` for executing agent-generated code.

### Next actual gates
1. On a fully authorized repo checkout (not this temporary assistant host), run:
   \`node --test .github/continuum/ccc1-independent-verifier.test.mjs\`
   \`node --test .github/continuum/structured-statement-translator.test.mjs\`
   \`node --test .github/continuum/coin-wm-v3-structured-shadow.test.mjs\`.
2. Create the existing owner-approved typed task through the canonical PREPARE flow and independently verify its state journal and full SHA.
3. Supply the configured branded OS executor (immutable image sha256, network off, no host fallback) from existing \`isolated-executor.mjs\`. Fail if Docker absent.
4. Choose an actually failing baseline controlled Node fixture and safe one-file known correction. Observe real nonzero RED → GREEN → independent fresh GREEN, plus tracked tests unchanged.
5. Inject partial/blank JUnit, changed owner tests, wrong source/grant, missing Docker, SIGKILL/ENOSPC, code generator failure, and flaky fresh-run mismatch; all must stay not learning-eligible.
6. Only AFTER an independent receipt and explicit owner review design a separate read-only adapter from canonical CCC1 evidence to NTW1 training datasets. No automatic model weight promotion, ledger update or APK installation is authorized by this PR.

### Rollback
Revert only the CCC1 verifier and test commits from PR #163; preserve the base research model/translator and PRs #159–#162. Never touch main in this research phase.
