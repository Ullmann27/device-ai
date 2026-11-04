# Bot 02 — local build inventory

Date: 2026-11-04. Read-only inventory; no foreign checkout, cache, daemon or
environment configuration was modified.

The inspected build declares Gradle 8.11.1, Android Gradle Plugin 8.10.0,
Kotlin 2.4.0, compile/target SDK 36, minimum SDK 26 and JVM target 17.
The relevant Android tasks are `:app:assembleDebug`, `:app:lintDebug` and
`:app:testDebugUnitTest`; UI widget tests require `-PcontinuumUiWidgetTests=true`.

The default PATH has Node 24.22.1 and npm 11.9.0. The default Java is a JRE 21;
absence of javac/Gradle/adb in that PATH is not absence of the separately installed
build environment. The coordinator directly read
`/workspace/scratch/391c0f6de827/build-env/run_gradle.py`, which selects the bundled
JDK17, SDK, Gradle 8.11.1 and its own Gradle cache. The pure Kotlin/JUnit baseline
was actually compiled and run through those local tools (see BASELINE.md).

The inherited coordination contract serializes Gradle with
`/workspace/scratch/b3170a7ae924/gradle-build.lock`. Bot 02 did not execute Gradle
or prove the complete offline dependency cache. No build from another checkout
may be used as acceptance for this support source.

The unrelated memory worktree already contained an AiContextCompiler edit and
was left unchanged. The earlier 2,371-test report is historical documentation,
not a new support-team result. Fresh support baseline: 45 Node PASS and 8 pure
Kotlin JUnit PASS. Full Android compilation, lint, device and APK gates remain
separate and unverified here.
