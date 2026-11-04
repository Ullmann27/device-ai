# LM1 training continuity — prospective support round 3

Status: WIP / EXPERIMENTS_NOT_RUN. Owner continuation: 2026-11-04, sequential work while the owner is unavailable. No new parallel agents.

## Immutable starting points

- Own support base: #514, `bfed526727041b2581f22b5d613dcb5cd8c188b3`.
- Latest quality evidence inspected: #508, `59891cfad6dd015f47c187cc688ea94b0026ad7d`.
- Independent source review: #492, `0af14f75b04e68ba849a92316b8be51041e8738b`.
- Existing gate integration: #525, `57a52a326020dc1fb11367afc655254cc3329c14`. Do not duplicate that integration.
- Original LM kernel source: `30cd3a89810ed14fa84eca95236d3177e016118d`.

The completed quality studies missed their acceptance criteria. Their final data are consumed. No continuation training or model selection from those failed candidates, no reading of sealed datasets, no automatic 16M retry under the old registration. This round tests training correctness and interruption recovery with independently specified tiny synthetic fixtures. It does not assert improved language quality.

## Three sequential milestones

### M1 — reject invalid optimizer updates before mutation

Reproduce the native optimizer's handling of non-finite/malformed gradients and invalid numerical controls. Preserve the original failure outputs. Supply an isolated patch that validates an entire update before any weight, optimizer moment or step changes. Positive updates must remain byte-identical to the original arithmetic; epsilon convention and weight-decay scope remain unchanged. Test actual Kotlin classes, not a Python reimplementation.

### M2 — preserve full training state

Add a research-only checkpoint codec for weights, Adam first/second moments, optimizer step, learning-rate schedule settings, worker count, tokenizer identity, source identity and deterministic data schedule identity. Verify uninterrupted versus saved/reloaded continuation on fixed tiny sequences. Include a weights-only restart negative control. Reject corruption, missing state and incompatible identity before restoring usable training state.

### M3 — bounded sequential recovery

Connect that codec to a locally executed bounded research runner. Observe real independent JVM segments and a deliberately interrupted process. Verify exact recovered final weights/moments and schedule position against uninterrupted execution, immutable checkpoint publication, duplicate-launch prevention and explicit incomplete-run status. No background daemon or Android runtime activation. A finite budget and reproducible restart entry point replace any claim of unlimited execution.

## Fixed initial fixtures and limits

Numerical correctness fixtures use fresh minGRU models with byte tokenizer (258 vocabulary entries), dim 8, one layer, context 32, MLP 16, seed 20261104. Fixed short synthetic sequences, unequal sequence lengths and repeated IDs exercise batching. Positive compatibility probes also cover the existing transformer branch. Every fixture, optimizer setting and execution command is recorded before its measured run.

The process budget is at most 120 seconds and 1 GiB JVM heap per tiny native invocation, one training worker, at most 32 updates per continuity scenario. Failure is recorded; a changed implementation is a new software revision, not a repeated independent quality trial. No actual 16M optimizer update is planned in this round.

## Scope and compatibility

Related experiment areas: EXP-05 own decoder and EXP-12 checkpoint continuity. All new material stays under `experimental/weight-space-20261104/support-round3/` plus the dedicated handoff. Production sources remain byte-identical. Candidate kernel changes are delivered as patches and compiled into isolated test artifacts. Existing LM1C weight checkpoints retain their format; a complete training-state bundle is a separate research format and cannot silently replace a weight-only checkpoint.

No paid model API, Actions, main merge, APK signing/install, active-model promotion, external messages or deletion of user files. Save meaningful checkpoints to the official repository. No claims of uninterrupted execution after this session ends.

## External technical reference

PyTorch's official AdamW documentation exposes optimizer-state save/load separately from model weights: https://docs.pytorch.org/docs/stable/generated/torch.optim.AdamW . It is a reference for state ownership, not a new runtime dependency. The actual acceptance criterion here is exact replay of Continuum's own native arithmetic.
