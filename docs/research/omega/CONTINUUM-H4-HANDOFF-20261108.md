# H4 robust transfer: preregistration and resumable handoff

Issue #652; continuation, not a restart. Verified starting H3 HEAD:
`8688f855e967c72b345debce3cd6c2c90fae5806`, #649 -> #648 -> #647 -> Code50.
Own branch: `research/continuum-h4-robust-transfer-20261108`.
Other agents' branches, main, graphics, release/signing, model weights and
owner data remain untouched. Existing AGENTS/experiment contracts still apply.

## H4-A preregistration (before implementation)

**Hypothesis:** under wrong priors and at most one incorrect binary observation,
planning for a uniquely checkable evidence certificate will yield more correct
conditional diagnoses than greedy posterior information gain. Use the existing
`HypothesisWorkspace`, not another planner service or memory database.

Proposed method: bounded lookahead over remaining probe subsets, maximizing the
fraction of hypothetical response transcripts that leave one possible cause
under the declared error bound, divided by total simulated cost. Replan after
each observation; missing values contribute no evidence. Priors cannot exclude
possibilities. Independent set-consistency verification determines whether a
diagnosis is admissible, never a posterior maximum.

This combines bounded experimental design/version spaces with error-tolerant
coding, not a claimed new theorem. With only three binary probes and one possible
error, unrestricted diagnosis of many causes is information-theoretically
impossible. ABSTAIN is an expected result, not a failure to hide.

**Data origin:** new procedural causal worlds authored in this session;
prospective self-created DEV/TEST, **not independently authored blind data**.
No H1/H2 fixtures reused. Freeze generator and seeds before candidate code.
DEV seed **41073**, 64 worlds. TEST seed **92861**, 192 worlds; do not generate
or inspect TEST results until candidate and verifier are committed.

Each world has 2..6 candidate root-cause configurations over three binary root
faults (including compound causes), nine measured conjunction/exception
signals, redundant sensors, asymmetric costs 1..4, and sometimes observationally
indistinguishable causes. Truth and answer labels are withheld from strategies.
Priors are deliberately concentrated on a wrong cause. Four balanced strata:
clean, first observation missing, second observation inverted, and both.
At most one incorrect non-missing observation per case. Sensor order and
identities are seeded; no test-specific condition in candidate code.

**Four arms, identical worlds and limits:** legacy sequential admission,
H2 batch admission, H4 certificate planning, nonadaptive seeded catalog order.
All have at most **three simulated probes**, total simulated cost **9**.
Use the same independent robust certificate verifier for accepted outcomes;
report raw greedy proposals separately. A unique cause is conditional on the
explicit model and error bound, not a real execution receipt or owner fact.
An oracle with the true hidden cause scores correctness after execution.

**Metrics:** accepted correct/wrong diagnoses; ambiguous/empty-set ABSTAIN;
raw unsupported/wrong proposals; avoidable abstention relative to an exhaustive
three-probe oracle; version-space information gained per probe and per cost;
actual thread CPU, wall time and process high-water RSS. Simulated costs are
not physical time or joules.

**Acceptance frozen now:** on TEST, H4 has at least **8 additional correct
diagnoses out of 192** versus *each* other arm, zero accepted wrong diagnoses,
zero accepted non-singleton certificates, and no budget overrun. Require
paired one-sided exact discordance test p < .05 against each arm; no multiple
world permutations masquerading as independent samples. All negative and
regression tests pass. Otherwise retain FAIL/negative result; do not relabel
the hypothesis or tune TEST. Only a passed H4-A gate permits H4-B.

**Physical budget:** one JVM at a time for measurements, heap 256 MiB;
maximum 12 probes / 12 hypotheses accepted by the new method, lookahead <=3,
120-second subprocess timeout, measured per-world p95 <=250 ms. Four paired
measurement rounds, rotating arm order, fixed warmup excluded from samples.
All arms use the same host/resources. Compiler/JIT overhead is not a probe.
Energy/Joule, Android/Gradle/Robolectric, Gemma and Fold7: NOT_RUN.

**Controls/ablations:** H4 planner with lookahead restricted to one; no posterior
truth shortcut; duplicates/unknown probe receipts rejected; missing observations
neutral; retraced/retracted evidence rebuilt; irrelevant hypotheses and priors
must not manufacture a certificate. An explicit two-error counterexample
documents failure outside the error model, not universal noise immunity.
No network, tool dispatch, persistent facts or learned parameters.

## H4-B / H4-C gate

After H4-A acceptance, preregister new multi-turn German/de-AT stories and
separate solution keys before code changes. Score semantic answer/ABSTAIN,
not H1 routing. Identify extraction/reference/evidence/reasoning/output errors
separately. If a local Gemma runtime is unavailable, state that explicitly:
host evidence-logic tests cannot establish free-language model understanding.
Reuse existing temporal/causal/contradiction/counterfactual components; no
parallel memory. Only demonstrated deficits justify a minimal shadow extension.

## Initial checkpoint (historical)

At this checkpoint: protocol only, experiments NOT_RUN. Next: implement the
frozen world generator, original-source DEV baseline and independent verifier;
preserve RED before candidate implementation. Rollback: do not merge this Draft;
there is no schema/weight migration or installed-product activation.

## DEV checkpoints (TEST still ungenerated)

Draft #655. Generator/oracle/RED frozen at
`15478ec930443763ad90ba0ef56eb1c6b883d492`. New 64-world DEV:
sequential 14, batch 13, nonadaptive 11 correct certificates; zero accepted
wrong/unsupported diagnoses. Exhaustive hidden-truth oracle finds 28 worlds
solvable under the resource budget. The new three-probe regression is genuinely
RED: the unchanged greedy policy leaves `[a,c]` instead of `[a]`.

Candidate v1 (uniform transcript coverage / cost) is **rejected on DEV**:
16/64 correct, 12 avoidable abstentions, and the same targeted regression fails.
It prefers a cheap mixed plan whose certificate coverage is high only for
some error positions. A one-step ablation solves 0/64 and takes no probes.
These results are retained; they are not reported as TEST gains.

Pre-TEST algorithm revision v2: prioritize the fraction of still-compatible
causes that become singleton-certifiable for **every** future response pattern
consistent with the remaining error budget, per cost. Use the original
transcript-coverage score only as a secondary tie-break. This is a minimax
error-placement criterion, not calibrated posterior confidence.
The original dataset, seeds, oracle, physical budgets, TEST success threshold
and significance criterion remain unchanged. Revision justified only by DEV.

Research tool check: web search located Nowak's *Noisy Generalized Binary
Search* (NeurIPS 2009, abstract identifier
`556f391937dfd4398cbac35e050a2177`) and bounded-lie search literature.
Direct primary-page retrieval failed DNS; no full-paper review is claimed.
The search assistant's numerical explanation was inconsistent and is **not**
used as evidence. Our basic bound is independently checkable: with three
fixed binary probes, each cause needs four distinct one-error transcripts;
four disjoint such sets cannot fit into eight transcripts. The experiment
does not assume that every four-cause world can always be solved.

## Closure checkpoint: candidate v2 freeze

The owner requested closure at the remaining usage limit. No H4-B/H4-C or
new research project will start in this session. Finish the begun H4-A
comparison, preserve negative as well as positive results, then hand over.

V2 passes the original RED and all 14 workspace/prior/robust-probe JVM tests.
DEV: **17/64** correct versus sequential 14, batch 13, nonadaptive 11;
zero accepted wrong/unsupported decisions, 160 probes, simulated cost 279,
11 oracle-avoidable abstentions. This is DEV, not confirmation of the gate.
Six independent-auditor controls cover fabricated observations, duplicate
receipts, foreign source IDs, stale feasible sets, instruction text and false
claims. These are synthetic data-boundary tests, not Android privacy-gate
or live tool-authorization acceptance.

New `h4-measure.py` runs four rotating paired rounds, enforces the frozen TEST
size/seed, uses a 256 MiB JVM and 120-second subprocess timeout, checks repeated
decision outputs, and reports paired exact discordance tests, thread CPU,
wall time and process high-water RSS. It requires a clean committed source.
Reader handles are closed explicitly; optimized Python cannot disable the
auditor's assertions silently. The harness stops when a policy returns no
probe instead of repeating an identical null decision.

Integration boundary: `selectCertificateProbe` is implemented directly in
the existing `HypothesisWorkspace` and exercised through the H4 harness.
There is **no new memory/planner service and no production caller activation**.
SOL/Astra must not connect this bounded synthetic policy to real observations,
tools or permanent facts without the H4-B/C evidence and existing ToolGate/
privacy/provenance checks. No external agent's execution is claimed.
