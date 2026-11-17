# Cinema QA — 2026-11-10

Validated app source: local commit `e2fb22d71464415ac4438fea7f53558a25a6558f`, tree `88888ff449a420a4c39bab4b85fbc065efca37b5`; app subtree `1a516b2c1d059d24663d6cb5ff279edfd975de07`. The publishing agent mapped this tree to PR #715 commit `5d98a14273a5372f4502eeb4b37fa075918582ec`. Later documentation or preview-only changes do not extend these results to changed app code.

| Check | Result | Scope |
| --- | --- | --- |
| Android `:app:compileDebugKotlin` | PASS | Full production Kotlin; Android SDK 36; 2m 18s |
| Gradle selected JUnit/Robolectric | PASS, 56/56 | Six classes below; no failures, errors or skipped cases |
| Standalone Android Lint | PASS, 0 issues | Existing `NewApi` check only; 2m 10s |
| Host JUnit | PASS, 33/33 | Entrance + fluid-light; overlaps Gradle cases |
| Node contracts | PASS, 18/18 | Five focused Cinema/layout/original-orb files |
| SDK native-source compile | PASS | Actual overlay, AGSL bridge and Android painter; also covered by full Android compile |
| Full test suite | NOT_RUN | Only selected runtime tests were run |
| GPU / Filament runtime / Fold7 / thermal measurement | NOT_RUN | Compilation and software previews are not device acceptance |
| APK / protected signing | NOT_RUN | No packaging or signing requested or performed |

Gradle selection: `JarvisCinemaEntranceTest` 29, `JarvisFluidLightTest` 4, `CinemaWindowGazeBridgeTest` 8, `SpatialWindowLightTest` 5, `JarvisRenderQualityTest` 7, `JarvisCinemaReplayUiTest` 3. The three UI lifecycle cases used Robolectric SDK 28 and the actual `JarvisShellView`; they exercised pending orders during return, cancellation of an ended pending order, and an early result followed by stop. They are not GPU rendering tests.

`gradle-selected-tests.json` contains all testcase names and raw XML digests without environment properties. `tested-app-sources.json` binds all 15 changed app files to SHA-256 values. Original assets and `app/build.gradle.kts` are unchanged against Code 52 commit `17491f4d8103f0e55836369333fab07e80e6e9c2`.

The original combined Gradle invocation completed the selected test task and wrote six passing XML reports, then lost its daemon during `lintAnalyzeDebug`. Its overall exit was nonzero; it is not recorded as a green combined build. Initial toolchain attempts also encountered proxy configuration and memory pressure. Toolchains/caches were moved from the RAM-backed `/tmp` filesystem to disk; the full compile then passed. The separate lint-only retry passed with bounded heaps and reported zero issues. No source workaround was used for these environment issues.

## Reproduction

Tool versions: Temurin JDK 21.0.13, Gradle 8.11.1, Android Gradle Plugin 8.10.0, Android platform 36, build-tools 35.0.0. Host-only previews/tests additionally used Kotlin 2.4.0, JUnit 4.13.2 and Hamcrest 1.3. Set `JAVA_HOME`, `ANDROID_HOME` and `GRADLE_USER_HOME` to the installed tools. This environment additionally needs its current proxy and CA properties; no credentials or machine-specific proxy endpoints are included here.

Compile:

```sh
gradle --no-daemon --max-workers=1 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx2400m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumOwnerLocalQa=true \
  -PcontinuumOwnerLocalVersionCode=52 :app:compileDebugKotlin
```

Selected tests (the original run also appended `:app:lintDebug`):

```sh
gradle --no-daemon --max-workers=1 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx2400m -XX:MaxMetaspaceSize=512m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumOwnerLocalQa=true \
  -PcontinuumOwnerLocalVersionCode=52 -PcontinuumUiWidgetTests=true \
  :app:testDebugUnitTest \
  --tests '*JarvisCinemaEntranceTest' --tests '*JarvisFluidLightTest' \
  --tests '*CinemaWindowGazeBridgeTest' --tests '*SpatialWindowLightTest' \
  --tests '*JarvisCinemaReplayUiTest' --tests '*JarvisRenderQualityTest'
```

Isolated lint retry:

```sh
gradle --no-daemon --max-workers=1 --console=plain \
  -Dorg.gradle.jvmargs='-Xmx1100m -XX:MaxMetaspaceSize=384m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Pandroid.experimental.lint.heapSize=1400m \
  -PdeviceAiMentorPreview=true -PcontinuumOwnerLocalQa=true \
  -PcontinuumOwnerLocalVersionCode=52 -PcontinuumUiWidgetTests=true \
  :app:lintDebug
```

Node selection:

```sh
node --test \
  .github/continuum/code52-cinema-source-contract.test.mjs \
  .github/continuum/code52-cinema-prism-contract.test.mjs \
  .github/continuum/fold7-portrait-reference.test.mjs \
  .github/continuum/brain-orb-render-allocation.test.mjs \
  .github/continuum/owner-cockpit-voice-original-orb.test.mjs
```

Independent research branch: local `d97026b86e7f47cae160d0cb14bfc9c49936cda6` passed 106 context/voice cases with `scripts/continuum-context-cinema-check.sh`. These changes and results are separate from the Cinema app tree above.
