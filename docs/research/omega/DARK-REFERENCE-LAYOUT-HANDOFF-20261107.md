# Dark reference home layout

## Source and authorization

- Owner requested the supplied darker reference layout on 2026-11-07, with three assistants.
- Base: PR #594, `88dfd45139471463618673a1f827ee062e9bf6d5`.
- Branch: `codex/continuum-dark-reference-layout-20261107`.
- Scope: native home composition, static surfaces and functional hit targets.
- Status at initial checkpoint: **WIP / UNVERIFIED**.

## Design

Keep the original cyan/violet identity, live avatar and original orb. Use near-black card interiors, saturated cyan/violet edge illumination against dark interiors and larger, vertically arranged feature cards. Restore the home input bar. The hero owns the primary area; secondary diagnostics follow all ten feature cards and the main status strip in the scrollable content. The owner explicitly confirmed scrolling and requested stronger color intensity/contrast on 2026-11-07. Preserve actual runtime status rather than copying the reference's illustrative green indicators.

Portrait, open Fold and tablet retain all ten feature destinations. Native action bounds follow the drawn controls; microphone, camera, appearance, notifications, settings, model details and navigation stay reachable. No extra animation timer, render loop, remote model, asset dependency or permission is introduced.

## Implementation plan

- [x] Layout assistant: `JarvisCockpitGrid.kt`, `JarvisShellLayout.kt`, `JarvisWindowProfile.kt`; tall cards, shared input bounds and scrollable secondary details.
- [x] Surface assistant: `JarvisReferenceHud.kt`, `JarvisTiles.kt`, palette in `JarvisDesign.kt`; dark glass, bounded rims and static dimensional icon pedestals.
- [x] Integrator: `JarvisCockpitGridContent.kt`, `JarvisShellRenderer.kt`, `ContinuumHomeView.kt`; full card labels, compact companion badge, input controls and native action routing.
- [x] Reviewer: inspect native routing, resize/accessibility tests and actual native Canvas frames; preserve unchanged avatar pose/camera assertions.
- [ ] Run focused geometry/drawing tests and real Android host UI tests. Use `JarvisDeterministicCaptureUiTest` for code-rendered images, not generated mockups.
- [ ] Commit and push source-bound results, exact commands and limitations.

## Compatibility and review focus

Presentation-only change around EXP-05/EXP-11; no model, memory, worker, authority or release protocol changes. Retain large-font scrolling, original render identity, meaningful status, cancelled/stale-task behavior and minimum 48dp controls. Existing PR #594 review findings outside the layout scope remain separate.

No main merge, signing, publication, device installation or claim of physical Fold7/GPU/thermal acceptance. Original signing/package/version contract is untouched. No GitHub Actions trigger requested for this layout work; checkpoint commits use `[skip ci]`.

## Verification

Initial checkpoint: NOT_RUN. The base's results are not evidence for the changed layout.

Source checkpoint `ac6f363e07795d70341b19c719aae59b8cf37144`: real offline Android SDK/Gradle `:app:compileDebugKotlin` succeeded in 1m11s. A later small header-width adjustment and the final tests require a fresh run. Current test sources cover live input touch dispatch, disabled controls, responsive bounds, dark materials and real avatar pose/framing. Physical-device results remain NOT_RUN.

## Resume and rollback

Read this file, fetch the exact branch HEAD and inspect its PR before continuing. Revert only this branch's layout commits to return to PR #594. Other active branches must remain untouched.

### First integrated test checkpoint

Frozen source `b12acea2d4fb2dcea5c100b12fed4c6c09436864`: real offline SDK/Gradle `:app:testDebugUnitTest` ran 247 tests: 245 passed, 2 failed. `JarvisHeaderBrandFitTest` exposed a truncated header caption; `JarvisLivingSceneTest` still asserted the old horizontal dialog/head relationship. The native routing, avatar framing, all 12 real Activity reference tests and deterministic captures passed. The failures are being corrected, with native font checks for complete rail/card labels and revised saturated surfaces pending a fresh source-bound run. This checkpoint does not claim those fixes passed.

The initial CLI `--tests` invocation was rejected by the existing QA task allowlist. The subsequent run used a Gradle init-script test filter and the permitted `:app:testDebugUnitTest` task; no gate was disabled.

### Saturated-material and readability checkpoint

Source `c318e2d0ada64e8f95aef463ab72d1f45366e5d0`: **239/239 focused Android host/JVM tests passed**, zero failures/errors/skips, Gradle succeeded in 1m43s. Native font checks include all ten cards across three widths and font factors 1/1.5/2 (90 cases) plus complete rail labels. Actual owner-profile captures are 1384x1536 pixels at density 2; the diagnostic capture scrolls the real dashboard by 1283px, and all four diagnostic panels are visible below the main status strip.

Final visual review found decorative fine ribbons crossing the face. The last material adjustment moves only those normalized ribbons into the lower half of the hero/input backdrop; avatar/orb signals and geometry are unchanged. This small final adjustment awaits the fresh checkpoint run before completion.
