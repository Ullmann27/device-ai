# CDX generation repair — 2026-10-26

Status: **OWNER_STOPPED / PARTIALLY_MEASURED / RESEARCH_SHADOW**, EXP-05;
no runtime activation. Owner requested secure stop at20%usage; no further
training/inference/comparison job was started after that instruction.

Official base: `c391b832`, branch `sol/cdx-generation-repair-20261026`,
integration target `sol/continuum-brain-breakthroughs-20261026`. Work is isolated
in its own worktree. Owner SWB1/CDX1/CRE1 files, prior checkpoints, main, deployed
resources, memory routing and Gemma are outside this repair's write scope.

## Ordered investigation

1. Reproduce free generation of the archived synthetic CDX1 checkpoint; trace
   training/inference contexts, conditioner use and parameter derivatives.
2. Freeze an authored train/dev protocol, baseline and measurable quality gates
   before candidate training/prediction. Old independent TEST16 is consumed.
3. Test the smallest justified repair, or a separately named research decoder
   if diagnosis establishes the architecture is inadequate. Preserve every old
   checkpoint format and failing result. Falling teacher-forced loss alone is
   not progress.
4. Freeze the chosen candidate and ask the parent for new independently authored
   evaluation cases. Execute once with exact source/checkpoint hashes; archive
   all outputs, positive and negative results, provenance and reproduction.

Checks at the initial checkpoint were **NOT_RUN**. Existing baseline evidence is in
`docs/evidence/sol-brain-20261025/cdx-baseline-v1/`: 6000 updates, train1/4,
consumed DEV0/2, consumed independent TEST0/16 despite loss5.55→0.25.

Host-only bounded single-core experiments are authorized. No Android/phone
quality, training, installation, promotion or production benefit is claimed.
Checkpoint this branch on GitHub every meaningful step or about5–10minutes.

Diagnostic source: `experimental/brain/Cdx1CausalDiagnosis.kt` compiles against
the real SDK classes with module `DeviceAI_app_debug`, target17. It compares
teacher forcing with the inference forward function, ablates conditioner
vectors, and finite-differences30 nonzero parameter derivatives across every
weight array. Frozen source `1f18e6bc008508225216f58fbaaa7c0f5d192f40` measured
maximum absolute error5.85e-11, exact train/infer forward agreement and bit-exact
reproduction of original6000 binary hash. No app code has changed.

`docs/evidence/sol-cdx-20261026/` archives all12 matched raw/unit/centered models,
diagnostics, outputs and manifests. Frozen protocol `f2406ff6` used original4
TRAIN pairs and newly authored20 DEV, no old TEST16. Centered×32 at30000 updates
was selected by frozen DEV rule: TRAIN4/4 vs raw1/4; known-fact wording4/8 vs
raw2/8. All changed-fact4, unseen-entity4 and unsupported4 DEV remain wrong.
Real generation regression RED3/4wrong→GREEN4/4correct. This demonstrates only
known-fact wording transfer/memorization repair; no novel-fact generalization.

Next bounded hypothesis: separately named full-evidence neural pointer
transducer, new train/dev protocol, no template replacement or app integration.
Every next-pointer/abstention/EOS choice must come from trained neural scores;
raw source tokens are its dynamic output vocabulary. Engineered lexical,
sentence-boundary/negation-presence and relative-pointer inputs are explicit
inductive biases, not a claim of learned general semantics. Parent authors NEW
independent cases only after final source/model freeze.

Next entry: freeze narrow pointer task, protocol and architecture before training. Rollback by
reverting individual research commits; prior source and checkpoints remain.

## Clean stop and exact continuation boundary

All measured conditioner results, diagnostics and12 checkpoints were pushed in
`b9ece28c`; frozen executable comparison source is
`1f18e6bc008508225216f58fbaaa7c0f5d192f40`. Protocol is`f2406ff6`.
Selected centered×32/30000 binarySHA:
`7097efec621f81fcd35ef58561489ecbbc9e72e9f6f9da54efde359c4be5cc76`.

Separate CDXA1 data/protocol/tests were pushed at`01c828ed` before any quality
prediction. Pending model source is now secured by this closure commit.
**CDXA1 UNTRAINED / UNVERIFIED**, no weights or quality results exist.
Seven unit/numerical checks passed in0.051s before stop; captured output is in
`docs/evidence/sol-cdx-20261026/cdxa1-unit-checks.txt`. They cover48 finite-
difference comparisons, exact Unicode source chunks, read-only generation,
serialization/limits and zero weights repeating token0 (no forced next/EOS).
No new unit run, training, inference benchmark, comparator or production work
was undertaken after the stop instruction. No model process remains active.

`experimental/brain/CDXA1-RESUME.md` gives the exact architecture, commands,
unsupported tasks, still-missing fair CSX/CDX comparators and next steps.
The separately authored NEW32in-family+8challenge independent fixture remains
**UNOPENED / UNCONSUMED** by this agent; no test-based selection/tuning happened.
Its announcedSHA is`58bcb0d6393b10f9af0f064692a71458f1ff8978759fb9b5e86d1b8b0a4e0e7c`.

No source file under`app/`, deployed resource, owner data or existing model
checkpoint was modified. No main merge, signing, installation or promotion.
