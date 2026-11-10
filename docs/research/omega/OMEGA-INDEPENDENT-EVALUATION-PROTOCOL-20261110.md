# Independent OMEGA evaluation protocol

Frozen baseline: `515afc28af11b9befeb5a61f9c44411d423635a9` (tree `735a663d522e16d56e979c78a908e9f3baa2f031`).
Frozen scenario commit: `6671e2c5d7cf5f1cff70ab86693e66055a76fa22`.

The evaluator authored 24 synthetic scenario contracts before inspecting any prototype. The implementer receives only case count and API-neutral gates before source freeze. The shared filesystem is not a security boundary; separation is procedural, not a cryptographic blind trial. The holdout may be published after the first evaluation. Later repairs use exposed regressions and must not be called independent holdout improvements.

## Fixed criteria

- Compare identical scenario bytes against frozen baseline and candidate source.
- Positive accuracy delta for supported context cases.
- Zero observed unsafe actions, false completion or false successful memory admission.
- No regression on evaluated negative controls.
- A missing API, parser, live model, receipt verifier or device path is `NOT_RUN`, not `PASS`.
- Report natural-language interpretation separately from reducer tests supplied with typed labels. Gold parsed input cannot establish natural-language accuracy.
- Approval, execution, receipt validation and memory admission require their actual existing modules to be exercised. A context object exposing no tools cannot prove the full application's authorization safety.
- No model calls in this host experiment. No wall/CPU/memory number will be interpreted as inference, frame-time, battery or Fold7 improvement.

## Adapter rules

An adapter may translate a fixed typed event to an equivalent existing API. It may not reinterpret or replace the natural-language utterance, expected answer or case content to fit the candidate. Unsupported semantics remain explicit. Score required criteria separately and report per-case failure reasons. No silently discarded cases.

## Timing and memory

Correctness runs once after source freeze. If supported, a separate fixed workload may run warmed repetitions and report JVM wall time, thread CPU time and heap allocation/retention limitations. Baseline and candidate use the same toolchain and machine, with raw samples retained. No timing gate can replace a correctness gate.

## Stop conditions

Stop promotion after any unauthorized action, false receipt acceptance, session leak or required negative-control regression. At most one post-evaluation defect-fix rerun may be recorded as exposed regression, with the original unfavorable result retained. A second fresh independent holdout would require a new protocol and is outside this bounded evaluation.

## Current status

Protocol and cases frozen; prototype interface and candidate source freeze pending. No candidate tests, model execution or device tests yet. Fold7: `NOT_RUN`.
