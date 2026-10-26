# Continuum local developer worker and SWB1 shadow-lab — 2026-10-26

## Observed GitHub automation failure, evidence limits

Current scheduled `Continuum Hourly Watch` failed repeatedly on 2026-10-26 (e.g. runs 40387465683, 40380889559, 40369410813). In run 40387465683, the GitHub `watch` job was marked failure approximately **two seconds after job creation** with **zero reported executed steps**; repair and publish were skipped. GitHub returned HTTP 404 BlobNotFound for per-job logs. The separate scheduled `Continuum Zero-Cost Core` also had failing runs, with comment-triggered jobs skipped by its authorization condition. These signals **do not prove** a code, quota, runner, billing or permission root cause: no concrete error text is available. Do not assert that a code patch fixes the runner provisioning problem or send blind costly CI retries.

The latest persisted GitHub state on `continuum-state` was last modified 2026-10-16 03:06 Europe/Vienna, **not today**. SWB1: 12 rounds, 2 accepted, 13,000 accepted model weight updates, 45 public replay observations. Nine? No: **ten** consecutive rejected rounds (#3–#12) had `selectedSteps=0` and `VALIDATION_REGRESSION`; the baseline semantic validation was 13/20 while every one of those training candidates fell to 11/20. Intent candidates often rose from 19/20 to 20/20. Preservation of the baseline is correct behavior; the acceptance rate is not a reliable general intelligence measure.

## Implemented two independent improvements on a private review branch

**Owner-local worker**, `scripts/continuum-local-development-worker.mjs`: `node scripts/continuum-local-development-worker.mjs --once /external/status-dir` or `--loop /external/status-dir`. Runs on a powered owner computer without GitHub Actions or AI APIs; samples the exact Git HEAD, requires a clean non-main/non-production/non-release checkout, runs all existing bounded Node tests without shell interpolation, waits **at least one hour** between cycles, reports genuine TAP pass/fail/skipped counters and preserves failures rather than auto-approving a patch. One instance locks its output directory. Status file `latest.json` is outside the repository. No network access, owner data reading, Android signing, Git checkout/merge/push, CI secret or repair execution is performed by this worker. Its work is detection and gating, not autonomous patch generation.

**SWB1 experiment**, `.github/continuum/neural-shadow-budget-lab.mjs`: exports a deterministic, optional sweep of the existing `trainRound` over budgets 250,500,1000,2000,4000,8000,12000, with all profiles starting from **exactly the same verified baseline**. It uses the production training/validation/retention gates unchanged; AUDIT is recorded only AFTER selection. The report contains counts, checkpoint SHA, withheld audit metrics and the stagnation diagnosis, but **no model bytes** and `promoted=false` with `weightsWritten=false`. The local runner `scripts/continuum-swb1-shadow-run.mjs` accepts a read-only existing `.continuum/state.json` path, writes metrics only outside the checkout, and preserves the exact state file bytes. Source corpus and 2/12 baseline remain unchanged.

Example from a locally checked-out approved development worktree:

```bash
node --test .github/continuum/local-development-worker.test.mjs .github/continuum/neural-shadow-budget-lab.test.mjs
node scripts/continuum-local-development-worker.mjs --once /tmp/continuum-dev-worker
node scripts/continuum-swb1-shadow-run.mjs /external/state.json /tmp/continuum-swb1-evidence
```

`/external/state.json` must be an owner-verified **copy** of the existing GitHub `continuum-state:.continuum/state.json`. Do not overwrite it, push private local app memories, or use model-export artifacts as state input. The development branch is separate from `main`, which is **not modified**.

## Acceptance gates still outstanding

- Native exact-HEAD Node `--test` execution of both new test files and the full existing suite; these source files are committed but not yet executed with the actual private repo checkout in this host. No claims of improved 2/12 yet.
- Run shadow A/B on an immutable copy of the **real frozen state**, using distinct unseen held-out examples before choosing a future profile. Existing AUDIT has been exposed to prior reports; using it repeatedly to tune strategy would bias generalization claims.
- Analyze whether the 10 repeated `VALIDATION_REGRESSION` results derive from excessive positive-only public-source replay, learning-rate imbalance, or saturation; never silently relax semantic no-regression checks.
- Review GitHub Actions billing/runner availability using owner account UI. The workflow produced **no steps**; editing the Python/Node script alone cannot prove a fix to provisioning.
- No automatic write to `main`, `continuum-state`, released Android weights, or owner APK. No new cloud charges.
