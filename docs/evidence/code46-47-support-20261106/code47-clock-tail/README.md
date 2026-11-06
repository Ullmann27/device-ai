# Code47 time-anchor contract — narrow verification (2026-11-06)

Source baseline: primary Code47 `9ee7535bb131504cc85419afd30f446089a40171`; support change `492f1ad2bd5091351c1090080663d6f4201d905f` in Draft PR #577.

## Reproduced full SDK failure

The unmodified earlier Code47 source `f26d1063b25c65c22b8d411789d1733c92e3ad72` compiled real Android production and test code, but the complete `:app:testDebugUnitTest` failed 1/3577 (20 skipped). Its sole failure was `LocalPromptSizeAndStabilityTest.timeAnchorIsInTheRequiredTailNotInTheStaticPrefix`; lint was not reached. Original XML is in `../code47-baseline/`.

The outdated assertion searched for the time anchor directly inside `LocalPromptFrame.assemble`. Code47 M1 now builds `promptEnd` with `LocalClockIntent.promptAnchor`, passes it as `requiredTail=promptEnd` into `LocalDecisionPromptFrame.assemble`, and the wrapper passes `requiredTail` to both `LocalPromptFrame.assemble` calls. This is intentional, not a missing clock.

## Test-only change

Replace the stale direct-call assertion with a source-wiring check that requires: no clock in static `promptBody`; clock present in the `promptEnd` construction; the actual owner call forwards `promptEnd` as `requiredTail`; and both wrapper calls forward `requiredTail` to `LocalPromptFrame`. A second regression test deliberately detaches the owner tail, removes the clock and drops one wrapper tail. All must be rejected by the same structural checker.

## Actually executed (limited)

JavaScript source checks against the exact fetched GitHub Activity and wrapper strings: actual wiring accepted; three mutations rejected (4/4 expected outcomes). Kotlin 1.9.0/JRE 21 syntax-and-behavior smoke of the checker against copied representative source excerpts: `KOTLIN_1_9_POSITIVE=1 NEGATIVE_REJECTED=3 RESULT=PASS`. This smoke uses excerpts and is **not** an actual Gradle/JUnit execution of the checked-in full test class. No production files changed in this correction.

## Required final gates

After integrating M1 conflict fix, M2 dispatch-start correction and this test-only update on the exact eventual Code47 candidate: rerun full Android/JVM + UiTest/Node/backend/lint plus ARM64 assembly, preserving the strict gates. No Code47 APK, original signing, model/Fold7 GPU/audio or runtime improvement has been verified by this test correction.

Rollback: revert only the time-anchor test change from commit `492f1ad2bd5091351c1090080663d6f4201d905f` if its contract proves incorrect. Do not alter original Code46 `577f0931e7937ffcac02dc855a540f093facdb3b` or the original publisher.
