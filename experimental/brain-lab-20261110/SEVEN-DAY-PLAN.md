# Seven workdays: evidence before promotion

Planning only; no scheduled automation, device booking or completed experiment
is implied. Integration owner: Cinema root integrator. Target remains
`cinema/fluid-light-stage-20261110`; no main/release/signing changes.

The existing [context/voice seven-day protocol in #716](https://github.com/Ullmann27/device-ai/blob/00cf15d925d2c114d8ce57ee23dac473a89e6e0f/docs/research/omega/CINEMA-CONTEXT-HANDOFF-20261110.md)
already covers dialogue bindings, approval and voice. Reuse its cases and
owners; this companion adds the measured compute/event work and platform
presentation gates. It does not introduce another controller or memory store.

Starting evidence: [RESULTS.md](RESULTS.md). There are 43,200 exact synthetic
outputs and 69 passing Node checks, but cache admission fails the churn CPU
overhead cap (+9.4% versus +5%). The accumulator adds two false wakes versus
zero for threshold-only. Both candidates remain **REJECT_PROMOTION**; these
known cases are regressions, never fresh holdouts.

| Day | Bounded work and baseline | Required evidence / stop gate |
|---|---|---|
| 1 | Freeze source/model/policy/memory revisions and protocol; retain all current negative samples. For each of four families (compute reuse, fast/slow, evidence memory, causal/event signals), define 20 development cases and obtain 20 separately authored holdouts. | Freeze 80 development and 80 holdout IDs/hashes before tuning. Record expected result, abstention, permission and revision rules. If independent authorship or a real model is unavailable, mark that lane NOT_RUN; do not relabel current fixtures as independent. |
| 2 | Compare direct work, existing verified reuse and one newly preregistered admission candidate on bounded read-only repository stages. Keep cheap, unique and revision-churn controls. | Six alternating fresh-process pairs per workload; count kernel/verifier/identity work and audit every result. Zero mismatches; >=10% median wall AND CPU savings plus 6/6 paired wins on the benefit holdout; <=5% median wall AND CPU overhead on each negative workload. Any miss rejects promotion. No retuning on holdout. |
| 3 | Use the existing CognitiveBudgetController/ResearchRunner from #717 for fixed-budget versus adaptive FAST/NORMAL/DEEP. Keep TOOL/MEMORY/RESEARCH in their existing zero-local-call handoff. | Same task/model/context and ceiling; publish attempted/completed calls, stop reason, bytes, latency and per-case answer differences. Include cycles, stable wrong answers, timeout, cancellation and revocation. Digest stability is not correctness; lower calls alone cannot pass a quality gate. |
| 4 | Run the existing read-only memory review with no-memory, raw episodic, verified episodic and procedural-candidate arms; reuse the context protocol's correction/revocation cases. | Report clean and poisoned inputs separately, correct/wrong/abstained counts and every changed answer. Zero cross-owner/revision leaks, zero promotion of unverified facts and zero loss of corrections. Retain original evidence; no new store, executable skills or training. |
| 5 | Reuse HypothesisWorkspace/CounterfactualWorldOverlay and existing independent consistency checks; replay always-wake, threshold and accumulator event baselines. Add missing/wrong observations and delayed or repeated low residuals. | Unchanged reality outside the overlay; zero unsupported diagnoses/actions. Count probes, abstentions, false wakes and missed labelled anomalies separately. An accumulator must improve a preregistered target without increasing false wakes or missing corrections; otherwise keep the simpler threshold. |
| 6 | Replay the same real event identities and scenes through the existing Android quality path. Run three bounded ten-turn Fold7 sessions only when authorized hardware/model access exists; exercise idle, inference, speech, cancellation, thermal/power and resume. | Record frame-time distributions, process memory, actual thermal states, model latency and failures against the same-model baseline. No late-turn speech/work, no permission bypass, no sustained memory growth, no >5% p95 model-latency regression. Missing hardware/sensors => NOT_RUN, never inferred Joules. Desktop runtime remains NOT_IMPLEMENTED. |
| 7 | Lock the candidate; execute each fresh holdout once, exact-source relevant Node/JVM/Android checks and owner-visible replay. Publish all samples and rejected hypotheses. | Zero new safety/evidence regressions; every day-specific benefit/overhead gate must pass. Require equal-or-better independently scored correctness and coverage at the agreed budget. Any failure retains disabled SHADOW. Passing host tests only permits the next bounded evaluation; live activation still needs actual device evidence and owner approval. |

The 20-per-family counts define a small diagnostic trial, not statistical proof
of general intelligence. Keep per-case results and family denominators; do not
pool repeated timings with independent tasks. Budget increases, new hardware,
cloud execution, model training or paid services need their own scoped decision.
The [quality map](QUALITY-PROFILES.md) specifies the existing presentation policy
and the unimplemented desktop boundary. No date here commits a background job.
