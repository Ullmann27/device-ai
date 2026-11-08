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

## Checkpoint

At this checkpoint: protocol only, experiments NOT_RUN. Next: implement the
frozen world generator, original-source DEV baseline and independent verifier;
preserve RED before candidate implementation. Rollback: do not merge this Draft;
there is no schema/weight migration or installed-product activation.
