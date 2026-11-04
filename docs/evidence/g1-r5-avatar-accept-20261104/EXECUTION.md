# Executor-observed host runs

Source base: 28b228698aef8c52e46c72b71fdd5e1915d6e76c. Kotlin 1.9.0; OpenJDK 21.0.12.1. Compiler target 17. No Android SDK, Gradle cache, JUnit runtime, GPU, device or model execution.

## Source identities verified before execution

| File | Base Git blob | Base SHA-256 |
| --- | --- | --- |
| AvatarContract.kt | 42a92a5f57512344a403a5910daab07a93bf7154 | 02b04292e0cd3c9e46cfa8cdfe262e17ffee5eb7da7a29746adf1a4b561fba9d |
| AvatarMorphMapper.kt | d1961d03d43652ab52deb0a2676c4de8dc285889 | 27f619196873eff007eefc3169ac732d4b757feeaadb371dd866f05972d1fe59 |
| AvatarIdleMotion.kt | c60d0e180d2d3393b2db355b188a61dcba42f79e | 88bbdff0ca524500a5e63fdac6c360868f05a4c0606d84acdb44ea5577d7634a |
| tools/fable-avatar-motion-check/Main.kt | 3b3b6fed8d44b2793664519d2185334a198c6ea8 | 6f3ff17ea0335e7f23645a052a8c46dcd5e61efab6c275119615debbe90555f0 |

Final sampler: blob 31801578f47f6a677c58283a408bc2840297de6d; SHA-256 e68e8044e04d3506686fde2b98512550bceeb5316832b196aa718c36739d1b16. Exact import from #495 9ef9bd16, not a new alternative implementation.

Shared five-case test blob: 5c860c712e9e54e38b77fe27d3f44020d2a030d0. JUnit wrapper blob: d66e3529ff300df29a1ea844bedba971d612e25a. Draft-preservation UI test blob: a438aeadd657558c38ea54de1a6c1435d1b892c6. Only the shared five-case body was executed; wrapper and UI tests are NOT_RUN.

## Actual command forms

Local root was /mnt/data/continuum-g1-r5. Compiler outputs were temporary host jars, not APKs.

```sh
kotlinc host/src/AvatarContract.kt host/src/AvatarMorphMapper.kt host/src/AvatarIdleMotion.kt host/fixtures/AvatarGlbContract.kt host/src/Main.kt -jvm-target 17 -include-runtime -d host/avatar-check.jar
java -jar host/avatar-check.jar
kotlinc host/src/AvatarIdleMotion.kt new-tests/AvatarCycleBoundaryCases.kt -jvm-target 17 -include-runtime -d evidence/cycle-boundary/red.jar
java -jar evidence/cycle-boundary/red.jar
# Then replace ONLY AvatarIdleMotion with the verified #495 blob.
kotlinc host/src/AvatarIdleMotion.kt new-tests/AvatarCycleBoundaryCases.kt -jvm-target 17 -include-runtime -d evidence/cycle-boundary/green.jar
java -jar evidence/cycle-boundary/green.jar
kotlinc host/src/AvatarContract.kt host/src/AvatarMorphMapper.kt host/src/AvatarIdleMotion.kt host/fixtures/AvatarGlbContract.kt host/src/Main.kt -jvm-target 17 -include-runtime -d host/avatar-check-final.jar
java -jar host/avatar-check-final.jar
```

The motion fixture contains only the exact TARGETS list from AvatarGlbContract blob ba4cc5c77e1c67fb8882f17ebf0b7b39bd0770d9. It replaces the org.json/Android parser for this narrow test and proves nothing about real GLB bytes. The numeric cycle tests compile the sampler directly, without a declaration fixture.

## Observed outcomes

- Initial 26-check motion run: compile 0, execute 0; 26 passed. Start 2026-11-04T19:54:46.516672Z; finish 19:55:11.421315Z.
- New five-case baseline: compile 0, execute 1; 3 passed, 2 failed. Start 2026-11-04T19:59:41.220559Z; finish 19:59:53.483282Z.
- Same five cases after exact import: compile 0, execute 0; 5 passed. Start 2026-11-04T20:00:03.996855Z; finish 20:00:15.131105Z.
- Existing 26-check runner recompiled after import: compile 0, execute 0; 26 passed.

No claim of 52 distinct motion checks. These are repeated baseline/final runs. No claim that authored Android tests have been executed.

## Raw baseline case output

```text
PASS positive cycle boundaries
FAIL negative zero: ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 116
FAIL negative cycle boundaries: ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 116
PASS near-zero finite bounds
PASS deterministic boundary sampling
RESULT passed=3 failed=2 total=5
Exception in thread "main" java.lang.IllegalStateException: Cycle-boundary acceptance failed
	at at.heinz.deviceai.avatar3d.AvatarCycleBoundaryCasesKt.main(AvatarCycleBoundaryCases.kt:71)
	at at.heinz.deviceai.avatar3d.AvatarCycleBoundaryCasesKt.main(AvatarCycleBoundaryCases.kt)
```

## Raw corrected case output

```text
PASS positive cycle boundaries
PASS negative zero
PASS negative cycle boundaries
PASS near-zero finite bounds
PASS deterministic boundary sampling
RESULT passed=5 failed=0 total=5
```

## Raw post-import motion output

```text
PASS idle gaze holds a fixation instead of continuously sweeping
PASS idle gaze moves to a new bounded fixation
PASS double blink has two closures separated by reopened lids
PASS one 460-second cycle contains 100 single and 20 extra blink closures
PASS every sampled 60-second window stays within 12 to 20 closures
PASS combined cosmetic gaze and head drift remains below six degrees
PASS same timestamp is independent of prior cosmetic frame sequence
PASS cosmetic motion is continuous across the existing clock wrap
PASS whole-cycle clock offsets preserve cosmetic pose
PASS observed gaze overrides every idle offset
PASS all non-idle runtime states retain their actual gaze
PASS reduced motion clears cosmetic motion and blinking immediately
PASS invalid time clears cosmetics without producing nonfinite values
PASS paused mapper resumes at current time without catch-up
PASS blink targets remain name-bound under reversed asset ordering
PASS both eyelids use the same bounded blink
PASS existing 150ms primary blink timing stays compatible
PASS existing 70ms speech crossfade is unchanged
PASS bilabial seal closes outgoing vowel and measured jaw immediately
PASS silence immediately closes an active mouth transition
PASS reduced motion clears speech and does not revive old vowels
PASS repeated speech time preserves the blend and rollback starts a new epoch
PASS existing reducer still rejects interrupted utterance callbacks
PASS existing observed-gaze consent clear still reaches the mapper
PASS cosmetic idle motion never invents speech weights
PASS malformed target names still fail closed
RESULT passed=26 failed=0 total=26
```

## Infrastructure boundary

The attempted direct git clone exited 128 with `Could not resolve host: github.com`. GitHub connector source reads and writes succeeded. No existing owner build service was modified or invoked. Full repository tests and configured lint remain NOT_RUN, not green. #513 author receipts remain separate inherited evidence.
