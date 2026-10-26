# Verified recall → local model handoff: safe oversized fallback

Status: START_CHECKPOINT / TEST_FIRST_PEND.
Date 2026-10-26.
Exact parent: PR161 source commit `4b569515bb3f43abccf008e1889eac68d570a105`.
Repo: Ullmann27/device-ai, working branch `integration/continuum-grounded-recall-fallback-20261026`.

## Existing real flow
`AgentActivity` receives a confirmed dialogue reply and an explicit synthesis request; `LocalFirstPolicy.afterMemory` selects `LOCAL_MODEL`. For grounded replies, `AgentActivity` currently directly constructs `LocalGroundedRecall(answer,evidence)` on the Android UI thread. The immutable data object requires `answer.isNotBlank() && answer.length<=4000` and `provenance.isNotBlank() && provenance.length<=2000`. Neither is checked at the UI call site. An out-of-bounds retrieved answer/provenance can therefore throw `IllegalArgumentException` in the success handler, losing the grounded answer despite valid local memory.

## Required minimal behavior
Do NOT silently truncate a confirmed memory to trick the LLM into inference. If the bounded grounding handoff is not representable, keep the original direct grounded answer visible and surface a clear local-model skip status. Never promote model output to owner fact. Preserve existing cancellation/session identity, chat safety and no external network.

TDD: add new Kotlin JUnit oversized/blank/valid cases to `LocalGroundedRecallTest.kt` for a safe factory; then implement the factory and update the single `AgentActivity` synthesis call site. Tests and source review before any merge. Real Android Gradle, Fold7, and APK remain NOT_RUN unless executed.

Experiments EXP-05/06/08/12. Review-only, no main changes or owner-data writes; rollback via targeted revert of this branch's source commits.

## Published source fixes / source verification

- Kotlin tests FIRST: `app/src/test/java/at/heinz/deviceai/cognitive/LocalGroundedRecallTest.kt`, Git blob `0ad00630bf3c51504705f4f8ce03c195b740e1e8`, commit `48f2b40def61e215400ef70cd50b56ea6391df1d`. Added exact boundary/one-over/blank and unmodified-content assertions.
- Factory: `LocalGroundedRecall.kt`, Git blob `1628c9552bbbb861b8e41dea37c07215ea816ed9`, commit `9887ff069d5d8f3d2034578cb61134073a02b62b`. Data class immutability, strict `init` bounds and JSON serialization unchanged. New `boundedOrNull()` declines unsafe/incomplete evidence without altering the retrieved owner facts.
- Android UI glue: `AgentActivity.kt`, Git blob `86c87e31f608bfafb5817a328709bdcb058de438`, commit `75d293475316fdfdb29704aa01a154d604234d41`. If the existing model route requests synthesis on confirmed memory, create a bounded envelope first. On failure display exactly the original `answer` with an explicit skipped-analysis audit note; never call local model with partial memory. On success call the old `runLocal(request,modelQuestion,recall)` with original source/evidence and existing model/toolgate authority. No new memory database, remote service or worker.
- One new source-level wiring regression: `.github/continuum/grounded-recall-android-wiring.test.mjs`, blob `8db5b9a0cf8710957d0f5b7d2d17ca5eff972555`; direct source callback executed in V8 using exact GitHub production sources: 1/1 PASS. This is **not** a complete Node CLI run.

## Independent JVM RED/GREEN – exact verified bytes

In the local JVM21 / Kotlin compiler 1.9.0 `-jvm-target 17` validation workspace:
- An exact reconstructed **baseline** `LocalGroundedRecall.kt` Git blob `f70ef3c5d6b3ad27ebdb025bd8658373703fe17f` failed compilation of the new test invocation: `unresolved reference: boundedOrNull` (exit1), as expected.
- A reconstructed **candidate** `LocalGroundedRecall.kt` Git blob `1628c9552bbbb861b8e41dea37c07215ea816ed9` matches GitHub's blob identity and compiled. Seven focused factory assertions PASS.
- The real, unmodified test file Git blob `0ad00630bf3c51504705f4f8ce03c195b740e1e8` was compiled and all **3/3 actual test method bodies PASSED** on the JVM using minimal **test-only JUnit and JSONObject shim classes** in lieu of the unavailable Android Gradle classpath. This is not the original Android Gradle/JUnit runner; the existing JSON-render test was exercised against the shim and retains its usual real-Android test requirement.
- The first manual JSON shim incorrectly decoded escaped newline and quotes, yielding a red test failure; correcting the **test shim only** made all 3 actual methods green. No change to production JSON code or GitHub source.

## Remaining gates
- Full `:app:testDebugUnitTest`, `:app:lintDebug`, real app compile and Fold7 on-device test remain **NOT_RUN** at this branch SHA.
- Paired PR161 source-commit callback gate also awaits Android SDK and device validation.
- No real new model inference, arbitrary coding program, APK signing/installation or learning/weight promotion has taken place.

Resume: full checkout of this exact branch, run `node --test .github/continuum/grounded-recall-android-wiring.test.mjs`, `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin`, and only then planned on-device and owner-signing gates. Roll back this branch independently on failure.
