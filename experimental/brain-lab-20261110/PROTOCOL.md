# BRAIN-LAB-1: bounded compute and event probes

Status: WIP / UNVERIFIED. This protocol is committed before implementation and
measurement. Owner: brain_lab; integration owner: Cinema root integrator.
Target: `cinema/fluid-light-stage-20261110` (base `4ac9c9b94b9fb5e391646e2e99e986db32ec7997`).
Experiments: EXP-03, EXP-05, EXP-07, EXP-11; no registry completion claim.

## Inventory and reuse

- `tools/continuum-cognitive-pilots/verified-reuse-pilot.mjs` (#649): reuse its
  independent oracle, broker factory and version-bound request constructor.
- `tools/continuum-virtual-fabric/reuse-experiment.mjs` (#287): unchanged real
  CPU matrix kernel. No new cache, scheduler, workers or model.
- `tools/continuum-cognitive-pilots/ReasoningPilot.kt` (#648): existing bounded
  diagnostic/counterfactual controls; rerun unchanged when Kotlin is available.
- `tools/continuum-virtual-fabric/expert-mesh.test.mjs`: existing logical routing
  and denied-resource controls. Reservations are not neural experts.
- `experimental/omega/benchmarks/controlled-repair.mjs`: established frozen
  proposal/evaluator separation and truthful model-call counters inform this
  protocol; no duplicate repair provider or invented model inference.
- `LocalModelReasoningAdapter.execute` calls `runtime.streamThinking(prompt,callback)`;
  this interface exposes output events, not hidden-state recurrent depth.

The earlier whole-repository PR/branch catalogs were consulted as inventory,
not treated as proof of complete per-file review. Current open #715/#716 and
historical #287/#648/#649 overlap is recorded; no branches are closed.

## Frozen compute design

Three arms: direct existing kernel, existing independently verified reuse,
and lab-only adaptive admission to that same broker. Admission requires matrix
size >= 16 and a previous identical input in the same revision epoch; retain
at most 256 observed identities. First observations and cheap work execute
directly. This is a hand-written admission heuristic, not a learned ACT model.

Four workloads, 600 requests each, six paired fresh-process measurements,
alternating arm order, 64 warmup requests, 128 MiB Node heap ceiling:

| Workload | Split | Inputs | Matrix |
|---|---|---|---|
| repeated-expensive | DEV | 8 keys, seeds from 0 | 32 |
| mixed-cost-repeat | HOLDOUT | 16 expensive + 16 cheap keys, seeds from 5000 | 24 / 1 |
| unique-expensive | NEGATIVE HOLDOUT | 600 keys, seeds from 10000 | 24 |
| epoch-churn | NEGATIVE HOLDOUT | 8 keys; epoch changes every 8 requests | 24 |

Synthetic holdouts are authored here and excluded from parameter selection;
they are not blind external tasks. Kernel seed periodicity means distinct
identities need not represent mathematically independent matrices.

Every final result is compared to the independent oracle outside the timed
section, identically in all arms. Verification of cache misses remains INSIDE
measured runtime. Count all kernel/verifier calls, cache hits, bypasses and
admission decisions. Report wall and process CPU, RSS/heap before/after and
process high-water RSS, plus separate audit time. Fresh child-process launch
cost is outside the measured work section. No Joules/GPU/Fold7 claims.

Acceptance for *further real-workload shadow evaluation only*: zero mismatches;
>=10% median wall AND CPU improvement for adaptive vs direct on mixed holdout;
all six pairs improve both; <=5% median wall AND CPU overhead on each negative
holdout. Failure rejects promotion; thresholds are not retuned after results.
No benchmark outcome authorizes app activation.

## Frozen event probe

One scalar bounded leaky accumulator: residual = abs(observed - predicted),
probability surprise = -log(max(1e-9, probability of observed binary outcome)).
Accumulate residual with decay exp(-elapsedTicks/4); threshold 0.75 resets after
an advisory wake. Corrections always wake. Output is advisory only, never
memory admission, tool permission or model-quality confidence. No weights,
training, database, timers, live listeners or network.

Fixed streams test quiet calibrated events, unexpected failures, recovery,
corrections with zero residual, malformed input and nonmonotonic event time.
Compare always-wake, threshold-only and accumulator arms. Retain false wakes
and misses against explicit fixture labels; do not call labels human salience.
Input holdouts contain alternating low residuals and sparse delayed anomalies.

## Integration and rejection contract

Production authority stays with `CognitiveBudgetController` /
`CognitiveBudgetResearchRunner` (cognitive_budget) and the read-only
`ContinuumMemorySkillResearch.review` adapter (memory_skills). This lab never
ports those controllers into JavaScript. Future adapter consumes their
receipts; mismatched source/model/policy/memory revisions reject the trial.
No text result is execution evidence. Default remains disabled.

PORTIERT: lab sources only. VERIFIZIERT: pending exact-source host evidence.
GERÄTEBESTÄTIGT: NOT_RUN. Even if host gates pass, promotion requires independent
fresh tasks, real model/source receipts, same-model device comparison, thermal
and memory checks, privacy/permission regressions and explicit owner approval.
Removal of this directory/handoff reverts all new behavior; no app imports it.

Hidden-state ACT, learned sparse experts, learned predictive coding, SNNs,
real Gemma quality, memory adapter, Android app changes, training and energy:
NOT_RUN. An ordinary extra Gemma prompt is another model call, not recurrent
hidden-state depth.
