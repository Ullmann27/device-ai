# Avatar motion: selective PR503 integration and signed-zero correction

Date: 2026-11-04. Child agent: `avatar_render_motion`.

## Source and scope

- Isolated worktree: `/workspace/scratch/b6ee64d418cf/avatar-round2-renderer`.
- Branch: `codex/avatar-round2-renderer-20261104`.
- Base: `ac2ca364b3e21655fa249d3db943c26751e92bd5`.
- Patch commit: `0be6f579c0dd60ffa1266c3ab7866360d1a0e8e2`.
- Reviewed upstream: PR503, `2582be86c121d7f6d1179c7edc49e979bee8ba9c`, including the prior PR502 motion implementation.
- Secured originals remain unchanged under `/workspace/scratch/b6ee64d418cf/avatar-round2-upstream/pr503/`.
- Exactly six repository files changed: 342 insertions, 34 deletions. No Gradle invocation or direct push by this child. Root integrates into the existing Draft PR495 and runs the combined gate.

## File-by-file decision

| Repository path | Integration and review |
| --- | --- |
| `app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt` | PR503 original plus one time-normalization correction: canonical positive zero before binary search; retains `CYCLE_SECONDS`. |
| `app/src/main/java/at/heinz/deviceai/avatar3d/AvatarMorphMapper.kt` | Byte-identical PR503 original. Adds the existing deterministic idle-motion sampler; active PP closes outgoing vowel morphs and measured jaw immediately; repeated render timestamps preserve the current blend. |
| `app/src/test/java/at/heinz/deviceai/avatar3d/Avatar3dContractTest.kt` | Byte-identical PR503 original, 10 JUnit methods. Fresh mapper per viseme isolates target-name mapping from crossfade history; all original name/weight assertions remain. |
| `app/src/test/java/at/heinz/deviceai/avatar3d/AvatarArticulationBoundaryTest.kt` | Byte-identical PR503 original, all **four** JUnit methods: bilabial onset, bilabial onset at unchanged time, repeated-time idempotence, and equal elapsed time across different frame counts. |
| `app/src/test/java/at/heinz/deviceai/avatar3d/AvatarMorphTransitionTest.kt` | All seven upstream methods plus two preserved general outgoing-vowel tests using AA→SS. Nine JUnit methods total. |
| `app/src/test/java/at/heinz/deviceai/avatar3d/AvatarIdleMotionTest.kt` | Nine JUnit methods: signed zero / negative whole cycles, fixation hold and change, double blink with reopened lids, history-independent sampling, wrap continuity, whole-cycle offsets, observed-gaze precedence, non-IDLE gaze precedence, and reduced-motion stop. |

All per-file Git blob IDs, SHA-256 hashes, byte lengths, upstream equality results, and JUnit method counts are recorded in `probe-manifest.json`.

## Why the test changes preserve the previous contracts

The existing PP transition tests could not simultaneously require an outgoing vowel to remain visible and require an immediate bilabial seal. The imported PP cases now test the latter explicitly. The earlier general compensation and inner-jaw-alignment checks remain as additional AA→SS cases; SS has zero authored opening and does not require PP's immediate closure. The cue-only case additionally asserts both visible viseme weights. Tolerances and the common-opening assertions are retained.

The old repeated-time snap expectation is superseded by the explicit idempotence test in `AvatarArticulationBoundaryTest`. Clock rollback still has its own snap/reinitialization test. The target-name test still checks every viseme against reversed asset ordering, independently of previous frames.

The nine idle-motion tests include a small adaptation of still-valid PR502 `Main.kt` behavioural checks. The standalone runner itself is not copied or executed. It still contains two stale assumptions: AA→PP retaining the outgoing AA blend, and a repeated timestamp snapping the transition. Its reported historical test count is not claimed as current verification.

## Reproduced defect and minimal correction

`seconds.toDouble() % CYCLE_SECONDS` retains negative zero for `-0.0f` and exact negative whole-cycle inputs. The old `< 0.0` condition does not change that value. `DoubleArray.binarySearch(-0.0)` orders it before the positive zero at `STARTS[0]`; the derived slot becomes `-1`, followed by an out-of-bounds read.

The corrected code maps every zero remainder to positive `0.0` before computing the phase or searching the slot. Ordinary negative remainders still add the existing `CYCLE_SECONDS` constant. No index clamp, fallback animation, new clock, or exception suppression was introduced.

## Actually executed evidence

The same `SignedZeroProbe.kt` was used for the original and corrected `AvatarIdleMotion`. The direct cached compiler is Kotlin 2.4.0 and the JVM is Java 21.0.13. This probe uses the actual Kotlin class, without a JavaScript reimplementation, Gradle, an Android stub, or a renderer.

| Probe | Process exit | Observed successful inputs | Observed input errors |
| --- | --- | --- | --- |
| Original PR503 class | 0 on the recorded replay | 6 | 3 `ArrayIndexOutOfBoundsException` results: `-0.0f`, `-460.0f`, `-920.0f` |
| Corrected class | 0 | 9 | 0 |

The original probe deliberately catches `Throwable` per input. **Its zero process exit is not a passing sample result.** The first original log did not record an exit code. Replaying its previously compiled original classes returned exit 0 and produced output byte-identical to that preserved log. Both facts are recorded separately in the manifest. The corrected compilation returned exit 0 and its process returned exit 0; the output was independently checked for nine `OK` lines and no `ERROR` line.

Successful control inputs were positive zero, ordinary negative time `-0.5f`, positive one/two-cycle offsets, NaN, and positive infinity. The three previously failing inputs now all produce the same five zero-valued motion outputs as positive zero.

Saved evidence in this directory:

- `SignedZeroProbe.kt`: unchanged probe source.
- `original-probe.log`: original failure output, preserved byte-for-byte.
- `original-repeat-probe.log`: byte-identical repeat of the previously compiled original probe; process exit captured in the manifest.
- `fixed-probe.log`: nine successful corrected samples.
- `fixed-compile.log`: corrected compiler output.
- `probe-manifest.json`: exact source hashes, commands, runtime versions, process exits, sample error counts, patch commit and file manifest.

`git diff --cached --check` passed before commit; the worktree was clean after commit. Static verification confirmed four unchanged upstream articulation methods and the exact six-file scope.

## Remaining acceptance boundary and handoff

The JUnit source now contains 32 methods across these four changed/added test classes: 10 contract, four articulation, nine idle-motion, and nine transition methods. **The child has not executed these JUnit methods.** Root must run them on the combined integration source together with the existing speech/mouth/lifecycle regression selection. The direct signed-zero probe only verifies the actual `AvatarIdleMotion` class for its nine input samples.

Native GPU rendering, an Android device, audible TTS, frame pacing, lifecycle integration and visual quality were not tested by this child. No asset, Activity, UI fixture, reducer/director, Filament renderer, build setting, signature, release, installed package, or other project strand was changed.

Relevant project contracts: EXP-07 regression evidence, EXP-11 coordinated integration through the existing review path, EXP-12 reproducible commit and rollback. Status is `SOURCE_INTEGRATED` on the isolated branch, direct JVM probe verified, combined JUnit/Android/device gates pending. This local commit is `NOT_PUSHED` by the child; Root owns publication through PR495. Rollback is a scoped revert of `0be6f579c0dd60ffa1266c3ab7866360d1a0e8e2` or of Root's corresponding selective integration commit; preserve the original and corrected evidence.
