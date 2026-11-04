# Continuum: three tuning bots — 2026-11-04

## Owner scope
Exactly three helpers optimize existing components only. No new features, repair scope, cross-branch integration, avatar creation, model growth, training, main merge, release, installation or provider activation. Frontier superiority is an unproven ambition, not a result.

## Frozen source and coordination
- Repository: Ullmann27/device-ai.
- Base: PR #491, branch codex/astra-jarvis-takeover-20261104.
- Base commit: e101e393dd6c80803ac9d9ac91fccd9812c4a18d.
- Base tree: 72e62267ba22309d60ad13abb3e2a5c6a155e544.
- Dedicated branch: tuning/continuum-three-bots-20261104.
- Concurrent repair (#505), integration (#506), model (#507/#508) and research (#509) branches remain independently owned.

## Three real assigned helpers
1. tuning_model_inference: existing model/inference preprocessing, decoding and allocation work; EXP-05.
2. tuning_memory_context: existing retrieval/context selection, preserving ordering, scores, provenance and time/privacy semantics; EXP-06/12/14.
3. tuning_runtime_workers: existing worker/runtime/scheduler efficiency with unchanged authority, cancellation and job behavior; EXP-01/08/11.

## Acceptance
Freeze input and original source before each measurement. Compare identical workloads and outputs, keep raw samples and source hashes, and run relevant regressions. Prefer reduced repeated computation/allocation over relaxed quality or safety limits. A synthetic host benchmark is not end-to-end model, Fold7, battery, thermal or frontier-model evidence. Reject changes with unexplained behavior differences.

## Initial status
WIP / UNVERIFIED. Three agents have been started; read-only source inspection is in progress. New optimizations and measurements are not yet claimed. Results will be recorded here with exact commits, evidence and remaining limits.

## Persistence and rollback
All useful patches, reproducible measurement harnesses and evidence belong in this review branch. Preserve the original source and compare per-file changes. Rollback is a reviewed revert of the individual optimization commit, never a reset of another chat's branch. Existing owner data, model bytes, signatures, package identity, update channel, offline preference, ToolGate and training gates remain unchanged.

## Coordination and baseline checkpoint
Checked directly on 2026-11-04:
- Repository instructions, experiment contract/register, current Issue #41 and PR #287 were read. This task keeps EXP-05/06/12/14/01/08/11 interfaces intact.
- PR #494 at 93f1f1130a3963062ad4c43c4a1edfe0cf7e2759 already owns prepared-term/top-k retrieval optimization. That work is not repeated or counted as this team's result.
- Changed-file lists for #493, #494, #496, #505, #506, #508 and #510 were compared. The LM sampler and Node backlog selector are not among their changed production paths. #506 changes AiContextCompiler for recall integration; its existing appendSection byte-budget calculation is still unchanged. Our delta is restricted to that calculation.
- #496/#506 own ChiefWorkerPool and routine scope admission; #505 owns calendar-retrieval and sequence-lifecycle repairs.

Root independently executed on the unchanged production tree:
`node --test --test-reporter=spec .github/continuum/autonomous-teamleader.test.mjs .github/continuum/development-backlog.test.mjs`
Result: 48 tests, 48 passed, 0 failures, 0 skipped, exit 0. Local documentation-only checkpoint 9ebd374b62e575a2922bb9dca71bcd2c53356fe3, tree d91f340a31792473ff5f84655a21c1bfcdbd6821; this tree equals published f67fc0bb2e2084280887dabd413072f09a57ba4f. This baseline result is not candidate acceptance.

## Selected small optimization candidates
1. LmEval.sample in learning/lm/LmModel.kt: only deterministic (temperature <= 0) token selection, preserving Float.compareTo ordering, first-index ties, top-k validation and RNG consumption. Positive-temperature sampling stays unchanged.
2. AiContextCompiler.compile/appendSection: track accepted UTF-8 bytes instead of re-encoding the full accumulated context for every section. Preserve redaction, whole-section acceptance, metadata, Unicode and exception behavior.
3. nextEligible in .github/continuum/development-backlog.mjs: linear best-task selection with the same eligibility/filter/comparator rules, instead of sorting all eligible tasks to return only the first. Real valid backlogs are limited to 500 tasks.

Agents are preparing source-bound equivalence checks and bounded host benchmarks. Formal measurements: NOT_RUN at this checkpoint. Timing is coordinated serially within this team; other conversations can still cause shared-host load. Report allocation/work counts alongside repeat measurements.

Primary measurement reference: [Android Microbenchmark overview](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview). It distinguishes individual hot-path measurements, warmup/allocation counts and device clock/thermal conditions; no Jetpack dependency or device configuration is added by this task.
