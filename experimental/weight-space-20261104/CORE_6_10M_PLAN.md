# Registered 6–10 million parameter research follow-up

Task: G1-INTEGRATE-WEIGHT-002. Scope: RESEARCH_SHADOW only.

This plan is a separate, prospectively registered follow-up to the completed
weight-space experiment. It depends on the independently verified P0 repair
integrated in commit `8e2a2370a9392aeff6bb68a8e2b4b0c1caaa68b2` (driver SHA-256
`9a68ddf27158692044738aed96892ad6476a520b7e30ab6651adfff2c4f38447`).
The original `PROTOCOL.md`, historical results, checkpoints, and production
model runtime are unchanged. This plan changes the registered research budgets
and final holdout only; it does not loosen any quality or resource gate.

## Fixed profiles

| Profile | Target | Parameter budget | Next stage, rejected before allocation |
| --- | --- | ---: | --- |
| `core-6562304` | width-8, 6,562,304 parameters | 6,562,304 | depth-3, 9,712,128 parameters |
| `core-9712128` | depth-3, 9,712,128 parameters | 10,000,000 | depth-4, 12,861,952 parameters |

Both profiles traverse the existing ordered width stages: factor 2 (460,928),
factor 4 (1,708,288), and factor 8 (6,562,304). The second profile then adds one
layer to the verified width-8 checkpoint, yielding 9,712,128 parameters. Width
stages originate independently from the selected small calibration checkpoint;
noise is exactly 0.001 and the seed is 20261104. Depth uses the same fixed seed.
Creation receipts, parity, checkpoint integrity, and fresh-JVM capacity evidence
must pass for every executed stage and both original and selected small sources.

The first unexecuted stage above the registered budget receives the exact
`PREFLIGHT_REJECTED` / `BUDGET_LIMIT` record. This is a planned study boundary,
not an observed resource failure. A forged stop, different next stage, modified
budget, missing proof, or target that did not pass cannot authorize training.
There is no smaller-target or small-control-only fallback in either profile.

## Unchanged training, quality, and resource requirements

Each profile starts its own small control and expanded continuation from their
verified initial checkpoints. Both use the original TRAIN document split,
16 steps, batch 4, at most 63 content tokens per example, seed 20261104,
learning rate 0.0001, AdamW weight decay 0.01, warmup 2, gradient clip 1, and
two workers. The dataset, tokenizer, actual token schedule, completed steps,
and trained-token totals must match. Each process retains its 600-second limit
and 3 GiB Java heap. Training remains accessible only through the central
source-bound authorization path. The existing Kotlin APIs need no changes.

Every capacity decision uses the unchanged fixed 64-token prompt, one warmup,
three measured pairs, complete measured 1/32-token generations, at most 1500 ms
for the one-token call, at most 5000 ms for the 32-token call, and at most
768 MiB observed VmHWM across all samples and the summary. The benchmark has
a 120-second process timeout and 1 GiB Java heap. Checkpoints remain bounded
by 64 MiB. Existing parity tolerances, finite-value checks, stop-reason gates,
development BPB selection, and the 0.00001-BPB preference for fewer parameters
are unchanged. Failed or incomplete continuation is not a successful candidate.
The fixed million-parameter target is required for the expanded training source,
not for the quality winner. Development selection still compares every eligible
candidate, including the original baseline and small controls; either may win.
Report the large initial checkpoints separately as verified capacity candidates.

Two completed matched training pairs are required for the joint study freeze.
Capacity-ineligible trained candidates remain recorded and ineligible. Added
parameters initially preserve the learned function; they do not initially add
knowledge. Identical optimizer settings do not isolate a causal parameter-count
effect because expansion changes optimization geometry.

## Source, data, and isolated results

Use the original registered baseline, tokenizer, TRAIN split, and development
dataset. Use the approved experimental JAR SHA-256
`1b6d22ee57977a6e27ec2d0b72e57fab3a35fb8e694905c1270a4ffe87a2f9ba`.
The fresh independently authored holdout has 32 paragraphs and 4,997 bytes;
its sealed SHA-256 is
`a910f25ed4a54742e87c3e75c105472f0c882df2d4026e15a62ee3d48c90bbbd`.
Its text is withheld from selection. The published historical holdout is not
used as new blind evidence. No settings may be retuned after a holdout result.

Use a new study root with child folders named exactly `core-6562304` and
`core-9712128`. Each has its own environment, logs, content-addressed checkpoint
store, calibration, ladder, and frozen selection. The first constructor writes
`study-registration.json` once. It binds the source, approved JAR, original
protocol, this plan, exact profiles, shared data identities, paths, and limits.
Every new-profile invocation receipt binds the profile, plan, and registration
hashes. Missing, changed, foreign, or historical receipts cannot substitute
for those bindings. Historical default resumes retain their original identity
format and do not require a study registration.

## Common freeze before final evaluation

Complete calibration and the ladder for both profiles, verify both preflights,
then complete each continuation through its own `frozen-selection.json`.
The `study-freeze` phase validates both complete children using cached evidence
only and writes `study-frozen-selection.json` exactly once. It binds both child
selection hashes and the shared fresh holdout, and records the joint development
preference using the unchanged selection rule.

Every new-profile `final` call, including a completed-result resume, requires
the same valid common freeze before holdout evaluation. The child and global
freeze identities must remain unchanged throughout final evaluation. A common
freeze also permanently blocks further training, including when a child freeze
or child environment is missing. Complete, valid historical and study resumes
remain read-only. Do not run a single-profile `all` command before the common
freeze; use the explicit calibration, ladder, continuation, study-freeze, and
final phases in the stated order.

## Boundaries and rollback

No productive training, model promotion, active checkpoint replacement, runtime
change, Android performance claim, release, or installation is part of this
study. A failed gate stops the affected study workflow and leaves the historical
research and original snapshots unchanged. Retain all failures as evidence;
do not manufacture a ladder or call the raw training CLI to continue past a gate.
