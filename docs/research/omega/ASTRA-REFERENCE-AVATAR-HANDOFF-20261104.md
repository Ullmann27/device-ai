# Astra reference avatar continuation — 2026-11-04

## Scope and acceptance

Base: `8bb6d8b0424e5c991c866536fa5e73daa08161f6`.
Work branch: `codex/astra-reference-avatar-20261104`.
The owner has rejected the earlier procedural Canvas head. It is not an accepted
reference match. This work owns the existing animatable GLB, its Filament
presentation, avatar pose delivery and their focused tests. The surrounding
layout and `ContinuumHologramView` remain with the integration owner.

The supplied 1536 × 1024 cockpit image is the current visual target; the supplied
portrait closeups clarify anatomy and the violet/cyan fibres. No portrait is used
as a flat replacement for the existing 3D mesh. No paid generation, new model
provider, permission, release or installation is part of this work.

EXP-05/11/12 compatibility: retain the existing runtime event and cancellation
contracts, keep visual work independent of local model inference, and preserve
isolated reversible checkpoints. No observed-memory or cognition result may be
invented by the animation.

## Initial verified findings

The packaged asset is 10,165,644 bytes, SHA-256
`5f49eface4c828bb7f7564acee751e4d1f232a6e574b5038c6a564de69c9a4a3`.
It contains a skinned Face, NeuralThreads, 21 common morph targets, articulated
eyes and jaw, and two embedded 1024 × 1024 material maps. Its geometry is an
adapted MakeHuman bust, not a reconstruction of the newly supplied portraits.

The existing command

```sh
python3 tools/epsilon-avatar-contract/validate_glb.py app/src/main/assets/avatar3d/continuum-avatar.glb
```

was executed on this base and returned exit 1 with
`Eye/Jaw pivots outside preregistered metre-scale bust anatomy`. This reproduces
PR #496. The old Python contract requires jaw Y 0.20–0.25 m and eye–jaw vertical
distance 0.04–0.09 m. The asset's jaw is approximately 0.267 m, eye mean Y
approximately 0.288414 m.

This does **not** establish an Android fallback cause: the Kotlin runtime
`AvatarGlbContract` does not enforce those old numeric windows. The +0.04 m jaw
change was a documented later correction with inversely rebased rigid children.
Do not undo it merely to pass the old numeric test. Resolve the versioned
contract against actual neutral skin, tooth positions and reachable mouth poses;
retain the old tests and use independent closure/intersection regressions.

## PR #495 regression import

The following tests were read and imported unchanged from
`6cf6e2128463e6a24dfac4652bb65e325ff6f18a`:

- `AvatarMorphTransitionTest`: seven visible-blend/jaw/stop cases.
- `AvatarSpeechOrderingRegressionTest`: ten stale, duplicate, future and
  interrupted audio cases against the actual reducer.
- `JarvisAvatarDispatchRegressionTest`: nine queued callback, re-registration
  and failing optional consumer cases against the actual slot.

The central builder executed all 26 tests against the unchanged baseline
production classes after verifying byte identity of the three affected sources:
**18 failed / 8 passed**, compile exit 0 and JUnit exit 1. Failures comprise four
visible mouth-blend cases, seven audio-ordering cases and seven dispatch cases.
The incorrect initial runner spelling is preserved separately and is not RED.
Raw evidence is in `docs/evidence/astra-reference-avatar-20261104/pr495-red/`.

After this observed RED, the three minimal production repairs were imported
from `7859d5d212d61829fabc7dfdbd3ca7587852388d`. Focused GREEN on the resulting
candidate is **NOT_RUN** at this checkpoint. The original PR's green reports are
provenance, not a new local test result.
The real implementation files are `AvatarContract.kt` (contains
`AvatarPoseReducer`), `AvatarMorphMapper.kt`, and `CockpitContracts.kt` (contains
`JarvisAvatarSlot`). No surrounding layout changes are included.

## Next steps and rollback

Observe focused RED on the unchanged production files, apply the small reviewed
repairs, and request focused GREEN from the central builder. Then inspect the
unaltered native asset render and improve actual model/material/camera behaviour
in separate commits. Android Filament GPU, performance, Fold7, exact reference
likeness and owner appearance acceptance remain **NOT_RUN / NOT_ACCEPTED**.

The integration owner publishes the local commits as Draft checkpoints. Until a
remote commit is independently confirmed, this branch is **LOCAL_ONLY**.
Rollback is an individual revert of the relevant commit; no branch reset,
deletion, force-push, main merge, signature or update-channel change.
