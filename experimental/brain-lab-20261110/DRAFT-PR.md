# research: measure bounded compute admission and retain failed promotion gates

Target: `cinema/fluid-light-stage-20261110`. Draft only; no main/release merge.

Repeated deterministic work can benefit from the existing verified-reuse broker,
but cache admission itself adds overhead. This isolated lab reuses #649's broker
and independent oracle, compares direct/reuse/adaptive arms under a frozen
protocol, and keeps unfavorable unique/churn cases. A small event-salience
probe compares a leaky accumulator with simple threshold and always-wake arms.
Existing #648 counterfactual/diagnostic and #287 logical-routing checks are reused.

Measured source `ff01682a952792cad83b09468e12d49252819d04`:
- 43,200 exact results; 69/69 Node tests.
- Existing Kotlin batch: 17/17 JUnit, 768/768 prior permutations, two 96/96
  diagnosis sets, 26 dependency/revocation/counterfactual controls.
- Sequential baseline retains expected RED (0/768 prior invariance; 48/96
  diagnoses per four-cause set), with raw failure output.
- Adaptive mixed holdout CPU 22.689 ms vs direct 49.391 ms; epoch-churn CPU
  +9.4% fails the preregistered +5% cap. Overall **REJECT_PROMOTION**.
- Accumulator has 2 false wakes/20 low-residual holdout events; threshold has 0.

No app/default/controller/memory changes, model inference, learned MoE/SNN,
hidden-state Gemma recurrence, training, cloud execution or energy claims.
Counterfactual results are synthetic host replications, not device evidence.
Raw samples include time, process memory, calls and exact source hashes.

Integration owner: Cinema root integrator. Keep the lab and negative decision
as auditable research; no production activation. Fast/slow authority stays in
CognitiveBudgetController/ResearchRunner, memory in ContinuumMemorySkillResearch.
Real workload/model/device/permission/thermal gates and owner approval must
precede promotion. See PROTOCOL.md, RESULTS.md and BRAIN-LAB-HANDOFF-20261110.md.
