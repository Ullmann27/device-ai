# R5 source-bound RED — 5 November 2026

Base public commit: `00049e5c76b30e3a3587755a65ac6a30a693eb8a` (#529 final evidence checkpoint).
Product file: `app/src/main/java/at/heinz/deviceai/cognitive/AiContextCompiler.kt`.
Original production blob: `0e4718176518131828688092ea95e894a1d4c2fa`.
The exact private `redactSecrets(text: String)` method was extracted, unchanged, into a standalone Kotlin class for **host-only** execution. Local copy was compared with the GitHub method using the 32-bit FNV-1a over every UTF-16 code unit: length 2248, FNV `e80709ad` on both sides. This identity check covers **only this method**, not the complete class/Android application.
Frozen Android-project regression tests were committed **before** any production edit at `9e2fbbde4e35b4e4f6d0c87dbe8442bb13d863bf`.

Environment: Linux container, `kotlinc-jvm 1.9.0`, JRE 21; no Android SDK or project Gradle run.
Actual host commands: `kotlinc RedactorSource.kt RedactorProbe.kt -include-runtime -d probe-red.jar` then `java -jar probe-red.jar`.
Compile exit: 0. JVM exit: **1 (expected RED)**.
Tested synthetic values only; no real credentials.

| Case | Original |
| --- | --- |
| Standalone Bearer followed by LF + `Nevermore` | FAIL: next-line `Nevermore` removed |
| Standalone Bearer followed by CRLF + `Nevermore` | FAIL: same loss |
| Standalone same-line Bearer token | PASS: token removed |
| JSON `access_token` | FAIL: test credential remains |
| `refresh_token` | FAIL: test credential remains |
| `client_secret` | FAIL: test credential remains |
| `session_token` | FAIL: test credential remains |
| Explicit authorization Bearer | PASS |
| Legacy x-token | PASS |
| Unrelated `access_tokenizer` | PASS |

**Observed total: 10 host cases, 4 PASS / 6 FAIL.** The line-loss mechanism is the last regex's `\\s+`, which matches a line break followed by a token-like ordinary word. The underscore keys are absent from the first assignment-key group, and `\\btoken` cannot begin at an underscore within the key.

Only the R5 specialist branch will receive a minimal production-source change. C1/#519 already owns the shared intake of #529; this stacked patch must be independently reviewed and integrated **only once** by C1. Do not change Activity, factory, renderer, app package, signing, version, owner records or model files.

Limit: this RED is an actual Kotlin execution of a verified identical production **method**, not a full Android project or a live Fold7/privacy certification. The five new Android JUnit methods have **NOT_RUN** status on the original product source here; they are frozen for C1's real project gate.
