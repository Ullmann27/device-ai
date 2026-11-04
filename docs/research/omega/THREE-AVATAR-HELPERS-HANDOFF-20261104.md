# Three additional Continuum helpers — 2026-11-04

## Owner request and starting checkpoint

Heinz requested three additional development helpers to support the existing
design/avatar (#491), personality bots (#493), memory/brain (#494) and model-weight
(#492) workstreams, then focus all three on avatar rendering, expression,
mathematics and animation. These are bounded development agents for this work
session, not three newly installed or continuously running Android bots.

Base: `codex/astra-jarvis-takeover-20261104` at
`d352bab6855db2a44def0bbf60fd93202b4e3066` (#491), independently read through GitHub.
Branch: `codex/continuum-three-avatar-helpers-20261104`.

The first checkpoint was planning only. The unchanged production baseline was
then compiled and tested locally: **62 JUnit tests, 0 failures/errors/skips** on
`a65cfcac63c794ea9953034349989b4a45f6ac73` (production tree from `d352bab6`).
Implementation is still in progress. Earlier PR counts are historical and are
not this branch's evidence.

## Three active assignments

| Helper | Initial support | Subsequent avatar focus |
| --- | --- | --- |
| `helfer_integration` | Verify current ownership, wiring and open review findings | Existing event/state integration and independent review |
| `helfer_avatar_mathematik` | Inspect expression/render contracts and numerical gaps | Face/morph geometry and finite, bounded expression output |
| `helfer_pruefung_animation` | Inspect timing, lifecycle and regression coverage | Animation timing, interruption and reproducible verification |

The coordinator combines only reviewed independent patches. Existing other
helpers, including motion and animation worktrees, are read for collision checks
before file ownership is assigned. Final assignments and measured results are
recorded below as they become available.

## Existing contracts and boundaries

Use `docs/ARCHITEKTUR-JARVIS.md`, the avatar/math handoffs, current sources and
PR descriptions as the implementation contract. Preserve the single original
avatar, actual speech signals, existing mapper/rig contracts, original package,
signing identity and update channel. No second memory store, scheduler or avatar
event authority. #494 retains the memory retriever/compiler/ecology and genuine
memory-event adapter; #492 retains weight experiments; #493 retains bot profiles.

Applicable experiment compatibility: EXP-07 (independent evidence), EXP-11
(existing orchestration) and EXP-12 (source-bound checkpoint/rollback). No model
quality, native GPU, Fold7, APK, runtime-independence or reference-equivalence
milestone is advanced merely by a host unit test or preview.

No main merge, force push, release, installation, new paid service, model/S01
execution or permission expansion is part of this helper branch. Private source
videos, owner data and uncertain-provenance GLB assets are not added or replaced.

## Verification plan

1. Reproduce each specific defect against the exact base with a focused test.
2. Verify each minimal correction and neighbouring production contracts.
3. Combine the three patches and run the affected Kotlin/JVM suite on the
   combined commit using the existing local Android toolchain and builder lock.
4. Record exact commands, source SHA, raw results and any unavailable checks.

Device and native Filament visual acceptance remain **NOT_RUN** unless separately
observed. A host render must be labelled as such and cannot stand in for Fold7.

### Fresh baseline

Executed locally with the existing JDK 17 / Android SDK and a shared builder lock:

```sh
./gradlew --no-daemon --max-workers=1 --console=plain \
  -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest \
  --tests 'at.heinz.deviceai.avatar.*' \
  --tests 'at.heinz.deviceai.avatar3d.*' \
  --tests 'at.heinz.deviceai.motion.*'
```

Exit 0; 62 tests, no failures/errors/skips. Original XML, full console log and
source-bound summary are in `docs/evidence/three-avatar-helpers-20261104/baseline`.
This is a focused Android Gradle/JVM baseline, not a full application, device or
native GPU acceptance. Shared local builder wrapper:
`/workspace/scratch/b3170a7ae924/run_gradle.py`, with `CONTINUUM_BUILD_ROOT` set to
this helper checkout and log `three-helpers-baseline.log`.

## Rollback and continuation

Rollback is to omit these helper commits or revert them normally on the review
branch. Do not reset another active branch. Resume by rereading the latest #491–
#494 heads and this branch's actual HEAD, then compare file ownership and source
digests before integrating. All work is kept on its own review branch and Draft PR.
