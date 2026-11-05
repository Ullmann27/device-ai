# BOT1 / Existing Blink Contract – red-to-green follow-up (2026-11-05)

**Scope:** #551 only. Reproduces and fixes the independent Q1 finding in #519 (the pre-existing `Avatar3dContractTest` requires `BlinkLeft == 1f` at **4.525 s**). No new renderer, clock, asset, materials or other UI changes.

## Source provenance

- Source branch: `bot1/avatar-blink-kinematics-20261105` (base #535 `4bbf2863dc3c7debe74ac81f3d16521926c8ae0a`); PR #539 is a separate orb donor, not merged.
- Original before this follow-up: `AvatarIdleMotion.kt` Git blob `4fc3914cf161f371e6375a63c3fb1b7a8863ba0b`. Full local copy matched `git hash-object`.
- Fixed production Git blob: `5b89c17443ca8e362f479816f77cda862150f887`, production commit `c48bf7b7e1c3a6f690d7bf8727b5906e1df9912e`. GitHub returned and then reread this exact blob.
- Added assertion in existing `AvatarBlinkEasingTest.kt`: old blob `9e37279d48da93e4c052ca30a687c88358e7dd9f`, new blob `3426e05f5b257a45c0644c68b1f9d4e679b849c5`, commit `791f6f66f3b9843f404871320888e1d3875791b1`.
- Updated self-contained Kotlin probe `tools/avatar-3d/motion-tests/AvatarBlinkKinematicsCheck.kt`: blob `a8485e4934e7834532631388f84f581069dca06d`, commit `20354d7787ab1bdab3c76aa7a7a1acd1ab73440f`.
- The complete original `app/src/test/java/at/heinz/deviceai/avatar3d/Avatar3dContractTest.kt` was read from #551 as blob `ad30277acb517e0228a28ee54b602b3a8dd07b7a`; its existing test `visemesCrossfadeOverSeventyMillisecondsAndBlinkLastsFifteenHundredths` contains `assertEquals(1f, closed.weights[idx("BlinkLeft")], 0f)` after calling `map.update(..., 4.525f, true)`. This *original Android test* was **not** executed here.

## Cause and minimal fix

Prior 55-ms close/95-ms reopen had a complete closure at 4.505 s but gave `0.9336765f` at 4.525 s, violating the unchanged original test. The revised 150-ms profile is **55 ms quintic close + 20 ms full-closure plateau + 75 ms quintic reopen**, preserving 4.450–4.600 s window, 100 primary + 20 secondary blink slots, existing eye/head harmonics, clock ownership and morph mapper. Quintic output is clamped to [0, 1] to avoid float overshoot (observed `1.0000006` in an intermediate host run). This patch has no GLB edits.

## Actually executed / Host Linux x86_64, OpenJDK21, kotlinc-jvm 1.9.0

A full copy of pre-fix and post-fix production source was compiled locally; original Git blob identity was verified before editing. Regression was frozen before the production update.

1. Frozen mapper-contract **host-only** Kotlin check (separate standalone harness, directly samples the production motion class):
   - Before: compile exit 0, run **exit 1**, `expected 1.0, got 0.9336765` at `4.525f`.
   - After: compile exit 0, run **exit 0**, “PASS: preserved 4.525s 100% lid closure plus minimum-jerk starts/plateau/open, cycle, bounds”.
   - It checks exact closure, secondary closure, start/end/plateau velocity, 4,601 cycle samples, invalid clock values.
2. Actual `AvatarBlinkEasingTest.kt` source, compiled with a **temporary local JUnit API stub** and five methods directly invoked (not Android Gradle/JUnit runner):
   - Before: compile exit 0, run **exit 1**; after first two green methods, newly added exact-closure test fails with `expected=1.0, actual=0.9336765, delta=0.0`.
   - After: compile exit 0, run **exit 0**, **5/5 directly invoked test methods PASS** (no skips).
3. The **published self-contained** `AvatarBlinkKinematicsCheck.kt` (blob above), compiled and run with the exact production source:
   - Before: compile exit 0, run **exit 1**, `legacy exact 4.525-s closure contract broken`.
   - After: compile exit 0, run **exit 0**, “PASS: smooth primary closure/reopen and cycle seam; 150-ms profile, extra closure, bounded deterministic motion”.

Reproduce item 3 on exact #551 HEAD with a local Kotlin/JDK toolchain:

```sh
kotlinc app/src/main/java/at/heinz/deviceai/avatar3d/AvatarIdleMotion.kt \
  tools/avatar-3d/motion-tests/AvatarBlinkKinematicsCheck.kt \
  -include-runtime -d /tmp/continuum-bot1-blink-contract.jar
java -jar /tmp/continuum-bot1-blink-contract.jar
```

## Review and remaining gates

**CODE-HOST-GREEN, ANDROID-CONTRACT-NOT_RUN.** The claim is restricted to this precise small source pair and self-contained host probe; there was no full Gradle/Android-JUnit, Robolectric, native Filament 3D render, model inference, audio audition, Fold7 device/GPU test, signing or APK generation. The full 12,038,416-byte pinned GLB could not be loaded through this session's GitHub file reader (`base64` content empty); only its original Git blob identity and metadata were available. No SHA-256 over those bytes or 3D image comparison is claimed.

C1/#519: the independent existing-test regression in prior #551 has a **source fix**, not a joint-tree acceptance. Review production commit `c48bf7b7e1c3a6f690d7bf8727b5906e1df9912e`, test commit `791f6f66f3b9843f404871320888e1d3875791b1`, host probe commit `20354d7787ab1bdab3c76aa7a7a1acd1ab73440f` in that order. Independently run the original full `Avatar3dContractTest` and Android suite on C1's precise combined tree before intake. No main merge, signature/publisher, original update or user-file changes. Revert the donor slice by excluding #551.
