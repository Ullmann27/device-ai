# Independent audit of PR508 recovery study v1

## Scope and pinned identities

This contribution audits **new recovery-study evidence**, not the older EOS investigation. It does not train, run inference, change model/app code, invoke Actions, evaluate a holdout, or promote a model.

- Repository: `Ullmann27/device-ai`.
- Audited PR508 snapshot: `7131c1a50a9c697e481a6debe259758dbf422eef`.
- Recorded source commit of the executed study: `def5acf9cbba82ee51a53cc06fecc52e9cd0324d`.
- Prior PR507: `e726deb96dc74bacd40c2fe0ef640c6db57903f5`.
- Basis PR492: `30cd3a89810ed14fa84eca95236d3177e016118d`.
- Registration SHA-256: `540f646e6963bcc9902a362f77d963fd39aa76513837125b86085768af8b8992`.
- Recorded experiment JAR SHA-256: `901b94e4c4d9b54c5ba422a5fa3b00bd9f28f187b68ab0b79f7f9b8fc6b62911`.

Every repository source reference below is relative to that immutable PR508 snapshot. The registration is prospective: `studyTrainingStarted=false` records its preregistration state. Subsequent completed run receipts and actual measurements establish that study v1 ran; the prospective field must not be presented as a current execution status.

## Findings supported by the published evidence

### The four diagnostic runs completed; none passed the separate capacity gate

`study-v1/diagnostic-summary.json`, each `diagnostic-*/diagnostic.tsv`, and the captured `logs/diagnostic-*.tsv` report 16 completed updates. They have the same registered token schedule and 3,255 target tokens per arm. Content/EOS losses and EOS-probe values are finite in the inspected data. Captured stdout and the curated diagnostic records agree after ignoring unstructured JVM warning lines.

Completion is not an acceptance result. All four arms report `limitedContinuationSuccess=false`. Every separate post-training capacity result reports:

- `pass=false`;
- `NO_FIRST_TOKEN_OBSERVATION,INCOMPLETE_32_TOKEN_WORKLOAD`;
- zero first-token observations and zero complete 32-token observations.

The measured capacity calls stop at EOS with zero generated tokens. Their short elapsed call times therefore do not measure successful token-generation throughput. The observations establish this workload failure; they do not by themselves establish why training caused it or whether an architecture can never recover.

Some diagnostic losses improved: small-standard content BPB changed from 2.3525414158359124 to 2.35250680129539; wide-rate-scaled changed from 2.352541414133888 to 2.3518676225797335. Wide-standard changed from 2.352541414133888 to 2.4365331813205846 and wide-nf-scaled to 2.436379135799533. A reduction of fixed-batch training loss or development BPB does not override a failed preregistered capacity requirement.

Source: `experimental/lm1-training-recovery-20261104/DIAGNOSTIC_PROTOCOL.md`; selection predicate in `run_registered_study.py`; raw records and summaries under `docs/evidence/lm1-training-recovery-20261104/study-v1/`.

### Intent learning improved development scores but missed the registered signal threshold

| Arm | Step 0 | Step 32 | Step 64 | Step 128 | Selected step | Selected answer NLL |
|---|---:|---:|---:|---:|---:|---:|
| small, 132,160 parameters | 0/24 | 6/24 | 6/24 | 8/24 | 128 | 1.4439758398249918 |
| wide, 6,562,304 parameters | 0/24 | 6/24 | 8/24 | 10/24 | 128 | 1.2483727727174918 |

The selection rule ranks steps 32/64/128 by more exact correct answers, lower answer NLL, then lower step number. Step 128 is consequently the correct selection in both arms. Step 0 is a baseline, not an eligible trained candidate.

The development-signal gate requires the wide candidate to solve at least **12/24** and at least **four more** than its own wide step-0 baseline. The wide candidate improved by ten over baseline but reached only ten, so it missed the 12/24 condition. `NO_DEVELOPMENT_SIGNAL` means that this specific preregistered gate was missed. It does **not** mean zero learning or zero correct answers.

The final per-class correct counts are small `[0,4,4,0]` and wide `[2,6,2,0]`, with six development examples per class. The wide arm's two-answer lead over small on this development set does not establish a reliable, held-out, causal advantage of width.

Both selected intent checkpoints also failed their separate 32-token capacity checks. Correct one-digit answers followed by EOS are not proof of general 32-token generation capacity.

Source: `INTENT_PROTOCOL.md`; `LmIntentStudy.kt` (`exactAnswer` requires exact label text, exactly one new token, and EOS); `intent-{small,wide}/selection.tsv`; `logs/intent-{small,wide}.tsv`; `intent-summary.json`.

### The published decision remains no holdout evaluation and no promotion

`no-development-signal.json` and `intent-summary.json` consistently report `NO_DEVELOPMENT_SIGNAL` and `testEvaluated=false`; the intent summary also reports `modelPromotion=false`. The training manifests report `testReadDuringTraining=false`, and both selections report `testRead=false` and `inputsUnchanged=true`. Logged CASE/SCORE records belong to development, not test.

This audit reads no `test.sealed.tsv` bytes. Its reader rejects any path containing `sealed` before invoking Git, and a negative test verifies that rule. The sealed file's registered hash is metadata only; this audit intentionally does not recompute it. Published receipts and logs provide an evidence trail, not a complete proof that no outside activity ever accessed a file.

The later long-study protocol registers a fresh-start 512-update study with development observations at 0/128/256/512. That registration is a plan. It is not evidence of a completed long run, improved accuracy, a passing gate, or evaluated sealed test. This contribution neither executes nor modifies that study.

## Reproducible checker and its boundaries

`recovery_evidence_audit.py` is a Python standard-library-only, read-only checker. It checks:

1. Registration identity and every registered source/data file SHA-256 value, excluding the sealed split. Any unavailable non-sealed registered source/data file rejects the audit.
2. All six training receipts and six post-training capacity receipts against their raw log hashes, recorded source/JAR/registration identities, completion flags, and configured timeouts.
3. Every diagnostic summary's loss/probe/first-batch records against the raw logs, finite/range checks, EOS-margin arithmetic, final checkpoint identity, training sequence, equal token schedule, and acceptance predicate.
4. Both intent summaries against raw SCORE/SELECTION records and the selection tables/checkpoint identities. It independently reconstructs all 192 development CASE outcomes, per-class counts, EOS counts, token counts, and reported mean losses/probabilities, then recalculates checkpoint ranking and the 12/24 signal gate.
5. Post-training capacity identity and failure outcome against the measured trial records, rather than accepting the JSON summary alone.
6. Initial intent capacity-log hash binding and initial checkpoint identity.

It does not independently execute Kotlin, regenerate NLL from weights, rehash unavailable model/JAR/baseline binaries, inspect sealed contents, or infer Android behavior. A recorded source commit is checked consistently across receipts; this is not independent proof of the process that originally ran. Loss aggregation uses only a narrow tolerance for JVM Float-to-string rounding.

The script disables Git lazy fetch and sets per-read timeouts. This matters for a partial clone: otherwise a missing blob can stall while Git tries an unavailable network origin. Missing registered source blobs and missing decisive result logs/receipts fail the audit. A complete clone with the pinned objects is sufficient to rerun without network.

## Commands and evidence

Run from this directory, replacing the repository argument with any local clone containing the pinned snapshot:

```sh
python recovery_evidence_audit.py --repo /path/to/device-ai --commit 7131c1a50a9c697e481a6debe259758dbf422eef
python -m unittest -v test_recovery_evidence_audit
```

**Actual full audit: PASS**, with 56 audited Git blobs, all 20 non-sealed registered files hash-matched, no unavailable registered source files, 12 receipt/log pairs, and 192 development CASE records independently aggregated. **Actual targeted tests: 13 tests PASS.** The machine-readable result is recorded in `evidence/recovery-audit.json`; the read trace is `evidence/recovery-audit-readlog.txt`; test output is `evidence/recovery-audit-tests.txt`. Treat the JSON's status as authoritative. At the first attempt, the local partial clone lacked several published receipt/capacity blobs, so that attempt correctly failed rather than claiming a full audit. The parent subsequently supplied the five required missing blobs through its authorized connector and verified their Git object identity against the pinned tree. The final full rerun then passed with lazy fetch disabled; the current evidence file is that successful rerun.

The targeted tests cover tampered log identity, source/JAR/registration mismatch, invalid checkpoint schedules, selection tie-breaking, NaN/infinity, duplicate TSV keys, false exact-answer claims, two-token answers incorrectly counted as exact, aggregate discrepancies, and the sealed-read prohibition. These tests run no model or training pipeline.

### Root integration correction

The preceding 13-test evidence describes the original helper delivery. Root subsequently reproduced a missing-source defect: the original checker listed an unavailable registered source blob but could still return PASS. The integrated checker now rejects that case before the full audit. A new regression test brings the recovery suite to **14 passing tests**; the real pinned-snapshot audit with all source blobs available still produces the same result. No model process was started for this correction.

The original checker, tests and this report are preserved under `evidence/recovery-delivered-source/`; `evidence/deliverable-sha256.json` remains the original helper manifest and refers to those original bytes. The other original evidence files remain unchanged. Current source hashes and actual v2 run-log hashes are recorded in `root-final-verification/receipt.json`. The exact reproduced failure and corrected rejection are recorded in `root-final-verification/missing-registered-blob-red.json` and `root-final-verification/missing-registered-blob-green.json`.

## Remaining measurement

No new model repair is justified by this evidence audit alone. The remaining scientific question is whether a separately preregistered, bounded continuation can produce both a real development signal and the required unrestricted generation capacity. Held-out evaluation remains subject to the original gate and protocol. This audit provides no measurement of 16M or 64M feasibility and no device measurement.
