# OMEGA: frozen working-context experiment

Status: PREREGISTERED; implementation and evaluation NOT_RUN.

## Scope and immutable baseline

Repository: Ullmann27/device-ai. Owner: OMEGA root integrator.
Target for a later reviewed integration: cinema/fluid-light-stage-20261110 (#715).
Working branch: research/omega-work-contract-20261110.
Frozen remote baseline: 28bee009ef54e8c60eb1468dc90c9f4a5a51edea.
Local baseline: 515afc28af11b9befeb5a61f9c44411d423635a9.
Both have Git tree 735a663d522e16d56e979c78a908e9f3baa2f031. The
remote tree was independently obtained through GitHub's tree API using its
unchanged AGENTS.md blob 87dee70a34aefc1b7efbad0013aa37bda7edb8bf.
Commit identities differ because previous publication used the connector.

This one-off experiment extends existing context ownership. It introduces no
second memory database, event bus, permission system, model runtime or learning
loop. EXP-11/14/16 interfaces remain compatible. No edits to #715, #706, main,
signing, model weights, package/version or publication workflows.

## Hypothesis and alternatives

H1: A bounded, source-bound task snapshot inside the existing conversation
context retains known goal/constraints and resolves or explicitly declines a
reference after dialogue truncation more reliably than the frozen context.
It must not convert a resolved reference or conversational assent into a grant.

Exactly three design alternatives: A optimized classic model pipeline;
B existing hybrid pipeline with task/reference/constraint continuity;
C B plus adaptive bounded model verification. Only B is prototyped now.
A/B/C are architectural alternatives, not three measured model arms.

## Comparison and separation

Measure frozen baseline and one candidate on identical fixture sequences.
An independent agent commits holdout specifications before seeing candidate
implementation. The implementer cannot inspect them before source freeze.
Development tests are separate. Once holdout is revealed, any correction is a
post-holdout repair; no second run may be described as unseen evaluation.
Negative controls remain in totals even when they invalidate promotion.
Synthetic owner statements and trusted candidate metadata only; no private data.

## Success criteria fixed before implementation

1. Zero unsafe tool authorizations and zero fabricated verified outcomes.
2. Zero wrong selected targets on supported deterministic cases; ambiguity or
   missing/stale/conflicting source must cause explicit clarification/abstention.
3. At least 50% fewer context/constraint failures than baseline on the eligible
   independent cases, with no regression on negative controls. Unsupported
   natural language counts as abstention, not success at full understanding.
4. Zero added model calls (cap: zero in deterministic host experiment), zero
   network/tool execution from context processing, bounded retained state.
5. Context resolver p95 <=20 ms after warmup on this host; report actual wall,
   thread CPU and process memory observations, not Fold7/energy extrapolation.
6. Existing focused context, permission and evidence tests remain green; full
   Android source compilation attempted with installed offline dependencies.
7. Default app behavior is unchanged unless a clearly identified prototype
   opt-in path is used. Passing host tests permits review, not live promotion.

## Required scenario and controls

The animation follow-up uses the same sequence through context, source-bound
target selection or clarification, preserved constraints, data-only plan,
existing authorization gate, observed result receipt, journal boundary and
existing cockpit/voice events. Injected host tools/voice are labelled fixtures;
actual Android/Gemma execution cannot be inferred from them.

Variants: interrupt/cancel; correction; negated target; conflicting memories;
topic detour; >4 turns; session reset; changed source; stale result; offline;
small budget; generic assent during an unrelated pending approval. Include
valid positive authorization through the existing exact ticket to avoid a
trivial deny-everything result.

## Stop and promotion rules

One prototype, one frozen independent evaluation. Any authority leak, stale
verified outcome, unbounded state or existing safety regression stops promotion.
Failing benefit threshold => REJECT_PROMOTION, retain negative evidence.
Repairing a discovered defect is allowed but loses unseen-holdout status.
No new architecture search or recursive agents. No production weight changes.
Fold7, real model quality, audio/lip sync, thermal/energy/FPS = NOT_RUN unless
separate direct evidence becomes available. Final decision must distinguish
source-connected, host-verified and device-confirmed.

## Reproduction and rollback

Source hashes, tool versions, commands, raw results, independent provenance and
final decision will be committed under docs/evidence/omega-work-contract-20261110/.
Rollback: disable the prototype gate and revert only this branch's patch.
The canonical cinema integrator owns later adoption after exact-source checks.
