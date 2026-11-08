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

## Historical DEV checkpoints (before opening TEST)

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

## Final H4-A result: FAIL, preserved without TEST tuning

**Measured source:** `d93eaeb6ea8fc3e2a09a040b16edbdc3eed38171`.
Candidate and verifier were committed, pushed and remote-readback verified
before generating TEST. The measurement runner records a clean working tree.
TEST seed 92861, 192 prospective self-authored worlds; JSON SHA-256:
`4ead2d7d50ab6d779504fb6b394035a9d7190c4de1aecab05315352d8af39397`.
Source dependencies remain #647 -> #648 -> #649 -> **Draft #655**.

| Arm | Correct /192 | Wrong / unsupported | Abstain | Oracle-avoidable abstain | Probes | Simulated cost |
|---|---:|---:|---:|---:|---:|---:|
| Original sequential | 35 | 0 / 0 | 157 | 39 | 563 | 959 |
| H2 batch | 34 | 0 / 0 | 158 | 40 | 563 | 929 |
| H4 v2 certificate | 44 | 0 / 0 | 148 | 30 | 481 | 806 |
| Nonadaptive catalog | 26 | 0 / 0 | 166 | 48 | 572 | 1374 |
| One-step ablation | 0 | 0 / 0 | 192 | 74 | 0 | 0 |

All arms correctly abstain on the 118 worlds that the truth-aware exhaustive
oracle also cannot certify under the budget. The oracle's 74 solvable worlds
are an upper bound, not an online-achievable guarantee.

H4 versus sequential: **18 wins, 9 losses, +9 correct, p=0.0610390604**.
Versus batch: 18 wins, 8 losses, +10, p=0.0377593488.
Versus nonadaptive: 24 wins, 6 losses, +18, p=0.0007154532.
The numerical +8 threshold passes against every baseline, but the frozen
requirement p<0.05 against **each** baseline fails. Therefore overall **FAIL**:
the full superiority hypothesis is not confirmed. No changed threshold,
TEST-based tuning, selective rerun or claim of established generalization.

Four rotating rounds, identical decision transcripts each round:

| Arm | Median thread CPU ms /192 | Median decision wall ms /192 | Maximum process RSS KiB | Worst round p95 world wall ms |
|---|---:|---:|---:|---:|
| Sequential | 20.436 | 22.367 | 73152 | 0.346 |
| Batch | 20.265 | 25.491 | 75696 | 0.391 |
| H4 certificate | 95.957 | 104.549 | 85548 | 1.119 |
| Nonadaptive | 7.541 | 7.541 | 67696 | 0.083 |
| One-step ablation | 12.217 | 12.543 | 66736 | 0.141 |

The candidate uses **approximately 4.7x sequential CPU** despite fewer
simulated probes. Information gain/probe also declines from 0.2002 to 0.1776;
gain/simulated cost declines from 0.1175 to 0.1060. This is not a compute-
efficiency breakthrough. Per-world p95 remains below the frozen 250 ms cap;
no observation or simulated-cost budget violation was accepted.
Timing excludes input parsing, initial prior admission and fixed warmup;
RSS includes the process. Host: Linux x64, Kotlin 2.2.21, JDK 27 executing
JVM 17 bytecode, JUnit 4.13.2. CPU is **not energy**.

The trace's `raw` field is the unused legacy posterior argmax, not an H4
accepted answer. Its wrong counts are 118/139/164/158/192 for the table's
five arms; retaining these exposes misleading wrong-prior confidence.
The common independent certificate check, not that argmax, admits answers.
The one-step ablation takes no probe because no single observation can
certify a cause with one possible error. It establishes a planning-horizon
dependency, not a comparison with an optimized myopic exploration policy.

### Executed checks and reproduction

**PASS:** H4 14 JVM tests, H1 20 JVM tests, H2/JTMS/counterfactual 17 JVM tests
and 26 reasoning controls; suites overlap and are not 51 unique tests.
Six independent auditor tests pass, including with ResourceWarnings treated
as errors. The actual original/v1 RED logs remain archived. TEST strategy
outputs are identical across all four rounds. **FAIL:** frozen H4-A gate,
with command exit code 1 as intended. No runtime/test crash is being hidden.

Use Kotlin 2.2.21 on PATH and `CONTINUUM_JUNIT_JARS` pointing to JUnit 4.13.2
and Hamcrest 1.3. On a clean checkout of the measured SHA, with an external
output directory:

```sh
python3 -B -W error::ResourceWarning tools/continuum-cognitive-pilots/h4-audit-test.py
bash scripts/continuum-h4-check.sh /tmp/h4-check
bash scripts/continuum-context-pilot.sh /tmp/h4-context
bash scripts/continuum-reasoning-pilot.sh /tmp/h4-reasoning batch
python3 -B tools/continuum-cognitive-pilots/h4-worlds.py test /tmp/h4-test
python3 -B tools/continuum-cognitive-pilots/h4-measure.py /tmp/h4-check/h4.jar /tmp/h4-test /tmp/h4-measured
```

Last command is expected to return **1 (gate FAIL)**, after writing the
complete report. Original actual outputs, all 20 traces, frozen datasets,
jar/dataset hashes and per-world paired decisions are in
`docs/evidence/h4-20261108/`; TEST summary is `test/comparison.json`.
The preregistration SHA is `2b500050d1f9ebf36808927305431c071634ab54`,
generator/oracle/initial RED SHA `15478ec930443763ad90ba0ef56eb1c6b883d492`,
rejected v1 SHA `51d7639113f722853574ec5b468ab388d383d81c`.

### SOL/Astra integration handoff and stopping point

**Current outcome: secured research, NOT production acceptance.**
Core changes are only `HypothesisWorkspace.ProbeObservation` and
`selectCertificateProbe`; existing `add`, `admitPriors`, `observe`,
`selectNextProbe` and their production callers are unchanged.
The other additions are the H4 harness/generator/auditor/measurement runner,
tests, evidence and this handoff. No new truth database, dispatcher, weights,
external action or persistent owner fact was introduced.

SOL: review #655 against its frozen #649 dependency; do **not** promote the
failed H4 strategy or cherry-pick the stacked branch without checking ancestry
and current SHAs. Astra: the method lives in the existing cognitive workspace,
but no real conversation-to-certificate/ToolGate path has been validated.
The partial component must not be presented as a completed brain connection.
No other agent branch was changed; handoff publication does not start an agent.

**NOT_RUN / intentionally deferred:** H4-B de-AT multi-turn semantics; H4-C
shadow integration; full Android/Gradle/Robolectric tests; actual Gemma;
Fold7/device, long conversations, thermal/energy measurements; product privacy
and live stale-revision/ToolGate checks. No main merge, signing, APK release,
training or production activation. The owner closure order and failed H4-A
gate both prevent beginning H4-B now.

Limitations: the model's structural predictions are assumed correct, only
priors/observations are corrupted. Two errors can defeat a one-error
certificate (explicit counterexample). The three-root synthetic family does
not demonstrate understanding of unknown human situations or natural language.
The planner optimizes complete future binary responses, not future missing
values. Observations carry probe IDs, not production-grade authenticated
provenance/revision receipts.

**Next falsifiable experiment, not started:** on a newly preregistered,
independently authored family, compare this fixed v2 against a bounded
missing-response-aware planner at the same three-probe/cost-9 limits.
Require an independently verified correctness gain against sequential and
batch baselines plus a separately frozen CPU overhead ceiling before opening
new TEST. This TEST is consumed and must not become a fresh holdout.
Rollback is simply not activating/merging this research; no migration is needed.

## H4-B1 preregistration (owner continuation order, 2026-11-08 19:29 UTC)

The owner lifted the closure mode ("Weiter … konzentriere dich auf die
Erweiterung Stufe super Intelligenz"). Superintelligence remains the long-term
research direction, not a property claimed here. H4-A stays **FAIL**; H4-B1
starts as a *separately* preregistered experiment under owner direction, not
as a consequence of a passed H4-A gate. Same branch and Draft #655.

**Scope (host-only):** the deterministic sub-competence "binding a delayed
explicit return reference to an earlier topic" in the real production path
`AgentActivity.situationHint -> LocalConversationContext ->
LocalConversationSituationTracker -> ConversationSituationModel`. Free-language
pronoun, role, causal and counterfactual understanding needs the local Gemma
runtime, which is unavailable here: **NOT_RUN**, not inferred from this test.

**Source audit before code change (to be runtime-confirmed by RED):** the
tracker truncates topic cues to 10 characters, but the model tokenizes the
return utterance untruncated, so German compounds longer than 10 characters
(Druckertreiber, Steuererklärung, Versicherungspolizze) can never overlap;
no umlaut/ß folding for ASR variants (Fuehrerschein); no inflection tolerance
(Rechnung/Rechnungen). `recordCorrection`/`addOpenQuestion` are never called
in production. Both files are byte-identical from Code50 `473f7972` through
H3 `8688f855`, so "Code50" and "H1–H3" are **one** baseline arm here.

**Hypothesis H4-B1:** one shared cue normalization inside the existing model
(identical truncation for cues and queries, umlaut/ß folding, prefix tolerance
requiring at least 6 shared leading characters) increases correct bindings on
prospective German/de-AT stories without increasing wrong bindings on
ambiguous and no-referent controls. Falsified if the gain is absent, if wrong
bindings increase, or if the too-tolerant ablation is equally safe (then the
minimum-prefix safeguard is not a necessary mechanism).

**Data origin:** procedural, seeded, self-authored, prospective, **not blind**.
DEV seed **50321**, 60 stories; TEST seed **77419**, 180 stories; TEST is not
generated or inspected before candidate and scorer are committed. Each story:
2–4 topic segments of 1–2 owner turns (German/de-AT vocabulary, compounds,
inflected forms, umlaut-less ASR variants, one distractor turn), then one
return turn that always contains a cue the *baseline* regex recognizes
(`vorhin`, `davor`, `zurück zu dem/der`, `fortsetzen`), so only matching
differs. Strata: unique-long-compound, unique-inflected, unique-ASR,
unique-short (≤10 chars; baseline expected to work), ambiguous (two topics
share the head noun -> key AMBIGUOUS), no-referent (never-discussed topic ->
key NONE). Solution keys are derived from story structure, not from code.

**Arms:** (a) original tracker+model compiled from `git show 8688f855…`;
(b) H4-B1 candidate; (c) ablation prefix minimum 3 (expected over-merging);
(d) ablation truncation alignment only (no folding, no prefix tolerance).
Identical stories, order, JVM flags and independent scorer.

**Metrics:** correct binding; **wrong binding** (critical); correct abstention
(Ambiguous/NoMatch where the key says so); under-commitment (Ambiguous where
unique); missed reference (NoMatch where a referent exists). Thread CPU and
wall per story, p95, process RSS; four rotating paired rounds, 256 MiB heap,
120 s subprocess cap, one JVM at a time. CPU is not energy.

**Acceptance frozen now (TEST):** candidate ≥ **+20 correct bindings /180**
versus the original, paired one-sided exact discordance p < .05; wrong
bindings **not above** the original's count; **zero** wrong bindings on
no-referent cases; all existing tracker/model/context JVM tests pass;
per-story p95 ≤ 20 ms. Otherwise FAIL is retained without TEST tuning.

**Boundaries:** hint stays navigation-only (no fact, memory, tool authority);
no new return-cue regex patterns in H4-B1 (cue coverage such as `zurück zum`
is logged as separate future hypothesis H4-B2); no Android/Gemma/Fold7 claim.

### H4-B1 pre-run amendments (before any DEV arm run, same day)

* The preregistered "ambiguous" stratum (two compounds sharing a head noun,
  return by head noun) is unreachable for both arms because the head is a
  *suffix*; it would have tested nothing about over-commitment. It is replaced
  by same-noun/different-qualifier ambiguity ("Rechnung vom Elektriker" vs
  "Rechnung vom Zahnarzt", return "Rechnung"), which the original resolves as
  Ambiguous and the candidate must preserve. The compound-head case is kept as
  the separate exploratory stratum **head** (unique referent, both arms
  expected MISSED; documents a limitation for a later hypothesis H4-B2). The
  180-story denominator keeps the head stratum, making the +20 gate harder.
* Inflected variants use stem-preserving dative plurals ("zu den Garagen");
  umlaut plurals (Spitäler) are excluded on purpose.

### H4-B1 RED and DEV baseline (`a69b0f29`, source unchanged since Code50)

`scripts/continuum-h4b-check.sh <out> HEAD`: 16 JUnit tests, **3 failures**
exactly on the predicted cases (long compound, umlaut-less ASR spelling,
inflected return -> `NoMatch`); 9 existing + 4 control tests pass
(`docs/evidence/h4-20261108/h4b/red/`). Scorer controls: 14/14
(`h4b-score-test.py`). DEV (seed 50321, 60 stories, JSON SHA-256
`cfc37f0e…`): original arm CORRECT 8/60 (all "short"), MISSED 36 (long 9,
inflected 9, asr 9, head 9), AMBIGUOUS_CORRECT 8/8, NOMATCH_CORRECT 8/8, wrong
0, process max RSS 65772 KiB, p95 story wall 1.96 ms
(`docs/evidence/h4-20261108/h4b/dev-baseline/`). No candidate code exists yet.

### H4-B1 candidate frozen (GREEN 16/16, DEV comparison)

Change: `ConversationSituationModel.CueNormalization` (fold ä/ö/ü/ß, truncate
to the tracker's 10 characters, prefix tolerance only with ≥6 shared leading
characters) used by both `tokens()` and the tracker's cues; labels stay
byte-identical; `LocalConversationContext` needs no change (default
constructor). Ablations are constructor policies only
(`tools/continuum-cognitive-pilots/h4b-arms/candidate/H4BArms.kt`).

DEV (seed 50321, 60 stories; original arm compiled from `a69b0f29`):

| arm | CORRECT | wrong bindings | AMBIGUOUS_CORRECT | NOMATCH_CORRECT | MISSED | median CPU /60 |
|---|---|---|---|---|---|---|
| original | 8 | 0 | 8/8 | 8/8 | 36 | 65.0 ms |
| candidate | 37 | 0 | 8/8 | 8/8 | 7 (head) | 58.3 ms |
| ablation prefix ≥3 | 30 | 5 (+7 under-commits) | 6/8 | 7/8 | 5 | 56.2 ms |
| ablation truncation only | 17 | 0 | 8/8 | 8/8 | 27 | 53.6 ms |

Paired CORRECT: candidate−original +29/0, p=1.9e-9; truncation alone +9 (the
"long" stratum), folding adds the ASR stratum, prefix ≥6 adds the inflected
stratum; prefix ≥3 is **not** equally safe (5 wrong incl. 1 no-referent), so
the minimum-prefix safeguard is a necessary mechanism on DEV. Head-noun
returns stay unsolved (2/9 only via genuine prefixes such as
Wohnung/Wohnungsübergabe). Evidence: `docs/evidence/h4-20261108/h4b/green/`,
`…/dev-candidate/`. TEST is opened only after this commit.
