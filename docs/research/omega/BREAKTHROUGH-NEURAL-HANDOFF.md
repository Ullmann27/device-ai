# Breakthrough neural experiment — 2026-11-03

Status: `WIP / UNVERIFIED / RESEARCH_SHADOW`. Experiment IDs EXP-04/EXP-05; no Android caller, productive weights, owner memory or promotion change. Base source is `64ec06a73f50cae9ee49035c840710d87777d50f`; branch `codex/breakthrough-neural-20261103`, PR base `codex/team-chief/omega-20261103`.

## Pre-registration (before new experiment execution)

Hypothesis: the existing frozen CDX2 base (11 weights) and current frozen-backbone adapter (11+2 weights) do not reliably generalize exact entity/relation/source binding to new German role swaps, unknown entities/values, corrections and source corruption. Compare their RAW, RULE and VERIFIED outputs on the same requests; inspect feature/candidate availability when errors occur. Existing A–E corpora are consumed regression data, never a new blind test.

Ownership: only `tools/continuum-span-research/breakthrough-neural-20261103/**`, `docs/evidence/breakthrough-neural-20261103/**`, and this handoff. Existing production/model files remain read-only. No second verifier/ranker or memory system.

Before inspecting independent holdout cases/labels: freeze executable source, model/checkpoint hashes, prediction/scoring policy and candidate selection. Independent QA owns unseen cases; receive schema/domain/count only. Development cases are explicitly authored and consumed. After unblinding, no tuning, relabeling, case removal or threshold changes. Full old C including C027 is a regression gate.

If development exposes a fixable representation error, at most three bounded candidate attempts may be tried. No full-model training. Any candidate must preserve the eleven original weights; new weights/features, if justified, live only in this namespace. No claim of learned semantics from handwritten features. A no-gain or failing candidate is rejected and retained.

Acceptance requires: complete reproducible per-case exact answer/source/revision/hash/UTF-16 tuple scores; zero added unsupported positives/false source assignments; zero case regressions versus current learned baseline; at least 4 paired exact improvements and at least 5 percentage points over both current learned VERIFIED and RULE on independent holdout; exact paired two-sided binomial p <= .05; old C perfect retention; corruption controls fail closed. Failure of any gate means `REJECTED_FOR_PROMOTION`, even if training loss falls. A frozen diagnostic experiment without a successful candidate is a valid negative result. Host-only timing/RSS, not Fold7 performance.

Resources: max 2 logical CPU cores (`ActiveProcessorCount=2`, process affinity when available), JVM heap <=1 GiB, <=3 development candidates, <=25 minutes aggregate execution budget. No Gradle, external paid API, external code download or productive checkpoint import. Quota: `NOT_VISIBLE`.

Measurements: exact supported tuples, correct abstentions, unsupported positives, false source assignment, false abstention, paired disagreements, Wilson intervals and exact McNemar/binomial; classifier NLL only where gold is in the candidate set, with absence reported. Equal batch warm-up/timing for models; record per-arm median/p95 milliseconds and host child peak RSS KiB. New corpus/source/checkpoint SHA-256 manifests and actual Git SHA accompany outputs. Hashes establish identity, not truth or independent authorship.

Rollback: revert only this lane's source/evidence commits on a review branch; no runtime/model migration is necessary. Next entry point: new namespace runner and its frozen manifest once added. Current tests: `NOT_RUN`; independent holdout: `NOT_RUN`; Android/device: `NOT_RUN`.

## Development observation and one pre-registered residual attempt

Source `5c285f0d4b574d0580e7f946ff6b9c0203b8fb8d` executed the author-known DEV40: both base11/current13 RAW and VERIFIED 24/40 (8/24 supported, 16/16 justified abstentions), RULE 28/40. No unsupported positives. Six gold spans absent from the candidate lattice; four gold choices share identical features with competitors. This is a representation gap; no full retraining is justified.

Before its first training run, one amended representation is fixed: reuse the current options, add exact source-position slots for explicit inverted static clauses and apostrophe/connector/four-word places; add two residual features for that unique slot versus NOANSWER. It is handwritten task syntax, not learned syntax or another verifier. All prior thirteen weights remain bit-identical. The existing verifier runs unchanged on all original evidence. Training uses 48 new author-known examples, fixed seed17, 60 epochs, rate0.05, no parameter search. Equal-budget arms: base11, current13, amendedZero (new lattice/features with zero residual weights), trained15. Only the final two weights train. DEV is consumed; independent QA holdout stays sealed until source/checkpoint freeze. Maximum planned candidate attempts now one; reject if the original registered comparison to RULE/VERIFIED fails.
