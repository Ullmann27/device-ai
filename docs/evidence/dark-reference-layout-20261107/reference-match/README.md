# Dark reference correction — source-bound host evidence

## Source and result

- Review: [PR #597](https://github.com/Ullmann27/device-ai/pull/597), branch `codex/continuum-dark-reference-layout-20261107`.
- Stacked base: PR #594 at `88dfd45139471463618673a1f827ee062e9bf6d5`.
- Application/capture source: [`60ec75e3fc468b6cf96237a338f3f6388e6bcaf0`](https://github.com/Ullmann27/device-ai/commit/60ec75e3fc468b6cf96237a338f3f6388e6bcaf0).
- Final source/test commit: [`64609f65c319a97dacacd7608d541fbc196c0265`](https://github.com/Ullmann27/device-ai/commit/64609f65c319a97dacacd7608d541fbc196c0265).
- Status: **HOST_VERIFIED_WITH_TARGETED_RETRY**. Native/host compilation and the selected cases have passing evidence. Physical-device and Filament/GPU acceptance remain **NOT_RUN**.

The final commit changes exactly one assertion in `JarvisCockpitGridTest.kt`: the preferred subtitle line count is three, matching the reference, rather than the historical two. `app/src/main` is byte-identical between the two commits. The older 239- and 252-test receipts describe earlier layouts and do not certify this correction.

| Run | Source | Result | Duration |
| --- | --- | --- | --- |
| Coordinated native/JVM verification | `60ec75e3` | 267 executed, 266 passed, one stale subtitle assertion failed | 9m 26s |
| Entire affected geometry/content class after assertion correction | `64609f65` | 7/7 passed, `BUILD SUCCESSFUL` | 15s |
| Independent production Kotlin geometry harness | Hashes in this directory | 150/150 combinations passed | See harness log |

There are **267 distinct cases with passing evidence across the two Gradle runs** and **274 total executions**. This is not a claim that the coordinated run itself was green. Its failure is retained in [test-results.json](test-results.json) and [coordinated-result.txt](coordinated-result.txt); the successful correction is retained in [subtitle-fix-result.txt](subtitle-fix-result.txt). No application source changed after the coordinated run. Repeating unrelated native tests was unnecessary for this test-only one-character correction.

## Covered behavior

- Ten real feature destinations, no fixed Home rail, normal scrolling and large-text reflow.
- Nine live-tab cases: real touch routing, current values, unavailable/invalid readings, no time-derived telemetry, finite animation, reduced motion, scale zero, lifecycle closure and resize continuity.
- Two work-window host cases: the actual clipped viewport and the visible tapped-card origin, without resetting the dashboard's scroll position.
- Phone, Fold-like, shortened-window and large-text navigation, appearance changes, working deck clearance and existing protected avatar/dialog/orb regions.
- Shared shell/hologram/telemetry motion controls and existing work/document/memory/planning routes.
- Native card readability: 90 card/font combinations inside the dedicated case, including 200% text.
- Six deterministic Android Activity/native Canvas captures and two existing visual-evidence scenarios.

The three requested helpers independently covered geometry, visual surfaces/typography and native routing/motion. Earlier runs found posted-click fixture timing, a transient native-width drawer dismissal, native font-hinting overflow and the scroll-reset origin regression. Their fixes preserve real touch dispatch and existing accessibility/geometry constraints.

## Capture fidelity

The PNGs were rendered by the actual Activity hierarchy with native Robolectric graphics. They deliberately select the application's **existing 2D fallback head** because this host cannot validate Filament JNI/GPU output. They are neither a browser recreation nor a composite using the Blender portrait. Native startup state supplies the values: missing telemetry stays unknown, and no ready/stable state was invented to copy the reference.

| Metadata | Purpose |
| --- | --- |
| [activity-reference-864x1536.json](activity-reference-864x1536.json) | Reference aspect, complete primary composition and start of diagnostics |
| [activity-fold-1384x1536.json](activity-fold-1384x1536.json) | Owner screenshot aspect and actual initial viewport |
| [activity-fold-1384x1536-live-system.json](activity-fold-1384x1536-live-system.json) | Actual expanded system drawer, native controls and current values |
| [activity-fold-1384x1536-scrolled-diagnostics.json](activity-fold-1384x1536-scrolled-diagnostics.json) | Scrolled memory/model diagrams and secondary state |
| [activity-compact-412x915.json](activity-compact-412x915.json) | Compact phone viewport |
| [activity-medium-832x748-font200.json](activity-medium-832x748-font200.json) | Medium window with 200% text |

Each record contains the exact source commit, PNG SHA-256, actual geometry/scroll and explicit `native_filament_verified: false` / `physical_device_verified: false`. The production sources are unchanged at the final test commit. The previews are supplied separately to the owner; no private preview identifiers are published here.

## Original 3D asset

The packaged `app/src/main/assets/avatar3d/continuum-avatar.glb` is unchanged from the base: 12,038,416 bytes, SHA-256 `c8b88385c73fa01596a5734d20859440b1ecaef06cb1abea6cd7a47e62b08b9a`.

[actual-glb-host-render.json](actual-glb-host-render.json) records the separate Blender 4.3.2 Cycles CPU render: 512×704, 12 samples, the real packaged rig and a bounded Fog Glow compositor. Its PNG SHA-256 is `4e011bc0c79903e3c084fce79285dd3499ae89024243d0aab0d76666ae5be9a9`. This verifies the available realistic cyan/magenta portrait asset, not Android bloom, physical-device performance or pixel identity with the supplied reference.

## Reproduction

Use the existing provisioned JDK 17, Gradle 8.11.1, Android SDK 36 and cached native Robolectric dependencies. The actual run used the environment's established SDK/trust setup and `GRADLE_USER_HOME=/workspace/scratch/3d1967be6193/android-gradle-cache`.

For the coordinated scope, use [focused-tests.init.gradle](focused-tests.init.gradle). The original run exported `CONTINUUM_CAPTURE_SOURCE_COMMIT=60ec75e3fc468b6cf96237a338f3f6388e6bcaf0`. At another commit, set this variable to that actual source SHA so capture metadata stays truthful.

```sh
gradle --offline --no-daemon --max-workers=2 --console=plain \
  '-Dorg.gradle.jvmargs=-Xmx3000m -XX:MaxMetaspaceSize=768m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PdeviceAiMentorPreview=true -PcontinuumStandaloneVersionCode=49 \
  -PcontinuumUiWidgetTests=true -PcontinuumLocalStandaloneQa=true \
  -PcontinuumUiEvidenceDir=/absolute/path/to/evidence \
  -I docs/evidence/dark-reference-layout-20261107/reference-match/focused-tests.init.gradle \
  :app:testDebugUnitTest
```

The targeted retry uses the same flags, omits the unused evidence-directory property and substitutes [subtitle-fix.init.gradle](subtitle-fix.init.gradle). It was run at final source/test commit `64609f65c319a97dacacd7608d541fbc196c0265`.

[RailFreeReferenceCheck.kt](RailFreeReferenceCheck.kt), [rail-free-reference-check.log](rail-free-reference-check.log) and [rail-free-reference-source-sha256.txt](rail-free-reference-source-sha256.txt) preserve the separate geometry check. The three production-source hashes still match the final code. [source-sha256.json](source-sha256.json) lists the changed application/test file hashes for the final source/test commit.

## Integration boundary

Only EXP-05/EXP-11 presentation and native controls are changed. Original model/memory/worker authority, CCC1/ToolGate permissions, package/version and signing contract remain intact. No GitHub Actions, APK assembly/signing/install, release, main merge or paid workflow was performed. The owner reference remains the visual target; exact on-device appearance still requires the existing physical Fold7/Filament acceptance gate.
