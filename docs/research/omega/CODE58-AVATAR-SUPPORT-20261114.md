# Code58 avatar support — clock epoch repair

## Scope and integration

- Base: PR #836, source `1656443d137bb6787c2eabe69a3e91ff1b2275b2`.
- Support branch: `work/code58-support-avatar-20261114`.
- Integration owner: the Code58 support coordinator; this change belongs in its combined support PR targeting #836.
- Affected cinema experiment: `JarvisCinematicSettings.Experiment.EXP_09_FOCUS_BLINK`.
- No change to the separate repository experiment register's EXP-09 toolchain recipes.
- Runtime path: `ContinuumHologramView.advanceFrame` → `FilamentAvatarRenderer.render` → `AvatarMorphMapper.update` → `GazeBlinkPolicy.sample`.

## Reproduced defect

The view wraps its avatar clock after 460 seconds. `GazeBlinkPolicy` previously retained `lastEventAt` and `triggeredAt` across that wrap. A blink triggered at 459.1 seconds prevented fresh gaze blinks in the next clock epoch because the cooldown difference became negative. A completed old blink could also reappear when a rolled-back clock revisited its former trigger time.

The existing call path and policy were unchanged between the inspected earlier source and Code58's exact base. This is a repair to the existing runtime policy.

## Change

Track the last finite frame time and clear gaze/blink history when time moves backwards, before applying enabled/input gates. The first frame of the new epoch establishes a gaze baseline; it does not invent a blink. The normal 1.8-second cooldown remains in force when blinking is disabled and re-enabled within the same epoch.

The five new JUnit methods cover the real clock wrap, expired-blink replay, same-epoch disable/re-enable, wrap during disabled frames, and rollback after invalid time/gaze input. Existing blink shape, movement threshold, cooldown, and opt-in gate remain intact.

## Verification

Host: OpenJDK 21.0.13, Kotlin compiler 2.2.0, actual JUnit 4.13.2 and Hamcrest 1.3.

| Source | Check | Result |
| --- | --- | --- |
| `1656443d137bb6787c2eabe69a3e91ff1b2275b2` policy plus new tests | JUnit `GazeBlinkPolicyTest` | RED: 7 tests, 4 failures |
| Patched policy plus the same tests | JUnit `GazeBlinkPolicyTest` | GREEN: 7 tests, 0 failures |
| Original and patched policies | Independent standalone Kotlin scenarios | RED 4/7 failures → GREEN 7/7 passed |
| Patched tree | `git diff --check` | PASS |
| Android build, native rendering, Fold7, FPS | Device/runtime checks | NOT_RUN in this support task |

Observed before repair: fresh post-wrap blink peak `0.0` instead of greater than `0.9`; expired blink replay `1.0` instead of `0.0`. Both are corrected by the same epoch reset.

Verified source SHA-256:

- `GazeBlinkPolicy.kt`: `d187ec96fce2445238a7c32b9cae59ba6151b6066c0ef8e6c470ab02a36eaa03`
- `GazeBlinkPolicyTest.kt`: `84a8197c8fd5f628b1a10636a29bc0c75234298e3f2facb5bf2b7e4e888f12f9`

Reproduction without an Android SDK, from the repository root:

```sh
avatar_kotlin_lib=/workspace/scratch/e0abce7d2838/toolchain/gradle-9.1.0/lib
avatar_test_jars=/tmp/continuum-support-tools/junit-4.13.2.jar:/tmp/continuum-support-tools/hamcrest-core-1.3.jar
java -cp "$avatar_kotlin_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$avatar_kotlin_lib/kotlin-stdlib-2.2.0.jar:$avatar_test_jars" -d /tmp/gaze-blink-tests.jar app/src/main/java/at/heinz/deviceai/avatar3d/GazeBlinkPolicy.kt app/src/test/java/at/heinz/deviceai/avatar3d/GazeBlinkPolicyTest.kt
java -cp "/tmp/gaze-blink-tests.jar:$avatar_kotlin_lib/kotlin-stdlib-2.2.0.jar:$avatar_test_jars" org.junit.runner.JUnitCore at.heinz.deviceai.avatar3d.GazeBlinkPolicyTest
```

For the RED run, compile the policy exported from the exact base commit with the same new test file. Do not replace the tests with the base version.

## Status and next step

- PORTIERT: existing runtime policy repaired; no alternate animation loop or renderer.
- VERIFIZIERT: exact policy and test sources compiled and run with real host JUnit.
- GERÄTEBESTÄTIGT: no. No claim about measured frame rate, native GPU output, or Fold7 rendering.
- Coordinator next step: combine the support commits and run the existing Code58 Android QA for that combined source. No standalone remote write by this worker.
- Rollback: revert this support commit; no migration, settings, assets, or persistent state are affected.
