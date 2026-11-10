# BRAIN-LAB-1 measured result: reject promotion

Frozen protocol commit: `e2ddc534a3735085e60a44a8a922a71a5462d454`.
Measured source: `ff01682a952792cad83b09468e12d49252819d04`.
Working tree was clean before and after the compute measurements. Input source
hashes, all 72 process samples and event traces are in `evidence/comparison.json`.
No app caller, model call, training, memory write or network experiment ran.

## Compute admission

**43,200 / 43,200 final results** match the existing independent matrix oracle.
Every workload has 600 outputs x 3 arms x 6 fresh-process repetitions.
Verification on reuse misses is inside measured time; final common audits are
outside and separately reported. Process memory includes runtime/warmup/audit.
Six repetitions are not six independent task populations. This shared host
was not thermally isolated; timings are host diagnostics, not Fold7 evidence.

| Workload | Direct wall / CPU ms | Existing reuse wall / CPU ms | Adaptive wall / CPU ms | Frozen gate |
|---|---:|---:|---:|---|
| Repeated expensive DEV | 146.717 / 149.306 | 10.834 / 22.338 | 13.577 / 22.831 | PASS, 6/6 paired wins |
| Mixed-cost HOLDOUT | 42.157 / 49.391 | 11.340 / 20.103 | 12.999 / 22.689 | PASS, 6/6 paired wins |
| Unique NEGATIVE HOLDOUT | 81.660 / 87.701 | 172.869 / 189.108 | 78.609 / 88.660 | PASS, CPU +1.1% |
| Epoch-churn NEGATIVE HOLDOUT | 146.848 / 92.737 | 176.539 / 185.639 | 102.282 / 101.476 | **FAIL, CPU +9.4%** |

Values are medians, not per-request latencies. Churn's wall-time improvement
and CPU regression illustrate why the protocol requires both metrics. The
adaptive arm does not beat the existing reuse arm on either repeating set.
It avoids unnecessary cache verification on unique and rapidly changing data,
but its own identity bookkeeping still costs time.

| Workload | Direct kernel/verifier calls | Reuse kernel/verifier calls | Adaptive kernel/verifier calls | Adaptive hits | Max RSS direct/reuse/adaptive KiB |
|---|---:|---:|---:|---:|---:|
| Repeated expensive | 600 / 0 | 8 / 8 | 16 / 8 | 584 | 44612 / 44800 / 45064 |
| Mixed cost | 600 / 0 | 32 / 32 | 332 / 16 | 268 | 44416 / 45952 / 45352 |
| Unique | 600 / 0 | 600 / 600 | 600 / 0 | 0 | 44760 / 50492 / 49348 |
| Epoch churn | 600 / 0 | 600 / 600 | 600 / 0 | 0 | 45312 / 50952 / 49196 |

Counts are per 600-request process trial; RSS is the maximum process high-water
mark across six launches. No RAM delta is described as allocation count.
Energy/Joules, GPU use, neural compute and on-device thermals are **NOT_MEASURED**.
The frozen overall decision is **REJECT_PROMOTION**, despite the favorable
mixed workload. No thresholds were changed after observing this result.

## Event salience, surprise and residual

38 fixed synthetic events, each replayed through three arms. Expected wake
labels are removed before policy invocation. A correction with zero residual
still wakes in both selective arms. There are no missed labelled anomalies.

| Arm | Wakes / 38 | False wakes | Missed labelled wakes |
|---|---:|---:|---:|
| Always wake | 38 | 35 | 0 |
| Single-event residual threshold | 3 | 0 | 0 |
| Leaky accumulator | 5 | 2 | 0 |

The two accumulator false wakes arise on the 20-event alternating low-residual
holdout. Thus accumulation has no demonstrated benefit over the simple
threshold on this suite; **do not promote the accumulator**. These labels are
same-author fixture expectations, not human relevance or general intelligence.
The scalar sidecontroller is an advisory signal filter only; no learned SNN,
predictive-coding network or correctness probability is claimed.

## Existing sparse routing and counterfactual replication

All **69 Node tests PASS**, zero failures/skips: 14 new lab tests and 55 existing
fabric/reuse tests. The existing expert-mesh tests are included; they validate
logical role routing, resource reservation, revocation and queueing without
resources. They do not execute learned experts or establish a speed gain.

The unchanged existing Kotlin `ReasoningPilot` and its production-source
helpers were compiled with Kotlin 2.4.0 / JDK 21. `reasoning-batch` has **17/17
JUnit PASS**, plus 768/768 prior permutations and 96/96 verified diagnoses in
each of DEV-equal4 and HOLDOUT-unequal4. All 26 dependency/revocation/overlay
controls PASS, including counterfactual changes not mutating reality.

The sequential admission ablation has **11/11 overlapping JUnit PASS** but its
pre-existing experiment correctly exits 1: 0/768 prior permutations, 48/96
diagnoses in each four-cause set, 48 abstentions in each, four first-probe
variants. The failure is preserved in `evidence/reasoning-sequential/`.
It is not hidden as an infrastructure failure or counted as successful planning.
No new planner was implemented; these are replications of #648, not new gains.
Single rerun timing: batch 85.632 ms wall / 79.544 ms thread CPU; sequential
102.787 / 92.361 ms. One timing pair does not establish a performance effect.

Six-cause scenarios only test priors, not a fabricated two-probe diagnosis.
No natural-language causal extraction or real tool execution was tested.

## Fast/slow and memory boundary

The canonical fast/normal/deep runner is owned by `cognitive_budget`, using
`CognitiveBudgetController.decide` and `CognitiveBudgetResearchRunner.run`.
This lab's cache admission heuristic does not duplicate that controller.
A direct runner-to-lab receipt adapter is **NOT_RUN**; any separate runner
results belong to its own source SHA and evidence, never this test count.

Memory is owned by `memory_skills`, via read-only
`ContinuumMemorySkillResearch.review`. No memory consolidation, fact promotion,
model weights or executable skill storage is implemented here. An integration
must bind source/model/policy/memory revisions and retain independent evidence.

Hidden-state recurrence, learned halting, learned sparse MoE, real Gemma
quality, learned predictive coding, trained SNNs, Android/Gradle/Fold7 and energy
remain **NOT_RUN**. Primary papers and the exact architecture boundary are in
`PRIMARY-SOURCES.md`; ordinary prompt repetition cannot modify model depth.

## Reproduce and next decision

On the measured commit, run the following from the repository root with an
empty external directory. `kotlinc` and `CONTINUUM_JUNIT_JARS` are needed only
for the unchanged Kotlin replication; no dependency installation is performed.

```sh
bash experimental/brain-lab-20261110/reproduce.sh /tmp/brain-lab-fresh
```

`reproduce.sh` was added after the measured source; its individual constituent
commands were run, and shell syntax checked. The wrapper itself was not run
as one combined command. A new run must commit any local source edits first.

Next experiment needs a new preregistration, not threshold adjustment on this
holdout: actual read-only repository-analysis stages, fresh unseen revision
patterns, and a cheaper identity admission path with no >5% negative overhead.
Only then consider the canonical Kotlin ComputeFabric path in disabled shadow.
Independent model/device/permission/thermal evidence and owner approval remain
mandatory before activation. Remove this lab directory and its handoff to
roll back; there are no application imports.
