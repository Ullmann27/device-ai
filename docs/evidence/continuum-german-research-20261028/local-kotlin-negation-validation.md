# Continuum German-source contradiction improvement – verified narrow JVM probe

Datum: 28.10.2026. Source branch: `chatgpt/continuum-german-research-contradiction-20261028`, stacked on Draft PR #204 → #203.

## Genuine local execution (no Android SDK)

The **exact Kotlin source** `app/src/main/java/at/heinz/deviceai/cognitive/ResearchNegationSignals.kt` was copied to an isolated local test directory and verified using `git hash-object`. The actual GitHub file independently reports git blob SHA **`0fe6e121782be6a630c3a9edf0e5f63901dfa715`**, matching the local compiler input byte-for-byte.

Toolchain: local **kotlinc-jvm 1.9.0 / OpenJDK 21**; these are **not** the required project's Android JDK17/Gradle8.11.1/SDK36 setup. This is an isolated helper compile only.

Executed:
```text
$ git hash-object ResearchNegationSignals.kt
0fe6e121782be6a630c3a9edf0e5f63901dfa715
$ kotlinc ResearchNegationSignals.kt ResearchNegationLocalCheck.kt -include-runtime -d negation-check.jar
EXIT_CODE=0
$ java -jar negation-check.jar
PASS 1/9
PASS 2/9
PASS 3/9
PASS 4/9
PASS 5/9
PASS 6/9
PASS 7/9
PASS 8/9
PASS 9/9
EXIT_CODE=0
```

The nine deterministic checks cover German `nicht` and `keinen`, English `not` / `doesn't`, same-polarity identity, and `nicht nur` not being misclassified as an explicit reversal. These are **simple lexical test inputs**, NOT an independent quality evaluation.

## Production code correction

- `ResearchPipeline.research`: explicit contradiction is evaluated **before** `similarity >= 0.7` support. Previously a claim and its near-identical German negation could be incorrectly counted as independent support and eligible for `ValidationState.VERIFIED`.
- `DeepResearchEngine`: uses the same limited cross-language negation test for graph conflict and `ValidationState.CONTRADICTED`. Existing source-domain independence checks remain.
- Added real project JUnit tests under `ResearchNegationSignalsTest.kt`, `ResearchTest.kt`, `ResearchEngineTest.kt` for this regression, including source conflict/negative-memory nonpromotion. **Those full Android-project JUnit tests have NOT yet been run here**; their presence is not a passing test report.

The helper remains lexical, cannot infer negation scope or truth across arbitrary German grammar. No automatic source trust, model weight, memory, permissions or tool access changes; no direct private owner information is used.

## Owner-APK status

The local owner builder exists (`scripts/continuum-local-owner-apk.sh`), targets the **same installed app package** `at.heinz.deviceai.mentorpaired` with incremented versionCode, requires exact SHA approval, full JUnit/lint/build, and confirms original certificate SHA-256 `2b05fa9de378fb6c6c5376143774a573897dc9c9151305f1c44010275c76a9cc`.

**BLOCKED** for owner-installable APK: the private original signing P12/password and the authentic Android SDK36/JDK17/Gradle8.11.1 build checkout are unavailable in this session; Fold7 filesystem is inaccessible. Previous user screenshot shows Termux at home without a Git repository. No APK has been generated, downloaded, signed, installed or published, and no measurable device/search quality gain has been demonstrated. Never use disposable debug signing as a purported in-place owner update; never uninstall the existing owner app to work around signature mismatch.

**Next valid gate:** in the existing authenticated Termux/Linux source checkout, run PR #204's unfiltered `:app:testDebugUnitTest` and validate all original XML reports. Then run the real main-app QA builder, and only with locally present original P12/password and owner-approved source SHA perform the existing owner signed update. Validate actual installed versionCode before release. Fold7 model inference and a baseline-versus-candidate web-research contradiction-quality comparison follow separately.

Rollback: keep PR #205 Draft, do not merge into main or #203 without actual full regression and owner review.