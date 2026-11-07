# SS-EFF-01: conditional preregistration, not a benchmark result

Status: `PROTOCOL_ONLY / BLOCKED_LOCAL_MODEL_UNAVAILABLE / FOLD7_NOT_RUN`.
No tasks or model outputs were run in this literature lane. This freezes selection
and reporting rules before implementation. It is **not** a sealed executable
benchmark yet: the independent test custodian must lock the model/runtime/source,
actual task hashes and approved host/device configuration before candidate access.
An exposed task is thereafter regression material, not a blind holdout.

## Question and smallest experiment

Does selective use of an independent challenger improve independently verified
completion at the **same total** inference/tool budget, compared with one existing
agent? Null: no useful increase; extra coordination may make performance worse.
Start a ten-task feasibility pilot, not a general intelligence benchmark: three
multi-hop/context tasks, three fixed-tool state tasks, four tiny authorized coding
repairs. Include sequential and decomposable tasks. Two repetitions per arm use
paired seeds where supported and randomized arm order. A later independent sample
is required for generalization claims; this pilot only estimates feasibility and
failure modes.

| Arm | Procedure | Ablation |
| --- | --- | --- |
| A0 | Existing single agent; same total budget may be spent revising its answer | Reference |
| A1 | Role labels in serial steps using the same shared inference runtime and isolated contexts | Identity/role change without independent challenge |
| A2 | Builder plus challenger; challenger initially sees task/criteria, not builder conclusion | Independence versus A1 |
| A3 | A2 plus at most two authorized historical evidence records | Recall versus A2 |
| A4 | Rule-first selective activation: one worker, bounded challenge only on predefined verification failure/uncertainty | Activation versus always-on A2/A3 |

One loaded runtime, at most one generation at a time, no hidden cloud answer or
externally written patch. Immutable weight reuse is not shared conversation/KV
state. All input preparation, packing, role instructions, reports and retries are
charged to the arm; no budget reset when delegating or resuming.

Initial pilot ceiling per task/arm: four model invocations; 8,192 aggregate input
tokens; 2,048 aggregate generated tokens (including visible reasoning if emitted);
eight fixed-profile tool invocations; 180 seconds wall time; 256 KiB aggregate
captured output. These are hard ceilings, not grants. Existing canonical execution
policy may be stricter and wins. Before locking the run, document model context
limits and whether it exposes all compute-relevant tokens. Unobservable hidden
reasoning means **not token-comparable**, not an estimated pass. Input and output
token totals must be reported separately because their runtime costs differ.

Memory/CPU/device thermal limits are configured from actual admission capabilities
and frozen before any task outcome is inspected. A change requires a new protocol
version and complete rerun, never a silent per-arm exception. Identical model file,
runtime build, quantization, tokenizer, task files, permissions and verifier for all
arms. Serialize timed trials without other builds/inference/render jobs; record
warm/cold startup separately. No persistent retrieval cache reuse between arms.

## Evidence and integrity

Success requires final-state or deterministic test acceptance from a separate
verifier. Coding additionally requires initial RED, candidate GREEN and fresh
independent checks on the bound source/patch. Model text saying “passed” is a false
success if not backed by those receipts. Empty/unavailable outputs are abstentions
or infrastructure failures, never excluded after looking at the score.

A3 retrieves only records currently allowed by the canonical CCC1 grant/source
rules. The existing same-SHA recall restriction must not be bypassed for this test.
A changed-source experience stays historical and cannot be presented as a tested
patch for new code. If the candidate cannot legally retrieve relevant examples,
report that arm unavailable and record the missing capability. Require restart
before recall, plus separate irrelevant/expired/contradictory/revoked-memory
negative controls; any forbidden recall fails the safety gate, even with full
storage. Reflections cannot manufacture verified capabilities or owner facts.

Freeze challenger tests with hashes before Builder gets task access; withhold
expected outputs and independent checks. After first disclosure use new cases for
further generalization. Keep all unsuccessful trials, raw commands, source SHA,
receipt IDs and exact budgets. Do not call the selected optimal arm an independent
holdout result from the same pilot.

## Measures, decision and stop rule

Report per arm: verified successes, wrong answers, correct abstentions, false
success claims, regressions by task, input/output tokens, model/tool calls, complete
wall time including coordination, p50/p95 with raw small-sample values, peak RSS/PSS
where supported, persisted bytes and mailbox traffic. State host/emulator/Fold7
separately. Energy is `NOT_MEASURED` unless a real measurement method exists;
thermal status is not joules. Include fixed orchestration overhead with inference
turned off as a separately labelled software experiment.

Retain for a larger independent trial only if an arm has at least two additional
verified successes in the ten-task pilot, no false success or safety regression,
and no budget increase. This is a triage rule, not statistical proof. Otherwise
retain A0; record negative/ambiguous results. Stop the affected run immediately on
cross-bot data access, granted-right expansion, unknown-effect replay, stale
post-cancel activation, budget overrun or broken isolation. Do not “fix” such
failures by disabling the gate or filtering trials. Reproducible infrastructure
failures are reported separately and must be repaired before comparing accuracy.

## Deferred neuro-inspired variants

Do not add CfC/SNN merely to route these ten tasks. First record safe, non-personal,
timestamped queue/resource traces. A future small resource-prediction experiment
must compare a fixed rule, EWMA and ordinary small classifier before CfC, using
forward-time train/holdout splits and missing/irregular sample controls. Predictor
output is advisory; exact limits remain authoritative. Retain only an end-to-end
latency/energy benefit under equivalent accuracy and admission safety. Fewer
simulated spikes or parameters alone prove no hardware energy reduction. SNN
feasibility is deferred until a concrete signal task and measurable energy setup
exist; no biometric or biological data is needed.
