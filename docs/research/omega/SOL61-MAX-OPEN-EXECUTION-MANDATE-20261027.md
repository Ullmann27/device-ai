# SOL 6.1 / MAX – CONTINUUM EXECUTION MANDATE 2026-10-27

**Status:** Owner-directed handoff for the next coding session, not evidence of work already completed.  
**Scope:** ONLY Device AI Continuum (`Ullmann27/device-ai`) and the actual installed Android main application. NO demo APK, unrelated app, cloud LLM mandate, unreviewed main/release pushes or automated production model promotion.

## 0. ABSOLUTE NON-LOCKING / OPEN-HANDOFF DIRECTIVE

You are Sol 6.1 with maximum reasoning. Act as an executing senior engineer, not a status narrator. **EVERY completed result must remain inspectable, writable and extensible by ChatGPT, Sol, and a later reviewer.** Preserve a normal Git branch with normal UTF-8 source files and complete tests; never close the work inside a private ephemeral workspace, unpublished commit, opaque artifact, encrypted archive, password, restricted generated code, locked discussion or hidden submodule. Do not delete old development branches or prior commits; do not force push or rebase other collaborators' branches. No new PR/base lock, CODEOWNERS restriction, irreversible history rewrite or protected-branch edit. No artificial need for your continued availability. Push completed work to the owner's existing GitHub repository, leave an open Draft PR, and document branch name, exact HEAD SHA, base SHA, file list, tests, failures and what the successor can do next. This rule applies even if interrupted.

If you lack GitHub or local machine access, say so explicitly and produce immediately applicable complete patches + test commands and a truthful non-execution report; never claim to have pushed or tested what you could not execute.

## 1. PIN THE REAL CURRENT STATE BEFORE ANY EDIT

Repository: `Ullmann27/device-ai`.

- Research/Android integration **Draft PR #176**: `chatgpt/continuum-feedback-process-safety-20261026`, head `3585d99cc71b9f9d238db317256e7461f2110c6d`, base `08bfe94f21a9741a2144e9e4bae72b341fa68332`. Contains CCC1/VH-SR evidence gates, process-safe feedback, isolated **NEURON DEMO** fix for owners with prior learned lessons, and associated regression sources. NOT YET full Android/JUnit-verified or released.
- Local worker / SWB1 research **Draft PR #177**: `chatgpt/continuum-local-worker-learning-lab-20261026`, head `a8614744c92b838877b97b656b731917f17371e4`, base `ec4b4caecd64835e56af924dae1f7ad825059bfc`. The two PRs target different integration bases: inspect merge bases before cross-porting; NO blanket/cherry-pick of unrelated experiments.
- `main` verified at `190f5eabaa60fc59292b2b80d3086aafd7743a80`; never change it as part of this assignment.
- Persistent GitHub state: `continuum-state:.continuum/state.json`, blob `420880e8040a7af3436805c68fc58e65ed4d7a10`; original SWB1 model SHA256 `5e577c21b22c6b31ff71274640d45f8900ca646e370bef6179ab608ffb992cd1`. **12 historical training rounds, only 2 accepted**, **13,000 accepted weight updates**, 45 public replay records. Rounds #3–#12 repeatedly `VALIDATION_REGRESSION`; baseline semantic 13/20, unaccepted long-run candidates typically 11/20 while Intent 19/20 → 20/20.
- Previously published owner-installable APK: `0.3.23`, `versionCode=24`, package `at.heinz.deviceai.mentorpaired`, owner persistent signing certificate. The separate `0.3.24` / code 25 QA APK was **test signed** and not an in-place owner update. Never advise uninstall/reset; no new APK claims without same-certificate verified build.
- GitHub scheduled **Hourly Watch** run `40387465683`, both original and targeted rerun #2 failed before *any reported step*; repair/publish skipped, job logs inaccessible with 404 BlobNotFound. This establishes failure before observable script execution, NOT whether runner quota/account/billing/permission is responsible. Stop blind reruns. `continuum-zero-cost.yml` also had failed scheduled runs; inspect independently.
- The new frozen test suite `.github/continuum/neural-fresh-transfer-20261026.mjs` was committed **before first score**, blob `cd09e9dc8be70b6b36628beb2659ddba4503e102`, with 20 novel Intent and 32 balanced positive/negative semantic comparisons, zero literal prior corpus overlaps. Actual old model: Intent **18/20**; semantics **16/32 (chance-like)**. Three 1000-step SWB1 shadow arms (old replay, reduced replay, no replay) **did not improve correct-answer count**. CONTROL and NO_REPLAY met old validation loss gate, LOW_REPLAY did not. No weights were promoted. Since the scores are now known, this suite is NO LONGER a pristine unseen holdout for selection.

Re-fetch all refs and original source before coding. If HEADs changed, do not quietly overwrite; reconcile and document real changes. The record above is a handoff baseline, not a promise that the current repo still matches it.

## 2. PRIORITY ONE — REPLACE THE BROKEN AUTONOMOUS EXECUTION PATH

Goal: a genuinely working, power-on persistent, **owner-local** test-and-research controller without relying on GitHub Actions, GitHub-hosted runners, paid AI, or downloading model APIs. Review `scripts/continuum-local-development-worker.mjs` from PR #177, not a rewrite from assumptions.

Implement/verify in an isolated development worktree:
1. A process lifecycle with restart policy (user-level systemd where supported; for Android use the officially supported `JobScheduler`/WorkManager mechanisms ONLY when verified in actual source; no invented APIs). Respect exact machine environment. The local worker must survive individual failed test cycles and not treat SKIP/TODO as success.
2. Safe maximum CPU/memory/time budgets, process timeout, manual pause/stop, durable cycle receipts, exact Git HEAD identity, prevention of overlapping workers, and stale-lock recovery **only with verifiable process ownership**. Protect against symlinks, untrusted repo paths, untracked tests, checkout drift, and corrupted receipts. A Node test runner executes code; it is not an OS sandbox: use trusted tracked code and an actual OS isolation policy where available.
3. Run genuine existing Node tests, optionally Kotlin JUnit/Gradle if host toolchains are present. Classify `PASS`, `FAIL`, `SKIP`, `NOT_RUN` separately and preserve logs. Include a deterministic offline reproduction of a failed cycle followed by a successful retry.
4. Add a **gated, isolated repair proposal** path only if present independent CCC1 verifier and existing repair pipeline can safely be reused. No direct source patch acceptance, no main/production write, no automatic code execution from web/logs, no auto-merging. If proposer isn't implementable safely, keep runner read-only and state it plainly.
5. Diagnose GitHub's zero-step failure separately at repository/account runner or billing level using actual status evidence; if unavailable, label unknown and keep local replacement as durable solution. NEVER invent root cause or imply repaired GitHub Actions without successful same-job run.

Required deliverable: reproducible local launch and stop commands, verified ONE-SHOT and cyclic tests, report schema, restart demonstration, no cloud dependency.

## 3. PRIORITY TWO — CAUSAL IMPROVEMENT OF SWB1 SEMANTICS, NOT AN INFLATED 2/12

Understand `.github/continuum/neural-core.mjs`, `portable-subword.mjs`, `neural-curriculum.mjs`, current state and SWB2 experimental features. Research says public web title/summary replay currently generates **positive-only semantic updates**; at least one plausible cause of 13/20→11/20 regression is missing well-grounded negative contrasts and representation interference. This is a *hypothesis*, not a proven diagnosis.

Execute, with immutable original baseline and SHA:
1. Pre-register a brand-new **sealed** multi-domain counterfactual/semantic generalization test with strict phrase/group/source deduplication and group-aware partitioning; freeze file hash **before** evaluating candidates. Keep training, validation, previously revealed AUDIT/TRANSFER, and the NEW sealed test disjoint. Only evaluate final selected experimental mechanism once on the new sealed set. Do not select by leaked audits or transfer examples.
2. Add trusted, explicitly labeled **positive + negative contrastive semantic pairs**. Never assume unrelated search results are semantically false, and never automatically label unverified web titles/abstracts as true. Distinguish `PUBLIC_OBSERVATION` from `TRUSTED_LABELED_EXAMPLE` with provenance, hashes and deterministic rejection tests. Do not train from owner-private conversations without opt-in.
3. Run at least a baseline-versus-controlled ablation to isolate sources of loss: legacy positive-only replay; zero public replay; manually grounded balanced negative/positive replay; optional separate intent/semantic gradient or bounded replay strength. Avoid multiplying 20 tuned hyperparameters on the same holdout. No training on evaluation examples.
4. Investigate SWB1 representational limits and whether the existing experimental SWB2 weighted feature map improves *previously unseen* semantics. **Never reinterpret SWB1 bytes as SWB2** or silently change Kotlin/Android model format. If SWB2 is proposed, versioned migration, checksum, cross-language parity, and rollback are mandatory.
5. Preserve both tasks: INTENT and SEMANTIC exact validated correct counts; no catastrophic forgetting, monotonic quality gates and model SHA. Report per-domain confusion matrix, negatives false-positive rate, held-out counts, loss, confidence/uncertainty and multiple seed stability; no statistical overclaim on 20–32 samples.
6. Do not persist any candidate to `continuum-state` or change the official **2/12** as part of research. A future genuine accepted round increments the denominator honestly (e.g. 3/13, not 3/12) only after independent verification and owner-approved promotion.
7. If the native exact-checkout run contradicts the V8 adapter calculations, trust the authoritative native run and diagnose any adapter discrepancy. Do NOT pretend source syntax or synthetic smoke implies native Node success.

**Success is measured in improved unseen correct-answer generalization, not number of SGD steps, lower loss alone, a higher acceptance percentage, or impressive fictional intelligence claims.** If unchanged, explicitly report that and propose next falsifiable experiment.

## 4. PRIORITY THREE — MAIN APP INTEGRATION AND OPEN REVIEW

Review PR #176's `NeuralDemoSandbox`, `AgentActivity` wiring, `ResearchFeedbackFileLock`, and actual CCC1/VH-SR evidence bridge. Run native Kotlin/JUnit, full Gradle tests and lint where toolchain exists. Preserve the existing owner data store and certificate. Resolve failures, cross-process lock behavior, no symlink leakage and cancellation. Integrate only independently reviewed/compatible pieces into the **existing main Android application** via the established repair gate. NEVER generate a second demo Android app. No APK link or 'installed' status until actual signed artifact, APK signature, versionCode, package and device upgrade validation. No silent new APK from temporary test signer.

## 5. EXECUTION CADENCE AND DELIVERY

Start with one short factual inventory of HEADs/available tools and a specific ranked risk list, then WRITE/RUN/VERIFY. Create small reversible commits on **your own new unprotected work branch**, with one small Draft PR to the correct integration base; explain any relationship to #176 and #177. Keep ownership and branch refs accessible to the next agent, no force pushes, no branch deletions. Prevent accidental simultaneous edits to collaborators' open PR branches.

After each functional slice, show:
- exact branch/HEAD/source SHA and changed filenames;
- **actual** native command, test count, exit code and logs; distinguish `NOT_RUN` from `PASS`;
- measured old vs candidate held-out performance, model hash, no promotion;
- obstacles and immediate next repair, not generic analysis;
- whether executable code remains open and successor can edit the same files.

Final handoff MUST include: durable GitHub code/PR links, no private workspace-only commit, unaltered baseline SHA, all open test failures, next executable command, and a 10-line plain German summary for the Android owner. If interrupted, save a usable intermediate Draft PR with all source/tests/logs and a precise continuation note, **without closing or locking anything**.

**Immediate next action:** inspect current PR #177 HEAD and run/fix its native Node tests; resolve the short-limit/loop/test-failure correctness; then implement one controlled balanced negative semantic experiment against the fixed SWB1 model with a newly sealed holdout. Do not spend the first session merely rewriting this mandate.
