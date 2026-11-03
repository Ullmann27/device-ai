# Breakthrough neural experiment — 2026-11-03

Status: `RESEARCH_SHADOW / REJECTED_FOR_PROMOTION` after real development and old-C replay; independent QA holdout pending at this checkpoint. Experiment IDs EXP-04/EXP-05; no Android caller, productive weights, owner memory or promotion change. Base source is `64ec06a73f50cae9ee49035c840710d87777d50f`; branch `codex/breakthrough-neural-20261103`, [Draft PR402](https://github.com/Ullmann27/device-ai/pull/402), PR base `codex/team-chief/omega-20261103`.

## Pre-registration (before new experiment execution)

Hypothesis: the existing frozen CDX2 base (11 weights) and current frozen-backbone adapter (11+2 weights) do not reliably generalize exact entity/relation/source binding to new German role swaps, unknown entities/values, corrections and source corruption. Compare their RAW, RULE and VERIFIED outputs on the same requests; inspect feature/candidate availability when errors occur. Existing A–E corpora are consumed regression data, never a new blind test.

Ownership: only `tools/continuum-span-research/breakthrough-neural-20261103/**`, `docs/evidence/breakthrough-neural-20261103/**`, and this handoff. Existing production/model files remain read-only. No second verifier/ranker or memory system.

Before inspecting independent holdout cases/labels: freeze executable source, model/checkpoint hashes, prediction/scoring policy and candidate selection. Independent QA owns unseen cases; receive schema/domain/count only. Development cases are explicitly authored and consumed. After unblinding, no tuning, relabeling, case removal or threshold changes. Full old C including C027 is a regression gate.

If development exposes a fixable representation error, at most three bounded candidate attempts may be tried. No full-model training. Any candidate must preserve the eleven original weights; new weights/features, if justified, live only in this namespace. No claim of learned semantics from handwritten features. A no-gain or failing candidate is rejected and retained.

Acceptance requires: complete reproducible per-case exact answer/source/revision/hash/UTF-16 tuple scores; zero added unsupported positives/false source assignments; zero case regressions versus current learned baseline; at least 4 paired exact improvements and at least 5 percentage points over both current learned VERIFIED and RULE on independent holdout; exact paired two-sided binomial p <= .05; old C perfect retention; corruption controls fail closed. Failure of any gate means `REJECTED_FOR_PROMOTION`, even if training loss falls. A frozen diagnostic experiment without a successful candidate is a valid negative result. Host-only timing/RSS, not Fold7 performance.

Resources: max 2 logical CPU cores (`ActiveProcessorCount=2`, process affinity when available), JVM heap <=1 GiB, <=3 development candidates, <=25 minutes aggregate execution budget. No Gradle, external paid API, external code download or productive checkpoint import. Quota: `NOT_VISIBLE`.

Measurements: exact supported tuples, correct abstentions, unsupported positives, false source assignment, false abstention, paired disagreements, Wilson intervals and exact McNemar/binomial; classifier NLL only where gold is in the candidate set, with absence reported. Equal batch warm-up/timing for models; record per-arm median/p95 milliseconds and host child peak RSS KiB. New corpus/source/checkpoint SHA-256 manifests and actual Git SHA accompany outputs. Hashes establish identity, not truth or independent authorship.

Rollback: revert only this lane's source/evidence commits on a review branch; no runtime/model migration is necessary. Next entry point: new namespace `run.py` and `docs/evidence/breakthrough-neural-20261103/CANDIDATE-FREEZE.json`. Android/device: `NOT_RUN`.

## Development observation and one pre-registered residual attempt

Source `5c285f0d4b574d0580e7f946ff6b9c0203b8fb8d` executed the author-known DEV40: both base11/current13 RAW and VERIFIED 24/40 (8/24 supported, 16/16 justified abstentions), RULE 28/40. No unsupported positives. Six gold spans absent from the candidate lattice; four gold choices share identical features with competitors. This is a representation gap; no full retraining is justified.

Before its first training run, one amended representation is fixed: reuse the current options, add exact source-position slots for explicit inverted static clauses and apostrophe/connector/four-word places; add two residual features for that unique slot versus NOANSWER. It is handwritten task syntax, not learned syntax or another verifier. All prior thirteen weights remain bit-identical. The existing verifier runs unchanged on all original evidence. Training uses 48 new author-known examples, fixed seed17, 60 epochs, rate0.05, no parameter search. Equal-budget arms: base11, current13, amendedZero (new lattice/features with zero residual weights), trained15. Only the final two weights train. DEV is consumed; independent QA holdout stays sealed until source/checkpoint freeze. Maximum planned candidate attempts now one; reject if the original registered comparison to RULE/VERIFIED fails.

## Executed results before independent holdout

Predictive source: `daf1f40b2d1e0e09826eb19a0acd0ac78f7409df`. Frozen source/data/checkpoint manifest committed and pushed at `4d2e825f2caf783085dcb85c005960cd01347b14`, before independent holdout disclosure. New checkpoint SHA256 `b80b4bd16f04cd6cabd473f4701fb9b19675a1df65f2fe8908da5f1e06772519`; learned residuals `[14.148664958615312, -13.905599333694303]`. No existing checkpoint bytes changed. New training corpus SHA256 `4aceeb9f056d6834d4f58d9c68f81d54094a718ddd788c25e099794d08f9f4bd`.

| Authored DEV40 arm | RAW exact | VERIFIED exact | RULE exact | Unsupported RAW | Gold available |
|---|---:|---:|---:|---:|---:|
| base11 | 24/40 | 24/40 | 28/40 | 0 | 34/40 |
| current13 | 24/40 | 24/40 | 28/40 | 0 | 34/40 |
| amendedZero | 24/40 | 24/40 | 28/40 | 0 | 40/40 |
| trained15 | 32/40 | 24/40 | 28/40 | 0 | 40/40 |

There are 24 supported and 16 missing DEV cases; trained15 answers 16/24 supported and correctly abstains16/16. RAW eight paired improvements/zero regressions versus current13 and amendedZero (descriptive exact paired p=.0078125 on consumed development, not confirmatory evidence). The manual representation removes six missing gold spans and two of four feature-alias cases. Learning calibrates the introduced syntactic feature; it does not learn parsing or a general semantic representation. Training's unknown-entity negatives do not activate the new feature, so this curriculum does not teach hard-negative discrimination of a falsely grounded slot.

**Mandatory consumed old-C gate FAILED.** Current13 and amendedZero are RAW55/55, trained15 RAW54/55: C054 is an added unsupported positive under the old frozen contract. A target assertion plus an unsupported second clause for the same entity lets the residual override the old ambiguity feature. RULE and VERIFIED remain55/55 because the original verifier rejects the proposal. C027 remains correctly abstained. The old base11 is RAW53/55 and VERIFIED55/55. No relabeling or post-freeze tuning; candidate rejected even if subsequent holdout were favorable.

Four invalid-transport controls (hash mismatch, stale text/hash, revision zero, duplicate source ID): all four arms, all three output modes correctly abstain. Five real JVM process tests pass: gold-label refusal, frozen-weight mutation refusal, nonfinite residual refusal, bounded request count, byte-exact checkpoint reproduction. Existing independent scorer11/11 tests pass including100 source-tuple mutations. Runtime files are read-only snapshots before/after these tests.

Failed attempts retained: first old-C invocation stopped after prediction at the harness's strict wrapper identity assertion because legacy input had an extra top-level `schema` key. The 55 case arrays were exactly equal. `old-c/normalized-inputs.json` removes only that wrapper metadata; corpus/source/checkpoint/scorer unchanged. Failed command/predictions and explicit reason are in `old-c-wrapper-failure/`. The normalized repeat is the reported scored result, not a neural repair.

Host resources: two-core affinity, JVM heap cap1GiB. DEV training wall268.075ms (user CPU0.36748s + system0.028863s). DEV instrumented median/p95 milliseconds: base11 .316/.624; current13 .341/.606; amendedZero .397/.720; trained15 .465/.845. Equal five-pass warmup/five-pass measurements include full RAW/RULE/VERIFIED and trace allocation; no inference speedup claimed. DEV total child peak RSS594300KiB, old-C607948KiB, corruption599188KiB; Linux cumulative child high-water includes compiler, not isolated inference RAM or Android PSS. Actual commands and raw sample distributions retained. No Fold7, energy, APK, native neural model, or real user-data measurement.

All new code is first-party project research code using pinned existing CDX2 math/Probe/Verifier/scorer. No external paper code or dataset imported; no new license dependency. No productive main caller exists. The scientific outcome is a reproducible rejected slot/learned-residual candidate, not a general-intelligence breakthrough.

## Reproduce

```bash
source /workspace/continuum-build-env/env.sh
python3 tools/continuum-span-research/breakthrough-neural-20261103/run.py \
  docs/evidence/breakthrough-neural-20261103/development/inputs.json /tmp/neural-dev-fresh \
  --corpus docs/evidence/breakthrough-neural-20261103/development/corpus.json \
  --corpus-sha256 8b7ce98c02ca3713d85f7e6e7b201a24768419f547c98c7454664b1bdc4e25a3 \
  --candidate docs/evidence/breakthrough-neural-20261103/checkpoints/residual15.json \
  --freeze-manifest docs/evidence/breakthrough-neural-20261103/CANDIDATE-FREEZE.json
python3 tools/continuum-span-research/breakthrough-neural-20261103/test_contracts.py /tmp/neural-dev-fresh/classes
node --test scripts/cdx2-blind-score.test.mjs
```

Training reproduction: use a new output directory and replace `--candidate ...` with `--train docs/evidence/breakthrough-neural-20261103/training/corpus.json`. The produced checkpoint must match the frozen digest. Old-C command uses `old-c/normalized-inputs.json`, existing `docs/evidence/issue340-20261101/m2/blind-c/corpus.json`, and SHA `3bd5bddb76a50d020c9216db9eebf7ba9b717967a7b0947b3415f3a2c0fd5a1c`. Invalid sources are separate `--corruption-controls` because the immutable scorer correctly rejects malformed corpus source hashes before scoring.

Next experiment requires a new independent holdout and a separately pre-registered hypothesis; do not repair C054 or expand the verifier against the now-exposed evaluation and call it blind. Preserve this rejected candidate and current13 baseline. Any future syntax expansion needs explicit invalidator/ambiguity representation and independently accepted source semantics before considering app integration.

## Independent QA handoff boundary

QA authored and froze64 unseen cases before opening this candidate in QA commit `0ca2b075` (full commit/remote evidence belongs to the QA handoff). Corpus SHA256 `006d9eab5ce46efca382b421d1289f6ae045ebb8a81acd8ec78160b2254711e1`;12 separately frozen invalid-transport controls SHA256 `0145f560bca673049d560749544e56126058c22745e8b613d22ebba74e9348d8`. Only schema/domain/count and hashes were shared with this implementer before source/checkpoint freeze. QA received frozen source `4d2e825f2caf783085dcb85c005960cd01347b14` and a label-free invocation; labels were never supplied to the prediction process. Later commits alter tests/evidence/documentation only; all14 predictive freeze hashes remain exact.

At this lane's final handoff the Chief relayed that QA64 found no15-parameter gain against13/amendedZero/RULE. The final scored report and exact counts have not yet been supplied directly to this lane, so this is attributed status, **not** a newly claimed measurement here. Formal independent result: `EXTERNAL_QA_REPORT_PENDING`; read QA's exact report/commit before quoting aggregate numbers. Old-C rejection is already decisive. No further predictive experiments or integration are authorized by a favorable interpretation of QA.
