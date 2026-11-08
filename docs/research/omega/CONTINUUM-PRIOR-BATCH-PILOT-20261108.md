# H2: prior-preserving diagnostic planning

Status: **RESEARCH_SHADOW**, no live callers or authority changes.
Branch `research/continuum-prior-batch-pilot-20261108`, stacked on H1
`f93b661ff2652b0783ab6224810edd85b8c368ff` / #647.
The experimental baseline remains Code50
`473f79727e4b52509cc7d1af71e59b80ab9b6be5`, not the H1 policy modification.

## Bottleneck and new trial

#635 established admission-order bias but did not test task-solving consequences.
The current workspace normalizes after each single admission, mixing normalized
old mass with raw new priors. Four equal priors become .125/.125/.25/.5.
The strongest locally reproduced opportunity is therefore **prior-preserving
probe planning under a fixed budget**, rather than more models or parameters.

New method: atomically admit one finite prior set into an empty existing
`HypothesisWorkspace`, normalize once, retain all existing observation/probe
logic. This adds an explicit API, not a second planner or a change to the
legacy sequential-admission contract. Invalid/duplicate/over-capacity batches
must leave the workspace unchanged. Copies must isolate caller-owned mutation.

## Frozen design (before candidate implementation)

`tools/continuum-cognitive-pilots/ReasoningPilot.kt` contains fixed datasets:
four equal DEV priors (24 permutations); four unequal, renamed HOLDOUT priors
(24 permutations); six equal HOLDOUT priors (720 permutations). Oracle:
each original prior divided by their double-precision sum, tolerance 1e-6.
These are synthetic, prospectively frozen, author-created tests; not a blind
external corpus or natural-language comprehension benchmark.

Four-cause worlds have two bit probes and four singleton probes, all cost 1,
thermal cost 0. For every truth and every insertion order, allow **two** actual
fixture observations. A separate set-intersection verifier may report a
diagnosis only if exactly one candidate satisfies every observation. An
ambiguous posterior is ABSTAIN, not the highest-weight guess. This is a
deterministic simulated diagnostic task, not a real device tool invocation.

Predeclared success: 96/96 verified diagnoses on each four-cause set, no false
diagnosis, all 768 prior permutations correct, one first-probe choice per set.
Negative controls: nondiscriminating probes; full 2^3 combination of diagnosis,
permission and independent receipt in the existing three-hop JTMS graph;
revocation and prerequisite withdrawal; counterfactual overlay does not mutate
reality. Existing tests must remain green. Budget exceeds information capacity
for six causes: that set tests priors only, not a fabricated two-probe solution.

Ablation: same candidate source with legacy single `add` admission. This must
retain the original defect, attributing benefit to batching rather than the
unchanged probe/observation algorithm. No model confidence or simulated
receipt may be promoted to actual execution evidence.

## Original-source RED

```sh
bash scripts/continuum-reasoning-pilot.sh /tmp/h2-red sequential \
  473f79727e4b52509cc7d1af71e59b80ab9b6be5
```

Actual result: existing JUnit **11 PASS**, then experimental assertion
**FAIL**, exit 1. Prior invariance **0/768**. Verified diagnoses **48/96** on
DEV and **48/96** on unequal-prior HOLDOUT; remaining cases abstain, no wrong
diagnosis. First probe has four order-dependent variants in both sets.
Controls **26 PASS**. First run: 88.520 ms wall / 72.153 ms thread CPU;
this is not a paired timing conclusion.

No evidence justifies expanding trainable parameters or integrating this
planner into the chat router. Next gate after a positive trial: novel causal
graphs with misleading/missing observations and held-out cost distributions,
then actual authorized local diagnostic tools. No broad intelligence claim.
