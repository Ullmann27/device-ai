# Single navigation, diagrams and refined icons — verification

Verified application/test source: [`101c0810334a347001a7b50da3faff378987ff00`](https://github.com/Ullmann27/device-ai/commit/101c0810334a347001a7b50da3faff378987ff00).
Review: [PR #597](https://github.com/Ullmann27/device-ai/pull/597), stacked on PR #594 at `88dfd45139471463618673a1f827ee062e9bf6d5`.

## Actual result

- **252/252 focused tests PASS**; zero failures, errors or skipped tests.
- Real local Android SDK compilation; Gradle 8.11.1 / JDK 17 / compile SDK 36. `:app:testDebugUnitTest` finished successfully in **11m 3s**.
- **150/150 standalone production Kotlin geometry cases PASS**, with source hashes verified against the published application source.
- The original wide rail, all compact routes, native touch and accessibility, resize, scrolling, work-window origin, deck clearance, lifecycle and original avatar identity are covered.
- Native Activity/Canvas images were visually reviewed for the owner Fold aspect, tall reference aspect, compact phone and enlarged text. The thinner icons remain visible; saturated borders retain the glow.

`test-results.json` contains every executed test name and result. `source-sha256.json` identifies all 26 changed application/test files. `gradle-result.txt`, the exact filter, executable geometry harness and capture metadata accompany this receipt. The earlier parent-directory 239-test receipt is preserved as historical evidence and is not substituted for this result.

Selected groups:

| Group | JUnit tests | Result |
| --- | ---: | --- |
| Native work-window system, origin and deck restoration | 10 | PASS |
| Native navigation, real touches, accessibility and resize | 8 | PASS |
| Wide reference interaction profiles | 2 | PASS |
| Deterministic actual Activity captures | 4 | PASS |
| Real local-model/measurement presentation | 7 | PASS |
| Original avatar framing | 10 | PASS |
| Native card and rail font checks | 2 | PASS |

The card font method includes 30 actual compact-card cases and six explicit wide layouts without duplicate cards. The rail font method includes nine profiles. These internal cases are not added to the 252 JUnit count. The wide reference methods traverse normal/150%/200% text and a shortened scrollable rail. The other executed groups remain fully listed in the JSON receipt.

## Verified behavior

Wide layouts expose eleven rail destinations and no `TILES` slot/native tile controls. The main order is input, memory/model charts, agents/process, status, then camera/modules. Narrow phones retain ten feature cards and their previous order. Large fonts use fewer information columns and real scrolling.

The device card reads existing app-CPU history, RAM usage and battery temperature. Missing readings stay unknown. Memory bars represent observed events since Activity startup; they do not fabricate a full-day history. Work windows emerge from the real rail control on wide layouts or the real card on compact layouts.

## Native capture identity

The adjacent JSON files record the exact source, PNG hash, density, viewport, actual view bounds, navigation mode and real scroll offsets. PNG previews were delivered separately; original user references and private application data are not published.

- `activity-fold-1384x1536`: actual Activity, 692×768dp at density 2; rail-only navigation and diagrams in the former duplicate card area.
- `activity-fold-1384x1536-scrolled-diagnostics`: same Activity after a real **599px / 299.5dp** dashboard scroll; all four diagnostic panels are visible. Actual startup events become available between the two frames.
- `activity-reference-840x1050`: taller main composition.
- `activity-compact-412x915`: compact navigation retains the feature cards with the new icon treatment.
- `activity-medium-832x748-font200`: 200% text reflow, still without duplicated wide cards.

Captures use Robolectric API 28 native Canvas and explicitly select the existing **2D head**. The original 3D asset and orb were preserved. Physical Fold, native Filament/GPU and thermal acceptance are **NOT_RUN**. The phone hero title still ellipsizes in its narrow header; navigation labels are independently tested. No APK was assembled, signed or installed, and no main merge/release occurred.

## Reproduction

Use the verified source with the existing SDK, dependency and model-asset cache. This evidence-only commit supplies the filter and harness; place them outside the source checkout when reproducing the exact source commit.

```sh
CONTINUUM_CAPTURE_SOURCE_COMMIT=101c0810334a347001a7b50da3faff378987ff00 gradle \
  --offline --no-daemon --max-workers=2 --console=plain \
  '-Dorg.gradle.jvmargs=-Xmx3000m -XX:MaxMetaspaceSize=768m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumStandaloneVersionCode=49 \
  -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumUiEvidenceDir=/absolute/evidence/output \
  -I /absolute/evidence/focused-tests.init.gradle \
  :app:testDebugUnitTest

kotlinc app/src/main/java/at/heinz/deviceai/ui/JarvisCockpitGrid.kt \
  app/src/main/java/at/heinz/deviceai/ui/JarvisShellLayout.kt \
  app/src/main/java/at/heinz/deviceai/ui/JarvisWindowProfile.kt \
  /absolute/evidence/SingleNavigationCheck.kt \
  -include-runtime -d /tmp/single-navigation-check.jar
java -jar /tmp/single-navigation-check.jar
```

The existing QA task allowlist remains active. No CLI `--tests` selector, GitHub Actions or paid build workflow was used. No runtime timer, external icon dependency, permission gate, package or version contract was added or changed.
