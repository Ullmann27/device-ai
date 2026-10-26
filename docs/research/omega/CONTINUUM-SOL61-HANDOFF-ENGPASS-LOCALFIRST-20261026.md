# CONTINUUM SOL 6.1 — architecture & experiment handoff, 2026-10-26

**Audience:** Sol 6.1 (highest reasoning) with live GitHub access, independent of all prior chats. **Project ONLY** https://github.com/Ullmann27/device-ai (not Lumo). Source branches are review-only; owner retains main, app signing, private memory, approvals and production weights.

## 1. Snapshot and clear provenance

At start of this work the private GitHub connector read:
- main: `190f5eabaa60fc59292b2b80d3086aafd7743a80`.
- PR #159 Astra Brain-/Coding integration HEAD `301fe804700189ee99533662ce57be96c29ae450`, Draft.
- PR #160 infrastructure abort vs genuine negative repair learning HEAD `bc424dfe208e2b06ae3c12a4adf7abb02ec79b05`, Draft.
- PR #161 Android/Termux sourceCommit identity HEAD `4b569515bb3f43abccf008e1889eac68d570a105`, Draft.
- PR #162 bounded grounded recall fallback HEAD `cafc337a14eca01dc49e38eea3dd299034460552`, Draft.
- PR #163 NTW1-v3/structured patch/CCC1 independent verifier HEAD `2b824792487a36c99efecf190a02f0bcf7724bbb`, Draft.
- PR #164 independent JUnit test case identity and count parity HEAD `f763c5491290555228ae385c54e8fad9e91b392c`, Draft.
- PR #165 **actual applied** Sol native numeric/translator binding patch HEAD `da81db718612aa6f12ba27bd6db043e57c739e5f`, Draft.
- PR #166 three-arm diagnostic strict neural top1 HEAD at start `736aa0d67f8045d055e15098a5c414525a6660fd`, Draft; base PR #165.
- New stacked branch: `research/continuum-bottleneck-scorecard-local-escalation-20261026` at the commit of this handoff, based on PR #166. Branch and PR commits may advance, so always re-read their true Git HEAD.

All #160→#166 form stacked Draft PRs; DO NOT blindly cherry-pick them as separate independent patches, do not overwrite Astra/Sol/other branches and never merge to main without owner review.

Previous independent handoff: `docs/research/omega/SOL-6-1-PERPLEXITY-CONTINUUM-TRANSFER-20261026.md`. PR #166 report: `docs/research/omega/CONTINUUM-BOTTLENECK-ABC-DIAGNOSIS-20261026.md`. PR #164 report: `docs/evidence/ccc1-test-case-parity-20261026.md`. PR #165 report: `docs/evidence/sol-native-numeric-integration-20261026.md`. PR #163 security/CCC1 evidence under `docs/evidence/coin-wm-v3-native-node-20261026/`.

## 2. What was actually implemented and checked previously

Sol's own initial research branch `research/continuum-sol-native-numeric-binding-20261026` at `2abfee480d826e58ce6a80b2ce685a438d8b0329` saved a patch, **not applied source**. A later independent integration PR #165 applied it without fuzzy matching; model safety and translator binding behavior patched. Sol reported native Node.js 20.20.1 17/17 model + new regression tests passing on patched sources. GPT6 independently ran 22/22 exact-GitHub source/test callbacks in isolated V8 with declared syntax/crypto test shims; **NOT the same as a full native Node run**.

PR #163 contains the numerical/experimental `CoinWmV3ShadowModel`, a restricted **statement transformer, NOT a general JavaScript AST parser**, `coin-wm-v3-structured-shadow.mjs` and the CCC1 independent research verifier. A bare `new Function`, node:vm or plain spawnSync is **not** an acceptable sandbox: verified OS isolation via existing `isolated-executor.mjs` and fixed test profiles remains mandatory. The CCC1 verifier protects source full SHA, owner grant, allowed file path, tracked test hashes, real test evidence and fresh second worktree. It still returns `learningEligible:false` even when a research success is observed; no model weight/ledger promotion.

PR #164 closes two false-green scenarios: RED executed 120 tests, GREEN only 119 while losing failed test; and equal test counts where a different test replaces the RED failure. Hashed JUnit case identity helps but **does NOT attest runner identity or execution legitimacy**.

PR #166: `.github/continuum/ccc1-bottleneck-diagnosis.mjs` and its tests implement diagnostic arms:
- A `REFERENCE_PATCH`: controller/reference gives known fix content, but does **not** confer test evidence.
- B `GIVEN_OPERATOR`: controller forces exactly one registered atomic operator.
- C `NEURAL_TOP1`: actual NTW1 `rank()[0]`, **one attempted translation only**; no fallback to a lower ranked applicable operator. This intentionally fixes optimistic "Pass@1" claims from `proposeShadowStructuredCandidates` that previously skipped operators not applicable to the input.
- A and B and C bind source/task/candidate hashes to actual `validateCcc1CandidateBinding`. No execution or owner memory. All results remain `NOT_RUN`.
- Public development examples: sort mutation and one-based pagination; **not sealed holdout data**. Exact original GitHub source callbacks 5/5 V8 with test-only host shims; full native current branch NOT_RUN; no Docker repair receipts.

## 3. New code added in THIS work on top of PR #166

1. `.github/continuum/ccc1-bottleneck-diagnosis.mjs`: immutable observation objects now registered in a **WeakSet only within the running research process**. `isCcc1BottleneckObservation` distinguishes actual produced results from external fabricated JSON, but **is NOT cryptographic authentication or a trusted repair receipt**.

2. `.github/continuum/ccc1-bottleneck-scorecard.mjs` + `.test.mjs`: redacted, bounded **ANALYSIS_ONLY scorecard** containing canonical hashes, A/B/C case, actual rank1 operator/score, attempts, translation statuses and local next-gate recommendation. The scorecard returns **no raw source/patch code**, never executes, never trains, never requests an external teacher/model. All independent verification remains `NOT_RUN`. Distinguishes:
   - given operator cannot translate: first validate reference, then translator/scope.
   - given translates but neural first choice does not: first validate reference, then inspect ranking.
   - both generate same unverified candidate: first independent CCC1 verification; **no positive neural learning claim**.
   - both generate different candidates: same verification and direct comparison.
   Re-read the actual source. 5/5 exact GitHub callback V8 tests passed with disclosed test-only crypto/VM/owner OS shims, not full native Node module graph.

3. `.github/continuum/ccc1-bottleneck-evidence-export.mjs` + `.test.mjs`: explicit opt-in **owner-managed local report export** to a pre-created, canonical, PRIVATE `stateDir/research-scorecards` directory outside source; directory mode 0700, report mode 0600; SHA-256 from standard Node crypto; max 16 KiB envelope; unique no-clobber O_EXCL+O_NOFOLLOW file and file fsync. No grant/ledger changes, no personal memory, no network, no source text, no installer. Export is **NON_ATTESTED_ANALYSIS_ONLY**. Name includes case ID and content digest prefix. This is not a signature and does not make test XML or model results trustworthy.
   - Independent *native Node.js 22.16.0*: copied the **exact original GitHub exporter source** into a temporary isolated local directory and checked `git hash-object`: **d91e811c308bed183e92ce7cd81441ffa7921df6** matches published GitHub blob. Six real Node filesystem tests PASS: private redacted output + true sha256, existing report cannot be overwritten, public dir rejection, symlink dir rejection, state inside source rejection, untrusted observation rejection. **These tests used test-only stubs for the two imported project dependencies**, not the full authentic GitHub module graph. They are strong native FS/crypto checks but NOT a complete CCC1 test.
   - A separate V8 synthetic-filesystem replay of the exact exporter source/tests initially reported 2/4 FAIL due to missing `TextEncoder` in the test harness; 2/4 security negatives passed. This is a **test harness deficiency**, not a proven production defect. The six native filesystem tests above use real Buffer and remove this uncertainty. The complete new `.test.mjs` suite on the actual GitHub checkout is still NOT_RUN.
   - Use no Docker fallback. `docker` binary was not found on the assistant's test container (2026-10-26). Genuine owner-prepared CCC1 RED→GREEN→fresh GREEN is still **NOT_RUN**.

## 4. Most useful ideas retained from Perplexity proposal

A. Local-first core, optional large coding specialist **only by explicit owner authorization**, privacy/data minimization and clearly identified model/service revision/cost. No large external model or paid API is a requirement for Continuum core. Do **not** prioritize invented "500GB virtual phone memory" or unsupported distribution/offload. Any CPU/SSD-offload/perf gain needs measurable hardware proof, not a claim of vanishing compute.

B. Existing optional DeepSeek bridge has provider capability/version/privacy/cost protocol fields. A normal OpenAI-compatible endpoint does not automatically fulfill that contract; verify code at `docs/CONTINUUM-DEEPSEEK-BRIDGE-20261026.md` and relevant branch (DO NOT assume available endpoint). Official DeepSeek prompt encoding/recipe may help if exact model/revision and published toolkit truly support it. Validate via official sources and code, do not invent. Never send owner private memory or repository secrets to a teacher.

C. Independently verified **repair recipes** (preconditions + counterexamples + provenance) may help local retrieval more cheaply than immediately training model weights. Compare baseline retrieval vs recipes vs NN selection on frozen out-of-sample tasks. Even a real CCC1 test pass does not automatically authorize persistent recipe learning.

D. Resource-aware local routing: existing recipe → local model → optional consented teacher → abstention. Pre-register escalation thresholds and comparable compute/test budgets; measure true verified repairs per cost/time. New scorecard only **recommends a next local gate**, it does not implement unauthenticated escalation.

E. Better context selection: small falsifiable problem packet (relevant interfaces, failing test, bounded source and allowed paths). Compare against more context. No implicit sensitive data egress.

## 5. Most important open engineering/science gaps — do not bluff

- Native `node --test` on the full exact post-PR#166 module graph including this PR's new files (not just test shims).
- A correctly approved, real failure-containing Node fixture and the current Docker-backed `isolated-executor.mjs` run: A reference source patch RED→GREEN→fresh GREEN; then B forced strategy; then C actual top1 same budget. Check original tests + independent oracle. All proofs must be preserved as OWNER-created bounded evidence outside throwaway worktrees; prevent XML forgery.
- Source manifest and real SHA trust: current scorecard **declares**, but does not attest, model checkpoint & test manifest hashes.
- True generalization and BWT/retention with sealed unfamiliar compound tasks, operator manifest frozen and equal non-neural baseline. Past toy 5 cases are published development tests; no 5/5 neural breakthrough was independently reproduced. Encoder L2 drift isn't evidence of capability improvement.
- Real Android/Termux integration and signed Fold7 app upgrade: not tested here. Android local model/Memory grounding/ToolGate must not be sidestepped. Same package, owner signing cert and data preservation are mandatory for any future APK; no release in this research mission.
- Repo branch lineage is stacked; Sol and Astra code may overlap. Owner expects ALL independently valuable patches early pushed to a **separate Sol branch and Draft PR**, not a temporary disconnected cloud workspace.

## 6. Sol 6.1 explicit mission and branching/handoff rules

Sol should **own integration from PR #166 plus this successor research PR**, not start a competing planner, executor, memory database or "super-agent".

Mandatory first moves:
1. Read `AGENTS.md`, experiment contract/docs, PR #151–#167 current heads, all three new scorecard/export files, test suite, existing CCC1, coding task and repair loop.
2. Write a dependency graph with labels REAL / STUB / EXPERIMENTAL / NOT_CONNECTED.
3. **Create fresh distinct branch** `sol/continuum-local-intelligence-verification-<date-or-sha>` from verified desired base HEAD; **open Draft PR immediately**, push initial handoff and every checked patch early. Absolutely no branch permissions/locks/protection change, no force push, no deleting our branches, no work exclusively in a private ephemeral workspace. Publish complete reproducible diffs, scripts, sample manifests and test outputs to the research PR; keep code accessible to assistant and Astra.
4. Native full module suite; investigate exact breakages and patch only own branch.
5. One controlled owner-authorized CCC1 real OS-isolated reference proof on preexisting safe test fixture; check all permissions. No authenticated grant? Mark NOT_RUN and test read-only safety gates.
6. Once A actually succeeds independently, B then C; record exact NTW1 rank without rank2 fallback and all expenses. Optional external specialist never invoked without explicit owner grant, cost/privacy limits and registered source.
7. Build persistent bounded **non-attested observation** vs real independent authenticated receipt as distinct data types; never derive owner trainable positive reward from observation.
8. Measure recipe+retrieval baseline vs NN, new compound cases, AB split, replay/EWC only on genuine independent receipts, holdout pre-registered. Match compute budgets, include null/untrained NN, confidence intervals as feasible.
9. Connect proven research results to existing Android cognitive and worker interfaces ONLY via typed, review-only adapters, without cloud dependency. Include task queue, idempotent resume, crash recovery, source grant, drain/shutdown, cost/battery/wake constraints, no parallel branch races.
10. At end push exact commits, Draft PR URL, native test count and scope, red/green receipts or NOT_RUN, limitations and detailed next-step handoff file in repo.

**Hard constraints:** Never unreviewed main/Production write, auto APK install, owner signing, model checkpoint promotion, owner-private-memory mutation, tool-gate privilege escalation, self-granted sandbox, unbounded background work, unauthorised network teacher, hidden auto-merge, forged green status, or reliance on paid LLM APIs. Preserve existing repair mechanisms and Kotlin app schema. No reported "finished" claim without traced tool-connected on-device evidence.

**The priority question:** At which of (A) authorized test infrastructure, (B) atomic patch transformation, (C) neural rank1 selection, (D) independent real validation, (E) retained local recipes/model learning does the first real controlled owner-approved case fail, and what single minimal change measurably unblocks it? Prioritize that gate before new exotic neural architectures.

