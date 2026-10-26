# CDX generation repair — 2026-10-26

Status: **WIP / UNVERIFIED**, EXP-05 research shadow; no runtime activation.

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

Checks at this initial checkpoint: **NOT_RUN**. Existing baseline evidence is in
`docs/evidence/sol-brain-20261025/cdx-baseline-v1/`: 6000 updates, train1/4,
consumed DEV0/2, consumed independent TEST0/16 despite loss5.55→0.25.

Host-only bounded single-core experiments are authorized. No Android/phone
quality, training, installation, promotion or production benefit is claimed.
Checkpoint this branch on GitHub every meaningful step or about5–10minutes.

Diagnostic source: `experimental/brain/Cdx1CausalDiagnosis.kt` compiles against
the real SDK classes with module `DeviceAI_app_debug`, target17. It compares
teacher forcing with the inference forward function, ablates conditioner
vectors, and finite-differences30 nonzero parameter derivatives across every
weight array. **WIP:** scratch run completed; repeat from this committed source
before archiving measured results. No app code has changed.

Next entry: archive pinned diagnostic/reproduction evidence before modifications. Rollback by
reverting individual research commits; prior source and checkpoints remain.
