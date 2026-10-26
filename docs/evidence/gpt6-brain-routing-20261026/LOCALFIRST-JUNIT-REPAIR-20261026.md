# Kotlin test source repair – independent host RED/GREEN (2026-10-26)

## Scope and exact identities
- Repository: `Ullmann27/device-ai`, PR #157; no modification to production policy source, model weights, user data or `main`.
- Production class: `app/src/main/java/at/heinz/deviceai/LocalFirstPolicy.kt`, Git blob `123d1d795df408db23a6d2cf1b3d8d17743de94b` (unchanged).
- Broken test source before repair: `app/src/test/java/at/heinz/deviceai/LocalFirstPolicyTest.kt`, Git blob `6e8852e9fa32db3d6499222408ae6544e26ec0e0`.
- Corrected test source: Git blob `2a23a23e265c4d84a90b0cacc0a41fb1e20dc59a`; repair commits `cb90ec41d403c582432e4d22166b073e7fe03a3e` and `0cd5acb4a32de8c06af6795a12ca15a982f21a3e`.

## Reproduced regression and repair
Three literal backslash-n escape sequences occurred **outside a Kotlin string** between two `assertEquals` calls in the newly added quotation/synthesis tests. The test source was invalid Kotlin (even though the production class itself compiled). The fix restores actual statement line breaks and the complete `assertEquals(AssistantRoute.LOCAL_MODEL, …)` call. The two **inside-string** `\\n` sequences for conversation test cases are intentionally preserved.

## Independently executed host check
Environment: OpenJDK 21.0.12.1; `kotlinc-jvm 1.9.0`; `-jvm-target 17`. The downloaded GitHub source contents were reconstituted byte-for-byte locally and Git blob hashes rechecked via `git hash-object` against all three recorded identities above. The real production `LocalFirstPolicy.kt` was compiled with the exact existing `LocalModelState` enum declaration, extracted from the production model runtime.

- **RED:** broken source blob `6e8852e...`, real `kotlinc`: exit `1`, line 110 `unexpected tokens`.
- **GREEN:** repaired source blob `2a23a23...`, real `kotlinc`: success. All **8/8** test methods were executed by a local reflection runner: `0` failures.
- The host harness used narrowly scoped local **JUnit interface substitutes** for annotation discovery and `assertEquals` (no JUnit artifact available in that container); their logic compares expected/actual and throws `AssertionError`. This result verifies source compilation and the assertions' execution under this limited host harness; it **does not establish execution by the real JUnit Gradle runner**, Android SDK compile, device inference, lint, or APK release.
- Tests include direct recall, quoted synthesis tokens, explicit synthesis, long prompt retention, follow-up prompts, mode and UI state routing.

## Reproduction in full authorized checkout
Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin` with the real SDK and Kotlin/JUnit dependencies. Host-only reproduction can use `bash scripts/continuum-dialogue-routing-smoke.sh` for production route semantics; it does not on its own compile the JUnit test file.

## Next gate
Do not integrate PR #153 on the evidence of synthetic exact-match improvement alone: missed true-positive recall increased and challenge-case exact accuracy regressed. Independently rerun the frozen 60-case Node comparison from pinned Git objects, then verify Kotlin/Node behavior parity and Android tests. Preserve all sibling branches, owner signing cert and installed app. Rollback: revert only the two targeted test-file commits on this review branch. EXP-05, EXP-06, EXP-07, EXP-12; status: host source repair VERIFIED, Android `NOT_RUN`.
