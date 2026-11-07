# Dark reference layout: verification

Verified application and test source: [`3b0e4dd2f40575561b87bd84327caea0a8e63eb9`](https://github.com/Ullmann27/device-ai/commit/3b0e4dd2f40575561b87bd84327caea0a8e63eb9).
Base: PR #594, `88dfd45139471463618673a1f827ee062e9bf6d5`. Change review: [PR #597](https://github.com/Ullmann27/device-ai/pull/597).

## Actual result

- Gradle: **BUILD SUCCESSFUL**, 1m29s, 239 tests, zero failures, errors or skipped tests.
- Main Kotlin and Android unit-test sources compiled with the real local Android SDK; offline Gradle 8.11.1 / JDK 17 / compile SDK 36.
- Native Canvas/UI runs use Robolectric API 28 and the bundled fonts. They are host results, not a physical Fold or Filament/GPU result.
- `JarvisFeatureCardReadabilityUiTest` includes 90 card/font/window cases inside its one JUnit method. The rail font method covers 9 profiles. Those internal cases are not added to the 239 JUnit test count.
- The independent executable Kotlin geometry harness passed 150 window/font/compact combinations. Its three source hashes match the verified application source.

Selected groups from the complete machine-readable `test-results.json`:

| Group | JUnit tests | Result |
| --- | ---: | --- |
| `ContinuumReferenceCockpitUiTest` | 1 | PASS |
| `JarvisDeterministicCaptureUiTest` | 16 | PASS |
| `JarvisReferenceHitTargetsUiTest` | 8 | PASS |
| `AvatarFramingTest` | 10 | PASS |
| `CockpitCinematicCueUiTest` | 10 | PASS |
| `FullStageMemorySceneUiTest` | 1 | PASS |
| `JarvisFeatureCardReadabilityUiTest` | 1 | PASS |
| `JarvisHeaderBrandFitTest` | 3 | PASS |
| `JarvisLivingSceneTest` | 6 | PASS |
| `JarvisRailLabelReadabilityUiTest` | 1 | PASS |

The selected `ContinuumReferenceCockpitUiTest` method traverses every real navigation target at font factor 2 after a height-only resize. An earlier broader run at `b12acea2d4fb2dcea5c100b12fed4c6c09436864` passed all 12 methods of that class, but had two unrelated layout assertion failures among 247 total tests. Those exposed a header-caption truncation and the obsolete dialog/head relationship. Both are corrected and their tests pass in this verified source. The broader earlier result is not substituted for current-source verification.

## Reproduction

Use an isolated checkout of the verified source, the existing SDK/dependency/real-model-asset cache, and the filter beside this receipt. The filter is supplied outside the source checkout when reproducing this source commit because it is checked in by the later evidence-only commit.

```sh
CONTINUUM_CAPTURE_SOURCE_COMMIT=3b0e4dd2f40575561b87bd84327caea0a8e63eb9 gradle \
  --offline --no-daemon --max-workers=2 --console=plain \
  '-Dorg.gradle.jvmargs=-Xmx3000m -XX:MaxMetaspaceSize=768m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumStandaloneVersionCode=49 \
  -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumUiEvidenceDir=/absolute/evidence/output \
  -I /absolute/evidence/focused-tests.init.gradle \
  :app:testDebugUnitTest
```

The existing QA task allowlist remains active. No CLI `--tests` selector is used because the project interprets such selector arguments as tasks at configuration time; the init-script filter uses the permitted test task. No GitHub Actions or paid build service was invoked.

The geometry harness runs the real three Kotlin geometry sources, then checks bounds, nonoverlap, ten feature destinations, separate minimum 48dp input controls and diagnostics below the primary status. Its command is:

```sh
kotlinc app/src/main/java/at/heinz/deviceai/ui/JarvisCockpitGrid.kt \
  app/src/main/java/at/heinz/deviceai/ui/JarvisShellLayout.kt \
  app/src/main/java/at/heinz/deviceai/ui/JarvisWindowProfile.kt \
  /absolute/evidence/LayoutCheck.kt -include-runtime -d /tmp/layout-check.jar
java -jar /tmp/layout-check.jar
```

## Native capture identity

PNG files were delivered with the task; the adjacent JSON records their exact hashes, source, real view bounds, density and scroll offsets. No original user reference image or personal application dataset is published.

- `activity-fold-1384x1536`: actual Activity at 692x768dp, density 2, scroll 0. Tall 5x2 cards fill the primary area; the status strip continues below the viewport.
- `activity-fold-1384x1536-scrolled-diagnostics`: same Activity, real dashboard scroll 1283px / 641.5dp. All four diagnostic cards are visible after the main status strip.
- `activity-reference-840x1050`: taller window showing the whole primary composition.
- `activity-compact-412x915` and `activity-medium-832x748-font200`: narrow and enlarged-type evidence.

The head in these captures is the explicitly selected existing 2D renderer. The application's original 3D model, original orb and their runtime identity are preserved, but a native Filament/device rendering result is **NOT_RUN**. Isolated empty test storage is allowed to record genuine application bootstrap events; no model-ready or other green status is invented.

No APK was assembled, signed or installed. No main merge, package/version mutation, release, live service action or physical-device thermal/performance result is claimed.
