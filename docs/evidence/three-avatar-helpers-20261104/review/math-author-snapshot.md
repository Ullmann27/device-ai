# Avatar mouth transition correction — 2026-11-04

Status: LOCAL_ONLY / NOT_PUSHED. Root owns Gradle RED/GREEN and remote persistence in draft PR #495.

Worktree: `/workspace/scratch/b6ee64d418cf/helper-mathematik`
Branch: `codex/three-helpers-mathematik-20261104`
Base: `a65cfca` (parent reports production identical to `d352bab6855db2a44def0bbf60fd93202b4e3066`).
Tests-only commit: `f3f7d1f849d818b5186589d73aad9d74df259705`.

## Verified defect

The mapper subtracted the target cue's opening before advancing the crossfade, while the face still rendered the outgoing cue. Root's original compiled-production probe is at `/workspace/scratch/b6ee64d418cf/probe-baseline/AvatarBaselineProbe.java`, output `baseline.txt`.

AA(jaw=.6, cue=1) at 1.0 seconds -> PP at 1.016 seconds retained AA=.7714281. Its authored opening was .6267853, and the old code also added JawOpen=.6, yielding total common opening 1.2267853. The correct common opening is max(.6, .6267853), while the independent lip-shape contributions remain intact.

## Narrow change

Only these two repository files changed:

- `app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt`
- `app/src/test/java/at/heinz/deviceai/avatar3d/AvatarMorphTransitionTest.kt`

After progressing each viseme blend, sum the visible weights times their existing authored opening ratios. Set JawOpen=max(0, measuredJaw-renderedOpening) and jawPitch=max(measuredJaw, renderedOpening)*12. Public APIs, pose/reducer, asset/rig bytes, timing policy, speech source, Activity and motion libraries remain unchanged.

The seven new tests cover AA->PP and AA->O with 16-ms steps, PP->AA, cue-only outgoing AA, silence/zero-weight stop, reduced-motion reset/resume, and the existing repeated-time/clock-rollback snap policy. Expected failures on unmodified production: first four tests; three stop/timing control tests should remain green. Expected results are not execution evidence.

## Validation

- `git diff --check`: PASS before both commits.
- Gradle RED: pending root execution on tests-only commit.
- Gradle GREEN and existing focused suite: pending root execution after fix.
- No hardware/GPU, APK, signing or deployment claim.

## Integration and rollback

Cherry-pick the tests commit first for RED, then the immediately following fix commit on this branch for GREEN. Root will record exact integration SHAs, commands, logs and remote commit URLs in its shared versioned handoff. Rollback is a normal revert of the fix commit; no branch reset or force push. Existing experiment/core integration boundaries are unaffected; no new EXP feature is introduced.
