# Exact owner-memory intent routing: unquoted command words – 2026-10-26

Status: **ISOLATED REVIEW / KOTLIN FOCUSED RED/GREEN**. Not Android-Gradle or Fold7 verified.

## Reproduced defect
A directly recalled noun/topic may spell a synthesis command without being a request to synthesize. On the prior `LocalFirstPolicy.kt` Git blob `123d1d795df408db23a6d2cf1b3d8d17743de94b`, query `Was habe ich über analysiere gespeichert?` with confirmed memory and ready model incorrectly returns `LOCAL_MODEL`. Quotation-only guards cannot fix unquoted topical words.

Test written first in `app/src/test/java/at/heinz/deviceai/LocalFirstPolicyTest.kt`, commit `e167c27eb23daf40431cf41e4f76f57011deb36d`. Six memory-only and three real synthesis cases were added to the normal JUnit class.

## Source change
Commit `2aabe6979ee13c63a29e46c9570520964784f26e`, production Git blob `c1c38952f33cfa00cb38cb924ea4fba64053e794`:
- Adds an **anchored** recognition rule for direct German saved-owner-memory recall using topic data after `über`/`zu`, note placement or when an expression was written.
- Runs this narrow rule **only after confirmed memory is available** and before looking for unquoted synthesis verbs. No change to ToolGate, memory authority, local/cloud capability, model weights or generated answer text.
- Compound requests that actually demand reasoning, e.g. `Was habe ich über analysiere gespeichert und warum ist es relevant?`, stay eligible for `LOCAL_MODEL`.
- Unknown patterns fall back to existing policy; this is *not* a general German intent parser.

## Independent local Kotlin RED/GREEN
JDK21, `kotlinc-jvm 1.9.0`, JVM target17. A stand-alone Kotlin routing harness copied the relevant **exact source regex definitions and order of conditions** from the GitHub production policy:
- With the direct-recall guard removed, compilation succeeds but the very first negative case fails: `Was habe ich über analysiere gespeichert?` -> `MODEL` instead of `CORE`. Process exit1 (RED).
- With guard restored, **14/14 focused route assertions green**, including 6 unquoted direct questions, 2 quoted/dictionary recalls, 6 genuine synthesis questions.
- This **does not mean the full original Android production class was compiled**, nor does it mean JUnit/Gradle/lint/device/APK ran. New JUnit test file is source-committed and still needs actual Android Gradle verification.

## Next gate
On a properly authorized full checkout and Android SDK:
```
bash scripts/continuum-dialogue-routing-smoke.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin
```
Run Kotlin/Node cross-boundary evidence checks and inspect PR153's separate recall false-negative tradeoff before any merge. Keep PR157 Draft, separate from the GitHub-only Baby Core in PR158 and from `main`. Rollback only commit `2aabe697` and its accompanying new unit test commit if the revised matcher regresses normal requests. EXP-05, EXP-06, EXP-07, EXP-12.
